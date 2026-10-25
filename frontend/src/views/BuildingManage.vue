<script setup lang="ts">
/**
 * 建筑档案（物理资产/固定资产）—— ant-design-vue：
 * - 建筑=一栋楼（面积/楼层/地址/建成年份），部门（车间/组）坐落在建筑里；用途类别：厂房/行政楼/宿舍楼（仓库是建筑内区域不在此列）
 * - a-table：用途类别/编号/名称/楼层/占地/建筑面积/地址/建成年份/状态/关联车间/操作
 * - a-modal + a-form：新增/编辑（GET /system/building/{plantId} 详情回填）
 * - 删除：Modal.confirm 二次确认；有车间占用（deptCount>0）后端拒绝，透出后端 msg
 * - 后端：/system/building（list/{id}/POST/PUT/DELETE/{plantId}）
 */
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

const router = useRouter()

// ===== 列表 =====
const rows = ref<any[]>([])
const loading = ref(false)
const buildingType = ref<string | undefined>()   // 用途类别筛选（空=全部）

// 用途类别：厂房 / 行政楼 / 宿舍楼（仓库是建筑内区域，不在此列）
const typeOptions = ['厂房', '行政楼', '宿舍楼']
const typeColor: Record<string, string> = { '厂房': 'blue', '行政楼': 'green', '宿舍楼': 'purple' }
function typeText(v: any) { return v || '厂房' }
function typeColorOf(v: any) { return typeColor[String(v || '厂房')] || 'blue' }

// 状态字典：'0' 在用 / '1' 闲置 / '2' 维修
const statusMap: Record<string, { text: string; color: string }> = {
  '0': { text: '在用', color: 'green' },
  '1': { text: '闲置', color: 'orange' },
  '2': { text: '维修', color: 'red' },
}
function statusText(v: any) { return statusMap[String(v ?? '0')]?.text || '在用' }
function statusColor(v: any) { return statusMap[String(v ?? '0')]?.color || 'green' }

const columns = [
  { title: '用途类别', key: 'buildingType', width: 90 },
  { title: '建筑编号', dataIndex: 'plantCode', key: 'plantCode', width: 100 },
  { title: '建筑名称', dataIndex: 'plantName', key: 'plantName', width: 150 },
  { title: '楼层情况', dataIndex: 'floorInfo', key: 'floorInfo', width: 100 },
  { title: '占地面积(㎡)', dataIndex: 'landArea', key: 'landArea', width: 120 },
  { title: '建筑面积(㎡)', dataIndex: 'buildingArea', key: 'buildingArea', width: 120 },
  { title: '地址', dataIndex: 'address', key: 'address', ellipsis: true },
  { title: '建成年份', dataIndex: 'builtYear', key: 'builtYear', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '关联车间', key: 'deptCount', width: 100 },
  { title: '操作', key: 'ops', width: 120, fixed: 'right' as const },
]

// 面积展示：数字或字符串，统一 2 位以内的尾随展示；空显示 —
function fmtArea(v: any) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : String(Math.round(n * 100) / 100)
}
function fmtYear(v: any) {
  if (v === null || v === undefined || v === '') return '—'
  return String(v)
}

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/building/list', { params: { buildingType: buildingType.value ?? '' } })
    if (res.code === 200) rows.value = res.data || []
    else message.error(res.msg || '加载建筑失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '加载建筑失败')
  } finally { loading.value = false }
}

// ===== 新增/编辑 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  plantId: null as number | null,
  buildingType: '厂房',
  plantCode: '',
  plantName: '',
  floorInfo: undefined as string | undefined,   // a-select：单层/双层/三层/多层（allowClear 自由填）
  landArea: undefined as number | undefined,
  buildingArea: undefined as number | undefined,
  address: '',
  builtYear: undefined as number | undefined,
  status: '0',
  remark: '',
})
const form = ref(emptyForm())

const rules: any = {
  plantName: [{ required: true, message: '请输入建筑名称', trigger: 'blur' }],
}

const floorOptions = ['单层', '双层', '三层', '多层']

function openAdd() {
  isEdit.value = false
  form.value = emptyForm()
  showDlg.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  // 先 GET 详情回填；接口异常时退化用行数据
  let d: any = null
  try {
    const res: any = await api.get(`/system/building/${row.plantId}`)
    if (res.code === 200) d = res.data
  } catch { d = null }
  if (!d) d = row
  form.value = {
    plantId: Number(d.plantId),
    buildingType: d.buildingType || '厂房',
    plantCode: d.plantCode || '',
    plantName: d.plantName || '',
    floorInfo: d.floorInfo || undefined,
    // 数字字段后端可能回字符串，Number() 归一；空值保持 undefined
    landArea: d.landArea === null || d.landArea === undefined || d.landArea === '' ? undefined : Number(d.landArea),
    buildingArea: d.buildingArea === null || d.buildingArea === undefined || d.buildingArea === '' ? undefined : Number(d.buildingArea),
    address: d.address || '',
    builtYear: d.builtYear === null || d.builtYear === undefined || d.builtYear === '' ? undefined : Number(d.builtYear),
    status: d.status === 0 ? '0' : String(d.status ?? '0'),
    remark: d.remark || '',
  }
  showDlg.value = true
}

