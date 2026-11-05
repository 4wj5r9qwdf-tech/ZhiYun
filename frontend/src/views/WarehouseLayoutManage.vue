<script setup lang="ts">
import '../assets/standard-list-fill.css'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { AppstoreOutlined, DatabaseOutlined, EnvironmentOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import { canEnter } from '../utils/perms'
import ImportResultTable from '../components/ImportResultTable.vue'
import BatchResultModal from '../components/BatchResultModal.vue'
import { createSavedFormDefaults } from '../utils/savedFormDefaults'
import { encodeCsv } from '../utils/csv'
import { importHeaders, prepareLocationImport, submitLocationImport, type LocationImportRow } from '../utils/warehouseLayoutImport'

const route = useRoute()
const router = useRouter()
const guideOpen = ref(false)

type Location = Record<string, any> & { children?: Location[] }
type Warehouse = { warehouse_id: string; warehouse_code: string; warehouse_name: string; inbound_location_level: number; status: string }
const ROOT = '__warehouse_root__'
const LOCATION_LEVEL = { WAREHOUSE: 0, ZONE: 1, RACK: 2, BIN: 3 } as const
const activeLevel = ref<number>(LOCATION_LEVEL.ZONE)
const activeTypeName = computed(() => levelName(activeLevel.value))
const warehouses = ref<Warehouse[]>([])
const selectedWarehouseId = ref<string>()
const treeRows = ref<Location[]>([])
const loading = ref(false)
const saving = ref(false)
const autoCodeEnabled = ref(true)
const suggestedCode = ref('')
const codeEditedByUser = ref(false)
let codePreviewRequest = 0
const keyword = ref('')
const statusFilter = ref<string>()
const selectedNodeKey = ref(ROOT)
const treeCollapsed = ref(false)
const colSetOpen = ref(false)
const dialogOpen = ref(false)
const editing = ref(false)
const formRef = ref()
const emptyLocationForm = () => ({ locationId: '', warehouseId: '', parentLocationId: undefined as string | undefined, locationLevel: 1, locationCode: '', locationName: '', agvReachable: true, capacityLimit: undefined as number | undefined, capacityUnit: '', locationStatus: 'active', externalLocationCode: '', remark: '' })
const form = reactive(emptyLocationForm())
const codeInput = ref()
const reusedPrevious = ref(false)
function savedDefaults(level: number) { return createSavedFormDefaults(sessionStorage, `warehouse_layout:last_saved:${sessionStorage.getItem('userId') || 'anon'}:${level}`, emptyLocationForm, ['locationId']) }
const statusOptions = [
  { value: 'active', label: '可用' },
  { value: 'disabled', label: '停用' },
  { value: 'maintenance', label: '维护中' },
]
const warehouseNodes = computed(() => warehouses.value.filter(warehouse => !selectedWarehouseId.value || warehouse.warehouse_id === selectedWarehouseId.value).map(warehouse => ({
  key: `warehouse:${warehouse.warehouse_id}`,
  title: `${warehouse.warehouse_code} / ${warehouse.warehouse_name}`,
  warehouse_id: warehouse.warehouse_id,
  locationLevel: LOCATION_LEVEL.WAREHOUSE,
  children: treeRows.value.filter(row => String(row.warehouse_id) === warehouse.warehouse_id),
})))
const rootNode = computed(() => ({ key: ROOT, title: '全部仓库', locationLevel: -1, children: warehouseNodes.value }))
const defaultTreeKey = computed(() => selectedWarehouseId.value ? `warehouse:${selectedWarehouseId.value}` : ROOT)
const treeData = computed(() => {
  const prune = (items: any[]): any[] => items.filter(row => Number(row.location_level || 0) <= activeLevel.value).map(row => ({ ...row, children: row.children?.length ? prune(row.children) : undefined }))
  return prune([rootNode.value])
})
const nodeMap = computed(() => {
  const map = new Map<string, any>()
  map.set(ROOT, rootNode.value)
  warehouseNodes.value.forEach(row => map.set(row.key, row))
  const visit = (rows: Location[]) => rows.forEach(row => { map.set(String(row.location_id), row); visit(row.children || []) })
  visit(treeRows.value)
  return map
})
const selectedNode = computed<any>(() => nodeMap.value.get(selectedNodeKey.value) || rootNode.value)
const flattened = computed(() => {
  const all: Location[] = []
  const visit = (rows: Location[]) => rows.forEach(row => { all.push(row); visit(row.children || []) })
  visit(treeRows.value)
  return all
})
function ancestorAt(row: Location, level: number): Location | undefined {
  let current: Location | undefined = row
  const seen = new Set<string>()
  while (current && Number(current.location_level) >= level) {
    if (Number(current.location_level) === level) return current
    const key = String(current.location_id)
    if (seen.has(key)) return undefined
    seen.add(key)
    current = nodeMap.value.get(String(current.parent_location_id))
  }
}
const rows = computed(() => {
  const q = keyword.value.trim().toLocaleLowerCase()
  const node = selectedNode.value
  return flattened.value.filter(row => {
    if (Number(row.location_level) !== activeLevel.value) return false
    if (selectedWarehouseId.value && String(row.warehouse_id) !== selectedWarehouseId.value) return false
    if (selectedNodeKey.value !== ROOT) {
      if (String(node.warehouse_id) !== String(row.warehouse_id)) return false
      if (node.location_id && String(ancestorAt(row, Number(node.location_level))?.location_id) !== String(node.location_id)) return false
    }
    if (statusFilter.value && row.location_status !== statusFilter.value) return false
    return !q || [row.location_code, row.location_name, row.warehouse_name, row.parent_location_name, row.external_location_code].join(' ').toLocaleLowerCase().includes(q)
  }).map(({ children, ...row }) => row)
})
const navigationItems = [
  { level: LOCATION_LEVEL.ZONE, name: '库区', description: '划分仓库内部区域', icon: AppstoreOutlined },
  { level: LOCATION_LEVEL.RACK, name: '货架', description: '管理库区内的存放架', icon: DatabaseOutlined },
  { level: LOCATION_LEVEL.BIN, name: '货位', description: '定位货架上的存放点', icon: EnvironmentOutlined },
]
const scopeRows = computed(() => flattened.value.filter(row => {
  if (selectedWarehouseId.value && String(row.warehouse_id) !== selectedWarehouseId.value) return false
  const node = selectedNode.value
  if (selectedNodeKey.value === ROOT) return true
  if (String(node.warehouse_id) !== String(row.warehouse_id)) return false
  return !node.location_id || String(ancestorAt(row, Number(node.location_level))?.location_id) === String(node.location_id)
}))
function typeCount(level: number) { return scopeRows.value.filter(row => Number(row.location_level) === level).length }
const scopeLabel = computed(() => selectedNodeKey.value === ROOT ? '全部仓库' : selectedNode.value.title || '当前范围')
const prerequisiteMissing = computed(() => activeLevel.value > LOCATION_LEVEL.ZONE && !scopeRows.value.some(row => Number(row.location_level) === activeLevel.value - 1 && row.location_status === 'active'))
const emptyDescription = computed(() => {
  if (keyword.value || statusFilter.value) return `没有符合筛选条件的${activeTypeName.value}，可重置后查看`
  if (!warehouses.value.length) return '请先建立仓库，再维护仓内结构'
  if (prerequisiteMissing.value) return `当前范围还没有可用的${levelName(activeLevel.value - 1)}，请先建立上级档案`
  return `当前范围尚未建立${activeTypeName.value}`
})
function createPrerequisite() { activeLevel.value -= 1; switchLevel(); openAdd() }
function goWarehouse() { guideOpen.value = false; void router.push({ name: 'WarehouseManage' }) }
const columns = computed(() => [
  { title: '序号', key: 'index', width: 72 },
  { title: '所属仓库', dataIndex: 'warehouse_name', key: 'warehouse', width: 180 },
  { title: `${activeTypeName.value}编码`, dataIndex: 'location_code', key: 'location_code', width: 150, sorter: (a: Location, b: Location) => String(a.location_code).localeCompare(String(b.location_code), 'zh-CN') },
  { title: `${activeTypeName.value}名称`, dataIndex: 'location_name', key: 'location_name', width: 180, sorter: (a: Location, b: Location) => String(a.location_name).localeCompare(String(b.location_name), 'zh-CN') },
  ...(activeLevel.value > LOCATION_LEVEL.ZONE ? [{ title: `所属${levelName(activeLevel.value - 1)}`, key: 'parent', width: 180 }] : []),
  { title: 'AGV可达', key: 'agv', width: 100 },
  { title: '容量', key: 'capacity', width: 140 },
  { title: '状态', key: 'status', width: 140 },
  { title: '备注', dataIndex: 'remark', key: 'remark', width: 180 },
  { title: '操作', key: 'action', width: 230, fixed: 'right' as const },
])
type ColumnState = { key: string; visible: boolean; width?: number }
const colState = ref<ColumnState[]>([])
const columnStoreKey = computed(() => `warehouse_location_columns:${activeLevel.value}:${sessionStorage.getItem('userId') || 'anon'}`)
const tableColumns = computed(() => colState.value.flatMap(state => {
  const column = columns.value.find(item => item.key === state.key)
  return column && state.visible ? [{ ...column, width: state.width || column.width,
    customHeaderCell: () => ({ onMousedown: (event: MouseEvent) => resizeColumn(event, state.key) }),
  }] : []
}))
const page = ref(1)
const pageSize = ref(20)
const formZoneId = ref<string>()
const expandedKeys = ref<string[]>([ROOT])
const showAdvanced = ref(false)
function persistColumns() { try { localStorage.setItem(columnStoreKey.value, JSON.stringify(colState.value)) } catch { /* 浏览器存储不可用时仍可操作 */ } }
function loadColState() {
  let saved: ColumnState[] = []
  try { saved = JSON.parse(localStorage.getItem(columnStoreKey.value) || '[]') } catch { /* 使用默认列 */ }
  if (!Array.isArray(saved)) saved = []
  const defaults = columns.value.map(column => ({ key: column.key, visible: !['agv', 'capacity'].includes(column.key) }))
  colState.value = [...saved.filter(item => defaults.some(column => column.key === item.key)), ...defaults.filter(item => !saved.some(column => column.key === item.key))]
}
function toggleCol(key: string) { const item = colState.value.find(column => column.key === key); if (item) { item.visible = !item.visible; persistColumns() } }
function moveCol(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= colState.value.length) return
  const item = colState.value.splice(index, 1)[0]!
  colState.value.splice(target, 0, item)
  persistColumns()
}
function showAllCols() { colState.value = columns.value.map(column => ({ key: column.key, visible: !['agv', 'capacity'].includes(column.key) })); persistColumns() }
let stopResize = () => {}
function resizeColumn(event: MouseEvent, key: string) {
  const header = (event.target as HTMLElement).closest('th')
  if (!header || header.getBoundingClientRect().right - event.clientX > 14) return
  event.preventDefault()
  stopResize()
  const startX = event.clientX, width = header.getBoundingClientRect().width
  const move = (next: MouseEvent) => { const state = colState.value.find(item => item.key === key); if (state) state.width = Math.max(72, width + next.clientX - startX) }
  stopResize = () => { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', stopResize); persistColumns() }
  document.addEventListener('mousemove', move)
  document.addEventListener('mouseup', stopResize)
}
onBeforeUnmount(() => stopResize())
const parentOptions = computed(() => flattened.value.filter(row => String(row.warehouse_id) === form.warehouseId && Number(row.location_level) === form.locationLevel - 1 && row.location_status === 'active' && (form.locationLevel !== LOCATION_LEVEL.BIN || String(row.parent_location_id) === formZoneId.value)).map(row => ({ value: String(row.location_id), label: `${row.location_code} / ${row.location_name}` })))
const zoneOptions = computed(() => flattened.value.filter(row => String(row.warehouse_id) === form.warehouseId && Number(row.location_level) === LOCATION_LEVEL.ZONE && row.location_status === 'active').map(row => ({ value: String(row.location_id), label: `${row.location_code} / ${row.location_name}` })))
function changeFormWarehouse() { formZoneId.value = undefined; form.parentLocationId = undefined }
function changeFormZone() { form.parentLocationId = undefined }
async function refreshCodePreview() {
  const request = ++codePreviewRequest
  suggestedCode.value = ''
  if (!dialogOpen.value || editing.value || !autoCodeEnabled.value || !form.warehouseId || (form.locationLevel > LOCATION_LEVEL.ZONE && !form.parentLocationId)) {
    if (!codeEditedByUser.value) form.locationCode = ''
    return
  }
  try {
    const res: any = await api.get('/system/warehouse-location/next-code', { params: { warehouseId: form.warehouseId, parentLocationId: form.parentLocationId, locationLevel: form.locationLevel } })
    if (request !== codePreviewRequest || !dialogOpen.value || editing.value) return
    if (res.code !== 200) throw new Error(res.msg || '获取建议编码失败')
    suggestedCode.value = String(res.data || '')
    if (!codeEditedByUser.value) form.locationCode = suggestedCode.value
  } catch (error: any) {
    if (request === codePreviewRequest) message.error(error?.response?.data?.msg || error.message || '获取建议编码失败')
  }
}
watch([dialogOpen, () => form.warehouseId, () => form.parentLocationId, () => form.locationLevel, autoCodeEnabled], () => {
  if (!dialogOpen.value || editing.value) { codePreviewRequest++; return }
  codeEditedByUser.value = false
  void refreshCodePreview()
})
function switchLevel() {
  keyword.value = ''; statusFilter.value = undefined; page.value = 1
  if (Number(selectedNode.value.location_level || 0) > activeLevel.value) selectedNodeKey.value = defaultTreeKey.value
  loadColState()
}
function viewChildren(row: Location) {
  activeLevel.value = Number(row.location_level) + 1
  selectedNodeKey.value = String(row.location_id)
  keyword.value = ''; statusFilter.value = undefined; page.value = 1
  expandedKeys.value = [...new Set([...expandedKeys.value, ROOT, `warehouse:${row.warehouse_id}`, String(row.location_id)])]
  loadColState()
}
watch([keyword, statusFilter, selectedNodeKey, selectedWarehouseId], () => { page.value = 1 })
const formTypeName = computed(() => levelName(form.locationLevel))
const rules = computed(() => ({
  warehouseId: [{ required: true, message: '请选择所属仓库' }],
  parentLocationId: [{ required: form.locationLevel > LOCATION_LEVEL.ZONE, message: `请选择所属${levelName(form.locationLevel - 1)}` }],
  locationCode: [{ required: !autoCodeEnabled.value || editing.value, message: `请输入${formTypeName.value}编码` }],
  locationName: [{ required: true, message: `请输入${formTypeName.value}名称` }],
}))
function levelName(level: number) {
  return ({
    [LOCATION_LEVEL.WAREHOUSE]: '仓库',
    [LOCATION_LEVEL.ZONE]: '库区',
    [LOCATION_LEVEL.RACK]: '货架',
    [LOCATION_LEVEL.BIN]: '货位',
  } as Record<number, string>)[level] || '未知'
}
function warehouseLabel(id: string) {
  const item = warehouses.value.find(row => row.warehouse_id === String(id))
  return item ? `${item.warehouse_code} / ${item.warehouse_name}` : ''
}
async function resetFilters() {
  selectedWarehouseId.value = undefined
  keyword.value = ''
  statusFilter.value = undefined
  selectedNodeKey.value = defaultTreeKey.value
  await loadTree()
}
function resetTreeSelection() { selectedNodeKey.value = defaultTreeKey.value }
function activeWarehouseId(node: any = selectedNode.value) {
  return selectedWarehouseId.value || (node?.warehouse_id ? String(node.warehouse_id) : undefined)
}
function clearForm(warehouseId = activeWarehouseId()) {
  Object.assign(form, { locationId: '', warehouseId: warehouseId || '', parentLocationId: undefined, locationLevel: 1, locationCode: '', locationName: '', agvReachable: true, capacityLimit: undefined, capacityUnit: '', locationStatus: 'active', externalLocationCode: '', remark: '' })
}
function openAdd(usePrevious = true) {
  editing.value = false
  Object.assign(form, usePrevious ? savedDefaults(activeLevel.value).load() : emptyLocationForm(), { locationId: '', locationLevel: activeLevel.value })
  reusedPrevious.value = usePrevious && Boolean(form.locationCode || form.locationName)
  if (autoCodeEnabled.value) form.locationCode = ''
  suggestedCode.value = ''
  codeEditedByUser.value = false
  const scopedWarehouse = activeWarehouseId()
  if (scopedWarehouse && scopedWarehouse !== form.warehouseId) form.parentLocationId = undefined
  if (scopedWarehouse) form.warehouseId = scopedWarehouse
  if (!warehouses.value.some(row => row.warehouse_id === form.warehouseId && row.status === '0')) { form.warehouseId = ''; form.parentLocationId = undefined }
  formZoneId.value = undefined
  const node = selectedNode.value
  if (node.location_id) {
    const parent = ancestorAt(node, activeLevel.value - 1)
    if (parent?.location_status === 'active') form.parentLocationId = String(parent.location_id)
    if (activeLevel.value === LOCATION_LEVEL.BIN && Number(node.location_level) === LOCATION_LEVEL.ZONE) {
      formZoneId.value = String(node.location_id)
      if (String(nodeMap.value.get(String(form.parentLocationId))?.parent_location_id) !== formZoneId.value) form.parentLocationId = undefined
    }
  }
  if (form.locationLevel === LOCATION_LEVEL.ZONE) form.parentLocationId = undefined
  const parent = nodeMap.value.get(String(form.parentLocationId))
  if (!parent || parent.location_status !== 'active' || String(parent.warehouse_id) !== form.warehouseId || Number(parent.location_level) !== form.locationLevel - 1) form.parentLocationId = undefined
  if (form.locationLevel === LOCATION_LEVEL.BIN && form.parentLocationId) formZoneId.value = String(parent.parent_location_id)
  showAdvanced.value = false
  dialogOpen.value = true
  void nextTick(() => { formRef.value?.clearValidate(); codeInput.value?.focus({ cursor: 'all' }) })
}
function openEdit(row: Location) {
  editing.value = true
  clearForm()
  Object.assign(form, {
    locationId: String(row.location_id), warehouseId: String(row.warehouse_id),
    parentLocationId: row.parent_location_id == null ? undefined : String(row.parent_location_id),
    locationLevel: Number(row.location_level), locationCode: row.location_code || '', locationName: row.location_name || '',
    agvReachable: row.agv_reachable !== false, capacityLimit: row.capacity_limit == null ? undefined : Number(row.capacity_limit),
    capacityUnit: row.capacity_unit || '', locationStatus: row.location_status || 'active',
    externalLocationCode: row.external_location_code || '', remark: row.remark || '',
  })
  formZoneId.value = ancestorAt(row, LOCATION_LEVEL.ZONE)?.location_id
  showAdvanced.value = false
  dialogOpen.value = true
  void nextTick(() => formRef.value?.clearValidate())
}
async function loadTree() {
  loading.value = true
  try {
    const res: any = await api.get('/system/warehouse-location/tree', {})
    if (res.code !== 200) throw new Error(res.msg || '加载仓储位置失败')
    const normalize = (items: Location[]): Location[] => items.map(row => ({ ...row, key: String(row.location_id), title: `${row.location_code} / ${row.location_name}`, children: row.children?.length ? normalize(row.children) : undefined }))
    treeRows.value = normalize(res.data || [])
    if (!nodeMap.value.has(selectedNodeKey.value)) selectedNodeKey.value = ROOT
  } catch (error: any) {
    treeRows.value = []
    message.error(error?.response?.data?.msg || error.message || '加载仓储位置失败')
  } finally { loading.value = false }
}
async function loadOptions() {
  try {
    const res: any = await api.get('/system/warehouse-location/options')
    if (res.code !== 200) throw new Error(res.msg || '加载仓库失败')
    warehouses.value = (res.data || []).map((row: any) => ({ ...row, warehouse_id: String(row.warehouse_id), inbound_location_level: Number(row.inbound_location_level ?? 2) }))
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '加载仓库失败') }
}
async function loadCodeRule() {
  try {
    const res: any = await api.get('/system/config/configKey/inventory.warehouseLayout.codeRule')
    if (res.code === 200 && res.data) autoCodeEnabled.value = JSON.parse(String(res.data)).enabled !== false
  } catch { /* 新参数尚未保存时，后端默认启用自动编码 */ }
}
async function save(continueAdding = false) {
  if (saving.value) return
  saving.value = true
  try {
    await formRef.value?.validate()
    if (form.capacityLimit !== undefined && form.capacityLimit !== null && !form.capacityUnit.trim()) { message.warning('填写容量上限时请同时填写容量单位'); return }
    const payload = { ...form, autoGeneratedCode: !editing.value && autoCodeEnabled.value ? suggestedCode.value : '', locationCode: form.locationCode }
    const res: any = editing.value ? await api.put('/system/warehouse-location', payload) : await api.post('/system/warehouse-location', payload)
    if (res.code !== 200) throw new Error(res.msg || '保存仓储位置失败')
    savedDefaults(form.locationLevel).save({ ...form, locationCode: autoCodeEnabled.value ? '' : form.locationCode })
    const createdCode = !editing.value && typeof res.data === 'string' ? res.data : ''
    message.success(editing.value ? '修改成功' : createdCode ? `新增成功，编码：${createdCode}` : '新增成功')
    if (continueAdding && !editing.value) {
      form.locationId = ''
      if (autoCodeEnabled.value) form.locationCode = ''
      suggestedCode.value = ''
      codeEditedByUser.value = false
      reusedPrevious.value = true
      await nextTick()
      formRef.value?.clearValidate()
      codeInput.value?.focus({ cursor: 'all' })
    } else dialogOpen.value = false
    await loadTree()
    if (continueAdding && !editing.value) void refreshCodePreview()
  } catch (error: any) {
    if (error?.errorFields) { const name = error.errorFields[0]?.name; if (name) formRef.value?.scrollToField(name); return }
    message.error(error?.response?.data?.msg || error.message || '保存仓储位置失败')
  } finally { saving.value = false }
}
async function changeStatus(row: Location, next: string) {
  try {
    const res: any = await api.put(`/system/warehouse-location/${row.location_id}/status`, { locationStatus: next })
    if (res.code !== 200) throw new Error(res.msg || '修改状态失败')
    message.success(`${levelName(Number(row.location_level))}状态已更新`)
    await loadTree()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '修改状态失败') }
}
function remove(row: Location) {
  Modal.confirm({
    title: `删除${levelName(Number(row.location_level))}`,
    content: `确认删除“${row.location_name}（${row.location_code}）”吗？有子级或库存引用的位置不能删除。`,
    okText: '确认删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try {
      const res: any = await api.delete(`/system/warehouse-location/${row.location_id}`)
      if (res.code !== 200) throw new Error(res.msg || '删除失败')
      message.success('删除成功')
      await loadTree()
      } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '删除失败'); throw error }
    },
  })
}
const importOpen = ref(false)
const importLevel = ref<number>(LOCATION_LEVEL.ZONE)
const importTypeName = computed(() => levelName(importLevel.value))
const importInput = ref<HTMLInputElement>()
const importRows = ref<LocationImportRow[]>([])
const uploadedHeaders = ref<string[]>([])
const importReading = ref(false)
const importing = ref(false)
const importProgress = ref({ done: 0, total: 0 })
const importFileName = ref('')
const importResultOpen = ref(false)
const importSucceeded = ref(0)
const importFailures = ref<{ name: string; reason: string }[]>([])
const importParentColumns = computed(() => [
  { title: '仓库编码', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 120 },
  ...(importLevel.value > 1 ? [{ title: '库区编码', dataIndex: 'zoneCode', key: 'zoneCode', width: 120 }] : []),
  ...(importLevel.value > 2 ? [{ title: '货架编码', dataIndex: 'rackCode', key: 'rackCode', width: 120 }] : []),
])
onBeforeRouteLeave(() => { if (importing.value || saving.value) { message.warning('正在保存，请等待完成后再离开'); return false } })
const importValidCount = computed(() => importRows.value.filter(row => !row.error && row.outcome === 'pending').length)
function downloadCsv(name: string, records: string[][]) {
  const url = URL.createObjectURL(new Blob([encodeCsv(records)], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); URL.revokeObjectURL(url)
}
async function openImport() {
  importLevel.value = activeLevel.value
  importRows.value = []; uploadedHeaders.value = []; importFileName.value = ''; importProgress.value = { done: 0, total: 0 }
  importOpen.value = true
  importReading.value = true
  try { await reload() } finally { importReading.value = false }
}
function downloadImportTemplate() {
  const headers = importHeaders(importLevel.value)
  const example: Record<string, string> = { 仓库编码: 'WH001', 库区编码: 'Z001', 货架编码: 'R001', 货位编码: 'B001', 库区名称: '面料存储区', 货架名称: '1号货架', 货位名称: '第1层第1格', 状态: '可用', 备注: '', AGV可达: '是' }
  const node = selectedNode.value
  const wid = activeWarehouseId()
  const warehouse = warehouses.value.find(row => row.warehouse_id === wid)
  if (warehouse) example['仓库编码'] = warehouse.warehouse_code
  if (node.location_id) {
    const zone = ancestorAt(node, LOCATION_LEVEL.ZONE), rack = ancestorAt(node, LOCATION_LEVEL.RACK)
    if (importLevel.value > LOCATION_LEVEL.ZONE && zone) example['库区编码'] = zone.location_code
    if (importLevel.value > LOCATION_LEVEL.RACK && rack) example['货架编码'] = rack.location_code
  }
  downloadCsv(`${importTypeName.value}导入模板.csv`, [headers, headers.map(name => example[name] || '')])
}
async function readImport(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file || importing.value || importReading.value) return
  importReading.value = true; importRows.value = []; uploadedHeaders.value = []; importFileName.value = ''
  try {
    if (!file.name.toLowerCase().endsWith('.csv')) throw new Error('请选择 UTF-8 CSV 文件，Excel 文件请另存为 CSV UTF-8')
    if (file.size > 5 * 1024 * 1024) throw new Error('文件不能超过5MB，请分批导入')
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
    const result = prepareLocationImport(text, importLevel.value, warehouses.value, flattened.value.map(row => ({ location_id: String(row.location_id), warehouse_id: String(row.warehouse_id), parent_location_id: row.parent_location_id == null ? undefined : String(row.parent_location_id), location_level: Number(row.location_level), location_code: String(row.location_code), location_status: String(row.location_status), external_location_code: row.external_location_code })), autoCodeEnabled.value)
    importRows.value = result.rows; uploadedHeaders.value = result.headers; importFileName.value = file.name
  } catch (error: any) { message.error(error instanceof TypeError ? '文件编码无法识别，请另存为 CSV UTF-8 后重试' : error.message || '读取文件失败') }
  finally { importReading.value = false; input.value = '' }
}
function confirmImport() {
  if (importing.value || !importValidCount.value) return
  Modal.confirm({ title: `确认导入${importTypeName.value}`, content: `将新增 ${importValidCount.value} 条记录，${importRows.value.length - importValidCount.value} 条校验失败记录将跳过。已有档案不会被覆盖。`, okText: '确认导入', cancelText: '返回检查', maskClosable: false, keyboard: false, onOk: runImport })
}
async function runImport() {
  if (importing.value) return
  importing.value = true
  try {
    const result = await submitLocationImport(importRows.value, async payload => {
      const response: any = await api.post('/system/warehouse-location', payload)
      return response
    }, (done, total) => { importProgress.value = { done, total } })
    importSucceeded.value = result.succeeded; importFailures.value = result.failures
    importOpen.value = false; importResultOpen.value = true
    await loadTree()
  } finally { importing.value = false }
}
function downloadFailures() {
  const indices = uploadedHeaders.value.map((name, index) => ({ name, index })).filter(item => item.name !== '失败原因')
  const submitted = importRows.value.some(row => row.outcome !== 'pending')
  const failed = importRows.value.filter(row => row.outcome !== 'success' && (submitted || row.error))
  downloadCsv(`${importTypeName.value}未导入记录.csv`, [[...indices.map(item => item.name), '失败原因'], ...failed.map(row => [...indices.map(item => row.source[item.index] || ''), row.error || '未处理'])])
}
async function reload() { await loadOptions(); await loadTree() }
async function onWarehouseChange(value: any) { selectedWarehouseId.value = value || undefined; keyword.value = ''; statusFilter.value = undefined; selectedNodeKey.value = defaultTreeKey.value; await loadTree() }
onMounted(async () => { loadColState(); await Promise.all([loadOptions(), loadCodeRule()]); const requested = String(route.query.warehouseId || ''); if (warehouses.value.some(row => row.warehouse_id === requested)) { selectedWarehouseId.value = requested; selectedNodeKey.value = defaultTreeKey.value }; await loadTree(); expandedKeys.value = [ROOT, ...warehouseNodes.value.map(row => row.key)] })
</script>

