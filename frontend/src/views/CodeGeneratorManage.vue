<!--
文件用途：在织云 ERP 中完整承载 RuoYi-Vue-Plus 代码生成器的管理界面。
文件版本：2.0.0
功能目的：让有权限的维护人员在 ERP 内导入数据库表、配置代码模板、预览和下载单表/批量生成结果，并维护生成配置。
设计思路：页面只编排原生 /tool/gen 与菜单查询接口；生成规则、持久化、校验和权限继续由框架后端负责。
实现方法：复用标准列表、统一布局和 Ant Design Vue；以表级表单、字段表格、扩展参数映射及独立确认弹窗组织交互。
关键约束：所有动作均按后端权限码显示；保存扩展项必须写入 params；生成结果为 ZIP 源码，不会自动安装、执行 SQL 或发布页面。
依赖与引用：Docs/规范/前端规范.md；Docs/API/框架原生能力接入API.md；Docs/项目记录/功能实现状态.md
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：Codex（智能体）
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-24
修改标记：ZY-ERP-20261124-代码生成器完整接入；补齐原生配置、批量操作和参数持久化
最近测试时间：2026-11-24
测试人员：Codex（智能体）
测试状态：npm run build、git diff --check、接口/权限静态映射通过；认证页面操作待浏览器环境恢复，详见任务单
-->
<script setup lang="ts">
/**
 * 页面只连接框架已有代码生成 API。表结构读取、字段检查和代码渲染均由后端完成，
 * 这里负责显示真实返回值、采集完整配置、按操作权限执行调用并呈现错误。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'

type GeneratorTable = Record<string, any> & { id: string; tableId: string; tableName: string }
type GeneratorColumn = Record<string, any> & { columnId: string; javaField: string; columnName: string }
type MenuNode = { value: string; title: string; selectable: boolean; children: MenuNode[] }

const rows = ref<GeneratorTable[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ tableName: '', tableComment: '', dataName: '' })
const selectedKeys = ref<string[]>([])

const importOpen = ref(false)
const importLoading = ref(false)
const dataNames = ref<string[]>([])
const dataName = ref('')
const dbTables = ref<Record<string, any>[]>([])
const dbPage = ref(1)
const dbSize = ref(20)
const dbTotal = ref(0)
const dbTableName = ref('')
const dbTableComment = ref('')
const dbSelected = ref<string[]>([])

const editOpen = ref(false)
const editSaving = ref(false)
const editSnapshot = ref('')
const form = reactive<Record<string, any>>({})
const fieldRows = ref<GeneratorColumn[]>([])
const fieldFilter = ref('')
const menuTree = ref<MenuNode[]>([])
const menuLoading = ref(false)
const menuLoadFailed = ref(false)
const uniquePickOpen = ref(false)
const uniqueSearch = ref('')
const uniquePickValues = ref<string[]>([])

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewFiles = ref<Record<string, string>>({})
const previewKey = ref('')

/** 标准列表根据框架返回字段显示导入表与已保存的生成配置。 */
const tableColumns = [
  { key: 'tableName', title: '数据表', width: 210 },
  { key: 'tableComment', title: '表说明', width: 180 },
  { key: 'dataName', title: '数据源', width: 130 },
  { key: 'className', title: '实体名称', width: 150 },
  { key: 'functionName', title: '功能名称', width: 150 },
  { key: 'tplCategory', title: '模板类型', width: 100 },
  { key: 'frontendType', title: '前端模板', width: 100 },
  { key: 'createTime', title: '导入时间', width: 165 },
]
const importColumns = [
  { title: '数据表', dataIndex: 'tableName', key: 'tableName', width: 260 },
  { title: '表说明', dataIndex: 'tableComment', key: 'tableComment' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
]
const previewKeys = computed(() => Object.keys(previewFiles.value))
const canBatchOperate = computed(() => can('tool:gen:remove') || can('tool:gen:code'))
const filteredFieldRows = computed(() => {
  const keyword = fieldFilter.value.trim().toLocaleLowerCase()
  if (!keyword) return fieldRows.value
  return fieldRows.value.filter((column) =>
    [column.columnName, column.columnComment, column.javaField]
      .some((value) => String(value || '').toLocaleLowerCase().includes(keyword)),
  )
})
const unsupportedQueryColumns = computed(() => fieldRows.value.filter((column) =>
  ['GTE', 'LTE'].includes(String(column.queryType || ''))
  || (column.queryType === 'BETWEEN' && column.htmlType !== 'datetime'),
))
const fieldOptions = computed(() => fieldRows.value.map((column) => ({
  value: String(column.javaField),
  label: (column.columnComment ? String(column.columnComment) + '（' : '') + String(column.javaField) + (column.columnComment ? '）' : ''),
})))
const filteredUniqueOptions = computed(() => {
  const keyword = uniqueSearch.value.trim().toLocaleLowerCase()
  if (!keyword) return fieldOptions.value
  return fieldOptions.value.filter((option) => option.label.toLocaleLowerCase().includes(keyword))
})
const mainDataNameOptions = computed(() => [
  { value: '', label: '全部数据源' },
  ...dataNames.value.map((name) => ({ value: name, label: name })),
])
const dbRowSelection = computed(() => ({
  selectedRowKeys: dbSelected.value,
  preserveSelectedRowKeys: true,
  onChange: (keys: (string | number)[]) => {
    dbSelected.value = keys.map(String)
  },
}))

/** 把 API 失败统一转成可读错误；下载请求的错误正文需要从 Blob 中读取。 */
async function errorMessage(error: any, fallback: string): Promise<string> {
  const responseData = error?.response?.data
  if (typeof Blob !== 'undefined' && responseData instanceof Blob) {
    try {
      const text = await responseData.text()
      const parsed = JSON.parse(text)
      return parsed?.msg || parsed?.message || fallback
    } catch {
      return fallback
    }
  }
  return responseData?.msg || error?.message || fallback
}

/** 检查框架响应业务码；拦截器已将 Axios 响应解包为响应体。 */
function assertSuccess(result: any, fallback: string): void {
  if (result?.code !== 200) throw new Error(result?.msg || fallback)
}

/** 查询已导入配置；过滤字段与框架 GenTable 查询条件一一对应。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const result: any = await api.get('/tool/gen/list', {
      params: {
        pageNum: page.value,
        pageSize: size.value,
        tableName: filters.tableName.trim() || undefined,
        tableComment: filters.tableComment.trim() || undefined,
        dataName: filters.dataName || undefined,
      },
    })
    assertSuccess(result, '查询失败')
    rows.value = (result.data?.rows || []).map((row: any) => ({
      ...row,
      tableId: String(row.tableId),
      id: String(row.tableId),
    }))
    total.value = Number(result.data?.total || 0)
  } catch (error: any) {
    message.error(await errorMessage(error, '加载代码生成表失败'))
  } finally {
    loading.value = false
  }
}

/** 执行筛选时回到第一页，避免旧页码超出筛选后的总页数。 */
function search(): void {
  page.value = 1
  void load()
}

/** 清空筛选并重新查询。 */
function resetFilters(): void {
  filters.tableName = ''
  filters.tableComment = ''
  filters.dataName = ''
  search()
}

/** 清理导入弹窗状态，避免跨数据源误把同名表一并导入。 */
function changeDataName(value: unknown): void {
  const optionValue = value && typeof value === 'object' && 'value' in value
    ? (value as { value?: unknown }).value
    : value
  dataName.value = typeof optionValue === 'string' || typeof optionValue === 'number' ? String(optionValue) : ''
  dbSelected.value = []
  dbPage.value = 1
  void loadDbTables()
}

/** 查询当前数据源尚未导入的数据库表；后端按数据源过滤已导入表。 */
async function loadDbTables(): Promise<void> {
  if (!dataName.value) {
    dbTables.value = []
    dbTotal.value = 0
    return
  }
  importLoading.value = true
  try {
    const result: any = await api.get('/tool/gen/db/list', {
      params: {
        pageNum: dbPage.value,
        pageSize: dbSize.value,
        dataName: dataName.value,
        tableName: dbTableName.value.trim() || undefined,
        tableComment: dbTableComment.value.trim() || undefined,
      },
    })
    assertSuccess(result, '数据库表查询失败')
    dbTables.value = (result.data?.rows || []).map((row: any) => ({
      ...row,
      id: String(row.tableName),
    }))
    dbTotal.value = Number(result.data?.total || 0)
  } catch (error: any) {
    message.error(await errorMessage(error, '读取数据库表失败'))
  } finally {
    importLoading.value = false
  }
}

/** 读取可用数据源名称；列表页筛选和表导入共用同一份受权限控制的选项。 */
async function loadDataNames(): Promise<void> {
  const result: any = await api.get('/tool/gen/getDataNames')
  assertSuccess(result, '数据源读取失败')
  dataNames.value = Array.isArray(result.data) ? result.data.map(String) : []
}

/** 打开导入弹窗并加载框架中可用的数据源清单。 */
async function openImport(): Promise<void> {
  importOpen.value = true
  dbSelected.value = []
  dbPage.value = 1
  dbTableName.value = ''
  dbTableComment.value = ''
  try {
    await loadDataNames()
    if (!dataNames.value.includes(dataName.value)) dataName.value = dataNames.value[0] || ''
    await loadDbTables()
  } catch (error: any) {
    message.error(await errorMessage(error, '获取数据源失败'))
  }
}

/** 防止导入请求运行中关闭窗口；仅在没有写请求时允许取消。 */
function closeImport(): void {
  if (importLoading.value) return
  importOpen.value = false
}

/** 批量导入已选表；只新增生成器配置，不创建或修改数据库业务表。 */
async function importTables(): Promise<void> {
  if (!dbSelected.value.length) {
    message.warning('请至少选择一张表')
    return
  }
  if (!dataName.value) {
    message.warning('请先选择数据源')
    return
  }
  importLoading.value = true
  try {
    const result: any = await api.post('/tool/gen/importTable', null, {
      params: { tables: dbSelected.value.join(','), dataName: dataName.value },
    })
    assertSuccess(result, '导入失败')
    message.success('已导入 ' + dbSelected.value.length + ' 张表')
    importOpen.value = false
    dbSelected.value = []
    selectedKeys.value = []
    await load()
  } catch (error: any) {
    message.error(await errorMessage(error, '导入表结构失败'))
  } finally {
    importLoading.value = false
  }
}

/** 清空可复用响应式表单后写入当前表和后端解析后的生成选项。 */
function replaceForm(values: Record<string, any>): void {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, values)
}

