<script setup lang="ts">
/**
 * 工作台（登录默认首页）：
 * - 汇总用户加入工作台的功能（sys_user_workbench）
 * - 待办汇总（模块级累加）
 * - 顶栏：真实登录人 + 通知铃铛 + 全局搜索（复用 TopBar）
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import TopBar from '../components/TopBar.vue'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'

const router = useRouter()

// 所有功能目录（key→信息），供工作台展示已加入的功能
const allFunctions: Record<string, any> = {
  company: { title: '公司档案', desc: '公司名/logo/登录背景', icon: '🏛️', route: 'Company', module: 'system' },
}
// 模块目录（左侧导航，待办数从 todo/count 累加）
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

const activeModule = ref('workbench')
const workbenchKeys = ref<string[]>([])     // 已加入工作台的功能key
const todoByMenu = ref<Record<string, number>>({})   // 功能级待办
const todoByModule = ref<Record<string, number>>({}) // 模块级待办（累加）

onMounted(async () => {
  await Promise.all([loadWorkbench(), loadTodo()])
})

async function loadWorkbench() {
  try {
    const res: any = await api.get('/system/profile/workbench')
    if (res.code === 200) workbenchKeys.value = (res.data || []).map((r: any) => r.menu_key)
  } catch { /* ignore */ }
}

async function loadTodo() {
  try {
    const res: any = await api.get('/system/profile/todo/count')
    if (res.code === 200) {
      const byMenu: Record<string, number> = {}
      const byModule: Record<string, number> = {}
      for (const r of res.data || []) {
        const c = Number(r.count) || 0
        byMenu[r.menu_key] = c
        byModule[r.module_key] = (byModule[r.module_key] || 0) + c
      }
      todoByMenu.value = byMenu
      todoByModule.value = byModule
    }
  } catch { /* ignore */ }
}

// 工作台展示的功能列表（已加入工作台的，按置顶→排序）
const benchCards = computed(() => {
  return workbenchKeys.value
    .map(k => allFunctions[k])
    .filter(Boolean)
})

function moduleTodo(key: string) { return todoByModule.value[key] || 0 }

function navModule(key: string) {
  activeModule.value = key
  if (key === 'workbench') return
  if (key === 'system') router.push({ name: 'SystemIndex' })
  else alert(`「${modules.find(m => m.key === key)?.label}」模块开发中`)
}
</script>

<template>
  <div class="layout">
    <!-- 左侧竖排模块导航 -->
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
        <!-- 待办红点：模块级累加，无待办不显示 -->
        <i v-if="moduleTodo(m.key) > 0" class="todo-dot">{{ moduleTodo(m.key) > 99 ? '99+' : moduleTodo(m.key) }}</i>
      </div>
    </aside>

    <div class="main">
      <TopBar />

      <div class="content">
        <div class="module-title">模块 / 工作台</div>

        <!-- 工作台：加入的功能卡片 + 待办汇总 -->
        <div class="group">
          <div class="group-title">我的工作台</div>
          <div v-if="benchCards.length === 0" class="empty-hint">
            暂无加入工作台的功能。到「系统」模块的功能卡片上点「加入工作台」即可。
          </div>
          <!-- 只读模式：不带拖拽/置顶/加工作台手柄，仅展示 + 待办红点 -->
          <FunctionCardGrid v-else module-key="workbench" :cards="benchCards" :todo="todoByMenu" readonly />
        </div>

        <!-- 待办汇总 -->
        <div class="group">
          <div class="group-title">待办汇总</div>
          <div v-if="Object.keys(todoByModule).length === 0" class="empty-hint">暂无待办</div>
          <div v-else class="todo-list">
            <div v-for="(cnt, mk) in todoByModule" :key="mk" class="todo-row">
              <span class="todo-module">{{ modules.find(m => m.key === mk)?.label || mk }}</span>
              <span class="todo-count">{{ cnt }} 项待处理</span>
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

/* 待办红点（模块级累加）：有才显示 */
.todo-dot { position: absolute; top: 2px; right: 6px; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; padding: 0 4px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; z-index: 2; }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.content { flex: 1; overflow: auto; background: #F5F7FA; padding: 16px 20px; }
.module-title { font-size: 14px; color: #4E5969; margin-bottom: 16px; }
.group { margin-bottom: 24px; }
.group-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }

.empty-hint { color: #86909C; font-size: 13px; padding: 12px 0; }

/* 待办汇总列表 */
.todo-list { background: #fff; border-radius: 10px; padding: 4px 0; }
.todo-row { display: flex; align-items: center; padding: 12px 16px; border-bottom: 1px solid #F0F2F5; }
.todo-row:last-child { border-bottom: none; }
.todo-module { font-size: 14px; color: #1D2129; }
.todo-count { margin-left: auto; font-size: 13px; color: #F53F3F; }
</style>
