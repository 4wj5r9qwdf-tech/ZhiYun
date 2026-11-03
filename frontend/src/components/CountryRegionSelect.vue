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
