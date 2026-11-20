<!--
文件用途：为织云 ERP 页面提供可复用的标准列表页布局展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的标准列表页布局展示或交互能力。
设计思路：以属性接收调用页面提供的数据和配置，以事件返回用户操作；组件不决定业务授权。
实现方法：封装稳定视觉结构与通用交互，由业务页面传入数据并处理事件。
关键约束：不能绕过调用页或服务端的数据权限。
依赖与引用：Docs/规范/前端规范.md；frontend/src/views/SystemTemplate.vue
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-20
修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
最近测试时间：本次整理未执行功能测试
测试人员：本次整理未执行
测试状态：待功能回归；历史测试记录待追溯
测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
-->
<script setup lang="ts">
import { provide, ref } from 'vue'

const tableToolbar = ref<HTMLElement>()
provide('standard-list-table-toolbar', tableToolbar)
/** 通用功能页布局：筛选、操作、可选侧栏和主体均由业务通过插槽提供。 */
withDefaults(defineProps<{ collapsed?: boolean }>(), { collapsed: false })
const emit = defineEmits<{ (e: 'update:collapsed', value: boolean): void }>()
</script>
<template>
  <div class="std-page">
    <div v-if="$slots.filters" class="std-filters"><slot name="filters" /></div>
    <div v-if="$slots.actions" class="std-actions"><slot name="actions" /></div>
    <div ref="tableToolbar" class="std-table-toolbar" />
    <div class="std-body" :class="{ 'has-sidebar': $slots.sidebar }">
      <aside v-if="$slots.sidebar" class="std-sidebar" :class="{ collapsed }"><slot name="sidebar" /></aside>
      <button v-if="$slots.sidebar" type="button" class="std-toggle" :class="{ 'at-left': collapsed }" :aria-label="collapsed ? '展开分类' : '收起分类'" :title="collapsed ? '展开分类' : '收起分类'" @click="emit('update:collapsed', !collapsed)"><span /></button>
      <main class="std-main"><slot /></main>
    </div>
  </div>
</template>
<style scoped>
.std-page { box-sizing: border-box; height: 100%; min-height: 0; padding: 12px 0; display: flex; flex-direction: column; }
.std-filters { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.std-filters :deep(.zy-filter-field) { display: inline-flex; align-items: center; gap: 6px; flex: none; min-width: 0; color: #4e5969; font-size: 14px; white-space: nowrap; }
.std-actions { margin-bottom: 12px; }
.std-filters, .std-actions, .std-table-toolbar { flex-shrink: 0; }
.std-body { display: flex; flex: 1 1 0; align-items: stretch; min-height: 0; position: relative; }
.std-body.has-sidebar { padding-left: 0; }
.std-sidebar { width: 260px; flex: none; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; min-height: 0; overflow: auto; transition: width .2s, opacity .2s; margin-right: 8px; }
.std-sidebar.collapsed { width: 0; height: 0; min-height: 0; opacity: 0; margin-right: 0; pointer-events: none; overflow: hidden; }
.std-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: -4px; z-index: 6; width: 8px; height: 40px; padding: 0; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.std-toggle span { width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }
.std-toggle.at-left span { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.std-toggle:hover { border-color: #1E5EFF; }.std-main { flex: 1; min-width: 0; min-height: 0; }
.std-main:has(> .foundation-table) { display: flex; flex-direction: column; }
.std-main:has(> .foundation-table) > :deep(.foundation-table) { flex: 1 1 0; }
</style>
