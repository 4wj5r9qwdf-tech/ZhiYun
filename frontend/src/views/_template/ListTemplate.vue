<script setup lang="ts">
/**
 * 列表页标准模板（B.5.1.1 A~E 全量骨架）—— 新建列表页直接复制本文件改字段。
 *
 * 使用步骤：
 * 1. 复制本文件到 `views/XxxManage.vue`（注意：移出 _template 后 import 路径要少一层，`../../api` → `../api`），全局搜索 `TODO` 逐处替换（约 8 处）：
 *    - 页面标题 / 返回目标路由 / 表格 class 名 / localStorage key 前缀
 *    - 接口路径（loadList / save / remove 的 api.xxx）
 *    - ALL_COLUMNS 列定义 + bodyCell 模板 + 表单字段
 * 2. 删掉不需要的占位（如无批量诉求 → 删 rowSelection/removeBatch；无弹窗 → 删表单段）
 * 3. 逐项对照 前端规范.md B.5.1.1 的 E 检查清单打勾
 *
 * 禁改的"标准件"（改了就跑偏）：
 * - 查询按钮 type="primary" 无图标；重置默认无图标；无独立刷新按钮
 * - 列宽拖拽：原生 th + customHeaderCell + right-14 判定 + suppressHeaderClickUntil 吞排序 click
 * - 非 scoped 的 th::after 手柄样式（scoped 匹配不到 h() 渲染元素）
 * - scroll y = calc(100vh - 360px)；table-layout fixed；行样式 .xx-table 四件套
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../../api'

const router = useRouter()

// ===== 列表 =====
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')          // TODO 关键词（回车/查询触发）
const selectedRowKeys = ref<number[]>([])   // 批量选中（无批量诉求可整段删）

async function loadList() {      // TODO 接口路径
  loading.value = true
  try {
    const res: any = await api.get('/system/xxx/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value },
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
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
function onSearch() { selectedRowKeys.value = []; loadList() }
function resetFilters() { keyword.value = ''; selectedRowKeys.value = []; loadList() }

// ===== 列设置（colState：显隐+顺序+宽度，localStorage 按用户） =====
const ALL_COLUMNS = [            // TODO 列定义（dataIndex 对应行数据字段）
  { title: '字段一', dataIndex: 'field1', key: 'field1' },
  { title: '字段二', dataIndex: 'field2', key: 'field2' },
  { title: '字段三', dataIndex: 'field3', key: 'field3' },
  { title: '状态', dataIndex: 'status', key: 'status' },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
const colStoreKey = computed(() => `xxx_list:${localStorage.getItem('userId') || 'anon'}`)  // TODO key 前缀
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 160)  // 操作列宽可拖拽并持久化
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
// 自适应列宽：清空各列固定宽，交给表格按内容分配（再拖拽即重新固定）
function autoFitCols() { for (const c of colState.value) { (c as any).width = undefined } saveColState() }
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

// ===== 列宽拖拽（原生表头 + customHeaderCell，标准件勿改） =====
let suppressHeaderClickUntil = 0
function onColResize(key: string, w: number) {
  if (key === 'ops') { opsWidth.value = Math.max(150, Math.round(w)); try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ } return }
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

// ===== 新增/编辑弹窗（TODO 字段与 rules 按业务改） =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  id: null as number | null,
  field1: '',
  status: '0',                  // '0' 启用 / '1' 停用（RuoYi 约定）
})
const form = ref(emptyForm())

const rules: any = {
  field1: [{ required: true, message: '请输入字段一', trigger: 'blur' }],
}

function openAdd() {
  isEdit.value = false
  form.value = emptyForm()
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  form.value = { ...emptyForm(), ...row, id: row.id }   // TODO 按详情接口/行数据填表单
  showDlg.value = true
}

async function save() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    const payload: any = { ...form.value }               // TODO payload 组装
    delete (payload as any).id
    const res: any = isEdit.value
      ? await api.put('/system/xxx', { ...payload, id: form.value.id })   // TODO 接口
      : await api.post('/system/xxx', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

// ===== 删除（单条/批量，二次确认；失败透传 msg 让确认框停留） =====
async function doDelete(ids: number[]) {
  if (!ids.length) return
  const res: any = await api.delete(`/system/xxx/${ids.join(',')}`)   // TODO 接口
  if (res.code === 200) {
    message.success('已删除')
    selectedRowKeys.value = selectedRowKeys.value.filter(k => !ids.includes(k))
    if (filteredRows.value.length === ids.length && pageNum.value > 1) pageNum.value--
    loadList()
  } else {
    message.error(res.msg || '删除失败')
    throw new Error(res.msg || '删除失败')
  }
}

function removeOne(row: any) {
  Modal.confirm({
    title: '删除',
    content: `确认删除「${row.field1}」？`,               // TODO 展示字段
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try { await doDelete([row.id]) } catch (e: any) { message.error(e?.response?.data?.msg || '删除失败') }
    },
  })
}

function removeBatch() {          // 无批量诉求可整段删（含筛选栏按钮与 rowSelection）
  const n = selectedRowKeys.value.length
  Modal.confirm({
    title: '批量删除',
    content: `确认删除选中的 ${n} 条？删除后不可恢复。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try { await doDelete([...selectedRowKeys.value]) } catch (e: any) { message.error(e?.response?.data?.msg || '删除失败') }
    },
  })
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: any[]) => { selectedRowKeys.value = keys },
}))

// 前端过滤示例（若后端已支持 keyword 过滤，可直接用 rows）
const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter(r => String(r.field1 || '').toLowerCase().includes(kw))
})

onMounted(() => { loadList(); loadColState() })
</script>

<template>
  <div>
    <!-- A. 标题行：仅 返回 + 标题，无操作按钮 -->
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>  <!-- TODO 返回目标路由 -->
        <span class="head-title">页面标题</span>                                                          <!-- TODO 标题 -->
      </div>
    </div>

    <!-- A. 筛选栏：左筛选 + 查询/重置，右操作 -->
    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索关键词" allow-clear style="width:200px" @press-enter="onSearch">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <!-- TODO 更多筛选下拉放这里（状态/部门等） -->
      <a-button type="primary" @click="onSearch">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <!-- 批量按钮（无批量诉求整段删） -->
      <a-button :disabled="!selectedRowKeys.length" danger @click="removeBatch">
        删除选中{{ selectedRowKeys.length ? `（${selectedRowKeys.length}）` : '' }}
      </a-button>
      <a-button type="primary" @click="openAdd"><PlusOutlined />新增</a-button>   <!-- TODO 按钮文案 -->
    </div>

    <!-- A. 表格工具行：条数 + 列设置 -->
    <div class="table-bar">
      <span class="total">共 {{ filteredRows.length }} 条</span>
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

    <!-- B. a-table：四件套 + 拖拽 + 批量 -->
    <a-table
      :columns="columns"
      :data-source="filteredRows"
      :loading="loading"
      :pagination="{
        total,
        current: pageNum,
        pageSize,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: showTotalText,
        onChange: onPageChange,
      }"
      table-layout="fixed"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
      :row-selection="rowSelection"
      row-key="id"
      size="middle"
      class="xxx-table"   <!-- TODO 表格 class（同步改下方样式四处） -->
    >
      <template #bodyCell="{ column, record, index }">
        <!-- 序号列（标准件：固定最左、页内从 1 连续编号） -->
        <template v-if="column.key === 'index'">
          {{ (pageNum - 1) * pageSize + index + 1 }}
        </template>
        <!-- TODO 列渲染：状态 tag / 操作列等 -->
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === '0' ? 'green' : 'red'">{{ record.status === '0' ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <div class="ops-btns">
            <a-button type="primary" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button danger size="small" @click="removeOne(record)">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      :mask-closable="false"
      :keyboard="false"
      v-model:open="showDlg"
      :title="isEdit ? '编辑' : '新增'"
      width="520px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="字段一" name="field1">
          <a-input v-model:value="form.field1" placeholder="请输入" :maxlength="50" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch
            :checked="form.status === '0'"
            checked-children="启用" un-checked-children="停用"
            @change="(v) => (form.status = v ? '0' : '1')"
          />
        </a-form-item>
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

.table-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.table-bar .total { font-size: 13px; color: #4E5969; }
.col-set-panel { width: 200px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }

.xxx-table { width: 100%; background: #fff; border-radius: 10px; }   /* TODO 类名同步 */
.xxx-table :deep(.ant-table) { font-size: 13px; }
.xxx-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.xxx-table :deep(.ant-table-thead > tr > th),
.xxx-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.xxx-table :deep(.ant-table-thead > tr > th:last-child),
.xxx-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.xxx-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

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
