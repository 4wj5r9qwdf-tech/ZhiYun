<script setup lang="ts">
/**
 * 组织架构（人员模块）——原部门管理，万物皆部门后统一管理组织节点（部门/科室/小组）—— ant-design-vue：左树 + 右表分栏
 * - 左侧组织树（点节点→右侧只显示该节点及下级）
 * - 层级 = 树深度，按 ancestors 实时算（一级/二级/三级…），不依赖 org_category
 * - a-table 树形展开表 + 列设置 + 列宽拖拽（对齐人员信息标准布局）
 * - 操作：编辑 / 新增下级 / 删除；位置(楼/层/房间)渐进式；业务管辖关联
 */
import { ref, computed, onMounted, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import { SHOW_PARENT } from 'ant-design-vue/es/vc-tree-select'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'


// ===== 列表 =====
const tree = ref<any[]>([])        // 原始部门树（含 children）
const loading = ref(false)
const keyword = ref('')
// 左树选中节点（点节点→右侧只显示该节点及下级）
const selectedOrgId = ref<string | null>(null)
// 树面板收起/展开（默认展开）
const treeCollapsed = ref(false)
// 前端分页（组织树接口天然全量返回，列表切片展示）
const orgPage = ref(1)
const orgPageSize = ref(20)
// 筛选：层级 + 状态（前端过滤）
const filterCategory = ref<string | undefined>(undefined)
const filterStatus = ref<string | undefined>(undefined)
function resetFilters() { keyword.value = ''; filterCategory.value = undefined; filterStatus.value = undefined; selectedOrgId.value = null; treeCollapsed.value = false }

// 左侧组织树（a-tree 展开用）：全量树
const leftTree = computed(() => tree.value)
// 选中节点及其下级（右侧数据源）；未选=全部
const selectedSubtree = computed(() => {
  if (!selectedOrgId.value) return tree.value
  const node = findNode(tree.value, selectedOrgId.value)
  return node ? [node] : tree.value
})

// 业务管辖：每个部门管辖了多少被管部门（orgId -> 数），列表展示
const manageCounts = ref<Record<string, number>>({})
// 表单里被管部门选择（管理范围多选）
const managedIds = ref<string[]>([])

async function loadManageCounts() {
  try {
    const res: any = await api.get('/system/org/manage-counts')
    if (res.code === 200) manageCounts.value = res.data || {}
  } catch { /* ignore */ }
}
function manageCount(id: any) { return manageCounts.value[String(id)] || 0 }

// 全部列（不含操作列，操作列固定最后）
const ALL_COLUMNS = [
  { title: '部门名称', dataIndex: 'orgName', key: 'orgName' },
  { title: '类型', key: 'orgCategory', width: 80 },
  { title: '所在建筑', key: 'buildingId', width: 110 },
  { title: '位置', key: 'location', width: 160 },
  { title: '管辖', key: 'manages', width: 80 },
  { title: '负责人', dataIndex: 'leader', key: 'leader', width: 110 },
  { title: '电话', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '排序', dataIndex: 'orderNum', key: 'orderNum', width: 80 },
]

// 列设置：显隐 + 顺序 + 列宽，按"列表页+用户"持久化（localStorage）
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
const colStoreKey = computed(() => `org_list:${localStorage.getItem('userId') || 'anon'}`)
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 200)  // 操作列宽可拖拽并持久化

