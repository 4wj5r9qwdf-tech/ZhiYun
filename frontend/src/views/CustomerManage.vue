<script setup lang="ts">
/** 客户档案列表，保留统一列设置、拖拽与弹窗样式。
 * 主档、多个联系人及地址通过后端事务统一保存，通用标签使用 TagSelect 组件。
 */
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import TagSelect from '../components/TagSelect.vue'
import StandardPicker from '../components/StandardPicker.vue'
import StandardListLayout from '../components/StandardListLayout.vue'
import CountryRegionSelect from '../components/CountryRegionSelect.vue'
import { getTagOptions } from '../api/tag'

// ===== 列表 =====
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const treeCollapsed = ref(false)
const categoryTotal = ref(0)
const categoryCounts = ref<Record<string, number>>({})
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const selectedRowKeys = ref<number[]>([])   // 批量选中（无批量诉求可整段删）

// 分类数量由服务端统计全部客户，不受分页和筛选影响。
async function loadCategoryCounts() {
  try {
    const res: any = await api.get('/system/customer/category-counts')
    if (res.code !== 200) throw new Error()
    categoryTotal.value = Number(res.data.total || 0)
    categoryCounts.value = Object.fromEntries((res.data.tags || []).map((x: any) => [String(x.tag_id), Number(x.customer_count)]))
  } catch { message.error('客户分类数量加载失败') }
}
async function loadList() {
  void loadCategoryCounts()
  loading.value = true
  try {
    const res: any = await api.get('/system/customer/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value, status: statusFilter.value, salesId: salesFilter.value, tagId: tagFilter.value },
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
      void scheduleAutoFit()
    } else message.error(res.msg || '加载失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '加载失败')
  } finally { loading.value = false }
}

function onPageChange(p: number, ps: number) {
  if (p !== pageNum.value) selectedRowKeys.value = []   // 换页清空选择
  pageNum.value = p; pageSize.value = ps
  loadList()
}
function showTotalText(t: number) { return `共 ${t} 条` }
function onSearch() { pageNum.value = 1; selectedRowKeys.value = []; loadList() }
function resetFilters() { keyword.value = ''; statusFilter.value = undefined; salesFilter.value = undefined; tagFilter.value = undefined; onSearch() }
const batchStatusOpen = ref(false)
const batchStatusTarget = ref<string>()
const batchStatusLoading = ref(false)
/** 只处理当前页选中且状态需要改变的客户；已是目标状态的记录不重复请求。 */
async function batchStatus() {
  if (batchStatusLoading.value) return
  const target = batchStatusTarget.value
  if (target !== '0' && target !== '1') { message.warning('请选择启用或停用'); return }
  const selected = new Set(selectedRowKeys.value.map(Number))
  const chosen = rows.value.filter(row => selected.has(Number(row.id)))
  const changed = chosen.filter(row => row.status !== target)
  const skipped = chosen.length - changed.length
  if (!chosen.length) { message.warning('请先选择客户'); return }
  if (!changed.length) {
    batchStatusOpen.value = false
    message.info(`所选 ${skipped} 条客户已是目标状态，无需修改`)
    return
  }
  batchStatusLoading.value = true
  try {
    // 接口可能返回业务失败，也可能网络异常；逐条统计，避免部分失败时误报全部成功。
    const results = await Promise.allSettled(changed.map(async row => {
      const res: any = await api.put(`/system/customer/${row.id}/status`, { status: target })
      if (res.code !== 200) throw new Error(res.msg || '操作失败')
      return Number(row.id)
    }))
    const failed = changed.filter((_, index) => results[index].status === 'rejected')
    const succeeded = changed.length - failed.length
    selectedRowKeys.value = failed.map(row => Number(row.id))
    batchStatusOpen.value = false
    await loadList()
    const text = `成功 ${succeeded} 条，跳过 ${skipped} 条，失败 ${failed.length} 条`
    if (failed.length) message.warning(text + '；失败的客户仍保持选中，请检查后重试')
    else message.success(text)
  } finally { batchStatusLoading.value = false }
}
/** 导出当前筛选结果中已加载记录，采用 BOM CSV 兼容 Excel。 */
function exportRows() {
  const quote = (v: unknown) => `"${String(v ?? '').replaceAll('"', '""')}"`
  const fields = [['客户编号','customer_code'],['客户名称','customer_name'],['客户简称','customer_short_name'],['统一社会信用代码','unified_social_credit_code'],['国家/地区','country_display'],['业务员','sales_name'],['状态','status']]
  const csv = [fields.map(x => quote(x[0])).join(','), ...rows.value.map(row => fields.map(([,key]) => quote(key === 'status' ? (row.status === '0' ? '启用' : '停用') : row[key])).join(','))].join('\r\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = '客户档案.csv'; link.click(); URL.revokeObjectURL(url)
}

// ===== 列设置（colState：显隐+顺序+宽度，localStorage 按用户） =====
const ALL_COLUMNS = [
  { title: '客户编号', dataIndex: 'customer_code', key: 'customer_code' },
  { title: '客户名称', dataIndex: 'customer_name', key: 'customer_name' },
  { title: '客户简称', dataIndex: 'customer_short_name', key: 'customer_short_name' },
  { title: '统一社会信用代码', dataIndex: 'unified_social_credit_code', key: 'unified_social_credit_code' },
  { title: '国家/地区', dataIndex: 'country_display', key: 'country_region' },
  { title: '业务员', dataIndex: 'sales_name', key: 'sales_name' },
  { title: '状态', dataIndex: 'status', key: 'status' },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
// 仅用户拖动列宽后保存固定宽度；自动计算结果不写入个人设置。
const manualWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const colStoreKey = computed(() => `customer_list:${localStorage.getItem('userId') || 'anon'}`)
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 140)  // 操作列宽可拖拽并持久化
function loadColState() {
  manualWidths.value = localStorage.getItem(`${colStoreKey.value}:manual_widths`) === '1'
  try {
    const raw = localStorage.getItem(colStoreKey.value)
    if (raw) {
      const saved: ColState[] = JSON.parse(raw)
      const curKeys = new Set(ALL_COLUMNS.map(c => c.key))
      const ordered: ColState[] = []
      for (const item of saved) if (curKeys.has(item.key)) { ordered.push(manualWidths.value ? item : { key: item.key, visible: item.visible }); curKeys.delete(item.key) }
      for (const c of ALL_COLUMNS) if (curKeys.has(c.key)) ordered.push({ key: c.key, visible: true })
      colState.value = ordered
      return
    }
  } catch { /* ignore */ }
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true }))
}
function saveColState() { try { localStorage.setItem(colStoreKey.value, JSON.stringify(manualWidths.value ? colState.value : colState.value.map(({ key, visible }) => ({ key, visible })))) } catch { /* ignore */ } }
function toggleCol(key: string) { const s = colState.value.find(c => c.key === key); if (s) { s.visible = !s.visible; saveColState(); void scheduleAutoFit() } }
function showAllCols() {
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true }))
  manualWidths.value = false
  try { localStorage.removeItem(`${colStoreKey.value}:manual_widths`); localStorage.removeItem(`${colStoreKey.value}:ops_w`) } catch { /* ignore */ }
  saveColState()
  void scheduleAutoFit()
}
/**
 * 自动列宽根据表头文字及容器宽度计算，窄屏不足时允许横向滚动。
 * 自动计算不持久化；手动拖动后才记录个人列宽。
 */