/** 生成当前表单快照，用于识别表级或字段级未保存修改。 */
function currentEditSnapshot(): string {
  return JSON.stringify({ form: { ...form }, columns: fieldRows.value })
}

/** 获取配置表单；框架详情响应同时包含字段，避免重复请求列接口。 */
async function edit(row: GeneratorTable): Promise<void> {
  try {
    const result: any = await api.get('/tool/gen/' + row.id)
    assertSuccess(result, '读取配置失败')
    const info = result.data?.info || {}
    replaceForm({
      ...info,
      tableId: String(info.tableId),
      parentMenuId: info.parentMenuId == null ? '' : String(info.parentMenuId),
      enableExport: info.enableExport !== false,
      enableStatus: info.enableStatus === true,
      enableUnique: info.enableUnique === true,
      enableSort: info.enableSort === true,
      uniqueFields: Array.isArray(info.uniqueFields) ? info.uniqueFields.map(String) : [],
      treeRootValue: info.treeRootValue || '',
      treeAncestorsField: info.treeAncestorsField || '',
      treeOrderField: info.treeOrderField || '',
    })
    fieldRows.value = (result.data?.rows || []).map((column: any) => ({
      ...column,
      columnId: String(column.columnId),
      tableId: String(column.tableId),
    }))
    fieldFilter.value = ''
    editOpen.value = true
    editSnapshot.value = currentEditSnapshot()
    if (can('system:menu:query')) void loadMenuTree()
  } catch (error: any) {
    message.error(await errorMessage(error, '读取代码生成配置失败'))
  }
}

