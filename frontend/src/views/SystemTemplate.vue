<!--
  功能模板（系统模块）—— 织云 ERP 列表页标准件全集展览
  后期开发按需从本页/OrgManage/Person 搬对应区块；每块顶部注释标注对应规范条目。

  标准件清单：
  B.5.1.1 列表页四件套：序号列 / 列宽拖拽(customHeaderCell+右缘14px) / 自适应列宽(列名最小宽+操作列实测) /
          操作列按钮式(primary编辑/danger删除, size=small, 操作列左/右缘16px可拖) / 后端分页禁前端过滤
  连续新增：保存并新增 / 带入上次成功保存内容 / 清空内容（共用 savedFormDefaults 工具）
  B.5.1.2 表单弹窗：a-modal mask-closable=false + keyboard=false / 校验跳Tab / 多选勾选弹窗
  定版布局：页头行=查询条件左置(无标题无返回)；按钮行=+新增(最左)→查询→重置……动作/列设置(最右)
  左树面板：树+类型tag+人数角标+点击筛选该节点及下级+窄竖条折叠手柄(CSS三角, 展开贴面板内右缘/收起贴页左缘)
  行选择框：rowSelection(勾选跨页保持/批量操作按需显隐)
  列排序：sortable → 后端 orderBy 白名单(或前端sorter, 见注)
  列筛选（报表场景）：主查询圈范围 → 列内切片（如产量报表筛某员工）→ 合计按最终结果集重算；
          主查询变化/重新查询自动清空列筛选（主查询优先）；后端分页项目须把 filters 上送接口+合计接口同参
  演示数据：纯前端内存生成, 不入库不内置——刷新即重置
-->
<script setup lang="ts">
import '../assets/standard-list-fill.css'
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import StandardPicker from '../components/StandardPicker.vue'
import ImportResultTable from '../components/ImportResultTable.vue'
import BatchResultModal from '../components/BatchResultModal.vue'
import StandardListLayout from '../components/StandardListLayout.vue'
import { createSavedFormDefaults } from '../utils/savedFormDefaults'

// ===== 演示数据（内存生成，不入库） =====
type DemoNode = { orgId: string; orgName: string; orgCategory: string; children?: DemoNode[] }
type DemoRow = { id: string; code: string; name: string; orgId: string; type: string; status: string; amount: number; owner: string; remark: string; reviewStatus?: 'pending' | 'approved' }

const demoTree: DemoNode[] = [
  { orgId: 'd1', orgName: '总部', orgCategory: '部门', children: [
    { orgId: 'd11', orgName: '生产中心', orgCategory: '部门', children: [
      { orgId: 'd111', orgName: '一车间', orgCategory: '科室' },
      { orgId: 'd112', orgName: '二车间', orgCategory: '科室' },
    ] },
    { orgId: 'd12', orgName: '销售中心', orgCategory: '部门', children: [
      { orgId: 'd121', orgName: '华东销售科', orgCategory: '科室' },
    ] },
  ] },
]
const flatOrgs = (() => {
  const out: { orgId: string; orgName: string }[] = []
  const walk = (ns: DemoNode[]) => ns.forEach(n => { out.push({ orgId: n.orgId, orgName: n.orgName }); if (n.children) walk(n.children) })
  walk(demoTree)
  return out
})()
const demoRows = ref<DemoRow[]>(Array.from({ length: 23 }, (_, i) => ({
  id: 'r' + (i + 1),
  code: 'T' + String(1001 + i),
  name: '模板演示记录' + (i + 1),
  orgId: flatOrgs[i % flatOrgs.length].orgId,
  type: ['部门', '科室', '小组'][i % 3],
  status: i % 5 === 4 ? '1' : '0',
  amount: 1000 + i * 37,
  owner: ['张三', '李四', '王五'][i % 3],
  remark: i % 3 === 0 ? '' : '备注' + i,
  reviewStatus: 'pending',
})))

// ===== 左树面板：点节点筛该节点及下级；再点取消 =====
const treeCollapsed = ref(false)
const selectedOrgId = ref<string | null>(null)
function onTreeSelect(keys: any[]) { selectedOrgId.value = keys.length ? String(keys[0]) : null }
function walkIds(ns: DemoNode[]): string[] { return ns.flatMap(n => [n.orgId, ...(n.children ? walkIds(n.children) : [])]) }
/** 找到实际选择的节点，筛选时只包含该节点及其下级。 */
function findNode(ns: DemoNode[], id: string): DemoNode | undefined {
  for (const node of ns) { if (node.orgId === id) return node; const found = findNode(node.children || [], id); if (found) return found }
}
const orgCountMap = computed(() => {
  const m: Record<string, number> = {}
  const walk = (ns: DemoNode[]) => ns.forEach(n => {
    m[n.orgId] = demoRows.value.filter(r => walkIds([n]).includes(r.orgId)).length
    if (n.children) walk(n.children)
  })
  walk(demoTree)
  return m
})
function typeColorOf(t: string) { return t === '小组' ? 'green' : t === '科室' ? 'blue' : 'default' }

