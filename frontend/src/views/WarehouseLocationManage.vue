<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import { canEnter } from '../utils/perms'

type Location = Record<string, any> & { children?: Location[] }
type Warehouse = { warehouse_id: string; warehouse_code: string; warehouse_name: string; inbound_location_level: number }
const ROOT = '__warehouse_root__'
const warehouses = ref<Warehouse[]>([])
const selectedWarehouseId = ref<string>()
const treeRows = ref<Location[]>([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const statusFilter = ref<string>()
const selectedNodeKey = ref(ROOT)
const dialogOpen = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({ locationId: '', warehouseId: '', parentLocationId: undefined as string | undefined, locationLevel: 1, locationCode: '', locationName: '', agvReachable: true, capacityLimit: undefined as number | undefined, capacityUnit: '', locationStatus: 'active', externalLocationCode: '', remark: '' })
const statusOptions = [
  { value: 'active', label: '可用' },
  { value: 'disabled', label: '停用' },
  { value: 'maintenance', label: '维护中' },
]
const rootTitle = computed(() => {
  const item = warehouses.value.find(row => row.warehouse_id === selectedWarehouseId.value)
  return item ? `${item.warehouse_name}（仓库）` : '仓库'
})
const rootNode = computed(() => ({ key: ROOT, title: rootTitle.value, locationLevel: 0, children: treeRows.value as any[] }))
const treeData = computed(() => [rootNode.value] as any)
const nodeMap = computed(() => {
  const map = new Map<string, any>()
  map.set(ROOT, rootNode.value)
  const visit = (rows: Location[]) => rows.forEach(row => { map.set(String(row.location_id), row); visit(row.children || []) })
  visit(treeRows.value)
  return map
})
const selectedNode = computed<any>(() => nodeMap.value.get(selectedNodeKey.value) || rootNode.value)
const flattened = computed(() => {
  const all: Location[] = []
  const visit = (rows: Location[]) => rows.forEach(row => { all.push(row); visit(row.children || []) })
  visit(treeRows.value)
  return all
})
const rows = computed(() => {
  const q = keyword.value.trim().toLocaleLowerCase()
  let result: Location[]
  if (q) result = flattened.value.filter(row => `${row.location_code} ${row.location_name} ${row.parent_location_name || ''} ${row.external_location_code || ''}`.toLocaleLowerCase().includes(q))
  else result = (selectedNode.value.children || []) as Location[]
  return result.filter(row => !statusFilter.value || row.location_status === statusFilter.value)
})
const selectedLevel = computed(() => Number((selectedNode.value as any).locationLevel ?? (selectedNode.value as any).location_level ?? 0))
const addLevel = computed(() => selectedLevel.value >= 3 ? 0 : selectedLevel.value + 1)
const addLabel = computed(() => ['新增区域', '新增货架', '新增货位'][addLevel.value - 1] || '新增位置')
const columns = [
  { title: '位置编码', dataIndex: 'location_code', key: 'location_code', width: 150 },
  { title: '位置名称', dataIndex: 'location_name', key: 'location_name', width: 180 },
  { title: '层级', key: 'level', width: 100 },
  { title: '上级位置', key: 'parent', width: 180 },
  { title: 'AGV可达', key: 'agv', width: 100 },
  { title: '容量', key: 'capacity', width: 140 },
  { title: '状态', key: 'status', width: 140 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' as const },
]
const rules = {
  locationCode: [{ required: true, message: '请输入位置编码' }],
  locationName: [{ required: true, message: '请输入位置名称' }],
}
function levelName(level: number) { return ['仓库', '区域', '货架', '货位'][level] || '未知' }
function resetFilters() { keyword.value = ''; statusFilter.value = undefined; selectedNodeKey.value = ROOT }
function clearForm() {
  Object.assign(form, { locationId: '', warehouseId: selectedWarehouseId.value || '', parentLocationId: undefined, locationLevel: 1, locationCode: '', locationName: '', agvReachable: true, capacityLimit: undefined, capacityUnit: '', locationStatus: 'active', externalLocationCode: '', remark: '' })
}
function openAdd(parent?: Location | typeof rootNode.value) {
  if (!selectedWarehouseId.value) { message.warning('请先选择仓库'); return }
  const node = parent || selectedNode.value
  const parentLevel = Number((node as any).locationLevel ?? (node as any).location_level ?? 0)
  if (parentLevel >= 3) { message.warning('货位下不能继续新增子级'); return }
  editing.value = false
  clearForm()
  form.locationLevel = parentLevel + 1
  form.parentLocationId = parentLevel === 0 ? undefined : String((node as any).location_id)
  dialogOpen.value = true
}
function openEdit(row: Location) {
  editing.value = true
  clearForm()
  Object.assign(form, {
    locationId: String(row.location_id), warehouseId: String(row.warehouse_id),
    parentLocationId: row.parent_location_id == null ? undefined : String(row.parent_location_id),
    locationLevel: Number(row.location_level), locationCode: row.location_code || '', locationName: row.location_name || '',
    agvReachable: row.agv_reachable !== false, capacityLimit: row.capacity_limit == null ? undefined : Number(row.capacity_limit),
    capacityUnit: row.capacity_unit || '', locationStatus: row.location_status || 'active',
    externalLocationCode: row.external_location_code || '', remark: row.remark || '',
  })
  dialogOpen.value = true
}
async function loadTree() {
  if (!selectedWarehouseId.value) { treeRows.value = []; selectedNodeKey.value = ROOT; return }
  loading.value = true
  try {
    const res: any = await api.get('/system/warehouse-location/tree', { params: { warehouseId: selectedWarehouseId.value } })
    if (res.code !== 200) throw new Error(res.msg || '加载库位失败')
    treeRows.value = res.data || []
    if (!nodeMap.value.has(selectedNodeKey.value)) selectedNodeKey.value = ROOT
  } catch (error: any) {
    treeRows.value = []
    message.error(error?.response?.data?.msg || error.message || '加载库位失败')
  } finally { loading.value = false }
}
async function loadOptions() {
  try {
    const res: any = await api.get('/system/warehouse-location/options')
    if (res.code !== 200) throw new Error(res.msg || '加载仓库失败')
    warehouses.value = (res.data || []).map((row: any) => ({ ...row, warehouse_id: String(row.warehouse_id), inbound_location_level: Number(row.inbound_location_level ?? 2) }))
    if (!selectedWarehouseId.value || !warehouses.value.some(row => row.warehouse_id === selectedWarehouseId.value)) selectedWarehouseId.value = warehouses.value[0]?.warehouse_id
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '加载仓库失败') }
}
async function save() {
  try { await formRef.value?.validate() } catch { return }
  if (form.capacityLimit !== undefined && form.capacityLimit !== null && !form.capacityUnit.trim()) { message.warning('填写容量上限时请同时填写容量单位'); return }
  saving.value = true
  try {
    const payload = { ...form, warehouseId: selectedWarehouseId.value }
    const res: any = editing.value ? await api.put('/system/warehouse-location', payload) : await api.post('/system/warehouse-location', payload)
    if (res.code !== 200) throw new Error(res.msg || '保存库位失败')
    message.success(editing.value ? '修改成功' : '新增成功')
    dialogOpen.value = false
    await loadTree()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存库位失败') }
  finally { saving.value = false }
}
async function changeStatus(row: Location, next: string) {
  try {
    const res: any = await api.put(`/system/warehouse-location/${row.location_id}/status`, { locationStatus: next })
    if (res.code !== 200) throw new Error(res.msg || '修改状态失败')
    message.success('库位状态已更新')
    await loadTree()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '修改状态失败') }
}
function remove(row: Location) {
  Modal.confirm({
    title: '删除库位',
    content: `确认删除“${row.location_name}（${row.location_code}）”吗？有子级或库存引用的位置不能删除。`,
    okText: '确认删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      const res: any = await api.delete(`/system/warehouse-location/${row.location_id}`)
      if (res.code !== 200) throw new Error(res.msg || '删除失败')
      message.success('删除成功')
      await loadTree()
    },
  })
}
async function onWarehouseChange(value: any) { selectedWarehouseId.value = value; selectedNodeKey.value = ROOT; resetFilters(); await loadTree() }
onMounted(async () => { await loadOptions(); await loadTree() })
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <a-select v-model:value="selectedWarehouseId" show-search option-filter-prop="label" :options="warehouses.map(item => ({ label: `${item.warehouse_code} / ${item.warehouse_name}`, value: item.warehouse_id }))" placeholder="选择仓库" style="width: 260px" @change="onWarehouseChange" />
      <a-input v-model:value="keyword" allow-clear placeholder="搜索位置编码/名称/上级位置" style="width: 280px" @press-enter="loadTree" />
      <a-select v-model:value="statusFilter" allow-clear placeholder="状态" style="width: 130px" :options="statusOptions" />
    </template>
    <template #actions>
      <div class="action-bar">
        <div class="left-actions">
          <a-button v-if="canEnter('inventory:location:add') && addLevel > 0" type="primary" :disabled="!selectedWarehouseId" @click="openAdd()">{{ addLabel }}</a-button>
          <a-button type="primary" @click="loadTree">查询</a-button>
          <a-button @click="resetFilters">重置</a-button>
        </div>
        <span class="location-tip">入库定位：至少记录到{{ ['仓库','区域','货架','货位'][warehouses.find(item => item.warehouse_id === selectedWarehouseId)?.inbound_location_level ?? 2] }}；可继续填写更细位置</span>
      </div>
    </template>
    <template #sidebar>
      <div class="tree-head"><strong>仓内位置</strong><a @click="resetFilters">重置</a></div>
      <a-tree
        block-node
        :tree-data="treeData"
        :selected-keys="[selectedNodeKey]"
        :default-expanded-keys="[ROOT]"
        :field-names="{ title: 'title', key: 'key', children: 'children' }"
        @select="(keys:any[]) => { if (keys.length) selectedNodeKey = String(keys[0]) }"
      >
        <template #title="node">
          <span class="tree-node-title">{{ node.title || `${node.location_code} ${node.location_name}` }}</span>
          <a-tag v-if="node.locationLevel || node.location_level" class="level-tag">{{ levelName(Number(node.locationLevel ?? node.location_level)) }}</a-tag>
        </template>
      </a-tree>
      <a-empty v-if="!selectedWarehouseId" description="暂无可用仓库" />
    </template>
    <div class="table-card">
      <a-table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" row-key="location_id" :scroll="{ x: 1210, y: 'calc(100vh - 365px)' }" size="middle">
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'index'">{{ index + 1 }}</template>
          <template v-else-if="column.key === 'level'">{{ levelName(Number(record.location_level)) }}</template>
          <template v-else-if="column.key === 'parent'">{{ record.parent_location_name || '—' }}</template>
          <template v-else-if="column.key === 'agv'">{{ record.agv_reachable ? '可达' : '不可达' }}</template>
          <template v-else-if="column.key === 'capacity'">{{ record.capacity_limit == null ? '—' : `${record.capacity_limit} ${record.capacity_unit || ''}` }}</template>
          <template v-else-if="column.key === 'status'">
            <a-select :value="record.location_status" size="small" style="width:110px" :options="statusOptions" :disabled="!canEnter('inventory:location:edit')" @change="(value:any) => changeStatus(record,String(value))" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-if="canEnter('inventory:location:edit')" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="canEnter('inventory:location:add') && Number(record.location_level) < 3" type="link" size="small" @click="openAdd(record)">新增下级</a-button>
              <a-button v-if="canEnter('inventory:location:remove')" danger type="link" size="small" @click="remove(record)">删除</a-button>
            </a-space>
          </template>
        </template>
        <template #summary><a-table-summary><a-table-summary-row><a-table-summary-cell :index="0" :col-span="8">合计（当前筛选） {{ rows.length }} 条</a-table-summary-cell></a-table-summary-row></a-table-summary></template>
      </a-table>
    </div>
  </StandardListLayout>

  <a-modal v-model:open="dialogOpen" :title="editing ? '编辑位置' : addLabel" :width="720" :mask-closable="false" :keyboard="false" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save">
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="location-form">
      <a-row :gutter="16">
        <a-col :span="12"><a-form-item label="仓库"><a-input :value="rootTitle.replace('（仓库）','')" disabled /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="位置层级"><a-input :value="levelName(form.locationLevel)" disabled /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="上级位置"><a-input :value="form.parentLocationId ? `${nodeMap.get(form.parentLocationId)?.location_code || ''} ${nodeMap.get(form.parentLocationId)?.location_name || ''}` : '仓库根级'" disabled /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="位置编码" name="locationCode"><a-input v-model:value="form.locationCode" :maxlength="80" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="位置名称" name="locationName"><a-input v-model:value="form.locationName" :maxlength="200" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="AGV可达"><a-switch v-model:checked="form.agvReachable" checked-children="可达" un-checked-children="不可达" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="容量上限"><a-input-number v-model:value="form.capacityLimit" :min="0" :precision="4" style="width:100%" placeholder="可选" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="容量单位"><a-input v-model:value="form.capacityUnit" :maxlength="30" placeholder="如箱、托盘" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="外部位置映射码"><a-input v-model:value="form.externalLocationCode" :maxlength="120" placeholder="AGV/WMS对接预留" /></a-form-item></a-col>
        <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="3" :maxlength="1000" /></a-form-item></a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>

<style scoped>
.action-bar { display:flex; align-items:center; justify-content:space-between; gap:12px; background:#fff; padding:10px 12px; border-radius:10px; box-shadow:0 2px 8px rgba(29,33,41,.06); }
.left-actions { display:flex; gap:8px; }
.location-tip { color:#86909C; font-size:12px; }
.tree-head { display:flex; justify-content:space-between; align-items:center; padding:14px 12px; border-bottom:1px solid #F0F0F0; margin-bottom:8px; }
.tree-head strong { color:#1D2129; }
.tree-node-title { vertical-align:middle; }
.level-tag { margin-left:6px; font-size:10px; line-height:16px; padding-inline:4px; }
.table-card { height:100%; min-height:0; background:#fff; border-radius:10px; overflow:hidden; }
.location-form { padding-top:8px; }
:deep(.std-sidebar .ant-tree) { overflow:auto; padding:0 8px 12px; }
:deep(.std-sidebar .ant-empty) { margin-top:24px; }
@media (max-width: 800px) { .action-bar { align-items:flex-start; flex-direction:column; } .location-tip { white-space:normal; } }
</style>
