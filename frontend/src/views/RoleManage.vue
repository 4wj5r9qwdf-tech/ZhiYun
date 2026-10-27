<script setup lang="ts">
/**
 * 角色管理（系统模块）—— 对齐列表页标准 B.5.1.1/B.5.1.2：
 * - 标题行：← 返回（SystemIndex）+ 标题
 * - 筛选栏：搜索角色名（后端模糊）+ 查询(primary)/重置 + 靠右【新增角色】
 * - 表格：序号列 + colState 列设置 + 列宽拖拽 + 图标操作列（编辑/分配权限/删除）
 * - 分配权限弹窗：菜单树打钩（含按钮级权限码 list/query/add/edit/remove，有审核功能时补 xxx:audit 按钮自动出现）
 * - 后端：复用 RuoYi /system/role 全套（list/add/edit/permission/delete）+ /system/menu/treeselect
 * - roleId 为雪花 bigint → JSON 字符串，全程 String 处理禁止 Number
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined, EditOutlined, DeleteOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
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
const keyword = ref('')

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/role/list', {
      params: { pageNum: pageNum.value, pageSize: pageSize.value, roleName: keyword.value.trim() },
    })
    if (res.code === 200) { rows.value = res.data.rows || []; total.value = res.data.total || 0 }
    else message.error(res.msg || '加载失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '加载失败') }
  finally { loading.value = false }
}

function onPageChange(p: number, ps: number) { pageNum.value = p; pageSize.value = ps; loadList() }
function showTotalText(t: number) { return `共 ${t} 条` }
function onSearch() { pageNum.value = 1; loadList() }
function resetFilters() { keyword.value = ''; pageNum.value = 1; loadList() }

// 数据范围映射（RuoYi sys_role.data_scope）
const DATA_SCOPE: Record<string, string> = {
  '1': '全部数据', '2': '自定义节点', '3': '本部门及以下', '5': '仅本人', '6': '本小组及下级',
}
function scopeLabel(v: any) { return DATA_SCOPE[String(v)] || String(v ?? '') }
function statusText(s: any) { return String(s) === '0' ? '启用' : '停用' }
function statusColor(s: any) { return String(s) === '0' ? 'green' : 'red' }

// ===== 列设置（colState） =====
const ALL_COLUMNS = [
  { title: '角色名称', dataIndex: 'roleName', key: 'roleName' },
  { title: '权限字符', dataIndex: 'roleKey', key: 'roleKey' },
  { title: '数据范围', key: 'dataScope' },
  { title: '状态', key: 'status' },
  { title: '排序', dataIndex: 'roleSort', key: 'roleSort' },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
const colStoreKey = computed(() => `role_list_v1:${localStorage.getItem('userId') || 'anon'}`)
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

const columns = computed(() => {
  const idx = { title: '序号', key: 'index', width: 64, align: 'center' as const, fixed: 'left' as const,
    customHeaderCell: () => ({ class: 'zy-th-noresize' }) }
  const ops = { title: '操作', key: 'ops', width: 260, fixed: 'right' as const }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({ ...ALL_COLUMNS.find(a => a.key === c.key)!, width: c.width, customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) }))
    .filter(Boolean)
  return [idx, ...list, ops]
})

// ===== 列宽拖拽 =====
let suppressHeaderClickUntil = 0
function onColResize(key: string, w: number) {
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}
function onHeaderMouseDown(key: string, e: MouseEvent) {
  if (key === 'ops' || key === 'index') return
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
  orgIds: [] as string[],   // 数据范围=自定义部门时勾选
  roleId: '' as string,
  roleName: '',
  roleKey: '',
  dataScope: '1',
  roleSort: 0,
  status: '0',
  remark: '',
})
const form = ref(emptyForm())

const rules: any = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入权限字符', trigger: 'blur' }],
}

function openAdd() {
  isEdit.value = false
  form.value = emptyForm()
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  let d: any = null
  try {
    const res: any = await api.get(`/system/role/${row.roleId}`)
    if (res.code === 200) d = res.data
  } catch { d = null }
  if (!d) d = row
  form.value = {
    orgIds: [],
    roleId: String(d.roleId),
    roleName: d.roleName || '', roleKey: d.roleKey || '',
    dataScope: String(d.dataScope || '1'), roleSort: Number(d.roleSort ?? 0),
    status: String(d.status || '0'), remark: d.remark || '',
  }
  showDlg.value = true
  if (form.value.dataScope === '2') loadRoleOrgs(form.value.roleId)
}

async function save() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    const payload: any = {
      roleId: isEdit.value ? form.value.roleId : undefined,
      roleName: form.value.roleName, roleKey: form.value.roleKey,
      dataScope: form.value.dataScope, roleSort: form.value.roleSort ?? 0,
      orgIds: form.value.dataScope === '2' ? form.value.orgIds : undefined,
      status: form.value.status, remark: form.value.remark,
    }
    const res: any = isEdit.value ? await api.put('/system/role', payload) : await api.post('/system/role', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

function removeOne(row: any) {
  Modal.confirm({
    title: '删除角色',
    content: `确认删除角色「${row.roleName}」？角色下已分配人员时后端会拒绝。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await api.delete(`/system/role/${row.roleId}`)
        if (res.code === 200) { message.success('已删除'); loadList() }
        else message.error(res.msg || '删除失败')
      } catch (e: any) { message.error(e?.response?.data?.msg || '删除失败') }
    },
  })
}

// ===== 分配权限（菜单树打钩，含按钮级） =====
const permDlg = ref({ open: false, roleId: '', roleName: '' })
const permTree = ref<any[]>([])          // 菜单树（含 C 页面 + F 按钮）
const permChecked = ref<string[]>([])    // 已勾选（String id）
const permHalf = ref<string[]>([])
const permSaving = ref(false)

async function openPerm(row: any) {
  permDlg.value = { open: true, roleId: String(row.roleId), roleName: row.roleName }
  permTree.value = []
  permChecked.value = []
  permHalf.value = []
  try {
    const res: any = await api.get(`/system/menu/roleMenuTreeselect/${row.roleId}`)
    if (res.code === 200) {
      permTree.value = res.data.menus || []
      permChecked.value = (res.data.checkedKeys || []).map(String)
      permHalf.value = (res.data.halfCheckedKeys || []).map(String)
    } else message.error(res.msg || '加载菜单失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '加载菜单失败') }
}

async function savePerm() {
  permSaving.value = true
  try {
    // 勾了按钮必须隐含所属页面（半选父节点一起提交），否则"给了查询却进不了页面/列表403"
    const ids = new Set<string>([...permChecked.value, ...permHalf.value])
    const res: any = await api.put('/system/role/permission', {
      roleId: permDlg.value.roleId,
      menuIds: [...ids],
    })
    if (res.code === 200) { message.success('权限已分配'); permDlg.value.open = false }
    else message.error(res.msg || '分配失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '分配失败') }
  finally { permSaving.value = false }
}


// ===== 自定义部门（数据范围=2）勾选弹窗 =====
const orgDlg = ref({ open: false, saving: false })
const orgTreeData = ref<any[]>([])      // 全部部门树（排除小组）
const orgChecked = ref<string[]>([])

function buildOrgTree(list: any[]): any[] {
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

async function openOrgPick() {
  orgChecked.value = [...form.value.orgIds]
  if (!orgTreeData.value.length) {
    try {
      const res: any = await api.get('/system/org/list')
      if (res.code === 200) orgTreeData.value = buildOrgTree(res.data || [])
    } catch (e: any) { message.error(e?.response?.data?.msg || '加载部门失败') }
  }
  // 数据就绪后再开弹窗：antd tree 的 default-expand-all 只在挂载时生效，异步晚到不会补展开
  orgDlg.value.open = true
}

// 编辑回显：角色已选部门
async function loadRoleOrgs(roleId: string) {
  try {
    const res: any = await api.get(`/system/role/orgTree/${roleId}`)
    if (res.code === 200) orgChecked.value = (res.data.checkedKeys || []).map(String)
  } catch { /* ignore */ }
}