// ===== 查询条件（页头行，变更即查 + 回车查询） =====
const keyword = ref('')
const fType = ref<string | undefined>()
const fStatus = ref<string | undefined>()
// 列筛选状态（受控）：叠加在主查询结果之上的切片；主查询变化/重新查询时清空（主查询优先）
const colFilters = ref<Record<string, any[]>>({})
watch([keyword, fType, fStatus, selectedOrgId], () => { colFilters.value = {} })

const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
function loadList() { loading.value = true; setTimeout(() => { loading.value = false }, 150) }
watch([keyword, fType, fStatus, selectedOrgId], () => { pageNum.value = 1; selectedRowKeys.value = [] })
function resetFilters() {
  keyword.value = ''; fType.value = undefined; fStatus.value = undefined
  selectedOrgId.value = null; treeCollapsed.value = false; pageNum.value = 1; colFilters.value = {}; selectedRowKeys.value = []
}
function runSearch() { pageNum.value = 1; colFilters.value = {}; selectedRowKeys.value = []; loadList() }

// ===== 列表数据：树选节点及下级 + 关键词/类型/状态 过滤（后端分页项目换成接口；此处演示为前端切片） =====
const filteredRows = computed(() => {
  const selected = selectedOrgId.value ? findNode(demoTree, selectedOrgId.value) : undefined
  const ids = selected ? walkIds([selected]) : null
  return demoRows.value.filter(r =>
    (!ids || ids.includes(r.orgId))
    && (!keyword.value.trim() || (r.name + r.code + r.owner).includes(keyword.value.trim()))
    && (!fType.value || r.type === fType.value)
    && (!fStatus.value || r.status === fStatus.value))
})
// 列筛选切片（叠加在主查询结果上；合计基于最终结果集）
const colFilteredRows = computed(() => filteredRows.value.filter(r => {
  for (const [key, vals] of Object.entries(colFilters.value)) {
    if (vals && vals.length && !vals.includes(String((r as any)[key]))) return false
  }
  return true
}))
const pagedRows = computed(() => colFilteredRows.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
const total = computed(() => colFilteredRows.value.length)
// 合计：按最终结果集（主查询 ∩ 列筛选）重算——报工数量/金额类合计跟筛选走
const summary = computed(() => ({
  count: colFilteredRows.value.length,
  amount: colFilteredRows.value.reduce((s2, r) => s2 + (r.amount || 0), 0),
}))

// ===== 行选择框（批量操作按需显隐；跨页保持勾选） =====
const selectedRowKeys = ref<string[]>([])
const batchResultOpen = ref(false)
const batchResultTitle = ref('批量操作结果')
const batchSucceeded = ref(0)
const batchFailures = ref<{ name: string; reason: string }[]>([])
function showBatchResult(title: string, succeeded: number, failures: { name: string; reason: string }[] = []) {
  batchResultTitle.value = title; batchSucceeded.value = succeeded; batchFailures.value = failures; batchResultOpen.value = true
}
const hasSelection = computed(() => selectedRowKeys.value.length > 0)
function removeBatch() {
  Modal.confirm({
    title: `删除选中 ${selectedRowKeys.value.length} 条？`,
    content: '演示操作：仅内存生效，刷新即恢复',
    onOk: () => {
      const count = demoRows.value.filter(r => selectedRowKeys.value.includes(r.id)).length
      demoRows.value = demoRows.value.filter(r => !selectedRowKeys.value.includes(r.id))
      selectedRowKeys.value = []
      showBatchResult('批量删除结果（内存演示）', count)
    },
  })
}

// ===== 列表列（序号列/操作列在 columns computed 内拼接；sortable→orderBy 白名单） =====
const ALL_COLUMNS = [
  { title: '编码', key: 'code', width: 110, sorter: true },
  { title: '名称', key: 'name', width: 180, sorter: true },
  { title: '所属组织', key: 'orgId', width: 130 },
  { title: '类型', key: 'type', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '审核状态', key: 'reviewStatus', width: 110 },
  { title: '金额', key: 'amount', width: 110, sorter: true },
  // 列筛选（报表场景标准件）：主查询圈范围 → 列内切片（如产量报表里筛某员工）；
  // 本演示为前端全量内存数据直接过滤；后端分页项目请把 filters 值作为查询参数上送接口，
  // 合计接口带同样参数（保证跨页筛选正确 + 合计=最终结果集）
  { title: '负责人', key: 'owner', width: 110, filters: [] as { text: string; value: string }[], filterMultiple: true },
  { title: '备注', key: 'remark', width: 160 },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
// 仅用户拖动列宽后保存固定宽度；自动计算结果不写入个人设置。
const manualWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const colStoreKey = computed(() => `tpl_list:${localStorage.getItem('userId') || 'anon'}`)
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 200)

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
function colTitle(key: string) { return ALL_COLUMNS.find(c => c.key === key)?.title || key }
function moveCol(index: number, dir: -1 | 1) {
  const arr = colState.value
  const target = index + dir
  if (target < 0 || target >= arr.length) return
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  saveColState()
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
  const body = document.querySelector<HTMLElement>('.tpl-table .ant-table-body')
  const container = document.querySelector<HTMLElement>('.tpl-table .ant-table-container')
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

// 列排序：antd sorter（本地演示）；后端分页项目请用 sortable→orderBy 白名单（见 Person.vue）
function onTableChange(_pagination: any, filters: any, sorter: any) {
  // 列筛选（受控）：antd filters 回传 → colFilters；主查询变化会清空（见 watch）
  const next: Record<string, any[]> = {}
  for (const [k, v] of Object.entries(filters || {})) {
    const arr = (v as any[]) || []
    if (arr.length) next[k] = arr.map(String)
  }
  colFilters.value = next
  const key = sorter?.columnKey
  const order = sorter?.order
  if (!key || !order) { loadList(); return }
  demoRows.value = [...demoRows.value].sort((a: any, b: any) => {
    const va = a[key], vb = b[key]
    const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb))
    return order === 'ascend' ? cmp : -cmp
  })
}

