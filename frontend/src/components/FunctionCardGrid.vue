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
import * as AntIcons from '@ant-design/icons-vue'

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

// icon 字段兼容两种：antd 图标组件名字符串（如 'TeamOutlined'）→ 渲染组件；否则当 emoji/字符显示
function resolveIcon(name?: string) {
  if (!name) return null
  if (/Outlined$|Filled$|TwoTone$/.test(name)) return (AntIcons as any)[name] || null
  return null
}

// 卡片运行态（配置）：宽高独立（自由拉伸，非等比）
type CardState = Card & { sort: number; cardW: number; cardH: number; isTop: boolean; inWorkbench: boolean; locked: boolean }
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

// 等比锁定：每张卡各自独立（默认锁定=正方形档位；点该卡的锁解开成长方形档位）
const LOCK_DEFAULT = true

// ===== 卡片尺寸：档位制（对齐安卓小组件，不做任意像素，保证网格整齐）=====
// 单元 = UNIT（卡片内容）+ GAP（间距）构成一个网格格子；卡片尺寸只能取「格子数」的整数倍。
// 例：1×1=单格(正方形)、2×1=两格宽一格高(横长)、1×2=一格宽两格高(竖长)、2×2=田字(大正方形)。
const UNIT = 150           // 单个格子的内容边长
const GAP = 14             // 格子间距（也是卡片间隙）
const CELL = UNIT + GAP    // 一个完整格子占位（含间距）
const MIN_COLS = 1, MAX_COLS = 3   // 宽/高可取的格子数档位（1-3 格）
const MIN_ROWS = 1, MAX_ROWS = 3

// 档位 → 像素（内容宽高，卡片本身按内容尺寸，grid gap 负责间距）
function colsToW(cols: number) { return cols * UNIT + (cols - 1) * GAP }
function rowsToH(rows: number) { return rows * UNIT + (rows - 1) * GAP }
// 像素 → 最近档位
function wToCols(w: number) { return Math.max(MIN_COLS, Math.min(MAX_COLS, Math.round((w + GAP) / CELL))) }
function hToRows(h: number) { return Math.max(MIN_ROWS, Math.min(MAX_ROWS, Math.round((h + GAP) / CELL))) }

const DEFAULT_W = colsToW(1), DEFAULT_H = rowsToH(1) // 默认 = 1×1 正方形单格

onMounted(async () => {
  state.value = props.cards.map((c, i) => ({
    ...c, sort: i, cardW: DEFAULT_W, cardH: DEFAULT_H, isTop: false, inWorkbench: false, locked: LOCK_DEFAULT,
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
          c.locked = m.locked !== '0'   // 默认锁定；'0'=解锁
          // 宽高独立（新）；兼容旧单值 icon_size（沿用为宽，高按比例）
          if (m.card_w && m.card_h) { c.cardW = m.card_w; c.cardH = m.card_h }
          else if (m.icon_size) { c.cardW = m.icon_size; c.cardH = Math.round(m.icon_size * 0.65) }
          // 兼容旧配置：把像素尺寸吸附到最近档位（防止旧的任意像素越界/对不齐）
          c.cardW = colsToW(wToCols(c.cardW))
          c.cardH = rowsToH(hToRows(c.cardH))
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
      isTop: c.isTop ? '1' : '0', inWorkbench: c.inWorkbench ? '1' : '0', locked: c.locked ? '1' : '0',
    })
  } catch { /* ignore */ }
}

function toggleTop(c: CardState) { c.isTop = !c.isTop; saveCard(c) }
function toggleWorkbench(c: CardState) { c.inWorkbench = !c.inWorkbench; saveCard(c) }
function toggleLock(c: CardState) { c.locked = !c.locked; saveCard(c) }

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

// ===== 档位缩放（对齐安卓小组件：只在几档之间切换，不做任意像素）=====
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
  if (c.locked) {
    // 等比（正方形档位）：宽=高，取 1×1 / 2×2 / 3×3
    const delta = axis === 'h' ? (e.clientY - resizeStartY) : (e.clientX - resizeStartX)
    const startSide = Math.max(resizeStartW, resizeStartH)
    const n = Math.max(MIN_COLS, Math.min(MAX_COLS, wToCols(startSide + delta)))
    c.cardW = colsToW(n)
    c.cardH = rowsToH(n)
  } else {
    // 自由（成长方形档位）：宽高各自取最近档（如 2×1、1×2、3×2）
    if (axis === 'w' || axis === 'wh') {
      c.cardW = colsToW(wToCols(resizeStartW + (e.clientX - resizeStartX)))
    }
    if (axis === 'h' || axis === 'wh') {
      c.cardH = rowsToH(hToRows(resizeStartH + (e.clientY - resizeStartY)))
    }
  }
}
function endResize() {
  if (!resizing.value) return
  const c = state.value.find(x => x.key === resizing.value!.key)
  if (c) saveCard(c)
  resizing.value = null
  // 阻止本次拖拽收尾的 click 冒泡到卡片（避免拉伸后误进功能）
  suppressClickUntil = Date.now() + 250
}
// 拉伸后短暂抑制卡片点击跳转
let suppressClickUntil = 0

function openCard(c: CardState) {
  // 刚拉伸完的收尾 click 不跳转
  if (Date.now() < suppressClickUntil) return
  if (c.route) router.push({ name: c.route })
}
</script>

