<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { approvalTodoCount } from '../utils/approvalTodo'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'
import { functionsOf } from '../config/modules'
const todo = ref<Record<string, number>>({})
let timer: ReturnType<typeof setInterval> | undefined
async function refresh() { try { todo.value = { approval_waiting: await approvalTodoCount() } } catch { /* 保留当前结果。 */ } }
onMounted(() => { void refresh(); timer = setInterval(refresh, 60000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
const cards = functionsOf('approve').map(({ key, title, icon, route }) => ({ key, title, icon, route }))
</script>
<template><div class="approval-index"><a-typography-title :level="4">审批管理</a-typography-title><p>管理审批流程、查看个人待办及业务对象配置。</p><FunctionCardGrid module-key="approve" :cards="cards" :todo="todo" /></div></template>
<style scoped>.approval-index { padding: 0 10px; }.approval-index p { color: #86909c; }</style>
