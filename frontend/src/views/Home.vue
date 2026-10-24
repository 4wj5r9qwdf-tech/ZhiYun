<script setup lang="ts">
/**
 * 系统模块布局壳（/system）：左侧竖排模块导航 + 顶栏(TopBar) + 内容区(<router-view>)
 * 内容区按子路由渲染：'' → SystemIndex(功能卡片)，'company' → Company(公司档案)
 * 左侧导航和顶栏常驻，子页面切换不丢导航。
 */
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
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
const activeModule = computed(() => (route.path.startsWith('/person') ? 'person' : 'system'))

function navModule(key: string) {
  if (key === 'workbench') router.push({ name: 'Workbench' })
  else if (key === 'system') router.push({ name: 'SystemIndex' })
  else if (key === 'person') router.push({ name: 'PersonIndex' })
  else alert(`「${modules.find(m => m.key === key)?.label}」模块开发中`)
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
        <div class="module-title">模块 / {{ activeModule === 'person' ? '人员' : '系统' }}</div>
        <router-view />
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
</style>
