<script setup lang="ts">
/**
 * 部门管理（系统管理模块）—— ant-design-vue：
 * - 组织层级：厂 → 车间 → 区域 → 小组（sys_dept.dept_category 区分）
 * - a-table 树形展开表：部门名称/层级/负责人/电话/状态/排序/操作
 * - 操作：编辑 / 新增下级 / 删除（后端校验子部门与人员占用，失败弹 msg）
 * - 顶部工具栏：新增顶层部门、按名称前端过滤、刷新
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

const router = useRouter()

// ===== 列表 =====
const tree = ref<any[]>([])        // 原始部门树（含 children）
const loading = ref(false)
const keyword = ref('')

// 层级标签配色：厂区=蓝 车间=绿 区域=橙 小组=紫
const categoryColor: Record<string, string> = { 厂区: 'blue', 车间: 'green', 区域: 'orange', 小组: 'purple' }

const columns = [
  { title: '部门名称', dataIndex: 'deptName', key: 'deptName' },
  { title: '层级', dataIndex: 'deptCategory', key: 'deptCategory', width: 90 },
  { title: '所在建筑', key: 'buildingId', width: 110 },
  { title: '位置', key: 'location', width: 160 },
  { title: '负责人', dataIndex: 'leader', key: 'leader', width: 110 },
  { title: '电话', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '排序', dataIndex: 'orderNum', key: 'orderNum', width: 80 },
  { title: '操作', key: 'ops', width: 190 },
]

// 前端过滤部门名（保留命中节点及其祖先链，树结构不破坏）
const deptTree = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return tree.value
  const walk = (nodes: any[]): any[] => {
    const out: any[] = []
    for (const n of nodes) {
      const kids = walk(n.children || [])
      if (String(n.deptName || '').includes(kw) || kids.length) out.push({ ...n, children: kids })
    }
    return out
  }
  return walk(tree.value)
})

async function loadTree() {
  loading.value = true
  try {
    const res: any = await api.get('/system/dept/list')
    if (res.code === 200) tree.value = res.data || []
    else message.error(res.msg || '加载部门失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '加载部门失败')
  } finally { loading.value = false }
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
  const res: any = await api.put('/system/dept', {
    deptId: row.deptId, parentId: row.parentId, deptName: row.deptName,
    deptCategory: row.deptCategory, buildingId: row.buildingId ?? null, orderNum: row.orderNum,
    leader: row.leader, phone: row.phone, email: row.email,
    status: checked ? '0' : '1',
  })
  if (res.code === 200) { message.success(checked ? '已启用' : '已停用'); loadTree() }
  else { message.error(res.msg || '操作失败'); loadTree() }
}

// ===== 表单弹窗 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  deptId: null as number | null,
  parentId: undefined as number | undefined,
  deptName: '',
  deptCategory: undefined as string | undefined,
  buildingId: undefined as number | undefined,   // 所在建筑（哪栋楼，可选，所有层级都能填）
  floor: undefined as number | undefined,        // 楼层（几层，可选；buildingId 选了才出现）
  location: '',                                   // 位置路径尾段（车间/房间等，可选，自由填）
  orderNum: 0,
  leader: '',
  phone: '',
  email: '',
  status: '0',
})
const form = ref(emptyForm())

// 上级部门树选择（编辑时排除自己及子孙，防成环）
const parentTree = computed(() => {
  const excludeId = isEdit.value ? form.value.deptId : null
  const walk = (nodes: any[]): any[] => nodes
    .filter(n => Number(n.deptId) !== Number(excludeId))
    .map(n => ({ ...n, children: walk(n.children || []) }))
  return walk(tree.value)
})

const rules: any = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCategory: [{ required: true, message: '请选择层级', trigger: 'change' }],
}

function openAddTop() {
  isEdit.value = false
  form.value = emptyForm()
  form.value.deptCategory = '厂区'
  showDlg.value = true
}

// 新增下级：parentId=当前部门；层级按 厂区→车间→区域→小组 推导
function openAddChild(row: any) {
  isEdit.value = false
  form.value = emptyForm()
  form.value.parentId = Number(row.deptId)
  const next: Record<string, string> = { 厂区: '车间', 车间: '区域', 区域: '小组' }
  form.value.deptCategory = next[row.deptCategory] || '小组'
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await api.get(`/system/dept/${row.deptId}`)
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
    deptId: Number(d.deptId),
    parentId: d.parentId === 0 || d.parentId === '0' ? undefined : Number(d.parentId),
    deptName: d.deptName || '',
    deptCategory: d.deptCategory || undefined,
    buildingId: d.buildingId ? Number(d.buildingId) : undefined,
    floor,
    location: tail,
    orderNum: Number(d.orderNum ?? 0),
    leader: d.leader || '',
    phone: d.phone || '',
    email: d.email || '',
    status: d.status || '0',
  }
  showDlg.value = true
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch { return }
  saving.value = true
  try {
    const payload: any = {
      deptId: form.value.deptId ?? undefined,
      parentId: form.value.parentId ?? 0,
      deptName: form.value.deptName,
      deptCategory: form.value.deptCategory,
      buildingId: form.value.buildingId ?? null,
      location: composeLocation(),
      orderNum: form.value.orderNum ?? 0,
      leader: form.value.leader || '',
      phone: form.value.phone || '',
      email: form.value.email || '',
      status: form.value.status || '0',
    }
    const res: any = isEdit.value ? await api.put('/system/dept', payload) : await api.post('/system/dept', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadTree() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

// 删除：后端会校验有无子部门/人员占用，失败时弹后端 msg
function removeDept(row: any) {
  Modal.confirm({
    title: '删除部门',
    content: `确认删除「${row.deptName}」？若有下级部门或已分配人员，后端将拒绝删除。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.delete(`/system/dept/${row.deptId}`)
      if (res.code === 200) { message.success('已删除'); loadTree() }
      else message.error(res.msg || '删除失败')
    },
  })
}

// 弹窗关闭时重置校验状态
watch(showDlg, (v) => { if (!v) formRef.value?.clearValidate() })

onMounted(() => { loadTree(); loadBuildings() })
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>
        <span class="head-title">部门管理</span>
      </div>
      <div class="head-actions">
        <a-button type="primary" size="middle" @click="openAddTop">
          <PlusOutlined />新增厂区/部门
        </a-button>
      </div>
    </div>

    <!-- 工具栏：搜索（前端过滤）+ 刷新 -->
    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索部门名称" allow-clear style="width:220px">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <a-button :loading="loading" @click="loadTree">
        <ReloadOutlined />刷新
      </a-button>
    </div>

    <!-- 树表 -->
    <a-table
      :columns="columns"
      :data-source="deptTree"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 'max-content' }"
      row-key="deptId"
      size="middle"
      class="dept-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'deptName'">
          <span class="dept-name">{{ record.deptName }}</span>
        </template>
        <template v-else-if="column.key === 'deptCategory'">
          <a-tag :color="categoryColor[record.deptCategory]">{{ record.deptCategory || '-' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'buildingId'">
          {{ buildingLabel(record.buildingId) || '—' }}
        </template>
        <template v-else-if="column.key === 'location'">
          <span v-if="record.location" class="loc-cell">📍 {{ record.location }}</span>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'leader'">
          {{ record.leader || '-' }}
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
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a @click="openAddChild(record)">新增下级</a>
          <a-divider type="vertical" />
          <a class="danger-link" @click="removeDept(record)">删除</a>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑（a-modal + a-form） -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑部门' : '新增部门'"
      width="640px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="部门名称" name="deptName">
              <a-input v-model:value="form.deptName" :maxlength="30" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="层级" name="deptCategory">
              <a-select v-model:value="form.deptCategory" placeholder="请选择层级">
                <a-select-option value="厂区">厂区</a-select-option>
                <a-select-option value="车间">车间</a-select-option>
                <a-select-option value="区域">区域</a-select-option>
                <a-select-option value="小组">小组</a-select-option>
              </a-select>
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
          <a-col :span="12">
            <a-form-item label="上级部门（新建顶层可不选）">
              <a-tree-select
                v-model:value="form.parentId"
                :tree-data="parentTree"
                :field-names="{ label: 'deptName', value: 'deptId', children: 'children' }"
                allow-clear
                tree-default-expand-all
                placeholder="无（作为顶层）"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.orderNum" :min="0" style="width:100%" placeholder="数字越小越靠前" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-input v-model:value="form.leader" :maxlength="20" placeholder="请输入" />
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }

.dept-table { width: 100%; background: #fff; border-radius: 10px; }
.dept-table :deep(.ant-table) { font-size: 13px; }
.dept-table :deep(.ant-table-thead > tr > th),
.dept-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.dept-table :deep(.ant-table-thead > tr > th:last-child),
.dept-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.dept-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.dept-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.dept-name { font-weight: 500; color: #1D2129; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
.loc-preview { margin-top: 4px; font-size: 12px; color: #1E5EFF; background: #F4F7FF; border-radius: 4px; padding: 2px 8px; }
.loc-cell { color: #1E5EFF; font-size: 12px; }
.muted { color: #C9CDD4; }
</style>