async function scheduleAutoFit() {
  await nextTick()
  if (!manualWidths.value) await autoFitCols(false)
}
async function autoFitCols(resetManual = true) {
  if (resetManual) {
    manualWidths.value = false
    try { localStorage.removeItem(`${colStoreKey.value}:manual_widths`); localStorage.removeItem(`${colStoreKey.value}:ops_w`) } catch { /* ignore */ }
  }
  const body = document.querySelector<HTMLElement>('.customer-table .ant-table-body')
  const container = document.querySelector<HTMLElement>('.customer-table .ant-table-container')
  const available = Math.max(0, (body?.clientWidth || container?.clientWidth || 0) - 2)
  if (!available) return
  const operation = 120
  const widths = new Map<string, number>()
  let required = 48 + 64 + operation
  for (const col of colState.value) {
    const title = ALL_COLUMNS.find(item => item.key === col.key)?.title || col.key
    const min = Math.max(90, Array.from(String(title)).length * 16 + 48)
    widths.set(col.key, min)
    if (col.visible) required += min
  }
  const visible = colState.value.filter(col => col.visible)
  const extra = visible.length ? Math.max(0, Math.floor((available - required) / visible.length)) : 0
  for (const col of colState.value) col.width = (widths.get(col.key) || 90) + (col.visible ? extra : 0)
  opsWidth.value = operation
  if (resetManual) saveColState()
}

function colTitle(key: string) { return ALL_COLUMNS.find(c => c.key === key)?.title || key }
function moveCol(index: number, dir: -1 | 1) {
  const arr = colState.value
  const j = index + dir
  if (j < 0 || j >= arr.length) return
  ;[arr[index], arr[j]] = [arr[j], arr[index]]
  saveColState()
}

// 按 colState 顺序+显隐生成列（操作列固定最后；customHeaderCell 绑拖拽）
const columns = computed(() => {
  // 序号列表头禁画拖拽手柄线（全局 th::after 会无差别画线，叠加 fixed 列分界会花）
  const idx = { title: '序号', key: 'index', width: 64, align: 'center' as const, fixed: 'left' as const,
    customHeaderCell: () => ({ class: 'zy-th-noresize' }) }  // 序号列：固定最左、不可隐藏、不进 colState
  const ops = { title: '操作', key: 'ops', width: opsWidth.value, fixed: 'right' as const,
    customHeaderCell: () => ({ class: 'ops-th', onMousedown: (e: MouseEvent) => onHeaderMouseDown('ops', e), onClick: onHeaderClick }) }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({ ...ALL_COLUMNS.find(a => a.key === c.key)!, ...(c.width != null ? { width: c.width } : {}), customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) }))
    .filter(Boolean)
  return [idx, ...list, ops]
})

const scrollWidth = computed(() => 48 + 64 + opsWidth.value + colState.value.filter(c => c.visible).reduce((sum, c) => sum + (c.width || 120), 0))

