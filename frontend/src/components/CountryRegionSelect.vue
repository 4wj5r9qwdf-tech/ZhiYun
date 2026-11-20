<!--
文件用途：为织云 ERP 页面提供可复用的CountryRegionSelect展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的CountryRegionSelect展示或交互能力。
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
import { computed } from 'vue'

/**
 * 可复用的国家/地区选择器。数据由业务页面加载，保存稳定编码；中文、英文和编码可搜索。
 * 选择列表仅显示启用项；当前历史值如已停用，保留该项供显示但禁止选择。
 * 无法匹配的旧手填值单独展示，直到用户主动选择标准项；组件不静默覆盖旧值。
 */
type CountryOption = { country_code: string; name_zh: string; name_en: string; status: string }
const props = defineProps<{ modelValue?: string | null; options: CountryOption[]; legacyName?: string | null; placeholder?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string | undefined): void; (e: 'change', value: CountryOption | null): void }>()
const choices = computed(() => props.options
  .filter(item => item.status === '0' || item.country_code === props.modelValue)
  .map(item => ({ value: item.country_code, label: item.name_zh, search: `${item.name_zh} ${item.name_en} ${item.country_code}`, disabled: item.status !== '0' })))
function onChange(value: unknown) {
  const code = typeof value === 'string' && value ? value : undefined
  emit('update:modelValue', code)
  emit('change', code ? props.options.find(item => item.country_code === code) || null : null)
}
</script>
<template>
  <div>
    <a-select :value="modelValue || undefined" :options="choices" show-search allow-clear option-filter-prop="search" :placeholder="placeholder || '搜索并选择国家/地区'" style="width:100%" @change="onChange" />
    <div v-if="!modelValue && legacyName" class="legacy-country">原手填：{{ legacyName }}（请按需选择标准地区；暂不选择会保留原值） <a @click="onChange(undefined)">清除原值</a></div>
  </div>
</template>
<style scoped>.legacy-country { color: #ad6800; font-size: 12px; line-height: 1.5; margin-top: 4px; }</style>