function loadColState() {
  try {
    const raw = localStorage.getItem(colStoreKey.value)
    if (raw) {
      const saved: ColState[] = JSON.parse(raw)
      const curKeys = new Set(ALL_COLUMNS.map(c => c.key))
      const ordered: ColState[] = []
      for (const s of saved) if (curKeys.has(s.key)) { ordered.push(s); curKeys.delete(s.key) }
      for (const c of ALL_COLUMNS) if (curKeys.has(c.key)) ordered.push({ key: c.key, visible: true })
      colState.value = ordered
      return
    }
  } catch { /* ignore */ }
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true }))
}
function saveColState() { try { localStorage.setItem(colStoreKey.value, JSON.stringify(colState.value)) } catch { /* ignore */ } }
function toggleCol(key: string) { const s = colState.value.find(c => c.key === key); if (s) { s.visible = !s.visible; saveColState() } }
function showAllCols() { colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true })); saveColState() }
async function autoFitCols() {
  // 1) 操作列：实测当前渲染的按钮组最宽一行 + 单元格内边距（内容真实宽，不拍脑袋）
  let opsW = 200
  const wraps = document.querySelectorAll('.ops-btns')
  if (wraps.length) {
    // 量按钮两端（.ops-btns 是 flex 容器会拉伸填满单元格，直接量容器宽会把摊派宽度当成内容宽）
    const maxContent = Math.max(...[...wraps].map(w => {
      const bs = [...w.querySelectorAll('button')]
      if (!bs.length) return 0
      return Math.max(...bs.map(b => b.getBoundingClientRect().right)) - Math.min(...bs.map(b => b.getBoundingClientRect().left))
    }))
    const td = wraps[0].closest('td')
    const cs = td ? getComputedStyle(td) : null
    const pad = cs ? Math.round(parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight)) : 16
    opsW = Math.max(120, Math.round(maxContent + pad + 2))
  }
  // 2) 各普通列按列名给足最小宽（空数据列也放得下表头）
  const mins = new Map<string, number>()
  let visibleSum = 64  // 序号列固定
  for (const c of colState.value) {
    const t = ALL_COLUMNS.find(a => a.key === c.key)?.title || c.key
    const w = Math.max(90, String(t).length * 16 + 48)
    mins.set(c.key, w)
    if (c.visible) visibleSum += w
  }
  visibleSum += opsW
  // 3) 剩余空间（容器宽 - 列总宽）均分给可见普通列：列总宽=容器宽后 antd 不再摊派，
  //    操作列保持实测贴合值（这是"自己按内容适配"而不是让表格暗中摊宽）
  const container = document.querySelector('.ant-table-container')
  const cw = container ? Math.round(container.clientWidth) : 0
  const visibleCount = colState.value.filter(c => c.visible).length
  if (cw > visibleSum + 10 && visibleCount > 0) {
    const extra = Math.floor((cw - visibleSum) / visibleCount)
    for (const c of colState.value) {
      if (c.visible) mins.set(c.key, (mins.get(c.key) || 90) + extra)
    }
  }
  for (const c of colState.value) {
    ;(c as any).width = mins.get(c.key)
  }
  saveColState()
  opsWidth.value = opsW
  try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ }
}
function colTitle(key: string) { return ALL_COLUMNS.find(c => c.key === key)?.title || key }
function moveCol(index: number, dir: -1 | 1) {
  const arr = colState.value
  const j = index + dir
  if (j < 0 || j >= arr.length) return
  ;[arr[index], arr[j]] = [arr[j], arr[index]]
  saveColState()
}

// 列宽拖拽：改 colState 里对应列的 width 并持久化
function onColResize(key: string, w: number) {
  if (key === 'ops') { opsWidth.value = Math.max(120, Math.round(w)); try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ } return }
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}

// 拖拽后短暂抑制 click（否则 mousedown→mouseup 后的 click 会触发 antd 排序，表格重排导致边框跳变）
let suppressHeaderClickUntil = 0

// 表头 mousedown：只在 th 右缘 14px 内才开始拖拽（否则让位给排序点击）
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
// 表头 click：刚拖拽过则吞掉，避免触发排序
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

// 按 colState 顺序+显隐生成实际列（操作列固定最后、不可隐藏）
// 每列加 customHeaderCell → 原生 th 绑 mousedown（右缘拖拽改宽），th 由 antd 布局保证与数据对齐
const columns = computed(() => {
  // 序号列表头禁画拖拽手柄线（全局 th::after 会无差别画线，叠加 fixed 列分界会花）
  const idx = { title: '序号', key: 'index', width: 64, align: 'center' as const, fixed: 'left' as const,
    customHeaderCell: () => ({ class: 'zy-th-noresize' }) }
  const ops = { title: '操作', key: 'ops', width: opsWidth.value, fixed: 'right' as const,
    customHeaderCell: () => ({ class: 'ops-th', onMousedown: (e: MouseEvent) => onHeaderMouseDown('ops', e), onClick: onHeaderClick }) }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({ ...ALL_COLUMNS.find(a => a.key === c.key)!, ...(c.width != null ? { width: c.width } : {}), customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) }))
    .filter(Boolean)
  return [idx, ...list, ops]
})