// ===== 列宽拖拽（原生表头 + customHeaderCell，标准件勿改） =====
let suppressHeaderClickUntil = 0
function onColResize(key: string, w: number) {
  manualWidths.value = true
  try { localStorage.setItem(`${colStoreKey.value}:manual_widths`, '1') } catch { /* ignore */ }
  if (key === 'ops') { saveColState(); opsWidth.value = Math.max(120, Math.round(w)); try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ } return }
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}
function onHeaderMouseDown(key: string, e: MouseEvent) {
  if (key === 'index') return
  const th = e.currentTarget as HTMLTableCellElement
  const rect = th.getBoundingClientRect()
  const nextIsOps = !!th.nextElementSibling?.classList.contains('ops-th')
  const atOpsLeft = key === 'ops' && e.clientX <= rect.left + 16
  const atOpsRight = key === 'ops' && e.clientX >= rect.right - 16
  const atPrevRightOfOps = nextIsOps && e.clientX >= rect.right - 16
  if (atOpsLeft || atOpsRight || atPrevRightOfOps) {
    // 操作列：本列左/右缘、以及前驱列的右缘交界线，都归操作列（从左缘拖时宽度反向）
    const fromLeft = atOpsLeft || atPrevRightOfOps
    const startX = e.clientX
    const startW = opsWidth.value
    const onMove = (ev: MouseEvent) => onColResize('ops', fromLeft ? startW - (ev.clientX - startX) : startW + (ev.clientX - startX))
    const onUp = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
      document.body.style.cursor = ''
      suppressHeaderClickUntil = Date.now() + 300
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    document.body.style.cursor = 'col-resize'
    e.preventDefault()
    e.stopPropagation()
    return
  }
  if (key === 'ops') return
  if (e.clientX < rect.right - 14) return
  const startX2 = e.clientX
  const startW2 = rect.width
  const onMove2 = (ev: MouseEvent) => onColResize(key, startW2 + (ev.clientX - startX2))
  const onUp2 = () => {
    document.removeEventListener('mousemove', onMove2)
    document.removeEventListener('mouseup', onUp2)
    document.body.style.cursor = ''
    suppressHeaderClickUntil = Date.now() + 300
  }
  document.addEventListener('mousemove', onMove2)
  document.addEventListener('mouseup', onUp2)
  document.body.style.cursor = 'col-resize'
  e.preventDefault()
  e.stopPropagation()
}
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

// ===== 客户档案新增/编辑弹窗 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

/** 客户表单复用标准弹窗；多联系人、多地址随主档统一保存。 */
const statusFilter = ref<string>()
const salesFilter = ref<string>()
const tagFilter = ref<string>()
const salesOptions = ref<any[]>([])
const orgNodes = ref<any[]>([])
const tagOptions = ref<any[]>([])
/** 国家/地区编码统一来自基础资料；客户所在地区与地址所在地区分别选择。 */
const countryOptions = ref<{ country_code: string; name_zh: string; name_en: string; status: string }[]>([])
/** 有标准编码时展示基础资料的当前名称，未匹配的历史值继续原样显示。 */
function countryDisplay(code: unknown, legacy: unknown) { return countryOptions.value.find(item => item.country_code === code)?.name_zh || String(legacy || '') }
const tagTree = computed(() => [{ title: '全部客户', key: 'all' }, ...tagOptions.value.map(t => ({ title: t.label, key: t.value }))])
const postFilter = ref<string>()
const groupFilter = ref<string>()
const posts = computed(() => [...new Set(salesOptions.value.flatMap(p => String(p.posts || '').split('、')).filter(Boolean))].map(v => ({ label: v, value: v })))
const groups = computed(() => [...new Map(salesOptions.value.filter(p => p.group_id).map(p => [String(p.group_id), { label: String(p.group_name || '未命名小组'), value: String(p.group_id) }])).values()])
type PickerOrg = { orgId: string; orgName: string; parentId?: string; children: PickerOrg[] }
/**
 * 构建人员选择器左侧组织树。
 * 有组织查看权限时，使用组织接口返回的父子关系；否则仅用客户模块允许查看的
 * 业务员选项拼出所属组织和小组，不额外请求或暴露完整组织档案。
 * 接口返回的 ID 统一按字符串比较，避免后续数值转换造成长整型精度损失。
 */
