<script setup lang="ts">
import '../assets/standard-list-fill.css'
/**
 * 系统基础资料：国家/地区。
 * 页面复用项目定版布局，编码新增后固定；名称可维护，停用不会改变已有业务。
 * 本页目录数据量较小，先加载全部记录再在前端筛选分页；业务选择器通过独立接口获取同一目录。
 */
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import { can } from '../utils/perms'

type Country = { country_code: string; name_zh: string; name_en: string; status: string }
type ColumnState = { key: string; visible: boolean; width: number }
const allColumns = [
  { title: '编码', key: 'country_code', width: 120 },
  { title: '中文名称', key: 'name_zh', width: 220 },
  { title: '英文名称', key: 'name_en', width: 300 },
  { title: '状态', key: 'status', width: 120 },
]
const rows = ref<Country[]>([])
const loading = ref(false)
const keyword = ref('')
const status = ref<string>()
const pageNum = ref(1)
const pageSize = ref(20)
const columnOpen = ref(false)
const storageKey = computed(() => `country_region_list:${localStorage.getItem('userId') || 'anon'}`)
const columnState = ref<ColumnState[]>([])
const operationsWidth = ref(140)
// 用户拖动后才保存列宽，其他情况下随容器自动适配。
const manualWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return rows.value.filter(r => (!q || [r.country_code, r.name_zh, r.name_en].some(v => String(v || '').toLowerCase().includes(q))) && (!status.value || r.status === status.value))
})

/** 按用户恢复列显示、顺序与列宽；出现新字段时自动补齐默认配置。 */
function loadColumns() {
  manualWidths.value = localStorage.getItem(`${storageKey.value}:manual_widths`) === '1'
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey.value) || '[]') as ColumnState[]
    const remaining = new Set(allColumns.map(c => c.key))
    columnState.value = saved.filter(c => remaining.delete(c.key)).map(c => ({ key: c.key, visible: c.visible !== false, width: manualWidths.value && Number(c.width) >= 80 ? Number(c.width) : (allColumns.find(x => x.key === c.key)?.width || 120) }))
    for (const c of allColumns) if (remaining.has(c.key)) columnState.value.push({ key: c.key, visible: true, width: c.width })
    operationsWidth.value = Math.max(120, Number(localStorage.getItem(`${storageKey.value}:ops`)) || 140)
  } catch { columnState.value = allColumns.map(c => ({ key: c.key, visible: true, width: c.width })) }
}
function saveColumns() { localStorage.setItem(storageKey.value, JSON.stringify(manualWidths.value ? columnState.value : columnState.value.map(({ key, visible }) => ({ key, visible })))); if (manualWidths.value) localStorage.setItem(`${storageKey.value}:ops`, String(operationsWidth.value)) }
function titleOf(key: string) { return allColumns.find(c => c.key === key)?.title || key }
function moveColumn(index: number, delta: number) { const next = index + delta; if (next < 0 || next >= columnState.value.length) return; const [item] = columnState.value.splice(index, 1); columnState.value.splice(next, 0, item); saveColumns() }
function toggleColumn(key: string) { const item = columnState.value.find(c => c.key === key); if (item) { item.visible = !item.visible; saveColumns(); if (!manualWidths.value) void autoFit() } }
function resetColumns() { manualWidths.value = false; localStorage.removeItem(`${storageKey.value}:manual_widths`); localStorage.removeItem(`${storageKey.value}:ops`); columnState.value = allColumns.map(c => ({ key: c.key, visible: true, width: c.width })); operationsWidth.value = 140; saveColumns(); void autoFit() }

