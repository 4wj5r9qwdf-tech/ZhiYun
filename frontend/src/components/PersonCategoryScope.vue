<!--
文件用途：为织云 ERP 页面提供可复用的PersonCategoryScope展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的PersonCategoryScope展示或交互能力。
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
/** 按分类类型和树节点维护人员可见分类范围。 / Maintain employee category visibility grants by scheme and tree node. */
/** 独立维护人员分类授权，避免随人事表单误提交。 / Edit category grants independently of the personnel form. */
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import { foundationError, foundationRequest } from '../utils/foundation'

const props = defineProps<{ userId: string; name: string }>()
const emit = defineEmits<{ close: [] }>()
type Mode = 'ALL' | 'SELECTED' | 'NONE'
type Grant = { schemeId: string | null; categoryId: string | null; includeChildren: boolean; name: string }
type Entry = { id: string; name: string; status: string }
type Category = Entry & { schemeId: string; parentId: string | null }
type Option = { title: string; key: string; children: Option[] }
type ScopeResponse = {
  scope: { mode: Mode; grants: Grant[] }
  schemes: Entry[]
  categories: Category[]
}

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const mode = ref<Mode>('ALL')
const grants = ref<Grant[]>([])
const schemes = ref<Entry[]>([])
const categories = ref<Category[]>([])
const options = computed(() => {
  const nodes = new Map<string, Option>()
  const roots = new Map<string, Option>()
  for (const scheme of schemes.value) {
    roots.set(scheme.id, { title: scheme.name + (scheme.status === '0' ? '' : '（停用）'), key: 's:' + scheme.id, children: [] })
  }
  for (const category of categories.value) {
    nodes.set(category.id, { title: category.name + (category.status === '0' ? '' : '（停用）'), key: 'c:' + category.id, children: [] })
  }
  for (const category of categories.value) {
    const node = nodes.get(category.id)
    const parent = (category.parentId && nodes.get(category.parentId)) || roots.get(category.schemeId)
    if (node && parent) parent.children.push(node)
  }
  return [...roots.values()]
})
/** 索引全部分类节点，便于定位勾选项。 / Index all nodes by their stable keys. */
const nodeIndex = computed(() => new Map(options.value.flatMap(subtree).map(node => [node.key, node])))
const directGrants = computed(() => new Map(grants.value.map(grant => [grantKey(grant), grant])))
const missingGrants = computed(() => grants.value.filter(grant => !nodeIndex.value.has(grantKey(grant))))

/** 收集当前分支及其下级。 / Collect a node and its descendants. */
function subtree(node: Option): Option[] {
  return [node, ...node.children.flatMap(subtree)]
}

/** 按实际覆盖范围回显全选和半选，保留旧有仅本级授权。 / Display effective branch coverage without widening existing grants. */
const checkedKeys = computed(() => {
  const checked: string[] = []
  const halfChecked: string[] = []
  function visit(node: Option, inherited: boolean): boolean {
    const direct = directGrants.value.get(node.key)
    const full = inherited || direct?.includeChildren === true
    const hasChild = node.children.map(child => visit(child, full)).some(Boolean)
    if (full || (direct && !node.children.length)) checked.push(node.key)
    else if (direct || hasChild) halfChecked.push(node.key)
    return full || Boolean(direct) || hasChild
  }
  options.value.forEach(node => visit(node, false))
  return { checked, halfChecked }
})

/** 读取已保存授权和可配置分类。 / Load persisted grants and selectable taxonomy. */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await foundationRequest<ScopeResponse>('get', `/system/person/${props.userId}/category-scope`)
    mode.value = data.scope.mode
    grants.value = data.scope.grants
    schemes.value = data.schemes
    categories.value = data.categories
  } catch (cause) {
    error.value = foundationError(cause)
  } finally {
    loading.value = false
  }
}

/** 用稳定键回显直接授权项。 / Restore directly granted targets by stable key. */
function grantKey(grant: Grant) {
  return grant.schemeId ? `s:${grant.schemeId}` : `c:${grant.categoryId}`
}

/** 构建现有接口所需的节点或整类授权。 / Build a grant using the existing API contract. */
function nodeGrant(node: Option, includeChildren: boolean): Grant {
  const id = node.key.slice(2)
  const isScheme = node.key.startsWith('s:')
  return { schemeId: isScheme ? id : null, categoryId: isScheme ? null : id, includeChildren, name: node.title }
}

