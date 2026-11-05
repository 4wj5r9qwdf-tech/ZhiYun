<script setup lang="ts">
import '../assets/standard-list-fill.css'
/**
 * 系统基础资料：尺码与尺码组，复用项目标准页面。
 * 页面复用项目定版布局，编码新增后固定；名称可维护，停用不会改变已有业务。
 * 本页目录数据量较小，先加载全部记录再在前端筛选分页；业务选择器通过独立接口获取同一目录。
 */
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import TagSelect from '../components/TagSelect.vue'
import { can } from '../utils/perms'

const props = defineProps<{ kind: 'size' | 'size-group' }>()
const title = props.kind === 'size' ? '尺码' : '尺码组'
const moduleKey = props.kind === 'size' ? 'size' : 'size_group'
const endpoint = '/system/size-data/' + props.kind
const permission = (action: string) => can('system:' + props.kind + ':' + action)
type Country = { id: number; code: string; name: string; name_en: string; sort_order: number; remark: string; status: string; tagIds: string[]; tagOptions?: {label: string; value: string; disabled?: boolean}[]; tagNames?: string[]; items?: { id: number; name: string; status: string }[] }
const sizeOptions = ref<Country[]>([])
const sizePickerOpen = ref(false)
const sizePickerKeyword = ref('')
const sizePickerSelected = ref<string[]>([])
const filteredSizeOptions = computed(() => {
 const q=sizePickerKeyword.value.trim().toLowerCase()
 return sizeOptions.value.filter(item => (!q || item.name.toLowerCase().includes(q)) && (item.status === '0' || form.value.sizeIds.includes(String(item.id))))
})
const sizePickerRowSelection = computed(() => ({
 selectedRowKeys: sizePickerSelected.value,
 preserveSelectedRowKeys: true,
 getCheckboxProps: (record: Country) => ({ disabled: record.status !== '0' && !form.value.sizeIds.includes(String(record.id)) }),
 onChange: (keys: (string | number)[]) => { sizePickerSelected.value = keys.map(String) },
}))
type ColumnState = { key: string; visible: boolean; width: number }
const allColumns = [
  { title: '编码', key: 'code', width: 120 },
  { title: '名称', key: 'name', width: 220 },
  ...(props.kind === 'size' ? [{ title: '类型标签', key: 'tagNames', width: 180 }] : []),
  { title: '排序', key: 'sort_order', width: 100 },
  { title: '备注', key: 'remark', width: 200 },
  ...(props.kind === 'size-group' ? [{ title: '组内尺码', key: 'size_names', width: 240 }] : []),
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
const storageKey = computed(() => `size_data_${props.kind}:${localStorage.getItem('userId') || 'anon'}`)
const columnState = ref<ColumnState[]>([])
const operationsWidth = ref(140)
const selectedRowKeys = ref<(string | number)[]>([])
const batchTagsOpen = ref(false)
const batchTagsSaving = ref(false)
const batchTagIds = ref<string[]>([])
const batchTagOptions = ref<{label: string; value: string; disabled?: boolean}[]>([])
// 用户拖动后才保存列宽，其他情况下随容器自动适配。
const manualWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return rows.value.filter(r => (!q || [r.code, r.name, r.name_en, ...(r.tagNames || [])].some(v => String(v || '').toLowerCase().includes(q))) && (!status.value || r.status === status.value))
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
const rowSelection = computed(() => props.kind !== 'size' ? undefined : ({
  selectedRowKeys: selectedRowKeys.value,
  preserveSelectedRowKeys: true,
  onChange: (keys: (string | number)[]) => { selectedRowKeys.value = keys },
}))
const scrollWidth = computed(() => 64 + operationsWidth.value + columnState.value.filter(c => c.visible).reduce((sum, c) => sum + c.width, 0))

async function loadRows() {
  loading.value = true
  try { const res: any = await api.get(endpoint + '/list'); if (res.code !== 200) throw new Error(res.msg || '加载失败'); rows.value = (res.data || []).map((row: any) => ({...row,size_names:(row.items || []).map((item: any)=>item.name).join('、'),tagNames:(row.tagOptions || []).map((tag: any)=>tag.label)})) }
  catch (e: any) { message.error(e?.response?.data?.msg || e.message || '加载失败') } finally { loading.value = false }
}
function openBatchTags() {
 const selected=new Set(selectedRowKeys.value.map(String))
 const selectedRows=rows.value.filter(row=>selected.has(String(row.id)))
 if(!selectedRows.length) { message.warning('请先勾选尺码'); return }
 const options=new Map<string,{label:string;value:string;disabled?:boolean}>()
 for(const row of selectedRows) for(const option of row.tagOptions || []) options.set(option.value,option)
 batchTagOptions.value=[...options.values()]
 batchTagIds.value=[...options.keys()]
 batchTagsOpen.value=true
}
async function saveBatchTags() {
 if(!selectedRowKeys.value.length) { message.warning('请先勾选尺码'); return }
 batchTagsSaving.value=true
 try {
  const res:any=await api.post(endpoint+'/batch-tags',{ids:selectedRowKeys.value.map(Number),tagIds:batchTagIds.value.map(Number)})
  if(res.code!==200) throw new Error(res.msg || '批量修改失败')
  message.success(`已批量修改 ${selectedRowKeys.value.length} 条尺码的类型标签`)
  batchTagsOpen.value=false
  selectedRowKeys.value=[]
  await loadRows()
 } catch(e:any) { message.error(e?.response?.data?.msg || e.message || '批量修改失败') }
 finally { batchTagsSaving.value=false }
}
function search() { pageNum.value = 1 }
function resetFilters() { keyword.value = ''; status.value = undefined; pageNum.value = 1 }

const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const blankForm = () => ({ id: 0, code: '', name: '', name_en: '', sort_order: 0, remark: '', tagIds: [] as string[], tagOptions: [] as {label: string; value: string; disabled?: boolean}[], sizeIds: [] as string[] })
const form = ref(blankForm())
const initialForm = ref('')
async function refreshSizes() {
 if(props.kind !== 'size-group') return
 const res: any = await api.get('/system/size-data/size/options')
 if(res.code !== 200) throw new Error(res.msg || '尺码加载失败')
 sizeOptions.value = res.data || []
}
async function openAdd() {
 try { await refreshSizes(); isEdit.value=false; form.value=blankForm(); initialForm.value=JSON.stringify(form.value); showDlg.value=true }
 catch(e: any) { message.error(e.message || '加载失败') }
}
async function openEdit(row: Country) {
 try { await refreshSizes(); isEdit.value=true; form.value={...blankForm(),...row,sizeIds:(row.items || []).map(item=>String(item.id)),tagIds:(row.tagIds || []).map(String)}; initialForm.value=JSON.stringify(form.value); showDlg.value=true }
 catch(e: any) { message.error(e.message || '加载失败') }
}
/** 数组顺序即组内顺序，独立于尺码档案的默认排序。 */
function moveSize(index: number, direction: number) {
 const next=index+direction
 if(next<0 || next>=form.value.sizeIds.length) return
 const ids=form.value.sizeIds
 ;[ids[index],ids[next]]=[ids[next],ids[index]]
}
function openSizePicker() {
 sizePickerKeyword.value=''
 sizePickerSelected.value=[...form.value.sizeIds]
 sizePickerOpen.value=true
}
function confirmSizePicker() {
 const selected=new Set(sizePickerSelected.value)
 const retained=form.value.sizeIds.filter(id=>selected.has(id))
 const retainedSet=new Set(retained)
 const added=sizeOptions.value.filter(item=>selected.has(String(item.id))&&!retainedSet.has(String(item.id))).map(item=>String(item.id))
 form.value.sizeIds=[...retained,...added]
 sizePickerOpen.value=false
}
function removeSize(id: string) { form.value.sizeIds=form.value.sizeIds.filter(value=>value!==id) }
function closeForm() {
  if (saving.value) return
  if (JSON.stringify(form.value) === initialForm.value) { showDlg.value = false; return }
  Modal.confirm({ title: '放弃未保存的修改？', content: '关闭后，本次输入不会保存。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { showDlg.value = false } })
}
async function save() {
 if(saving.value) return
 if(!form.value.name.trim()) { message.warning('请填写名称'); return }
 saving.value=true
 try {
  const res: any=isEdit.value?await api.put(endpoint+'/'+form.value.id,form.value):await api.post(endpoint,form.value)
  if(res.code!==200) throw new Error(res.msg || '保存失败')
  showDlg.value=false; message.success('保存成功'); await loadRows()
 } catch(e: any) { message.error(e?.response?.data?.msg || e.message || '保存失败') }
 finally { saving.value=false }
}
function removeRow(row: Country) {
 Modal.confirm({
  title: `删除${title}`,
  content: props.kind === 'size'
   ? `确定删除“${row.name}（${row.code}）”吗？已被尺码组引用的尺码不能删除。`
   : `确定删除尺码组“${row.name}（${row.code}）”吗？删除后组内尺码关联和标签关联也会一并清理。`,
  okText: '确认删除', cancelText: '取消', okButtonProps: { danger: true },
  onOk: async () => {
   const res: any = await api.delete(endpoint + '/' + row.id)
   if (res.code !== 200) { message.error(res.msg || '删除失败'); throw new Error(res.msg || '删除失败') }
   message.success('删除成功')
   await loadRows()
  },
 })
}
function changeStatus(row: Country,checked: unknown) {
 const status=checked===true?'0':'1'
 Modal.confirm({title:status==='0'?'启用记录':'停用记录',content:'停用仅限制新增引用，已有尺码组和业务保留。',okText:'确认',cancelText:'取消',onOk:async()=>{
  const res: any=await api.put(endpoint+'/'+row.id+'/status',{status})
  if(res.code!==200) { message.error(res.msg || '操作失败'); throw new Error(res.msg) }
  await loadRows()
 }})
}
onMounted(async () => { loadColumns(); await loadRows(); if (!manualWidths.value) await autoFit(); widthObserver = new ResizeObserver(() => { if (!manualWidths.value) void autoFit() }); const area = document.querySelector<HTMLElement>('.country-table .ant-table-container'); if (area) widthObserver.observe(area) })
onBeforeUnmount(() => widthObserver?.disconnect())
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <div class="head-filters"><a-input v-model:value="keyword" placeholder="搜索编号 / 名称 / 英文名称" allow-clear style="width:240px" @press-enter="search"><template #prefix><SearchOutlined style="color:#86909C" /></template></a-input><a-select v-model:value="status" allow-clear placeholder="状态" style="width:110px" :options="[{label:'启用',value:'0'},{label:'停用',value:'1'}]" @change="search" /></div>
    </template>
    <template #actions>
      <div class="filter-bar"><a-button v-if="permission('add')" type="primary" @click="openAdd"><PlusOutlined />新增</a-button><a-button type="primary" @click="search">查询</a-button><a-button @click="resetFilters">重置</a-button><a-button v-if="props.kind === 'size' && permission('edit')" :disabled="!selectedRowKeys.length" @click="openBatchTags">批量修改<span v-if="selectedRowKeys.length">（{{ selectedRowKeys.length }}）</span></a-button><span class="filter-spacer" />
        <a-dropdown v-model:open="columnOpen" :trigger="['click']"><a-button>⚙ 列设置</a-button><template #overlay><a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }"><div v-for="(col,index) in columnState" :key="col.key" class="col-set-item"><a-checkbox :checked="col.visible" @change="toggleColumn(col.key)">{{ titleOf(col.key) }}</a-checkbox><span><a-button type="text" size="small" :disabled="index === 0" @click="moveColumn(index,-1)">↑</a-button><a-button type="text" size="small" :disabled="index === columnState.length-1" @click="moveColumn(index,1)">↓</a-button></span></div><a-divider style="margin:6px 0" /><div class="col-set-foot"><a @click="resetColumns">恢复默认</a><a-button type="primary" size="small" @click="columnOpen=false">完成</a-button></div></a-card></template></a-dropdown>
      </div>
    </template>
    <a-table :columns="columns" :data-source="filteredRows" :loading="loading" :row-selection="rowSelection" row-key="id" size="middle" class="country-table zy-fill-table" table-layout="fixed" :scroll="{ x: scrollWidth, y: 'calc(100vh - 390px)' }" :pagination="{ current: pageNum, pageSize, total: filteredRows.length, showSizeChanger: true, showQuickJumper: true, showTotal: (n: number) => `共 ${n} 条`, onChange: (p: number, ps: number) => { pageNum=p; pageSize=ps } }">
      <template #bodyCell="{column,record,index}">
        <template v-if="column.key === 'index'">{{ (pageNum-1)*pageSize+index+1 }}</template>
        <template v-else-if="column.key === 'tagNames'"><a-space v-if="record.tagNames?.length" size="small" wrap><a-tag v-for="tag in record.tagNames" :key="tag">{{ tag }}</a-tag></a-space><span v-else>—</span></template>
        <template v-else-if="column.key === 'status'"><a-switch :checked="record.status === '0'" :disabled="!permission('edit')" checked-children="启用" un-checked-children="停用" @change="v => changeStatus(record as Country, v)" /></template>
        <template v-else-if="column.key === 'action'"><a-space><a-button v-if="permission('edit')" type="primary" size="small" @click="openEdit(record as Country)">编辑</a-button><a-button v-if="permission('delete')" danger size="small" @click="removeRow(record as Country)">删除</a-button></a-space></template>
      </template>
      <template #summary><a-table-summary fixed><a-table-summary-row><a-table-summary-cell :index="0" :col-span="columns.length" class="summary-cell">合计（筛选结果）　{{ filteredRows.length }} 条</a-table-summary-cell></a-table-summary-row></a-table-summary></template>
    </a-table>
    <a-modal :open="showDlg" :title="(isEdit ? '编辑' : '新增') + title" width="760px" :mask-closable="false" :keyboard="false" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save" @cancel="closeForm">
      <a-form layout="vertical"><a-row :gutter="16">
        <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="form.name" :maxlength="100" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="英文名称"><a-input v-model:value="form.name_en" :maxlength="200" /></a-form-item></a-col>
        <a-col v-if="props.kind === 'size'" :span="12"><a-form-item label="编码" required><a-input v-model:value="form.code" :maxlength="40" :placeholder="isEdit ? '' : '留空按当前自动编码规则生成'" /></a-form-item></a-col>
        <a-col v-else :span="12"><a-form-item label="编号" required><a-input v-model:value="form.code" :maxlength="40" :placeholder="isEdit ? '请输入尺码组编号' : '留空按系统自动编码规则生成，也可手动填写'" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="排序"><a-input-number v-model:value="form.sort_order" :precision="0" :min="0" :max="2147483647" style="width:100%" /></a-form-item></a-col>
        <a-col :span="24"><a-form-item label="类型标签"><TagSelect v-model="form.tagIds" :selected-options="form.tagOptions" :module-key="moduleKey" :module-name="title" /></a-form-item></a-col>
        <a-col v-if="props.kind === 'size-group'" :span="24"><a-form-item label="组内尺码" required>
          <div class="size-selection-panel">
            <div class="size-selection-head"><div><strong>已选 {{ form.sizeIds.length }} 个尺码</strong><span>可调整组内显示顺序，停用尺码仅保留已有引用。</span></div><a-button @click="openSizePicker">选择尺码</a-button></div>
            <a-empty v-if="!form.sizeIds.length" description="请选择组内尺码" :image="'simple'" />
            <div v-else class="size-order-list">
              <div v-for="(id,index) in form.sizeIds" :key="id" class="size-order-item">
                <span class="size-order-index">{{ index + 1 }}</span>
                <div class="size-order-name"><strong>{{ sizeOptions.find(item => String(item.id) === id)?.name || id }}</strong><span>{{ sizeOptions.find(item => String(item.id) === id)?.code }}<a-tag v-if="sizeOptions.find(item => String(item.id) === id)?.status !== '0'" color="default">已停用</a-tag></span></div>
                <a-button size="small" :disabled="index===0" @click="moveSize(index,-1)">上移</a-button>
                <a-button size="small" :disabled="index===form.sizeIds.length-1" @click="moveSize(index,1)">下移</a-button>
                <a-button size="small" danger @click="removeSize(id)">移除</a-button>
              </div>
            </div>
          </div>
        </a-form-item></a-col>
        <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="2000" :rows="3" /></a-form-item></a-col>
      </a-row></a-form>
    </a-modal>
    <a-modal v-if="props.kind === 'size-group'" :open="sizePickerOpen" title="选择组内尺码" width="760px" :mask-closable="false" :keyboard="false" ok-text="确定" cancel-text="取消" @ok="confirmSizePicker" @cancel="sizePickerOpen=false">
      <a-input v-model:value="sizePickerKeyword" allow-clear placeholder="按尺码名称搜索" style="margin-bottom:12px" />
      <a-table :columns="[{title:'编码',dataIndex:'code',key:'code',width:150},{title:'尺码名称',dataIndex:'name',key:'name'},{title:'英文名称',dataIndex:'name_en',key:'name_en'},{title:'状态',dataIndex:'status',key:'status',width:100}]" :data-source="filteredSizeOptions" :row-selection="sizePickerRowSelection" row-key="id" size="small" :pagination="{pageSize:8,showTotal:(count:number)=>`共 ${count} 条`}" :scroll="{y:360}">
        <template #bodyCell="{column,record}"><a-tag v-if="column.key==='status'" :color="record.status==='0'?'green':'default'">{{ record.status==='0'?'启用':'停用' }}</a-tag></template>
      </a-table>
      <div class="picker-selection-summary">已选 {{ sizePickerSelected.length }} 个；确认后保留现有顺序，新选择的尺码追加在末尾。</div>
    </a-modal>
    <a-modal v-if="props.kind === 'size'" :open="batchTagsOpen" title="批量修改" :confirm-loading="batchTagsSaving" :mask-closable="false" ok-text="保存" cancel-text="取消" @ok="saveBatchTags" @cancel="batchTagsOpen=false">
      <a-typography-text type="secondary">将所选标签统一应用到 {{ selectedRowKeys.length }} 条尺码；留空保存会清除这些尺码的类型标签。</a-typography-text>
      <a-form layout="vertical">
        <a-form-item label="类型标签">
          <TagSelect v-model="batchTagIds" :selected-options="batchTagOptions" :module-key="moduleKey" :module-name="title" placeholder="选择或输入要统一设置的标签" />
        </a-form-item>
      </a-form>
    </a-modal>
  </StandardListLayout>
</template>

<style scoped>
.head-filters,.filter-bar { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.size-selection-panel { border:1px solid #E5E8EF; border-radius:8px; padding:12px 16px; }
.size-selection-head { display:flex; align-items:center; justify-content:space-between; gap:12px; padding-bottom:10px; border-bottom:1px solid #F0F0F0; }
.size-selection-head > div,.size-order-name { display:flex; flex-direction:column; gap:3px; }
.size-selection-head span,.size-order-name > span,.picker-selection-summary { color:#86909C; font-size:12px; }
.size-order-list { padding-top:8px; }
.size-order-item { display:flex; align-items:center; gap:8px; min-height:42px; padding:4px 0; border-bottom:1px solid #F5F5F5; }
.size-order-index { display:inline-flex; align-items:center; justify-content:center; width:24px; height:24px; border-radius:50%; color:#165DFF; background:#E8F3FF; }
.size-order-name { flex:1; min-width:0; }
.size-order-name > span { display:flex; align-items:center; gap:6px; }
.picker-selection-summary { margin-top:10px; }
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