const salesTree = computed<PickerOrg[]>(() => {
  const map = new Map<string, PickerOrg>()
  for (const n of orgNodes.value) { const id = String(n.orgId); map.set(id, { orgId: id, orgName: String(n.orgName || '未命名组织'), parentId: String(n.parentId || ''), children: [] }) }
  for (const p of salesOptions.value) {
    const orgId = String(p.org_id || '__unassigned__')
    if (!map.has(orgId)) map.set(orgId, { orgId, orgName: p.org_name || '未分配组织', children: [] })
    if (p.group_id && !map.has(String(p.group_id))) map.set(String(p.group_id), { orgId: String(p.group_id), orgName: p.group_name || '未命名小组', parentId: orgId, children: [] })
  }
  const roots: PickerOrg[] = []
  for (const node of map.values()) { const parent = node.parentId && map.get(node.parentId); if (parent && parent !== node) parent.children.push(node); else roots.push(node) }
  return roots
})
/** 人员可同时属于行政组织和生产小组；保留两条 ID 路径，任一路径命中树筛选即可。 */
const salesPickerItems = computed(() => salesOptions.value.map(p => ({ id: String(p.value), name: String(p.label), orgId: String(p.group_id || p.org_id || '__unassigned__'), orgIds: [p.org_id, p.group_id].filter(Boolean).map(String).concat(!p.org_id && !p.group_id ? ['__unassigned__'] : []), orgName: p.org_name || '—', groupName: p.group_name || '—', posts: p.posts || '—' })))
const salesPickerColumns = [{ title: '姓名', dataIndex: 'name', key: 'name' }, { title: '组织', dataIndex: 'orgName', key: 'orgName' }, { title: '小组', dataIndex: 'groupName', key: 'groupName' }, { title: '岗位', dataIndex: 'posts', key: 'posts' }]
const salesPickerOpen = ref(false)
const salesSelection = ref<string[]>([])
const selectedSalesName = computed(() => salesOptions.value.find(p => String(p.value) === form.value.sales_representative_id)?.label || (form.value.sales_representative_id ? '当前人员不可用，请重新选择' : ''))
/** 岗位和小组只是查找条件，不要求人员必须属于某个预置的业务员岗位。 */
function salesItemFilter(item: { id: string }) {
  const person = salesOptions.value.find(p => String(p.value) === item.id)
  return !!person && (!postFilter.value || String(person.posts || '').split('、').includes(postFilter.value)) && (!groupFilter.value || String(person.group_id || '') === groupFilter.value)
}
/** 每次打开清空临时筛选，并带入当前已选人员；取消选择器不会修改主表单。 */
function openSalesPicker() { postFilter.value = undefined; groupFilter.value = undefined; salesSelection.value = form.value.sales_representative_id ? [form.value.sales_representative_id] : []; salesPickerOpen.value = true }
/** 选择器确认后保存人员 ID，而不是姓名，并即时清除必填项的旧错误提示。 */
function selectSales(ids: string[]) { form.value.sales_representative_id = ids[0]; formRef.value?.validateFields(['sales_representative_id']).catch(() => {}) }
const emptyForm = () => ({ id: null as number | null, customer_code: '', customer_name: '', customer_short_name: '', unified_social_credit_code: '', country_region: '', country_region_code: undefined as string | undefined, sales_representative_id: undefined as string | undefined, remark: '', status: '0', contacts: [] as any[], addresses: [] as any[], tagOptions: [] as { label: string; value: string; disabled?: boolean }[], tagIds: [] as string[] })
const form = ref(emptyForm())
// 记录打开时的完整主档与子项快照，仅用于判断取消时是否需要提醒；不会发送到后端。
const initialForm = ref('')
const rules: any = { customer_name: [{ required: true, message: '请输入客户名称' }], sales_representative_id: [{ required: true, message: '请选择业务员' }] }
function openAdd() { isEdit.value = false; form.value = emptyForm(); initialForm.value = JSON.stringify(form.value); showDlg.value = true }
async function openEdit(row: any) {
  try { const res: any = await api.get('/system/customer/' + row.id); if (res.code !== 200) return message.error(res.msg); isEdit.value = true; form.value = { ...emptyForm(), ...res.data, sales_representative_id: String(res.data.sales_representative_id) }; initialForm.value = JSON.stringify(form.value); showDlg.value = true }
  catch (e: any) { message.error(e?.response?.data?.msg || '详情加载失败') }
}
/** 客户弹窗只在用户确认后丢弃尚未提交的主档与子项。 */
function closeCustomer() {
  if (saving.value) return
  if (JSON.stringify(form.value) === initialForm.value) { showDlg.value = false; return }
  Modal.confirm({ title: '放弃未保存的修改？', content: '关闭后，客户档案的本次修改不会保存。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { showDlg.value = false } })
}

// 联系人与地址共用当前编辑下标；子弹窗互斥，null 表示新增，数字表示编辑现有行。
// 修改先写入子弹窗草稿，点击确定才写回主表单；主表单保存时才通过接口提交数据库。
const contactVisible = ref(false)
const addressVisible = ref(false)
const childIndex = ref<number | null>(null)
const childSnapshot = ref('') // 打开子弹窗时的草稿快照，用于关闭时判断是否存在未保存输入。
const contactSource = ref<number>()
const blankContact = () => ({ contact_name: '', contact_title: '', phone_number: '', email: '', contact_role: '', is_primary: false })
const blankAddress = () => ({ address_type: '', recipient_name: '', recipient_phone: '', country_region: '', country_region_code: undefined as string | undefined, province: '', city: '', district: '', address_line: '', postal_code: '', is_default: false })
const contactDraft = ref(blankContact())
const addressDraft = ref(blankAddress())
const contactColumns = [{ title: '姓名', dataIndex: 'contact_name', key: 'contact_name' }, { title: '职务', dataIndex: 'contact_title', key: 'contact_title' }, { title: '电话', dataIndex: 'phone_number', key: 'phone_number' }, { title: '邮箱', dataIndex: 'email', key: 'email' }, { title: '默认', key: 'default', width: 70 }, { title: '操作', key: 'action', width: 110 }]
const addressColumns = [{ title: '用途', dataIndex: 'address_type', key: 'address_type' }, { title: '收件人', dataIndex: 'recipient_name', key: 'recipient_name' }, { title: '电话', dataIndex: 'recipient_phone', key: 'recipient_phone' }, { title: '详细地址', key: 'address' }, { title: '默认', key: 'default', width: 70 }, { title: '操作', key: 'action', width: 110 }]
/** 编辑时复制原有字段而非引用表格行，避免在子弹窗中输入就提前改变主表单。 */
function openContact(index?: number) { childIndex.value = index ?? null; contactDraft.value = { ...blankContact(), ...(index === undefined ? {} : form.value.contacts[index]) }; childSnapshot.value = JSON.stringify(contactDraft.value); contactVisible.value = true }
function openAddress(index?: number) { childIndex.value = index ?? null; contactSource.value = undefined; addressDraft.value = { ...blankAddress(), ...(index === undefined ? {} : form.value.addresses[index]) }; childSnapshot.value = JSON.stringify(addressDraft.value); addressVisible.value = true }
/** 地址可从客户联系人带出姓名与电话，带出后仍允许修改。 */
function fillRecipient(value: unknown) { if (value === undefined || value === null) return; const contact = form.value.contacts[Number(value)]; if (contact) { addressDraft.value.recipient_name = contact.contact_name || ''; addressDraft.value.recipient_phone = contact.phone_number || '' } }
/** 子弹窗取消时只放弃当前子项草稿，不影响此前已确认的其他联系人、地址或客户信息。 */
function closeChild(kind: 'contact' | 'address') {
  const close = () => { if (kind === 'contact') contactVisible.value = false; else addressVisible.value = false }
  const draft = kind === 'contact' ? contactDraft.value : addressDraft.value
  if (JSON.stringify(draft) === childSnapshot.value) { close(); return }
  Modal.confirm({ title: '放弃未保存的修改？', okText: '放弃修改', cancelText: '继续编辑', onOk: close })
}
/**
 * 联系人姓名和电话为当前接口约定的必填项。
 * 选为默认时先取消其他联系人的默认标记，保证一个客户最多一个默认联系人；
 * 此处只更新主表单草稿，后端仍会在事务中再次校验和保存。
 */
function saveContact() {
  const c = { ...contactDraft.value, contact_name: String(contactDraft.value.contact_name || '').trim(), phone_number: String(contactDraft.value.phone_number || '').trim() }
  if (!c.contact_name || !c.phone_number) { message.warning('请填写联系人姓名和联系电话'); return }
  if (c.is_primary) form.value.contacts.forEach(item => { item.is_primary = false })
  if (childIndex.value === null) form.value.contacts.push(c); else form.value.contacts.splice(childIndex.value, 1, c)
  contactVisible.value = false
}
/**
 * 地址用途和详细地址必填；用途包含“样衣”时还需收件人和电话，与后端校验保持一致。
 * 仅取消相同用途的其他默认地址，不影响办公联系、样衣收件等不同用途的默认项。
 */
function saveAddress() {
  const a = { ...addressDraft.value, address_type: String(addressDraft.value.address_type || '').trim(), address_line: String(addressDraft.value.address_line || '').trim() }
  if (!a.address_type || !a.address_line) { message.warning('请填写地址用途和详细地址'); return }
  if (a.address_type.includes('样衣') && (!String(a.recipient_name || '').trim() || !String(a.recipient_phone || '').trim())) { message.warning('样衣收件地址须填写收件人和电话'); return }
  if (a.is_default) form.value.addresses.forEach(item => { if (String(item.address_type || '').trim() === a.address_type) item.is_default = false })
  if (childIndex.value === null) form.value.addresses.push(a); else form.value.addresses.splice(childIndex.value, 1, a)
  addressVisible.value = false
}
/**
 * 先校验主表单，再调用同名检查；同名仅提醒，用户确认后仍可继续保存。
 * saving 在异步检查及实际保存期间阻止重复提交；接口失败时保留草稿并提示错误。
 * 客户编号由数据库生成，前端显示但不参与编号生成或修改。
 */
async function save() {
  if (saving.value) return
  saving.value = true
  let duplicate: any
  try {
    await formRef.value?.validate()
    duplicate = await api.get('/system/customer/duplicates', { params: { name: form.value.customer_name, id: form.value.id || 0 } })
    if (duplicate.code !== 200) throw new Error(duplicate.msg || '同名检查失败')
  } catch (e: any) {
    const name = e?.errorFields?.[0]?.name
    if (name) formRef.value?.scrollToField(name)
    else message.error(e?.response?.data?.msg || e.message || '保存前检查失败')
    return
  } finally { saving.value = false }
  if (Number(duplicate.data) > 0) { Modal.confirm({ title: '存在同名客户', content: '已有同名客户，是否仍然保存？', onOk: persist }); return }
  try { await persist() } catch { /* persist 已显示错误，保持弹窗供修改 */ }
}
/** 主档、联系人、地址及标签一次提交给后端事务；成功后刷新列表和可选标签。 */
async function persist() {
  if (saving.value) return
  saving.value = true
  try {
    const res: any = isEdit.value ? await api.put('/system/customer/' + form.value.id, form.value) : await api.post('/system/customer', form.value)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; await loadList(); await loadOptions() } else { message.error(res.msg || '保存失败'); throw new Error(res.msg) }
  } catch (e: any) { message.error(e?.response?.data?.msg || e.message || '保存失败'); throw e } finally { saving.value = false }
}
/** 停用只改变档案可选状态，不删除记录，不改变历史订单和样衣。 */
function changeCustomerStatus(row: any) {
  const next = row.status === '0' ? '1' : '0'
  Modal.confirm({ title: next === '1' ? '停用客户' : '启用客户', content: next === '1' ? '历史及进行中业务不受影响，停用后不能新建引用。' : '确认启用该客户？', onOk: async () => { const res: any = await api.put('/system/customer/' + row.id + '/status', { status: next }); if (res.code !== 200) throw new Error(res.msg); await loadList() } })
}
/** 删除客户须由服务端检查业务引用，联系人和地址会随未引用的客户一起删除。 */
function deleteCustomer(row: any) {
  Modal.confirm({
    title: '删除客户档案',
    content: `确定删除“${row.customer_name}”及其联系人、地址？已被业务引用的客户不能删除，可改为停用。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.delete('/system/customer/' + row.id)
      if (res.code !== 200) { message.error(res.msg || '删除失败'); throw new Error(res.msg || '删除失败') }
      message.success('删除成功')
      selectedRowKeys.value = selectedRowKeys.value.filter(id => id !== Number(row.id))
      await loadList()
    },
  })
}
/**
 * 人员和标签来自客户所需的现有接口；只有具备组织查看权限才请求完整组织列表。
 * 组织列表不可用时 salesTree 会从人员选项构建有限的组织/小组树，人员选择仍可使用。
 */
async function loadOptions() {
  const orgRequest = can('system:org:list') ? api.get('/system/org/list').catch(() => ({ data: [] })) : Promise.resolve({ data: [] })
  const [people, tags, orgs, countries]: any[] = await Promise.all([api.get('/system/customer/sales-options'), getTagOptions('customer_master'), orgRequest, api.get('/system/country-region/options').catch(() => null)])
  salesOptions.value = people.data || []
  tagOptions.value = (tags.data || []).map((t: any) => ({ label: t.tagName, value: String(t.tagId) }))
  orgNodes.value = orgs.data || []
  if (countries?.code === 200) countryOptions.value = countries.data || []
  else message.error('国家/地区资料加载失败，请稍后重试')
}

// 前端过滤示例（若后端已支持 keyword 过滤，可直接用 rows）
const filteredRows = computed(() => rows.value)

onMounted(() => {
  loadColState()
  loadList(); loadOptions()
  void scheduleAutoFit()
  widthObserver = new ResizeObserver(() => { void scheduleAutoFit() })
  nextTick(() => {
    const area = document.querySelector<HTMLElement>('.customer-table .ant-table-container')
    if (area) widthObserver?.observe(area)
  })
})
onBeforeUnmount(() => widthObserver?.disconnect())
watch(treeCollapsed, () => { void scheduleAutoFit() })
</script>

<template>
  <div class="customer-page">
    <StandardListLayout v-model:collapsed="treeCollapsed">
    <template #filters>
    <!-- A. 筛选栏：左筛选 + 查询/重置，右操作 -->
    <div class="customer-filter filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索编号/名称/简称" allow-clear style="width:200px" @press-enter="onSearch">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <a-select v-model:value="statusFilter" placeholder="状态" allow-clear style="width:110px" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" />
      <a-select v-model:value="salesFilter" :options="salesOptions" show-search option-filter-prop="label" placeholder="业务员" allow-clear style="width:160px" />
      <a-select v-model:value="tagFilter" :options="tagOptions" placeholder="客户标签" allow-clear style="width:160px" />
    </div>

    </template>
    <template #actions>
    <div class="filter-bar customer-actions">
      <a-button v-if="can('system:customer:add')" type="primary" @click="openAdd"><PlusOutlined />新增</a-button>
      <a-button type="primary" @click="onSearch">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button v-if="can('system:customer:edit')" :disabled="!selectedRowKeys.length" @click="batchStatusTarget = undefined; batchStatusOpen = true">批量启停{{ selectedRowKeys.length ? `（${selectedRowKeys.length}）` : '' }}</a-button>
      <a-button @click="exportRows">↓ 导出</a-button>
      <a-dropdown v-model:open="colSetOpen" :trigger="['click']">
        <a-button>⚙ 列设置</a-button>
        <template #overlay>
          <a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }">
            <div v-for="(c, i) in colState" :key="c.key" class="col-set-item">
              <a-checkbox :checked="c.visible" @change="toggleCol(c.key)">{{ colTitle(c.key) }}</a-checkbox>
              <span class="col-move">
                <a-button type="text" size="small" :disabled="i === 0" @click="moveCol(i, -1)">↑</a-button>
                <a-button type="text" size="small" :disabled="i === colState.length - 1" @click="moveCol(i, 1)">↓</a-button>
              </span>
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
      <div class="tree-head">
        <span class="tree-title">客户分类</span>
        <a v-if="tagFilter" class="tree-clear" @click="resetFilters">重置</a>
      </div>
      <div class="tree-body">
        <a-tree :tree-data="tagTree" :selected-keys="[tagFilter || 'all']" block-node @select="keys => { if (!keys.length) return; tagFilter = keys[0] === 'all' ? undefined : String(keys[0]); onSearch() }">
          <template #title="{ title, key }">
            <span class="tree-node">{{ title }}<span class="tree-count">{{ key === 'all' ? categoryTotal : (categoryCounts[String(key)] || 0) }}</span></span>
          </template>
        </a-tree>
      </div>
    </template>
    <div class="customer-list">
    <a-table
      :columns="columns"
      :data-source="filteredRows"
      :loading="loading"
      :pagination="false"
      table-layout="fixed"
      :scroll="{ x: scrollWidth, y: 'calc(100vh - 360px)' }"
      :row-selection="{ selectedRowKeys, onChange: (keys: (string|number)[]) => selectedRowKeys = keys.map(Number) }"
      row-key="id"
      size="middle"
      class="customer-table"
    >
      <template #bodyCell="{ column, record, index }">
        <!-- 序号列（标准件：固定最左、页内从 1 连续编号） -->
        <template v-if="column.key === 'index'">
          {{ (pageNum - 1) * pageSize + index + 1 }}
        </template>

        <template v-if="column.key === 'status'">
          <a-switch :checked="record.status === '0'" :disabled="!can('system:customer:edit')" checked-children="启用" un-checked-children="停用" @change="changeCustomerStatus(record)" />
        </template>
        <template v-else-if="column.key === 'ops'">
          <div class="ops-btns">
            <a-button v-if="can('system:customer:edit')" type="primary" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-if="can('system:customer:remove')" danger size="small" @click="deleteCustomer(record)">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>
      <div class="customer-table-footer"><strong>合计（筛选结果）　{{ total }} 条</strong><a-pagination :current="pageNum" :page-size="pageSize" :total="total" show-size-changer show-quick-jumper :show-total="showTotalText" @change="onPageChange" /></div>
    </div>
    </StandardListLayout>

    <a-modal :open="batchStatusOpen" title="批量启停客户" :mask-closable="false" :confirm-loading="batchStatusLoading" ok-text="确认" cancel-text="取消" @ok="batchStatus" @cancel="batchStatusOpen = false">
      <p>已选择 {{ selectedRowKeys.length }} 条客户，请选择目标状态。已是目标状态的客户将自动跳过。</p>
      <a-radio-group v-model:value="batchStatusTarget" :disabled="batchStatusLoading">
        <a-radio value="0">启用</a-radio>
        <a-radio value="1">停用</a-radio>
      </a-radio-group>
    </a-modal>

    <!-- 客户主档与子项在主弹窗内统一保存；子弹窗确认后仅写回本地草稿。 -->
    <a-modal :open="showDlg" :mask-closable="false" :keyboard="false" :title="isEdit ? '编辑客户档案' : '新增客户档案'" :width="'min(960px, calc(100vw - 32px))'" :body-style="{ maxHeight: '65vh', overflowY: 'auto', overflowX: 'hidden' }" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save" @cancel="closeCustomer">
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <section class="customer-form-section">
          <h3>基本信息</h3>
          <a-row :gutter="16">
            <a-col :xs="24" :sm="12"><a-form-item label="客户名称" name="customer_name"><a-input v-model:value="form.customer_name" :maxlength="200" placeholder="请输入客户名称" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="客户简称"><a-input v-model:value="form.customer_short_name" :maxlength="100" placeholder="可不填" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="客户编号"><a-input :value="form.customer_code" disabled placeholder="保存时自动生成" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="统一社会信用代码"><a-input v-model:value="form.unified_social_credit_code" :maxlength="18" placeholder="境外客户可不填" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="客户所在国家/地区"><CountryRegionSelect v-model="form.country_region_code" :options="countryOptions" :legacy-name="form.country_region" @change="value => form.country_region = value?.name_zh || ''" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="客户标签"><TagSelect v-model="form.tagIds" :selected-options="form.tagOptions" module-key="customer_master" module-name="客户档案" /></a-form-item></a-col>
            <a-col :xs="24" :sm="12"><a-form-item label="业务员" name="sales_representative_id"><a-input :value="selectedSalesName" readonly placeholder="从人员信息中选择" @click="openSalesPicker"><template #suffix><a @click.stop="openSalesPicker">选择人员</a></template></a-input></a-form-item></a-col>
            <a-col :xs="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="2000" :rows="2" placeholder="可不填" /></a-form-item></a-col>
          </a-row>
        </section>
        <section class="customer-form-section">
          <h3>联系人 <a-button size="small" @click="openContact()">＋添加联系人</a-button></h3>
          <a-table :columns="contactColumns" :data-source="form.contacts.map((item, index) => ({ ...item, _index: index }))" row-key="_index" size="small" :pagination="false" :scroll="{ x: 700 }">
            <template #emptyText>暂无联系人，可点击右上角添加</template>
            <template #bodyCell="{column,record}">
              <template v-if="column.key === 'default'"><a-tag v-if="record.is_primary" color="blue">默认</a-tag><span v-else>—</span></template>
              <template v-else-if="column.key === 'action'"><a-button type="link" size="small" @click="openContact(record._index)">编辑</a-button><a-button type="link" danger size="small" @click="form.contacts.splice(record._index, 1)">移除</a-button></template>
            </template>
          </a-table>
        </section>
        <section class="customer-form-section">
          <h3>地址 <a-button size="small" @click="openAddress()">＋添加地址</a-button></h3>
          <a-table :columns="addressColumns" :data-source="form.addresses.map((item, index) => ({ ...item, _index: index }))" row-key="_index" size="small" :pagination="false" :scroll="{ x: 700 }">
            <template #emptyText>暂无地址，可点击右上角添加</template>
            <template #bodyCell="{column,record}">
              <template v-if="column.key === 'address'">{{ [countryDisplay(record.country_region_code, record.country_region), record.province, record.city, record.district, record.address_line].filter(Boolean).join(' ') || '—' }}</template>
              <template v-else-if="column.key === 'default'"><a-tag v-if="record.is_default" color="blue">默认</a-tag><span v-else>—</span></template>
              <template v-else-if="column.key === 'action'"><a-button type="link" size="small" @click="openAddress(record._index)">编辑</a-button><a-button type="link" danger size="small" @click="form.addresses.splice(record._index, 1)">移除</a-button></template>
            </template>
          </a-table>
        </section>
      </a-form>
    </a-modal>

    <StandardPicker v-model:open="salesPickerOpen" v-model="salesSelection" title="选择业务员" :tree="salesTree" :items="salesPickerItems" :columns="salesPickerColumns" :item-filter="salesItemFilter" @confirm="selectSales">
      <template #filters><a-space wrap><a-select v-model:value="postFilter" :options="posts" placeholder="岗位" allow-clear style="width:160px" /><a-select v-model:value="groupFilter" :options="groups" placeholder="小组" allow-clear style="width:160px" /></a-space></template>
    </StandardPicker>

    <a-modal :open="contactVisible" :title="childIndex === null ? '添加联系人' : '编辑联系人'" :mask-closable="false" :keyboard="false" width="640px" ok-text="确定" cancel-text="取消" @ok="saveContact" @cancel="closeChild('contact')">
      <a-form layout="vertical"><a-row :gutter="16">
        <a-col :xs="24" :sm="12"><a-form-item label="联系人姓名" required><a-input v-model:value="contactDraft.contact_name" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="联系电话" required><a-input v-model:value="contactDraft.phone_number" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="职务"><a-input v-model:value="contactDraft.contact_title" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="邮箱"><a-input v-model:value="contactDraft.email" :maxlength="200" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="用途"><a-input v-model:value="contactDraft.contact_role" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="默认联系人"><a-switch v-model:checked="contactDraft.is_primary" checked-children="是" un-checked-children="否" /></a-form-item></a-col>
      </a-row></a-form>
    </a-modal>

    <a-modal :open="addressVisible" :title="childIndex === null ? '添加地址' : '编辑地址'" :mask-closable="false" :keyboard="false" width="760px" :body-style="{ maxHeight: '65vh', overflowY: 'auto', overflowX: 'hidden' }" ok-text="确定" cancel-text="取消" @ok="saveAddress" @cancel="closeChild('address')">
      <a-form layout="vertical"><a-row :gutter="16">
        <a-col :xs="24" :sm="12"><a-form-item label="地址用途" required><a-input v-model:value="addressDraft.address_type" :maxlength="100" placeholder="如：样衣收件、办公联系" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="从联系人带入（可选）"><a-select v-model:value="contactSource" :options="form.contacts.map((c, index) => ({ label: c.contact_name, value: index }))" allow-clear placeholder="选择已有联系人" @change="fillRecipient" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="收件人"><a-input v-model:value="addressDraft.recipient_name" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="收件电话"><a-input v-model:value="addressDraft.recipient_phone" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="12"><a-form-item label="地址所在国家/地区"><CountryRegionSelect v-model="addressDraft.country_region_code" :options="countryOptions" :legacy-name="addressDraft.country_region" @change="value => addressDraft.country_region = value?.name_zh || ''" /></a-form-item></a-col>
        <a-col v-for="field in [{key:'province',label:'省/州'},{key:'city',label:'城市'},{key:'district',label:'区县'}]" :key="field.key" :xs="24" :sm="12"><a-form-item :label="field.label"><a-input v-model:value="addressDraft[field.key as 'province' | 'city' | 'district']" :maxlength="100" /></a-form-item></a-col>
        <a-col :xs="24" :sm="16"><a-form-item label="详细地址" required><a-input v-model:value="addressDraft.address_line" :maxlength="500" /></a-form-item></a-col>
        <a-col :xs="24" :sm="8"><a-form-item label="邮政编码"><a-input v-model:value="addressDraft.postal_code" :maxlength="30" /></a-form-item></a-col>
        <a-col :xs="24"><a-form-item label="默认地址（同用途最多一个）"><a-switch v-model:checked="addressDraft.is_default" checked-children="是" un-checked-children="否" /></a-form-item></a-col>
      </a-row></a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.customer-page { height: 100%; min-height: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.tree-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid #F2F3F5; }
.tree-title { font-weight: 600; color: #1D2129; }
.tree-clear { color: #1E5EFF; cursor: pointer; font-size: 13px; }
.tree-body { flex: 1; overflow: auto; padding: 8px; font-size: 14px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.tree-count { font-size: 11px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #E8F3FF; color: #1E5EFF; display: inline-flex; align-items: center; justify-content: center; }
.customer-list { height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; border-radius: 10px; background: #fff; }
/* 表头保留高度，表体填充剩余空间，分页留在底部。 */
.customer-list .customer-table { width: 100%; flex: 1; min-height: 0; }
.customer-table :deep(.ant-spin-nested-loading),
.customer-table :deep(.ant-spin-container),
.customer-table :deep(.ant-table),
.customer-table :deep(.ant-table-container) { height: 100%; min-height: 0; display: flex; flex-direction: column; }
.customer-table :deep(.ant-table-body) { flex: 1; min-height: 0; max-height: none !important; }
.customer-table :deep(.ant-table-body:has(.ant-table-placeholder) > table) { height: 100%; }
.customer-table-footer { flex: none; }
.customer-table-footer { display: flex; align-items: center; justify-content: space-between; min-height: 48px; padding: 8px 12px; border-top: 1px solid #F0F2F5; }
.customer-filter { margin-bottom: 8px; }
.customer-actions { margin-bottom: 12px; }
.customer-form-section { padding: 8px 0 16px; border-bottom: 1px solid #F0F2F5; }
.customer-form-section h3 { display: flex; align-items: center; justify-content: space-between; margin: 0 0 14px; color: #1D2129; font-size: 14px; font-weight: 600; }

.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-spacer { flex: 1 1 auto; min-width: 8px; }

.table-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.table-bar .total { font-size: 13px; color: #4E5969; }
.col-set-panel { width: 200px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }

.customer-table { width: 100%; background: #fff; border-radius: 10px; }   /* TODO 类名同步 */
.customer-table :deep(.ant-table) { font-size: 13px; }
.customer-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.customer-table :deep(.ant-table-thead > tr > th),
.customer-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.customer-table :deep(.ant-table-thead > tr > th:last-child),
.customer-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.customer-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.ops-btns .ant-btn-sm { padding-inline: 5px; }
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-link { display: inline-flex; align-items: center; gap: 4px; margin-right: 10px; font-size: 13px; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
</style>

<!-- 非 scoped：列宽拖拽手柄（标准件勿改；scoped 匹配不到 h() 渲染元素） -->
<style>
.ant-table-thead > tr > th { position: relative; }
.ant-table-thead > tr > th.zy-th-noresize::after { display: none; }
.ant-table-thead > tr > th::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; transition: border-color .12s, background .12s; }
.ant-table-thead > tr > th:hover::after { border-right-color: #C9CDD4; }
.ant-table-thead > tr > th:active::after { background: rgba(30,94,255,.15); border-right-color: #1E5EFF; }
</style>