// ===== 列宽拖拽（B.5.1.1：th 右缘 14px 起拖；操作列左/右缘 16px 均可） =====
function onColResize(key: string, w: number) {
  manualWidths.value = true
  try { localStorage.setItem(`${colStoreKey.value}:manual_widths`, '1') } catch { /* ignore */ }
  if (key === 'ops') { saveColState(); opsWidth.value = Math.max(120, Math.round(w)); try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ } return }
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}
let suppressHeaderClickUntil = 0
function onHeaderMouseDown(key: string, e: MouseEvent) {
  if (key === 'index' || key === 'selection') return
  const th = e.currentTarget as HTMLTableCellElement
  const rect = th.getBoundingClientRect()
  const nextIsOps = !!th.nextElementSibling?.classList.contains('ops-th')
  const atOpsLeft = key === 'ops' && e.clientX <= rect.left + 16
  const atOpsRight = key === 'ops' && e.clientX >= rect.right - 16
  const atPrevRightOfOps = nextIsOps && e.clientX >= rect.right - 16
  if (atOpsLeft || atOpsRight || atPrevRightOfOps) {
    const fromLeft = atOpsLeft || atPrevRightOfOps
    const startX = e.clientX
    const startW = opsWidth.value
    const onMove = (ev: MouseEvent) => onColResize('ops', fromLeft ? startW - (ev.clientX - startX) : startW + (ev.clientX - startX))
    const onUp = () => {
      document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp)
      document.body.style.cursor = ''; suppressHeaderClickUntil = Date.now() + 300
    }
    document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
    document.body.style.cursor = 'col-resize'
    e.preventDefault(); e.stopPropagation()
    return
  }
  if (key === 'ops') return
  if (e.clientX < rect.right - 14) return
  const startX = e.clientX
  const startW = rect.width
  const onMove = (ev: MouseEvent) => onColResize(key, startW + (ev.clientX - startX))
  const onUp = () => {
    document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''; suppressHeaderClickUntil = Date.now() + 300
  }
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  e.preventDefault(); e.stopPropagation()
}
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

// 负责人列筛选项：从当前数据取 distinct（报表项目改为接口返回枚举或固定字典）
const ownerFilters = computed(() => [...new Set(demoRows.value.map(r => r.owner))].map(v => ({ text: v, value: v })))
watch(ownerFilters, v => { const c = ALL_COLUMNS.find(a => a.key === 'owner'); if (c) (c as any).filters = v }, { immediate: true })

const columns = computed(() => {
  const idx = {
    title: '序号', key: 'index', width: 64, align: 'center' as const, fixed: 'left' as const,
    customHeaderCell: () => ({ class: 'zy-th-noresize' }),
  }
  const ops = {
    title: '操作', key: 'ops', width: opsWidth.value, fixed: 'right' as const,
    customHeaderCell: () => ({ class: 'ops-th', onMousedown: (e: MouseEvent) => onHeaderMouseDown('ops', e), onClick: onHeaderClick }),
  }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({
      ...ALL_COLUMNS.find(a => a.key === c.key)!,
      dataIndex: c.key,
      width: c.width ?? ALL_COLUMNS.find(a => a.key === c.key)!.width,
      ...(ALL_COLUMNS.find(a => a.key === c.key)!.sorter ? { sorter: true } : {}),
      ...(colFilters.value[c.key] ? { filteredValue: colFilters.value[c.key] } : {}),
      customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }),
    }))
  return [idx, ...list, ops]
})
const scrollWidth = computed(() => 48 + 64 + opsWidth.value + colState.value.filter(c => c.visible).reduce((sum, c) => sum + (c.width ?? ALL_COLUMNS.find(a => a.key === c.key)?.width ?? 100), 0))

