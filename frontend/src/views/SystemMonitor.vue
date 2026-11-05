<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
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
  "menu-directory": {"menuName": "菜单名称", "menuType": "类型", "path": "路径", "status": "状态"},
  "cache-overview": {"metric": "指标", "value": "值"},
  "dictionary": {"dictName": "字典名称", "dictType": "字典类型", "remark": "备注"},
  "parameters": {"configName": "参数名称", "configKey": "参数键", "configValue": "参数值", "configType": "参数来源", "remark": "说明"},
  "files": {"originalName": "原文件名", "usage": "应用位置", "fileSuffix": "后缀", "createTime": "上传时间"},
  "storage-config": {"configKey": "配置名称", "bucketName": "存储桶", "region": "区域", "status": "状态"},
  "clients": {"clientKey": "客户端", "deviceType": "设备类型", "grantType": "授权方式", "status": "状态"},

  online: { userName: '账号', ipaddr: '登录 IP', browser: '浏览器', os: '操作系统', loginTime: '登录时间' },
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
const isLogs = computed(() => props.kind === 'logs')
const account = ref('')
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
  if (!isLogs.value) return kind.value === 'parameters' ? [...allColumns.value, { key: 'action', title: '操作', width: 90 }] : allColumns.value
  const hidden = hiddenColumns.value[kind.value] ?? (kind.value === 'operlog' ? technicalFields : [])
  return [
    { key: 'sequence', title: '序号', width: 64 },
    ...allColumns.value.filter(column => column.key !== 'auditDetail' && !hidden.includes(column.key)),
    ...allColumns.value.filter(column => column.key === 'auditDetail'),
  ]
})
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
  outcome.value = undefined
  search()
}
let requestId = 0
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const selected = ref<Record<string, unknown>>()
const configDialog = ref(false)
const configSaving = ref(false)
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
const parameterRows = computed(() => rows.value)
const sizeRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'system.code.size'))
const sizeGroupRuleRow = computed(() => parameterRows.value.find(row => row.configKey === 'system.code.sizeGroup'))
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
function editConfig(row: Record<string, unknown>) {
  editingConfig.value = { ...row }
  configDialog.value = true
}
async function saveConfig() {
  configSaving.value = true
  try {
    const res: any = await api.put('/system/config', editingConfig.value)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    message.success('参数已保存')
    configDialog.value = false
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    configSaving.value = false
  }
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
    const endpoints: Record<string,string> = {"menu-directory": "/system/menu/list", "cache-overview": "/monitor/cache", "dictionary": "/system/dict/type/list", "parameters": "/system/config/list", "files": "/resource/oss/list", "storage-config": "/resource/oss/config/list", "clients": "/system/client/list"}
    const res: any = await api.get(endpoints[kind.value] || '/monitor/' + kind.value + '/list', { params: { pageNum: page.value, pageSize: isLogs.value ? pageSize.value : currentKind === 'parameters' ? 100 : 20,
      ...(currentKind === 'files' ? { originalName: fileFilter.value || undefined } : {}),
      ...(isLogs.value ? { [currentKind === 'operlog' ? 'operName' : 'userName']: applied.value.account, status: applied.value.status } : {}) } })
    if (currentRequest !== requestId) return
    if (res.code !== 200) throw new Error(res.msg || '查询失败')
    const data = res.data
    const records = kind.value === 'cache-overview'
      ? Object.entries(data.info || {}).map(([metric, value]) => ({ metric, value }))
      : Array.isArray(data) ? data : data.rows || []
    if (currentKind === 'parameters') syncCodeRules(records as Record<string, any>[])
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
      ...Object.fromEntries(allColumns.value.map(({ key }) => [key, row[key]])), errorMsg: row.errorMsg, ossId: row.ossId, usage: currentKind === "files" ? usage.get(String(row.ossId))?.join("；") ?? "待核对" : undefined, ...(currentKind === "parameters" ? { configId: row.configId } : {}), ...(kind.value === 'operlog' ? sizeAudit(row) : {}), index,
    }))
    total.value = data.total ?? rows.value.length
  } catch (error) {
    if (currentRequest === requestId) message.error(error instanceof Error ? error.message : '查询失败')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
watch(kind, () => { rows.value = []; total.value = 0; selected.value = undefined; resetFilters() }, { immediate: true })
</script>
<template>
  <StandardListLayout>
    <template v-if="isLogs || kind === 'files'" #filters>
      <a-input v-if="kind === 'files'" v-model:value="fileName" allow-clear placeholder="搜索原文件名" style="width: 240px" @press-enter="fileFilter = fileName.trim(); search()" />
      <template v-else>
      <div class="head-filters">
      <a-radio-group v-model:value="category">
        <a-radio-button v-for="item in categories" :key="item.key" :value="item.key">{{ item.label }}</a-radio-button>
      </a-radio-group>
      <a-input v-model:value="account" allow-clear :placeholder="kind === 'operlog' ? '搜索操作人' : '搜索登录账号'"
        style="width: 200px" @press-enter="search">
        <template #prefix><SearchOutlined class="search-icon" /></template>
      </a-input>
      <a-select v-model:value="outcome" allow-clear placeholder="结果" style="width: 110px"
        :options="[{ label: '成功', value: '0' }, { label: '失败', value: '1' }]" />
      </div>
      </template>
    </template>
    <template #actions>
      <div v-if="isLogs" class="filter-bar">
        <a-button type="primary" :loading="loading" @click="search">查询</a-button>
        <a-button @click="resetFilters">重置</a-button>
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
      <div v-else-if="kind === 'parameters'" class="parameter-actions">
        <div><h2>系统参数</h2><span>配置影响系统各模块的默认行为</span></div>
        <a-button :loading="loading" @click="load">刷新设置</a-button>
      </div>
      <a-button v-else type="primary" :loading="loading" @click="load">刷新</a-button>
    </template>
    <section v-if="kind === 'parameters'" class="settings-page" :aria-busy="loading">
      <div class="settings-section-title">业务规则</div>
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

      <div class="settings-section-title">系统功能</div>
      <a-card class="setting-card" :bordered="false">
        <div class="setting-heading"><div><h3>OSS 预览列表资源</h3><p>控制文件选择器是否展示图片预览资源。</p></div><a-tag color="blue">系统内置</a-tag></div>
        <div class="setting-footer"><a-tag :color="ossPreviewRow?.configValue === 'true' ? 'green' : 'default'">{{ ossPreviewRow?.configValue === 'true' ? '已开启' : '已关闭' }}</a-tag><a-button v-if="ossPreviewRow && can('system:config:edit')" type="link" @click="editConfig(ossPreviewRow)">编辑</a-button></div>
      </a-card>
    </section>
    <a-table v-else class="zy-fill-table" :columns="columns" :data-source="rows" :loading="loading" row-key="index"
      :size="isLogs ? 'middle' : undefined" :table-layout="isLogs ? 'fixed' : undefined"
      :scroll="isLogs ? { x: columns.reduce((sum, column) => sum + (column.width ?? 110), 0), y: 'calc(100vh - 390px)' } : { y: 'calc(100vh - 330px)' }"
      :pagination="{ current: page, pageSize: isLogs ? pageSize : 20, total, showSizeChanger: isLogs, showQuickJumper: isLogs, showTotal: isLogs ? (count: number) => `共 ${count} 条` : undefined, onChange: (value: number, size: number) => { page = value; pageSize = size; load() } }">
      <template #bodyCell="{ column, text, record, index }">
        <template v-if="column.key === 'fileAction' && can('system:oss:download') && !String(record.originalName).startsWith('.')">
          <a-button v-if="/^\.?(png|jpe?g|gif|webp)$/i.test(String(record.fileSuffix))" type="link" @click="downloadFile(record, true)">预览</a-button>
          <a-button type="link" @click="downloadFile(record)">下载</a-button>
        </template>
        <template v-else-if="column.key === 'sequence'">{{ (page - 1) * pageSize + index + 1 }}</template>
        <a-button v-else-if="column.key === 'action' && can('system:config:edit') && record.configKey === 'sys.oss.previewListResource'" type="link" @click="editConfig(record)">编辑</a-button>
        <a-button v-else-if="column.key === 'auditDetail'" type="link" @click="selected = record">查看</a-button>
        <template v-else-if="column.key === 'status' && ['operlog', 'loginInfo'].includes(kind)"><a-tag :color="String(text) === '0' ? 'green' : 'red'">{{ String(text) === '0' ? '成功' : '失败' }}</a-tag></template>
        <template v-else-if="column.key === 'loginTime' && typeof text === 'number'">{{ new Date(text).toLocaleString('zh-CN') }}</template>
      </template>
    </a-table>
    <a-modal v-if="kind === 'files'" :open="!!previewUrl" title="图片预览" :footer="null" @cancel="closePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="文件预览" style="display: block; max-width: 100%; max-height: 70vh; margin: auto" />
    </a-modal>
    <a-modal v-if="kind === 'parameters'" v-model:open="configDialog" title="编辑系统参数" :confirm-loading="configSaving" @ok="saveConfig">
      <a-form layout="vertical">
        <a-form-item label="参数名称"><a-input v-model:value="editingConfig.configName" /></a-form-item>
        <a-form-item label="参数键"><a-input v-model:value="editingConfig.configKey" disabled /></a-form-item>
        <a-form-item label="参数值"><a-switch :checked="editingConfig.configValue === 'true'" checked-children="开启" un-checked-children="关闭" @change="editingConfig.configValue = $event ? 'true' : 'false'" /></a-form-item>
        <a-form-item label="说明"><a-input v-model:value="editingConfig.remark" /></a-form-item>
      </a-form>
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
</style>
