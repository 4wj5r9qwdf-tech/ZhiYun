<!--
文件用途：为织云 ERP 页面提供可复用的TagSelect展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的TagSelect展示或交互能力。
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
