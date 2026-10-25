<script setup lang="ts">
/**
 * 岗位管理（系统模块）—— ant-design-vue：
 * - a-table：分页/多选批量删除/操作列
 * - a-form + a-modal：新增/编辑（编码、名称、排序、状态、备注）
 * - a-switch：状态 0 启用 / 1 停用
 * - 后端：RuoYi 自带 /system/post 接口（list/{id}/POST/PUT/DELETE/{postIds}）
 */
import { ref, onMounted, computed } from 'vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

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

// ===== 表格列 =====
const columns = [
  { title: '岗位编码', dataIndex: 'postCode', key: 'postCode', width: 120 },
  { title: '岗位名称', dataIndex: 'postName', key: 'postName', width: 140 },
  { title: '排序', dataIndex: 'postSort', key: 'postSort', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'ops', width: 120, fixed: 'right' as const },
]

// 搜索过滤：当前页数据按名称/编码模糊匹配（页数少时简单直接；需要服务端过滤可换 params）
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

function refresh() {
  selectedRowKeys.value = []
  loadList()
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

// 状态开关：checked(true)=启用(status='0')
function onStatusChange(v: any) {
  form.value.status = v ? '0' : '1'
}

function openAdd() {
  isEdit.value = false
  form.value = emptyForm()
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
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

onMounted(() => { loadList(); loadRootDept() })
</script>

<template>
  <div>
    <div class="page-header">
      <span class="head-title">岗位管理</span>
      <div class="head-actions">
        <a-button :disabled="!selectedRowKeys.length" danger @click="removeBatch">
          删除选中{{ selectedRowKeys.length ? `（${selectedRowKeys.length}）` : '' }}
        </a-button>
        <a-input v-model:value="keyword" placeholder="搜索岗位名称/编码" allow-clear style="width:180px" @press-enter="refresh" />
        <a-button @click="refresh"><reload-outlined /> 刷新</a-button>
        <a-button type="primary" @click="openAdd"><plus-outlined /> 新增岗位</a-button>
      </div>
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
      :scroll="{ x: 'max-content' }"
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
        <a-form-item label="岗位编码" name="postCode">
          <a-input v-model:value="form.postCode" placeholder="如 SEW / QC / CUT" :maxlength="30" />
        </a-form-item>
        <a-form-item label="岗位名称" name="postName">
          <a-input v-model:value="form.postName" placeholder="如 缝纫工 / 质检" :maxlength="30" />
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
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

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