// 右侧数据源：平铺列表（树形交给左侧导航）——左树选中节点(及下级) + 关键词/类型/状态过滤
const orgRows = computed(() => {
  const base = selectedSubtree.value
  const kw = keyword.value.trim()
  const type = filterCategory.value   // 类型筛选：'部门'/'科室'/'小组'
  const st = filterStatus.value
  const out: any[] = []
  const walk = (nodes: any[]) => {
    for (const n of nodes) {
      const ok = (!kw || String(n.orgName || '').includes(kw))
        && (!type || n.orgCategory === type)
        && (!st || n.status === st)
      if (ok) out.push({ ...n, children: undefined })
      walk(n.children || [])
    }
  }
  walk(base)
  return out
})
// 分页切片
const pagedRows = computed(() => orgRows.value.slice((orgPage.value - 1) * orgPageSize.value, orgPage.value * orgPageSize.value))
// 筛选/树选变化时回到第一页
watch([keyword, filterCategory, filterStatus, selectedOrgId], () => { orgPage.value = 1 })

async function loadTree() {
  loading.value = true
  try {
    const res: any = await api.get('/system/org/list')
    if (res.code === 200) tree.value = buildTree(res.data || [])
    else message.error(res.msg || '加载部门失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '加载部门失败')
  } finally { loading.value = false }
}

// 平铺列表 → 嵌套树（接口返回平铺，前端按 parentId 组树，否则子部门全显示成顶层）
function buildTree(list: any[]): any[] {
  const map: Record<string, any> = {}
  for (const n of list) map[String(n.orgId)] = { ...n, children: [] }
  const roots: any[] = []
  for (const n of list) {
    const node = map[String(n.orgId)]
    const pid = n.parentId
    if (pid && pid !== 0 && pid !== '0' && map[String(pid)]) map[String(pid)].children.push(node)
    else roots.push(node)
  }
  return roots
}

// 建筑档案（车间"所在建筑"下拉 + 列表展示建筑名用）
const buildings = ref<any[]>([])
const buildingNameMap = computed(() => {
  const m: Record<string, string> = {}
  for (const b of buildings.value) m[String(b.plantId ?? b.plant_id)] = b.plantName ?? b.plant_name
  return m
})
function buildingLabel(id: any) {
  if (id === null || id === undefined || id === '') return ''
  return buildingNameMap.value[String(id)] || '—'
}
// 组装位置路径：楼/层/尾段，选到哪段拼到哪段（不强制填满）
function composeLocation() {
  const parts: string[] = []
  const b = form.value.buildingId ? buildingLabel(form.value.buildingId) : ''
  if (b && b !== '—') parts.push(b)
  if (form.value.floor != null && form.value.floor !== 0) parts.push(`${form.value.floor}层`)
  const tail = (form.value.location || '').trim().replace(/^\/+|\/+$/g, '')
  if (tail) parts.push(tail)
  return parts.join('/')
}
async function loadBuildings() {
  try {
    const res: any = await api.get('/system/building/list')
    if (res.code === 200) buildings.value = (res.data || []).map((x: any) => ({
      plantId: x.plantId ?? x.plant_id,
      plantName: x.plantName ?? x.plant_name,
    }))
  } catch { /* ignore */ }
}

// 状态切换（0 启用 / 1 停用），走编辑接口整行提交
async function toggleStatus(row: any, checked: any) {
  const res: any = await api.put('/system/org', {
    orgId: row.orgId, parentId: row.parentId, orgName: row.orgName,
    orgCategory: row.orgCategory, buildingId: row.buildingId ?? null, orderNum: row.orderNum,
    leader: row.leader, phone: row.phone, email: row.email,
    status: checked ? '0' : '1',
  })
  if (res.code === 200) { message.success(checked ? '已启用' : '已停用'); loadTree() }
  else { message.error(res.msg || '操作失败'); loadTree() }
}

// ===== 表单弹窗 =====
// 类型 tag 配色（档案标识，仅展示）
function typeColorOf(t: string) { return t === '小组' ? 'green' : t === '科室' ? 'blue' : 'default' }

// ===== 负责人：人员下拉（leader 存 user_id；bigint 一律字符串传输） =====
const persons = ref<any[]>([])
const personNameMap = computed(() => {
  const m: Record<string, string> = {}
  for (const p of persons.value) m[String(p.user_id)] = String(p.nick_name || p.user_name || '')
  return m
})
function personLabel(id: any) {
  if (id === null || id === undefined || id === '') return ''
  return personNameMap.value[String(id)] || String(id)
}
async function loadPersons() {
  try {
    const res: any = await api.get('/system/person/list', { params: { pageNum: 1, pageSize: 500 } })
    if (res.code === 200) persons.value = res.data?.rows || []
  } catch { /* ignore */ }
}

function personFilter(input: string, option: any) { return String(option.children).includes(input) }

const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  orgId: null as string | null,
  parentId: undefined as string | undefined,
  orgName: '',
  orgCategory: '部门',
  buildingId: undefined as number | undefined,   // 所在建筑（哪栋楼，可选，所有层级都能填）
  floor: undefined as number | undefined,        // 楼层（几层，可选；buildingId 选了才出现）
  location: '',                                   // 位置路径尾段（车间/房间等，可选，自由填）
  orderNum: 0,
  leader: undefined as string | undefined,
  phone: '',
  email: '',
  status: '0',
})
const form = ref(emptyForm())

