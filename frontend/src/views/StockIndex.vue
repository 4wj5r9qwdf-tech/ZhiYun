<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../api'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'
import { functionsOf } from '../config/modules'

const cards = functionsOf('stock').map(f => ({ key: f.key, title: f.title, icon: f.icon, route: f.route }))
const todoByMenu = ref<Record<string, number>>({})
onMounted(async () => {
  try {
    const res: any = await api.get('/system/profile/todo/count')
    if (res.code === 200) for (const row of res.data || []) todoByMenu.value[row.menu_key] = Number(row.count) || 0
  } catch { /* 工作台待办服务不可用时仍显示功能入口 */ }
})
</script>

<template>
  <div class="group">
    <a-typography-title :level="4" class="group-title">库存</a-typography-title>
    <FunctionCardGrid module-key="stock" :cards="cards" :todo="todoByMenu" />
  </div>
</template>

<style scoped>
.group { margin: 24px; }
.group-title { font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }
</style>
