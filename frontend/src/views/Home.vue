<script setup lang="ts">
/**
 * 功能管理页（系统模块）：
 * - 功能卡片：拖拽排序 + 自由拖拽图标像素大小(80-320,min/max) + 置顶 + 加入工作台
 * - 配置按用户存 sys_user_menu_config（拖拽后自动保存）
 * - 待办红点：卡片自己的待办数，无待办不显示
 * - 复用 TopBar（真实登录人/铃铛/搜索）
 */
import { ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '../api'
import TopBar from '../components/TopBar.vue'

const router = useRouter()
const route = useRoute()

// 左侧模块导航
const modules = [
  { key: 'workbench', icon: '📋', label: '工作台' },
  { key: 'order', icon: '📦', label: '订单' },
  { key: 'prod', icon: '🏭', label: '生产' },
  { key: 'stock', icon: '📊', label: '库存' },
  { key: 'person', icon: '👥', label: '人员' },
  { key: 'dorm', icon: '🏠', label: '宿舍' },
  { key: 'canteen', icon: '🍜', label: '食堂' },
  { key: 'vehicle', icon: '🚗', label: '车辆' },
  { key: 'property', icon: '🏢', label: '物业资产' },
  { key: 'finance', icon: '💰', label: '财务' },
  { key: 'approve', icon: '✅', label: '审批' },
  { key: 'alert', icon: '🔔', label: '预警' },
  { key: 'system', icon: '⚙️', label: '系统' },
]
const activeModule = ref('system')

// 系统模块功能卡片（key 唯一，对应 sys_user_menu_config.menu_key）
const cards = ref([
  { key: 'company', title: '公司档案', desc: '公司名/logo/登录背景', icon: '🏛️', route: 'Company',
    sort: 0, iconSize: 180, isTop: false, inWorkbench: false },
])
const todoByMenu = ref<Record<string, number>>({})

// 卡片拖拽排序状态
const dragIndex = ref(-1)
const overIndex = ref(-1)
// 图标大小拖拽状态
const resizing = ref<string | null>(null)
let resizeStartX = 0
let resizeStartSize = 0

onMounted(async () => {
  await Promise.all([loadConfig(), loadTodo()])
  bindResizeMove()
})

async function loadConfig() {
  try {
    const res: any = await api.get('/system/profile/menu-config')
    if (res.code === 200) {
      const map: Record<string, any> = {}
      for (const r of res.data || []) map[r.menu_key] = r
      for (const c of cards.value) {
        const m = map[c.key]
        if (m) { c.sort = m.sort; c.iconSize = m.icon_size; c.isTop = m.is_top === '1'; c.inWorkbench = m.in_workbench === '1' }
      }
    }
  } catch { /* ignore */ }
}

async function loadTodo() {
  try {
    const res: any = await api.get('/system/profile/todo/count')
    if (res.code === 200) {
      const byMenu: Record<string, number> = {}
      for (const r of res.data || []) byMenu[r.menu_key] = Number(r.count) || 0
      todoByMenu.value = byMenu
    }
  } catch { /* ignore */ }
}

// 保存单卡配置（拖拽排序/图标大小/置顶/加工作台）
async function saveCard(c: any) {
  try {
    await api.put('/system/profile/menu-config', {
      menuKey: c.key, sort: c.sort, iconSize: c.iconSize,
      isTop: c.isTop ? '1' : '0', inWorkbench: c.inWorkbench ? '1' : '0',
    })
  } catch { /* ignore */ }
}

// 置顶切换
function toggleTop(c: any) { c.isTop = !c.isTop; saveCard(c) }
// 加入工作台切换
function toggleWorkbench(c: any) { c.inWorkbench = !c.inWorkbench; saveCard(c) }

// ===== 拖拽排序（HTML5 drag）=====
function onDragStart(i: number, e: DragEvent) { dragIndex.value = i; e.dataTransfer?.setData('text/plain', String(i)) }
function onDragOver(i: number, e: DragEvent) { e.preventDefault(); overIndex.value = i }
function onDrop(i: number) {
  const from = dragIndex.value
  if (from < 0 || from === i) { resetDrag(); return }
  const arr = cards.value
  const [moved] = arr.splice(from, 1)
  arr.splice(i, 0, moved)
  arr.forEach((c, idx) => { c.sort = idx })
  // 置顶的排前面：重排 sort 让 isTop 排序稳定（简单：保存时按当前数组顺序）
  saveAll()
  resetDrag()
}
function resetDrag() { dragIndex.value = -1; overIndex.value = -1 }

// 批量保存排序
function saveAll() { cards.value.forEach(c => saveCard(c)) }

// ===== 自由拖拽图标像素大小（80-320，min/max 限制，安卓小组件式）=====
function startResize(c: any, e: MouseEvent) {
  e.stopPropagation(); e.preventDefault()
  resizing.value = c.key
  resizeStartX = e.clientX
  resizeStartSize = c.iconSize
}
function bindResizeMove() {
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', endResize)
}
function onResizeMove(e: MouseEvent) {
  if (!resizing.value) return
  const c = cards.value.find(x => x.key === resizing.value)
  if (!c) return
  // 向右拖=变大，向左拖=变小；min 80 max 320
  let size = resizeStartSize + (e.clientX - resizeStartX)
  size = Math.max(80, Math.min(320, size))
  c.iconSize = size
}
function endResize() {
  if (!resizing.value) return
  const c = cards.value.find(x => x.key === resizing.value)
  if (c) saveCard(c)
  resizing.value = null
}

function navModule(key: string) {
  activeModule.value = key
  if (key === 'workbench') router.push({ name: 'Workbench' })
  else if (key !== 'system') alert(`「${modules.find(m => m.key === key)?.label}」模块开发中`)
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">织</div>
      <div
        v-for="m in modules"
        :key="m.key"
        class="nav-item"
        :class="{ active: activeModule === m.key }"
        @click="navModule(m.key)"
      >
        <span class="ico">{{ m.icon }}</span>
        <span class="lbl">{{ m.label }}</span>
      </div>
    </aside>

    <div class="main">
      <TopBar />

      <div class="content">
        <div class="module-title">模块 / 系统</div>

        <div class="group">
          <div class="group-title">基础配置</div>
          <p class="tip">拖拽卡片调顺序 · 右下角拖拽调整大小(80-320px) · 置顶/加入工作台 · 配置自动保存</p>
          <div class="card-grid">
            <div
              v-for="(c, i) in cards"
              :key="c.key"
              class="card"
              :class="{ top: c.isTop, dragging: dragIndex === i, over: overIndex === i && dragIndex !== i }"
              :style="{ '--sz': c.iconSize + 'px' }"
              draggable="true"
              @dragstart="onDragStart(i, $event)"
              @dragover="onDragOver(i, $event)"
              @drop="onDrop(i)"
              @dragend="resetDrag"
            >
              <i v-if="(todoByMenu[c.key] || 0) > 0" class="todo-dot card-todo">{{ todoByMenu[c.key] }}</i>
              <span v-if="c.isTop" class="top-tag">置顶</span>

              <div class="card-ico" :style="{ width: c.iconSize + 'px', height: c.iconSize + 'px', fontSize: Math.round(c.iconSize / 2.2) + 'px' }">
                {{ c.icon }}
              </div>
              <div class="card-title">{{ c.title }}</div>
              <div class="card-desc">{{ c.desc }}</div>

              <div class="card-actions">
                <button class="act" :class="{ on: c.isTop }" @click="toggleTop(c)">置顶</button>
                <button class="act" :class="{ on: c.inWorkbench }" @click="toggleWorkbench(c)">{{ c.inWorkbench ? '已加工作台' : '加工作台' }}</button>
              </div>

              <!-- 右下角拖拽调大小手柄 -->
              <div class="resize-handle" @mousedown="startResize(c, $event)">◢</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout { display: flex; height: 100vh; overflow: hidden; }
.sidebar { width: 72px; background: #fff; border-right: 1px solid #E5E8EF; display: flex; flex-direction: column; align-items: center; padding-top: 12px; flex-shrink: 0; overflow-y: auto; }
.logo { width: 44px; height: 44px; border-radius: 10px; background: #1E5EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; margin-bottom: 14px; flex-shrink: 0; }
.nav-item { position: relative; width: 60px; height: 58px; border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; margin-bottom: 5px; color: #4E5969; gap: 2px; flex-shrink: 0; }
.nav-item:hover { background: #E8F0FF; }
.nav-item.active { background: #1E5EFF; color: #fff; }
.nav-item .ico { font-size: 19px; line-height: 1; }
.nav-item .lbl { font-size: 11px; }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.content { flex: 1; overflow: auto; background: #F5F7FA; padding: 16px 20px; }
.module-title { font-size: 14px; color: #4E5969; margin-bottom: 16px; }
.group { margin-bottom: 24px; }
.group-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }
.tip { font-size: 12px; color: #86909C; margin-bottom: 12px; }

.card-grid { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
.card { position: relative; width: var(--sz, 180px); background: #fff; border-radius: 10px; padding: 20px 16px; cursor: grab; box-shadow: 0 2px 8px rgba(29,33,41,.06); border: 1px solid transparent; transition: box-shadow 0.2s; user-select: none; }
.card:hover { border-color: #1E5EFF; box-shadow: 0 4px 16px rgba(30,94,255,.12); }
.card.dragging { opacity: 0.5; }
.card.over { border-color: #1E5EFF; box-shadow: 0 0 0 2px rgba(30,94,255,.25); }
.card.top { border: 1px solid #FF9500; }
.top-tag { position: absolute; top: 8px; left: 8px; background: #FF9500; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.card-todo { top: 10px; right: 10px; }
.todo-dot { position: absolute; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; padding: 0 4px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; z-index: 2; }

.card-ico { border-radius: 10px; background: #E8F0FF; display: flex; align-items: center; justify-content: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 4px; }
.card-desc { font-size: 12px; color: #86909C; margin-bottom: 8px; }

.card-actions { display: flex; gap: 6px; margin-top: 8px; }
.act { flex: 1; height: 26px; border: 1px solid #E5E8EF; background: #fff; border-radius: 6px; font-size: 11px; color: #4E5969; cursor: pointer; }
.act:hover { border-color: #1E5EFF; color: #1E5EFF; }
.act.on { background: #E8F0FF; border-color: #1E5EFF; color: #1E5EFF; }

.resize-handle { position: absolute; right: 6px; bottom: 6px; width: 18px; height: 18px; cursor: nwse-resize; color: #C9CDD4; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.resize-handle:hover { color: #1E5EFF; }
</style>