<template>
  <div class="fc-grid">
    <div
      v-for="(c, i) in sorted"
      :key="c.key"
      class="fc-card"
      :class="{ top: c.isTop, dragging: dragIndex === i, over: overIndex === i && dragIndex !== i, readonly, resizing: resizing?.key === c.key }"
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

      <!-- 图标区：flex 撑满上方（居中，不被文字盖）；文字/按钮固定底部 -->
      <div class="fc-ico">
        <img v-if="c.iconUrl" class="fc-ico-img" :src="c.iconUrl" :alt="c.title" />
        <component v-else-if="resolveIcon(c.icon)" :is="resolveIcon(c.icon)" class="fc-ico-comp" :style="{ fontSize: Math.round(Math.min(c.cardW, c.cardH) * 0.4) + 'px' }" />
        <span v-else class="fc-ico-txt" :style="{ fontSize: Math.round(Math.min(c.cardW, c.cardH) * 0.36) + 'px' }">{{ c.icon }}</span>
      </div>
      <div class="fc-body">
        <div class="fc-title" :title="c.title">{{ c.title }}</div>

        <div v-if="!readonly" class="fc-actions">
          <button class="fc-act" :class="{ on: c.isTop }" :title="c.isTop ? '取消置顶' : '置顶'" @click.stop="toggleTop(c)">⤒</button>
          <button class="fc-act" :class="{ on: c.inWorkbench }" :title="c.inWorkbench ? '移出工作台' : '加入工作台'" @click.stop="toggleWorkbench(c)">★</button>
          <button class="fc-act" :class="{ on: c.locked }" :title="c.locked ? '解锁：本卡可自由拉宽高(长方形)' : '锁定：本卡等比缩放(正方形)'" @click.stop="toggleLock(c)">{{ c.locked ? '🔒' : '🔓' }}</button>
        </div>
      </div>

      <!-- 三个拉伸手柄：右=宽，下=高，右下角=宽高（自由拉伸，非等比） -->
      <div v-if="!readonly" class="fc-resize fc-resize-w" @mousedown="startResize(c, 'w', $event)"></div>
      <div v-if="!readonly" class="fc-resize fc-resize-h" @mousedown="startResize(c, 'h', $event)"></div>
      <div v-if="!readonly" class="fc-resize fc-resize-wh" @mousedown="startResize(c, 'wh', $event)">◢</div>
    </div>
  </div>
</template>

<style scoped>
.fc-grid { display: flex; flex-wrap: wrap; gap: 14px; align-items: flex-start; }
.fc-card { position: relative; display: flex; flex-direction: column; width: var(--w, 140px); min-height: var(--h, 140px); background: #fff; border-radius: 10px; padding: 0; cursor: grab; box-shadow: 0 2px 8px rgba(29,33,41,.06); border: 1px solid transparent; transition: transform .15s, border-color .15s, box-shadow .15s; user-select: none; box-sizing: border-box; overflow: hidden; }
.fc-card.readonly { cursor: pointer; }
.fc-card:hover { border-color: #1E5EFF; transform: translateY(-2px); box-shadow: 0 4px 16px rgba(30,94,255,.12); }
.fc-card.dragging { opacity: .5; }
.fc-card.resizing { z-index: 20; }
.fc-card.over { border-color: #1E5EFF; box-shadow: 0 0 0 2px #E8F0FF; transform: none; }
.fc-card.top { border: 1px solid #FF9500; }
.fc-top-tag { position: absolute; top: 8px; left: 8px; background: #FF9500; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 4px; z-index: 2; }
.fc-todo { position: absolute; top: 8px; right: 8px; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; padding: 0 4px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; z-index: 2; }
/* 图标区：撑满上方剩余空间，居中；柔和渐变背景做卡片底 */
.fc-ico { flex: 1 1 auto; min-height: 0; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #F4F7FF 0%, #EEF2FB 100%); overflow: hidden; }
.fc-ico-txt { line-height: 1; filter: grayscale(0.12); opacity: .9; }
.fc-ico-comp { color: #1E5EFF; opacity: .9; }
.fc-ico-img { max-width: 64%; max-height: 80%; object-fit: contain; }
/* 文字/按钮层：固定底部，白底不透明（不再盖图标） */
.fc-body { position: relative; z-index: 1; flex: none; padding: 8px 12px 12px; background: #fff; border-top: 1px solid #F0F2F5; }
.fc-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fc-actions { display: flex; gap: 6px; margin-top: auto; }
.fc-act { width: 26px; height: 26px; flex: none; border: 1px solid #E5E8EF; background: #fff; border-radius: 6px; font-size: 14px; line-height: 1; color: #4E5969; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.fc-act:hover { border-color: #1E5EFF; color: #1E5EFF; }
.fc-act.on { background: #E8F0FF; border-color: #1E5EFF; color: #1E5EFF; }
/* 拉伸手柄：右=宽(ew)，下=高(ns)，右下角=宽高(nwse)
   注意：卡片 overflow:hidden，手柄必须放在卡内(right/bottom=0)否则被裁剪点不到；
   拉伸中的卡加 .resizing 提到最上层，避免被右侧/下方相邻卡盖住手柄。 */
.fc-resize { position: absolute; z-index: 3; }
.fc-resize-w { right: 0; top: 20%; bottom: 20%; width: 8px; cursor: ew-resize; }
.fc-resize-h { left: 20%; right: 20%; bottom: 0; height: 8px; cursor: ns-resize; }
.fc-resize-wh { right: 4px; bottom: 4px; width: 16px; height: 16px; cursor: nwse-resize; color: #C9CDD4; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.fc-resize-w:hover, .fc-resize-h:hover, .fc-resize-wh:hover { color: #1E5EFF; }
</style>