/** 勾选整支，取消时拆分覆盖该节点的上级授权，保留同级分支。 / Toggle a branch and split ancestor grants around an excluded subtree. */
function toggleBranch(_: unknown, event: { checked: boolean; node: { key: string | number } }) {
  const target = nodeIndex.value.get(String(event.node.key))
  if (!target) return
  const targetKey = target.key
  const targets = new Set(subtree(target).map(node => node.key))
  const next = new Map<string, Grant>()
  /** 收集当前分类分支及其子级中被选中的授权节点。 / Collect selected grants from the current category branch and its descendants. */
  function keep(grant: Grant) {
    const key = grantKey(grant)
    if (!next.get(key)?.includeChildren) next.set(key, grant)
  }
  /** 保留当前分支以外的既有选择，避免局部编辑误删其他权限。 / Preserve existing selections outside the edited branch. */
  function keepOutside(node: Option) {
    if (node.key === targetKey) return
    if (!subtree(node).some(item => item.key === targetKey)) {
      keep(nodeGrant(node, true))
      return
    }
    if (node.key.startsWith('c:')) keep(nodeGrant(node, false))
    node.children.forEach(keepOutside)
  }
  for (const grant of grants.value) {
    const key = grantKey(grant)
    if (targets.has(key)) continue
    const node = nodeIndex.value.get(key)
    if (!event.checked && grant.includeChildren && node && subtree(node).some(item => item.key === targetKey)) {
      keepOutside(node)
    } else {
      keep(grant)
    }
  }
  if (event.checked) keep(nodeGrant(target, true))
  grants.value = [...next.values()]
}

/** 仅提交当前模式需要的授权项。 / Submit only grants belonging to the selected mode. */
async function save() {
  if (saving.value || loading.value || error.value) return
  if (mode.value === 'SELECTED' && !grants.value.length) {
    message.warning('请至少添加一项分类授权')
    return
  }
  saving.value = true
  try {
    await foundationRequest('put', `/system/person/${props.userId}/category-scope`, {
      mode: mode.value,
      grants: mode.value === 'SELECTED' ? grants.value.map(({ schemeId, categoryId, includeChildren }) => ({ schemeId, categoryId, includeChildren })) : [],
    })
    message.success('分类权限已保存')
    emit('close')
  } catch (cause) {
    message.error(foundationError(cause))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <a-modal :open="true" :title="`分类权限 · ${name}`" :width="680"
    :mask-closable="false" :keyboard="false" :closable="!saving" :confirm-loading="saving"
    :ok-button-props="{ disabled: loading || !!error }" ok-text="保存" cancel-text="取消"
    @ok="save" @cancel="!saving && emit('close')">
    <a-alert v-if="error" type="error" show-icon :message="error">
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>
    <a-spin v-else :spinning="loading">
      <a-space direction="vertical" :size="16" style="width: 100%">
        <a-alert type="info" show-icon message="未单独设置的人员默认全部可见。需要限制时，可选择指定范围或全部不可见。" />
        <a-radio-group v-model:value="mode" :disabled="saving || loading">
          <a-radio value="ALL">全部可见</a-radio>
          <a-radio value="SELECTED">指定范围</a-radio>
          <a-radio value="NONE">全部不可见</a-radio>
        </a-radio-group>
        <template v-if="mode === 'SELECTED'">
          <div class="scope-tip">勾选上级会选中整个分支，并包含以后新增的下级。可单独取消下级，半选表示只开放该分支的一部分。</div>
          <div class="scope-tree">
            <a-tree v-if="options.length" checkable check-strictly default-expand-all
              :selectable="false" :disabled="saving || loading" :tree-data="options"
              :checked-keys="checkedKeys" @check="toggleBranch">
              <template #title="{ key, title }">
                {{ title }}
                <a-tag v-if="directGrants.get(String(key))?.includeChildren === false">含本级</a-tag>
              </template>
            </a-tree>
            <a-empty v-else description="暂无可配置的分类" />
          </div>
          <div v-if="missingGrants.length">
            <div v-for="grant in missingGrants" :key="grantKey(grant)">
              {{ grant.name }}（已不在分类树中）
              <a-button danger type="link" size="small" :disabled="saving"
                @click="grants = grants.filter(item => grantKey(item) !== grantKey(grant))">移除</a-button>
            </div>
          </div>
        </template>
      </a-space>
    </a-spin>
  </a-modal>
</template>

<style scoped>
.scope-tip { color: #86909c; margin-bottom: 8px; }
.scope-tree { max-height: 420px; overflow-y: auto; border: 1px solid #f0f2f5; border-radius: 6px; padding: 8px 4px; }
</style>
