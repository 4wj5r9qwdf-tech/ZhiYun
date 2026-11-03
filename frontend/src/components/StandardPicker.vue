<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'

/**
 * 通用组织/人员选择弹窗。
 * tree 和 items 由业务页面提供；组件不请求接口、不决定数据权限，也不保存业务数据。
 * modelValue 保存稳定的字符串 ID；multiple 控制单选/多选；columns、filters 插槽和
 * itemFilter 允许业务追加展示列与筛选条件，筛选不会改变其他页面的选择规则。
 */
type TreeNode = { orgId: string; orgName: string; children?: TreeNode[] }
type Item = { id: string; name: string; orgId: string; orgIds?: string[] }
const props = withDefaults(defineProps<{ open: boolean; title: string; tree: TreeNode[]; items: Item[]; modelValue: string[]; multiple?: boolean; itemFilter?: (item: Item) => boolean; columns?: { title: string; dataIndex: string; key: string }[] }>(), { multiple: false })
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'update:modelValue', value: string[]): void; (e: 'confirm', value: string[]): void }>()
const keyword = ref('')
const orgId = ref<string | null>(null)
// selected 是弹窗内的临时副本；勾选、翻页或关闭弹窗都不会直接改动父页面的值。
const selected = ref<string[]>([])
// 每次打开重置搜索和树筛选，并以父页面当前值初始化临时选择，避免复用弹窗时带入上一次条件。
watch(() => props.open, value => { if (value) { keyword.value = ''; orgId.value = null; selected.value = [...props.modelValue] } })
/** 递归查找所选组织，并收集本节点及全部下级 ID；找不到节点时返回 null。 */
function descendants(nodes: TreeNode[], id: string): string[] | null {
  for (const node of nodes) {
    if (node.orgId === id) { const collect = (n: TreeNode): string[] => [n.orgId, ...(n.children || []).flatMap(collect)]; return collect(node) }
    const found = descendants(node.children || [], id); if (found) return found
  }
  return null
}
// 树节点筛选包含下级；人员可能同时属于行政组织与生产小组，任一归属路径命中即可展示。
// 名称搜索及业务传入的 itemFilter 在树筛选结果上继续收窄，不负责替代服务端权限校验。
const filtered = computed(() => { const ids = orgId.value ? descendants(props.tree, orgId.value) : null; return props.items.filter(item => (!ids || (item.orgIds || [item.orgId]).some(id => ids.includes(id))) && item.name.includes(keyword.value.trim()) && (!props.itemFilter || props.itemFilter(item))) })
// 已选项独立于当前筛选结果，切换搜索、组织和分页时保留，并允许逐项移除。
const selectedItems = computed(() => selected.value.map(id => ({ id, name: props.items.find(item => item.id === id)?.name || id })))
/** 确认时一次性回传选中 ID；未选择时提示，取消则只关闭弹窗并丢弃临时选择。 */
function confirm() { if (!selected.value.length) { message.warning('请选择一项'); return } emit('update:modelValue', [...selected.value]); emit('confirm', [...selected.value]); emit('update:open', false) }
</script>

<template>
  <a-modal :open="open" :title="title" width="760px" :mask-closable="false" :keyboard="false" ok-text="确定" cancel-text="取消" @ok="confirm" @cancel="emit('update:open', false)">
    <div class="picker-layout">
      <aside class="picker-tree"><strong>组织架构</strong><a-tree :tree-data="(tree as any)" :field-names="{ title: 'orgName', key: 'orgId', children: 'children' }" default-expand-all block-node :selected-keys="orgId ? [orgId] : []" @select="keys => orgId = keys.length ? String(keys[0]) : null" /></aside>
      <main class="picker-main"><a-input v-model:value="keyword" allow-clear placeholder="搜索名称" />
        <div v-if="$slots.filters" class="picker-filters"><slot name="filters" /></div>
        <a-table :columns="columns || [{ title: '名称', dataIndex: 'name', key: 'name' }]" :data-source="filtered" row-key="id" size="small" :pagination="{ pageSize: 8, showTotal: (n: number) => `共 ${n} 条` }" :row-selection="{ type: multiple ? 'checkbox' : 'radio', selectedRowKeys: selected, preserveSelectedRowKeys: true, onChange: (keys: (string | number)[]) => selected = keys.map(String) }" />
        <div class="picker-selected">已选 {{ selected.length }} 项 <a-tag v-for="item in selectedItems" :key="item.id" closable @close="selected = selected.filter(id => id !== item.id)">{{ item.name }}</a-tag></div>
      </main>
    </div>
  </a-modal>
</template>
<style scoped>
.picker-layout { display: flex; gap: 12px; min-height: 360px; }
.picker-tree { flex: 0 0 210px; padding: 12px; border: 1px solid #E5E8EF; border-radius: 8px; overflow: auto; }
.picker-main { flex: 1; min-width: 0; }
.picker-main .ant-input-affix-wrapper { margin-bottom: 12px; }
.picker-filters { margin-bottom: 12px; }
.picker-selected { padding: 8px 0; color: #4E5969; }
@media (max-width: 640px) { .picker-layout { display: block; } .picker-tree { max-height: 150px; margin-bottom: 12px; } }
</style>
