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
    <div class="result-footer"><a-button type="primary" @click="emit('update:open', false)">完成</a-button></div>
  </a-modal>
</template>
<style scoped>.result-footer { display: flex; justify-content: flex-end; margin-top: 16px; }</style>
