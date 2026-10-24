<script setup lang="ts">
/**
 * 功能管理页（系统模块）：
 * - 功能卡片由系统级组件 FunctionCardGrid 渲染（拖拽排序/图标大小/置顶/加工作台/待办红点全在组件内）
 * - 本页只提供：模块导航 + cards 目录 + todo 数据
 * - 复用 TopBar（真实登录人/铃铛/搜索）
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import TopBar from '../components/TopBar.vue'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'

const router = useRouter()

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

// 系统模块功能卡片目录（key 唯一，新功能加到这即可）
const cards = [
  { key: 'company', title: '公司档案', desc: '公司名/logo/登录背景', icon: '🏛️', route: 'Company' },
]

// 功能级待办数（无待办不显示红点）
const todoByMenu = ref<Record<string, number>>({})

onMounted(loadTodo)

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
          <p class="tip">拖拽卡片调顺序 · 右/下/右下角手柄自由拉伸(宽120-480/高100-320，宽高独立) · 置顶/加入工作台 · 配置自动保存</p>
          <FunctionCardGrid module-key="system" :cards="cards" :todo="todoByMenu" />
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
</style>
