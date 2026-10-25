<script setup lang="ts">
/**
 * 小组管理（组织/人员模块）—— ant-design-vue：
 * - 小组 = 部门树末级节点（dept_category='小组'），挂靠在 车间 或 车间>区域 下
 * - GET /system/dept/list 拿整棵部门树，前端递归过滤出所有"小组"节点
 * - 通过树遍历（优先）、ancestors 兜底，反查每个小组的 所属车间 / 所属区域（展示用）
 * - a-table 小组列表：小组名称/所属车间/所属区域/排序/状态/操作(编辑、删除)
 * - a-modal + a-form 新增/编辑：parentId 优先挂区域，未选区域挂车间；ancestors 由后端维护
 * - 删除 Modal.confirm 二次确认；后端校验小组下是否有人占用，失败透传 msg
 */
import { ref, computed, onMounted } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import api from '../api'

// ===== 部门树 & 小组列表 =====
const tree = ref<any[]>([])        // 原始部门树（/system/dept/list 返回，含 children）
const loading = ref(false)
const rows = ref<any[]>([])        // 过滤后的小组行数据
const filterWorkshop = ref<number | undefined>()   // 工具栏：按车间筛选（可选）

const flatten = (list: any[]): any[] => {
  const out: any[] = []
  const walk = (arr: any[]) => arr.forEach(n => { out.push(n); if (n.children?.length) walk(n.children) })
  walk(list)
  return out
}
const allNodes = computed(() => flatten(tree.value))
const byId = computed(() => {
  const m: Record<string, any> = {}
  allNodes.value.forEach(n => { m[String(n.deptId)] = n })
  return m
})

// 车间选项（工具栏筛选 + 表单下拉都用它）
const workshops = computed(() => tree.value.filter(n => n.dept_category === '车间'))

// 递归收集所有 dept_category === '小组' 的节点
function collectGroups(list: any[], out: any[]) {
  list.forEach(n => {
    if (n.dept_category === '小组') out.push(n)
    if (n.children?.length) collectGroups(n.children, out)
  })
}

// 由祖先 id 串反查 所属车间 / 所属区域（优先树遍历，ancestors 兜底）
function resolveAncestors(group: any): { workshop?: any; area?: any } {
  const chain: any[] = []
  const dfs = (list: any[]): boolean => {
    for (const n of list) {
      chain.push(n)
      if (Number(n.deptId) === Number(group.deptId)) return true
      if (n.children?.length && dfs(n.children)) return true
      chain.pop()
    }
    return false
  }
  dfs(tree.value)
  // chain = [厂区?, 车间?, 区域?, 小组]
  let workshop = chain.filter(n => n.dept_category === '车间').pop()
  let area = chain.filter(n => n.dept_category === '区域').pop()
  if (!workshop || !area) {
    const ids = String(group.ancestors || '').split(',').filter(Boolean)
    if (!workshop) {
      const w = ids.map(id => byId.value[id]).find((n: any) => n && n.dept_category === '车间')
      if (w) workshop = w
    }
    if (!area) {
      const a = ids.map(id => byId.value[id]).find((n: any) => n && n.dept_category === '区域')
      if (a) area = a
    }
  }
  return { workshop, area }
}

// 生成表格行：小组 + 展示用 所属车间/所属区域
function buildRows() {
  const list: any[] = []
  collectGroups(tree.value, list)
  rows.value = list.map(g => {
    const { workshop, area } = resolveAncestors(g)
    return {
      ...g,
      workshopName: workshop?.deptName || '',
      workshopId: workshop ? Number(workshop.deptId) : undefined,
      areaName: area?.deptName || '',
      areaId: area ? Number(area.deptId) : undefined,
    }
  })
}

// 按车间筛选：小组所在链路上有该车间即可
function rowMatchFilter(r: any): boolean {
  if (!filterWorkshop.value) return true
  if (r.workshopId === Number(filterWorkshop.value)) return true
  return String(r.ancestors || '').split(',').filter(Boolean).includes(String(filterWorkshop.value))
}

const filteredRows = computed(() => rows.value.filter(rowMatchFilter))

async function loadAll() {
  loading.value = true
  try {
    const res: any = await api.get('/system/dept/list')
    if (res.code === 200) {
      tree.value = res.data || []
      buildRows()
    } else {
      tree.value = []
      rows.value = []
      message.error(res.msg || '加载部门树失败')
    }
  } catch (e: any) {
    tree.value = []
    rows.value = []
    message.error(e?.response?.data?.msg || '加载部门树失败')
  } finally { loading.value = false }
}

function onFilterChange() { /* v-model 绑定，表格走 computed */ }

// ===== 新增/编辑弹窗 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()
const editingId = ref<number | null>(null)

const emptyForm = () => ({
  deptName: '',
  workshopId: undefined as number | undefined,
  areaId: undefined as number | undefined,
  orderNum: 0,
  status: true,   // true=正常('0'), false=停用('1')
})
const form = ref(emptyForm())