<template>
  <div class="tpl-container">
  <StandardListLayout v-model:collapsed="treeCollapsed">
    <template #filters>
      <div class="location-filters">
      <a-tabs v-model:active-key="activeLevel" class="location-navigation" type="line" @change="switchLevel">
        <a-tab-pane v-for="item in navigationItems" :key="item.level">
          <template #tab>
            <span class="location-nav-icon"><component :is="item.icon" /></span>
            <span class="location-nav-text"><span class="location-nav-title">{{ item.name }}<span class="location-nav-count">{{ typeCount(item.level) }}</span></span><span class="location-nav-description">{{ item.description }}</span></span>
          </template>
        </a-tab-pane>
      </a-tabs>
      <div class="location-context"><span>建档顺序：仓库 <span class="hierarchy-arrow">›</span> 库区 <span class="hierarchy-arrow">›</span> 货架 <span class="hierarchy-arrow">›</span> 货位</span><span>当前查看：{{ scopeLabel }} · {{ activeTypeName }}</span></div>
      <div class="head-filters">
        <a-select v-model:value="selectedWarehouseId" allow-clear show-search option-filter-prop="label" :options="warehouses.map(item => ({ label: `${item.warehouse_code} / ${item.warehouse_name}`, value: item.warehouse_id }))" placeholder="全部仓库" style="width: 260px" @change="onWarehouseChange" />
        <a-input v-model:value="keyword" allow-clear :placeholder="`搜索${activeTypeName}编码、名称或上级名称`" style="width: 280px" @press-enter="reload" />
        <a-select v-model:value="statusFilter" allow-clear placeholder="状态" style="width: 130px" :options="statusOptions" />
      </div>
      </div>
    </template>
    <template #actions>
      <div class="filter-bar">
        <a-button v-if="canEnter('inventory:location:add')" type="primary" :disabled="loading || !warehouses.length" @click="openAdd()">新增{{ activeTypeName }}</a-button>
        <a-button type="primary" @click="reload">查询</a-button>
        <a-button @click="resetFilters">重置</a-button>
        <span class="filter-spacer"></span>
        <a-button v-if="canEnter('inventory:location:add')" :disabled="loading" @click="openImport">↑ 导入{{ activeTypeName }}</a-button>
        <a-button @click="guideOpen = true"><QuestionCircleOutlined />使用指引</a-button>
        <span class="location-tip">{{ selectedWarehouseId ? `入库定位：至少记录到${['仓库','库区','货架','货位'][warehouses.find(item => item.warehouse_id === selectedWarehouseId)?.inbound_location_level ?? 2]}；可继续填写更细位置` : '全部仓库' }}</span>
        <a-dropdown v-model:open="colSetOpen" :trigger="['click']">
          <a-button>⚙ 列设置</a-button>
          <template #overlay>
            <a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }">
              <div v-for="(column, index) in colState" :key="column.key" class="col-set-item">
                <a-checkbox :checked="column.visible" @change="toggleCol(column.key)">{{ columns.find(item => item.key === column.key)?.title }}</a-checkbox>
                <span><a-button type="text" size="small" :disabled="index === 0" @click="moveCol(index, -1)">↑</a-button><a-button type="text" size="small" :disabled="index === colState.length - 1" @click="moveCol(index, 1)">↓</a-button></span>
              </div>
              <a-divider style="margin: 6px 0" />
              <div class="col-set-foot">
                <a size="small" @click="showAllCols">恢复默认</a>
                <a-button type="primary" size="small" @click="colSetOpen = false">完成</a-button>
              </div>
            </a-card>
          </template>
        </a-dropdown>
      </div>
    </template>
    <template #sidebar>
      <div class="tree-head"><strong class="tree-title">仓内位置</strong><a v-if="selectedNodeKey !== defaultTreeKey" class="tree-clear" @click="resetTreeSelection">重置</a></div>
      <a-tree
        block-node
        :tree-data="treeData"
        :selected-keys="[selectedNodeKey]"
        v-model:expanded-keys="expandedKeys"
        :field-names="{ title: 'title', key: 'key', children: 'children' }"
        @select="(keys:any[]) => { if (keys.length) selectedNodeKey = String(keys[0]) }"
      >
        <template #title="node">
          <span class="tree-node-title">{{ node.title || `${node.location_code} ${node.location_name}` }}</span>
          <a-tag v-if="node.locationLevel > 0 || node.location_level" class="level-tag">{{ levelName(Number(node.locationLevel ?? node.location_level)) }}</a-tag>
        </template>
      </a-tree>
      <a-empty v-if="!warehouses.length" description="暂无可用仓库" />
    </template>
    <div class="table-card">
      <a-table :columns="tableColumns" :data-source="rows" :loading="loading" :pagination="{ current: page, pageSize, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条`, onChange: (current: number, size: number) => { page = current; pageSize = size } }" row-key="location_id" :scroll="{ x: tableColumns.reduce((sum, column) => sum + column.width, 0), y: 1 }" size="middle" class="tpl-table zy-fill-table">
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'index'">{{ (page - 1) * pageSize + index + 1 }}</template>
          <template v-else-if="column.key === 'level'">{{ levelName(Number(record.location_level)) }}</template>
          <template v-else-if="column.key === 'parent'">{{ record.parent_location_name || '—' }}</template>
          <template v-else-if="column.key === 'agv'">{{ record.agv_reachable ? '可达' : '不可达' }}</template>
          <template v-else-if="column.key === 'capacity'">{{ record.capacity_limit == null ? '—' : `${record.capacity_limit} ${record.capacity_unit || ''}` }}</template>
          <template v-else-if="column.key === 'status'">
            <a-select :value="record.location_status" size="small" style="width:110px" :options="statusOptions" :disabled="!canEnter('inventory:location:edit')" @change="(value:any) => changeStatus(record,String(value))" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-if="canEnter('inventory:location:edit')" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="Number(record.location_level) < LOCATION_LEVEL.BIN" type="link" size="small" @click="viewChildren(record)">查看{{ levelName(Number(record.location_level) + 1) }}</a-button>
              <a-button v-if="canEnter('inventory:location:remove')" danger type="link" size="small" @click="remove(record)">删除</a-button>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty :description="emptyDescription">
            <a-button v-if="!warehouses.length && canEnter('inventory:warehouse:list')" type="primary" @click="goWarehouse">前往仓库管理</a-button>
            <a-button v-else-if="keyword || statusFilter" @click="resetFilters">重置筛选</a-button>
            <a-button v-else-if="prerequisiteMissing && canEnter('inventory:location:add')" type="primary" @click="createPrerequisite">先建{{ levelName(activeLevel - 1) }}</a-button>
            <a-button v-else-if="canEnter('inventory:location:add')" type="primary" @click="openAdd()">新增{{ activeTypeName }}</a-button>
          </a-empty>
        </template>
        <template #summary><a-table-summary fixed><a-table-summary-row><a-table-summary-cell :index="0" :col-span="tableColumns.length" class="summary-cell">合计（当前筛选） {{ rows.length }} 条</a-table-summary-cell></a-table-summary-row></a-table-summary></template>
      </a-table>
    </div>
  </StandardListLayout>
  </div>

  <a-modal v-model:open="importOpen" :title="`导入${importTypeName}`" :width="860" :footer="null" :mask-closable="false" :keyboard="false" :closable="!importing && !importReading">
    <a-alert type="info" show-icon :message="`下载${importTypeName}模板，填写后上传 CSV UTF-8 文件`" description="按编码识别所属仓库及父级档案，请先完成上级建档。模板中的示例行请替换为真实数据；编码列建议在 Excel 中设为文本。每次最多1000条。" />
    <a-space style="margin:16px 0"><a-button :disabled="importing || importReading" @click="downloadImportTemplate">下载模板</a-button><a-button type="primary" :loading="importReading" :disabled="importing" @click="importInput?.click()">选择文件</a-button><span>{{ importFileName }}</span></a-space>
    <input ref="importInput" type="file" accept=".csv,text/csv" style="display:none" @change="readImport" />
    <ImportResultTable :rows="importRows" :extra-columns="importParentColumns" />
    <a-button v-if="importRows.some(row => row.error)" :disabled="importing" @click="downloadFailures">下载校验失败记录</a-button>
    <p v-if="importRows.length">只新增校验通过的记录，失败原因显示在对应行。确认后仍会逐条进行服务端校验，导入结果会列出成功数与失败原因。</p>
    <p v-if="importing">正在导入 {{ importProgress.done }} / {{ importProgress.total || importValidCount }} 条，请等待完成。</p>
    <div class="guide-footer"><a-button :disabled="importing || importReading" @click="importOpen = false">取消</a-button><a-button type="primary" :loading="importing" :disabled="!importValidCount || importReading" @click="confirmImport">导入有效记录（{{ importValidCount }}）</a-button></div>
  </a-modal>
  <BatchResultModal v-model:open="importResultOpen" :title="`${importTypeName}导入结果`" :succeeded="importSucceeded" :failures="importFailures" :mask-closable="false" :keyboard="false"><template #actions><a-button v-if="importFailures.length" @click="downloadFailures">下载未导入记录</a-button></template></BatchResultModal>
  <a-modal v-model:open="guideOpen" title="仓库布局使用指引" :width="760" :footer="null" :mask-closable="false" :keyboard="false">
    <a-alert type="info" show-icon message="先建仓库，再按库区 → 货架 → 货位逐层维护" description="例如：一车间面料仓 → 面料存储区 → A01货架 → A01-02-03货位。每个位置都能找到它所属的仓库与上级。" />
    <a-steps class="guide-steps" direction="vertical" :current="-1">
      <a-step title="仓库：在哪里存货" description="在仓库管理建立面料仓、辅料仓或样衣仓，并设置入库至少记录到哪一级。" />
      <a-step title="库区：仓库里如何分区" description="在库区页签新增面料存储区、辅料区等，选择所属仓库；同一仓库下可建多个并列库区。" />
      <a-step title="货架：库区里放在哪个架子" description="在货架页签选择所属仓库和库区；也可从库区行点击查看货架，带着归属范围继续建档。" />
      <a-step title="货位：货架上具体放在哪里" description="在货位页签选择库区和货架，建立具体存放点；例如第2层第3格。可从货架行点击查看货位。" />
    </a-steps>
    <a-divider />
    <p>左侧树和仓库筛选限定查看范围；顶部三个入口决定维护哪一类档案。点击重置可回到全部仓库，页签里的新增按钮始终新增当前类型。</p>
    <p>入库定位要求与建档层级分别设置：仓库要求记到货架时，入库至少选到货架；仍然可以维护和选用更细的货位。</p>
    <p>重复建档可使用导入模板；手动录入时用“保存并新增”，内容保留在表单中，修改编码和名称后可继续保存。</p>
    <p>停用请从货位向上处理，启用请从库区向下处理。有子级或业务引用的档案不能删除，可保留档案并按规则停用。</p>
    <div class="guide-footer"><a-button v-if="canEnter('inventory:warehouse:list')" @click="goWarehouse">打开仓库管理</a-button><a-button type="primary" @click="guideOpen = false">知道了，开始维护</a-button></div>
  </a-modal>
  <a-modal v-model:open="dialogOpen" :title="`${editing ? '编辑' : '新增'}${formTypeName}`" :width="720" :mask-closable="false" :keyboard="false" :confirm-loading="saving" :closable="!saving" :footer="null">
    <div v-if="!editing" class="form-reuse-tip"><span>{{ reusedPrevious ? '已带入上次成功保存的内容，可修改编码和名称后继续保存。' : '连续录入可使用“保存并新增”，保存后继续保留当前内容。' }}</span><a-button type="link" size="small" :disabled="saving" @click="openAdd(false)">清空内容</a-button></div>
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="location-form">
      <a-row :gutter="16">
        <a-col :span="12"><a-form-item label="所属仓库" name="warehouseId">
          <a-input v-if="editing" :value="warehouseLabel(form.warehouseId)" disabled />
          <a-select v-else v-model:value="form.warehouseId" show-search option-filter-prop="label" placeholder="请选择仓库" :options="warehouses.map(item => ({ label: `${item.warehouse_code} / ${item.warehouse_name}`, value: item.warehouse_id, disabled: item.status !== '0' }))" @change="changeFormWarehouse" />
        </a-form-item></a-col>
        <a-col v-if="form.locationLevel === LOCATION_LEVEL.BIN" :span="12"><a-form-item label="所属库区">
          <a-input v-if="editing" :value="nodeMap.get(String(formZoneId))?.location_name || '—'" disabled />
          <a-select v-else v-model:value="formZoneId" show-search option-filter-prop="label" :disabled="!form.warehouseId" :options="zoneOptions" placeholder="请选择库区" @change="changeFormZone" />
        </a-form-item></a-col>
        <a-col v-if="form.locationLevel > LOCATION_LEVEL.ZONE" :span="12"><a-form-item :label="`所属${levelName(form.locationLevel - 1)}`" name="parentLocationId">
          <a-input v-if="editing" :value="nodeMap.get(String(form.parentLocationId))?.location_name || '—'" disabled />
          <a-select v-else v-model:value="form.parentLocationId" show-search option-filter-prop="label" :options="parentOptions" :disabled="!form.warehouseId || (form.locationLevel === LOCATION_LEVEL.BIN && !formZoneId)" :placeholder="`请选择${levelName(form.locationLevel - 1)}`" />
        </a-form-item></a-col>
        <a-col v-if="!editing && form.locationLevel > LOCATION_LEVEL.ZONE" :span="24"><a-typography-text type="secondary">{{ form.locationLevel === LOCATION_LEVEL.RACK ? '先选择仓库，再选择货架所属库区；下拉框只显示可用库区。' : '依次选择仓库、库区和货架；下拉框只显示可用的上级档案。' }} 上级尚未建档时，请取消并先维护上级。</a-typography-text></a-col>
        <a-col :span="12"><a-form-item :label="`${formTypeName}编码`" name="locationCode"><a-input ref="codeInput" v-model:value="form.locationCode" :maxlength="80" :disabled="editing" :placeholder="autoCodeEnabled && !editing ? '已带出建议编码，可按需修改' : `请输入${formTypeName}编码`" @input="codeEditedByUser = true" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item :label="`${formTypeName}名称`" name="locationName"><a-input v-model:value="form.locationName" :maxlength="200" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="状态"><a-select v-model:value="form.locationStatus" :options="statusOptions" /></a-form-item></a-col>
        <a-col :span="24"><a-button type="link" @click="showAdvanced = !showAdvanced">{{ showAdvanced ? '收起扩展信息' : '展开扩展信息' }}</a-button></a-col>
        <template v-if="showAdvanced">
        <a-col :span="12"><a-form-item label="AGV可达"><a-switch v-model:checked="form.agvReachable" checked-children="可达" un-checked-children="不可达" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="容量上限"><a-input-number v-model:value="form.capacityLimit" :min="0" :precision="4" style="width:100%" placeholder="可选" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="容量单位"><a-input v-model:value="form.capacityUnit" :maxlength="30" placeholder="如箱、托盘" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="外部位置映射码"><a-input v-model:value="form.externalLocationCode" :maxlength="120" placeholder="AGV/WMS对接预留" /></a-form-item></a-col>
        </template>
        <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="3" :maxlength="1000" /></a-form-item></a-col>
      </a-row>
    </a-form>
    <div class="guide-footer"><a-button :disabled="saving" @click="dialogOpen = false">取消</a-button><a-button v-if="!editing" :loading="saving" @click="save(true)">保存并新增</a-button><a-button type="primary" :loading="saving" @click="save()">保存</a-button></div>
  </a-modal>