/** 以表体可见宽度分配空间；最小宽度总和超过屏幕时允许横向滚动。 */
async function autoFit() {
  await nextTick()
  const visible = columnState.value.filter(c => c.visible)
  const body = document.querySelector<HTMLElement>('.country-table .ant-table-body')
  const container = document.querySelector<HTMLElement>('.country-table .ant-table-container')
  const available = Math.max(0, (body?.clientWidth || container?.clientWidth || 0) - 2)
  operationsWidth.value = 140
  for (const c of visible) c.width = Math.max(100, titleOf(c.key).length * 16 + 48)
  const used = 64 + operationsWidth.value + visible.reduce((sum, c) => sum + c.width, 0)
  const extra = visible.length ? Math.max(0, Math.floor((available - used) / visible.length)) : 0
  for (const c of visible) c.width += extra
}
let suppressClickUntil = 0
function onHeaderMouseDown(key: string, event: MouseEvent) {
  const th = event.currentTarget as HTMLElement
  const rect = th.getBoundingClientRect()
  if (event.clientX < rect.right - 14) return
  const col = columnState.value.find(c => c.key === key)
  if (!col && key !== 'action') return
  const start = event.clientX, width = key === 'action' ? operationsWidth.value : col!.width
  const move = (e: MouseEvent) => { manualWidths.value = true; localStorage.setItem(`${storageKey.value}:manual_widths`, '1'); const next = Math.max(key === 'action' ? 120 : 80, Math.round(width + e.clientX - start)); if (key === 'action') operationsWidth.value = next; else col!.width = next }
  const up = () => { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); document.body.style.cursor = ''; suppressClickUntil = Date.now() + 300; saveColumns() }
  document.addEventListener('mousemove', move); document.addEventListener('mouseup', up); document.body.style.cursor = 'col-resize'; event.preventDefault(); event.stopPropagation()
}
function onHeaderClick(event: MouseEvent) { if (Date.now() < suppressClickUntil) { event.preventDefault(); event.stopPropagation() } }
const columns = computed(() => [
  { title: '序号', key: 'index', width: 64, fixed: 'left' as const, align: 'center' as const, customHeaderCell: () => ({ class: 'zy-th-noresize' }) },
  ...columnState.value.filter(c => c.visible).map(c => ({ title: titleOf(c.key), key: c.key, dataIndex: c.key, width: c.width, ellipsis: true, sorter: (a: Country, b: Country) => String(a[c.key as keyof Country] || '').localeCompare(String(b[c.key as keyof Country] || ''), 'zh-CN'), customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }) })),
  { title: '操作', key: 'action', width: operationsWidth.value, fixed: 'right' as const, customHeaderCell: () => ({ class: 'ops-th', onMousedown: (e: MouseEvent) => onHeaderMouseDown('action', e), onClick: onHeaderClick }) },
])
const scrollWidth = computed(() => 64 + operationsWidth.value + columnState.value.filter(c => c.visible).reduce((sum, c) => sum + c.width, 0))

async function loadRows() {
  loading.value = true
  try { const res: any = await api.get('/system/country-region/list'); if (res.code !== 200) throw new Error(res.msg || '加载失败'); rows.value = res.data || [] }
  catch (e: any) { message.error(e?.response?.data?.msg || e.message || '加载失败') } finally { loading.value = false }
}
function search() { pageNum.value = 1 }
function resetFilters() { keyword.value = ''; status.value = undefined; pageNum.value = 1 }

