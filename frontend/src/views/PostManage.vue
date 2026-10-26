<script setup lang="ts">
/**
 * 岗位管理（人员模块）—— 对齐列表页标准 B.5.1.1（基准 Person.vue）：
 * - 标题行：← 返回（PersonIndex）+ 标题，无操作按钮
 * - 筛选栏：搜索框（回车查询）+ 查询(primary)/重置 + filter-spacer + 靠右【删除选中】【新增岗位】
 * - 表格工具行：共 N 条 + ⚙列设置（colState 显隐+顺序+列宽，localStorage 按用户）
 * - a-table：table-layout fixed + 列宽拖拽（原生 th + customHeaderCell）+ scroll y
 * - a-form + a-modal：新增/编辑（编码、名称、排序、状态、备注）
 * - 后端：RuoYi 自带 /system/post 接口（list/{id}/POST/PUT/DELETE/{postIds}）
 */
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { pinyin } from 'pinyin-pro'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

const router = useRouter()

// ===== 列表 =====
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')   // 按岗位名称/编码搜索（前端过滤）

// 选中行（批量删除）
const selectedRowKeys = ref<number[]>([])

// 岗位归属部门（RuoYi 要求 deptId）：岗位为全厂通用，默认挂到顶层"厂区"节点
const rootDeptId = ref<number | undefined>()
async function loadRootDept() {
  try {
    const res: any = await api.get('/system/dept/list')
    if (res.code === 200) {
      const top = (res.data || []).find((n: any) => n.deptCategory === '厂区') || (res.data || [])[0]
      if (top) rootDeptId.value = Number(top.deptId)
    }
  } catch { /* ignore */ }
}

// 搜索过滤：当前页数据按名称/编码模糊匹配
const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter(r =>
    (r.postName || '').toLowerCase().includes(kw) || (r.postCode || '').toLowerCase().includes(kw),
  )
})

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/post/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value },
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
const ALL_COLUMNS = [
  { title: '岗位名称', dataIndex: 'postName', key: 'postName' },
  { title: '岗位编码', dataIndex: 'postCode', key: 'postCode' },
  { title: '排序', dataIndex: 'postSort', key: 'postSort' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
// v1：2026-10-26 列顺序调整（岗位名称提首位），升版让新默认顺序对已存 colState 的用户立即生效
const colStoreKey = computed(() => `post_list_v1:${localStorage.getItem('userId') || 'anon'}`)
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

// 按 colState 顺序+显隐生成列（操作列固定最后；customHeaderCell 绑拖拽）
const columns = computed(() => {
  const ops = { title: '操作', key: 'ops', width: 120, fixed: 'right' as const }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({ ...ALL_COLUMNS.find(a => a.key === c.key)!, width: c.width, customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) }))
    .filter(Boolean)
  return [...list, ops]
})

// ===== 列宽拖拽（原生表头 + customHeaderCell） =====
let suppressHeaderClickUntil = 0
function onColResize(key: string, w: number) {
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}
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
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

// ===== 新增/编辑 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  postId: null as number | null,
  postCode: '', postName: '',
  postSort: 0,
  status: '0',   // 0 启用 / 1 停用（RuoYi 约定）
  remark: '',
})
const form = ref(emptyForm())

// a-form 校验规则
const rules: any = {
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
}

// ===== 岗位编码自动生成（可手改）：中文取拼音首字母，非中文原样，全大写 =====
const postCodeTouched = ref(false)   // 用户手动改过编码后不再自动覆盖
function suggestCode(name: string): string {
  if (!name.trim()) return ''
  const parts = pinyin(name.trim(), {
    pattern: 'first', type: 'array', nonZh: 'consecutive',
  })
  return parts.join('').replace(/\s+/g, '').toUpperCase().slice(0, 30)
}
// 新增时：岗位名一变就刷新建议编码（除非用户手动改过）；
// 生成后主动清掉该字段的陈旧校验红标（曾 blur 空编码触发的"请输入岗位编码"不会因 model 变化自动消失）
watch(() => form.value.postName, (v) => {
  if (!isEdit.value && !postCodeTouched.value) {
    form.value.postCode = suggestCode(v)
    nextTick(() => formRef.value?.clearValidate(['postCode']))
  }
})

// 状态开关：checked(true)=启用(status='0')
function onStatusChange(v: any) {
  form.value.status = v ? '0' : '1'
}

function openAdd() {
  isEdit.value = false
  postCodeTouched.value = false   // 新增：允许按岗位名自动建议编码
  form.value = emptyForm()
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  postCodeTouched.value = true    // 编辑：保留原编码，不自动覆盖
  // 详情接口返回驼峰字段；若返回 null 则退化用行数据
  let d: any = null
  try {
    const res: any = await api.get(`/system/post/${row.postId}`)
    if (res.code === 200) d = res.data
  } catch { d = null }
  if (!d) d = row
  form.value = {
    postId: d.postId,
    postCode: d.postCode || '', postName: d.postName || '',
    postSort: d.postSort ?? 0,
    status: d.status || '0',
    remark: d.remark || '',
  }
  showDlg.value = true
}