</template>

<style scoped>
.tpl-container { height:100%; min-height:0; }
.location-filters { width:100%; }
.location-navigation :deep(.ant-tabs-nav) { margin:0; }
.location-navigation :deep(.ant-tabs-nav::before) { display:none; }
/* 独立导航卡片保留完整边框；不使用 card 页签的白色底边连接效果。 */
.location-navigation :deep(.ant-tabs-ink-bar) { display:none; }
.location-navigation :deep(.ant-tabs-nav-list) { width:100%; gap:12px; padding-block:2px; box-sizing:border-box; }
.location-navigation :deep(.ant-tabs-tab) { flex:1; margin:0 !important; padding:14px 20px; border:1px solid #E5E8EF; background:#fff; border-radius:10px !important; transition:background .15s, border-color .15s; }
.location-navigation :deep(.ant-tabs-tab:hover) { border-color:#94B8FF; }
.location-navigation :deep(.ant-tabs-tab-active) { background:#EDF3FF; border-color:#1E5EFF; box-shadow:inset 0 0 0 1px #1E5EFF; }
.location-navigation :deep(.ant-tabs-tab-btn) { display:flex; align-items:center; gap:14px; width:100%; }
.location-nav-icon { display:flex; width:38px; height:38px; align-items:center; justify-content:center; border-radius:8px; background:#F2F5FA; color:#4E5969; font-size:21px; }
.location-navigation :deep(.ant-tabs-tab-active) .location-nav-icon { background:#DDE9FF; color:#1E5EFF; }
.location-nav-text { display:flex; flex-direction:column; gap:3px; }
.location-nav-title { font-size:16px; font-weight:600; color:#1D2129; display:flex; align-items:center; gap:10px; }
.location-nav-count { font-size:12px; line-height:20px; min-width:24px; text-align:center; background:#F2F3F5; border-radius:10px; color:#4E5969; padding:0 6px; }
.location-navigation :deep(.ant-tabs-tab-active) .location-nav-title { color:#1E5EFF; }
.location-navigation :deep(.ant-tabs-tab-active) .location-nav-count { background:#DDE9FF; color:#1E5EFF; }
.location-nav-description { font-size:12px; color:#86909C; }
.location-context { display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; color:#4E5969; font-size:12px; padding:10px 2px; }
.hierarchy-arrow { padding:0 8px; color:#86909C; }
.guide-steps { margin-top:24px; }
.guide-footer { display:flex; justify-content:flex-end; gap:8px; }
@media(max-width:800px) { .location-navigation :deep(.ant-tabs-tab) { padding:10px; } .location-nav-description { display:none; } .location-nav-icon { width:28px; height:28px; font-size:17px; } .location-navigation :deep(.ant-tabs-tab-btn) { gap:6px; } }
.head-filters { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.filter-bar { display:flex; gap:8px; align-items:center; flex-wrap:wrap; background:#fff; padding:12px; border-radius:10px; box-shadow:0 2px 8px rgba(29,33,41,.06); }
.filter-spacer { flex:1 1 auto; min-width:8px; }
.location-tip { color:#86909C; font-size:12px; }
.tree-head { display:flex; align-items:center; justify-content:space-between; padding:10px 12px; border-bottom:1px solid #F2F3F5; }
.tree-title { font-weight:600; color:#1D2129; }
.tree-clear { color:#1E5EFF; cursor:pointer; font-size:13px; text-decoration:none; }
.tree-node-title { vertical-align:middle; }
.level-tag { margin-left:6px; font-size:10px; line-height:16px; padding-inline:4px; }
.table-card { display:flex; flex-direction:column; flex:1; height:100%; min-height:0; background:#fff; border-radius:10px; overflow:hidden; }
.tpl-table { flex:1; min-height:0; }
.summary-cell { font-weight:600; background:#F7F8FA; }
.col-set-panel { width:220px; border-radius:8px; box-shadow:0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display:flex; align-items:center; justify-content:space-between; padding:2px 8px; }
.col-set-foot { display:flex; align-items:center; justify-content:space-between; padding:4px 8px 0; }
.form-reuse-tip { display:flex; align-items:center; justify-content:space-between; gap:8px; color:#86909C; font-size:12px; margin:4px 0 12px; }
.location-form { padding-top:8px; }
:deep(.std-main) { display:flex; flex-direction:column; min-height:0; }
:deep(.std-sidebar .ant-tree) { overflow:auto; padding:0 8px 12px; }
:deep(.std-sidebar .ant-empty) { margin-top:24px; }
@media (max-width: 800px) { .filter-bar { align-items:flex-start; } .location-tip { white-space:normal; } }
</style>