const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({ country_code: '', name_zh: '', name_en: '' })
const initialForm = ref('')
function openAdd() { isEdit.value = false; form.value = { country_code: '', name_zh: '', name_en: '' }; initialForm.value = JSON.stringify(form.value); showDlg.value = true }
function openEdit(row: Country) { isEdit.value = true; form.value = { country_code: row.country_code, name_zh: row.name_zh, name_en: row.name_en || '' }; initialForm.value = JSON.stringify(form.value); showDlg.value = true }
function closeForm() {
  if (saving.value) return
  if (JSON.stringify(form.value) === initialForm.value) { showDlg.value = false; return }
  Modal.confirm({ title: '放弃未保存的修改？', content: '关闭后，本次输入不会保存。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { showDlg.value = false } })
}
async function save() {
  if (saving.value) return
  const code = form.value.country_code.trim().toUpperCase(), zh = form.value.name_zh.trim(), en = form.value.name_en.trim()
  if (!/^[A-Z][A-Z0-9_-]{1,11}$/.test(code)) { message.warning('编码须以大写字母开头，长度2至12位'); return }
  if (!zh) { message.warning('请输入中文名称'); return }
  saving.value = true
  try {
    const payload = { country_code: code, name_zh: zh, name_en: en }
    const res: any = isEdit.value ? await api.put(`/system/country-region/${encodeURIComponent(code)}`, payload) : await api.post('/system/country-region', payload)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    showDlg.value = false; message.success('保存成功'); await loadRows()
  } catch (e: any) { message.error(e?.response?.data?.msg || e.message || '保存失败') } finally { saving.value = false }
}
/** 停用只影响新业务选择；旧客户和地址仍保留原编码及名称。 */
function changeStatus(row: Country, checked: unknown) {
  const next = checked === true ? '0' : '1'
  if (next === row.status) return
  Modal.confirm({ title: `${next === '0' ? '启用' : '停用'}「${row.name_zh}」？`, content: next === '1' ? '停用后不能新增引用，已有客户和地址保留原记录。' : '启用后可供新业务选择。', okText: '确认', cancelText: '取消', onOk: async () => { const res: any = await api.put(`/system/country-region/${encodeURIComponent(row.country_code)}/status`, { status: next }); if (res.code !== 200) throw new Error(res.msg || '状态修改失败'); await loadRows(); message.success('状态已更新') } })
}
onMounted(async () => { loadColumns(); await loadRows(); if (!manualWidths.value) await autoFit(); widthObserver = new ResizeObserver(() => { if (!manualWidths.value) void autoFit() }); const area = document.querySelector<HTMLElement>('.country-table .ant-table-container'); if (area) widthObserver.observe(area) })
onBeforeUnmount(() => widthObserver?.disconnect())
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <div class="head-filters"><a-input v-model:value="keyword" placeholder="搜索编码 / 中文 / 英文名称" allow-clear style="width:240px" @press-enter="search"><template #prefix><SearchOutlined style="color:#86909C" /></template></a-input><a-select v-model:value="status" allow-clear placeholder="状态" style="width:110px" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" @change="search" /></div>
    </template>
    <template #actions>
      <div class="filter-bar"><a-button v-if="can('system:country:add')" type="primary" @click="openAdd"><PlusOutlined />新增</a-button><a-button type="primary" @click="search">查询</a-button><a-button @click="resetFilters">重置</a-button><span class="filter-spacer" />
        <a-dropdown v-model:open="columnOpen" :trigger="['click']"><a-button>⚙ 列设置</a-button><template #overlay><a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }"><div v-for="(col,index) in columnState" :key="col.key" class="col-set-item"><a-checkbox :checked="col.visible" @change="toggleColumn(col.key)">{{ titleOf(col.key) }}</a-checkbox><span><a-button type="text" size="small" :disabled="index === 0" @click="moveColumn(index,-1)">↑</a-button><a-button type="text" size="small" :disabled="index === columnState.length-1" @click="moveColumn(index,1)">↓</a-button></span></div><a-divider style="margin:6px 0" /><div class="col-set-foot"><a @click="resetColumns">恢复默认</a><a-button type="primary" size="small" @click="columnOpen=false">完成</a-button></div></a-card></template></a-dropdown>
      </div>
    </template>
    <a-table :columns="columns" :data-source="filteredRows" :loading="loading" row-key="country_code" size="middle" class="country-table zy-fill-table" table-layout="fixed" :scroll="{ x: scrollWidth, y: 'calc(100vh - 390px)' }" :pagination="{ current: pageNum, pageSize, total: filteredRows.length, showSizeChanger: true, showQuickJumper: true, showTotal: (n: number) => `共 ${n} 条`, onChange: (p: number, ps: number) => { pageNum=p; pageSize=ps } }">
      <template #bodyCell="{column,record,index}">
        <template v-if="column.key === 'index'">{{ (pageNum-1)*pageSize+index+1 }}</template>
        <template v-else-if="column.key === 'status'"><a-switch :checked="record.status === '0'" :disabled="!can('system:country:edit')" checked-children="启用" un-checked-children="停用" @change="v => changeStatus(record as Country, v)" /></template>
        <template v-else-if="column.key === 'action'"><a-button v-if="can('system:country:edit')" type="primary" size="small" @click="openEdit(record as Country)">编辑</a-button></template>
      </template>
      <template #summary><a-table-summary fixed><a-table-summary-row><a-table-summary-cell :index="0" :col-span="columns.length" class="summary-cell">合计（筛选结果）　{{ filteredRows.length }} 条</a-table-summary-cell></a-table-summary-row></a-table-summary></template>
    </a-table>
    <a-modal :open="showDlg" :title="isEdit ? '编辑国家/地区' : '新增国家/地区'" width="640px" :mask-closable="false" :keyboard="false" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save" @cancel="closeForm">
      <a-form layout="vertical"><a-row :gutter="12"><a-col :span="12"><a-form-item label="编码" required><a-input v-model:value="form.country_code" :disabled="isEdit" :maxlength="12" placeholder="如 CN，创建后不可修改" /></a-form-item></a-col><a-col :span="12"><a-form-item label="中文名称" required><a-input v-model:value="form.name_zh" :maxlength="100" /></a-form-item></a-col><a-col :span="24"><a-form-item label="英文名称"><a-input v-model:value="form.name_en" :maxlength="200" /></a-form-item></a-col></a-row></a-form>
    </a-modal>
  </StandardListLayout>
</template>

<style scoped>
.head-filters,.filter-bar { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.filter-bar { background:#fff; padding:12px; border-radius:10px; box-shadow:0 2px 8px rgba(29,33,41,.06); }
.filter-spacer { flex:1 1 auto; min-width:8px; }.col-set-panel { width:220px; }.col-set-item,.col-set-foot { display:flex; align-items:center; justify-content:space-between; padding:2px 8px; }
.country-table :deep(.ant-table) { font-size:14px; }.country-table :deep(.ant-table-thead > tr > th) { background:#F7F8FA; font-weight:600; }.country-table :deep(.ant-table-tbody > tr:hover > td) { background:#F5F8FF; }.summary-cell { background:#F7F8FA; font-weight:600; }
</style>
<style>
/* 和功能模板一致，表头右缘用于调整列宽；仅限定本页面，避免影响其他表格。 */
.country-table .ant-table-thead > tr > th { position: relative; }
.country-table .ant-table-thead > tr > th:not(.zy-th-noresize)::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; border-right: 2px solid #E5E8EF; }
.country-table .ant-table-thead > tr > th:not(.zy-th-noresize):hover::after { border-right-color: #C9CDD4; }
</style>
