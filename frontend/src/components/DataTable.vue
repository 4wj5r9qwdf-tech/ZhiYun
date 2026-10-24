<script setup lang="ts">
/**
 * 系统级数据表格组件（全站列表页共用，禁止每页各写一套 —— 前端规范 B.5.2）。
 *
 * 能力：
 * - 列配置（key/标题/是否数值合计/是否可排序）由调用方传入
 * - 排序：点列头升降序（前端对当前数据集排序；也可由后端 orderBy 承接）
 * - 列显隐：表头"设置列"弹层勾选（隐藏的是视图，数据还在）
 * - 列顺序：拖拽列头调换顺序
 * - 合计行：数值列求和，按当前筛选+排序后的数据集重算，贴底固定
 * - 配置持久化：按"列表页+用户"存 localStorage（key = storageKey）
 * - 行点击 / 操作列（slots）
 *
 * 用法：
 *   <DataTable
 *     storage-key="person_list"
 *     :columns="columns" :rows="rows" :loading="loading"
 *     @sort="(k,dir)=>loadList(k,dir)"
 *   >
 *     <template #cell-ops="{ row }">...</template>
 *   </DataTable>
 *   columns = [{ key, title, sortable?, sum? }]
 */
import { ref, computed, watch, onMounted } from 'vue'

interface Column {
  key: string
  title: string
  sortable?: boolean   // 可排序
  sum?: boolean        // 数值列，合计行求和
  width?: number
}

const props = withDefaults(defineProps<{
  storageKey: string          // 列配置持久化 key（"列表页+用户"，前端拼 userId）
  columns: Column[]
  rows: any[]
  loading?: boolean
  rowKey?: string            // 行唯一键，默认 id/user_id
  emptyText?: string
}>(), { loading: false, rowKey: 'user_id', emptyText: '暂无数据' })

const emit = defineEmits<{
  (e: 'sort', key: string, dir: 'asc' | 'desc'): void
  (e: 'rowClick', row: any): void
}>()

// ===== 列配置（顺序 + TopBar 缓存的当前登录人 userId），持久化 =====
type ColState = { key: string; visible: boolean }
const colState = ref<ColState[]>([])

// 存储 key = 列表页标识 + 当前用户（TopBar 加载 profile/info 时缓存 userId 到 localStorage）
// 保证：同一用户刷新/重登配置不丢；不同用户互不串
const fullKey = computed(() => {
  const uid = localStorage.getItem('userId') || 'anon'
  return `${props.storageKey}:${uid}`
})

function loadState() {
  try {
    const raw = localStorage.getItem(fullKey.value)
    if (raw) {
      const saved: ColState[] = JSON.parse(raw)
      const curKeys = new Set(props.columns.map(c => c.key))
      const ordered: ColState[] = []
      for (const s of saved) if (curKeys.has(s.key)) { ordered.push(s); curKeys.delete(s.key) }
      for (const c of props.columns) if (curKeys.has(c.key)) ordered.push({ key: c.key, visible: true })
      colState.value = ordered
      return
    }
  } catch { /* ignore */ }
  colState.value = props.columns.map(c => ({ key: c.key, visible: true }))
}
function saveState() {
  try { localStorage.setItem(fullKey.value, JSON.stringify(colState.value)) } catch { /* ignore */ }
}
onMounted(loadState)
watch(() => fullKey.value, loadState)

// 可见列（按 colState 顺序）
const visibleCols = computed(() =>
  colState.value.filter(s => s.visible).map(s => props.columns.find(c => c.key === s.key)!).filter(Boolean))

function toggleCol(key: string) {
  const s = colState.value.find(x => x.key === key)
  if (s) { s.visible = !s.visible; saveState() }
}
function showAll() { colState.value.forEach(s => s.visible = true); saveState() }

// ===== 排序（前端对当前数据集排序；同时 emit 给后端） =====
const sortKey = ref('')
const sortDir = ref<'asc' | 'desc'>('asc')

function onSort(col: Column) {
  if (!col.sortable) return
  if (sortKey.value === col.key) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else { sortKey.value = col.key; sortDir.value = 'asc' }
  emit('sort', col.key, sortDir.value)
}

const sortedRows = computed(() => {
  if (!sortKey.value) return props.rows
  const k = sortKey.value
  const dir = sortDir.value === 'asc' ? 1 : -1
  return [...props.rows].sort((a, b) => {
    const av = a[k], bv = b[k]
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir
    return String(av).localeCompare(String(bv), 'zh') * dir
  })
})

// ===== 合计行（数值列，按当前数据集） =====
const sumCols = computed(() => props.columns.filter(c => c.sum))
const sums = computed(() => {
  const m: Record<string, number> = {}
  for (const c of sumCols.value) {
    m[c.key] = sortedRows.value.reduce((s, r) => {
      const v = Number(r[c.key])
      return s + (Number.isFinite(v) ? v : 0)
    }, 0)
  }
  return m
})
function fmtSum(v: number) { return v.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }

// ===== 列顺序拖拽 =====
const dragKey = ref('')
const overKey = ref('')
function onColDragStart(key: string, e: DragEvent) { dragKey.value = key; e.dataTransfer?.setData('text/plain', key) }
function onColDragOver(key: string, e: DragEvent) { e.preventDefault(); overKey.value = key }
function onColDrop(key: string) {
  if (!dragKey.value || dragKey.value === key) { resetColDrag(); return }
  const arr = colState.value
  const from = arr.findIndex(s => s.key === dragKey.value)
  const to = arr.findIndex(s => s.key === key)
  if (from >= 0 && to >= 0) {
    const [m] = arr.splice(from, 1)
    arr.splice(to, 0, m)
    saveState()
  }
  resetColDrag()
}
function resetColDrag() { dragKey.value = ''; overKey.value = '' }

