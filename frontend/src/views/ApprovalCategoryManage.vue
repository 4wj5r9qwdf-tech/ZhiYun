<!--
文件用途：提供织云 ERP ApprovalCategoryManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP ApprovalCategoryManage的查询、编辑、状态展示和用户操作界面。
设计思路：使用 Vue 3 响应式状态与项目公共布局；页面编排交互，服务端负责业务规则和最终授权。
实现方法：加载选项/列表，按当前状态驱动表单和弹窗，调用业务 API 后刷新并反馈结果。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
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
/** 维护框架流程分类树。 / Maintain the workflow engine category tree. */
import { computed, onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'

type Category = { categoryId: string; parentId: string; categoryName: string; orderNum: number; createTime?: string; children?: Category[] }
const loading = ref(false)
const saving = ref(false)
const rows = ref<Category[]>([])
const open = ref(false)
const form = reactive({ categoryId: '', parentId: '0', categoryName: '', orderNum: 0 })
const tree = computed(() => {
  const nodes = rows.value.map(item => ({ ...item, categoryId: String(item.categoryId), parentId: String(item.parentId || '0'), children: [] as Category[] }))
  const byId = new Map(nodes.map(item => [item.categoryId, item]))
  const roots: Category[] = []
  for (const item of nodes) {
    const parent = byId.get(item.parentId)
    if (parent && parent !== item) parent.children.push(item)
    else roots.push(item)
  }
  return roots
})
const parentOptions = computed(() => {
  const descendants = new Set([form.categoryId])
  let size = -1
  while (size !== descendants.size) {
    size = descendants.size
    for (const row of rows.value) if (descendants.has(String(row.parentId))) descendants.add(String(row.categoryId))
  }
  return [{ value: '0', label: '顶级分类' }, ...rows.value.filter(row => !descendants.has(String(row.categoryId))).map(row => ({ value: String(row.categoryId), label: row.categoryName }))]
})
const columns = [{ title: '分类名称', dataIndex: 'categoryName', key: 'categoryName' }, { title: '显示顺序', dataIndex: 'orderNum', key: 'orderNum', width: 120 }, { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 190 }, { title: '操作', key: 'action', width: 220 }]
/** 读取框架分类记录。 / Load workflow categories from the framework. */
async function load() {
  loading.value = true
  try {
    const result: any = await api.get('/workflow/category/list')
    if (result.code !== 200) throw Error(result.msg)
    rows.value = result.data || []
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '分类加载失败') }
  finally { loading.value = false }
}
/** 打开新增或编辑表单。 / Open the create or edit form. */
function edit(row?: Category, parent?: Category) {
  Object.assign(form, { categoryId: row ? String(row.categoryId) : '', parentId: row ? String(row.parentId || '0') : parent ? String(parent.categoryId) : '0', categoryName: row?.categoryName || '', orderNum: row?.orderNum || 0 })
  open.value = true
}
/** 保存分类并保留服务端校验。 / Save a category using the framework validation. */
async function save() {
  const name = form.categoryName.trim()
  if (!name) { message.warning('请输入分类名称'); return }
  saving.value = true
  try {
    const body = { parentId: form.parentId, categoryName: name, orderNum: form.orderNum, ...(form.categoryId ? { categoryId: form.categoryId } : {}) }
    const result: any = form.categoryId ? await api.put('/workflow/category', body) : await api.post('/workflow/category', body)
    if (result.code !== 200) throw Error(result.msg)
    message.success('保存成功'); open.value = false; await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '保存失败') }
  finally { saving.value = false }
}
/** 删除前由框架检查子分类和流程引用。 / Let the framework reject referenced or nonempty categories. */
function remove(row: Category) {
  Modal.confirm({ title: '删除流程类别', content: `确认删除“${row.categoryName}”吗？`, okText: '删除', cancelText: '取消', async onOk() {
    const result: any = await api.delete('/workflow/category/' + row.categoryId)
    if (result.code !== 200) { message.error(result.msg || '删除失败'); return }
    message.success('删除成功'); await load()
  } })
}
/** 导出框架提供的流程类别表格。 / Export workflow categories using the framework endpoint. */
async function exportCategories() {
  try {
    const blob: any = await api.post('/workflow/category/export', null, { responseType: 'blob' })
    if (blob.type.includes('application/json')) {
      const result = JSON.parse(await blob.text())
      if (result.code !== 200) throw Error(result.msg || '导出失败')
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '流程类别.xlsx'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '导出失败') }
}
onMounted(load)
</script>
<template>
  <div class="category-page">
    <a-space class="toolbar"><a-button v-if="can('workflow:category:add')" type="primary" @click="edit()">新增</a-button><a-button v-if="can('workflow:category:export')" @click="exportCategories">导出</a-button><a-button @click="load">刷新</a-button></a-space>
    <a-table row-key="categoryId" :columns="columns" :data-source="tree" :loading="loading" :pagination="false" default-expand-all-rows>
      <template #bodyCell="{ column, record }"><a-space v-if="column.key === 'action'"><a-button v-if="can('workflow:category:add')" type="link" size="small" @click="edit(undefined, record as Category)">新增下级</a-button><a-button v-if="can('workflow:category:edit')" type="link" size="small" @click="edit(record as Category)">编辑</a-button><a-button v-if="can('workflow:category:remove')" type="link" danger size="small" @click="remove(record as Category)">删除</a-button></a-space></template>
    </a-table>
    <a-modal :open="open" :title="form.categoryId ? '编辑流程类别' : '新增流程类别'" :confirm-loading="saving" :mask-closable="false" @ok="save" @cancel="open = false">
      <a-form layout="vertical"><a-form-item label="上级分类" required><a-select v-model:value="form.parentId" show-search option-filter-prop="label" :options="parentOptions" /></a-form-item><a-form-item label="分类名称" required><a-input v-model:value="form.categoryName" :maxlength="30" placeholder="请输入分类名称" /></a-form-item><a-form-item label="显示顺序"><a-input-number v-model:value="form.orderNum" :min="0" style="width:100%" /></a-form-item></a-form>
    </a-modal>
  </div>
</template>
<style scoped>.category-page { padding: 12px; background: white; min-height: 100%; }.toolbar { margin-bottom: 12px; }</style>
