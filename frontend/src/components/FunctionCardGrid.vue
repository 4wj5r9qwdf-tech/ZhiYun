<script setup lang="ts">
/**
 * 系统级功能卡片网格组件（可复用，别重复造轮子）
 * 能力：拖拽排序 / 自由拖拽图标像素大小(80-320 min/max，安卓小组件式) / 置顶 / 加入工作台 / 待办红点
 * 配置按用户持久化到 sys_user_menu_config（后端 /system/profile/menu-config）。
 *
 * 用法（新功能模块直接复用）：
 *   <FunctionCardGrid :module-key="'system'" :cards="cards" />
 *   cards = [{ key, title, desc, icon, route }]
 *   组件内部自动：读配置→应用排序/大小/置顶/工作台→拖拽/改大小/置顶/加工作台自动保存
 *
 * 需要工作台模式（只读展示，不带编辑手柄）：
 *   <FunctionCardGrid :module-key="'workbench'" :cards="benchCards" :readonly="true" :todo="todoByMenu" />
 */
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

interface Card {
  key: string
  title: string
  desc?: string
  icon?: string     // 图标字符（emoji/字符），与 iconUrl 二选一
  iconUrl?: string  // 图标图片URL（优先于 icon；上传到 MinIO 后的地址）
  route?: string
}

const props = withDefaults(defineProps<{
  moduleKey: string       // 模块标识（sys_user_menu_config.menu_key 前缀/记录用，如 'system'）
  cards: Card[]           // 功能卡片列表（组件会注入 sort/iconSize/isTop/inWorkbench）
  readonly?: boolean      // 工作台只读模式：不显示置顶/加工作台/拖拽手柄（仅展示）
  todo?: Record<string, number>  // 功能级待办数（key→count），无待办不显示红点
}>(), { readonly: false })

const router = useRouter()

// 卡片运行态（配置）
type CardState = Card & { sort: number; iconSize: number; isTop: boolean; inWorkbench: boolean }
const state = ref<CardState[]>([])

// 拖拽排序
const dragIndex = ref(-1)
const overIndex = ref(-1)
// 图标大小拖拽
const resizing = ref<string | null>(null)
let resizeStartX = 0
let resizeStartSize = 0

// min/max 图标像素大小（安卓小组件式限制）；如需调整只改这三行
const MIN_SIZE = 80      // 最小
const MAX_SIZE = 320     // 最大
const DEFAULT_SIZE = 180 // 默认
// 图标占卡片宽度比例（emoji 字号 = iconSize × ICON_RATIO）；图片图标按 contain 缩放
const ICON_RATIO = 0.45

onMounted(async () => {
  state.value = props.cards.map((c, i) => ({
    ...c, sort: i, iconSize: DEFAULT_SIZE, isTop: false, inWorkbench: false,
  }))
  await loadConfig()
  bindResize()
})

onBeforeUnmount(unbindResize)

// 读用户配置，应用到卡片
async function loadConfig() {
  try {
    const res: any = await api.get('/system/profile/menu-config')
    if (res.code === 200) {
      const map: Record<string, any> = {}
      for (const r of res.data || []) map[r.menu_key] = r
      for (const c of state.value) {
        const m = map[c.key]
        if (m) { c.sort = m.sort; c.iconSize = m.icon_size; c.isTop = m.is_top === '1'; c.inWorkbench = m.in_workbench === '1' }
      }
    }
  } catch { /* ignore */ }
}

// 排序：置顶优先，其次 sort
const sorted = computed(() => {
  const arr = [...state.value]
  arr.sort((a, b) => (Number(b.isTop) - Number(a.isTop)) || (a.sort - b.sort))
  return arr
})

// 保存单卡配置
async function saveCard(c: CardState) {
  try {
    await api.put('/system/profile/menu-config', {
      menuKey: c.key, sort: c.sort, iconSize: c.iconSize,
      isTop: c.isTop ? '1' : '0', inWorkbench: c.inWorkbench ? '1' : '0',
    })
  } catch { /* ignore */ }
}

function toggleTop(c: CardState) { c.isTop = !c.isTop; saveCard(c) }
function toggleWorkbench(c: CardState) { c.inWorkbench = !c.inWorkbench; saveCard(c) }

// ===== 拖拽排序 =====
function onDragStart(i: number, e: DragEvent) { dragIndex.value = i; e.dataTransfer?.setData('text/plain', String(i)) }
function onDragOver(i: number, e: DragEvent) { e.preventDefault(); overIndex.value = i }
function onDrop(i: number) {
  const from = dragIndex.value
  if (from < 0 || from === i) { resetDrag(); return }
  // 在 state 中按可见顺序交换 sort
  const arr = sorted.value
  const [moved] = arr.splice(from, 1)
  arr.splice(i, 0, moved)
  arr.forEach((c, idx) => { c.sort = idx })
  state.value.forEach(c => saveCard(c))
  resetDrag()
}
function resetDrag() { dragIndex.value = -1; overIndex.value = -1 }