// 设置列弹层
const showColSet = ref(false)
</script>

<template>
  <div class="dt">
    <!-- 表头工具：设置列 -->
    <div class="dt-bar">
      <div class="dt-tip">共 {{ rows.length }} 条</div>
      <div class="dt-tools">
        <div class="colset-wrap">
          <button class="dt-btn" @click="showColSet = !showColSet">⚙ 设置列</button>
          <div v-if="showColSet" class="colset">
            <div class="colset-item" v-for="s in colState" :key="s.key">
              <label><input type="checkbox" :checked="s.visible" @change="toggleCol(s.key)" /> {{ columns.find(c => c.key === s.key)?.title }}</label>
            </div>
            <button class="dt-btn sm" @click="showAll">全选</button>
          </div>
        </div>
      </div>
    </div>

    <div class="dt-scroll">
      <table>
        <thead>
          <tr>
            <th v-for="c in visibleCols" :key="c.key"
              :class="{ sortable: c.sortable, dragging: dragKey === c.key, over: overKey === c.key && dragKey !== c.key }"
              :style="{ width: c.width }"
              draggable="true"
              @dragstart="onColDragStart(c.key, $event)"
              @dragover="onColDragOver(c.key, $event)"
              @drop="onColDrop(c.key)"
              @dragend="resetColDrag"
              @click="onSort(c)">
              {{ c.title }}
              <span v-if="c.sortable" class="sort-ico">{{ sortKey === c.key ? (sortDir === 'asc' ? '↑' : '↓') : '↕' }}</span>
            </th>
            <th v-if="$slots['cell-ops']" class="ops-th">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in sortedRows" :key="r[rowKey] ?? i" @click="emit('rowClick', r)">
            <td v-for="c in visibleCols" :key="c.key">
              <slot :name="'cell-' + c.key" :row="r" :value="r[c.key]">{{ r[c.key] ?? '-' }}</slot>
            </td>
            <td v-if="$slots['cell-ops']" class="ops-td" @click.stop>
              <slot name="cell-ops" :row="r" />
            </td>
          </tr>
          <tr v-if="sortedRows.length === 0 && !loading">
            <td :colspan="visibleCols.length + ($slots['cell-ops'] ? 1 : 0)" class="dt-empty">{{ emptyText }}</td>
          </tr>
          <tr v-if="loading">
            <td :colspan="visibleCols.length + ($slots['cell-ops'] ? 1 : 0)" class="dt-empty">加载中...</td>
          </tr>
        </tbody>
        <!-- 合计行：数值列求和，贴底 -->
        <tfoot v-if="sumCols.length > 0 && sortedRows.length > 0">
          <tr class="dt-sum">
            <td v-for="c in visibleCols" :key="c.key">
              <template v-if="c.sum">{{ fmtSum(sums[c.key] || 0) }}</template>
              <template v-else-if="c.key === visibleCols[0]?.key">合计</template>
            </td>
            <td v-if="$slots['cell-ops']"></td>
          </tr>
        </tfoot>
      </table>
    </div>
  </div>
</template>

<style scoped>
.dt { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); overflow: hidden; }
.dt-bar { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-bottom: 1px solid #F0F2F5; }
.dt-tip { font-size: 12px; color: #86909C; }
.dt-tools { display: flex; gap: 8px; }
.dt-btn { height: 28px; padding: 0 10px; background: #fff; border: 1px solid #E5E8EF; border-radius: 6px; font-size: 12px; color: #4E5969; cursor: pointer; }
.dt-btn:hover { border-color: #1E5EFF; color: #1E5EFF; }
.dt-btn.sm { height: 24px; font-size: 11px; margin-top: 6px; }
.colset-wrap { position: relative; }
.colset { position: absolute; right: 0; top: 32px; background: #fff; border: 1px solid #E5E8EF; border-radius: 8px; box-shadow: 0 8px 24px rgba(29,33,41,.12); padding: 10px; z-index: 20; min-width: 160px; max-height: 300px; overflow: auto; }
.colset-item { padding: 3px 0; font-size: 13px; }
.colset-item label { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #4E5969; }

.dt-scroll { overflow: auto; max-height: 65vh; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #F0F2F5; white-space: nowrap; }
th { background: #F7F8FA; color: #4E5969; font-weight: 600; position: sticky; top: 0; z-index: 2; user-select: none; }
th.sortable { cursor: pointer; }
th.sortable:hover { color: #1E5EFF; }
th.dragging { opacity: .5; }
th.over { box-shadow: inset 3px 0 0 #1E5EFF; }
.sort-ico { color: #C9CDD4; font-size: 11px; margin-left: 2px; }
tbody tr { cursor: default; }
tbody tr:hover { background: #F5F8FF; }
.dt-empty { text-align: center; color: #86909C; padding: 32px; }
.ops-th, .ops-td { text-align: center; }
.ops-td a { color: #1E5EFF; cursor: pointer; margin: 0 6px; }
.ops-td a:hover { text-decoration: underline; }

/* 合计行：浅底加粗贴底 */
.dt-sum td { background: #F0F4FF; font-weight: 600; color: #1D2129; border-top: 2px solid #1E5EFF; border-bottom: none; position: sticky; bottom: 0; z-index: 1; }
</style>
