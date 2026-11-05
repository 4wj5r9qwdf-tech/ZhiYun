<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import TagSelect from '../components/TagSelect.vue'
import { canEnter } from '../utils/perms'

type Row = Record<string, any>
const rows = ref<Row[]>([])
const loading = ref(false)
const keyword = ref('')
const filterStatus = ref<string>()
const filterTagId = ref<string>()
const tagOptions = ref<{ label: string; value: string }[]>([])
const buildings = ref<Row[]>([])
const managers = ref<Row[]>([])
const dialogOpen = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref()
const selectedTagOptions = ref<{ label: string; value: string; disabled?: boolean }[]>([])
const form = reactive({ warehouseId: '', warehouseCode: '', warehouseName: '', buildingId: undefined as string | undefined, floorName: '', areaName: '', managerUserId: undefined as string | undefined, status: '0', inboundLocationLevel: 2 as number, remark: '', tagIds: [] as string[] })
const rules = { warehouseCode: [{ required: true, message: '请输入仓库编码' }], warehouseName: [{ required: true, message: '请输入仓库名称' }] }
const columns = [
  { title: '序号', key: 'index', width: 72 },
  { title: '仓库编码', dataIndex: 'warehouse_code', key: 'warehouse_code', width: 140 },
  { title: '仓库名称', dataIndex: 'warehouse_name', key: 'warehouse_name', width: 180 },
  { title: '仓库类型', dataIndex: 'tag_names', key: 'tag_names', width: 180 },
  { title: '所属建筑', dataIndex: 'building_name', key: 'building_name', width: 160 },
  { title: '楼层/区域', key: 'place', width: 160 },
  { title: '入库定位', dataIndex: 'inbound_location_level', key: 'inbound_location_level', width: 110 },
  { title: '负责人', dataIndex: 'manager_name', key: 'manager_name', width: 120 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
]
const total = computed(() => rows.value.length)
/** 从建筑档案记录的楼层数生成该建筑可选的实际仓库楼层。 */
const floorOptions = computed(() => {
  const building = buildings.value.find(item => String(item.plant_id) === String(form.buildingId))
  const raw = String(building?.floor_info ?? '').trim()
  const chineseCounts: Record<string, number> = { 单: 1, 一: 1, 双: 2, 两: 2, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }
  const numeric = Number.parseInt(raw, 10)
  const count = Number.isInteger(numeric) && numeric > 0 ? numeric : (chineseCounts[raw.replace(/层/g, '')] || 0)
  return Array.from({ length: count }, (_, index) => ({ label: `${index + 1}楼`, value: `${index + 1}楼` }))
})

/** 按当前筛选条件读取仓库主档列表；失败时保留页面并显示服务端中文提示。 */
async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/warehouse/list', { params: { keyword: keyword.value.trim(), status: filterStatus.value || '', tagId: filterTagId.value || '' } })
    if (res.code === 200) rows.value = res.data || []
    else message.error(res.msg || '加载仓库失败')
  } catch (error: any) { message.error(error?.response?.data?.msg || '加载仓库失败') }
  finally { loading.value = false }
}

/** 加载表单关联项及仓库类型标签提示，供新增/编辑表单复用。 */
async function loadOptions() {
  try {
    const res: any = await api.get('/system/warehouse/options')
    if (res.code === 200) {
      buildings.value = res.data?.buildings || []
      managers.value = res.data?.managers || []
    }
    const tags: any = await api.get('/system/tag/options', { params: { moduleKey: 'warehouse_master' } })
    if (tags.code === 200) tagOptions.value = (tags.data || []).map((t: any) => ({ label: t.tagName, value: String(t.tagId) }))
  } catch { /* 选项服务失败时保留页面，并在具体操作时由接口提示 */ }
}