/** 保存进行中时锁住关闭；存在未保存修改时要求维护人员明确确认放弃。 */
function closeEdit(): void {
  if (editSaving.value) return
  if (editOpen.value && editSnapshot.value && currentEditSnapshot() !== editSnapshot.value) {
    Modal.confirm({
      title: '放弃未保存的配置？',
      content: '表级或字段配置有未保存修改，关闭后这些修改会丢失。',
      okText: '放弃修改',
      cancelText: '继续编辑',
      okType: 'danger',
      onOk() {
        editOpen.value = false
      },
    })
    return
  }
  editOpen.value = false
}

/** 将权限接口返回的菜单树转换为 Ant TreeSelect 需要的字符串 ID 节点。 */
function normalizeMenuNodes(nodes: any[]): MenuNode[] {
  return nodes.flatMap((node) => {
    const id = node?.id ?? node?.menuId ?? node?.value ?? node?.key
    if (id == null) return []
    const type = node?.menuType ?? node?.type
    return [{
      value: String(id),
      title: String(node?.label ?? node?.menuName ?? node?.title ?? node?.name ?? '未命名菜单'),
      selectable: type !== 'F',
      children: normalizeMenuNodes(Array.isArray(node?.children) ? node.children : []),
    }]
  })
}

/** 只有另有菜单查询权限时才加载可选父菜单；失败时保留手动填写 ID 的能力。 */
async function loadMenuTree(): Promise<void> {
  menuLoading.value = true
  menuLoadFailed.value = false
  try {
    const result: any = await api.get('/system/menu/treeselect')
    assertSuccess(result, '菜单查询失败')
    menuTree.value = normalizeMenuNodes(Array.isArray(result.data) ? result.data : [])
  } catch {
    menuTree.value = []
    menuLoadFailed.value = true
  } finally {
    menuLoading.value = false
  }
}

/** 保留当前已配置但未出现在可选树中的 ID，避免保存时静默丢失已有父菜单。 */
const menuTreeData = computed(() => {
  const currentId = String(form.parentMenuId || '')
  const contains = (nodes: MenuNode[]): boolean =>
    nodes.some((node) => node.value === currentId || contains(node.children))
  if (currentId && !contains(menuTree.value)) {
    return [{
      value: currentId,
      title: String(form.parentMenuName || '当前父菜单（' + currentId + '）'),
      selectable: true,
      children: [],
    }, ...menuTree.value]
  }
  return menuTree.value
})

/** 保存树节点 ID 及其展示名称；框架菜单 SQL 只接受数字型父菜单 ID。 */
function changeParentMenu(value: string | number | undefined, labels: unknown, extra: any): void {
  form.parentMenuId = value == null ? '' : String(value)
  const triggerTitle = extra?.triggerNode?.title
  const firstLabel = Array.isArray(labels) ? labels[0] : labels
  form.parentMenuName = String(triggerTitle || firstLabel || '')
}

