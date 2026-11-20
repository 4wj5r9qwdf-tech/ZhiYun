<!--
文件用途：为织云 ERP 页面提供可复用的BatchResultModal展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的BatchResultModal展示或交互能力。
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
/** 批量结果展示组件：业务负责执行与逐条结果，本组件只呈现成功数和失败原因。 */
import { computed } from 'vue'
const props = defineProps<{ open: boolean; title: string; succeeded: number; failures: { name: string; reason: string }[] }>()
const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>()
const failureRows = computed(() => props.failures.map((row, index) => ({ ...row, rowId: index })))
</script>
<template>
  <a-modal :open="open" :title="title" :footer="null" @cancel="emit('update:open', false)">
    <p>成功 {{ succeeded }} 条，未处理 {{ failures.length }} 条。</p>
    <a-table v-if="failures.length" :columns="[{ title: '记录', dataIndex: 'name', key: 'name' }, { title: '原因', dataIndex: 'reason', key: 'reason' }]" :data-source="failureRows" row-key="rowId" size="small" :pagination="{ pageSize: 5 }" />
    <div class="result-footer"><slot name="actions" /><a-button type="primary" @click="emit('update:open', false)">完成</a-button></div>
  </a-modal>
</template>
<style scoped>.result-footer { display: flex; justify-content: flex-end; gap:8px; margin-top: 16px; }</style>
