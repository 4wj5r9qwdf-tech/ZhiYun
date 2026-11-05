<script setup lang="ts">
/**
 * 基础档案复用表格：沿用功能模板工具栏、列设置、拖拽列宽/顺序、序号与满高分页。
 * Shared foundation table following the approved template, with per-user column preferences.
 */
import '../assets/standard-list-fill.css'
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
export type FoundationColumn = { key: string; title: string; width?: number; numeric?: boolean }
const props = defineProps<{ storageKey: string; columns: FoundationColumn[]; rows: any[]; loading?: boolean; selectedKeys?: string[]; actionWidth?: number }>()
defineSlots<{ actions(): any; action(props: {row:any}): any; cell(props: {column:any;row:any}): any }>()
const emit = defineEmits<{ (e: 'update:selectedKeys', keys: string[]): void }>()
let stopResize: (() => void) | undefined
let suppressClickUntil = 0
const state = ref<{ key: string; visible: boolean; width: number }[]>([])
const actionWidth = ref(props.actionWidth ?? 220)
const tableRoot = ref<HTMLElement>()
const manualWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const page = ref(1), pageSize = ref(20), settings = ref(false), dragged = ref('')
const preferenceKey = computed(() => `${props.storageKey}:${localStorage.getItem('userId') || 'anon'}`)
/** 恢复当前用户列偏好，新增列自动补齐。 / Restore only known columns and append newly introduced fields. */
function restore() {
  try { manualWidths.value = localStorage.getItem(`${preferenceKey.value}:manual-widths`) === '1' || !!localStorage.getItem(`${preferenceKey.value}:action-width`) } catch { manualWidths.value = false }
  try { actionWidth.value = Math.max(120, Number(localStorage.getItem(`${preferenceKey.value}:action-width`)) || props.actionWidth || 220) } catch { actionWidth.value = props.actionWidth ?? 220 }
  let saved: typeof state.value = []
  try { saved = JSON.parse(localStorage.getItem(preferenceKey.value) || '[]') } catch { /* 损坏偏好回退默认。 / Fall back on corrupt preferences. */ }
  if (!Array.isArray(saved)) saved = []
  const keys = new Set(props.columns.map(c => c.key))
  state.value = saved.filter(c => keys.delete(c.key)).map(c => ({ key: c.key, visible: c.visible !== false, width: Math.max(80, Number(c.width) || 160) }))
  props.columns.filter(c => keys.has(c.key)).forEach(c => state.value.push({ key: c.key, visible: true, width: c.width || 160 }))
  void scheduleAutoFit()
}
/** 持久化仅保存视图设置，失败不影响业务。 / Persist non-sensitive per-user view preferences. */
function persist() { try { localStorage.setItem(preferenceKey.value, JSON.stringify(manualWidths.value ? state.value : state.value.map(({key,visible})=>({key,visible})))) } catch { /* 存储不可用不阻塞业务。 / Storage is optional. */ } }
/** 未手动调整时将剩余空间分配给业务列，操作列保持紧凑。 / Fit business columns to the viewport while preserving the operation width. */
async function scheduleAutoFit() {
  await nextTick()
  if(manualWidths.value)return
  const available=tableRoot.value?.querySelector<HTMLElement>('.ant-table-body')?.clientWidth || tableRoot.value?.querySelector<HTMLElement>('.ant-table-container')?.clientWidth || 0
  if(!available)return
  const visible=state.value.filter(c=>c.visible)
  const minWidth=(key:string)=>Math.max(90,Array.from(props.columns.find(c=>c.key===key)?.title||key).length*16+48)
  const operation=props.actionWidth ?? 220
  const required=(props.selectedKeys?48:0)+64+operation+visible.reduce((sum,c)=>sum+minWidth(c.key),0)
  const extra=visible.length?Math.max(0,Math.floor((available-2-required)/visible.length)):0
  state.value.forEach(c=>{c.width=minWidth(c.key)+(c.visible?extra:0)})
  actionWidth.value=operation
}
/** 恢复自动列宽，并保留列顺序与显隐。 / Clear manual widths without losing column visibility or order. */
function autoFitColumns(){manualWidths.value=false;try{localStorage.removeItem(`${preferenceKey.value}:manual-widths`);localStorage.removeItem(`${preferenceKey.value}:action-width`)}catch{/* 保持界面可用。 / Keep the view usable. */}persist();void scheduleAutoFit()}
/** 重置到列定义默认值。 / Reset the current view to configured defaults. */
function resetColumns() { try { localStorage.removeItem(preferenceKey.value); localStorage.removeItem(`${preferenceKey.value}:action-width`);localStorage.removeItem(`${preferenceKey.value}:manual-widths`) } catch { /* 继续恢复内存默认值。 / Continue with in-memory defaults. */ }; restore() }
/** 调整列顺序，拖拽和设置面板共用。 / Reorder columns consistently from both controls. */
function move(from: number, to: number) { if (from < 0 || to < 0 || to >= state.value.length) return; const [item] = state.value.splice(from, 1); if (item) state.value.splice(to, 0, item); persist() }
/** 将表头右缘拖动转换为可持久化列宽。 / Resize from the header edge without triggering sorting. */
function resize(key: string, event: MouseEvent) {
  const cell = event.currentTarget as HTMLElement
  if (event.clientX < cell.getBoundingClientRect().right - 14) return
  const item = state.value.find(c => c.key === key); if (!item) return
  manualWidths.value=true;try{localStorage.setItem(`${preferenceKey.value}:manual-widths`,'1')}catch{/* 存储不可用不阻塞拖动。 / Resizing works without storage. */}
  const origin = event.clientX, width = item.width
  const onMove = (e: MouseEvent) => { item.width = Math.max(80, width + e.clientX - origin) }
  stopResize?.()
  const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp); stopResize=undefined; suppressClickUntil=Date.now()+300; persist() }
  stopResize=onUp
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp); event.preventDefault(); event.stopPropagation()
}
/** 操作列宽度按用户保存，适配不同页面的按钮数量。 / Persist the operation column width per user and page. */
function resizeAction(event: MouseEvent) {
  const cell=event.currentTarget as HTMLElement
  if(event.clientX<cell.getBoundingClientRect().right-14)return
  manualWidths.value=true;try{localStorage.setItem(`${preferenceKey.value}:manual-widths`,'1')}catch{/* 存储不可用不阻塞拖动。 / Resizing works without storage. */}
  const origin=event.clientX, width=actionWidth.value
  const onMove=(e:MouseEvent)=>{actionWidth.value=Math.max(120,width+e.clientX-origin)}
  stopResize?.()
  const onUp=()=>{document.removeEventListener('mousemove',onMove);document.removeEventListener('mouseup',onUp);stopResize=undefined;persist();try{localStorage.setItem(`${preferenceKey.value}:action-width`,String(actionWidth.value))}catch{/* 不影响列表使用。 / Storage is optional. */}}
  stopResize=onUp;document.addEventListener('mousemove',onMove);document.addEventListener('mouseup',onUp);event.preventDefault();event.stopPropagation()
}
const columns = computed(() => [
  { key: 'index', title: '序号', width: 64, fixed: 'left' as const },
  ...state.value.filter(c => c.visible).map(c => {
    const definition = props.columns.find(d => d.key === c.key)!
    return { key: c.key, dataIndex: c.key, title: definition.title, width: c.width, ellipsis: true,
      sorter: (a: any, b: any) => definition.numeric ? Number(a[c.key] || 0) - Number(b[c.key] || 0) : String(a[c.key] ?? '').localeCompare(String(b[c.key] ?? ''), 'zh-CN'),
      customHeaderCell: () => ({ draggable: true, onClickCapture: (event: MouseEvent) => { if(Date.now()<suppressClickUntil){event.preventDefault();event.stopPropagation()} }, onMousedown: (event: MouseEvent) => resize(c.key, event),
        onDragstart: () => { dragged.value = c.key }, onDragover: (event: DragEvent) => event.preventDefault(),
        onDrop: () => { move(state.value.findIndex(s => s.key === dragged.value), state.value.findIndex(s => s.key === c.key)); dragged.value = '' } }) }
  }),
  { key: 'action', title: '操作', width: actionWidth.value, fixed: 'right' as const, customHeaderCell: () => ({ onMousedown: resizeAction }) },
])
const selection = computed(() => props.selectedKeys ? { selectedRowKeys: props.selectedKeys, preserveSelectedRowKeys: true, onChange: (keys: (string | number)[]) => emit('update:selectedKeys', keys.map(String)) } : undefined)
watch(preferenceKey, restore, { immediate: true })
watch(() => props.rows, () => { page.value = 1 })
onMounted(async()=>{await nextTick();const container=tableRoot.value?.querySelector<HTMLElement>('.ant-table-container');if(container){widthObserver=new ResizeObserver(()=>{void scheduleAutoFit()});widthObserver.observe(container)}void scheduleAutoFit()})
onBeforeUnmount(() => {stopResize?.();widthObserver?.disconnect()})
</script>
<template>
  <div ref="tableRoot" class="foundation-table">
    <div class="foundation-actions">
      <slot name="actions" />
      <span class="spacer" />
      <a-button @click="autoFitColumns">自适应列宽</a-button>
      <a-dropdown v-model:open="settings" :trigger="['click']">
        <a-button>⚙ 列设置</a-button>
        <template #overlay><a-card :body-style="{ padding: '12px' }" style="min-width:240px">
          <div v-for="(col,index) in state" :key="col.key" class="column-setting"><a-checkbox v-model:checked="col.visible" @change="persist();scheduleAutoFit()">{{ props.columns.find(c => c.key === col.key)?.title }}</a-checkbox><span><a-button type="text" size="small" :disabled="index===0" @click="move(index,index-1)">↑</a-button><a-button type="text" size="small" :disabled="index===state.length-1" @click="move(index,index+1)">↓</a-button></span></div>
          <a-divider style="margin:8px 0" /><a-space><a-button size="small" @click="resetColumns">恢复默认</a-button><a-button type="primary" size="small" @click="settings=false">完成</a-button></a-space>
        </a-card></template>
      </a-dropdown>
    </div>
    <a-table class="zy-fill-table" :columns="columns" :data-source="rows" :loading="loading" row-key="id" size="middle" table-layout="fixed" :row-selection="selection" :scroll="{ x: (selection ? 48 : 0)+64+actionWidth+state.filter(c=>c.visible).reduce((sum,c)=>sum+c.width,0), y: '100%' }" :pagination="{ current:page, pageSize, showSizeChanger:true, showQuickJumper:true, showTotal:(total:number)=>`共 ${total} 条`, onChange:(p:number,size:number)=>{page=p;pageSize=size} }">
      <template #bodyCell="{ column,record,index }"><template v-if="column.key==='index'">{{ (page-1)*pageSize+index+1 }}</template><slot v-else-if="column.key==='action'" name="action" :row="record" /><slot v-else name="cell" :column="column" :row="record"><span>{{ record[String(column.key)] ?? '—' }}</span></slot></template>
    </a-table>
  </div>
</template>
<style scoped>
.foundation-table{height:100%;min-height:0;display:flex;flex-direction:column}.foundation-actions{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px}.spacer{flex:1}.column-setting{display:flex;justify-content:space-between;align-items:center;gap:16px}.foundation-table :deep(th.ant-table-cell){position:relative}.foundation-table :deep(th.ant-table-cell::after){content:'';position:absolute;right:0;top:0;width:12px;height:100%;cursor:col-resize}
</style>