/** 解析框架存储的历史选项字符串，以保留当前页面未编辑的兼容扩展项。 */
function parseStoredOptions(options: unknown): Record<string, any> {
  if (typeof options !== 'string' || !options.trim()) return {}
  try {
    const parsed = JSON.parse(options)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

/** 后端只从 params 写 options；把页面全部表级开关及树字段映射到框架约定键名。 */
function buildParams(): Record<string, any> {
  return {
    ...parseStoredOptions(form.options),
    ...(form.params && typeof form.params === 'object' ? form.params : {}),
    parentMenuId: String(form.parentMenuId || ''),
    parentMenuName: String(form.parentMenuName || ''),
    enableExport: Boolean(form.enableExport),
    enableStatus: Boolean(form.enableStatus),
    statusField: String(form.statusField || ''),
    enableUnique: Boolean(form.enableUnique),
    uniqueFields: Array.isArray(form.uniqueFields) ? form.uniqueFields.map(String) : [],
    enableSort: Boolean(form.enableSort),
    sortField: String(form.sortField || ''),
    treeCode: String(form.treeCode || ''),
    treeParentCode: String(form.treeParentCode || ''),
    treeName: String(form.treeName || ''),
    treeRootValue: String(form.treeRootValue || ''),
    treeAncestors: String(form.treeAncestorsField || ''),
    treeOrderField: String(form.treeOrderField || ''),
  }
}

/** 保存前校验框架必填字段及生成选项，具体字段存在性仍由后端复核。 */
function validateConfig(): boolean {
  const requiredFields = [
    ['tableComment', '表说明'],
    ['className', '实体类名称'],
    ['packageName', '生成包路径'],
    ['moduleName', '模块名'],
    ['businessName', '业务名'],
    ['functionName', '功能名称'],
    ['functionAuthor', '作者'],
  ]
  const missing = requiredFields.find(([key]) => !String(form[key] || '').trim())
  if (missing) {
    message.warning(String(missing[1]) + '不能为空')
    return false
  }
  const parentMenuId = String(form.parentMenuId || '').trim()
  if (parentMenuId && !/^\d+$/.test(parentMenuId)) {
    message.warning('父菜单 ID 只能填写数字；请使用菜单选择器或留空采用框架默认菜单')
    return false
  }
  if (form.enableStatus && !form.statusField) {
    message.warning('启用状态切换时，请选择状态字段')
    return false
  }
  if (form.enableSort && !form.sortField) {
    message.warning('启用排序调整时，请选择排序字段')
    return false
  }
  if (form.enableUnique && !(form.uniqueFields || []).length) {
    message.warning('启用组合唯一校验时，请至少选择一个字段')
    return false
  }
  if (form.tplCategory === 'tree') {
    const missingTreeField = [
      ['treeCode', '树编码字段'],
      ['treeParentCode', '树父编码字段'],
      ['treeName', '树名称字段'],
    ].find(([key]) => !String(form[key] || '').trim())
    if (missingTreeField) {
      message.warning(String(missingTreeField[1]) + '不能为空')
      return false
    }
  }
  if (fieldRows.value.some((column) => !String(column.javaField || '').trim())) {
    message.warning('Java 属性不能为空，请检查字段配置')
    return false
  }
  if (unsupportedQueryColumns.value.length) {
    const column = unsupportedQueryColumns.value[0]
    message.warning('字段“' + column.columnName + '”的查询方式与框架模板不兼容，请重新选择查询方式')
    return false
  }
  return true
}

/** 更新配置并一并保存所有字段规则及模板扩展参数。 */
async function saveConfig(): Promise<void> {
  if (!can('tool:gen:edit') || !validateConfig()) return
  editSaving.value = true
  try {
    const payload = {
      ...form,
      tableId: String(form.tableId),
      params: buildParams(),
      columns: fieldRows.value,
    }
    const result: any = await api.put('/tool/gen', payload)
    assertSuccess(result, '保存失败')
    message.success('生成配置已保存')
    editOpen.value = false
    await load()
  } catch (error: any) {
    message.error(await errorMessage(error, '保存代码生成配置失败'))
  } finally {
    editSaving.value = false
  }
}

/** 单表与批量生成均返回 ZIP；解析 Blob 业务错误后才触发浏览器下载。 */
async function saveZip(blob: Blob, filename: string): Promise<void> {
  if (!(blob instanceof Blob) || blob.size === 0) throw new Error('生成结果为空')
  if (blob.type.includes('application/json') || blob.type.includes('text/')) {
    const text = await blob.text()
    let parsed: any
    try {
      parsed = JSON.parse(text)
    } catch {
      throw new Error(text || '生成代码失败')
    }
    throw new Error(parsed?.msg || parsed?.message || '生成代码失败')
  }
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** 下载单表生成结果；代码生成权限由后端再次检查。 */
async function download(row: GeneratorTable): Promise<void> {
  try {
    const blob = await api.get('/tool/gen/download/' + row.id, { responseType: 'blob' }) as unknown as Blob
    await saveZip(blob, row.tableName + '.zip')
  } catch (error: any) {
    message.error(await errorMessage(error, '生成代码下载失败'))
  }
}

/** 批量下载所有已选择的表；FoundationTable 保留跨分页选择键。 */
async function downloadSelected(): Promise<void> {
  const tableIds = [...new Set(selectedKeys.value.map(String).filter(Boolean))]
  if (!tableIds.length) {
    message.warning('请先选择要生成代码的数据表')
    return
  }
  try {
    const blob = await api.get('/tool/gen/batchGenCode', {
      params: { tableIdStr: tableIds.join(',') },
      responseType: 'blob',
    }) as unknown as Blob
    await saveZip(blob, 'ruoyi-generated-code.zip')
  } catch (error: any) {
    message.error(await errorMessage(error, '批量生成代码失败'))
  }
}

/** 预览框架返回的模板文件路径和实际渲染内容。 */
async function preview(row: GeneratorTable): Promise<void> {
  previewOpen.value = true
  previewLoading.value = true
  previewFiles.value = {}
  try {
    const result: any = await api.get('/tool/gen/preview/' + row.id)
    assertSuccess(result, '代码预览失败')
    previewFiles.value = result.data || {}
    previewKey.value = Object.keys(previewFiles.value)[0] || ''
  } catch (error: any) {
    message.error(await errorMessage(error, '代码预览失败'))
  } finally {
    previewLoading.value = false
  }
}

/** 把多个已选配置作为单次路径数组删除；只删除生成器记录及字段配置。 */
function removeSelected(): void {
  const tableIds = [...new Set(selectedKeys.value.map(String).filter(Boolean))]
  if (!tableIds.length) return
  Modal.confirm({
    title: '批量删除生成配置',
    content: '将删除选中的 ' + tableIds.length + ' 条代码生成配置及其字段设置，不会删除数据库业务表。确认继续？',
    okText: '删除配置',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      const result: any = await api.delete('/tool/gen/' + tableIds.join(','))
      assertSuccess(result, '删除失败')
      message.success('已删除 ' + tableIds.length + ' 条生成配置')
      selectedKeys.value = []
      await load()
    },
  })
}

/** 删除单条生成器配置时再次说明影响范围，避免与物理业务表混淆。 */
function remove(row: GeneratorTable): void {
  Modal.confirm({
    title: '删除生成配置',
    content: '删除“' + row.tableName + '”的代码生成器配置及字段设置？数据库业务表和其中的数据不会删除。',
    okText: '删除配置',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      const result: any = await api.delete('/tool/gen/' + row.id)
      assertSuccess(result, '删除失败')
      message.success('生成配置已删除')
      selectedKeys.value = selectedKeys.value.filter((key) => key !== row.id)
      await load()
    },
  })
}

/** 同步只更新生成器中的列元数据；新增/删除列会反映到配置，不改数据库表。 */
function syncDb(row: GeneratorTable): void {
  Modal.confirm({
    title: '同步数据库结构',
    content: '重新读取“' + row.tableName + '”的数据库字段。生成器配置会新增数据库新字段，并移除已从数据库删除的字段；部分字段控件设置会按框架默认规则重置。不会修改业务表或业务数据。确认继续？',
    okText: '同步配置',
    cancelText: '取消',
    async onOk() {
      const result: any = await api.get('/tool/gen/synchDb/' + row.id)
      assertSuccess(result, '同步失败')
      message.success('生成器中的表字段配置已同步')
      await load()
    },
  })
}

/** 从表字段选项中挑出适用于启用、唯一校验及树配置的字符串属性。 */
function isDictionaryControl(htmlType: string): boolean {
  return ['select', 'radio', 'checkbox', 'switch'].includes(htmlType)
}

/** 只展示后端服务模板已实现的查询比较符；BETWEEN 依赖日期区间控件。 */
function queryTypeOptions(column: Record<string, any>): { value: string; label: string; disabled?: boolean }[] {
  const options: { value: string; label: string; disabled?: boolean }[] = [
    { value: 'EQ', label: '等于' },
    { value: 'NE', label: '不等于' },
    { value: 'GT', label: '大于' },
    { value: 'LT', label: '小于' },
    { value: 'LIKE', label: '模糊匹配' },
  ]
  if (column.htmlType === 'datetime') options.push({ value: 'BETWEEN', label: '日期范围' })
  if (['GTE', 'LTE'].includes(String(column.queryType || ''))) {
    options.unshift({ value: String(column.queryType), label: String(column.queryType) + '（旧值，请改选）', disabled: true })
  }
  if (column.queryType === 'BETWEEN' && column.htmlType !== 'datetime') {
    options.unshift({ value: 'BETWEEN', label: 'BETWEEN（需先选择日期时间控件）', disabled: true })
  }
  return options
}

/** 打开组合唯一字段弹窗时复制当前设置，取消不会影响正在编辑的表单。 */
function openUniquePicker(): void {
  uniqueSearch.value = ''
  uniquePickValues.value = Array.isArray(form.uniqueFields) ? form.uniqueFields.map(String) : []
  uniquePickOpen.value = true
}

/** 更新临时唯一字段集合；确认按钮才把集合写回配置表单。 */
function toggleUniqueField(value: string, checked: boolean): void {
  const values = new Set(uniquePickValues.value)
  if (checked) values.add(value)
  else values.delete(value)
  uniquePickValues.value = [...values]
}

/** 删除已选唯一字段标签。 */
function removeUniqueField(value: string): void {
  form.uniqueFields = (form.uniqueFields || []).filter((field: string) => String(field) !== value)
}

/** 将 Ant Table 的字段路径统一成字符串，兼容列定义的数组型 dataIndex。 */
function columnField(column: { dataIndex?: unknown; key?: unknown }): string {
  if (typeof column.dataIndex === 'string') return column.dataIndex
  if (Array.isArray(column.dataIndex)) return column.dataIndex.map(String).join('.')
  return String(column.key || '')
}

/** 首次打开即查询生成器已导入配置列表。 */
onMounted(() => {
  void load()
  if (can('tool:gen:list')) {
    void loadDataNames().catch(async (error: any) => {
      message.error(await errorMessage(error, '读取数据源筛选项失败'))
    })
  }
})
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <label class="zy-filter-field">
        <span>数据表</span>
        <a-input v-model:value="filters.tableName" allow-clear placeholder="输入数据表名称" @press-enter="search" />
      </label>
      <label class="zy-filter-field">
        <span>表说明</span>
        <a-input v-model:value="filters.tableComment" allow-clear placeholder="输入表说明" @press-enter="search" />
      </label>
      <label class="zy-filter-field">
        <span>数据源</span>
        <a-select v-model:value="filters.dataName" allow-clear :options="mainDataNameOptions" style="width: 180px" @change="search" />
      </label>
    </template>

    <FoundationTable
      page-toolbar
      storage-key="framework_generator"
      :columns="tableColumns"
      :rows="rows"
      :loading="loading"
      :selected-keys="canBatchOperate ? selectedKeys : undefined"
      :server-total="total"
      :server-page="page"
      :server-page-size="size"
      :action-width="360"
      @update:selected-keys="selectedKeys = $event"
      @page-change="(current, pageSize) => { page = current; size = pageSize; void load() }"
    >
      <template #actions>
        <a-button v-if="can('tool:gen:import') && can('tool:gen:list')" type="primary" @click="openImport">导入数据库表</a-button>
        <a-button v-if="can('tool:gen:code')" :disabled="!selectedKeys.length" @click="downloadSelected">批量生成代码</a-button>
        <a-button v-if="can('tool:gen:remove')" danger :disabled="!selectedKeys.length" @click="removeSelected">批量删除配置</a-button>
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="resetFilters">重置</a-button>
      </template>

      <template #cell="{ column, row }">
        <a-tag v-if="column.key === 'tplCategory'">{{ row.tplCategory === 'tree' ? '树表' : '单表 CRUD' }}</a-tag>
        <a-tag v-else-if="column.key === 'frontendType'">{{ String(row.frontendType || 'vue').toUpperCase() }}</a-tag>
        <span v-else>{{ row[column.key] || '—' }}</span>
      </template>

      <template #action="{ row }">
        <a-button v-if="can('tool:gen:query')" type="link" size="small" @click="edit(row)">配置</a-button>
        <a-button v-if="can('tool:gen:preview')" type="link" size="small" @click="preview(row)">预览</a-button>
        <a-button v-if="can('tool:gen:code')" type="link" size="small" @click="download(row)">下载</a-button>
        <a-dropdown v-if="can('tool:gen:edit') || can('tool:gen:remove')">
          <a-button type="link" size="small">更多</a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-if="can('tool:gen:edit')" @click="syncDb(row)">同步数据库结构</a-menu-item>
              <a-menu-item v-if="can('tool:gen:remove')" danger @click="remove(row)">删除生成配置</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>
    </FoundationTable>
  </StandardListLayout>

  <a-modal
    :open="importOpen"
    title="导入数据库表"
    width="980px"
    :confirm-loading="importLoading"
    :closable="!importLoading"
    :cancel-button-props="{ disabled: importLoading }"
    :mask-closable="false"
    :keyboard="false"
    ok-text="导入选中表"
    cancel-text="取消"
    :ok-button-props="{ disabled: !dbSelected.length || !dataName }"
    @ok="importTables"
    @cancel="closeImport"
  >
    <a-alert
      type="info"
      show-icon
      message="这里只把数据库表结构登记到代码生成器；不会新建或修改业务表。候选列表会自动排除当前数据源中已导入的表。"
      style="margin-bottom: 14px"
    />
    <div class="import-toolbar">
      <label>
        <span>数据源</span>
        <a-select v-model:value="dataName" style="width: 240px" :options="dataNames.map((name) => ({ value: name, label: name }))" @change="changeDataName" />
      </label>
      <label>
        <span>数据表</span>
        <a-input v-model:value="dbTableName" allow-clear placeholder="按数据表名称筛选" @press-enter="dbPage = 1; loadDbTables()" />
      </label>
      <label>
        <span>表说明</span>
        <a-input v-model:value="dbTableComment" allow-clear placeholder="按表说明筛选" @press-enter="dbPage = 1; loadDbTables()" />
      </label>
      <a-button @click="dbPage = 1; loadDbTables()">查询</a-button>
    </div>
    <div class="selection-hint">已选 {{ dbSelected.length }} 张表；跨页选择会保留，切换数据源会清空选择。</div>
    <a-table
      row-key="id"
      size="small"
      :columns="importColumns"
      :data-source="dbTables"
      :loading="importLoading"
      :row-selection="dbRowSelection"
      :scroll="{ x: 760, y: '48vh' }"
      :pagination="{
        current: dbPage,
        pageSize: dbSize,
        total: dbTotal,
        showSizeChanger: true,
        showTotal: (count) => '共 ' + count + ' 张表',
        onChange: (current, pageSize) => { dbPage = current; dbSize = pageSize; void loadDbTables() }
      }"
    />
  </a-modal>

  <a-modal
    :open="editOpen"
    title="代码生成配置"
    width="1280px"
    :footer="null"
    :closable="!editSaving"
    :mask-closable="false"
    :keyboard="false"
    :body-style="{ maxHeight: '72vh', overflowY: 'auto' }"
    @cancel="closeEdit"
  >
    <a-tabs>
      <a-tab-pane key="base" tab="基础信息">
        <a-alert
          type="info"
          show-icon
          message="配置用于生成后端代码、MyBatis XML、数据库菜单 SQL，以及所选 Vue 或 React 前端模板。生成结果需下载、审查并按项目规范集成，不会自动发布到 ERP。"
          style="margin-bottom: 16px"
        />
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="数据表"><a-input :value="form.tableName" disabled /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="数据源"><a-input :value="form.dataName" disabled /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="表说明"><a-input v-model:value="form.tableComment" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="实体类名称"><a-input v-model:value="form.className" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="模板类型"><a-select v-model:value="form.tplCategory" :disabled="!can('tool:gen:edit')" :options="[{ value: 'crud', label: '单表 CRUD' }, { value: 'tree', label: '树表 CRUD' }]" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="前端模板"><a-select v-model:value="form.frontendType" :disabled="!can('tool:gen:edit')" :options="[{ value: 'vue', label: 'Vue 3' }, { value: 'react', label: 'React' }]" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="生成包路径"><a-input v-model:value="form.packageName" :disabled="!can('tool:gen:edit')" placeholder="如 org.dromara.demo" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="模块名"><a-input v-model:value="form.moduleName" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="业务名"><a-input v-model:value="form.businessName" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="功能名称"><a-input v-model:value="form.functionName" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="作者"><a-input v-model:value="form.functionAuthor" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="备注"><a-input v-model:value="form.remark" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="8">
            <a-form-item label="父菜单">
              <a-tree-select
                v-if="can('system:menu:query')"
                v-model:value="form.parentMenuId"
                show-search
                allow-clear
                tree-default-expand-all
                tree-node-filter-prop="title"
                :tree-data="menuTreeData"
                :loading="menuLoading"
                :disabled="!can('tool:gen:edit')"
                placeholder="选择生成菜单的上级菜单"
                @change="changeParentMenu"
              />
              <a-input v-else v-model:value="form.parentMenuId" :disabled="!can('tool:gen:edit')" placeholder="数字菜单 ID；留空采用框架默认菜单" />
            </a-form-item>
          </a-col>
          <a-col :span="16">
            <a-form-item label="菜单名称">
              <a-input v-model:value="form.parentMenuName" :disabled="!can('tool:gen:edit')" placeholder="记录父菜单名称，便于核对生成位置" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-alert
              v-if="menuLoadFailed"
              type="warning"
              show-icon
              message="未能读取菜单树；已有菜单 ID 会保留。可重试打开配置，或填写数字菜单 ID。"
              style="margin-bottom: 12px"
            />
          </a-col>
        </a-row>

        <a-divider orientation="left">生成选项</a-divider>
        <a-row :gutter="16">
          <a-col :span="6"><a-form-item label="生成导出功能"><a-switch v-model:checked="form.enableExport" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="6"><a-form-item label="生成状态切换"><a-switch v-model:checked="form.enableStatus" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="状态字段">
              <a-select v-model:value="form.statusField" show-search allow-clear option-filter-prop="label" :disabled="!can('tool:gen:edit') || !form.enableStatus" :options="fieldOptions" placeholder="选择状态属性" />
            </a-form-item>
          </a-col>
          <a-col :span="6"><a-form-item label="生成组合唯一校验"><a-switch v-model:checked="form.enableUnique" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="18">
            <a-form-item label="唯一字段">
              <div class="unique-field-control">
                <a-space wrap>
                  <a-tag v-for="field in form.uniqueFields || []" :key="field" :closable="can('tool:gen:edit')" @close="removeUniqueField(String(field))">{{ field }}</a-tag>
                  <span v-if="!(form.uniqueFields || []).length" class="muted-value">未选择</span>
                </a-space>
                <a-button :disabled="!can('tool:gen:edit') || !form.enableUnique" @click="openUniquePicker">选择字段</a-button>
              </div>
            </a-form-item>
          </a-col>
          <a-col :span="6"><a-form-item label="生成排序调整"><a-switch v-model:checked="form.enableSort" :disabled="!can('tool:gen:edit')" /></a-form-item></a-col>
          <a-col :span="18">
            <a-form-item label="排序字段">
              <a-select v-model:value="form.sortField" show-search allow-clear option-filter-prop="label" :disabled="!can('tool:gen:edit') || !form.enableSort" :options="fieldOptions" placeholder="选择排序属性" />
            </a-form-item>
          </a-col>
        </a-row>

        <template v-if="form.tplCategory === 'tree'">
          <a-divider orientation="left">树表配置</a-divider>
          <a-alert type="info" show-icon message="树表模板必须选择编码、父级编码和名称字段；祖级及排序字段按表结构选填。" style="margin-bottom: 12px" />
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="树编码字段"><a-select v-model:value="form.treeCode" show-search option-filter-prop="label" :disabled="!can('tool:gen:edit')" :options="fieldOptions" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="树父编码字段"><a-select v-model:value="form.treeParentCode" show-search option-filter-prop="label" :disabled="!can('tool:gen:edit')" :options="fieldOptions" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="树名称字段"><a-select v-model:value="form.treeName" show-search option-filter-prop="label" :disabled="!can('tool:gen:edit')" :options="fieldOptions" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="根节点值"><a-input v-model:value="form.treeRootValue" :disabled="!can('tool:gen:edit')" placeholder="按数据实际值填写" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="祖级字段"><a-select v-model:value="form.treeAncestorsField" show-search allow-clear option-filter-prop="label" :disabled="!can('tool:gen:edit')" :options="fieldOptions" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="树排序字段"><a-select v-model:value="form.treeOrderField" show-search allow-clear option-filter-prop="label" :disabled="!can('tool:gen:edit')" :options="fieldOptions" /></a-form-item></a-col>
          </a-row>
        </template>
      </a-tab-pane>

      <a-tab-pane key="columns" :tab="'字段配置（' + fieldRows.length + '）'">
        <a-alert
          type="info"
          show-icon
          message="主键和自增标识按数据库元数据只读。字段属性控制生成代码中的新增、编辑、列表、查询和控件；字典类型仅对下拉、单选、复选、开关生效。"
          style="margin-bottom: 12px"
        />
        <a-alert
          v-if="unsupportedQueryColumns.length"
          type="warning"
          show-icon
          :message="unsupportedQueryColumns.length + ' 个字段保留了旧版不兼容查询方式；请替换为当前模板支持的查询方式后再保存。'"
          style="margin-bottom: 12px"
        />
        <div class="field-toolbar">
          <a-input v-model:value="fieldFilter" allow-clear placeholder="按数据库列名、字段说明或 Java 属性筛选" style="width: 360px" />
          <span class="muted-value">显示 {{ filteredFieldRows.length }} / {{ fieldRows.length }} 个字段</span>
        </div>
        <a-table
          size="small"
          :pagination="false"
          :data-source="filteredFieldRows"
          row-key="columnId"
          :scroll="{ x: 1880, y: '52vh' }"
          :columns="[
            { title: '数据库列', dataIndex: 'columnName', key: 'columnName', fixed: 'left', width: 150 },
            { title: '字段说明', dataIndex: 'columnComment', key: 'columnComment', width: 180 },
            { title: 'Java 属性', dataIndex: 'javaField', key: 'javaField', width: 155 },
            { title: 'Java 类型', dataIndex: 'javaType', key: 'javaType', width: 145 },
            { title: '主键', dataIndex: 'isPk', key: 'isPk', width: 76 },
            { title: '自增', dataIndex: 'isIncrement', key: 'isIncrement', width: 76 },
            { title: '必填', dataIndex: 'isRequired', key: 'isRequired', width: 84 },
            { title: '新增', dataIndex: 'isInsert', key: 'isInsert', width: 84 },
            { title: '编辑', dataIndex: 'isEdit', key: 'isEdit', width: 84 },
            { title: '列表', dataIndex: 'isList', key: 'isList', width: 84 },
            { title: '查询', dataIndex: 'isQuery', key: 'isQuery', width: 84 },
            { title: '查询方式', dataIndex: 'queryType', key: 'queryType', width: 112 },
            { title: '控件类型', dataIndex: 'htmlType', key: 'htmlType', width: 145 },
            { title: '字典类型', dataIndex: 'dictType', key: 'dictType', width: 150 },
            { title: '字段顺序', dataIndex: 'sort', key: 'sort', width: 100 },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <a-input v-if="columnField(column) === 'columnComment'" v-model:value="record.columnComment" size="small" :disabled="!can('tool:gen:edit')" />
            <a-input v-else-if="columnField(column) === 'javaField'" v-model:value="record.javaField" size="small" :disabled="!can('tool:gen:edit')" />
            <a-select
              v-else-if="columnField(column) === 'javaType'"
              v-model:value="record.javaType"
              size="small"
              :disabled="!can('tool:gen:edit')"
              :options="[
                { value: 'String', label: 'String' },
                { value: 'Integer', label: 'Integer' },
                { value: 'Long', label: 'Long' },
                { value: 'Double', label: 'Double' },
                { value: 'Float', label: 'Float' },
                { value: 'BigDecimal', label: 'BigDecimal' },
                { value: 'Boolean', label: 'Boolean' },
                { value: 'LocalDateTime', label: 'LocalDateTime' }
              ]"
            />
            <a-tag v-else-if="columnField(column) === 'isPk'" :color="record.isPk === '1' ? 'blue' : 'default'">{{ record.isPk === '1' ? '是' : '否' }}</a-tag>
            <a-tag v-else-if="columnField(column) === 'isIncrement'" :color="record.isIncrement === '1' ? 'blue' : 'default'">{{ record.isIncrement === '1' ? '是' : '否' }}</a-tag>
            <a-select
              v-else-if="['isRequired', 'isInsert', 'isEdit', 'isList', 'isQuery'].includes(columnField(column))"
              v-model:value="record[columnField(column)]"
              size="small"
              :disabled="!can('tool:gen:edit')"
              :options="[{ value: '1', label: '是' }, { value: '0', label: '否' }]"
            />
            <a-select
              v-else-if="columnField(column) === 'queryType'"
              v-model:value="record.queryType"
              size="small"
              :disabled="!can('tool:gen:edit') || record.isQuery !== '1'"
              :options="queryTypeOptions(record)"
            />
            <a-select
              v-else-if="columnField(column) === 'htmlType'"
              v-model:value="record.htmlType"
              size="small"
              :disabled="!can('tool:gen:edit')"
              :options="[
                { value: 'input', label: '文本输入' },
                { value: 'textarea', label: '多行文本' },
                { value: 'select', label: '下拉选择' },
                { value: 'radio', label: '单选' },
                { value: 'checkbox', label: '复选' },
                { value: 'datetime', label: '日期时间' },
                { value: 'switch', label: '开关' },
                { value: 'inputNumber', label: '数字输入' },
                { value: 'imageUpload', label: '图片上传' },
                { value: 'fileUpload', label: '文件上传' },
                { value: 'editor', label: '富文本' }
              ]"
            />
            <a-input
              v-else-if="columnField(column) === 'dictType' && isDictionaryControl(record.htmlType)"
              v-model:value="record.dictType"
              size="small"
              :disabled="!can('tool:gen:edit')"
              placeholder="字典类型编码"
            />
            <span v-else-if="columnField(column) === 'dictType'" class="muted-value">不适用</span>
            <a-input-number
              v-else-if="columnField(column) === 'sort'"
              v-model:value="record.sort"
              size="small"
              :min="0"
              :disabled="!can('tool:gen:edit')"
              style="width: 76px"
            />
            <span v-else class="muted-value">{{ record[columnField(column)] || '—' }}</span>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <div class="modal-footer">
      <a-button :disabled="editSaving" @click="closeEdit">取消</a-button>
      <a-button v-if="can('tool:gen:edit')" type="primary" :loading="editSaving" @click="saveConfig">保存配置</a-button>
    </div>
  </a-modal>

  <a-modal
    :open="uniquePickOpen"
    title="选择组合唯一字段"
    width="560px"
    :mask-closable="false"
    :keyboard="false"
    ok-text="确定"
    cancel-text="取消"
    @ok="form.uniqueFields = [...uniquePickValues]; uniquePickOpen = false"
    @cancel="uniquePickOpen = false"
  >
    <a-input v-model:value="uniqueSearch" allow-clear placeholder="搜索字段说明或 Java 属性" style="margin-bottom: 12px" />
    <div class="unique-picker-list">
      <label v-for="option in filteredUniqueOptions" :key="option.value">
        <a-checkbox
          :checked="uniquePickValues.includes(option.value)"
          @change="toggleUniqueField(option.value, $event.target.checked)"
        >
          {{ option.label }}
        </a-checkbox>
      </label>
      <a-empty v-if="!filteredUniqueOptions.length" description="没有匹配字段" />
    </div>
  </a-modal>

  <a-modal
    :open="previewOpen"
    title="生成代码预览"
    width="1180px"
    :footer="null"
    :mask-closable="false"
    :keyboard="false"
    @cancel="previewOpen = false"
  >
    <a-spin :spinning="previewLoading">
      <a-tabs v-if="previewKeys.length" v-model:active-key="previewKey" tab-position="left">
        <a-tab-pane v-for="path in previewKeys" :key="path" :tab="path">
          <pre class="code-preview">{{ previewFiles[path] }}</pre>
        </a-tab-pane>
      </a-tabs>
      <a-empty v-else description="暂无预览内容" />
    </a-spin>
  </a-modal>
</template>

<style scoped>
.import-toolbar,
.field-toolbar,
.unique-field-control {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.import-toolbar {
  margin-bottom: 10px;
}
.import-toolbar label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.selection-hint,
.muted-value {
  color: #86909c;
  font-size: 12px;
}
.selection-hint {
  margin-bottom: 8px;
}
.field-toolbar {
  justify-content: space-between;
  margin-bottom: 10px;
}
.unique-field-control {
  justify-content: space-between;
  min-height: 32px;
}
.unique-picker-list {
  max-height: 55vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
}
.code-preview {
  height: 66vh;
  overflow: auto;
  margin: 0;
  padding: 14px;
  background: #101828;
  color: #e6edf3;
  border-radius: 8px;
  font: 12px/1.6 ui-monospace, SFMono-Regular, monospace;
  white-space: pre;
}
</style>