async function save() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    const payload: any = {
      postCode: form.value.postCode, postName: form.value.postName,
      postSort: form.value.postSort ?? 0, status: form.value.status, remark: form.value.remark,
      deptId: rootDeptId.value ?? undefined,   // 岗位归属部门（全厂通用→厂区根节点）
    }
    if (isEdit.value) payload.postId = form.value.postId
    const res: any = isEdit.value
      ? await api.put('/system/post', payload)
      : await api.post('/system/post', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

// ===== 删除（单条 / 批量，二次确认）=====
async function doDelete(ids: number[]) {
  if (!ids.length) return
  const idStr = ids.join(',')
  const res: any = await api.delete(`/system/post/${idStr}`)
  if (res.code === 200) {
    message.success('已删除')
    selectedRowKeys.value = selectedRowKeys.value.filter(k => !ids.includes(k))
    // 当前页删光了就回退一页
    if (filteredRows.value.length === ids.length && pageNum.value > 1) pageNum.value--
    loadList()
  } else {
    message.error(res.msg || '删除失败')
    throw new Error(res.msg || '删除失败')   // 让 Modal.confirm 停留在打开态，不静默关闭
  }
}

function removeOne(row: any) {
  Modal.confirm({
    title: '删除岗位',
    content: `确认删除岗位「${row.postName}（${row.postCode}）」？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try { await doDelete([row.postId]) } catch (e: any) { message.error(e?.response?.data?.msg || '删除失败') }
    },
  })
}

function removeBatch() {
  const n = selectedRowKeys.value.length
  Modal.confirm({
    title: '批量删除岗位',
    content: `确认删除选中的 ${n} 个岗位？删除后不可恢复。`,
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

onMounted(() => { loadList(); loadRootDept(); loadColState() })
</script>

<template>
  <div>
    <!-- 标题行：仅返回 + 标题 -->
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>
        <span class="head-title">岗位管理</span>
      </div>
    </div>

    <!-- 筛选栏：搜索 + 查询/重置 + 靠右操作 -->
    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索岗位名称/编码" allow-clear style="width:200px" @press-enter="onSearch">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <a-button type="primary" @click="onSearch">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button :disabled="!selectedRowKeys.length" danger @click="removeBatch">
        删除选中{{ selectedRowKeys.length ? `（${selectedRowKeys.length}）` : '' }}
      </a-button>
      <a-button type="primary" @click="openAdd"><PlusOutlined />新增岗位</a-button>
    </div>

    <!-- 表格工具行：条数 + 列设置 -->
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
              <a-button type="primary" size="small" @click="colSetOpen = false">完成</a-button>
            </div>
          </a-card>
        </template>
      </a-dropdown>
    </div>

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
      row-key="postId"
      size="middle"
      class="post-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === '0' ? 'green' : 'red'">{{ record.status === '0' ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a class="danger-link" @click="removeOne(record)">删除</a>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑 -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑岗位' : '新增岗位'"
      width="520px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="岗位名称" name="postName">
          <a-input v-model:value="form.postName" placeholder="如 缝纫工 / 质检" :maxlength="30" />
        </a-form-item>
        <a-form-item label="岗位编码" name="postCode">
          <a-input v-model:value="form.postCode" placeholder="按岗位名自动生成，可修改" :maxlength="30" @change="postCodeTouched = true" />
        </a-form-item>
        <a-form-item label="排序" name="postSort">
          <a-input-number v-model:value="form.postSort" :min="0" style="width: 100%" placeholder="数字越小越靠前" />
        </a-form-item>
        <a-form-item label="状态">
          <!-- a-switch checked = 启用（status='0'），即 checked 对应 0 -->
          <a-switch
            :checked="form.status === '0'"
            checked-children="启用" un-checked-children="停用"
            @change="onStatusChange"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="3" :maxlength="200" placeholder="选填" />
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

.post-table { width: 100%; background: #fff; border-radius: 10px; }
.post-table :deep(.ant-table) { font-size: 13px; }
.post-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.post-table :deep(.ant-table-thead > tr > th),
.post-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.post-table :deep(.ant-table-thead > tr > th:last-child),
.post-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.post-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
</style>

<!-- 非 scoped：列宽拖拽手柄（原生表头 th::after 画右缘线） -->
<style>
.ant-table-thead > tr > th { position: relative; }
.ant-table-thead > tr > th::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; transition: border-color .12s, background .12s; }
.ant-table-thead > tr > th:hover::after { border-right-color: #C9CDD4; }
.ant-table-thead > tr > th:active::after { background: rgba(30,94,255,.15); border-right-color: #1E5EFF; }
</style>