async function save() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    // undefined 字段不传（后端按 null/缺省处理）
    const payload: any = {
      buildingType: form.value.buildingType || '厂房',
      plantCode: form.value.plantCode || undefined,
      plantName: form.value.plantName,
      floorInfo: form.value.floorInfo || undefined,
      landArea: form.value.landArea ?? undefined,
      buildingArea: form.value.buildingArea ?? undefined,
      address: form.value.address || undefined,
      builtYear: form.value.builtYear ?? undefined,
      status: form.value.status || '0',
      remark: form.value.remark || undefined,
    }
    if (isEdit.value) payload.plantId = form.value.plantId
    const res: any = isEdit.value
      ? await api.put('/system/building', payload)
      : await api.post('/system/building', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

// ===== 删除 =====
function removeBuilding(row: any) {
  Modal.confirm({
    title: '删除建筑',
    content: `确认删除建筑「${row.plantName}」？若有车间占用，后端将拒绝删除。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try {
        const res: any = await api.delete(`/system/building/${row.plantId}`)
        if (res.code === 200) { message.success('已删除'); loadList() }
        else message.error(res.msg || '删除失败')
      } catch (e: any) {
        message.error(e?.response?.data?.msg || '删除失败')
      }
    },
  })
}

// 弹窗关闭时重置校验状态
watch(showDlg, (v) => { if (!v) formRef.value?.clearValidate() })

onMounted(loadList)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>
        <span class="head-title">建筑档案</span>
        <span class="head-sub">建筑=物理资产（一栋楼：面积/楼层/地址/建成年份），部门坐落在建筑里</span>
      </div>
      <div class="head-actions">
        <a-select v-model:value="buildingType" placeholder="用途类别" allow-clear style="width:120px" @change="loadList">
          <a-select-option v-for="t in typeOptions" :key="t" :value="t">{{ t }}</a-select-option>
        </a-select>
        <a-button :loading="loading" @click="loadList">
          <ReloadOutlined />刷新
        </a-button>
        <a-button type="primary" size="middle" @click="openAdd">
          <PlusOutlined />新增建筑
        </a-button>
      </div>
    </div>

    <!-- 建筑列表（条数少，不分页） -->
    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 'max-content' }"
      row-key="plantId"
      size="middle"
      class="plant-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'buildingType'">
          <a-tag :color="typeColorOf(record.buildingType)">{{ typeText(record.buildingType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'plantCode'">
          {{ record.plantCode || '—' }}
        </template>
        <template v-else-if="column.key === 'plantName'">
          <span class="plant-name">{{ record.plantName }}</span>
        </template>
        <template v-else-if="column.key === 'floorInfo'">
          {{ record.floorInfo || '—' }}
        </template>
        <template v-else-if="column.key === 'landArea'">
          {{ fmtArea(record.landArea) }}
        </template>
        <template v-else-if="column.key === 'buildingArea'">
          {{ fmtArea(record.buildingArea) }}
        </template>
        <template v-else-if="column.key === 'address'">
          <a-tooltip :title="record.address">
            <span class="addr">{{ record.address || '—' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'builtYear'">
          {{ fmtYear(record.builtYear) }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'deptCount'">
          <span v-if="Number(record.deptCount) > 0" class="dept-count">{{ record.deptCount }}</span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'ops'">
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a class="danger-link" @click="removeBuilding(record)">删除</a>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑 -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑建筑' : '新增建筑'"
      width="640px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="用途类别">
              <a-select v-model:value="form.buildingType" placeholder="请选择">
                <a-select-option v-for="t in typeOptions" :key="t" :value="t">{{ t }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="建筑编号">
              <a-input v-model:value="form.plantCode" :maxlength="30" placeholder="如 P1（选填）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="建筑名称" name="plantName">
              <a-input v-model:value="form.plantName" :maxlength="50" placeholder="请输入建筑名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="楼层情况">
              <a-select
                v-model:value="form.floorInfo"
                allow-clear
                placeholder="请选择或自由输入"
              >
                <a-select-option v-for="f in floorOptions" :key="f" :value="f">{{ f }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="建成年份">
              <a-input-number v-model:value="form.builtYear" :min="1900" :max="2100" style="width:100%" placeholder="如 2020" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="占地面积(㎡)">
              <a-input-number v-model:value="form.landArea" :min="0" :precision="2" style="width:100%" placeholder="㎡" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="建筑面积(㎡)">
              <a-input-number v-model:value="form.buildingArea" :min="0" :precision="2" style="width:100%" placeholder="㎡" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="地址">
              <a-input v-model:value="form.address" :maxlength="200" placeholder="选填" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="状态">
              <a-radio-group v-model:value="form.status">
                <a-radio value="0">在用</a-radio>
                <a-radio value="1">闲置</a-radio>
                <a-radio value="2">维修</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="form.remark" :rows="3" :maxlength="500" placeholder="选填" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 8px; min-width: 0; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-sub { font-size: 12px; color: #86909C; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.plant-table { width: 100%; background: #fff; border-radius: 10px; }
.plant-table :deep(.ant-table) { font-size: 13px; }
.plant-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.plant-table :deep(.ant-table-thead > tr > th),
.plant-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.plant-table :deep(.ant-table-thead > tr > th:last-child),
.plant-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.plant-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

.plant-name { font-weight: 500; color: #1D2129; }
.addr { color: #4E5969; }
.dept-count { color: #1E5EFF; font-weight: 500; }
.danger-link { color: #F53F3F; }
.danger-link:hover { color: #cb2634; }
</style>