function confirmOrgPick() {
  form.value.orgIds = [...orgChecked.value]
  orgDlg.value.open = false
}


// antd tree 的 check 回调可能是数组或 {checkedKeys,...} 对象，统一取数组
function extractKeys(v: any): string[] {
  if (Array.isArray(v)) return v.map(String)
  if (v && Array.isArray(v.checkedKeys)) return v.checkedKeys.map(String)
  if (v && Array.isArray(v.checked)) return v.checked.map(String)
  return []
}
function onOrgCheck(keys: any) { orgChecked.value = extractKeys(keys) }
function onPermCheck(keys: any, e: any) {
  permChecked.value = extractKeys(keys)
  const half = e?.halfCheckedKeys || e?.halfChecked
  permHalf.value = Array.isArray(half) ? half.map(String) : []
}

onMounted(() => { loadList(); loadColState() })
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'SystemIndex' })" href="javascript:;">←</a>
        <span class="head-title">角色管理</span>
      </div>
    </div>

    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索角色名称" allow-clear style="width:200px" @press-enter="onSearch">
        <template #prefix><SearchOutlined style="color:#86909C" /></template>
      </a-input>
      <a-button type="primary" @click="onSearch">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button type="primary" @click="openAdd"><PlusOutlined />新增角色</a-button>
    </div>

    <div class="table-bar">
      <span class="total">共 {{ total }} 条</span>
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
      :data-source="rows"
      :loading="loading"
      :pagination="{ total, current: pageNum, pageSize, showSizeChanger: true, showQuickJumper: true, showTotal: showTotalText, onChange: onPageChange }"
      table-layout="fixed"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
      row-key="roleId"
      size="middle"
      class="role-table"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'index'">
          {{ (pageNum - 1) * pageSize + index + 1 }}
        </template>
        <template v-else-if="column.key === 'roleName'">
          <span class="role-name">{{ record.roleName }}</span>
        </template>
        <template v-else-if="column.key === 'dataScope'">
          {{ scopeLabel(record.dataScope) }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <div class="ops-btns">
            <a-button v-if="record.roleKey !== 'superadmin'" type="primary" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-else size="small" disabled title="系统内置角色，不可编辑删除">编辑</a-button>
            <a-button size="small" @click="openPerm(record)">分配权限</a-button>
            <a-button v-if="record.roleKey !== 'superadmin'" danger size="small" @click="removeOne(record)">删除</a-button>
            <a-button v-else danger size="small" disabled title="系统内置角色，不可编辑删除">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑角色 -->
    <a-modal
      v-model:open="showDlg"
      :mask-closable="false"
      :keyboard="false"
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="560px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="角色名称" name="roleName">
          <a-input v-model:value="form.roleName" placeholder="如 生产管理 / 质检 / 跟单" :maxlength="30" />
        </a-form-item>
        <a-form-item label="权限字符" name="roleKey">
          <a-input v-model:value="form.roleKey" placeholder="英文标识，如 production / qc" :maxlength="100" />
        </a-form-item>
        <a-form-item label="数据范围">
          <a-select v-model:value="form.dataScope" @change="form.dataScope === '2' ? openOrgPick() : undefined">
            <a-select-option value="1">全部数据</a-select-option>
            <a-select-option value="2">自定义节点</a-select-option>
            <a-select-option value="3">本部门及以下</a-select-option>
            <a-select-option value="6">本小组及下级</a-select-option>
            <a-select-option value="5">仅本人</a-select-option>
          </a-select>
          <div v-if="form.dataScope === '2'" class="scope-orgs">
            <a class="pick-btn" @click="openOrgPick">选择节点</a>
            <span v-if="form.orgIds.length" class="scope-tip">已选 {{ form.orgIds.length }} 个节点（含其下级）</span>
            <span v-else class="scope-tip">未选择</span>
          </div>
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.roleSort" :min="0" style="width:100%" placeholder="数字越小越靠前" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.status" checked-value="0" un-checked-value="1" checked-children="启用" un-checked-children="停用" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" :maxlength="200" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 自定义部门勾选弹窗 -->
    <a-modal
      v-model:open="orgDlg.open"
      :mask-closable="false"
      :keyboard="false"
      title="选择数据范围节点（部门/小组均可勾，均含其全部下级）"
      width="460px"
      ok-text="确定"
      cancel-text="取消"
      @ok="confirmOrgPick"
    >
      <div class="perm-tree">
        <a-tree
          v-if="orgTreeData.length"
          checkable
          default-expand-all
          :tree-data="orgTreeData"
          :field-names="{ key: 'orgId', title: 'orgName', children: 'children' }"
          :checked-keys="orgChecked"
          @check="onOrgCheck"
          :selectable="false"
        />
      </div>
    </a-modal>

    <!-- 分配权限（菜单树打钩，含按钮级权限码） -->
    <a-modal
      v-model:open="permDlg.open"
      :mask-closable="false"
      :keyboard="false"
      :title="`分配权限 - ${permDlg.roleName}`"
      width="520px"
      ok-text="保存权限"
      cancel-text="取消"
      :confirm-loading="permSaving"
      @ok="savePerm"
    >
      <div class="perm-tip">勾选该角色可用的功能（含页面与按钮级权限：查看/新增/编辑/删除；有审核的功能会以"审核"按钮出现）</div>
      <div class="perm-tree">
        <a-tree
          checkable
          default-expand-all
          :tree-data="permTree"
          :field-names="{ key: 'id', title: 'label', children: 'children' }"
          :checked-keys="permChecked"
          @check="onPermCheck"
          :half-checked-keys="permHalf"
          :selectable="false"
        />
      </div>
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

.role-table { width: 100%; background: #fff; border-radius: 10px; }
.role-table :deep(.ant-table) { font-size: 13px; }
.role-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.role-table :deep(.ant-table-thead > tr > th),
.role-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.role-table :deep(.ant-table-thead > tr > th:last-child),
.role-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.role-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.role-name { font-weight: 500; color: #1D2129; }
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-link { display: inline-flex; align-items: center; gap: 4px; margin-right: 10px; font-size: 13px; }
.ops-disabled { color: #C9CDD4; cursor: not-allowed; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }

.scope-orgs { display: flex; align-items: center; gap: 10px; margin-top: 6px; }
.scope-orgs .pick-btn { color: #1E5EFF; cursor: pointer; }
.scope-tip { font-size: 12px; color: #86909C; }

.perm-tip { font-size: 12px; color: #86909C; margin-bottom: 10px; line-height: 1.6; }
.perm-tree { max-height: 420px; overflow-y: auto; border: 1px solid #F0F2F5; border-radius: 6px; padding: 8px 4px; }
</style>

<!-- 非 scoped：列宽拖拽手柄（标准件勿改） -->
<style>
.ant-table-thead > tr > th.zy-th-noresize::after { display: none; }
.ant-table-thead > tr > th { position: relative; }
.ant-table-thead > tr > th::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; transition: border-color .12s, background .12s; }
.ant-table-thead > tr > th:hover::after { border-right-color: #C9CDD4; }
.ant-table-thead > tr > th:active::after { background: rgba(30,94,255,.15); border-right-color: #1E5EFF; }
</style>
