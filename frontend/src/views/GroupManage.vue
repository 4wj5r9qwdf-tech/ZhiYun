<script setup lang="ts">
/**
 * 小组管理（人员模块）—— ant-design-vue：独立小组列表页
 * - 小组 = sys_dept 里 dept_category='小组' 的行（区别于普通部门；普通部门 deptCategory 存 "1 级"/"2 级" 等）
 * - GET /system/dept/list 返回平铺数组，前端按 parentId 组树（层级按 ancestors 深度算，车间 = depth 2 节点）
 * - 列表列：小组名 / 所属部门(直接父) / 所属车间(ancestors 链 depth=2 反查) / 负责人 / 排序 / 状态 / 操作
 * - 表单：所属部门（所有非小组部门任意层级）→ 自动带出该部门链路上的车间，可手动改；deptCategory 固定 '小组'
 * - a-table + 列设置(colState) + 列宽拖拽（customHeaderCell 绑原生 th mousedown，对齐人员信息标准布局）
 * - deptId 一律 String（bigint 字符串，禁 Number() 防丢精度）
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

const router = useRouter()

// ===== 部门数据（平铺 → 组树）=====
const tree = ref<any[]>([])          // 嵌套树（buildTree 生成，含 children）
const flat = ref<any[]>([])          // 平铺原始数组（组树前）
const loading = ref(false)
const keyword = ref('')              // 筛选：小组名关键词

// id 索引：deptId(String) -> 节点
const byId = computed(() => {
  const m: Record<string, any> = {}
  const walk = (nodes: any[]) => { for (const n of nodes) { m[String(n.deptId)] = n; walk(n.children || []) } }
  walk(tree.value)
  return m
})

// 建筑档案（厂房等）：所属车间 = 部门所在建筑（厂房）名
const buildings = ref<any[]>([])
const buildingNameMap = computed(() => {
  const m: Record<string, string> = {}
  for (const b of buildings.value) m[String(b.plantId ?? b.plant_id)] = String(b.plantName ?? b.plant_name ?? '')
  return m
})
function buildingLabel(id: any) {
  if (id === null || id === undefined || id === '') return ''
  return buildingNameMap.value[String(id)] || ''
}
async function loadBuildings() {
  try {
    const res: any = await api.get('/system/building/list')
    if (res.code === 200) buildings.value = res.data || []
  } catch { /* ignore */ }
}

// 人员（负责人下拉）：leader 存 user_id
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

// 平铺 → 嵌套树（参考 DeptManage.vue buildTree；deptId/parentId 均 String）
function buildTree(list: any[]): any[] {
  const map: Record<string, any> = {}
  for (const n of list) map[String(n.deptId)] = { ...n, children: [] }
  const roots: any[] = []
  for (const n of list) {
    const node = map[String(n.deptId)]
    const pid = n.parentId
    if (pid && pid !== 0 && pid !== '0' && map[String(pid)]) map[String(pid)].children.push(node)
    else roots.push(node)
  }
  return roots
}

// 直接父节点（parentId 对应的部门）
function findParent(n: any): any {
  const pid = n?.parentId
  if (!pid || pid === 0 || pid === '0') return null
  return byId.value[String(pid)] || null
}

// ===== 小组列表行（过滤 + 展示字段派生）=====
// 小组 = deptCategory === '小组'（新数据只用这个字段判断，不再看车间/区域）
const rows = computed(() => {
  const kw = keyword.value.trim()
  const out: any[] = []
  const walk = (nodes: any[]) => {
    for (const n of nodes) {
      if (n.deptCategory === '小组') {
        if (!kw || String(n.deptName || '').includes(kw)) {
          const parent = findParent(n)
          // 去掉 children：小组是末级，平铺展示（否则 a-table 误渲染树形展开 +）
          const { children: _c, ...rest } = n
          out.push({ ...rest, parentName: parent?.deptName || '', workshopName: buildingLabel(parent?.buildingId) })
        }
      }
      walk(n.children || [])
    }
  }
  walk(tree.value)
  return out
})

function resetFilters() { keyword.value = '' }

async function loadAll() {
  loading.value = true
  try {
    const res: any = await api.get('/system/dept/list')
    if (res.code === 200) {
      flat.value = res.data || []
      tree.value = buildTree(flat.value)
    } else {
      tree.value = []
      message.error(res.msg || '加载部门失败')
    }
  } catch (e: any) {
    tree.value = []
    message.error(e?.response?.data?.msg || '加载部门失败')
  } finally { loading.value = false }
}

// ===== 表单：新增/编辑小组 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  deptId: '' as string,
  parentId: undefined as string | undefined,   // 所属部门（普通部门）
  workshopId: undefined as string | undefined, // 所属车间（展示用，parentId 链路上 depth=2 节点）
  deptName: '',
  leader: '',
  orderNum: 0,
  status: '0',                                  // '0' 启用 / '1' 停用
})
const form = ref(emptyForm())

