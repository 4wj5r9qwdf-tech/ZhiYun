<script setup lang="ts">
/**
 * 人员模块首页（内容区）：功能卡片网格
 * 人员信息功能卡片 → /person/person-info（Person 列表页）
 * 复用系统级组件 FunctionCardGrid（拖拽/拉伸/置顶/加工作台/待办红点）
 */
import { ref, onMounted } from 'vue'
import api from '../api'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'

// 人员模块功能卡片目录（key 唯一，新功能加到这即可）
const cards = [
  { key: 'person_info', title: '人员信息', desc: '员工档案/建登录账号', icon: '👥', route: 'Person' },
]

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
    <div class="group">
      <div class="group-title">人员管理</div>
      <p class="tip">拖拽卡片调顺序 · 手柄自由拉伸 · ⤒置顶 · ★加工作台 · 配置自动保存</p>
      <FunctionCardGrid module-key="person" :cards="cards" :todo="todoByMenu" />
    </div>
  </div>
</template>

<style scoped>
.group { margin-bottom: 24px; }
.group-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }
.tip { font-size: 12px; color: #86909C; margin-bottom: 12px; }
</style>
