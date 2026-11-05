<script setup lang="ts">
/**
 * 系统模块首页（内容区）：功能卡片网格（按用途分组）
 * 卡片由系统级组件 FunctionCardGrid 渲染（拖拽/拉伸/置顶/加工作台/待办红点）
 */
import { computed, ref, onMounted } from 'vue'
import api from '../api'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'
import { functionsOf } from '../config/modules'

// 功能卡片目录统一维护在 config/modules.ts；icon 直接传 antd 图标名（FunctionCardGrid 渲染 antd 组件）
const cards = computed(() => functionsOf('system').map(f => ({
  key: f.key,
  title: f.title,
  desc: f.desc,
  icon: f.icon,
  route: f.route,
})))

const categories = [
  { title: '权限与入口', keys: ['system_role', 'menu-directory', 'clients'] },
  { title: '系统配置', keys: ['company', 'parameters', 'dictionary', 'system_template'] },
  { title: '业务基础资料', keys: ['system_unit', 'system_category', 'system_tag', 'system_size', 'system_size_group', 'system_country_region'] },
  { title: '文件与存储', keys: ['files', 'storage-config'] },
  { title: '运行监控', keys: ['monitor_online', 'monitor_operlog', 'cache-overview'] },
]
const groups = computed(() => {
  const classified = new Set(categories.flatMap(group => group.keys))
  return [
    ...categories.map(group => ({ title: group.title, cards: cards.value.filter(card => group.keys.includes(card.key)) })),
    { title: '其他', cards: cards.value.filter(card => !classified.has(card.key)) },
  ].filter(group => group.cards.length > 0)
})

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
</script>

<template>
  <div>
    <p class="tip">拖拽卡片调顺序 · 拖动手柄独立调整宽高 · ⤒置顶 · ★加工作台 · 配置自动保存</p>
    <div v-for="group in groups" :key="group.title" class="group">
      <a-typography-title :level="4" class="group-title">{{ group.title }}</a-typography-title>
      <FunctionCardGrid module-key="system" :cards="group.cards" :todo="todoByMenu" />
    </div>
  </div>
</template>

<style scoped>
.group { margin-bottom: 24px; }
.group-title { font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }
.tip { font-size: 12px; color: #86909C; margin-bottom: 12px; }
</style>