// ===== 自由拖拽图标像素大小（min/max，其他卡片自动重排）=====
function startResize(c: CardState, e: MouseEvent) {
  e.stopPropagation(); e.preventDefault()
  resizing.value = c.key
  resizeStartX = e.clientX
  resizeStartSize = c.iconSize
}
function bindResize() { window.addEventListener('mousemove', onResizeMove); window.addEventListener('mouseup', endResize) }
function unbindResize() { window.removeEventListener('mousemove', onResizeMove); window.removeEventListener('mouseup', endResize) }
function onResizeMove(e: MouseEvent) {
  if (!resizing.value) return
  const c = state.value.find(x => x.key === resizing.value)
  if (!c) return
  let size = resizeStartSize + (e.clientX - resizeStartX)
  size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size))
  c.iconSize = size
}
function endResize() {
  if (!resizing.value) return
  const c = state.value.find(x => x.key === resizing.value)
  if (c) saveCard(c)
  resizing.value = null
}

function openCard(c: CardState) {
  if (c.route) router.push({ name: c.route })
}
</script>

<template>
  <div class="fc-grid">
    <div
      v-for="(c, i) in sorted"
      :key="c.key"
      class="fc-card"
      :class="{ top: c.isTop, dragging: dragIndex === i, over: overIndex === i && dragIndex !== i, readonly }"
      :style="{ '--sz': c.iconSize + 'px' }"
      :draggable="!readonly"
      @dragstart="onDragStart(i, $event)"
      @dragover="onDragOver(i, $event)"
      @drop="onDrop(i)"
      @dragend="resetDrag"
      @click="openCard(c)"
    >
      <i v-if="(props.todo?.[c.key] || 0) > 0" class="fc-todo">{{ props.todo![c.key] }}</i>
      <span v-if="c.isTop" class="fc-top-tag">置顶</span>

      <div class="fc-ico" :style="{ width: c.iconSize + 'px', height: c.iconSize + 'px' }">
        <img v-if="c.iconUrl" class="fc-ico-img" :src="c.iconUrl" :alt="c.title" />
        <span v-else class="fc-ico-txt" :style="{ fontSize: Math.round(c.iconSize * ICON_RATIO) + 'px' }">{{ c.icon }}</span>
      </div>
      <div class="fc-title">{{ c.title }}</div>
      <div v-if="c.desc" class="fc-desc">{{ c.desc }}</div>

      <div v-if="!readonly" class="fc-actions">
        <button class="fc-act" :class="{ on: c.isTop }" @click.stop="toggleTop(c)">置顶</button>
        <button class="fc-act" :class="{ on: c.inWorkbench }" @click.stop="toggleWorkbench(c)">{{ c.inWorkbench ? '已加工作台' : '加工作台' }}</button>
      </div>

      <div v-if="!readonly" class="fc-resize" @mousedown="startResize(c, $event)">◢</div>
    </div>
  </div>
</template>

<style scoped>
.fc-grid { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
.fc-card { position: relative; width: var(--sz, 180px); background: #fff; border-radius: 10px; padding: 20px 16px; cursor: grab; box-shadow: 0 2px 8px rgba(29,33,41,.06); border: 1px solid transparent; transition: box-shadow .2s; user-select: none; }
.fc-card.readonly { cursor: pointer; }
.fc-card:hover { border-color: #1E5EFF; box-shadow: 0 4px 16px rgba(30,94,255,.12); }
.fc-card.dragging { opacity: .5; }
.fc-card.over { border-color: #1E5EFF; box-shadow: 0 0 0 2px rgba(30,94,255,.25); }
.fc-card.top { border: 1px solid #FF9500; }
.fc-top-tag { position: absolute; top: 8px; left: 8px; background: #FF9500; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.fc-todo { position: absolute; top: 10px; right: 10px; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; padding: 0 4px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; z-index: 2; }
.fc-ico { border-radius: 10px; background: #E8F0FF; display: flex; align-items: center; justify-content: center; margin-bottom: 12px; overflow: hidden; }
.fc-ico-txt { line-height: 1; }
.fc-ico-img { width: 100%; height: 100%; object-fit: contain; }
.fc-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 4px; }
.fc-desc { font-size: 12px; color: #86909C; margin-bottom: 8px; }
.fc-actions { display: flex; gap: 6px; margin-top: 8px; }
.fc-act { flex: 1; height: 26px; border: 1px solid #E5E8EF; background: #fff; border-radius: 6px; font-size: 11px; color: #4E5969; cursor: pointer; }
.fc-act:hover { border-color: #1E5EFF; color: #1E5EFF; }
.fc-act.on { background: #E8F0FF; border-color: #1E5EFF; color: #1E5EFF; }
.fc-resize { position: absolute; right: 6px; bottom: 6px; width: 18px; height: 18px; cursor: nwse-resize; color: #C9CDD4; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.fc-resize:hover { color: #1E5EFF; }
</style>