// 所选车间下的区域（区域可选；车间下无区域时可直接挂车间）
const areaOptions = computed(() => {
  const wid = form.value.workshopId
  if (!wid) return []
  const w = byId.value[String(wid)]
  return (w?.children || []).filter((n: any) => n.dept_category === '区域')
})

function onWorkshopChange() {
  // 换车间后清空已选区域，避免挂到错误车间下
  form.value.areaId = undefined
}

const rules: any = {
  deptName: [{ required: true, message: '请输入小组名称', trigger: 'blur' }],
  workshopId: [{ required: true, message: '请选择所属车间', trigger: 'change' }],
}

function openAdd() {
  isEdit.value = false
  editingId.value = null
  form.value = emptyForm()
  showDlg.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  editingId.value = Number(row.deptId)
  form.value = {
    deptName: row.deptName || '',
    workshopId: row.workshopId,
    areaId: row.areaId,
    orderNum: row.orderNum ?? 0,
    status: String(row.status) === '0',
  }
  showDlg.value = true
}

// 状态列展示：'0' 正常 / '1' 停用
function statusText(s: any) { return String(s) === '0' ? '正常' : '停用' }
function statusColor(s: any) { return String(s) === '0' ? 'green' : 'red' }

async function save() {
  try { await formRef.value?.validate() } catch { return }
  if (!form.value.workshopId) { message.warning('请选择所属车间'); return }
  saving.value = true
  try {
    // parentId 优先挂所选区域的 deptId，未选区域则挂所选车间的 deptId（后端 parentId 只接受一个）
    const parentId = form.value.areaId != null ? form.value.areaId : form.value.workshopId
    const payload: any = {
      parentId,
      deptName: form.value.deptName,
      deptCategory: '小组',
      orderNum: form.value.orderNum ?? 0,
      status: form.value.status ? '0' : '1',
    }
    let res: any
    if (isEdit.value) {
      payload.deptId = editingId.value
      res = await api.put('/system/dept', payload)
    } else {
      res = await api.post('/system/dept', payload)
    }
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

const tableColumns = [
  { title: '小组名称', dataIndex: 'deptName', key: 'deptName' },
  { title: '所属车间', dataIndex: 'workshopName', key: 'workshopName' },
  { title: '所属区域', dataIndex: 'areaName', key: 'areaName' },
  { title: '排序', dataIndex: 'orderNum', key: 'orderNum', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'ops', width: 130 },
]

function showTotalText(t: number) { return `共 ${t} 条` }

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <span class="head-title">小组管理</span>
        <span class="head-desc">小组挂在车间/区域下，是部门树的末级节点（dept_category='小组'）</span>
      </div>
      <div class="head-actions">
        <a-button type="primary" @click="openAdd"><plus-outlined /> 新增小组</a-button>
      </div>
    </div>

    <!-- 工具栏：车间筛选 + 刷新 + 条数 -->
    <div class="filter-bar">
      <a-select
        v-model:value="filterWorkshop"
        placeholder="按车间筛选"
        allow-clear
        style="width: 180px"
        @change="onFilterChange"
      >
        <a-select-option v-for="w in workshops" :key="Number(w.deptId)" :value="Number(w.deptId)">
          {{ w.deptName }}
        </a-select-option>
      </a-select>
      <a-button @click="loadAll"><reload-outlined /> 刷新</a-button>
      <span class="total">共 {{ filteredRows.length }} 个小组</span>
    </div>

    <!-- 小组列表 -->
    <a-table
      :columns="tableColumns"
      :data-source="filteredRows"
      :loading="loading"
      :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: showTotalText, showQuickJumper: true }"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 300px)' }"
      row-key="deptId"
      size="middle"
      class="group-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'areaName'">
          {{ record.areaName || '-' }}
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
            <a-form-item label="所属车间" name="workshopId">
              <a-select v-model:value="form.workshopId" placeholder="请选择车间" @change="onWorkshopChange">
                <a-select-option v-for="w in workshops" :key="Number(w.deptId)" :value="Number(w.deptId)">
                  {{ w.deptName }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属区域（可选）" name="areaId">
              <a-select
                v-model:value="form.areaId"
                placeholder="车间下无区域可直接挂车间"
                allow-clear
                :disabled="!form.workshopId || areaOptions.length === 0"
              >
                <a-select-option v-for="a in areaOptions" :key="Number(a.deptId)" :value="Number(a.deptId)">
                  {{ a.deptName }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.orderNum" :min="0" style="width: 100%" placeholder="数字越小越靠前" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-switch v-model:checked="form.status" checked-children="正常" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-desc { font-size: 12px; color: #86909C; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-bar .total { margin-left: auto; font-size: 13px; color: #4E5969; }

.group-table { width: 100%; background: #fff; border-radius: 10px; }
.group-table :deep(.ant-table) { font-size: 13px; }
.group-table :deep(.ant-table-thead > tr > th),
.group-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.group-table :deep(.ant-table-thead > tr > th:last-child),
.group-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.group-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.group-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
</style>