// 所属部门下拉：任意部门/小组都可当父（小组可挂小组 → 支持 二级小组，如 缝纫1组 挂 缝纫组）；
// 编辑时排除自己及自己的子孙（防成环）
const deptTreeOptions = computed(() => {
  const selfId = isEdit.value ? String(form.value.deptId) : null
  const walk = (nodes: any[]): any[] => {
    const out: any[] = []
    for (const n of nodes) {
      if (String(n.deptId) === selfId) continue   // 排除自己（含其子树）
      out.push({ ...n, children: walk(n.children || []) })
    }
    return out
  }
  return walk(tree.value)
})

// 车间下拉：组织树 depth=2 节点（车间按新层级定义）
// 所属车间下拉 = 建筑（厂房）选项；值为 buildingId
const workshopOptions = computed(() => buildings.value.map(b => ({ id: String(b.plantId ?? b.plant_id), name: String(b.plantName ?? b.plant_name ?? '') })))

// 由所选部门反查它的所在建筑（厂房）→ 车间默认带出
function workshopOfDept(deptId?: string): string | undefined {
  if (!deptId) return undefined
  const node = byId.value[String(deptId)]
  if (!node || !node.buildingId) return undefined
  return String(node.buildingId)
}

function onDeptChange(v: any) {
  // 换部门后自动带出该部门所在建筑（厂房）；取不到则清空（可手动改/留空）
  form.value.workshopId = workshopOfDept(v)
}