/** 清空关键字、状态和类型筛选，并立即重新查询。 */
function reset() { keyword.value = ''; filterStatus.value = undefined; filterTagId.value = undefined; void loadList() }
/** 重置表单模型和已选标签，避免新增/编辑窗口之间残留上次输入。 */
function clearForm() {
  Object.assign(form, { warehouseId: '', warehouseCode: '', warehouseName: '', buildingId: undefined, floorName: '', areaName: '', managerUserId: undefined, status: '0', inboundLocationLevel: 2, remark: '', tagIds: [] })
  selectedTagOptions.value = []
}
/** 初始化空表单并打开新增仓库窗口。 */
function openAdd() { editing.value = false; clearForm(); dialogOpen.value = true }
/** 读取指定仓库详情并回填关联标签，再打开编辑窗口。 */
async function openEdit(row: Row) {
  editing.value = true; clearForm()
  try {
    const res: any = await api.get(`/system/warehouse/${row.warehouse_id}`)
    if (res.code !== 200) { message.error(res.msg || '加载仓库详情失败'); return }
    const data = res.data || {}
    Object.assign(form, { warehouseId: String(data.warehouse_id), warehouseCode: data.warehouse_code || '', warehouseName: data.warehouse_name || '', buildingId: data.building_id == null ? undefined : String(data.building_id), floorName: data.floor_name || '', areaName: data.area_name || '', managerUserId: data.manager_user_id == null ? undefined : String(data.manager_user_id), status: String(data.status ?? '0'), inboundLocationLevel: Number(data.inbound_location_level ?? 2), remark: data.remark || '', tagIds: (data.tagIds || []).map(String) })
    selectedTagOptions.value = data.tagOptions || []
    dialogOpen.value = true
  } catch (error: any) { message.error(error?.response?.data?.msg || '加载仓库详情失败') }
}
/** 建筑切换后只保留仍在该建筑楼层范围内的仓库楼层值。 */
function changeBuilding(value: any) {
  form.buildingId = value == null ? undefined : String(value)
  if (!floorOptions.value.some(option => option.value === form.floorName)) form.floorName = ''
}
/** 校验并提交仓库档案；成功后关闭窗口并刷新列表及标签提示。 */
async function save() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  const payload = { ...form, tagIds: form.tagIds }
  try {
    const res: any = editing.value ? await api.put('/system/warehouse', payload) : await api.post('/system/warehouse', payload)
    if (res.code === 200) { message.success('保存成功'); dialogOpen.value = false; await Promise.all([loadList(), loadOptions()]) }
    else message.error(res.msg || '保存失败')
  } catch (error: any) { message.error(error?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}
/** 修改仓库启停状态；状态由后端校验并写入审计记录。 */
async function changeStatus(row: Row, checked: boolean) {
  try {
    const res: any = await api.put(`/system/warehouse/${row.warehouse_id}/status`, { status: checked ? '0' : '1' })
    if (res.code === 200) { message.success(checked ? '仓库已启用' : '仓库已停用'); await loadList() }
    else message.error(res.msg || '修改状态失败')
  } catch (error: any) { message.error(error?.response?.data?.msg || '修改状态失败') }
}
/** 二次确认后逻辑删除仓库，避免误删并保留历史关联可追溯性。 */
function remove(row: Row) {
  Modal.confirm({ title: '删除仓库', content: `确认删除“${row.warehouse_name}”吗？`, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => {
    try { const res: any = await api.delete(`/system/warehouse/${row.warehouse_id}`); if (res.code === 200) { message.success('删除成功'); await loadList() } else message.error(res.msg || '删除失败') }
    catch (error: any) { message.error(error?.response?.data?.msg || '删除失败') }
  } })
}
onMounted(async () => { await Promise.all([loadOptions(), loadList()]) })
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <a-input v-model:value="keyword" allow-clear placeholder="搜索仓库编码/名称/位置" style="width: 270px" @press-enter="loadList" />
      <a-select v-model:value="filterStatus" allow-clear placeholder="状态" style="width: 120px" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" />
      <a-select v-model:value="filterTagId" allow-clear placeholder="仓库类型" style="width: 180px" :options="tagOptions" />
    </template>
    <template #actions>
      <div class="action-bar">
        <div><a-button v-if="canEnter('inventory:warehouse:add')" type="primary" @click="openAdd">+ 新增仓库</a-button><a-button type="primary" @click="loadList">查询</a-button><a-button @click="reset">重置</a-button></div>
      </div>
    </template>
    <div class="table-card">
      <a-table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" row-key="warehouse_id" :scroll="{ x: 1360, y: 'calc(100vh - 330px)' }" size="middle">
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'index'">{{ index + 1 }}</template>
          <template v-else-if="column.key === 'place'">{{ [record.floor_name, record.area_name].filter(Boolean).join(' / ') || '—' }}</template>
          <template v-else-if="column.key === 'inbound_location_level'">{{ ['仓库级','区域级','货架级','货位级'][Number(record.inbound_location_level)] || '货架级' }}</template>
          <template v-else-if="column.key === 'status'"><a-switch :checked="String(record.status) === '0'" checked-children="启用" un-checked-children="停用" @change="(v:any) => changeStatus(record,Boolean(v))" /></template>
          <template v-else-if="column.key === 'action'"><a-space><a-button v-if="canEnter('inventory:warehouse:edit')" type="link" size="small" @click="openEdit(record)">编辑</a-button><a-button v-if="canEnter('inventory:warehouse:remove')" danger type="link" size="small" @click="remove(record)">删除</a-button></a-space></template>
        </template>
        <template #summary><a-table-summary><a-table-summary-row><a-table-summary-cell :index="0" :col-span="9">合计（筛选结果） {{ total }} 条</a-table-summary-cell></a-table-summary-row></a-table-summary></template>
      </a-table>
    </div>
  </StandardListLayout>

  <a-modal v-model:open="dialogOpen" :title="editing ? '编辑仓库' : '新增仓库'" :width="760" :mask-closable="false" :keyboard="false" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save">
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="warehouse-form">
      <a-row :gutter="16">
        <a-col :span="12"><a-form-item label="仓库编码" name="warehouseCode"><a-input v-model:value="form.warehouseCode" :maxlength="50" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="仓库名称" name="warehouseName"><a-input v-model:value="form.warehouseName" :maxlength="200" /></a-form-item></a-col>
        <a-col :span="24"><a-form-item label="仓库类型标签"><TagSelect v-model="form.tagIds" :selected-options="selectedTagOptions" module-key="warehouse_master" module-name="仓库管理" placeholder="选择或输入类型标签，如面料仓、样衣仓" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="所属建筑"><a-select :value="form.buildingId" allow-clear show-search option-filter-prop="label" :options="buildings.map((x:any)=>({label:x.plant_name,value:String(x.plant_id)}))" placeholder="选择所属建筑" @change="changeBuilding" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="所在楼层"><a-select v-model:value="form.floorName" allow-clear :disabled="!floorOptions.length" :options="floorOptions" :placeholder="form.buildingId ? '建筑档案未维护楼层数' : '请先选择所属建筑'" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="区域说明"><a-input v-model:value="form.areaName" placeholder="如主仓储区、样衣仓" :maxlength="100" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="入库定位层级"><a-select v-model:value="form.inboundLocationLevel" :options="[{label:'仓库级（不强制选择库内位置）',value:0},{label:'区域级',value:1},{label:'货架级（默认）',value:2},{label:'货位级',value:3}]" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="仓库负责人"><a-select v-model:value="form.managerUserId" allow-clear show-search option-filter-prop="label" :options="managers.map((x:any)=>({label:x.nick_name || x.user_name,value:String(x.user_id)}))" placeholder="选择负责人" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="状态"><a-select v-model:value="form.status" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" /></a-form-item></a-col>
        <a-col :span="24"><a-form-item label="入库定位说明"><a-typography-text type="secondary">此项控制后续入库单至少记录到哪一级；单据可自愿填写更深位置。</a-typography-text></a-form-item></a-col>
        <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="3" :maxlength="1000" /></a-form-item></a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>

<style scoped>
.action-bar { display:flex; justify-content:space-between; background:#fff; padding:10px 12px; border-radius:10px; box-shadow:0 2px 8px rgba(29,33,41,.06); }
.action-bar > div { display:flex; gap:8px; }
.table-card { height:100%; min-height:0; background:#fff; border-radius:10px; overflow:hidden; }
.warehouse-form { padding-top:8px; }
</style>