// 上级部门树选择（编辑时排除自己及子孙，防成环）
const parentTree = computed(() => {
  const excludeId = isEdit.value ? form.value.orgId : null
  const walk = (nodes: any[]): any[] => nodes
    .filter(n => String(n.orgId) !== String(excludeId))
    .map(n => ({ ...n, children: walk(n.children || []) }))
  return walk(tree.value)
})

// 管辖范围树选择：排除自己，但把自己的子节点提升上来（如生产部不显示，但它的 1车间生产科 可选）
const manageTree = computed(() => {
  const selfId = isEdit.value ? String(form.value.orgId) : null
  const walk = (nodes: any[]): any[] => {
    const out: any[] = []
    for (const n of nodes) {
      const kids = walk(n.children || [])
      if (String(n.orgId) === selfId) {
        // 排除自己，但子节点提升到当前层级（保留可选）
        out.push(...kids)
      } else {
        out.push({ ...n, children: kids })
      }
    }
    return out
  }
  return walk(tree.value)
})

const rules: any = {
  orgName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

function findNode(nodes: any[], id: string): any {
  for (const n of nodes) {
    if (String(n.orgId) === id) return n
    const f = findNode(n.children || [], id)
    if (f) return f
  }
  return null
}

// ===== 层级 = 树深度（按 ancestors 实时算，不依赖 org_category）=====
// ancestors 形如 "0" / "0,父id" / "0,父id,祖父id" → 段数 = 层级（顶层=1级）
// 由上级推导本级层级（新节点）：父级深度+1



function openAddTop() {
  isEdit.value = false
  form.value = emptyForm()
  managedIds.value = []
  showDlg.value = true
}

// 新增下级：parentId=当前部门；层级=父级深度+1
function openAddChild(row: any) {
  isEdit.value = false
  form.value = emptyForm()
  form.value.parentId = String(row.orgId)
  form.value.buildingId = row.buildingId ?? undefined   // 默认带出上级所在建筑（同一栋楼）
  managedIds.value = []
  showDlg.value = true
}

// 弹窗内手动换上级：当前建筑为空时自动带出新上级的建筑
function onParentPicked(val: any) {
  if (!val || form.value.buildingId) return
  const n = findNode(tree.value, String(val))
  if (n?.buildingId) form.value.buildingId = n.buildingId
}

async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await api.get(`/system/org/${row.orgId}`)
  if (res.code !== 200) { message.error('加载详情失败'); return }
  const d = res.data
  // 回填位置：location 是拼好的路径（楼/层/尾段），反解出 buildingId/floor/尾段
  const loc = d.location || ''
  const bName = buildingLabel(d.buildingId)
  let rest = loc
  if (bName && loc.startsWith(bName)) rest = loc.slice(bName.length).replace(/^\/+/, '')
  const segs = rest.split('/').filter(Boolean)
  let floor: number | undefined
  let tail = ''
  if (segs.length && /^\d+层?$/.test(segs[0])) { floor = Number(segs[0].replace('层', '')); tail = segs.slice(1).join('/') }
  else tail = segs.join('/')
  form.value = {
    orgId: String(d.orgId),
    parentId: d.parentId === 0 || d.parentId === '0' ? undefined : String(d.parentId),
    orgName: d.orgName || '',
    orgCategory: d.orgCategory || '部门',
    buildingId: d.buildingId ? Number(d.buildingId) : undefined,
    floor,
    location: tail,
    orderNum: Number(d.orderNum ?? 0),
    leader: d.leader ? String(d.leader) : undefined,
    phone: d.phone || '',
    email: d.email || '',
    status: d.status || '0',
  }
  showDlg.value = true
  loadManages(String(d.orgId))
}