// ===== 演示弹窗（B.5.1.2：mask-closable=false / keyboard=false / 保存校验） =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()
const initialForm = ref('')
const emptyForm = () => ({ id: null as string | null, code: '', name: '', orgId: undefined as string | undefined, type: '部门', status: '0', amount: undefined as number | undefined, owner: '', remark: '' })
const form = ref(emptyForm())
const codeInput = ref()
const reusedPrevious = ref(false)
const formDefaults = () => createSavedFormDefaults(sessionStorage, `system_template:last_saved:${sessionStorage.getItem('userId') || 'anon'}`, emptyForm, ['id'])
const rules: any = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
}
/** 业务接入时按用户和档案类型隔离缓存；主键始终来自空表单，当前选择的归属优先于缓存。 */
function openAdd(usePrevious = true) {
  isEdit.value = false
  form.value = usePrevious ? formDefaults().load() : emptyForm()
  if (selectedOrgId.value) form.value.orgId = selectedOrgId.value
  reusedPrevious.value = usePrevious && Boolean(form.value.code || form.value.name)
  initialForm.value = JSON.stringify(form.value)
  showDlg.value = true
  void nextTick(() => { formRef.value?.clearValidate(); codeInput.value?.focus({ cursor: 'all' }) })
}
function openEdit(row: any) { isEdit.value = true; form.value = { ...row } as any; initialForm.value = JSON.stringify(form.value); showDlg.value = true }
/** 保留用户未保存的输入；保存进行时不能关闭，避免重复提交。 */
function closeForm() {
  if (saving.value) return
  if (JSON.stringify(form.value) === initialForm.value) { showDlg.value = false; return }
  Modal.confirm({ title: '放弃未保存的修改？', content: '关闭后，本次输入不会保存。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { showDlg.value = false } })
}
/** 真实业务将内存写入替换为 await API，确认保存成功后才能记忆表单并准备下一条。 */
async function saveRow(continueAdding = false) {
  if (saving.value) return
  saving.value = true
  try {
    await formRef.value?.validate()
    if (demoRows.value.some(row => row.id !== form.value.id && row.code.toLocaleLowerCase() === form.value.code.trim().toLocaleLowerCase())) { message.warning('示例编码已存在，请修改编码后保存'); return }
    if (isEdit.value && form.value.id) {
      const i = demoRows.value.findIndex(row => row.id === form.value.id)
      if (i < 0) throw new Error('演示记录不存在，请刷新列表')
      demoRows.value[i] = { ...demoRows.value[i], ...form.value } as DemoRow
    } else {
      demoRows.value.unshift({ ...form.value, id: 'r' + Date.now(), amount: form.value.amount ?? 0 } as DemoRow)
    }
    formDefaults().save(form.value)
    if (continueAdding && !isEdit.value) {
      form.value.id = null
      reusedPrevious.value = true
      initialForm.value = JSON.stringify(form.value)
      await nextTick()
      formRef.value?.clearValidate()
      codeInput.value?.focus({ cursor: 'all' })
    } else showDlg.value = false
    message.success('已保存（内存演示，刷新即重置）')
  } catch (error: any) {
    const name = error?.errorFields?.[0]?.name
    if (name) formRef.value?.scrollToField(name)
    else message.error(error.message || '保存失败')
  } finally { saving.value = false }
}
function removeOne(row: any) {
  Modal.confirm({
    title: `删除「${row.name}」？`,
    content: '演示操作：仅内存生效，刷新即恢复',
    onOk: () => { demoRows.value = demoRows.value.filter(r => r.id !== row.id); message.success('已删除（内存演示）') },
  })
}
function toggleStatus(row: any, checked: any) {
  Modal.confirm({ title: checked ? '启用该记录？' : '停用该记录？', content: checked ? '启用后可再次用于新业务。' : '停用后不能新建引用，已有业务继续执行。', okText: '确认', cancelText: '取消', onOk: () => { row.status = checked ? '0' : '1'; message.success(checked ? '已启用（内存演示）' : '已停用（内存演示）') } })
}

// 以下为可选标准能力的演示；不连接业务接口，也不预设实际模块的审核、导入或删除规则。
// 批量启停共用一个入口；演示仅修改内存数据，不代表业务接口规则。
const batchStatusVisible = ref(false)
const batchStatusTarget = ref<string>()
function openBatchStatus() { batchStatusTarget.value = undefined; batchStatusVisible.value = true }
function applyBatchStatus() {
  if (batchStatusTarget.value !== '0' && batchStatusTarget.value !== '1') { message.warning('请选择启用或停用'); return }
  const selected = demoRows.value.filter(row => selectedRowKeys.value.includes(row.id))
  const changed = selected.filter(row => row.status !== batchStatusTarget.value)
  const skipped = selected.length - changed.length
  for (const row of changed) row.status = batchStatusTarget.value
  batchStatusVisible.value = false
  selectedRowKeys.value = []
  message.success(`成功 ${changed.length} 条，跳过 ${skipped} 条，失败 0 条（内存演示）`)
}
const batchVisible = ref(false)
const batchType = ref<string>()
function batchEdit() { batchType.value = undefined; batchVisible.value = true }
function applyBatchEdit() {
  if (!batchType.value) { message.warning('请选择需要修改的类型'); return }
  const type = batchType.value
  Modal.confirm({ title: `批量修改 ${selectedRowKeys.value.length} 条？`, content: `仅修改类型为「${type}」，其他字段保持原值。`, onOk: () => {
    let count = 0
    for (const row of demoRows.value) if (selectedRowKeys.value.includes(row.id)) { row.type = type; count++ }
    batchVisible.value = false; selectedRowKeys.value = []; showBatchResult('批量修改结果（内存演示）', count)
  } })
}
function batchAudit() {
  const selected = demoRows.value.filter(row => selectedRowKeys.value.includes(row.id))
  const ready = selected.filter(row => row.reviewStatus !== 'approved')
  Modal.confirm({ title: `批量审核 ${selected.length} 条？`, content: `可审核 ${ready.length} 条，已审核 ${selected.length - ready.length} 条将跳过。`, onOk: () => {
    ready.forEach(row => { row.reviewStatus = 'approved' }); selectedRowKeys.value = []
    showBatchResult('批量审核结果（内存演示）', ready.length, selected.filter(row => !ready.includes(row)).map(row => ({ name: row.name, reason: '已审核，跳过' })))
  } })
}
const exportVisible = ref(false)
const exportScope = ref('filtered')
function openExport() { exportScope.value = 'filtered'; exportVisible.value = true }
function confirmExport() { exportVisible.value = false; exportCsv(exportScope.value === 'selected') }

// 选择器保留弹窗内临时选择，只有点击确定才写回表单。
const pickerVisible = ref(false)
const pickerKind = ref<'org' | 'person'>('org')
const pickerSelected = ref<string[]>([])
const pickerMultiple = ref(false)
const pickerItems = computed(() => pickerKind.value === 'org'
    ? flatOrgs.map(o => ({ id: o.orgId, name: o.orgName, orgId: o.orgId }))
    : [{ id: '张三', name: '张三', orgId: 'd111' }, { id: '李四', name: '李四', orgId: 'd112' }, { id: '王五', name: '王五', orgId: 'd121' }])
function openPicker(kind: 'org' | 'person', multiple = false) {
  pickerKind.value = kind; pickerMultiple.value = multiple
  pickerSelected.value = kind === 'org' ? (form.value.orgId ? [form.value.orgId] : []) : form.value.owner.split('、').filter(Boolean)
  pickerVisible.value = true
}
function confirmPicker(values: string[]) {
  if (pickerKind.value === 'org') form.value.orgId = values[0]
  else form.value.owner = values.join('、')
}

const importVisible = ref(false)
const importInput = ref<HTMLInputElement>()
const importRows = ref<{ line: number; code: string; name: string; orgId: string; error: string }[]>([])
function downloadFile(name: string, content: string) {
  const url = URL.createObjectURL(new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url)
}
function downloadImportTemplate() { downloadFile('功能模板导入示例.csv', '编码,名称,组织编码\r\nT2001,演示记录,d111') }
/** CSV 支持带引号、逗号和转义引号；校验结果由用户确认后才写入内存。 */
function parseCsvLine(line: string): string[] {
  const fields: string[] = []; let value = ''; let quoted = false
  for (let i = 0; i < line.length; i++) { const ch = line[i]; if (ch === '"') { if (quoted && line[i + 1] === '"') { value += '"'; i++ } else quoted = !quoted } else if (ch === ',' && !quoted) { fields.push(value.trim()); value = '' } else value += ch }
  if (quoted) throw new Error('引号未闭合')
  fields.push(value.trim()); return fields
}
async function readImport(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return
  try {
    const lines = (await file.text()).replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.trim())
    if (lines.length < 2 || lines[0].trim() !== '编码,名称,组织编码') throw new Error('请使用下载的 CSV 模板')
    const seen = new Set(demoRows.value.map(r => r.code.toLowerCase()))
    importRows.value = lines.slice(1).map((line, index) => {
      let fields: string[] = []; let error = ''
      try { fields = parseCsvLine(line) } catch { error = 'CSV 引号格式错误' }
      const [code = '', name = '', orgId = ''] = fields
      if (!error && (fields.length !== 3 || !code || !name || !orgId)) error = '请填写编码、名称和组织编码'
      else if (!error && !flatOrgs.some(org => org.orgId === orgId)) error = '组织编码不存在'
      else if (!error && seen.has(code.toLowerCase())) error = '编码重复'
      if (!error) seen.add(code.toLowerCase())
      return { line: index + 2, code, name, orgId, error }
    })
  } catch (e: any) { importRows.value = []; message.error(e.message || '读取文件失败') } finally { input.value = '' }
}
function applyImport() {
  const valid = importRows.value.filter(row => !row.error)
  if (!valid.length) { message.warning('没有可导入的记录'); return }
  Modal.confirm({ title: '确认导入？', content: `有效 ${valid.length} 条，失败 ${importRows.value.length - valid.length} 条将跳过。`, onOk: () => {
    valid.forEach((row, i) => demoRows.value.unshift({ id: `import-${Date.now()}-${i}`, code: row.code, name: row.name, orgId: row.orgId, type: '部门', status: '0', amount: 0, owner: '', remark: '', reviewStatus: 'pending' }))
    message.success(`导入成功 ${valid.length} 条，失败 ${importRows.value.length - valid.length} 条（内存演示）`); importVisible.value = false; importRows.value = []
  } })
}

// ===== 导出 CSV（前端 blob，导当前筛选行；后端分页项目见 Person.vue 的 /system/person/export） =====
function exportCsv(selectedOnly = false) {
  const head = '编码,名称,所属组织,类型,状态,金额,负责人,备注'
  const orgName = (id: string) => flatOrgs.find(o => o.orgId === id)?.orgName || ''
  const data = selectedOnly ? demoRows.value.filter(r => selectedRowKeys.value.includes(r.id)) : colFilteredRows.value
  if (!data.length) { message.warning(selectedOnly ? '请先选择记录' : '没有可导出的记录'); return }
  const lines = data.map(r => [r.code, r.name, orgName(r.orgId), r.type, r.status === '0' ? '启用' : '停用', r.amount, r.owner, r.remark].map(v => `"${String(v ?? '').replaceAll('"', '""')}"`).join(','))
  const blob = new Blob(['\ufeff' + [head, ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '功能模板导出.csv'
  a.click()
  URL.revokeObjectURL(a.href)
  message.success(`已导出 ${data.length} 条`)
}

onMounted(() => {
  loadColState()
  loadList()
  void scheduleAutoFit()
  widthObserver = new ResizeObserver(() => { void scheduleAutoFit() })
  nextTick(() => {
    const area = document.querySelector<HTMLElement>('.tpl-table .ant-table-container')
    if (area) widthObserver?.observe(area)
  })
})
onBeforeUnmount(() => widthObserver?.disconnect())
watch(treeCollapsed, () => { void scheduleAutoFit() })
</script>

<template>
  <div class="tpl-container">
    <StandardListLayout v-model:collapsed="treeCollapsed">
    <!-- 页头行：查询条件左置（定版布局：无标题无返回） -->
    <template #filters>
      <div class="head-filters">
        <a-input v-model:value="keyword" placeholder="搜索编码/名称/负责人" allow-clear style="width:200px" @press-enter="runSearch">
          <template #prefix><SearchOutlined style="color:#86909C" /></template>
        </a-input>
        <a-select v-model:value="fType" allow-clear placeholder="类型" style="width:110px" @change="loadList">
          <a-select-option value="部门">部门</a-select-option>
          <a-select-option value="科室">科室</a-select-option>
          <a-select-option value="小组">小组</a-select-option>
        </a-select>
        <a-select v-model:value="fStatus" allow-clear placeholder="状态" style="width:100px" @change="loadList">
          <a-select-option value="0">启用</a-select-option>
          <a-select-option value="1">停用</a-select-option>
        </a-select>
      </div>
    </template>

    <!-- 按钮行：+新增（最左）→ 查询 → 重置 …… 批量/导出/列设置（最右） -->
    <template #actions>
    <div class="filter-bar">
      <a-button type="primary" @click="openAdd()"><PlusOutlined />新增</a-button>
      <a-button type="primary" @click="runSearch">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button :disabled="!hasSelection" @click="openBatchStatus">批量启停{{ hasSelection ? `（${selectedRowKeys.length}）` : '' }}</a-button>
      <a-button :disabled="!hasSelection" @click="batchEdit">批量修改</a-button>
      <a-button :disabled="!hasSelection" @click="batchAudit">批量审核</a-button>
      <a-button :disabled="!hasSelection" danger @click="removeBatch">
        删除选中{{ hasSelection ? `（${selectedRowKeys.length}）` : '' }}
      </a-button>
      <a-button @click="importRows = []; importVisible = true">↑ 导入</a-button>
      <a-button @click="openExport">↓ 导出</a-button>
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

    <!-- 左树 + 右表（树可收起；树面板头部"重置"与表头重置同行为） -->
    <template #sidebar>
        <div class="tree-head">
          <span class="tree-title">组织架构</span>
          <a v-if="selectedOrgId" class="tree-clear" @click="resetFilters">重置</a>
        </div>
        <div class="tree-body">
          <a-tree
            :tree-data="(demoTree as any)"
            :field-names="{ title: 'orgName', key: 'orgId', children: 'children' }"
            block-node
            default-expand-all
            :selected-keys="selectedOrgId ? [selectedOrgId] : []"
            @select="onTreeSelect"
          >
            <template #title="{ orgName, orgId, orgCategory }">
              <span class="tree-node">
                {{ orgName }}
                <a-tag v-if="orgCategory" :color="typeColorOf(orgCategory)" class="type-tag">{{ orgCategory }}</a-tag>
                <span v-if="orgCountMap[orgId]" class="tree-count">{{ orgCountMap[orgId] }}</span>
              </span>
            </template>
          </a-tree>
        </div>
    </template>
    <template #default>
        <a-table
          :columns="columns"
          :data-source="pagedRows"
          :loading="loading"
          :row-selection="{ selectedRowKeys, onChange: (keys: any[]) => { selectedRowKeys = keys as any } }"
          :pagination="{ total, current: pageNum, pageSize, showSizeChanger: true, showQuickJumper: true, showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, ps: number) => { pageNum = p; pageSize = ps } }"
          :scroll="{ x: scrollWidth, y: 'calc(100vh - 390px)' }"
          table-layout="fixed"
          row-key="id"
          size="middle"
          class="tpl-table zy-fill-table"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'index'">
              {{ (pageNum - 1) * pageSize + index + 1 }}
            </template>
            <template v-else-if="column.key === 'orgId'">
              {{ flatOrgs.find(o => o.orgId === record.orgId)?.orgName || '—' }}
            </template>
            <template v-else-if="column.key === 'type'">
              <a-tag :color="typeColorOf(record.type)">{{ record.type }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-switch :checked="record.status === '0'" checked-children="启用" un-checked-children="停用" @change="(v: any) => toggleStatus(record, v)" />
            </template>
            <template v-else-if="column.key === 'reviewStatus'">
              <a-tag :color="record.reviewStatus === 'approved' ? 'green' : 'orange'">{{ record.reviewStatus === 'approved' ? '已审核' : '待审核' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'amount'">
              {{ record.amount?.toLocaleString() }}
            </template>
            <template v-else-if="column.key === 'remark'">
              {{ record.remark || '—' }}
            </template>
            <template v-else-if="column.key === 'ops'">
              <div class="ops-btns">
                <a-button type="primary" size="small" @click="openEdit(record)">编辑</a-button>
                <a-button danger size="small" @click="removeOne(record)">删除</a-button>
              </div>
            </template>
          </template>
          <template #summary>
            <a-table-summary fixed>
              <a-table-summary-row>
                <a-table-summary-cell :index="0" :col-span="columns.length + 1" class="summary-cell">合计（筛选结果）　{{ summary.count }} 条　金额 ￥{{ summary.amount.toLocaleString() }}</a-table-summary-cell>
              </a-table-summary-row>
            </a-table-summary>
          </template>
        </a-table>
    </template>
    </StandardListLayout>

    <!-- 新增/编辑弹窗（B.5.1.2） -->
    <a-modal
      :open="showDlg"
      :mask-closable="false"
      :keyboard="false"
      :title="isEdit ? '编辑演示记录' : '新增演示记录'"
      width="640px"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      :body-style="{ maxHeight: 'min(65vh, 600px)', overflowY: 'auto' }"
      :footer="null"
      :closable="!saving"
      @cancel="closeForm"
    >
      <div v-if="!isEdit" class="continuous-add-tip"><span>{{ reusedPrevious ? '已带入上次成功保存的内容，修改编码和名称即可继续新增。' : '保存并新增会保留填写内容，供下一条记录修改使用。' }}</span><a-button type="link" size="small" :disabled="saving" @click="openAdd(false)">清空内容</a-button></div>
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="编码" name="code">
              <a-input ref="codeInput" v-model:value="form.code" :maxlength="20" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" name="name">
              <a-input v-model:value="form.name" :maxlength="30" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属组织" name="orgId">
              <a-input :value="flatOrgs.find(o => o.orgId === form.orgId)?.orgName || ''" readonly placeholder="点击选择组织" @click="openPicker('org')"><template #suffix><a @click.stop="openPicker('org')">选择</a></template></a-input>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="类型">
              <a-select v-model:value="form.type">
                <a-select-option value="部门">部门</a-select-option>
                <a-select-option value="科室">科室</a-select-option>
                <a-select-option value="小组">小组</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="金额">
              <a-input-number v-model:value="form.amount" style="width:100%" :min="0" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-input :value="form.owner" readonly placeholder="点击选择人员（支持多选）" @click="openPicker('person', true)"><template #suffix><a @click.stop="openPicker('person', true)">选择</a></template></a-input>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-switch v-model:checked="form.status" checked-value="0" un-checked-value="1" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="form.remark" :maxlength="100" :rows="2" placeholder="可不填" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="continuous-add-footer"><a-button :disabled="saving" @click="closeForm">取消</a-button><a-button v-if="!isEdit" :loading="saving" @click="saveRow(true)">保存并新增</a-button><a-button type="primary" :loading="saving" @click="saveRow()">保存</a-button></div>
    </a-modal>
    <!-- 统一查询选择器：树筛选、关键词、结果列表、临时选择与已选数。 -->
    <StandardPicker v-model:open="pickerVisible" v-model="pickerSelected" :title="pickerKind === 'org' ? '选择组织' : '选择人员'" :tree="demoTree" :items="pickerItems" :multiple="pickerMultiple" @confirm="confirmPicker" />
    <BatchResultModal v-model:open="batchResultOpen" :title="batchResultTitle" :succeeded="batchSucceeded" :failures="batchFailures" />
    <a-modal v-model:open="batchStatusVisible" title="批量启停演示" :mask-closable="false" ok-text="确认" cancel-text="取消" @ok="applyBatchStatus">
      <p>已选择 {{ selectedRowKeys.length }} 条记录，请选择目标状态。已是目标状态的记录将自动跳过。</p>
      <a-radio-group v-model:value="batchStatusTarget"><a-radio value="0">启用</a-radio><a-radio value="1">停用</a-radio></a-radio-group>
    </a-modal>
    <a-modal v-model:open="batchVisible" title="批量修改演示" :mask-closable="false" ok-text="下一步" cancel-text="取消" @ok="applyBatchEdit">
      <p>已选择 {{ selectedRowKeys.length }} 条，仅修改下方选定的字段。</p>
      <a-form layout="vertical"><a-form-item label="修改类型" required><a-select v-model:value="batchType" placeholder="请选择" :options="[{label:'部门',value:'部门'},{label:'科室',value:'科室'},{label:'小组',value:'小组'}]" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="exportVisible" title="导出演示" :mask-closable="false" ok-text="导出" cancel-text="取消" @ok="confirmExport">
      <a-radio-group v-model:value="exportScope"><a-radio value="filtered">当前筛选结果（{{ colFilteredRows.length }} 条）</a-radio><a-radio value="selected" :disabled="!hasSelection">选中记录（{{ selectedRowKeys.length }} 条）</a-radio></a-radio-group>
    </a-modal>
    <a-modal v-model:open="importVisible" title="导入演示" width="760px" :mask-closable="false" ok-text="导入有效记录" cancel-text="取消" @ok="applyImport">
      <p>下载模板，填写后选择 UTF-8 CSV 文件；校验后确认导入，演示数据刷新即重置。示例组织编码：总部 d1、生产中心 d11、一车间 d111、二车间 d112、销售中心 d12、华东销售科 d121。</p>
      <a-space><a-button @click="downloadImportTemplate">下载模板</a-button><a-button type="primary" @click="importInput?.click()">选择文件</a-button></a-space>
      <input ref="importInput" type="file" accept=".csv,text/csv" style="display:none" @change="readImport" />
      <ImportResultTable :rows="importRows" />
    </a-modal>
  </div>
</template>

<style scoped>
.continuous-add-tip { display:flex; align-items:center; justify-content:space-between; gap:8px; color:#86909C; font-size:12px; margin-bottom:12px; }
.continuous-add-footer { display:flex; justify-content:flex-end; gap:8px; }
.tpl-container { height: 100%; min-height: 0; }
.page-header { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.head-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); }
.filter-spacer { flex: 1 1 auto; min-width: 8px; }

/* 左树 + 右表 分栏（树可收起） */
.tpl-body { display: flex; flex: 1; align-items: stretch; gap: 0; min-height: 0; position: relative; }
.tpl-body.t-collapsed .tpl-main { margin-left: 14px; }
.tree-panel { width: 260px; flex: none; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; overflow: hidden; transition: width .2s, opacity .2s; margin-right: 8px; }
.tree-panel.collapsed { width: 0; height: 0; min-height: 0; opacity: 0; margin-right: 0; padding: 0; border: none; pointer-events: none; }
.tree-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid #F2F3F5; }
.tree-title { font-weight: 600; color: #1D2129; }
.tree-clear { color: #1E5EFF; cursor: pointer; font-size: 13px; }
.tree-body { flex: 1; overflow: auto; padding: 8px; font-size: 14px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.type-tag { font-size: 11px; line-height: 1; padding: 1px 5px; border-radius: 3px; margin: 0; }
.tree-count { font-size: 11px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #E8F3FF; color: #1E5EFF; display: inline-flex; align-items: center; justify-content: center; }

/* 折叠手柄：8px 窄竖条 + CSS 三角（展开贴面板内右缘，收起贴页面左缘） */
.tree-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: 244px; z-index: 6; width: 16px; height: 40px; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.tree-toggle::before { content: ''; width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }
.tree-toggle.at-left { left: 4px; }
.tree-toggle.at-left::before { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.tree-toggle:hover { border-color: #1E5EFF; }
.tree-toggle:hover::before { border-right-color: #1E5EFF; }
.tree-toggle.at-left:hover::before { border-left-color: #1E5EFF; border-right-color: transparent; }

.tpl-main { flex: 1; min-width: 0; }
.tpl-table :deep(.ant-table) { font-size: 14px; }
.tpl-table :deep(.ant-table-tbody .ant-btn-sm) { min-height: 28px; }
.tpl-table :deep(.ant-table-thead > tr > th) { background: #F7F8FA; font-weight: 600; }
.tpl-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

/* 操作列按钮组（紧凑，自适应列宽按其内容实测） */
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-btns .ant-btn-sm { padding-inline: 5px; }
.summary-cell { font-weight: 600; background: #F7F8FA; }

/* 列设置面板 */
.col-set-panel { width: 220px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }
</style>
