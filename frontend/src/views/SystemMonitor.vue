<!--
文件用途：提供织云 ERP SystemMonitor的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP SystemMonitor的查询、编辑、状态展示和用户操作界面。
设计思路：使用 Vue 3 响应式状态与项目公共布局；页面编排交互，服务端负责业务规则和最终授权。
实现方法：加载选项/列表，按当前状态驱动表单和弹窗，调用业务 API 后刷新并反馈结果。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-20
修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
最近测试时间：本次整理未执行功能测试
测试人员：本次整理未执行
测试状态：待功能回归；历史测试记录待追溯
测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
-->
<script setup lang="ts">
import { precisionKey, precisionLabels, readPrecision, displayDecimal } from "../utils/decimalDisplay"
import { computed, reactive, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import { SearchOutlined } from '@ant-design/icons-vue'
import api from '../api'
import { can, canEnter } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import '../assets/standard-list-fill.css'

const props = defineProps<{ kind: string }>()
const categories = [
  { key: 'operlog', label: '操作日志', permission: 'monitor:operlog:list' },
  { key: 'loginInfo', label: '登录日志', permission: 'monitor:logininfo:list' },
].filter(item => canEnter(item.permission))
const category = ref(categories[0]?.key ?? '')
const kind = computed(() => props.kind === 'logs' ? category.value : props.kind)
const fields: Record<string, Record<string, string>> = {
  "menu-directory": {"menuName": "菜单名称", "menuType": "类型", "parentName": "上级菜单", "path": "路由地址", "component": "组件路径", "perms": "权限标识", "status": "状态"},
  "cache-overview": {"metric": "指标", "value": "值"},
  "dictionary": {"dictName": "字典名称", "dictType": "字典类型", "remark": "备注"},
  "parameters": {"configName": "参数名称", "configKey": "参数键", "configValue": "参数值", "configType": "参数来源", "remark": "说明"},
  "files": {"originalName": "原文件名", "usage": "应用位置", "fileSuffix": "后缀", "createTime": "上传时间"},
  "storage-config": {"configKey": "配置名称", "bucketName": "存储桶", "region": "区域", "status": "状态"},
  "clients": {"clientKey": "客户端", "clientId": "客户端标识", "deviceType": "设备类型", "grantType": "授权方式", "status": "状态"},

  online: { userName: '账号', orgName: '所属部门', clientKey: '客户端', deviceType: '设备类型', ipaddr: '登录 IP', loginLocation: '登录地点', browser: '浏览器', os: '操作系统', loginTime: '登录时间' },
  operlog: { operName: '操作人', title: '功能', businessType: '业务动作', auditObject: '操作对象', auditDetail: '详情', operUrl: '请求路径', operIp: '来源 IP', costTime: '耗时（毫秒）', requestMethod: '请求方式', operTime: '操作时间', status: '结果' },
  loginInfo: { userName: '账号', ipaddr: '登录 IP', browser: '浏览器', loginTime: '登录时间', status: '结果' },
}
const fileName = ref('')
const fileFilter = ref('')
const fileBusy = ref(false)
const filePicker = ref<HTMLInputElement>()
const previewUrl = ref('')
function closePreview() {
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
async function downloadFile(row: Record<string, unknown>, preview = false) {
  try {
    const file = await api.get('/resource/oss/download/' + row.ossId, { responseType: 'blob' }) as unknown as Blob
    if (!(file instanceof Blob) || file.type.includes('application/json')) throw new Error()
    const url = URL.createObjectURL(file)
    if (preview) {
      closePreview()
      previewUrl.value = url
      return
    }
    const link = document.createElement('a')
    link.href = url
    link.download = String(row.originalName)
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    message.error(preview ? '预览失败' : '下载失败')
  }
}
async function uploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  fileBusy.value = true
  try {
    for (const file of files) {
      const data = new FormData()
      data.append('file', file)
      try {
        const res: any = await api.post('/resource/oss/upload', data)
        if (res.code !== 200) throw new Error()
        message.success(file.name + '：上传成功')
      } catch {
        message.error(file.name + '：上传失败')
      }
    }
    await load()
  } finally {
    input.value = ''
    fileBusy.value = false
  }
}
const isLogs = computed(() => ['logs', 'operlog', 'loginInfo'].includes(props.kind))
const account = ref('')
const ipAddress = ref('')
const dictionaryName = ref('')
const dictionaryType = ref('')
const dictionaryDrawerOpen = ref(false)
const dictionaryItems = ref<Record<string, any>[]>([])
const dictionaryItemsLoading = ref(false)
const dictionaryItemModalOpen = ref(false)
const dictionarySaving = ref(false)
const dictionaryEditing = ref(false)
const dictionaryForm = reactive<Record<string, any>>({ dictId: undefined, dictName: '', dictType: '', remark: '' })
const dictionaryItemForm = reactive<Record<string, any>>({ dictCode: undefined, dictSort: 0, dictLabel: '', dictValue: '', dictType: '', cssClass: '', listClass: 'default', isDefault: 'N', remark: '' })
const selectedDictionary = ref<Record<string, any>>()
const logSelectedRowKeys = ref<string[]>([])
const menuModalOpen = ref(false)
const menuSaving = ref(false)
const menuEditing = ref(false)
const menuForm = reactive<Record<string, any>>({ menuId: undefined, parentId: 0, menuName: '', orderNum: 1, path: '', component: '', queryParam: '', isFrame: 'N', isCache: 'Y', menuType: 'C', visible: '0', status: '0', perms: '', icon: '#', activeMenu: '', ext: '', remark: '' })
const clientModalOpen = ref(false)
const clientSaving = ref(false)
const clientEditing = ref(false)
const clientForm = reactive<Record<string, any>>({ id: undefined, clientId: '', clientKey: '', clientSecret: '', grantTypeList: ['password'], deviceType: 'pc', accessPathList: [], ipWhitelistList: [], activeTimeout: 1800, timeout: 86400, status: '0' })
const storageModalOpen = ref(false)
const storageSaving = ref(false)
const storageEditing = ref(false)
const storageForm = reactive<Record<string, any>>({ ossConfigId: undefined, configKey: '', accessKey: '', secretKey: '', bucketName: '', prefix: '', endpoint: '', domainUrl: '', isHttps: 'N', status: 'N', region: '', ext1: '', remark: '', accessPolicy: '0' })
const recordKeyword = ref('')
const columnSettingsOpen = ref(false)
const outcome = ref<string>()
const applied = ref<{ account?: string; status?: string }>({})
const pageSize = ref(20)
const hiddenColumns = ref<Record<string, string[]>>({})
const technicalFields = ['operUrl', 'operIp', 'costTime', 'requestMethod']
const allColumns = computed(() => Object.entries(fields[kind.value] || {}).map(([key, title]) => ({
  key, title, dataIndex: key,
  ...(isLogs.value ? { width: ['operTime', 'loginTime', 'auditObject', 'operUrl'].includes(key) ? 190 : 110, ellipsis: true } : {}),
})))
const columns = computed(() => {
  if (kind.value === 'files') return [...allColumns.value, { key: 'fileAction', title: '操作', width: 160 }]
  if (kind.value === 'dictionary') return [...allColumns.value, { key: 'dictionaryAction', title: '操作', width: 150 }]
  if (kind.value === 'menu-directory') return [...allColumns.value, { key: 'menuAction', title: '操作', width: 140 }]
  if (kind.value === 'clients') return [...allColumns.value, { key: 'clientAction', title: '操作', width: 180 }]
  if (kind.value === 'storage-config') return [...allColumns.value, { key: 'storageAction', title: '操作', width: 180 }]
  if (kind.value === 'parameters' && parameterView.value === 'framework') return [...allColumns.value, { key: 'action', title: '操作', width: 140 }]
  if (kind.value === 'online') return [...allColumns.value, { key: 'onlineAction', title: '操作', width: 110 }]
  if (!isLogs.value) return kind.value === 'parameters' ? [...allColumns.value, { key: 'action', title: '操作', width: 90 }] : allColumns.value
  const hidden = hiddenColumns.value[kind.value] ?? (kind.value === 'operlog' ? technicalFields : [])
  return [
    { key: 'sequence', title: '序号', width: 64 },
    ...allColumns.value.filter(column => column.key !== 'auditDetail' && !hidden.includes(column.key)),
    ...allColumns.value.filter(column => column.key === 'auditDetail'),
    { key: 'logAction', title: '操作', width: kind.value === 'loginInfo' ? 110 : 70 },
  ]
})
const logRowSelection = computed(() => ({
  selectedRowKeys: logSelectedRowKeys.value,
  onChange: (keys: (string | number)[]) => { logSelectedRowKeys.value = keys.map(String) },
}))
function toggleColumn(key: string) {
  const hidden = hiddenColumns.value[kind.value] ?? (kind.value === 'operlog' ? technicalFields : [])
  hiddenColumns.value[kind.value] = hidden.includes(key) ? hidden.filter(item => item !== key) : [...hidden, key]
}
function search() {
  applied.value = { account: account.value.trim() || undefined, status: outcome.value }
  page.value = 1
  void load()
}
function resetFilters() {
  account.value = ''
  ipAddress.value = ''
  outcome.value = undefined
  search()
}
/** 统一处理动态表格字段名，兼容框架列的可选字段与数组路径。 / Normalize dynamic table field paths, including optional and array-shaped framework columns. */
function columnField(column: { dataIndex?: unknown }): string { return typeof column.dataIndex === 'string' ? column.dataIndex : Array.isArray(column.dataIndex) ? column.dataIndex.map(String).join('.') : '' }
/** 重置字典类型查询条件并重新加载第一页。 / Reset dictionary type filters and reload the first page. */
function resetDictionaryFilters() { dictionaryName.value = ''; dictionaryType.value = ''; page.value = 1; void load() }
function resetOnlineFilters() { account.value = ''; ipAddress.value = ''; page.value = 1; void load() }
/** 下载框架原生 Excel 导出结果。 / Download a framework-provided Excel export. */
async function exportFrameworkList(endpoint: string, filename: string, params: Record<string, unknown> = {}) {
  try {
    const blob = await api.post(endpoint, null, { params, responseType: 'blob' }) as unknown as Blob
    if (!(blob instanceof Blob) || blob.type.includes('application/json')) throw new Error('导出失败，请检查导出权限')
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '导出失败') }
}
let requestId = 0
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const selected = ref<Record<string, unknown>>()
const configDialog = ref(false)
const configSaving = ref(false)
const configEditing = ref(false)
const parameterView = ref<'business' | 'framework'>('business')
const editingConfig = ref<Record<string, any>>({})
const autoCodeSaving = ref(false)
const autoCodeConfigId = ref<number>()
const autoCodeRule = ref({ enabled: true, prefix: 'SZ', digits: 4 })
const autoCodeEditing = ref(false)
const savedAutoCodeRule = ref({ enabled: true, prefix: 'SZ', digits: 4 })
const sizeGroupCodeRule = ref({ enabled: true, prefix: 'SG', digits: 4 })
const savedSizeGroupCodeRule = ref({ enabled: true, prefix: 'SG', digits: 4 })
const sizeGroupCodeEditing = ref(false)
const sizeGroupCodeSaving = ref(false)
const colorCodeRule = ref({ enabled: true, prefix: 'CLR', digits: 4 })
const savedColorCodeRule = ref({ ...colorCodeRule.value })
const colorCodeEditing = ref(false)
const colorCodeSaving = ref(false)
const materialCodeRule = ref({ enabled: true, prefix: 'MAT', digits: 5 })
const savedMaterialCodeRule = ref({ ...materialCodeRule.value })
const materialCodeEditing = ref(false)
const materialCodeSaving = ref(false)
const materialDefaultSchemeId = ref('')
const savedMaterialDefaultSchemeId = ref('')
const materialDefaultEditing = ref(false)
const materialDefaultSaving = ref(false)
const materialDefaultSchemes = ref<{value:string;label:string}[]>([])
const styleDefaultSchemeId = ref('')
const savedStyleDefaultSchemeId = ref('')
const styleDefaultEditing = ref(false)
const styleDefaultSaving = ref(false)
const customerDefaultSchemeId = ref('')
const savedCustomerDefaultSchemeId = ref('')
const customerDefaultEditing = ref(false)
const customerDefaultSaving = ref(false)
const displayRules = ref(readPrecision())
const editingDisplay = ref(false)
const savingDisplay = ref(false)
/** 保存只读数字显示精度；业务计算与已保存单据原值不随该设置改写。 / Save presentation precision without changing calculations or persisted document values. */
async function saveDisplayRules() {
  savingDisplay.value = true
  try {
    const result:any = await api.post('/system/config/display-decimals', displayRules.value)
    if (result.code !== 200) throw Error(result.msg)
    editingDisplay.value = false
    message.success('数字显示精度已保存')
    await load()
  } catch (error:any) { message.error(error.message || '保存失败') }
  finally { savingDisplay.value = false }
}
const offlineLeadDays = ref(0)
const offlineLeadEditing = ref(false)
const offlineLeadSaving = ref(false)
async function saveOfflineLeadDays() {
  if (!Number.isInteger(offlineLeadDays.value) || offlineLeadDays.value < 0 || offlineLeadDays.value > 3650) { message.warning('请输入 0 至 3650 的整数天数'); return }
  offlineLeadSaving.value=true
  try { const res:any=await api.post('/system/config/order-offline-lead-days',{days:offlineLeadDays.value}); if(res.code!==200) throw new Error(res.msg || '保存失败'); offlineLeadEditing.value=false; message.success('默认下线日期规则已保存'); await load() }
  catch(error:any) { message.error(error?.response?.data?.msg || error.message || '保存失败') }
  finally { offlineLeadSaving.value=false }
}
const layoutCodeRule = ref({ enabled: true, zonePrefix: 'Z', zoneDigits: 2, rackPrefix: 'R', rackDigits: 2, binPrefix: 'B', binDigits: 3 })
const savedLayoutCodeRule = ref({ ...layoutCodeRule.value })
const layoutCodeEditing = ref(false)
const layoutCodeSaving = ref(false)
const layoutCodeRow = computed(() => parameterRows.value.find(row => row.configKey === 'inventory.warehouseLayout.codeRule'))
const warehouseCodeRule = ref({ enabled: true, prefix: 'WH', digits: 4 })
const savedWarehouseCodeRule = ref({ ...warehouseCodeRule.value })
const warehouseCodeEditing = ref(false)
const warehouseCodeSaving = ref(false)
const warehouseCodeRow = computed(() => parameterRows.value.find(row => row.configKey === 'inventory.warehouse.codeRule'))
const categoryCodeRule = ref({ enabled: true, separator: '-', digits: 4 })
const savedCategoryCodeRule = ref({ ...categoryCodeRule.value })
const categoryCodeEditing = ref(false)
const categoryCodeSaving = ref(false)
const categoryCodeRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'base.category.codeRule'))
const categoryCodeExample = computed(() => `01${categoryCodeRule.value.separator}${'0'.repeat(Math.max(0, categoryCodeRule.value.digits - 1))}1`)
const parameterRows = computed(() => rows.value)
const sizeRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'system.code.size'))
const sizeGroupRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'system.code.sizeGroup'))
const colorCodeRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'base.color.codeRule'))
const materialCodeRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'inventory.material.codeRule'))
const materialDefaultRow = computed(() => parameterRows.value.find(row => row.configKey === 'inventory.material.defaultCategoryScheme'))
const styleDefaultRow = computed(() => parameterRows.value.find(row => row.configKey === 'rd.style.defaultCategoryScheme'))
const customerDefaultRow = computed(() => parameterRows.value.find(row => row.configKey === 'sales.customer.defaultCategoryScheme'))
const ossPreviewRow = computed(() => parameterRows.value.find(row => row.configKey === 'sys.oss.previewListResource'))
function readCodeRule(row: Record<string, any> | undefined, fallback: { enabled: boolean; prefix: string; digits: number }) {
  if (!row?.configValue) return { ...fallback }
  try {
    const value = JSON.parse(String(row.configValue))
    if (typeof value.enabled === 'boolean' && typeof value.prefix === 'string' && Number.isInteger(value.digits) && value.digits >= 1 && value.digits <= 10) {
      return { enabled: value.enabled, prefix: value.prefix, digits: value.digits }
    }
  } catch { /* 规则值损坏时显示默认值，由保存流程修复。 */ }
  return { ...fallback }
}
function syncCodeRules(configs: Record<string, any>[]) {
  if (!editingDisplay.value) displayRules.value = readPrecision(configs.find(row=>row.configKey===precisionKey)?.configValue)
  if (!offlineLeadEditing.value) { const n=Number(configs.find(row=>row.configKey==='order.customer.offlineLeadDays')?.configValue ?? 0); offlineLeadDays.value=Number.isInteger(n) && n>=0 && n<=3650 ? n : 0 }
  const sizeRow = configs.find(row => row.configKey === 'system.code.size')
  const groupRow = configs.find(row => row.configKey === 'system.code.sizeGroup')
  if (!autoCodeEditing.value) {
    autoCodeRule.value = readCodeRule(sizeRow, { enabled: true, prefix: 'SZ', digits: 4 })
    savedAutoCodeRule.value = { ...autoCodeRule.value }
  }
  if (!sizeGroupCodeEditing.value) {
    sizeGroupCodeRule.value = readCodeRule(groupRow, { enabled: true, prefix: 'SG', digits: 4 })
    savedSizeGroupCodeRule.value = { ...sizeGroupCodeRule.value }
  }
  if (!colorCodeEditing.value) {
    colorCodeRule.value = readCodeRule(configs.find(row => row.configKey === 'base.color.codeRule'), { enabled: true, prefix: 'CLR', digits: 4 })
    savedColorCodeRule.value = { ...colorCodeRule.value }
  }
  if (!layoutCodeEditing.value) {
    const row = configs.find(item => item.configKey === 'inventory.warehouseLayout.codeRule')
    const fallback = { enabled: true, zonePrefix: 'Z', zoneDigits: 2, rackPrefix: 'R', rackDigits: 2, binPrefix: 'B', binDigits: 3 }
    try {
      const value = row?.configValue ? JSON.parse(String(row.configValue)) : fallback
      layoutCodeRule.value = { ...fallback, ...value }
    } catch { layoutCodeRule.value = { ...fallback } }
    savedLayoutCodeRule.value = { ...layoutCodeRule.value }
  }
  if (!warehouseCodeEditing.value) {
    const row = configs.find(item => item.configKey === 'inventory.warehouse.codeRule')
    const fallback = { enabled: true, prefix: 'WH', digits: 4 }
    try { warehouseCodeRule.value = row?.configValue ? { ...fallback, ...JSON.parse(String(row.configValue)) } : { ...fallback } }
    catch { warehouseCodeRule.value = { ...fallback } }
    savedWarehouseCodeRule.value = { ...warehouseCodeRule.value }
  }
  if (!categoryCodeEditing.value) {
    const row = configs.find(item => item.configKey === 'base.category.codeRule')
    const fallback = { enabled: true, separator: '-', digits: 4 }
    try { categoryCodeRule.value = row?.configValue ? { ...fallback, ...JSON.parse(String(row.configValue)) } : { ...fallback } }
    catch { categoryCodeRule.value = { ...fallback } }
    savedCategoryCodeRule.value = { ...categoryCodeRule.value }
  }
  if (!materialCodeEditing.value) {
    materialCodeRule.value = readCodeRule(configs.find(row => row.configKey === 'inventory.material.codeRule'), { enabled: true, prefix: 'MAT', digits: 5 })
    savedMaterialCodeRule.value = { ...materialCodeRule.value }
  }
  if (!styleDefaultEditing.value) {
    styleDefaultSchemeId.value = String(configs.find(row => row.configKey === 'rd.style.defaultCategoryScheme')?.configValue || '')
    savedStyleDefaultSchemeId.value = styleDefaultSchemeId.value
  }
  if (!customerDefaultEditing.value) {
    customerDefaultSchemeId.value = String(configs.find(row => row.configKey === 'sales.customer.defaultCategoryScheme')?.configValue || '')
    savedCustomerDefaultSchemeId.value = customerDefaultSchemeId.value
  }
  if (!materialDefaultEditing.value) {
    materialDefaultSchemeId.value = String(configs.find(row => row.configKey === 'inventory.material.defaultCategoryScheme')?.configValue || '')
    savedMaterialDefaultSchemeId.value = materialDefaultSchemeId.value
  }
}
function openLayoutCodeEdit() { layoutCodeEditing.value = true }
function cancelLayoutCodeEdit() { layoutCodeRule.value = { ...savedLayoutCodeRule.value }; layoutCodeEditing.value = false }
async function saveLayoutCodeRule() {
  const rule = layoutCodeRule.value
  if (!['zone', 'rack', 'bin'].every(level => /^[A-Za-z0-9]{1,8}$/.test(rule[`${level}Prefix` as 'zonePrefix' | 'rackPrefix' | 'binPrefix']) && Number.isInteger(rule[`${level}Digits` as 'zoneDigits' | 'rackDigits' | 'binDigits']) && rule[`${level}Digits` as 'zoneDigits' | 'rackDigits' | 'binDigits'] >= 1 && rule[`${level}Digits` as 'zoneDigits' | 'rackDigits' | 'binDigits'] <= 6)) { message.warning('前缀须为1到8位字母或数字，序号位数须为1到6位'); return }
  layoutCodeSaving.value = true
  try {
    const saved = { ...rule, zonePrefix: rule.zonePrefix.toUpperCase(), rackPrefix: rule.rackPrefix.toUpperCase(), binPrefix: rule.binPrefix.toUpperCase() }
    const res: any = await api.post('/system/config/warehouse-layout-code-rule', saved)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    layoutCodeRule.value = { ...saved }; savedLayoutCodeRule.value = { ...saved }; layoutCodeEditing.value = false
    const configValue = JSON.stringify(saved)
    rows.value = layoutCodeRow.value
      ? rows.value.map(row => row.configKey === 'inventory.warehouseLayout.codeRule' ? { ...row, configValue } : row)
      : [...rows.value, { configName: '仓库布局自动编码规则', configKey: 'inventory.warehouseLayout.codeRule', configValue, configType: 'Y', remark: '仓库布局编码开关、库区/货架/货位前缀及序号位数' }]
    message.success('仓库布局自动编码规则已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存仓库布局编码规则失败') }
  finally { layoutCodeSaving.value = false }
}
function openWarehouseCodeEdit() { warehouseCodeEditing.value = true }
function cancelWarehouseCodeEdit() { warehouseCodeRule.value = { ...savedWarehouseCodeRule.value }; warehouseCodeEditing.value = false }
async function saveWarehouseCodeRule() {
  const rule = warehouseCodeRule.value
  if (!/^[A-Za-z0-9]{1,12}$/.test(rule.prefix) || !Number.isInteger(rule.digits) || rule.digits < 1 || rule.digits > 10) { message.warning('仓库编码前缀须为1到12位字母或数字，序号位数须为1到10位'); return }
  warehouseCodeSaving.value = true
  try {
    const saved = { ...rule, prefix: rule.prefix.toUpperCase() }
    const res: any = await api.post('/system/config/warehouse-code-rule', saved)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    warehouseCodeRule.value = { ...saved }; savedWarehouseCodeRule.value = { ...saved }; warehouseCodeEditing.value = false
    const configValue = JSON.stringify(saved)
    rows.value = warehouseCodeRow.value
      ? rows.value.map(row => row.configKey === 'inventory.warehouse.codeRule' ? { ...row, configValue } : row)
      : [...rows.value, { configName: '仓库自动编码规则', configKey: 'inventory.warehouse.codeRule', configValue, configType: 'Y', remark: '仓库编码自动生成开关、前缀及序号位数' }]
    message.success('仓库自动编码规则已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存仓库自动编码规则失败') }
  finally { warehouseCodeSaving.value = false }
}
/** 恢复已保存的分类编码规则，丢弃本次未提交输入。 / Restore the saved category code rule and discard the draft. */
function cancelCategoryCodeEdit() { categoryCodeRule.value = { ...savedCategoryCodeRule.value }; categoryCodeEditing.value = false }
/** 校验并保存类型联动规则；前后值由后端业务审计记录。 / Validate and persist the type-linked rule with server-side audit. */
async function saveCategoryCodeRule() {
  const rule = categoryCodeRule.value
  if (!['-', '_', '.', ''].includes(rule.separator) || !Number.isInteger(rule.digits) || rule.digits < 1 || rule.digits > 8) { message.warning('分隔符只能选短横线、下划线、点号或不使用；序号位数须为1到8位'); return }
  categoryCodeSaving.value = true
  try {
    const saved = { ...rule }
    const res: any = await api.post('/system/config/category-code-rule', saved)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    categoryCodeRule.value = { ...saved }; savedCategoryCodeRule.value = { ...saved }; categoryCodeEditing.value = false
    const configValue = JSON.stringify(saved)
    rows.value = categoryCodeRuleRow.value
      ? rows.value.map(row => row.configKey === 'base.category.codeRule' ? { ...row, configValue } : row)
      : [...rows.value, { configName: '分类编码自动生成规则', configKey: 'base.category.codeRule', configValue, configType: 'Y', remark: '分类类型编码前缀、分类编码自动生成开关、分隔符及序号位数' }]
    message.success('分类编码自动生成规则已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存分类编码规则失败') }
  finally { categoryCodeSaving.value = false }
}
async function openAutoCode() {
  try {
    if (!rows.value.length) await load()
    const config = rows.value.find((row: Record<string, unknown>) => row.configKey === 'system.code.size') as Record<string, any> | undefined
    if (!config) throw new Error('参数列表中未找到尺码自动编码规则，请刷新参数列表确认')
    autoCodeConfigId.value = config.configId
    if (!config.configValue) {
      autoCodeRule.value = { enabled: true, prefix: 'SZ', digits: 4 }
    } else {
      const saved = JSON.parse(config.configValue)
      if (typeof saved.enabled !== 'boolean' || typeof saved.prefix !== 'string' || !Number.isInteger(saved.digits)) throw new Error('尺码自动编码规则格式无效，请检查系统参数')
      autoCodeRule.value = { enabled: saved.enabled, prefix: saved.prefix, digits: saved.digits }
    }
    savedAutoCodeRule.value = { ...autoCodeRule.value }
    autoCodeEditing.value = true
  } catch (error) { message.error(error instanceof Error ? error.message : '读取编码规则失败') }
}
function cancelAutoCodeEdit() {
  autoCodeRule.value = { ...savedAutoCodeRule.value }
  autoCodeEditing.value = false
}
async function saveAutoCode() {
  if (typeof autoCodeRule.value.prefix !== 'string' || !autoCodeRule.value.prefix.trim() || autoCodeRule.value.prefix.trim().length > 30 || !Number.isInteger(autoCodeRule.value.digits) || autoCodeRule.value.digits < 1 || autoCodeRule.value.digits > 10) {
    message.warning('请填写前缀（最多30位），位数需在1到10之间'); return
  }
  autoCodeSaving.value = true
  try {
    if (!autoCodeConfigId.value) throw new Error('缺少系统参数编号，请关闭设置后重新打开')
    const res: any = await api.post('/system/config/size-code-rule', { ...autoCodeRule.value, prefix: autoCodeRule.value.prefix.trim() })
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    message.success('尺码自动编码规则已保存')
    savedAutoCodeRule.value = { ...autoCodeRule.value }
    autoCodeEditing.value = false
    const configValue = JSON.stringify({ ...autoCodeRule.value, prefix: autoCodeRule.value.prefix.trim() })
    rows.value = rows.value.map(row => row.configKey === 'system.code.size' ? { ...row, configValue } : row)
  } catch (error: any) {
    const status = error?.response?.status
    const url = error?.config?.url
    message.error(status ? `保存请求失败（HTTP ${status}）：${url || '/system/config'}` : error instanceof Error ? error.message : '保存编码规则失败')
  }
  finally { autoCodeSaving.value = false }
}
async function openSizeGroupCode() {
  try {
    if (!rows.value.length) await load()
    const config = rows.value.find((row: Record<string, unknown>) => row.configKey === 'system.code.sizeGroup') as Record<string, any> | undefined
    const saved = config?.configValue ? JSON.parse(config.configValue) : { enabled: true, prefix: 'SG', digits: 4 }
    if (typeof saved.enabled !== 'boolean' || typeof saved.prefix !== 'string' || !Number.isInteger(saved.digits)) throw new Error('尺码组自动编码规则格式无效，请检查系统参数')
    sizeGroupCodeRule.value = { enabled: saved.enabled, prefix: saved.prefix, digits: saved.digits }
    savedSizeGroupCodeRule.value = { ...sizeGroupCodeRule.value }
    sizeGroupCodeEditing.value = true
  } catch (error) { message.error(error instanceof Error ? error.message : '读取编码规则失败') }
}
function cancelSizeGroupCodeEdit() {
  sizeGroupCodeRule.value = { ...savedSizeGroupCodeRule.value }
  sizeGroupCodeEditing.value = false
}
async function saveSizeGroupCode() {
  if (!sizeGroupCodeRule.value.prefix.trim() || sizeGroupCodeRule.value.prefix.trim().length > 30 || !Number.isInteger(sizeGroupCodeRule.value.digits) || sizeGroupCodeRule.value.digits < 1 || sizeGroupCodeRule.value.digits > 10) { message.warning('请填写前缀（最多30位），位数需在1到10之间'); return }
  sizeGroupCodeSaving.value = true
  try {
    const saved = { ...sizeGroupCodeRule.value, prefix: sizeGroupCodeRule.value.prefix.trim() }
    const res: any = await api.post('/system/config/size-group-code-rule', saved)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    message.success('尺码组自动编码规则已保存')
    savedSizeGroupCodeRule.value = { ...saved }
    sizeGroupCodeRule.value = { ...saved }
    sizeGroupCodeEditing.value = false
    const configValue = JSON.stringify(saved)
    if (rows.value.some(row => row.configKey === 'system.code.sizeGroup')) rows.value = rows.value.map(row => row.configKey === 'system.code.sizeGroup' ? { ...row, configValue } : row)
    else rows.value = [...rows.value, { configId: undefined, configName: '尺码组自动编码规则', configKey: 'system.code.sizeGroup', configValue, configType: 'Y', remark: '尺码组自动编码开关、编号前缀及数字位数' }]
  } catch (error: any) {
    const status = error?.response?.status
    message.error(status ? `保存请求失败（HTTP ${status}）：${error?.config?.url || '/system/config'}` : error instanceof Error ? error.message : '保存编码规则失败')
  } finally { sizeGroupCodeSaving.value = false }
}
function openColorCode() {
  colorCodeRule.value = readCodeRule(colorCodeRuleRow.value, { enabled: true, prefix: 'CLR', digits: 4 })
  savedColorCodeRule.value = { ...colorCodeRule.value }
  colorCodeEditing.value = true
}
function cancelColorCodeEdit() {
  colorCodeRule.value = { ...savedColorCodeRule.value }
  colorCodeEditing.value = false
}
async function saveColorCode() {
  const prefix = colorCodeRule.value.prefix.trim()
  if (!prefix || prefix.length > 30 || !/^[A-Za-z0-9_.-]+$/.test(prefix) || !Number.isInteger(colorCodeRule.value.digits) || colorCodeRule.value.digits < 1 || colorCodeRule.value.digits > 10) { message.warning('编码前缀须为字母、数字、点、下划线或短横线，序号位数须在1到10之间'); return }
  colorCodeSaving.value = true
  try {
    const saved = { ...colorCodeRule.value, prefix }
    const res: any = await api.post('/system/color/code-rule', saved)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    colorCodeRule.value = { ...saved }
    savedColorCodeRule.value = { ...saved }
    colorCodeEditing.value = false
    const configValue = JSON.stringify(saved)
    if (rows.value.some(row => row.configKey === 'base.color.codeRule')) rows.value = rows.value.map(row => row.configKey === 'base.color.codeRule' ? { ...row, configValue } : row)
    else rows.value = [...rows.value, { configId: undefined, configName: '颜色自动编码规则', configKey: 'base.color.codeRule', configValue, configType: 'Y', remark: '颜色编码自动生成开关、前缀及序号位数' }]
    message.success('颜色编码规则已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error?.message || '保存颜色编码规则失败') }
  finally { colorCodeSaving.value = false }
}
function openMaterialCodeEdit(){materialCodeEditing.value=true}
function cancelMaterialCodeEdit(){materialCodeRule.value={...savedMaterialCodeRule.value};materialCodeEditing.value=false}
async function saveMaterialCodeRule(){
  const prefix=materialCodeRule.value.prefix.trim()
  if(!prefix||prefix.length>30||!/[A-Za-z0-9_.-]+/.test(prefix)||!/^[A-Za-z0-9_.-]+$/.test(prefix)||!Number.isInteger(materialCodeRule.value.digits)||materialCodeRule.value.digits<1||materialCodeRule.value.digits>10){message.warning('编码前缀须为字母、数字、点、下划线或短横线，序号位数须在1到10之间');return}
  materialCodeSaving.value=true
  try{const saved={...materialCodeRule.value,prefix:prefix.toUpperCase()};const res:any=await api.post('/system/config/material-code-rule',saved);if(res.code!==200)throw new Error(res.msg||'保存失败');materialCodeRule.value={...saved};savedMaterialCodeRule.value={...saved};materialCodeEditing.value=false;const configValue=JSON.stringify(saved);rows.value=materialCodeRuleRow.value?rows.value.map(row=>row.configKey==='inventory.material.codeRule'?{...row,configValue}:row):[...rows.value,{configName:'物料自动编码规则',configKey:'inventory.material.codeRule',configValue,configType:'Y',remark:'物料编码自动生成开关、前缀和序号位数；建议编码可以人工修改'}];message.success('物料自动编码规则已保存')}
  catch(error:any){message.error(error?.response?.data?.msg||error.message||'保存物料编码规则失败')}finally{materialCodeSaving.value=false}
}
async function openMaterialDefaultCategory(){
  try{const [scopeRes,defaultRes]=await Promise.all([api.get('/inventory/material/category-options'),api.get('/inventory/material/default-category')]) as any[];if(scopeRes.code!==200||defaultRes.code!==200)throw new Error(scopeRes.msg||defaultRes.msg||'读取物料分类参数失败');const unique=new Map<string,{value:string;label:string}>();for(const row of scopeRes.data?.rows||[])if(row.selectable)unique.set(String(row.schemeId),{value:String(row.schemeId),label:String(row.schemeName)});materialDefaultSchemes.value=[...unique.values()];materialDefaultSchemeId.value=String(defaultRes.data?.schemeId||'');savedMaterialDefaultSchemeId.value=materialDefaultSchemeId.value;materialDefaultEditing.value=true}catch(error:any){message.error(error?.response?.data?.msg||error.message||'读取物料分类参数失败')}
}
function cancelMaterialDefaultCategory(){materialDefaultSchemeId.value=savedMaterialDefaultSchemeId.value;materialDefaultEditing.value=false}
async function saveMaterialDefaultCategory(){if(materialDefaultSchemeId.value&&!materialDefaultSchemes.value.some(option=>option.value===materialDefaultSchemeId.value)){message.warning('所选分类类型当前不可用，请刷新后重试');return}materialDefaultSaving.value=true;try{const value=materialDefaultSchemeId.value;const res:any=await api.post('/system/config/material-default-category',{schemeId:value});if(res.code!==200)throw new Error(res.msg||'保存失败');savedMaterialDefaultSchemeId.value=value;materialDefaultEditing.value=false;const configValue=value;rows.value=materialDefaultRow.value?rows.value.map(row=>row.configKey==='inventory.material.defaultCategoryScheme'?{...row,configValue}:row):[...rows.value,{configName:'物料默认分类类型',configKey:'inventory.material.defaultCategoryScheme',configValue,configType:'Y',remark:'物料档案页面的默认分类类型；留空时展示当前员工全部分类授权范围'}];message.success('物料默认分类参数已保存')}catch(error:any){message.error(error?.response?.data?.msg||error.message||'保存物料默认分类参数失败')}finally{materialDefaultSaving.value=false}}
/** 参数只影响进入款式档案时的初始定位，不扩展人员分类权限。 */
async function openStyleDefaultCategory() {
  try {
    const response: any = await api.get('/inventory/material/category-options')
    if (response.code !== 200) throw new Error(response.msg || '读取分类类型失败')
    const unique = new Map<string, {value: string; label: string}>()
    for (const row of response.data?.rows || []) if (row.selectable) unique.set(String(row.schemeId), { value: String(row.schemeId), label: String(row.schemeName) })
    materialDefaultSchemes.value = [...unique.values()]
    styleDefaultSchemeId.value = savedStyleDefaultSchemeId.value
    styleDefaultEditing.value = true
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取分类类型失败') }
}
function cancelStyleDefaultCategory() { styleDefaultSchemeId.value = savedStyleDefaultSchemeId.value; styleDefaultEditing.value = false }
async function saveStyleDefaultCategory() {
  const value = String(styleDefaultSchemeId.value || '')
  if (value && !materialDefaultSchemes.value.some(option => option.value === value)) return message.warning('所选分类类型当前不可用，请刷新后重试')
  styleDefaultSaving.value = true
  try {
    const response: any = await api.post('/system/config/style-default-category', { schemeId: value })
    if (response.code !== 200) throw new Error(response.msg || '保存失败')
    savedStyleDefaultSchemeId.value = value; styleDefaultSchemeId.value = value; styleDefaultEditing.value = false
    rows.value = styleDefaultRow.value
      ? rows.value.map(row => row.configKey === 'rd.style.defaultCategoryScheme' ? { ...row, configValue: value } : row)
      : [...rows.value, { configName: '款式默认分类类型', configKey: 'rd.style.defaultCategoryScheme', configValue: value, configType: 'Y' }]
    message.success('款式默认分类参数已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存款式默认分类参数失败') }
  finally { styleDefaultSaving.value = false }
}
async function openCustomerDefaultCategory() {
  try {
    const response: any = await api.get('/inventory/material/category-options')
    if (response.code !== 200) throw new Error(response.msg || '读取分类类型失败')
    const unique = new Map<string, {value: string; label: string}>()
    for (const row of response.data?.rows || []) if (row.selectable) unique.set(String(row.schemeId), { value: String(row.schemeId), label: String(row.schemeName) })
    materialDefaultSchemes.value = [...unique.values()]
    customerDefaultSchemeId.value = savedCustomerDefaultSchemeId.value
    customerDefaultEditing.value = true
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取分类类型失败') }
}
function cancelCustomerDefaultCategory() { customerDefaultSchemeId.value = savedCustomerDefaultSchemeId.value; customerDefaultEditing.value = false }
async function saveCustomerDefaultCategory() {
  const value = String(customerDefaultSchemeId.value || '')
  if (value && !materialDefaultSchemes.value.some(option => option.value === value)) return message.warning('所选分类类型当前不可用，请刷新后重试')
  customerDefaultSaving.value = true
  try {
    const response: any = await api.post('/system/config/customer-default-category', { schemeId: value })
    if (response.code !== 200) throw new Error(response.msg || '保存失败')
    savedCustomerDefaultSchemeId.value = value; customerDefaultSchemeId.value = value; customerDefaultEditing.value = false
    rows.value = customerDefaultRow.value
      ? rows.value.map(row => row.configKey === 'sales.customer.defaultCategoryScheme' ? { ...row, configValue: value } : row)
      : [...rows.value, { configName: '客户默认分类类型', configKey: 'sales.customer.defaultCategoryScheme', configValue: value, configType: 'Y' }]
    message.success('客户默认分类参数已保存')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存客户默认分类参数失败') }
  finally { customerDefaultSaving.value = false }
}

function editConfig(row: Record<string, unknown>) {
  configEditing.value = true
  editingConfig.value = { ...row }
  configDialog.value = true
}
function addConfig() {
  configEditing.value = false
  editingConfig.value = { configId: undefined, configName: '', configKey: '', configValue: '', configType: 'N', remark: '' }
  configDialog.value = true
}
async function saveConfig() {
  configSaving.value = true
  try {
    const res: any = configEditing.value
      ? await api.put('/system/config', editingConfig.value)
      : await api.post('/system/config', editingConfig.value)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    message.success('参数已保存')
    configDialog.value = false
    await load()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '保存失败')
  } finally {
    configSaving.value = false
  }
}
function removeConfig(row: Record<string, any>) {
  Modal.confirm({ title: '删除系统参数', content: `确认删除参数“${row.configName}”（${row.configKey}）吗？删除系统内置参数可能影响运行行为。`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/system/config/' + row.configId)
    if (result.code !== 200) throw new Error(result.msg || '删除失败')
    message.success('系统参数已删除')
    await load()
  } })
}
async function refreshConfigCache() {
  try {
    const result: any = await api.delete('/system/config/refreshCache')
    if (result.code !== 200) throw new Error(result.msg || '刷新失败')
    message.success('系统参数缓存已刷新')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '刷新参数缓存失败') }
}
function sizeAudit(row: Record<string, unknown>) {
  const url = String(row.operUrl ?? '')
  const person = /^\/system\/person(?:\/\d+(?:\/(?:reset-password|restore|permanent))?)?$/.test(url)
  const customer = url.match(/^\/system\/customer(?:\/(\d+))?(\/status)?$/)
  const path = (person ? [url, 'person'] : null) ?? (customer ? [customer[0], 'customer', customer[1], customer[2]] : null) ?? url.match(/^\/system\/size-data\/(size-group|size)(?:\/(\d+))?(\/status)?$/)
  const action = ({ '1': '新增', '2': '修改', '3': '删除' } as Record<string, string>)[String(row.businessType)] ?? '其他'

  function parse(value: unknown): Record<string, unknown> {
    try { return JSON.parse(String(value)) ?? {} } catch { return {} }
  }
  // LogAspect joins separately serialized arguments with spaces.
  const rawParams = String(row.operParam ?? '')
  let params = parse(rawParams)
  if (!Object.keys(params).length || Array.isArray(params) || typeof params !== 'object') {
    params = {}
    for (const match of rawParams.matchAll(/"(?:\\.|[^"\\])*"|\{(?:[^{}"\\]|"(?:\\.|[^"\\])*")*\}/g)) {
      if (!match[0].startsWith('{')) continue
      const candidate = parse(match[0])
      if ('name' in candidate || 'status' in candidate || 'sizeIds' in candidate) {
        params = candidate
        break
      }
    }
  }
  if (!path && typeof params.action !== 'string') return { businessType: action, auditObject: '未记录' }
  const result = parse(row.jsonResult)
  const target = person ? '人员' : customer ? '客户' : path?.[1] === 'size-group' ? '尺码组' : '尺码'
  const id = path?.[2] ?? (typeof params.id === 'string' ? params.id : undefined) ?? (String(row.status) === '0' ? String(result.data ?? '').match(/^\d+$/)?.[0] : undefined)
  const status = String(params.status ?? '')
  const verb = path?.[3] ? status === '0' ? '启用' : status === '1' ? '停用' : '变更状态' : action
  const name = typeof params.name === 'string' ? params.name : ''
  const auditObject = [name, id && ('ID ' + id)].filter(Boolean).join(' / ')
  return {
    businessType: typeof params.action === 'string' ? params.action : verb + target,
    auditObject: auditObject || '未记录',
    auditFields: customer && !params.changes && !path?.[3]
      ? { '变更明细': '历史日志未保存变更明细' }
      : params.changes && typeof params.changes === 'object'
      ? Object.fromEntries(Object.entries(params.changes).map(([key, pair]) => {
          const labels: Record<string, string> = { sales_representative_id: '关联业务员', name: '名称', name_en: '英文名称', sort_order: '排序', remark: '备注', status: '状态', sizeIds: '尺码成员 ID', tagIds: '标签 ID' }
          const format = (value: unknown) => ['status', '状态', '账号状态'].includes(key) && ['0', '1'].includes(String(value))
            ? String(value) === '0' ? '启用' : '停用'
            : Array.isArray(value) ? value.map(item => typeof item === 'object' ? JSON.stringify(item) : String(item)).join('、') || '空' : typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '空')
          return [labels[key] ?? key, Array.isArray(pair) ? pair.map(format).join(' → ') : '未记录']
        }))
      : {
      '提交名称': name || undefined,
      '提交英文名称': params.name_en,
      '提交排序': params.sort_order,
      '提交备注': params.remark,
      '提交尺码 ID': Array.isArray(params.sizeIds) ? params.sizeIds.join('、') : undefined,
      '目标状态': status === '0' ? '启用' : status === '1' ? '停用' : undefined,
      '变更前的值': undefined,
    },
  }
}
async function load() {
  if (!kind.value) return
  const currentRequest = ++requestId
  const currentKind = kind.value
  loading.value = true
  try {
    const endpoints: Record<string,string> = {"menu-directory": "/system/menu/list", "cache-overview": "/monitor/cache", "dictionary": "/system/dict/type/list", "parameters": "/system/config/list", "files": "/resource/oss/list", "storage-config": "/resource/oss/config/list", "clients": "/system/client/list", "loginInfo": "/monitor/loginInfo/list"}
    const res: any = await api.get(endpoints[kind.value] || '/monitor/' + kind.value + '/list', { params: { pageNum: page.value, pageSize: isLogs.value ? pageSize.value : currentKind === 'parameters' ? 100 : 20,
      ...(currentKind === 'files' ? { originalName: fileFilter.value || undefined } : {}),
      ...(isLogs.value ? { [currentKind === 'operlog' ? 'operName' : 'userName']: applied.value.account, status: applied.value.status } : {}),
      ...(currentKind === 'online' ? { userName: account.value.trim() || undefined, ipaddr: ipAddress.value.trim() || undefined } : {}),
      ...(currentKind === 'dictionary' ? { dictName: dictionaryName.value.trim() || undefined, dictType: dictionaryType.value.trim() || undefined } : {}),
      ...(currentKind === 'menu-directory' ? { menuName: dictionaryName.value.trim() || undefined, status: outcome.value } : {}),
      ...(currentKind === 'clients' ? { clientKey: recordKeyword.value.trim() || undefined } : {}),
      ...(currentKind === 'storage-config' ? { configKey: recordKeyword.value.trim() || undefined } : {}),
      ...(currentKind === 'parameters' && parameterView.value === 'framework' ? { configKey: recordKeyword.value.trim() || undefined } : {}) } })
    if (currentRequest !== requestId) return
    if (res.code !== 200) throw new Error(res.msg || '查询失败')
    const data = res.data
    const records = kind.value === 'cache-overview'
      ? Object.entries(data.info || {}).map(([metric, value]) => ({ metric, value }))
      : Array.isArray(data) ? data : data.rows || []
    if (currentKind === 'parameters' && (!recordKeyword.value.trim() || parameterView.value === 'business')) {
      syncCodeRules(records as Record<string, any>[])
      try {
        const categoryRes: any = await api.get('/inventory/material/category-options')
        if (categoryRes.code === 200) {
          const unique = new Map<string, { value: string; label: string }>()
          for (const item of categoryRes.data?.rows ?? []) if (item.selectable) unique.set(String(item.schemeId), { value: String(item.schemeId), label: String(item.schemeName) })
          materialDefaultSchemes.value = [...unique.values()]
        }
      } catch { /* 无分类查看范围时仍允许保留空默认值；管理员可稍后重试配置。 */ }
    }
    const usage = new Map<string, string[]>()
    if (currentKind === 'files' && can('system:company:list')) {
      try {
        const ids = records.map((row: Record<string, unknown>) => String(row.ossId)).filter((id: string) => /^\d+$/.test(id))
        const companies: any = ids.length ? await api.get('/system/company/file-usage/' + ids.join(',')) : { code: 200, data: [] }
        if (companies.code === 200) {
          for (const company of companies.data?.rows ?? companies.data ?? []) {
            for (const [id, field] of [[company.logo_oss_id, '公司标志'], [company.login_bg_oss_id, '登录背景']]) {
              if (id == null || id === '') continue
              const key = String(id)
              const locations = usage.get(key) ?? []
              locations.push('公司档案 / ' + company.company_name + ' / ' + field)
              usage.set(key, locations)
            }
          }
        }
      } catch {
        // Keep unknown usage visible when the company list is unavailable.
      }
    }
    if (currentRequest !== requestId) return
    rows.value = records.map((row: Record<string, unknown>, index: number) => ({
      ...Object.fromEntries(allColumns.value.map(({ key }) => [key, row[key]])), errorMsg: row.errorMsg, ossId: row.ossId,
      dictId: row.dictId, dictCode: row.dictCode, infoId: row.infoId, operId: row.operId, tokenId: row.tokenId,
      menuId: row.menuId, parentId: row.parentId, clientId: row.id, ossConfigId: row.ossConfigId,
      id: String(row.infoId ?? row.operId ?? row.dictId ?? row.menuId ?? row.ossConfigId ?? row.id ?? row.ossId ?? row.tokenId ?? index),
      usage: currentKind === "files" ? usage.get(String(row.ossId))?.join("；") ?? "待核对" : undefined,
      ...(currentKind === "parameters" ? { configId: row.configId } : {}), ...(kind.value === 'operlog' ? sizeAudit(row) : {}), index,
    }))
    total.value = data.total ?? rows.value.length
    logSelectedRowKeys.value = []
  } catch (error) {
    if (currentRequest === requestId) message.error(error instanceof Error ? error.message : '查询失败')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
/** 导出框架日志为 Excel；导出权限仍由后端逐项校验。 / Export framework logs with server-enforced permissions. */
async function exportLogs() {
  const endpoint = kind.value === 'loginInfo' ? '/monitor/loginInfo/export' : '/monitor/operlog/export'
  const query = kind.value === 'loginInfo'
    ? { userName: applied.value.account, status: applied.value.status }
    : { operName: applied.value.account, status: applied.value.status }
  try {
    const blob = await api.post(endpoint, null, { params: query, responseType: 'blob' }) as unknown as Blob
    if (!(blob instanceof Blob) || blob.type.includes('application/json')) throw new Error('导出失败，请检查导出权限')
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = kind.value === 'loginInfo' ? '登录日志.xlsx' : '操作日志.xlsx'
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '日志导出失败') }
}
/** 按所选日志 ID 批量删除。 / Delete only the selected log records. */
function deleteSelectedLogs() {
  const idField = kind.value === 'loginInfo' ? 'infoId' : 'operId'
  const ids = rows.value.filter(row => logSelectedRowKeys.value.includes(String(row.id))).map(row => String(row[idField] ?? '')).filter(Boolean)
  if (!ids.length) { message.warning('请先勾选要删除的日志'); return }
  const endpoint = kind.value === 'loginInfo' ? '/monitor/loginInfo/' : '/monitor/operlog/'
  Modal.confirm({ title: '删除日志', content: `确认删除选中的 ${ids.length} 条记录吗？`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete(endpoint + ids.join(','))
    if (result.code !== 200) throw new Error(result.msg || '删除失败')
    message.success('已删除所选日志')
    await load()
  } })
}
/** 清空当前日志类别全部历史记录，使用破坏性操作二次确认。 / Require confirmation before clearing all records of this log category. */
function clearLogs() {
  const label = kind.value === 'loginInfo' ? '登录日志' : '操作日志'
  Modal.confirm({ title: `清空${label}`, content: `此操作会清空全部${label}，且无法恢复。确认继续吗？`, okText: '清空全部', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete(kind.value === 'loginInfo' ? '/monitor/loginInfo/clean' : '/monitor/operlog/clean')
    if (result.code !== 200) throw new Error(result.msg || '清空失败')
    message.success(`${label}已清空`)
    await load()
  } })
}
/** 解锁日志中出现登录失败的账号。 / Clear the login failure lock for the selected account. */
function unlockAccount(row: Record<string, any>) {
  const userName = String(row.userName || '').trim()
  if (!userName) { message.warning('当前日志没有可用于解锁的账号'); return }
  Modal.confirm({ title: '解锁登录账号', content: `确认清除账号“${userName}”的登录失败锁定状态吗？`, okText: '解锁', cancelText: '取消', async onOk() {
    const result: any = await api.get('/monitor/loginInfo/unlock/' + encodeURIComponent(userName))
    if (result.code !== 200) throw new Error(result.msg || '解锁失败')
    message.success('账号锁定状态已清除')
  } })
}
/** 管理员强制结束异常在线会话。 / Let an authorized administrator terminate an online session. */
function forceLogout(row: Record<string, any>) {
  if (!row.tokenId) { message.warning('该会话没有返回会话编号，无法执行强退'); return }
  Modal.confirm({ title: '强制下线', content: `确认让账号“${row.userName || '未知账号'}”在当前设备上退出系统吗？`, okText: '强制下线', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/monitor/online/' + encodeURIComponent(String(row.tokenId)))
    if (result.code !== 200) throw new Error(result.msg || '强制下线失败')
    message.success('该在线会话已强制下线')
    await load()
  } })
}
/** 打开字典类型或字典数据维护窗口。 / Open framework dictionary type and item maintenance. */
async function openDictionaryItems(row: Record<string, any>) {
  selectedDictionary.value = row
  dictionaryDrawerOpen.value = true
  await loadDictionaryItems()
}
async function loadDictionaryItems() {
  const dictType = String(selectedDictionary.value?.dictType || '')
  if (!dictType) return
  dictionaryItemsLoading.value = true
  try {
    const result: any = await api.get('/system/dict/data/list', { params: { dictType, pageNum: 1, pageSize: 200 } })
    if (result.code !== 200) throw new Error(result.msg || '读取字典数据失败')
    dictionaryItems.value = result.data?.rows || []
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取字典数据失败') }
  finally { dictionaryItemsLoading.value = false }
}
function editDictionary(row?: Record<string, any>) {
  dictionaryEditing.value = !!row
  Object.assign(dictionaryForm, row ? { ...row } : { dictId: undefined, dictName: '', dictType: '', remark: '' })
  dictionaryDrawerOpen.value = false
  dictionaryTypeModalOpen.value = true
}
const dictionaryTypeModalOpen = ref(false)
async function saveDictionary() {
  if (!String(dictionaryForm.dictName || '').trim() || !String(dictionaryForm.dictType || '').trim()) { message.warning('字典名称和字典类型均为必填项'); return }
  dictionarySaving.value = true
  try {
    const result: any = dictionaryEditing.value
      ? await api.put('/system/dict/type', dictionaryForm)
      : await api.post('/system/dict/type', dictionaryForm)
    if (result.code !== 200) throw new Error(result.msg || '保存字典类型失败')
    dictionaryTypeModalOpen.value = false
    message.success('字典类型已保存')
    await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存字典类型失败') }
  finally { dictionarySaving.value = false }
}
function removeDictionary(row: Record<string, any>) {
  Modal.confirm({ title: '删除字典类型', content: `删除“${row.dictName}”及其字典数据？框架会执行关联检查。`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/system/dict/type/' + row.dictId)
    if (result.code !== 200) throw new Error(result.msg || '删除失败')
    message.success('字典类型已删除')
    await load()
  } })
}
function editDictionaryItem(row?: Record<string, any>) {
  dictionaryEditing.value = !!row
  Object.assign(dictionaryItemForm, row ? { ...row } : { dictCode: undefined, dictSort: 0, dictLabel: '', dictValue: '', dictType: selectedDictionary.value?.dictType, cssClass: '', listClass: 'default', isDefault: 'N', remark: '' })
  dictionaryItemModalOpen.value = true
}
async function saveDictionaryItem() {
  if (!String(dictionaryItemForm.dictLabel || '').trim() || !String(dictionaryItemForm.dictValue || '').trim()) { message.warning('字典标签和键值均为必填项'); return }
  dictionarySaving.value = true
  try {
    const result: any = dictionaryEditing.value
      ? await api.put('/system/dict/data', dictionaryItemForm)
      : await api.post('/system/dict/data', dictionaryItemForm)
    if (result.code !== 200) throw new Error(result.msg || '保存字典数据失败')
    dictionaryItemModalOpen.value = false
    message.success('字典数据已保存')
    await loadDictionaryItems()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存字典数据失败') }
  finally { dictionarySaving.value = false }
}
function removeDictionaryItem(row: Record<string, any>) {
  Modal.confirm({ title: '删除字典数据', content: `确认删除“${row.dictLabel}”吗？`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/system/dict/data/' + row.dictCode)
    if (result.code !== 200) throw new Error(result.msg || '删除失败')
    message.success('字典数据已删除')
    await loadDictionaryItems()
  } })
}
async function refreshDictionaryCache() {
  try {
    const result: any = await api.delete('/system/dict/type/refreshCache')
    if (result.code !== 200) throw new Error(result.msg || '刷新失败')
    message.success('字典缓存已刷新')
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '刷新字典缓存失败') }
}
/** 框架菜单写操作仅允许超级管理员，页面提示不能替代后端角色校验。 / Menu mutations remain server-restricted to the framework super-admin role. */
async function editMenu(row?: Record<string, any>) {
  menuEditing.value = !!row
  Object.assign(menuForm, { menuId: undefined, parentId: 0, menuName: '', orderNum: 1, path: '', component: '', queryParam: '', isFrame: 'N', isCache: 'Y', menuType: 'C', visible: '0', status: '0', perms: '', icon: '#', activeMenu: '', ext: '', remark: '' })
  if (row) {
    try {
      const result: any = await api.get('/system/menu/' + row.menuId)
      if (result.code !== 200) throw new Error(result.msg || '读取菜单失败')
      Object.assign(menuForm, result.data)
    } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取菜单失败'); return }
  }
  menuModalOpen.value = true
}
/** 保持菜单 ID 作为字符串传给选择器，避免长整型主键在浏览器侧失真。 / Keep menu IDs as strings in the selector to preserve 64-bit identifier precision. */
function menuOptionValue(row: Record<string, unknown>): string | number { const id = row.menuId; return typeof id === 'number' || typeof id === 'string' ? id : String(id ?? '') }
async function saveMenu() {
  if (!String(menuForm.menuName || '').trim()) { message.warning('请输入菜单名称'); return }
  if (menuForm.menuType === 'F' && !String(menuForm.perms || '').trim()) { message.warning('按钮权限标识不能为空'); return }
  menuSaving.value = true
  try {
    const result: any = menuEditing.value ? await api.put('/system/menu', menuForm) : await api.post('/system/menu', menuForm)
    if (result.code !== 200) throw new Error(result.msg || '保存菜单失败')
    menuModalOpen.value = false
    message.success('菜单已保存；路由权限会在重新登录后重新加载')
    await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存菜单失败') }
  finally { menuSaving.value = false }
}
function removeMenu(row: Record<string, any>) {
  Modal.confirm({ title: '删除菜单', content: `确认删除“${row.menuName}”吗？如存在子菜单或角色授权，框架会拒绝删除。`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/system/menu/' + row.menuId)
    if (result.code !== 200) throw new Error(result.msg || '删除菜单失败')
    message.success('菜单已删除')
    await load()
  } })
}
/** 查看或新增框架客户端授权配置。 / Load or initialize an OAuth client configuration. */
async function editClient(row?: Record<string, any>) {
  clientEditing.value = !!row
  Object.assign(clientForm, { id: undefined, clientId: '', clientKey: '', clientSecret: '', grantTypeList: ['password'], deviceType: 'pc', accessPathList: [], ipWhitelistList: [], activeTimeout: 1800, timeout: 86400, status: '0' })
  if (row) {
    try {
      const result: any = await api.get('/system/client/' + row.id)
      if (result.code !== 200) throw new Error(result.msg || '读取客户端配置失败')
      Object.assign(clientForm, result.data, {
        grantTypeList: result.data?.grantTypeList || String(result.data?.grantType || '').split(',').filter(Boolean),
        accessPathList: result.data?.accessPathList || String(result.data?.accessPath || '').split(',').filter(Boolean),
        ipWhitelistList: result.data?.ipWhitelistList || String(result.data?.ipWhitelist || '').split(',').filter(Boolean),
      })
    } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取客户端配置失败'); return }
  }
  clientModalOpen.value = true
}
async function saveClient() {
  if (!String(clientForm.clientKey || '').trim() || !String(clientForm.clientSecret || '').trim()) { message.warning('客户端 Key 和客户端密钥均为必填项'); return }
  clientSaving.value = true
  try {
    const payload = { ...clientForm, clientKey: String(clientForm.clientKey).trim(), clientSecret: String(clientForm.clientSecret).trim() }
    const result: any = clientEditing.value ? await api.put('/system/client', payload) : await api.post('/system/client', payload)
    if (result.code !== 200) throw new Error(result.msg || '保存客户端配置失败')
    clientModalOpen.value = false
    message.success('客户端配置已保存')
    await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存客户端配置失败') }
  finally { clientSaving.value = false }
}
function changeClientStatus(row: Record<string, any>) {
  const status = String(row.status) === '0' ? '1' : '0'
  Modal.confirm({ title: status === '0' ? '启用客户端' : '停用客户端', content: `确认${status === '0' ? '启用' : '停用'}客户端“${row.clientKey}”吗？`, okText: '确认', cancelText: '取消', async onOk() {
    const result: any = await api.put('/system/client/changeStatus', { clientId: row.clientId, status })
    if (result.code !== 200) throw new Error(result.msg || '修改客户端状态失败')
    message.success('客户端状态已更新')
    await load()
  } })
}
function removeClient(row: Record<string, any>) {
  Modal.confirm({ title: '删除客户端', content: `确认删除客户端“${row.clientKey}”吗？删除后使用该客户端凭证的集成应用将无法继续认证。`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/system/client/' + row.id)
    if (result.code !== 200) throw new Error(result.msg || '删除客户端失败')
    message.success('客户端配置已删除')
    await load()
  } })
}
/** 读取对象存储详情；列表接口会隐藏密钥，详情只在授权用户打开编辑时读取。 / Retrieve storage credentials only for an authorized editor. */
async function editStorageConfig(row?: Record<string, any>) {
  storageEditing.value = !!row
  Object.assign(storageForm, { ossConfigId: undefined, configKey: '', accessKey: '', secretKey: '', bucketName: '', prefix: '', endpoint: '', domainUrl: '', isHttps: 'N', status: 'N', region: '', ext1: '', remark: '', accessPolicy: '0' })
  if (row) {
    try {
      const result: any = await api.get('/resource/oss/config/' + row.ossConfigId)
      if (result.code !== 200) throw new Error(result.msg || '读取对象存储配置失败')
      Object.assign(storageForm, result.data)
    } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取对象存储配置失败'); return }
  }
  storageModalOpen.value = true
}
async function saveStorageConfig() {
  const required = ['configKey', 'accessKey', 'secretKey', 'bucketName', 'endpoint', 'accessPolicy']
  if (required.some(key => !String(storageForm[key] || '').trim())) { message.warning('配置标识、访问密钥、私钥、存储桶、访问站点和桶权限类型均为必填项'); return }
  storageSaving.value = true
  try {
    const result: any = storageEditing.value ? await api.put('/resource/oss/config', storageForm) : await api.post('/resource/oss/config', storageForm)
    if (result.code !== 200) throw new Error(result.msg || '保存对象存储配置失败')
    storageModalOpen.value = false
    message.success('对象存储配置已保存')
    await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存对象存储配置失败') }
  finally { storageSaving.value = false }
}
function changeStorageStatus(row: Record<string, any>) {
  if (row.status === 'Y') { message.info('当前已是默认存储。请在其他配置上选择“设为默认”来切换。'); return }
  Modal.confirm({ title: '设为默认存储', content: `确认将“${row.configKey}”设为默认对象存储吗？切换后新上传文件会使用该配置。`, okText: '设为默认', cancelText: '取消', async onOk() {
    const result: any = await api.put('/resource/oss/config/changeStatus', { ossConfigId: row.ossConfigId, status: 'Y' })
    if (result.code !== 200) throw new Error(result.msg || '修改对象存储状态失败')
    message.success('对象存储默认配置已更新')
    await load()
  } })
}
function removeStorageConfig(row: Record<string, any>) {
  Modal.confirm({ title: '删除对象存储配置', content: `确认删除配置“${row.configKey}”吗？如果配置仍被系统使用，框架会阻止删除。`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const result: any = await api.delete('/resource/oss/config/' + row.ossConfigId)
    if (result.code !== 200) throw new Error(result.msg || '删除对象存储配置失败')
    message.success('对象存储配置已删除')
    await load()
  } })
}
watch(kind, () => { rows.value = []; total.value = 0; selected.value = undefined; resetFilters() }, { immediate: true })
</script>
<template>
  <StandardListLayout>
    <template v-if="isLogs || kind === 'files' || kind === 'online' || kind === 'dictionary' || kind === 'menu-directory' || kind === 'clients' || kind === 'storage-config' || (kind === 'parameters' && parameterView === 'framework')" #filters>
      <label v-if="kind === 'files'" class="zy-filter-field"><span>文件名</span><a-input v-model:value="fileName" allow-clear placeholder="搜索原文件名" style="width: 240px" @press-enter="fileFilter = fileName.trim(); search()" /></label>
      <template v-else-if="kind === 'online'">
        <label class="zy-filter-field"><span>登录账号</span><a-input v-model:value="account" allow-clear placeholder="输入账号" style="width: 200px" @press-enter="load" /></label>
        <label class="zy-filter-field"><span>登录 IP</span><a-input v-model:value="ipAddress" allow-clear placeholder="输入 IP 地址" style="width: 180px" @press-enter="load" /></label>
      </template>
      <template v-else-if="kind === 'dictionary'">
        <label class="zy-filter-field"><span>字典名称</span><a-input v-model:value="dictionaryName" allow-clear placeholder="输入字典名称" style="width: 200px" @press-enter="page=1;load()" /></label>
        <label class="zy-filter-field"><span>字典类型</span><a-input v-model:value="dictionaryType" allow-clear placeholder="输入字典类型" style="width: 200px" @press-enter="page=1;load()" /></label>
      </template>
      <template v-else-if="kind === 'menu-directory'">
        <label class="zy-filter-field"><span>菜单名称</span><a-input v-model:value="dictionaryName" allow-clear placeholder="输入菜单名称" style="width: 220px" @press-enter="page=1;load()" /></label>
        <label class="zy-filter-field"><span>状态</span><a-select v-model:value="outcome" allow-clear placeholder="全部状态" style="width: 130px" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" /></label>
      </template>
      <template v-else-if="kind === 'clients' || kind === 'storage-config'">
        <label class="zy-filter-field"><span>{{ kind==='clients'?'客户端 Key':'配置标识' }}</span><a-input v-model:value="recordKeyword" allow-clear :placeholder="kind==='clients'?'输入客户端 Key':'输入对象存储配置标识'" style="width:240px" @press-enter="page=1;load()" /></label>
      </template>
      <label v-else-if="kind==='parameters' && parameterView==='framework'" class="zy-filter-field"><span>参数键</span><a-input v-model:value="recordKeyword" allow-clear placeholder="输入参数键" style="width:260px" @press-enter="load" /></label>
      <template v-else>
      <div class="head-filters">
      <a-radio-group v-if="props.kind === 'logs'" v-model:value="category">
        <a-radio-button v-for="item in categories" :key="item.key" :value="item.key">{{ item.label }}</a-radio-button>
      </a-radio-group>
      <label class="zy-filter-field"><span>{{ kind === 'operlog' ? '操作人' : '登录账号' }}</span><a-input v-model:value="account" allow-clear :placeholder="kind === 'operlog' ? '搜索操作人' : '搜索登录账号'"
        style="width: 200px" @press-enter="search">
        <template #prefix><SearchOutlined class="search-icon" /></template>
      </a-input></label>
      <label class="zy-filter-field"><span>结果</span><a-select v-model:value="outcome" allow-clear placeholder="结果" style="width: 110px"
        :options="[{ label: '成功', value: '0' }, { label: '失败', value: '1' }]" /></label>
      </div>
      </template>
    </template>
    <template #actions>
      <div v-if="isLogs" class="filter-bar">
        <a-button type="primary" :loading="loading" @click="search">查询</a-button>
        <a-button @click="resetFilters">重置</a-button>
        <a-button v-if="can(kind === 'loginInfo' ? 'monitor:logininfo:export' : 'monitor:operlog:export')" @click="exportLogs">导出</a-button>
        <a-button v-if="can(kind === 'loginInfo' ? 'monitor:logininfo:remove' : 'monitor:operlog:remove')" :disabled="!logSelectedRowKeys.length" danger @click="deleteSelectedLogs">删除所选</a-button>
        <a-button v-if="can(kind === 'loginInfo' ? 'monitor:logininfo:remove' : 'monitor:operlog:remove')" danger @click="clearLogs">清空全部</a-button>
        <span class="filter-spacer" />
        <a-dropdown v-model:open="columnSettingsOpen" :trigger="['click']" placement="bottomRight">
          <a-button>⚙ 列设置</a-button>
          <template #overlay>
            <a-card class="col-set-panel" :body-style="{ padding: '8px' }">
              <div v-for="column in allColumns.filter(item => item.key !== 'auditDetail')" :key="column.key" class="col-set-item">
                <a-checkbox :checked="columns.some(item => item.key === column.key)" @change="toggleColumn(column.key)">{{ column.title }}</a-checkbox>
              </div>
              <a-divider style="margin: 6px 0" />
              <div class="col-set-foot">
                <a-button type="link" size="small" @click="delete hiddenColumns[kind]">恢复默认</a-button>
                <a-button type="primary" size="small" @click="columnSettingsOpen = false">完成</a-button>
              </div>
            </a-card>
          </template>
        </a-dropdown>
      </div>
      <div v-else-if="kind === 'files'" class="filter-bar">
        <template v-if="can('system:oss:upload')">
          <a-button type="primary" :loading="fileBusy" @click="filePicker?.click()">＋ 上传文件</a-button>
          <input ref="filePicker" type="file" multiple hidden @change="uploadFiles" />
        </template>
        <a-button type="primary" :loading="loading" @click="fileFilter = fileName.trim(); search()">查询</a-button>
        <a-button @click="fileName = ''; fileFilter = ''; search()">重置</a-button>
      </div>
      <div v-else-if="kind === 'online' || kind === 'dictionary' || kind === 'menu-directory' || kind === 'clients' || kind === 'storage-config'" class="filter-bar">
        <template v-if="kind === 'dictionary' && can('system:dict:add')"><a-button type="primary" @click="editDictionary()">新增字典类型</a-button></template>
        <a-button v-if="kind==='dictionary' && can('system:dict:export')" @click="exportFrameworkList('/system/dict/type/export','字典类型.xlsx',{dictName:dictionaryName||undefined,dictType:dictionaryType||undefined})">导出</a-button>
        <template v-if="kind === 'dictionary' && can('system:dict:remove')"><a-button @click="refreshDictionaryCache">刷新字典缓存</a-button></template>
        <template v-if="kind === 'menu-directory' && can('system:menu:add')"><a-button type="primary" @click="editMenu()">新增菜单</a-button><a-alert type="warning" show-icon message="菜单写操作由框架限定超级管理员；当前织云导航使用静态路由，新增菜单记录不会自动生成业务页面，请勿随意修改在线生产路由与权限标识。" /></template>
        <template v-if="kind === 'clients' && can('system:client:add')"><a-button type="primary" @click="editClient()">新增客户端</a-button></template>
        <a-button v-if="kind==='clients' && can('system:client:export')" @click="exportFrameworkList('/system/client/export','客户端配置.xlsx',{clientKey:recordKeyword||undefined})">导出</a-button>
        <template v-if="kind === 'storage-config' && can('system:ossConfig:add')"><a-button type="primary" @click="editStorageConfig()">新增存储配置</a-button></template>
        <a-button type="primary" :loading="loading" @click="kind==='online' ? load() : (page=1,load())">查询</a-button>
        <a-button @click="kind==='online' ? resetOnlineFilters() : kind==='dictionary' ? resetDictionaryFilters() : kind==='menu-directory' ? (dictionaryName='',outcome=undefined,page=1,load()) : (recordKeyword='',page=1,load())">重置</a-button>
      </div>
      <div v-else-if="kind === 'parameters'" class="parameter-actions">
        <div><h2>{{ parameterView==='business'?'业务参数':'框架参数' }}</h2><span>{{ parameterView==='business'?'配置织云业务模块的默认行为':'维护框架参数，修改可能影响系统运行' }}</span></div>
        <a-space><a-segmented v-model:value="parameterView" :options="[{label:'业务参数',value:'business'},{label:'框架参数',value:'framework'}]" @change="page=1;recordKeyword='';load()"/><template v-if="parameterView==='framework'"><a-button v-if="can('system:config:add')" type="primary" @click="addConfig">新增参数</a-button><a-button v-if="can('system:config:export')" @click="exportFrameworkList('/system/config/export','系统参数.xlsx',{configKey:recordKeyword||undefined})">导出</a-button><a-button v-if="can('system:config:remove')" @click="refreshConfigCache">刷新参数缓存</a-button><a-button type="primary" :loading="loading" @click="load">查询</a-button><a-button @click="recordKeyword='';page=1;load()">重置</a-button></template><a-button :loading="loading" @click="load">刷新设置</a-button></a-space>
      </div>
      <a-button v-else type="primary" :loading="loading" @click="load">刷新</a-button>
    </template>
    <section v-if="kind === 'parameters' && parameterView === 'business'" class="settings-page" :aria-busy="loading">
      <div class="settings-section-title">业务规则</div>
      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>数字显示精度</h3><p>数量跟随计量单位。下列位数控制只读显示，编辑与结算使用原值。</p></div></div>
        <div class="setting-controls"><a-form-item v-for="(label,key) in precisionLabels" :key="key" :label="label">
          <a-input-number v-model:value="displayRules[key]" :min="0" :max="8" :precision="0" :disabled="!editingDisplay"/>
          <small> 示例 {{ displayDecimal('1.23456789',displayRules[key] ?? 0) }}</small>
        </a-form-item></div>
        <div class="setting-footer"><a-space v-if="can('system:config:edit')">
          <a-button v-if="editingDisplay" @click="editingDisplay=false;syncCodeRules(rows)">取消</a-button>
          <a-button v-if="editingDisplay" type="primary" :loading="savingDisplay" @click="saveDisplayRules">保存</a-button>
          <a-button v-else type="primary" @click="editingDisplay=true">修改规则</a-button>
        </a-space></div>
      </a-card>
      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>客户订单默认下线日期</h3><p>根据 PO 默认交期提前自然日生成下线日期；订单中可自行修改，已保存日期不会因参数变化而改变。</p></div></div>
        <div class="setting-controls"><a-form-item label="比默认交期提前"><a-input-number v-model:value="offlineLeadDays" :min="0" :max="3650" :precision="0" :disabled="!offlineLeadEditing" /> 天</a-form-item></div>
        <div class="setting-footer"><span>0 天表示建议与交期同一天。</span><a-space v-if="can('system:config:edit')"><a-button v-if="offlineLeadEditing" @click="offlineLeadEditing=false; syncCodeRules(rows)">取消</a-button><a-button v-if="offlineLeadEditing" type="primary" :loading="offlineLeadSaving" @click="saveOfflineLeadDays">保存规则</a-button><a-button v-else type="primary" @click="offlineLeadEditing=true">修改规则</a-button></a-space></div>
      </a-card>
      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading">
          <div><h3>尺码自动编码</h3><p>设置新增尺码时使用的编码前缀与数字位数。已有尺码编码不受影响。</p></div>
          <a-tag v-if="sizeRuleRow" color="blue">系统内置</a-tag>
        </div>
        <a-spin :spinning="loading">
          <a-empty v-if="!loading && !sizeRuleRow" description="未找到尺码自动编码规则" />
          <template v-else-if="sizeRuleRow">
            <div class="setting-controls">
              <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="autoCodeRule.enabled" :disabled="!autoCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
              <a-form-item label="编码前缀"><a-input v-model:value="autoCodeRule.prefix" :disabled="!autoCodeEditing" :maxlength="30" placeholder="例如 SZ" /></a-form-item>
              <a-form-item label="数字位数"><a-input-number v-model:value="autoCodeRule.digits" :disabled="!autoCodeEditing" :min="1" :max="10" :precision="0" /></a-form-item>
              <div class="setting-example">生成示例：<strong>{{ autoCodeRule.prefix || 'SZ' }}{{ '0'.repeat(Math.max(0, autoCodeRule.digits - 1)) }}1</strong></div>
            </div>
            <div class="setting-footer"><span>关闭后，新增尺码需手动填写编码。</span><a-space v-if="can('system:config:edit')"><a-button v-if="autoCodeEditing" @click="cancelAutoCodeEdit">取消</a-button><a-button v-if="autoCodeEditing" type="primary" :loading="autoCodeSaving" @click="saveAutoCode">保存规则</a-button><a-button v-else type="primary" @click="openAutoCode">修改规则</a-button></a-space></div>
          </template>
        </a-spin>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>颜色自动编码</h3><p>配置颜色基础资料的编码建议；自动生成值始终可以在颜色档案中手动修改。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <a-spin :spinning="loading">
          <a-empty v-if="!loading && !colorCodeRuleRow" description="未找到颜色自动编码规则，请先执行颜色管理数据库脚本" />
          <template v-else-if="colorCodeRuleRow">
            <div class="setting-controls">
              <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="colorCodeRule.enabled" :disabled="!colorCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
              <a-form-item label="编码前缀"><a-input v-model:value="colorCodeRule.prefix" :disabled="!colorCodeEditing" :maxlength="30" placeholder="例如 CLR" /></a-form-item>
              <a-form-item label="序号位数"><a-input-number v-model:value="colorCodeRule.digits" :disabled="!colorCodeEditing" :min="1" :max="10" :precision="0" /></a-form-item>
              <div class="setting-example">生成示例：<strong>{{ colorCodeRule.prefix || 'CLR' }}{{ '0'.repeat(Math.max(0, colorCodeRule.digits - 1)) }}1</strong></div>
            </div>
            <div class="setting-footer"><span>{{ colorCodeRule.enabled ? '新增颜色时显示可编辑建议编码；关闭后需手动填写。' : '自动生成已关闭，新增颜色时须手动填写编码。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="colorCodeEditing" @click="cancelColorCodeEdit">取消</a-button><a-button v-if="colorCodeEditing" type="primary" :loading="colorCodeSaving" @click="saveColorCode">保存规则</a-button><a-button v-else type="primary" @click="openColorCode">修改规则</a-button></a-space></div>
          </template>
        </a-spin>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>物料自动编码</h3><p>按统一前缀和流水号生成物料编码建议；已有编码不重编，新增时用户仍可编辑建议值。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <a-spin :spinning="loading">
          <a-empty v-if="!loading && !materialCodeRuleRow" description="未找到物料自动编码规则，请先执行物料档案数据库脚本" />
          <template v-else-if="materialCodeRuleRow">
            <div class="setting-controls">
              <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="materialCodeRule.enabled" :disabled="!materialCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
              <a-form-item label="编码前缀"><a-input v-model:value="materialCodeRule.prefix" :disabled="!materialCodeEditing" :maxlength="30" placeholder="例如 MAT" /></a-form-item>
              <a-form-item label="序号位数"><a-input-number v-model:value="materialCodeRule.digits" :disabled="!materialCodeEditing" :min="1" :max="10" :precision="0" /></a-form-item>
              <div class="setting-example">生成示例：<strong>{{ materialCodeRule.prefix || 'MAT' }}{{ '0'.repeat(Math.max(0, materialCodeRule.digits - 1)) }}1</strong></div>
            </div>
            <div class="setting-footer"><span>{{ materialCodeRule.enabled ? '新增物料时显示可编辑建议编码；关闭后必须手动填写。' : '自动生成已关闭，新增物料时必须手动填写编码。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="materialCodeEditing" @click="cancelMaterialCodeEdit">取消</a-button><a-button v-if="materialCodeEditing" type="primary" :loading="materialCodeSaving" @click="saveMaterialCodeRule">保存规则</a-button><a-button v-else type="primary" @click="openMaterialCodeEdit">修改规则</a-button></a-space></div>
          </template>
        </a-spin>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>物料默认分类类型</h3><p>配置后，物料档案的分类树、列表和表单只显示所设类型。留空时可从全部授权类型中选择。</p></div><a-tag color="blue">业务参数</a-tag></div>
        <div class="setting-controls">
          <a-form-item label="默认分类类型"><a-select v-model:value="materialDefaultSchemeId" :disabled="!materialDefaultEditing" allow-clear placeholder="全部授权分类" :options="materialDefaultSchemes" /></a-form-item>
        </div>
        <div class="setting-footer"><span>{{ materialDefaultSchemeId ? '当前仅显示所选类型及其授权分类。' : '未配置默认类型时，物料档案展示当前人员有权查看的全部分类。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="materialDefaultEditing" @click="cancelMaterialDefaultCategory">取消</a-button><a-button v-if="materialDefaultEditing" type="primary" :loading="materialDefaultSaving" @click="saveMaterialDefaultCategory">保存参数</a-button><a-button v-else type="primary" @click="openMaterialDefaultCategory">修改参数</a-button></a-space></div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>款式默认分类类型</h3><p>配置后，款式档案的分类树、列表和表单只显示所设类型。留空时可从全部授权类型中选择。</p></div><a-tag color="blue">业务参数</a-tag></div>
        <div class="setting-controls"><a-form-item label="默认分类类型"><a-select v-model:value="styleDefaultSchemeId" :disabled="!styleDefaultEditing" allow-clear placeholder="全部授权分类" :options="materialDefaultSchemes" /></a-form-item></div>
        <div class="setting-footer"><span>{{ styleDefaultSchemeId ? '当前仅显示所选类型及其授权分类。' : '未配置时展示当前人员有权查看的全部分类。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="styleDefaultEditing" @click="cancelStyleDefaultCategory">取消</a-button><a-button v-if="styleDefaultEditing" type="primary" :loading="styleDefaultSaving" @click="saveStyleDefaultCategory">保存参数</a-button><a-button v-else type="primary" @click="openStyleDefaultCategory">修改参数</a-button></a-space></div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>客户默认分类类型</h3><p>打开客户档案时优先定位到所设类型。</p></div><a-tag color="blue">业务参数</a-tag></div>
        <div class="setting-controls"><a-form-item label="默认分类类型"><a-select v-model:value="customerDefaultSchemeId" :disabled="!customerDefaultEditing" allow-clear placeholder="全部授权分类" :options="materialDefaultSchemes" /></a-form-item></div>
        <div class="setting-footer"><span>{{ customerDefaultSchemeId ? '打开客户档案时默认筛选此类型。' : '未配置时打开全部客户。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="customerDefaultEditing" @click="cancelCustomerDefaultCategory">取消</a-button><a-button v-if="customerDefaultEditing" type="primary" :loading="customerDefaultSaving" @click="saveCustomerDefaultCategory">保存参数</a-button><a-button v-else type="primary" @click="openCustomerDefaultCategory">修改参数</a-button></a-space></div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading">
          <div><h3>尺码组自动编码</h3><p>设置新增尺码组使用的编号前缀与数字位数。已有尺码组编号不受影响。</p></div>
          <a-tag color="blue">系统内置</a-tag>
        </div>
        <a-spin :spinning="loading">
          <div class="setting-controls">
            <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="sizeGroupCodeRule.enabled" :disabled="!sizeGroupCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
            <a-form-item label="编码前缀"><a-input v-model:value="sizeGroupCodeRule.prefix" :disabled="!sizeGroupCodeEditing" :maxlength="30" placeholder="例如 SG" /></a-form-item>
            <a-form-item label="数字位数"><a-input-number v-model:value="sizeGroupCodeRule.digits" :disabled="!sizeGroupCodeEditing" :min="1" :max="10" :precision="0" /></a-form-item>
            <div class="setting-example">生成示例：<strong>{{ sizeGroupCodeRule.prefix || 'SG' }}{{ '0'.repeat(Math.max(0, sizeGroupCodeRule.digits - 1)) }}1</strong></div>
          </div>
          <div class="setting-footer"><span>{{ sizeGroupRuleRow ? '关闭后，新增尺码组需手动填写编号。' : '尚未保存规则，保存后将创建系统参数。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="sizeGroupCodeEditing" @click="cancelSizeGroupCodeEdit">取消</a-button><a-button v-if="sizeGroupCodeEditing" type="primary" :loading="sizeGroupCodeSaving" @click="saveSizeGroupCode">保存规则</a-button><a-button v-else type="primary" @click="openSizeGroupCode">修改规则</a-button></a-space></div>
        </a-spin>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>仓库编码自动生成</h3><p>新增仓库时按统一前缀和序号生成建议编码，已有仓库编码不自动重编。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <div class="setting-controls">
          <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="warehouseCodeRule.enabled" :disabled="!warehouseCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
          <a-form-item label="仓库编码前缀"><a-input v-model:value="warehouseCodeRule.prefix" :disabled="!warehouseCodeEditing" :maxlength="12" placeholder="例如 WH" /></a-form-item>
          <a-form-item label="序号位数"><a-input-number v-model:value="warehouseCodeRule.digits" :disabled="!warehouseCodeEditing" :min="1" :max="10" :precision="0" /></a-form-item>
          <div class="setting-example">生成示例：<strong>{{ warehouseCodeRule.prefix || 'WH' }}{{ '0'.repeat(Math.max(0, warehouseCodeRule.digits - 1)) }}1</strong></div>
        </div>
        <div class="setting-footer"><span>{{ warehouseCodeRule.enabled ? '新增时自动带出可编辑的建议编码；关闭后需手动填写。' : '自动生成已关闭，新增时须手动填写编码。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="warehouseCodeEditing" @click="cancelWarehouseCodeEdit">取消</a-button><a-button v-if="warehouseCodeEditing" type="primary" :loading="warehouseCodeSaving" @click="saveWarehouseCodeRule">保存规则</a-button><a-button v-else type="primary" @click="openWarehouseCodeEdit">修改规则</a-button></a-space></div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>仓库布局层级编码</h3><p>按仓库、库区、货架、货位路径生成编码。已有档案编码不重编，新增时按所选上级分配下一个序号。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <div class="setting-controls">
          <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="layoutCodeRule.enabled" :disabled="!layoutCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
          <a-form-item label="库区前缀"><a-input v-model:value="layoutCodeRule.zonePrefix" :disabled="!layoutCodeEditing" :maxlength="8" /></a-form-item>
          <a-form-item label="库区序号位数"><a-input-number v-model:value="layoutCodeRule.zoneDigits" :disabled="!layoutCodeEditing" :min="1" :max="6" :precision="0" /></a-form-item>
          <a-form-item label="货架前缀"><a-input v-model:value="layoutCodeRule.rackPrefix" :disabled="!layoutCodeEditing" :maxlength="8" /></a-form-item>
          <a-form-item label="货架序号位数"><a-input-number v-model:value="layoutCodeRule.rackDigits" :disabled="!layoutCodeEditing" :min="1" :max="6" :precision="0" /></a-form-item>
          <a-form-item label="货位前缀"><a-input v-model:value="layoutCodeRule.binPrefix" :disabled="!layoutCodeEditing" :maxlength="8" /></a-form-item>
          <a-form-item label="货位序号位数"><a-input-number v-model:value="layoutCodeRule.binDigits" :disabled="!layoutCodeEditing" :min="1" :max="6" :precision="0" /></a-form-item>
          <div class="setting-example">生成示例：<strong>WH01-{{ layoutCodeRule.zonePrefix }}{{ '0'.repeat(Math.max(0, layoutCodeRule.zoneDigits - 1)) }}1-{{ layoutCodeRule.rackPrefix }}{{ '0'.repeat(Math.max(0, layoutCodeRule.rackDigits - 1)) }}1-{{ layoutCodeRule.binPrefix }}{{ '0'.repeat(Math.max(0, layoutCodeRule.binDigits - 1)) }}1</strong></div>
        </div>
        <div class="setting-footer"><span>{{ layoutCodeRule.enabled ? '关闭自动生成后，新增时须手动填写编码。仓库编码和已建档位置编码不可修改。' : '自动生成已关闭，新增时须手动填写编码。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="layoutCodeEditing" @click="cancelLayoutCodeEdit">取消</a-button><a-button v-if="layoutCodeEditing" type="primary" :loading="layoutCodeSaving" @click="saveLayoutCodeRule">保存规则</a-button><a-button v-else type="primary" @click="openLayoutCodeEdit">修改规则</a-button></a-space></div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>分类编码自动生成</h3><p>分类类型编码作为前缀，每个类型独立分配序号；例如类型编码01生成01-0001。已有分类编码不自动重编，新增时仍可手动修改建议值。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <div class="setting-controls">
          <div class="setting-control"><span>自动生成</span><a-switch v-model:checked="categoryCodeRule.enabled" :disabled="!categoryCodeEditing" checked-children="开启" un-checked-children="关闭" /></div>
          <a-form-item label="类型编码与序号的分隔符"><a-select v-model:value="categoryCodeRule.separator" :disabled="!categoryCodeEditing" :options="[{label:'短横线（-）',value:'-'},{label:'下划线（_）',value:'_'},{label:'点号（.）',value:'.'},{label:'不使用分隔符',value:''}]" /></a-form-item>
          <a-form-item label="序号位数"><a-input-number v-model:value="categoryCodeRule.digits" :disabled="!categoryCodeEditing" :min="1" :max="8" :precision="0" /></a-form-item>
          <div class="setting-example">生成示例：类型编码 <strong>01</strong> → <strong>{{ categoryCodeExample }}</strong></div>
        </div>
        <div class="setting-footer"><span>{{ categoryCodeRule.enabled ? (categoryCodeRuleRow ? '新增时按所选分类类型自动带出可编辑建议值；保存时按当前占用情况重新分配。' : '尚未保存规则；默认开启类型编码关联生成。') : '自动生成已关闭，新增分类时须手动填写编码。' }}</span><a-space v-if="can('system:config:edit')"><a-button v-if="categoryCodeEditing" @click="cancelCategoryCodeEdit">取消</a-button><a-button v-if="categoryCodeEditing" type="primary" :loading="categoryCodeSaving" @click="saveCategoryCodeRule">保存规则</a-button><a-button v-else type="primary" @click="categoryCodeEditing=true">修改规则</a-button></a-space></div>
      </a-card>

      <div class="settings-section-title">系统功能</div>
      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>OSS 预览列表资源</h3><p>控制文件选择器是否展示图片预览资源。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <div class="setting-footer"><a-tag :color="ossPreviewRow?.configValue === 'true' ? 'green' : 'default'">{{ ossPreviewRow?.configValue === 'true' ? '已开启' : '已关闭' }}</a-tag><a-button v-if="ossPreviewRow && can('system:config:edit')" type="link" @click="editConfig(ossPreviewRow)">编辑</a-button></div>
      </a-card>
    </section>
    <a-table v-else class="zy-fill-table" :columns="columns" :data-source="rows" :loading="loading" row-key="id" :row-selection="isLogs ? logRowSelection : undefined"
      :size="isLogs ? 'middle' : undefined" :table-layout="isLogs ? 'fixed' : undefined"
      :scroll="isLogs ? { x: columns.reduce((sum, column) => sum + (column.width ?? 110), 0), y: 'calc(100vh - 390px)' } : { y: 'calc(100vh - 330px)' }"
      :pagination="{ current: page, pageSize: isLogs ? pageSize : kind==='parameters' && parameterView==='framework' ? 100 : 20, total, showSizeChanger: isLogs, showQuickJumper: isLogs, showTotal: isLogs ? (count: number) => `共 ${count} 条` : undefined, onChange: (value: number, size: number) => { page = value; pageSize = size; load() } }">
      <template #bodyCell="{ column, text, record, index }">
        <template v-if="column.key === 'fileAction' && can('system:oss:download') && !String(record.originalName).startsWith('.')">
          <a-button v-if="/^\.?(png|jpe?g|gif|webp)$/i.test(String(record.fileSuffix))" type="link" @click="downloadFile(record, true)">预览</a-button>
          <a-button type="link" @click="downloadFile(record)">下载</a-button>
        </template>
        <template v-else-if="column.key === 'sequence'">{{ (page - 1) * pageSize + index + 1 }}</template>
        <template v-else-if="column.key === 'action' && kind==='parameters' && parameterView==='framework'"><a-button v-if="can('system:config:edit')" type="link" size="small" @click="editConfig(record)">编辑</a-button><a-button v-if="can('system:config:remove')" type="link" danger size="small" @click="removeConfig(record)">删除</a-button></template>
        <a-button v-else-if="column.key === 'action' && can('system:config:edit') && record.configKey === 'sys.oss.previewListResource'" type="link" @click="editConfig(record)">编辑</a-button>
        <template v-else-if="column.key === 'logAction'"><a-button v-if="kind==='loginInfo' && can('monitor:logininfo:unlock') && String(record.status)!=='0'" type="link" size="small" @click="unlockAccount(record)">解锁账号</a-button></template>
        <template v-else-if="column.key === 'onlineAction'"><a-button v-if="can('monitor:online:forceLogout')" type="link" danger size="small" @click="forceLogout(record)">强制下线</a-button></template>
        <template v-else-if="column.key === 'dictionaryAction'"><a-button type="link" size="small" @click="openDictionaryItems(record)">字典数据</a-button><a-button v-if="can('system:dict:edit')" type="link" size="small" @click="editDictionary(record)">编辑</a-button><a-button v-if="can('system:dict:remove')" type="link" danger size="small" @click="removeDictionary(record)">删除</a-button></template>
        <template v-else-if="column.key === 'menuType'"><a-tag>{{ ({M:'目录',C:'菜单',F:'按钮'} as Record<string,string>)[String(text)] || text || '—' }}</a-tag></template>
        <template v-else-if="column.key === 'menuAction'"><a-button v-if="can('system:menu:edit')" type="link" size="small" @click="editMenu(record)">编辑</a-button><a-button v-if="can('system:menu:remove')" type="link" danger size="small" @click="removeMenu(record)">删除</a-button></template>
        <template v-else-if="column.key === 'clientAction'"><a-button v-if="can('system:client:edit')" type="link" size="small" @click="editClient(record)">编辑</a-button><a-button v-if="can('system:client:edit')" type="link" size="small" @click="changeClientStatus(record)">{{ String(record.status)==='0'?'停用':'启用' }}</a-button><a-button v-if="can('system:client:remove')" type="link" danger size="small" @click="removeClient(record)">删除</a-button></template>
        <template v-else-if="column.key === 'storageAction'"><a-button v-if="can('system:ossConfig:edit')" type="link" size="small" @click="editStorageConfig(record)">编辑</a-button><a-button v-if="can('system:ossConfig:edit') && record.status!=='Y'" type="link" size="small" @click="changeStorageStatus(record)">设为默认</a-button><a-tag v-else-if="record.status==='Y'" color="green">当前默认</a-tag><a-button v-if="can('system:ossConfig:remove')" type="link" danger size="small" @click="removeStorageConfig(record)">删除</a-button></template>
        <a-button v-else-if="column.key === 'auditDetail'" type="link" @click="selected = record">查看</a-button>
        <template v-else-if="column.key === 'status' && ['operlog', 'loginInfo'].includes(kind)"><a-tag :color="String(text) === '0' ? 'green' : 'red'">{{ String(text) === '0' ? '成功' : '失败' }}</a-tag></template>
        <template v-else-if="column.key === 'loginTime' && typeof text === 'number'">{{ new Date(text).toLocaleString('zh-CN') }}</template>
        <template v-else-if="column.key === 'status' && kind==='clients'"><a-tag :color="String(text)==='0'?'green':'red'">{{ String(text)==='0'?'启用':'停用' }}</a-tag></template>
        <template v-else-if="column.key === 'status' && kind==='storage-config'"><a-tag :color="text==='Y'?'green':'default'">{{ text==='Y'?'默认':'未启用' }}</a-tag></template>
      </template>
    </a-table>
    <a-modal v-if="kind === 'files'" :open="!!previewUrl" title="图片预览" :footer="null" @cancel="closePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="文件预览" style="display: block; max-width: 100%; max-height: 70vh; margin: auto" />
    </a-modal>
    <a-modal v-if="kind === 'parameters'" v-model:open="configDialog" :title="configEditing ? '编辑系统参数' : '新增系统参数'" :confirm-loading="configSaving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="saveConfig">
      <a-form layout="vertical">
        <a-form-item label="参数名称"><a-input v-model:value="editingConfig.configName" /></a-form-item>
        <a-form-item label="参数键"><a-input v-model:value="editingConfig.configKey" :disabled="configEditing" /></a-form-item>
        <a-form-item label="参数值"><a-switch v-if="editingConfig.configKey==='sys.oss.previewListResource'" :checked="editingConfig.configValue === 'true'" checked-children="开启" un-checked-children="关闭" @change="editingConfig.configValue = $event ? 'true' : 'false'" /><a-textarea v-else v-model:value="editingConfig.configValue" :rows="4" /></a-form-item>
        <a-form-item label="参数来源"><a-select v-model:value="editingConfig.configType" :options="[{value:'Y',label:'系统内置'},{value:'N',label:'用户自定义'}]" /></a-form-item>
        <a-form-item label="说明"><a-input v-model:value="editingConfig.remark" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-if="kind === 'dictionary'" v-model:open="dictionaryTypeModalOpen" :title="dictionaryEditing ? '编辑字典类型' : '新增字典类型'" :confirm-loading="dictionarySaving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="saveDictionary">
      <a-form layout="vertical"><a-form-item label="字典名称" required><a-input v-model:value="dictionaryForm.dictName" :maxlength="100" placeholder="例如：订单状态" /></a-form-item><a-form-item label="字典类型" required><a-input v-model:value="dictionaryForm.dictType" :disabled="dictionaryEditing" :maxlength="100" placeholder="例如：order_status（小写字母、数字、下划线）" /></a-form-item><a-form-item label="备注"><a-textarea v-model:value="dictionaryForm.remark" :rows="3" /></a-form-item></a-form>
    </a-modal>
    <a-drawer v-if="kind === 'dictionary'" :open="dictionaryDrawerOpen" :title="`字典数据：${selectedDictionary?.dictName || ''}`" width="760" @close="dictionaryDrawerOpen=false">
      <div class="dict-drawer-tools"><span>字典类型：<a-tag color="blue">{{ selectedDictionary?.dictType }}</a-tag></span><a-space><a-button v-if="can('system:dict:export')" @click="exportFrameworkList('/system/dict/data/export',`${selectedDictionary?.dictName || '字典数据'}.xlsx`,{dictType:selectedDictionary?.dictType})">导出数据</a-button><a-button v-if="can('system:dict:add')" type="primary" @click="editDictionaryItem()">新增数据项</a-button></a-space></div>
      <a-table :columns="[{title:'排序',dataIndex:'dictSort',width:70},{title:'标签',dataIndex:'dictLabel',width:140},{title:'键值',dataIndex:'dictValue',width:120},{title:'默认',dataIndex:'isDefault',width:70},{title:'备注',dataIndex:'remark'},{title:'操作',key:'itemAction',width:110}]" :data-source="dictionaryItems" :loading="dictionaryItemsLoading" row-key="dictCode" :pagination="false" size="small">
        <template #bodyCell="{column,record}"><a-tag v-if="columnField(column)==='isDefault'" :color="record.isDefault==='Y'?'blue':'default'">{{ record.isDefault==='Y'?'是':'否' }}</a-tag><span v-else-if="column.key==='itemAction'"><a-button v-if="can('system:dict:edit')" type="link" size="small" @click="editDictionaryItem(record)">编辑</a-button><a-button v-if="can('system:dict:remove')" type="link" danger size="small" @click="removeDictionaryItem(record)">删除</a-button></span><span v-else>{{ record[columnField(column)]||'—' }}</span></template>
        <template #emptyText><a-empty description="暂无字典数据" /></template>
      </a-table>
    </a-drawer>
    <a-modal v-if="kind === 'dictionary'" v-model:open="dictionaryItemModalOpen" :title="dictionaryEditing ? '编辑字典数据' : '新增字典数据'" :confirm-loading="dictionarySaving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="saveDictionaryItem">
      <a-form layout="vertical"><div class="dict-form-grid"><a-form-item label="字典标签" required><a-input v-model:value="dictionaryItemForm.dictLabel" :maxlength="100" /></a-form-item><a-form-item label="字典键值" required><a-input v-model:value="dictionaryItemForm.dictValue" :maxlength="100" /></a-form-item><a-form-item label="排序"><a-input-number v-model:value="dictionaryItemForm.dictSort" :min="0" :precision="0" style="width:100%" /></a-form-item><a-form-item label="默认项"><a-switch :checked="dictionaryItemForm.isDefault==='Y'" checked-children="是" un-checked-children="否" @change="dictionaryItemForm.isDefault=$event?'Y':'N'" /></a-form-item><a-form-item label="列表样式"><a-select v-model:value="dictionaryItemForm.listClass" :options="['default','primary','success','info','warning','danger'].map(value=>({value,label:value}))" /></a-form-item><a-form-item label="样式类名"><a-input v-model:value="dictionaryItemForm.cssClass" /></a-form-item></div><a-form-item label="备注"><a-textarea v-model:value="dictionaryItemForm.remark" :rows="2" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-if="kind === 'menu-directory'" v-model:open="menuModalOpen" :title="menuEditing ? '编辑菜单权限' : '新增菜单权限'" width="760px" :confirm-loading="menuSaving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="saveMenu">
      <a-alert type="warning" show-icon message="菜单路由和权限标识会影响框架入口及角色授权；织云当前使用静态前端路由，新增菜单记录不会自动生成页面。框架接口要求超级管理员角色，请谨慎维护。" style="margin-bottom:16px" />
      <a-form layout="vertical"><div class="dict-form-grid">
        <a-form-item label="菜单类型" required><a-select v-model:value="menuForm.menuType" :options="[{value:'M',label:'目录'},{value:'C',label:'菜单页面'},{value:'F',label:'按钮权限'}]" /></a-form-item>
        <a-form-item label="上级菜单"><a-select v-model:value="menuForm.parentId" show-search option-filter-prop="label" :options="[{value:0,label:'顶级目录'},...rows.filter(row=>row.menuType!=='F' && String(row.menuId)!==String(menuForm.menuId)).map(row=>({value:menuOptionValue(row),label:`${row.parentName ? row.parentName+' / ' : ''}${row.menuName}`}))]" /></a-form-item>
        <a-form-item label="菜单名称" required><a-input v-model:value="menuForm.menuName" :maxlength="50" /></a-form-item>
        <a-form-item label="显示顺序" required><a-input-number v-model:value="menuForm.orderNum" :min="0" :precision="0" style="width:100%" /></a-form-item>
        <template v-if="menuForm.menuType!=='F'"><a-form-item label="路由地址"><a-input v-model:value="menuForm.path" :maxlength="200" /></a-form-item><a-form-item label="组件路径"><a-input v-model:value="menuForm.component" :maxlength="200" /></a-form-item></template>
        <a-form-item label="权限标识"><a-input v-model:value="menuForm.perms" :maxlength="100" placeholder="例如 system:example:list" /></a-form-item>
        <a-form-item label="图标"><a-input v-model:value="menuForm.icon" placeholder="Ant Design 图标名" /></a-form-item>
        <a-form-item label="状态"><a-select v-model:value="menuForm.status" :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" /></a-form-item>
        <a-form-item label="显示"><a-select v-model:value="menuForm.visible" :options="[{value:'0',label:'显示'},{value:'1',label:'隐藏'}]" /></a-form-item>
        <a-form-item label="外链"><a-select v-model:value="menuForm.isFrame" :options="[{value:'N',label:'否'},{value:'Y',label:'是'}]" /></a-form-item>
        <a-form-item label="缓存页面"><a-select v-model:value="menuForm.isCache" :options="[{value:'Y',label:'是'},{value:'N',label:'否'}]" /></a-form-item>
        <a-form-item label="激活菜单路径"><a-input v-model:value="menuForm.activeMenu" /></a-form-item>
        <a-form-item label="路由参数 JSON"><a-input v-model:value="menuForm.queryParam" placeholder="留空或填写合法 JSON 对象" /></a-form-item>
      </div><a-form-item label="备注"><a-textarea v-model:value="menuForm.remark" :rows="2" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-if="kind === 'clients'" v-model:open="clientModalOpen" :title="clientEditing ? '编辑客户端' : '新增客户端'" width="760px" :confirm-loading="clientSaving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="saveClient">
      <a-alert type="warning" show-icon message="客户端密钥属于认证凭证。保存或轮换密钥后，依赖该客户端的应用需要同步更新配置。" style="margin-bottom:16px" />
      <a-form layout="vertical"><div class="dict-form-grid">
        <a-form-item label="客户端 Key" required><a-input v-model:value="clientForm.clientKey" :maxlength="100" /></a-form-item>
        <a-form-item label="客户端 ID"><a-input v-model:value="clientForm.clientId" :disabled="clientEditing" placeholder="新增时可留空，由框架生成" /></a-form-item>
        <a-form-item label="客户端密钥" required><a-input-password v-model:value="clientForm.clientSecret" autocomplete="new-password" /></a-form-item>
        <a-form-item label="设备类型"><a-select v-model:value="clientForm.deviceType" :options="[{value:'pc',label:'PC'},{value:'android',label:'安卓'},{value:'ios',label:'iOS'},{value:'xcx',label:'小程序'}]" /></a-form-item>
        <a-form-item label="授权方式" required><a-select v-model:value="clientForm.grantTypeList" mode="multiple" :options="[{value:'password',label:'密码认证'},{value:'sms',label:'短信认证'},{value:'email',label:'邮件认证'},{value:'xcx',label:'小程序认证'},{value:'social',label:'三方登录认证'}]" /></a-form-item>
        <a-form-item label="状态"><a-select v-model:value="clientForm.status" :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" /></a-form-item>
        <a-form-item label="Token 活跃超时（秒）"><a-input-number v-model:value="clientForm.activeTimeout" :min="-1" style="width:100%" /></a-form-item>
        <a-form-item label="Token 固定超时（秒）"><a-input-number v-model:value="clientForm.timeout" :min="-1" style="width:100%" /></a-form-item>
        <a-form-item label="允许访问路径"><a-select v-model:value="clientForm.accessPathList" mode="tags" :token-separators="[',']" placeholder="输入路径后回车；留空按框架规则" /></a-form-item>
        <a-form-item label="IP 白名单"><a-select v-model:value="clientForm.ipWhitelistList" mode="tags" :token-separators="[',']" placeholder="输入 IP 或网段后回车" /></a-form-item>
      </div></a-form>
    </a-modal>
    <a-modal v-if="kind === 'storage-config'" v-model:open="storageModalOpen" :title="storageEditing ? '编辑对象存储配置' : '新增对象存储配置'" width="820px" :confirm-loading="storageSaving" :mask-closable="false" :keyboard="false" ok-text="保存配置" cancel-text="取消" @ok="saveStorageConfig">
      <a-alert type="warning" show-icon message="此处是文件存储连接凭证。设为默认会影响新上传文件；删除前框架会检查配置引用情况。" style="margin-bottom:16px" />
      <a-form layout="vertical"><div class="dict-form-grid">
        <a-form-item label="配置标识" required><a-input v-model:value="storageForm.configKey" :maxlength="100" /></a-form-item>
        <a-form-item label="存储桶名称" required><a-input v-model:value="storageForm.bucketName" :maxlength="100" /></a-form-item>
        <a-form-item label="访问密钥" required><a-input-password v-model:value="storageForm.accessKey" autocomplete="off" /></a-form-item>
        <a-form-item label="私钥" required><a-input-password v-model:value="storageForm.secretKey" autocomplete="new-password" /></a-form-item>
        <a-form-item label="访问站点" required><a-input v-model:value="storageForm.endpoint" placeholder="填写对象存储服务 Endpoint" /></a-form-item>
        <a-form-item label="区域"><a-input v-model:value="storageForm.region" /></a-form-item>
        <a-form-item label="对象前缀"><a-input v-model:value="storageForm.prefix" /></a-form-item>
        <a-form-item label="自定义域名"><a-input v-model:value="storageForm.domainUrl" /></a-form-item>
        <a-form-item label="连接使用 HTTPS"><a-select v-model:value="storageForm.isHttps" :options="[{value:'Y',label:'是'},{value:'N',label:'否'}]" /></a-form-item>
        <a-form-item label="桶访问策略" required><a-select v-model:value="storageForm.accessPolicy" :options="[{value:'0',label:'私有'},{value:'1',label:'公开'},{value:'2',label:'自定义'}]" /></a-form-item>
        <a-form-item label="新增后设为默认"><a-select v-model:value="storageForm.status" :options="[{value:'Y',label:'是'},{value:'N',label:'否'}]" /></a-form-item>
        <a-form-item label="扩展配置"><a-input v-model:value="storageForm.ext1" /></a-form-item>
      </div><a-form-item label="备注"><a-textarea v-model:value="storageForm.remark" :rows="2" /></a-form-item></a-form>
    </a-modal>
    <a-modal :open="!!selected" title="操作日志详情" :width="760" :footer="null" @cancel="selected = undefined">
      <a-descriptions v-if="selected" bordered :column="1" size="small">
        <a-descriptions-item label="操作人">{{ selected.operName }}</a-descriptions-item>
        <a-descriptions-item label="操作时间">{{ selected.operTime }}</a-descriptions-item>
        <a-descriptions-item label="结果">{{ String(selected.status) === '0' ? '成功' : '失败' }}</a-descriptions-item>
        <a-descriptions-item v-if="selected.errorMsg" label="失败原因">{{ selected.errorMsg }}</a-descriptions-item>
        <a-descriptions-item label="业务动作">{{ selected.businessType }}</a-descriptions-item>
        <a-descriptions-item label="操作对象">{{ selected.auditObject }}</a-descriptions-item>
        <a-descriptions-item label="请求路径">{{ selected.requestMethod }} {{ selected.operUrl }}</a-descriptions-item>
        <a-descriptions-item label="来源 IP">{{ selected.operIp }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{ selected.costTime ?? '—' }} 毫秒</a-descriptions-item>
        <a-descriptions-item v-for="(value, label) in (selected.auditFields as Record<string, unknown>)" :key="label" :label="label">
          {{ value ?? '未记录' }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </StandardListLayout>
</template>
<style scoped>
.parameter-actions { display:flex; align-items:center; justify-content:space-between; background:#fff; border-radius:8px; padding:14px 18px; }
.parameter-actions h2 { margin:0 0 4px; font-size:16px; font-weight:600; }
.parameter-actions span { color:#86909c; font-size:13px; }
.settings-page { height:100%; overflow:auto; padding:4px 2px 24px; }
.settings-section-title { margin:16px 0 10px; color:#4e5969; font-size:14px; font-weight:600; }
.setting-card { margin-bottom:14px; border-radius:8px; box-shadow:0 2px 8px rgba(29,33,41,.06); }
.setting-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
.setting-heading h3 { margin:0 0 5px; color:#1d2129; font-size:15px; font-weight:600; }
.setting-heading p { margin:0; color:#86909c; font-size:13px; }
.setting-controls { display:flex; align-items:flex-end; gap:24px; flex-wrap:wrap; margin-top:24px; }
.setting-control { min-width:150px; display:flex; align-items:center; justify-content:space-between; gap:18px; padding-bottom:24px; color:#4e5969; }
.setting-controls :deep(.ant-form-item) { margin-bottom:0; min-width:150px; }
.setting-controls :deep(.ant-input-number) { width:120px; }
.setting-example { padding-bottom:7px; color:#86909c; font-size:13px; }
.setting-example strong { color:#1e5eff; font-size:15px; }
.setting-footer { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-top:18px; padding-top:14px; border-top:1px solid #f0f0f0; color:#86909c; font-size:13px; }
</style>


<style scoped>
.head-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search-icon { color: #86909c; }
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(29, 33, 41, .06);
}
.filter-spacer { flex: 1 1 auto; min-width: 8px; }
.col-set-panel { width: 220px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29, 33, 41, .12); }
.col-set-item { padding: 2px 8px; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }
.dict-drawer-tools { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.dict-form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 14px; }
</style>
