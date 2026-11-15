<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import { ensureTag, getTagOptions } from '../api/tag'

/**
 * 可复用的多标签输入器。
 * modelValue 存储标签ID数组；moduleKey/moduleName 由接入的业务功能提供。
 * 用户首次输入标签时先由服务端动态登记模块归属和标签档案，成功后才回写表单。
 */
const props = defineProps<{
  modelValue: string[]
  moduleKey: string
  moduleName: string
  placeholder?: string
  /** 已关联但现已停用的标签仍显示名称，允许移除但不允许新选。 */
  selectedOptions?: { label: string; value: string; disabled?: boolean; groupName?: string }[]
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string[]): void }>()
type TagOption = { label: string; value: string; disabled?: boolean; groupName: string }
const options = ref<{ label: string; options: TagOption[] }[]>([])
const flatOptions = computed(() => options.value.flatMap(group => group.options))
const loading = ref(false)

/** 按分组组织选项；旧接口返回的已选标签信息缺少组名时放入默认分组。 */
function groupOptions(items: TagOption[]) {
  const grouped = new Map<string, TagOption[]>()
  for (const item of items) {
    const groupName = item.groupName || '默认分组'
    const bucket = grouped.get(groupName) || []
    bucket.push(item)
    grouped.set(groupName, bucket)
  }
  return [...grouped].map(([label, values]) => ({ label, options: values }))
}

/** 仅加载当前模块的启用标签，切换 moduleKey 时由 watch 重新拉取。 */
async function loadOptions() {
  loading.value = true
  try {
    const res: any = await getTagOptions(props.moduleKey)
    const enabled = (res.data || []).map((item: any) => ({
      label: item.tagName,
      value: String(item.tagId),
      groupName: String(item.groupName || '默认分组'),
    }))
    const selected = (props.selectedOptions || []).map(item => ({ ...item, groupName: item.groupName || '默认分组' }))
    options.value = groupOptions([...new Map([...selected, ...enabled].map(item => [item.value, item])).values()])
  } finally {
    loading.value = false
  }
}

/**
 * 规范用户选择并自动收录手工输入的名称。
 * 已有选项以稳定 tagId 回传；新输入名称经服务端创建/查重后，也改用规范记录的 tagId。
 * API 收录失败的新增名称不回写 modelValue，防止业务单据保存一个未进入目录的标签。
 */
async function onChange(value: unknown) {
  const values = Array.isArray(value) ? value : []
  const entries = [...new Set(values.map(value => String(value).trim()).filter(Boolean))]
  const byId = new Map(flatOptions.value.map(item => [item.value, item]))
  const accepted = new Set(entries.filter(value => byId.has(value)))
  const newNames = entries.filter(value => !byId.has(value))
  for (const name of newNames) {
    try {
      const res: any = await ensureTag(props.moduleKey, props.moduleName, name)
      const tag = res.data
      const option = { label: tag.tagName, value: String(tag.tagId), groupName: String(tag.groupName || '默认分组') }
      if (!flatOptions.value.some(item => item.value === option.value)) options.value = groupOptions([...flatOptions.value, option])
      accepted.add(option.value)
    } catch {
      message.error('标签“' + name + '”收录失败')
    }
  }
  emit('update:modelValue', entries.map(value => {
    if (accepted.has(value)) return value
    const matched = flatOptions.value.find(item => item.label.toLocaleLowerCase() === value.toLocaleLowerCase())
    return matched?.value
  }).filter((value): value is string => !!value))
}

onMounted(loadOptions)
watch(() => props.moduleKey, loadOptions)
watch(() => props.selectedOptions, loadOptions)
</script>

<template>
  <a-select
    :value="modelValue"
    mode="tags"
    :options="options"
    :loading="loading"
    :placeholder="placeholder || '选择或输入标签'"
    style="width: 100%"
    @change="onChange"
  />
</template>