const rules: any = {
  deptName: [{ required: true, message: '请输入小组名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
}

function openAdd() {
  isEdit.value = false
  form.value = emptyForm()
  showDlg.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  const parent = findParent(row)   // 所属部门
  form.value = {
    deptId: String(row.deptId),
    parentId: row.parentId ? String(row.parentId) : undefined,
    workshopId: parent?.buildingId ? String(parent.buildingId) : undefined,
    deptName: row.deptName || '',
    leader: row.leader || '',
    orderNum: Number(row.orderNum ?? 0),   // orderNum 非 bigint，可安全转 Number
    status: String(row.status) === '0' ? '0' : '1',
  }
  showDlg.value = true
}

// 状态展示：'0' 启用 / '1' 停用
function statusText(s: any) { return String(s) === '0' ? '启用' : '停用' }
function statusColor(s: any) { return String(s) === '0' ? 'green' : 'red' }

async function save() {
  try { await formRef.value?.validate() } catch { return }
  if (!form.value.parentId) { message.warning('请选择所属部门'); return }
  saving.value = true
  try {
    const payload: any = {
      deptId: isEdit.value ? form.value.deptId : undefined,
      parentId: form.value.parentId,
      deptName: form.value.deptName,
      deptCategory: '小组',                        // 固定传 '小组'，后端据此识别
      leader: form.value.leader || null,   // user_id(Long)；空则传 null 避免 Long 解析 '' 报错
      orderNum: form.value.orderNum ?? 0,
      status: form.value.status || '0',
    }
    const res: any = isEdit.value ? await api.put('/system/dept', payload) : await api.post('/system/dept', payload)
    if (res.code === 200) {
      message.success('保存成功')
      showDlg.value = false
      loadAll()
    } else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

// 删除（二次确认；后端校验小组下是否有人占用，失败透传 msg）
function removeGroup(row: any) {
  Modal.confirm({
    title: '删除小组',
    content: `确认删除小组「${row.deptName}」？若小组下已有人员占用，后端会拒绝删除。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await api.delete(`/system/dept/${row.deptId}`)
        if (res.code === 200) { message.success('已删除'); loadAll() }
        else message.error(res.msg || '删除失败')
      } catch (e: any) { message.error(e?.response?.data?.msg || '删除失败') }
    },
  })
}

// ===== 列设置（显隐 + 顺序 + 列宽）localStorage key: group_list:${userId} =====
const ALL_COLUMNS = [
  { title: '小组名', dataIndex: 'deptName', key: 'deptName' },
  { title: '所属部门', dataIndex: 'parentName', key: 'parentName', width: 140 },
  { title: '所属车间', dataIndex: 'workshopName', key: 'workshopName', width: 140 },
  { title: '负责人', dataIndex: 'leader', key: 'leader', width: 110 },
  { title: '排序', dataIndex: 'orderNum', key: 'orderNum', width: 80 },
  { title: '状态', key: 'status', width: 90 },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
const colStoreKey = computed(() => `group_list:${localStorage.getItem('userId') || 'anon'}`)

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
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}

// 拖拽后短暂抑制 click（否则 mousedown→mouseup 后的 click 会触发 antd 排序，表格重排导致边框跳变）
let suppressHeaderClickUntil = 0

// 表头 mousedown：只在 th 右缘 14px 内才开始拖拽（否则让位给排序点击）
function onHeaderMouseDown(key: string, e: MouseEvent) {
  if (key === 'ops') return
  const th = e.currentTarget as HTMLTableCellElement
  const rect = th.getBoundingClientRect()
  if (e.clientX < rect.right - 14) return
  const startX = e.clientX
  const startW = rect.width
  const onMove = (ev: MouseEvent) => { onColResize(key, startW + (ev.clientX - startX)) }
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
}
// 表头 click：刚拖拽过则吞掉，避免触发排序
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

// 按 colState 顺序+显隐生成实际列（操作列固定最后、不可隐藏）
// 每列加 customHeaderCell → 原生 th 绑 mousedown（右缘拖拽改宽），th 由 antd 布局保证与数据对齐
const columns = computed(() => {
  const ops = { title: '操作', key: 'ops', width: 130, fixed: 'right' as const }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({ ...ALL_COLUMNS.find(a => a.key === c.key)!, width: c.width, customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) }))
    .filter(Boolean)
  return [...list, ops]
})

function showTotalText(t: number) { return `共 ${t} 条` }

watch(showDlg, (v) => { if (!v) formRef.value?.clearValidate() })

onMounted(() => { loadAll(); loadBuildings(); loadPersons(); loadColState() })
</script>

<template>
  <div class="group-page">
    <!-- 标题行：返回 + 标题（无操作按钮，对齐 DeptManage 标准） -->
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>
        <span class="head-title">小组管理</span>
      </div>
    </div>

    <!-- 筛选栏：搜索小组名 + 查询/重置 + 靠右新增 -->
    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索小组名" allow-clear style="width:200px">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <a-button @click="loadAll"><ReloadOutlined />查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button type="primary" @click="openAdd"><PlusOutlined />新增小组</a-button>
    </div>

    <!-- 表格工具行：共N条 + ⚙列设置 -->
    <div class="table-bar">
      <span class="total">共 {{ rows.length }} 个小组</span>
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

    <!-- 小组列表 -->
    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: showTotalText, showQuickJumper: true }"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
      table-layout="fixed"
      row-key="deptId"
      size="middle"
      class="group-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'deptName'">
          <span class="group-name">{{ record.deptName }}</span>
        </template>
        <template v-else-if="column.key === 'parentName'">
          {{ record.parentName || '—' }}
        </template>
        <template v-else-if="column.key === 'workshopName'">
          {{ record.workshopName || '—' }}
        </template>
        <template v-else-if="column.key === 'leader'">
          {{ personLabel(record.leader) || '—' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a class="danger-link" @click="removeGroup(record)">删除</a>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑小组 -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑小组' : '新增小组'"
      width="560px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-form-item label="小组名称" name="deptName">
          <a-input v-model:value="form.deptName" placeholder="请输入小组名称" :maxlength="30" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="所属部门" name="parentId">
              <a-tree-select
                v-model:value="form.parentId"
                :tree-data="deptTreeOptions"
                :field-names="{ label: 'deptName', value: 'deptId', children: 'children' }"
                allow-clear
                tree-default-expand-all
                placeholder="请选择所属部门（任意层级）"
                @change="onDeptChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属车间（厂房，可选，选部门后自动带出）" name="workshopId">
              <a-select
                v-model:value="form.workshopId"
                allow-clear
                placeholder="选了部门后自动带出所在厂房，可改"
              >
                <a-select-option v-for="w in workshopOptions" :key="w.id" :value="w.id">
                  {{ w.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-select v-model:value="form.leader" allow-clear placeholder="选人员（可空）">
                <a-select-option v-for="p in persons" :key="String(p.user_id)" :value="String(p.user_id)">
                  {{ p.nick_name || p.user_name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.orderNum" :min="0" style="width:100%" placeholder="数字越小越靠前" />
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }

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

/* 表格样式：白底圆角、表头底色 #FAFBFD、行悬停 #F5F8FF、列间浅竖线 #F0F2F5、font-size 13px */
.group-table { width: 100%; background: #fff; border-radius: 10px; }
.group-table :deep(.ant-table) { font-size: 13px; }
.group-table :deep(.ant-table-thead > tr > th),
.group-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.group-table :deep(.ant-table-thead > tr > th:last-child),
.group-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.group-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.group-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.group-name { font-weight: 500; color: #1D2129; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
.muted { color: #C9CDD4; }
</style>

<!-- 非 scoped：给 antd 原生表头 th 加右缘拖拽手柄（::after 画线，th 自带 mousedown） -->
<style>
.ant-table-thead > tr > th { position: relative; }
.ant-table-thead > tr > th::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; transition: border-color .12s, background .12s; }
.ant-table-thead > tr > th:hover::after { border-right-color: #C9CDD4; }
.ant-table-thead > tr > th:active::after { background: rgba(30,94,255,.15); border-right-color: #1E5EFF; }
</style>
