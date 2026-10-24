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

// 卡片运行态（配置）：宽高独立（自由拉伸，非等比）
type CardState = Card & { sort: number; cardW: number; cardH: number; isTop: boolean; inWorkbench: boolean }
const state = ref<CardState[]>([])

// 拖拽排序
const dragIndex = ref(-1)
const overIndex = ref(-1)
// 尺寸拖拽（axis: w=宽度 h=高度 wh=宽高同时，右下角）
const resizing = ref<{ key: string; axis: 'w' | 'h' | 'wh' } | null>(null)
let resizeStartX = 0
let resizeStartY = 0
let resizeStartW = 0
let resizeStartH = 0

// 卡片尺寸边界（宽高独立，自由拉伸，安卓小组件式）；要调整只改这些常量
const MIN_W = 120, MIN_H = 100       // 最小宽/高（保证图标+标题+按钮放得下，不挤成细条）
const MAX_W = 480, MAX_H = 320       // 最大宽/高
const DEFAULT_W = 180, DEFAULT_H = 120 // 默认宽/高
// 图标高度 = 卡片高度 × ICON_RATIO（随卡片自适应）；emoji 字号 = 图标高度 × 0.7
const ICON_RATIO = 0.45

onMounted(async () => {
  state.value = props.cards.map((c, i) => ({
    ...c, sort: i, cardW: DEFAULT_W, cardH: DEFAULT_H, isTop: false, inWorkbench: false,
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
        if (m) {
          c.sort = m.sort
          c.isTop = m.is_top === '1'
          c.inWorkbench = m.in_workbench === '1'
          // 宽高独立（新）；兼容旧单值 icon_size（沿用为宽，高按比例）
          if (m.card_w && m.card_h) { c.cardW = m.card_w; c.cardH = m.card_h }
          else if (m.icon_size) { c.cardW = m.icon_size; c.cardH = Math.round(m.icon_size * 0.65) }
        }
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
      menuKey: c.key, sort: c.sort, cardW: c.cardW, cardH: c.cardH,
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

// ===== 自由拉伸尺寸（宽高独立，非等比；右把手=宽，下把手=高，右下角=宽高）=====
function startResize(c: CardState, axis: 'w' | 'h' | 'wh', e: MouseEvent) {
  e.stopPropagation(); e.preventDefault()
  resizing.value = { key: c.key, axis }
  resizeStartX = e.clientX
  resizeStartY = e.clientY
  resizeStartW = c.cardW
  resizeStartH = c.cardH
}
function bindResize() { window.addEventListener('mousemove', onResizeMove); window.addEventListener('mouseup', endResize) }
function unbindResize() { window.removeEventListener('mousemove', onResizeMove); window.removeEventListener('mouseup', endResize) }
function onResizeMove(e: MouseEvent) {
  if (!resizing.value) return
  const c = state.value.find(x => x.key === resizing.value!.key)
  if (!c) return
  const { axis } = resizing.value
  if (axis === 'w' || axis === 'wh') {
    c.cardW = Math.max(MIN_W, Math.min(MAX_W, resizeStartW + (e.clientX - resizeStartX)))
  }
  if (axis === 'h' || axis === 'wh') {
    c.cardH = Math.max(MIN_H, Math.min(MAX_H, resizeStartH + (e.clientY - resizeStartY)))
  }
}
function endResize() {
  if (!resizing.value) return
  const c = state.value.find(x => x.key === resizing.value!.key)
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
      :style="{ '--w': c.cardW + 'px', '--h': c.cardH + 'px' }"
      :draggable="!readonly"
      @dragstart="onDragStart(i, $event)"
      @dragover="onDragOver(i, $event)"
      @drop="onDrop(i)"
      @dragend="resetDrag"
      @click="openCard(c)"
    >
      <i v-if="(props.todo?.[c.key] || 0) > 0" class="fc-todo">{{ props.todo![c.key] }}</i>
      <span v-if="c.isTop" class="fc-top-tag">置顶</span>

      <div class="fc-ico" :style="{ height: Math.round(c.cardH * ICON_RATIO) + 'px' }">
        <img v-if="c.iconUrl" class="fc-ico-img" :src="c.iconUrl" :alt="c.title" />
        <span v-else class="fc-ico-txt" :style="{ fontSize: Math.round(c.cardH * ICON_RATIO * 0.7) + 'px' }">{{ c.icon }}</span>
      </div>
      <div class="fc-title" :title="c.title">{{ c.title }}</div>
      <div v-if="c.desc" class="fc-desc" :title="c.desc">{{ c.desc }}</div>

      <div v-if="!readonly" class="fc-actions">
        <button class="fc-act" :class="{ on: c.isTop }" @click.stop="toggleTop(c)">置顶</button>
        <button class="fc-act" :class="{ on: c.inWorkbench }" @click.stop="toggleWorkbench(c)">{{ c.inWorkbench ? '已加工作台' : '加工作台' }}</button>
      </div>

      <!-- 三个拉伸手柄：右=宽，下=高，右下角=宽高（自由拉伸，非等比） -->
      <div v-if="!readonly" class="fc-resize fc-resize-w" @mousedown="startResize(c, 'w', $event)"></div>
      <div v-if="!readonly" class="fc-resize fc-resize-h" @mousedown="startResize(c, 'h', $event)"></div>
      <div v-if="!readonly" class="fc-resize fc-resize-wh" @mousedown="startResize(c, 'wh', $event)">◢</div>
    </div>
  </div>
</template>

<style scoped>
.fc-grid { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
.fc-card { position: relative; display: flex; flex-direction: column; width: var(--w, 180px); min-height: var(--h, 120px); background: #fff; border-radius: 10px; padding: 14px 14px; cursor: grab; box-shadow: 0 2px 8px rgba(29,33,41,.06); border: 1px solid transparent; transition: box-shadow .2s; user-select: none; box-sizing: border-box; }
.fc-card.readonly { cursor: pointer; }
.fc-card:hover { border-color: #1E5EFF; box-shadow: 0 4px 16px rgba(30,94,255,.12); }
.fc-card.dragging { opacity: .5; }
.fc-card.over { border-color: #1E5EFF; box-shadow: 0 0 0 2px rgba(30,94,255,.25); }
.fc-card.top { border: 1px solid #FF9500; }
.fc-top-tag { position: absolute; top: 8px; left: 8px; background: #FF9500; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.fc-todo { position: absolute; top: 10px; right: 10px; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; padding: 0 4px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; z-index: 2; }
.fc-ico { border-radius: 10px; background: #E8F0FF; display: flex; align-items: center; justify-content: center; margin-bottom: 10px; overflow: hidden; flex-shrink: 0; }
.fc-ico-txt { line-height: 1; }
.fc-ico-img { max-width: 100%; max-height: 100%; object-fit: contain; }
.fc-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fc-desc { font-size: 12px; color: #86909C; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fc-actions { display: flex; gap: 6px; margin-top: auto; }
.fc-act { flex: 1; height: 26px; border: 1px solid #E5E8EF; background: #fff; border-radius: 6px; font-size: 11px; color: #4E5969; cursor: pointer; }
.fc-act:hover { border-color: #1E5EFF; color: #1E5EFF; }
.fc-act.on { background: #E8F0FF; border-color: #1E5EFF; color: #1E5EFF; }
/* 拉伸手柄：右=宽(ew)，下=高(ns)，右下角=宽高(nwse) */
.fc-resize { position: absolute; z-index: 3; }
.fc-resize-w { right: -3px; top: 20%; bottom: 20%; width: 8px; cursor: ew-resize; }
.fc-resize-h { left: 20%; right: 20%; bottom: -3px; height: 8px; cursor: ns-resize; }
.fc-resize-wh { right: 4px; bottom: 4px; width: 16px; height: 16px; cursor: nwse-resize; color: #C9CDD4; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.fc-resize-w:hover, .fc-resize-h:hover, .fc-resize-wh:hover { color: #1E5EFF; }
</style>