// 加载该部门当前管辖的被管部门
async function loadManages(orgId: string) {
  try {
    const res: any = await api.get(`/system/org/${orgId}/manages`)
    if (res.code === 200) managedIds.value = (res.data || []).map((x: any) => String(x))
  } catch { managedIds.value = [] }
}

// 新增下级后：把新部门自动纳入直接上级的管辖范围（父的管辖列表追加新部门id）
async function autoManageNewChild(parentId: string, childName: string) {
  try {
    await loadTree()   // 刷新，拿到新部门的 orgId
    const parent = findNode(tree.value, String(parentId))
    if (!parent) return
    const child = (parent.children || []).find((n: any) => n.orgName === childName)
    if (!child) return
    const cur: any = await api.get(`/system/org/${parentId}/manages`)
    const ids: string[] = cur.code === 200 ? (cur.data || []).map((x: any) => String(x)) : []
    const cid = String(child.orgId)
    if (!ids.includes(cid)) {
      ids.push(cid)
      await api.put(`/system/org/${parentId}/manages`, ids)
    }
  } catch { /* 自动纳入失败不影响保存 */ }
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch { return }
  saving.value = true
  try {
    const payload: any = {
      orgId: form.value.orgId ?? undefined,
      parentId: form.value.parentId ?? 0,
      orgName: form.value.orgName,
      orgCategory: form.value.orgCategory,
      buildingId: form.value.buildingId ?? null,
      location: composeLocation(),
      orderNum: form.value.orderNum ?? 0,
      leader: form.value.leader ?? null,
      phone: form.value.phone || '',
      email: form.value.email || '',
      status: form.value.status || '0',
    }
    const res: any = isEdit.value ? await api.put('/system/org', payload) : await api.post('/system/org', payload)
    if (res.code === 200) {
      // 编辑时同步保存管辖范围（新增部门先保存再编辑设置管辖）
      if (isEdit.value) await api.put(`/system/org/${payload.orgId}/manages`, managedIds.value)
      // 新增下级时，自动把新部门纳入直接上级的管辖范围
      if (!isEdit.value && payload.parentId) await autoManageNewChild(payload.parentId, payload.orgName)
      message.success('保存成功'); showDlg.value = false; loadTree(); loadManageCounts()
    }
    else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

// 删除：后端会校验有无子部门/人员占用，失败时弹后端 msg
function removeOrg(row: any) {
  Modal.confirm({
    title: '删除组织',
    content: `确认删除「${row.orgName}」？若有下级部门或已分配人员，后端将拒绝删除。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.delete(`/system/org/${row.orgId}`)
      if (res.code === 200) { message.success('已删除'); loadTree() }
      else message.error(res.msg || '删除失败')
    },
  })
}

// 弹窗关闭时重置校验状态
watch(showDlg, (v) => { if (!v) formRef.value?.clearValidate() })

// 左树点节点 → 右侧只显示该节点及下级，并自动展开该节点的行（再点取消）
function onTreeSelect(keys: any[]) {
  selectedOrgId.value = keys.length ? String(keys[0]) : null
}

onMounted(() => { loadTree(); loadBuildings(); loadManageCounts(); loadColState(); loadPersons() })
</script>

<template>
  <div class="org-page">
    <!-- 页头行：← 组织架构 + 查询条件（搜索/类型/状态） -->
    <div class="page-header">
      <div class="head-filters">
        <a-input v-model:value="keyword" placeholder="搜索组织名称" allow-clear style="width:200px" @press-enter="loadTree">
          <template #prefix><SearchOutlined style="color:#86909C" /></template>
        </a-input>
        <a-select v-model:value="filterCategory" allow-clear placeholder="类型" style="width:110px" @change="loadTree">
          <a-select-option value="部门">部门</a-select-option>
          <a-select-option value="科室">科室</a-select-option>
          <a-select-option value="小组">小组</a-select-option>
        </a-select>
        <a-select v-model:value="filterStatus" allow-clear placeholder="状态" style="width:100px" @change="loadTree">
          <a-select-option value="0">启用</a-select-option>
          <a-select-option value="1">停用</a-select-option>
        </a-select>
      </div>
    </div>

    <!-- 按钮行：新增组织（最左）→ 查询 → 重置 …… 列设置（最右） -->
    <div class="filter-bar">
      <a-button type="primary" @click="openAddTop"><PlusOutlined />新增组织</a-button>
      <a-button type="primary" @click="loadTree">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
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
              <a size="small" @click="autoFitCols">自适应列宽</a>
              <a-button type="primary" size="small" @click="colSetOpen = false">完成</a-button>
            </div>
          </a-card>
        </template>
      </a-dropdown>
    </div>

    <!-- 树 + 表格 并排（树可向左收起，收起后表格占满） -->
    <div class="org-body" :class="{ 't-collapsed': treeCollapsed }">
      <!-- 左：组织树 -->
      <div class="org-tree-panel" :class="{ collapsed: treeCollapsed }">
        <div class="tree-head">
          <span class="tree-title">组织架构</span>
          <a v-if="selectedOrgId" class="tree-clear" @click="resetFilters">重置</a>
        </div>
        <div class="tree-body">
          <a-tree
            v-if="leftTree.length"
            :tree-data="leftTree"
            :field-names="{ title: 'orgName', key: 'orgId', children: 'children' }"
            block-node
            default-expand-all
            :selected-keys="selectedOrgId ? [selectedOrgId] : []"
            @select="onTreeSelect"
          >
            <template #title="{ orgName, orgCategory }">
              <span class="tree-node">
                {{ orgName }}
                <a-tag v-if="orgCategory" :color="typeColorOf(orgCategory)" class="type-tag">{{ orgCategory }}</a-tag>
              </span>
            </template>
          </a-tree>
          <a-empty v-else description="暂无组织，点右上'新增组织'创建顶层节点" />
        </div>
      </div>

      <!-- 收起/展开手柄 -->
      <div class="tree-toggle" :class="{ 'at-left': treeCollapsed }" :title="treeCollapsed ? '展开组织树' : '收起组织树'" @click="treeCollapsed = !treeCollapsed" />
    

      <!-- 右：表格区 -->
      <div class="org-main">


    <!-- 树表 -->
    <a-table
      :columns="columns"
      :data-source="pagedRows"
      children-column-name="_flat_"
      :loading="loading"
      :pagination="{ total: orgRows.length, current: orgPage, pageSize: orgPageSize, showSizeChanger: true, showQuickJumper: true, showTotal: (t: number) => `共 ${t} 个组织`, onChange: (p: number, ps: number) => { orgPage = p; orgPageSize = ps } }"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
      table-layout="fixed"
      row-key="orgId"
      size="middle"
      class="org-table"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'index'">
          {{ index + 1 }}   <!-- 树形无分页，按显示行连续编号 -->
        </template>
        <template v-if="column.key === 'orgName'">
          <span class="org-name">{{ record.orgName }}</span>
        </template>
        <template v-else-if="column.key === 'orgCategory'">
          <a-tag :color="typeColorOf(record.orgCategory)">{{ record.orgCategory || '部门' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'buildingId'">
          {{ buildingLabel(record.buildingId) || '—' }}
        </template>
        <template v-else-if="column.key === 'location'">
          <span v-if="record.location" class="loc-cell">📍 {{ record.location }}</span>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'manages'">
          <a-tag v-if="manageCount(record.orgId)" color="geekblue">管 {{ manageCount(record.orgId) }}</a-tag>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'leader'">
          {{ personLabel(record.leader) || '-' }}
        </template>
        <template v-else-if="column.key === 'phone'">
          {{ record.phone || '-' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-switch
            :checked="String(record.status) === '0'"
            checked-children="启用"
            un-checked-children="停用"
            @change="toggleStatus(record, $event)"
          />
        </template>
        <template v-else-if="column.key === 'ops'">
          <div class="ops-btns">
            <a-button type="primary" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" @click="openAddChild(record)">新增下级</a-button>
            <a-button danger size="small" @click="removeOrg(record)">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>
    </div><!-- /org-main -->
    </div><!-- /org-body -->

    <!-- 新增/编辑（a-modal + a-form） -->
    <a-modal
      :mask-closable="false"
      :keyboard="false"
      v-model:open="showDlg"
      :title="isEdit ? '编辑组织' : '新增组织'"
      width="640px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="组织名称" name="orgName">
              <a-input v-model:value="form.orgName" :maxlength="30" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="档案类型">
              <a-select v-model:value="form.orgCategory">
                <a-select-option value="部门">部门</a-select-option>
                <a-select-option value="科室">科室</a-select-option>
                <a-select-option value="小组">小组</a-select-option>
              </a-select>
              <div class="form-tip">仅作档案标识与展示区分，不影响数据权限逻辑</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="上级组织">
              <a-tree-select
                v-model:value="form.parentId"
                :tree-data="parentTree"
                :field-names="{ label: 'orgName', value: 'orgId', children: 'children' }"
                allow-clear
                tree-default-expand-all
                placeholder="不选 = 顶层"
                @change="onParentPicked"
               
              />
            </a-form-item>
          </a-col>
          <!-- 所在位置（渐进式：选楼→选层→填车间/房间，选到哪段算哪段，均可不填） -->
          <a-col :span="12">
            <a-form-item label="所在建筑（哪栋楼，可不填）">
              <a-select v-model:value="form.buildingId" allow-clear placeholder="请选择建筑（建筑档案）">
                <a-select-option v-for="b in buildings" :key="Number(b.plantId)" :value="Number(b.plantId)">{{ b.plantName }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col v-if="form.buildingId" :span="12">
            <a-form-item label="楼层（几层，可不填）">
              <a-input-number v-model:value="form.floor" :min="1" :max="99" style="width:100%" placeholder="如 3" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="位置（车间/房间，可不填；如 裁剪车间 / 302室）">
              <a-input v-model:value="form.location" :maxlength="100" placeholder="选到哪填到哪，不强制" />
              <div v-if="composeLocation()" class="loc-preview">📍 {{ composeLocation() }}</div>
            </a-form-item>
          </a-col>
          <!-- 业务管辖（管理部门管哪些部门，与组织树无关，可不填） -->
          <a-col :span="24">
            <a-form-item label="管辖范围（本部门管理哪些部门，可不填；如生产部管三个车间）">
              <a-tree-select
                v-model:value="managedIds"
                :tree-data="manageTree"
                :field-names="{ label: 'orgName', value: 'orgId', children: 'children' }"
                multiple
                allow-clear
                tree-checkable
                tree-default-expand-all
                :show-checked-strategy="SHOW_PARENT"
                placeholder="不选 = 不管理部门"
              />
              <div v-if="managedIds.length" class="loc-preview">🤝 管辖 {{ managedIds.length }} 个部门（业务管理关系，不改变组织树层级）</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.orderNum" :min="0" style="width:100%" placeholder="数字越小越靠前" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-select v-model:value="form.leader" allow-clear placeholder="选人员（可空）" show-search :filter-option="personFilter">
                <a-select-option v-for="p in persons" :key="String(p.user_id)" :value="String(p.user_id)">{{ p.nick_name || p.user_name }}（{{ p.user_name }}）</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="电话">
              <a-input v-model:value="form.phone" :maxlength="11" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="邮箱">
              <a-input v-model:value="form.email" :maxlength="50" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-switch v-model:checked="form.status" checked-value="0" un-checked-value="1" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.head-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* 左树 + 右表 分栏 */
/* 树 + 表格 并排（标题行/筛选栏下方） */
.org-body { display: flex; align-items: stretch; gap: 0; min-height: calc(100vh - 300px); position: relative; }
.org-body.t-collapsed .org-main { margin-left: 14px; }
.org-tree-panel { width: 260px; flex: none; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; overflow: hidden; transition: width .2s, opacity .2s; margin-right: 8px; }
.org-tree-panel.collapsed { width: 0; height: 0; min-height: 0; opacity: 0; margin-right: 0; padding: 0; border: none; pointer-events: none; }
.tree-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid #F0F2F5; }
.tree-title { font-size: 14px; font-weight: 600; color: #1D2129; white-space: nowrap; }
.tree-clear { font-size: 12px; color: #1E5EFF; cursor: pointer; white-space: nowrap; }
.tree-body { flex: 1; overflow: auto; padding: 8px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.type-tag { font-size: 11px; line-height: 1; padding: 1px 5px; border-radius: 3px; background: #E8FFEA; color: #00B42A; }
/* 收起/展开手柄：竖条 + 图标，贴在树右缘 */
.tree-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: 248px; z-index: 6; width: 8px; height: 36px; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.tree-toggle::before { content: ''; width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }
.tree-toggle.at-left { left: 4px; }
.tree-toggle.at-left::before { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.tree-toggle:hover, .tree-toggle:hover::before { border-color: #1E5EFF; }
.tree-toggle:hover::before { border-right-color: #1E5EFF; }
.tree-toggle.at-left:hover::before { border-left-color: #1E5EFF; border-right-color: transparent; }
.org-main { flex: 1; min-width: 0; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-spacer { flex: 1 1 auto; min-width: 8px; }

/* 表格工具行（条数 + 列设置） */
.table-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.table-bar .total { font-size: 13px; color: #4E5969; }
.col-set-panel { width: 200px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }

.org-table { width: 100%; background: #fff; border-radius: 10px; }
.org-table :deep(.ant-table) { font-size: 13px; }
.org-table :deep(.ant-table-thead > tr > th),
.org-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.org-table :deep(.ant-table-thead > tr > th:last-child),
.org-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.org-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.org-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.org-name { font-weight: 500; color: #1D2129; }
.ops-btns .ant-btn-sm { padding-inline: 5px; }
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-link { display: inline-flex; align-items: center; gap: 4px; margin-right: 10px; font-size: 13px; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
.loc-preview { margin-top: 4px; font-size: 12px; color: #1E5EFF; background: #F4F7FF; border-radius: 4px; padding: 2px 8px; }
.loc-cell { color: #1E5EFF; font-size: 12px; }
.muted { color: #C9CDD4; }

/* 表格纵向滚动条：细条+去箭头（操作列右缘拖拽不被滚动条按钮截获） */
.org-table :deep(.ant-table-body) { scrollbar-width: thin; }
.org-table :deep(.ant-table-body::-webkit-scrollbar) { width: 8px; }
.org-table :deep(.ant-table-body::-webkit-scrollbar-button) { display: none; height: 0; }
.org-table :deep(.ant-table-body::-webkit-scrollbar-thumb) { background: #C9CDD4; border-radius: 4px; }
.org-table :deep(.ant-table-body::-webkit-scrollbar-track) { background: transparent; }
</style>

<!-- 非 scoped：给 antd 原生表头 th 加右缘拖拽手柄（::after 画线，th 自带 mousedown） -->
<style>
.ant-table-thead > tr > th { position: relative; }
.ant-table-thead > tr > th.zy-th-noresize::after { display: none; }
.ant-table-thead > tr > th::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; transition: border-color .12s, background .12s; }
.ant-table-thead > tr > th:hover::after { border-right-color: #C9CDD4; }
.ant-table-thead > tr > th:active::after { background: rgba(30,94,255,.15); border-right-color: #1E5EFF; }
</style>
