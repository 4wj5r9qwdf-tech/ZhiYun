<!--
文件用途：提供织云 ERP TagManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP TagManage的查询、编辑、状态展示和用户操作界面。
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
import '../assets/standard-list-fill.css'
import { computed, nextTick, onActivated, onMounted, onBeforeUnmount, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import { addTag, addTagGroup, deleteTag, deleteTagGroup, editTag, editTagGroup, getTagGroups, getTagList, getTagModules } from '../api/tag'
import { MODULES, MODULE_FUNCTIONS } from '../config/modules'
import { can } from '../utils/perms'

type TagRow = { tagId: string; moduleKey: string; moduleName: string; groupId: string; groupName: string; tagName: string; isCommon: string; status: string }
type TagGroup = { groupId: string; moduleKey: string; groupName: string; isDefault: string; sortOrder: number; tagCount: number }
/** 只保存服务端实际动态登记过的功能，不在前端预置空功能。 */
const moduleOptions = ref<{ moduleKey: string; label: string; tagCount: number }[]>([])
/** 标签分组归属于具体功能命名空间；左侧树按系统模块→功能→分组呈现。 */
const groupsByModule = ref<Record<string, TagGroup[]>>({})
const moduleKey = ref('')
const selectedModule = ref('')
const selectedTreeKey = ref('')
const selectedGroup = ref<{ moduleKey: string; groupId: string } | null>(null)
const keyword = ref('')
const status = ref('')
const rows = ref<TagRow[]>([])
const loading = ref(false)
const visible = ref(false)
const editing = ref(false)
/** 选择已有模块或手动新建模块，避免同一名称出现两个输入框。 */
const creatingModule = ref(false)
const form = ref({ tagId: '', moduleKey: '', moduleName: '', groupId: '', tagName: '', isCommon: '0', status: '0' })
const formGroups = ref<TagGroup[]>([])
const groupDialogOpen = ref(false)
const groupFormOpen = ref(false)
const groupEditing = ref(false)
const groupForm = ref({ groupId: '', moduleKey: '', groupName: '' })
const managedGroups = ref<TagGroup[]>([])
const managedModule = ref<{ moduleKey: string; label: string } | null>(null)
const pageNum = ref(1)
const pageSize = ref(20)
const selectedRowKeys = ref<string[]>([])
const batchEditOpen = ref(false)
const batchStatus = ref('unchanged')
const batchIsCommon = ref('unchanged')
const colSetOpen = ref(false)
const treeCollapsed = ref(false)
/** 表格字段定义；序号列固定显示，其余列由用户列设置控制显隐和顺序。 */
const allColumns = [
  { key: 'moduleName', title: '功能名称', dataIndex: 'moduleName', width: 180 },
  { key: 'groupName', title: '标签分组', dataIndex: 'groupName', width: 170 },
  { key: 'tagName', title: '标签名称', dataIndex: 'tagName', width: 220 },
  { key: 'isCommon', title: '常用提示', dataIndex: 'isCommon', width: 140 },
  { key: 'status', title: '状态', dataIndex: 'status', width: 120 },
]
// v2 清除旧版曾把剩余宽度挤进操作列的错误宽度记录。
const colStoreKey = computed(() => `tag_list_v2:${localStorage.getItem('userId') || 'anon'}`)
const colState = ref<{ key: string; visible: boolean; width: number }[]>([])
const hadStoredWidths = ref(false)
let widthObserver: ResizeObserver | undefined
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 150)
const columns = computed(() => [
  { title: '序号', key: 'index', width: 64, fixed: 'left' as const, align: 'center' as const, customHeaderCell: () => ({ class: 'zy-th-noresize' }) },
  ...colState.value.filter(c => c.visible).map(c => ({ ...allColumns.find(item => item.key === c.key)!, width: c.width,
    sorter: c.key === 'tagName',
    customHeaderCell: () => ({ onMousedown: (event: MouseEvent) => onHeaderMouseDown(c.key, event), onClick: onHeaderClick }),
  })),
  { title: '操作', key: 'action', width: opsWidth.value, fixed: 'right' as const,
    customHeaderCell: () => ({ class: 'ops-th', onMousedown: (event: MouseEvent) => onHeaderMouseDown('ops', event), onClick: onHeaderClick }) },
])
/** 让表格宽度服从容器与当前列宽总和，避免 max-content 按空白区内容把表格撑出视口。 */
const tableScrollWidth = computed(() => Math.max(1, 64 + 48 + opsWidth.value + colState.value.filter(c => c.visible).reduce((sum, c) => sum + c.width, 0)))
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => { selectedRowKeys.value = keys.map(String) },
}))
const filteredRows = computed(() => rows.value.filter(row =>
  (!keyword.value.trim() || row.tagName.toLowerCase().includes(keyword.value.trim().toLowerCase()))
  && (!status.value || row.status === status.value)))
const total = computed(() => filteredRows.value.length)
const pagedRows = computed(() => filteredRows.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))

/** 按当前登录用户恢复列顺序和显隐，避免不同账号之间共享页面偏好。 */
function loadColState() {
  try {
    const saved = JSON.parse(localStorage.getItem(colStoreKey.value) || '[]') as { key: string; visible: boolean; width?: number }[]
    hadStoredWidths.value = localStorage.getItem(`${colStoreKey.value}:manual_widths`) === '1'
    const valid = new Set(allColumns.map(item => item.key))
    colState.value = saved.filter(item => valid.delete(item.key)).map(item => ({
      ...item,
      width: hadStoredWidths.value && Number(item.width) > 0 ? Number(item.width) : allColumns.find(column => column.key === item.key)!.width,
    }))
    for (const item of allColumns) if (valid.has(item.key)) colState.value.push({ key: item.key, visible: true, width: item.width })
  } catch {
    hadStoredWidths.value = false
    colState.value = allColumns.map(item => ({ key: item.key, visible: true, width: item.width }))
  }
}
function saveColState() { localStorage.setItem(colStoreKey.value, JSON.stringify(hadStoredWidths.value ? colState.value : colState.value.map(({ key, visible }) => ({ key, visible })))) }
/** 切换单列显隐后立即持久化当前账号的偏好。 */
async function toggleCol(key: string) {
  const item = colState.value.find(col => col.key === key)
  if (item) { item.visible = !item.visible; saveColState(); await nextTick(); if (!hadStoredWidths.value) autoFitCols() }
}
/** 按列设置面板中的上下按钮调整显示顺序；固定序号列不参与排序。 */
function moveCol(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= colState.value.length) return
  const [item] = colState.value.splice(index, 1)
  colState.value.splice(target, 0, item)
  saveColState()
}
/** 恢复所有业务列的默认顺序和显示状态。 */
async function showAllCols() {
  hadStoredWidths.value = false
  localStorage.removeItem(`${colStoreKey.value}:manual_widths`)
  localStorage.removeItem(`${colStoreKey.value}:ops_w`)
  colState.value = allColumns.map(item => ({ key: item.key, visible: true, width: item.width }))
  saveColState()
  await nextTick()
  autoFitCols()
}
/** 折叠树会改变表格可用宽度，等布局完成后重新按剩余空间分配普通列宽。 */
async function toggleTree() {
  treeCollapsed.value = !treeCollapsed.value
  await nextTick()
  if (!hadStoredWidths.value) autoFitCols()
}
/** 表格表头右缘拖动调整列宽；操作列左右边缘都可调整并按用户保存。 */
let suppressHeaderClickUntil = 0
function onColResize(key: string, width: number) {
  hadStoredWidths.value = true
  localStorage.setItem(`${colStoreKey.value}:manual_widths`, '1')
  const visibleColumns = colState.value.filter(item => item.visible && item.key !== key)
  const compensate = (delta: number) => {
    // 保持总列宽不变：放大目标列时从其他业务列回收宽度，缩小时把空间分给其他业务列。
    if (!visibleColumns.length || delta === 0) return
    if (delta < 0) {
      const share = Math.floor(-delta / visibleColumns.length)
      let remainder = -delta - share * visibleColumns.length
      for (const item of visibleColumns) { item.width += share + (remainder-- > 0 ? 1 : 0) }
      return
    }
    let remaining = delta
    for (const item of visibleColumns) {
      const take = Math.min(remaining, Math.max(0, item.width - 80))
      item.width -= take
      remaining -= take
      if (!remaining) break
    }
  }
  if (key === 'ops') {
    const nextWidth = Math.max(120, Math.round(width))
    compensate(nextWidth - opsWidth.value)
    opsWidth.value = nextWidth
    localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value))
    saveColState()
    return
  }
  const column = colState.value.find(item => item.key === key)
  if (column) {
    const nextWidth = Math.max(80, Math.round(width))
    compensate(nextWidth - column.width)
    column.width = nextWidth
    saveColState()
  }
}
function onHeaderMouseDown(key: string, event: MouseEvent) {
  if (key === 'index') return
  const th = event.currentTarget as HTMLTableCellElement
  const rect = th.getBoundingClientRect()
  const ops = key === 'ops'
  const fromOpsLeft = ops && event.clientX <= rect.left + 16
  const prevToOpsLeft = !ops && !!th.nextElementSibling?.classList.contains('ops-th') && event.clientX >= rect.right - 14
  if (ops && !fromOpsLeft && event.clientX < rect.right - 16) return
  if (!ops && !prevToOpsLeft && event.clientX < rect.right - 14) return
  const resizeKey = prevToOpsLeft ? 'ops' : key
  const fromLeft = fromOpsLeft || prevToOpsLeft
  const startX = event.clientX
  const startWidth = resizeKey === 'ops' ? opsWidth.value : rect.width
  const onMove = (moveEvent: MouseEvent) => onColResize(resizeKey, resizeKey === 'ops' && fromLeft ? startWidth - (moveEvent.clientX - startX) : startWidth + (moveEvent.clientX - startX))
  const onUp = () => {
    document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''; suppressHeaderClickUntil = Date.now() + 300
  }
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'; event.preventDefault(); event.stopPropagation()
}
function onHeaderClick(event: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { event.preventDefault(); event.stopPropagation() }
}
/** 操作列贴合按钮内容，其余可见列先满足表头最小宽度，再均分表格剩余空间。 */
function autoFitCols() {
  const buttons = document.querySelector('.tag-table .ops-btns')
  if (buttons) {
    const buttonWidths = [...buttons.querySelectorAll('button')].map(button => button.getBoundingClientRect().width)
    const gap = Math.max(0, buttonWidths.length - 1) * 6
    opsWidth.value = Math.max(120, Math.ceil(buttonWidths.reduce((sum, width) => sum + width, 0) + gap + 24))
  }
  const visibleColumns = colState.value.filter(column => column.visible)
  let usedWidth = 64 + 48 + opsWidth.value // 序号列、选择框列、固定操作列
  for (const column of visibleColumns) {
    const title = colTitle(column.key)
    column.width = Math.max(90, title.length * 16 + 48)
    usedWidth += column.width
  }
  const mainWidth = Math.round(document.querySelector('.tag-main')?.clientWidth || 0)
  if (mainWidth > usedWidth && visibleColumns.length) {
    const extra = Math.floor((mainWidth - usedWidth) / visibleColumns.length)
    for (const column of visibleColumns) column.width += extra
  }
}
function colTitle(key: string) { return allColumns.find(item => item.key === key)?.title || key }
const legacyFunctionAliases: Record<string, string> = {
  customer: 'customer_master',
  size: 'system_size',
  size_group: 'system_size_group',
  warehouse_master: 'inventory_warehouse',
}
function functionForModuleKey(moduleKey: string) {
  const canonicalKey = legacyFunctionAliases[moduleKey] || moduleKey
  return MODULE_FUNCTIONS.find(func => func.key === canonicalKey)
}
const selectedManageModule = computed(() => {
  if (selectedGroup.value) return moduleOptions.value.find(item => item.moduleKey === selectedGroup.value?.moduleKey) || null
  const actual = moduleKey.value && !moduleKey.value.startsWith('function:')
    ? moduleOptions.value.filter(item => item.moduleKey === moduleKey.value)
    : moduleKey.value.startsWith('function:')
      ? moduleOptions.value.filter(item => (legacyFunctionAliases[item.moduleKey] || item.moduleKey) === moduleKey.value.slice('function:'.length))
      : []
  return actual.length === 1 ? actual[0] : null
})
const tagTree = computed(() => MODULES.filter(module => module.key !== 'workbench').map(module => ({
  title: module.label, key: `module:${module.key}`, selectable: false,
  children: MODULE_FUNCTIONS.filter(func => func.module === module.key).flatMap(func => {
    const aliases = moduleOptions.value.filter(item => (legacyFunctionAliases[item.moduleKey] || item.moduleKey) === func.key)
    if (!aliases.length) return []
    const groupNodes = aliases.flatMap(item => (groupsByModule.value[item.moduleKey] || []).map(group => ({
      title: group.groupName, key: `group:${group.moduleKey}:${group.groupId}`, tagCount: group.tagCount,
    })))
    return [{ title: func.title, key: `function:${func.key}`, tagCount: aliases.reduce((sum, item) => sum + item.tagCount, 0), children: groupNodes }]
  }),
})).filter(module => module.children.length > 0))
/** 从服务端刷新真实标签归属；首次收录标签的业务模块随后出现在此树中。 */
async function loadModules() {
  try {
    const res: any = await getTagModules()
    moduleOptions.value = (res.data || []).map((item: any) => ({
      moduleKey: String(item.moduleKey), label: String(item.moduleName), tagCount: Number(item.tagCount || 0),
    }))
    const groupResults = await Promise.all(moduleOptions.value.map(async item => {
      const result: any = await getTagGroups(item.moduleKey)
      return [item.moduleKey, (result.data || []).map((group: any) => ({
        ...group, groupId: String(group.groupId), tagCount: Number(group.tagCount || 0),
      }))] as const
    }))
    groupsByModule.value = Object.fromEntries(groupResults)
    const selectedFunctionKey = moduleKey.value.startsWith('function:') ? moduleKey.value.slice('function:'.length) : ''
    const hasSelectedTarget = selectedFunctionKey
      ? moduleOptions.value.some(item => (legacyFunctionAliases[item.moduleKey] || item.moduleKey) === selectedFunctionKey)
      : moduleOptions.value.some(item => item.moduleKey === moduleKey.value)
    if (moduleKey.value && !hasSelectedTarget) {
      moduleKey.value = ''
      selectedTreeKey.value = ''
      selectedGroup.value = null
      pageNum.value = 1
    }
    // 标签管理默认进入一个具体功能，避免首次打开时把所有功能的标签混在一起，
    // 造成分组看起来像全局共用。没有功能可选时才展示空状态。
    if (!moduleKey.value && moduleOptions.value.length) selectDefaultFunction()
    await loadRows()
  } catch {
    moduleOptions.value = []
    groupsByModule.value = {}
    moduleKey.value = ''
    rows.value = []
    message.error('标签归属加载失败')
  }
}

/** 默认选中首个已有标签的功能；重置筛选也回到功能范围，而不是全局汇总。 */
function selectDefaultFunction() {
  const first = moduleOptions.value[0]
  if (!first) return
  const functionKey = legacyFunctionAliases[first.moduleKey] || first.moduleKey
  moduleKey.value = `function:${functionKey}`
  selectedModule.value = functionForModuleKey(functionKey)?.module || ''
  selectedTreeKey.value = `function:${functionKey}`
  selectedGroup.value = null
}

/** 每个业务功能独立维护分组；接口确保功能至少有一个默认分组。 */
async function loadGroups(module: string) {
  if (!module) { formGroups.value = []; return }
  try {
    const res: any = await getTagGroups(module)
    formGroups.value = (res.data || []).map((item: any) => ({ ...item, groupId: String(item.groupId) }))
  } catch {
    formGroups.value = []
    message.error('标签分组加载失败')
  }
}
/** 功能节点查看该功能全部标签；分组节点精确筛选功能和分组。导航模块节点只负责展开。 */
function onTreeSelect(keys: (string | number)[]) {
  const selected = String(keys[0] || '')
  selectedTreeKey.value = selected
  selectedGroup.value = null
  if (selected.startsWith('module:')) {
    // 模块层仅作树目录；即使组件回传了模块节点，也定位到其首个具体功能，
    // 不提供跨功能的混合标签/分组视图。
    const rootModule = selected.slice('module:'.length)
    const firstFeature = moduleOptions.value.find(item => functionForModuleKey(item.moduleKey)?.module === rootModule)
    if (firstFeature) {
      const functionKey = legacyFunctionAliases[firstFeature.moduleKey] || firstFeature.moduleKey
      moduleKey.value = `function:${functionKey}`
      selectedTreeKey.value = `function:${functionKey}`
      selectedModule.value = rootModule
      searchRows()
    } else {
      selectedTreeKey.value = ''
      selectedGroup.value = null
    }
  } else if (selected.startsWith('function:')) {
    const functionKey = selected.slice('function:'.length)
    moduleKey.value = `function:${functionKey}`
    selectedModule.value = functionForModuleKey(functionKey)?.module || ''
    searchRows()
  } else if (selected.startsWith('group:')) {
    const groupKey = selected.slice('group:'.length)
    const splitAt = groupKey.lastIndexOf(':')
    const targetModuleKey = groupKey.slice(0, splitAt)
    const groupId = groupKey.slice(splitAt + 1)
    const functionKey = legacyFunctionAliases[targetModuleKey] || targetModuleKey
    selectedGroup.value = { moduleKey: targetModuleKey, groupId }
    moduleKey.value = `function:${functionKey}`
    selectedModule.value = functionForModuleKey(functionKey)?.module || ''
    searchRows()
  }
}

/** 请求当前功能列表；未选功能时合并所有已登记功能的数据。 */
async function loadRows() {
  loading.value = true
  try {
    const selectedModules = moduleKey.value.startsWith('function:')
      ? moduleOptions.value.filter(item => (legacyFunctionAliases[item.moduleKey] || item.moduleKey) === moduleKey.value.slice('function:'.length))
      : moduleOptions.value.filter(item => {
        const func = functionForModuleKey(item.moduleKey)
        return !selectedModule.value || func?.module === selectedModule.value
      })
    const results = await Promise.all(selectedModules.map(item => getTagList(
      item.moduleKey, keyword.value, status.value,
      selectedGroup.value?.moduleKey === item.moduleKey ? selectedGroup.value.groupId : '',
    )))
    // API 的 tagId 可能以数字返回；与 rowSelection 内统一的字符串 key 对齐，
    // 否则工具栏计数会变化，但表格按严格相等匹配时无法显示勾选或取消勾选。
    rows.value = results.flatMap((res: any) => res.data || [])
      .map((row: TagRow) => ({ ...row, tagId: String(row.tagId) }))
      .sort((a, b) => a.moduleKey.localeCompare(b.moduleKey) || a.tagName.localeCompare(b.tagName, 'zh-CN'))
    selectedRowKeys.value = []
  } catch {
    message.error('标签列表加载失败')
  } finally {
    loading.value = false
  }
}

/** 搜索或切换模块后从第一页开始，避免旧页码超出新结果集范围。 */
function searchRows() { pageNum.value = 1; loadRows() }
/** 搜索栏和树面板共用重置：清除文本、状态及功能筛选。 */
function resetFilter() { keyword.value = ''; status.value = ''; moduleKey.value = ''; selectedModule.value = ''; selectedTreeKey.value = ''; selectedGroup.value = null; treeCollapsed.value = false; selectedRowKeys.value = []; selectDefaultFunction(); searchRows() }
/** Ant Design Vue 分页回调；数据由本次接口全量返回，分页仅在当前模块结果集内进行。 */
function onPageChange(page: number, size: number) { pageNum.value = page; pageSize.value = size }
function onTableChange(_pagination: unknown, _filters: unknown, sorter: any) {
  if (sorter?.order) rows.value = [...rows.value].sort((a, b) => a.tagName.localeCompare(b.tagName, 'zh-CN') * (sorter.order === 'ascend' ? 1 : -1))
}
/** 状态开关沿用编辑接口，确保状态变更受服务端权限与业务校验约束。 */
async function toggleStatus(row: TagRow, enabled: unknown) {
  const isEnabled = enabled === true
  try {
    await editTag({ ...row, status: isEnabled ? '0' : '1' })
    message.success(isEnabled ? '标签已启用' : '标签已停用')
    await loadRows()
  } catch (error: any) { message.error(error?.response?.data?.msg || '状态更新失败') }
}
function showTotalText(count: number) { return `共 ${count} 条` }

/** 新增时默认使用当前树节点；保存后允许切换到表单选定的模块查看新标签。 */
async function openAdd() {
  editing.value = false
  creatingModule.value = moduleOptions.value.length === 0
  const selectedFunctionKey = moduleKey.value.startsWith('function:') ? moduleKey.value.slice('function:'.length) : ''
  const featureMatches = selectedFunctionKey
    ? moduleOptions.value.filter(item => (legacyFunctionAliases[item.moduleKey] || item.moduleKey) === selectedFunctionKey)
    : []
  const chosenModuleKey = selectedGroup.value?.moduleKey
    || (selectedFunctionKey && featureMatches.length === 1 ? featureMatches[0].moduleKey : moduleKey.value.startsWith('function:') ? '' : moduleKey.value)
  form.value = { tagId: '', moduleKey: chosenModuleKey, moduleName: moduleOptions.value.find(item => item.moduleKey === chosenModuleKey)?.label || '', groupId: '', tagName: '', isCommon: '0', status: '0' }
  await loadGroups(chosenModuleKey)
  form.value.groupId = (selectedGroup.value?.moduleKey === chosenModuleKey ? selectedGroup.value.groupId : '')
    || formGroups.value.find(item => item.isDefault === '1')?.groupId || ''
  visible.value = true
}

/** 编辑只维护当前标签属性；功能归属在对话框中展示为不可修改。 */
async function openEdit(row: TagRow) {
  editing.value = true
  creatingModule.value = false
  await loadGroups(row.moduleKey)
  form.value = { tagId: String(row.tagId), moduleKey: row.moduleKey, moduleName: moduleOptions.value.find(item => item.moduleKey === row.moduleKey)?.label || '', groupId: String(row.groupId), tagName: row.tagName, isCommon: row.isCommon, status: row.status }
  visible.value = true
}

/** 切换功能录入方式时清理旧值，避免新功能误用已有标识。 */
function switchModuleMode() {
  creatingModule.value = !creatingModule.value
  const selected = creatingModule.value ? undefined : (moduleOptions.value.find(item => item.moduleKey === moduleKey.value) || moduleOptions.value[0])
  form.value.moduleKey = selected?.moduleKey || ''
  form.value.moduleName = selected?.label || ''
  void loadGroups(form.value.moduleKey).then(() => { form.value.groupId = formGroups.value.find(item => item.isDefault === '1')?.groupId || '' })
}

/** 表单切换现有功能时同步加载其分组，并默认选中兜底组。 */
async function selectFormModule(value: string) {
  form.value.moduleKey = value
  form.value.moduleName = moduleOptions.value.find(item => item.moduleKey === value)?.label || ''
  await loadGroups(value)
  form.value.groupId = formGroups.value.find(item => item.isDefault === '1')?.groupId || ''
}

/** 只允许在单一业务功能上下文中管理分组，避免把分组误建到跨功能汇总视图。 */
async function openGroupManager() {
  if (!selectedManageModule.value) return
  managedModule.value = selectedManageModule.value
  await loadGroups(managedModule.value.moduleKey)
  managedGroups.value = [...formGroups.value]
  groupDialogOpen.value = true
}

function openGroupAdd() {
  if (!managedModule.value) return
  groupEditing.value = false
  groupForm.value = { groupId: '', moduleKey: managedModule.value.moduleKey, groupName: '' }
  groupFormOpen.value = true
}

function openGroupEdit(group: TagGroup) {
  if (group.isDefault === '1') return
  groupEditing.value = true
  groupForm.value = { groupId: group.groupId, moduleKey: group.moduleKey, groupName: group.groupName }
  groupFormOpen.value = true
}

async function saveGroup() {
  const groupName = groupForm.value.groupName.trim()
  if (!groupName) return message.warning('请输入分组名称')
  try {
    if (groupEditing.value) await editTagGroup({ ...groupForm.value, groupName })
    else await addTagGroup({ moduleKey: groupForm.value.moduleKey, groupName })
    message.success(groupEditing.value ? '分组已更新' : '分组已创建')
    groupFormOpen.value = false
    await loadGroups(groupForm.value.moduleKey)
    managedGroups.value = [...formGroups.value]
    if (form.value.moduleKey === groupForm.value.moduleKey) form.value.groupId ||= formGroups.value.find(item => item.groupName === groupName)?.groupId || ''
  } catch (error: any) { message.error(error?.response?.data?.msg || '分组保存失败') }
}

function confirmDeleteGroup(group: TagGroup) {
  Modal.confirm({
    title: '删除标签分组', content: `确定删除分组“${group.groupName}”吗？分组必须为空才能删除。`,
    okText: '确定删除', cancelText: '取消', okType: 'danger',
    onOk: async () => {
      await deleteTagGroup(group.groupId)
      message.success('分组已删除')
      if (managedModule.value) await loadGroups(managedModule.value.moduleKey)
      managedGroups.value = [...formGroups.value]
    },
  })
}

/** 表格插槽将行推断为通用对象，此处收窄为标签档案类型供编辑和停用操作使用。 */
function asTagRow(row: unknown): TagRow {
  return row as TagRow
}
/** 让表格行键与 rowSelection 的字符串 ID 使用同一类型，避免勾选状态严格比较失败。 */
function rowKey(row: TagRow) { return String(row.tagId) }

async function save() {
  const tagName = form.value.tagName.trim()
  if (!tagName) return message.warning('请输入标签名')
  try {
    if (editing.value) {
      await editTag({ ...form.value, tagName })
    } else {
      const moduleName = form.value.moduleName.trim()
      if (!moduleName) return message.warning('请输入模块名称')
      if (!/^[a-z][a-z0-9_]{0,49}$/.test(form.value.moduleKey)) return message.warning('模块标识须以小写字母开头，只能包含小写字母、数字和下划线')
      if (creatingModule.value && moduleOptions.value.some(item => item.moduleKey === form.value.moduleKey)) return message.warning('模块标识已存在，请选择已有模块')
      await addTag({ moduleKey: form.value.moduleKey, moduleName, groupId: form.value.groupId || undefined, tagName, isCommon: form.value.isCommon })
      moduleKey.value = form.value.moduleKey
    }
    message.success(editing.value ? '标签已更新' : '标签已创建')
    visible.value = false
    await loadModules()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || '保存失败')
  }
}

/** 删除未被业务记录引用的标签；后端会在事务内再次校验引用关系。 */
function confirmDelete(row: TagRow) {
  Modal.confirm({
    title: '删除标签',
    content: `确定删除标签“${row.tagName}”吗？已被业务记录使用的标签不能删除，请改为停用。`,
    okText: '确定删除',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      await deleteTag(String(row.tagId))
      message.success('标签已删除')
      await loadModules()
    },
  })
}

/** 打开批量编辑；默认保持字段原值，只有明确选择的字段才会写入。 */
function openBatchEdit() {
  const selected = rows.value.filter(row => selectedRowKeys.value.includes(String(row.tagId)))
  if (!selected.length) return
  batchStatus.value = 'unchanged'
  batchIsCommon.value = 'unchanged'
  batchEditOpen.value = true
}

/** 批量更新选中标签；只提交用户在弹窗中明确指定的字段。 */
async function saveBatchEdit() {
  const selected = rows.value.filter(row => selectedRowKeys.value.includes(String(row.tagId)))
  if (!selected.length) return
  if (batchStatus.value === 'unchanged' && batchIsCommon.value === 'unchanged') return message.warning('请至少选择一项要修改的内容')
  try {
    await Promise.all(selected.map(row => editTag({
      tagId: String(row.tagId), moduleKey: row.moduleKey, groupId: String(row.groupId), tagName: row.tagName,
      isCommon: batchIsCommon.value === 'unchanged' ? row.isCommon : batchIsCommon.value,
      status: batchStatus.value === 'unchanged' ? row.status : batchStatus.value,
    })))
    message.success(`已批量编辑 ${selected.length} 个标签`)
    batchEditOpen.value = false
    selectedRowKeys.value = []
    await loadRows()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || '批量编辑失败，请刷新后核对结果')
    await loadRows()
  }
}

/** 导出当前模块及当前筛选结果为 UTF-8 BOM CSV，供 Excel 正常识别中文。 */
function exportCsv() {
  const quote = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const lines = [['功能名称', '标签分组', '标签名称', '常用提示', '状态'], ...rows.value.map(row => [
    row.moduleName, row.groupName, row.tagName, row.isCommon === '1' ? '是' : '否', row.status === '0' ? '启用' : '停用',
  ])].map(line => line.map(quote).join(','))
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '标签档案.csv'
  link.click()
  URL.revokeObjectURL(url)
}

let activationSeen = false
onActivated(() => {
  if (activationSeen) loadModules()
  activationSeen = true
})
onMounted(async () => {
  loadColState()
  await loadModules()
  await nextTick()
  // 旧版偏好未保存列宽时，仅初始化时自动铺满；保留用户之后手动调过的宽度。
  if (!hadStoredWidths.value) autoFitCols()
  widthObserver = new ResizeObserver(() => { if (!hadStoredWidths.value) autoFitCols() })
  const area = document.querySelector<HTMLElement>('.tag-main')
  if (area) widthObserver.observe(area)
})
onBeforeUnmount(() => widthObserver?.disconnect())
</script>

<template>
  <div class="tag-page zy-list-page">
    <!-- 模板布局：整行筛选条件、整行操作工具栏、下方左侧模块树与右侧列表。 -->
    <div class="page-header">
      <div class="head-filters">
        <label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" placeholder="搜索标签名称" allow-clear style="width: 200px" @pressEnter="searchRows">
          <template #prefix><SearchOutlined style="color:#86909C" /></template>
        </a-input></label>
        <label class="zy-filter-field"><span>状态</span><a-select v-model:value="status" placeholder="状态" allow-clear style="width: 110px" @change="searchRows">
          <a-select-option value="0">启用</a-select-option>
          <a-select-option value="1">停用</a-select-option>
        </a-select></label>
      </div>
    </div>
    <div class="filter-bar">
      <a-button type="primary" @click="openAdd">＋新增标签</a-button>
      <a-button type="primary" @click="searchRows">查询</a-button>
      <a-button @click="resetFilter">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button :disabled="!selectedRowKeys.length" @click="openBatchEdit">批量编辑{{ selectedRowKeys.length ? `（${selectedRowKeys.length}）` : '' }}</a-button>
      <a-button @click="exportCsv">↓ 导出</a-button>
      <a-button v-if="selectedManageModule" @click="openGroupManager">管理当前功能分组</a-button>
      <a-dropdown v-model:open="colSetOpen" :trigger="['click']">
        <a-button>⚙ 列设置</a-button>
        <template #overlay>
          <a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }">
            <div v-for="(col, index) in colState" :key="col.key" class="col-set-item">
              <a-checkbox :checked="col.visible" @change="toggleCol(col.key)">{{ colTitle(col.key) }}</a-checkbox>
              <span class="col-move">
                <a-button type="text" size="small" :disabled="index === 0" @click="moveCol(index, -1)">↑</a-button>
                <a-button type="text" size="small" :disabled="index === colState.length - 1" @click="moveCol(index, 1)">↓</a-button>
              </span>
            </div>
            <a-divider style="margin: 6px 0" />
            <div class="col-set-foot">
              <a @click="showAllCols">恢复默认</a>

              <a-button type="primary" size="small" @click="colSetOpen = false">完成</a-button>
            </div>
          </a-card>
        </template>
      </a-dropdown>
    </div>
    <div class="tag-layout" :class="{ 'tree-collapsed': treeCollapsed }">
      <aside class="tag-tree-panel" :class="{ collapsed: treeCollapsed }">
        <div class="tree-head">
          <span class="tree-title">模块 / 功能 / 分组</span>
          <a class="tree-clear" @click="resetFilter">重置</a>
        </div>
        <div class="tree-body">
          <a-tree :tree-data="tagTree" :field-names="{ title: 'title', key: 'key', children: 'children' }"
            :selected-keys="[selectedTreeKey || moduleKey || (selectedModule ? `module:${selectedModule}` : '')]" default-expand-all block-node @select="onTreeSelect">
            <template #title="{ title, key, tagCount }">
              <span class="tree-node">{{ title }}<span v-if="String(key).startsWith('module:')" class="type-tag">模块</span>
                <span v-else class="tree-count">{{ tagCount ?? 0 }}</span></span>
            </template>
          </a-tree>
          <div v-if="!moduleOptions.length" class="tree-empty-hint">
            <div>暂无标签功能。可在此手动建立功能归属，也可在业务功能首次添加标签时自动登记。</div>
            <a-button type="link" size="small" @click="openAdd">立即新增标签</a-button>
          </div>
        </div>
      </aside>
      <button type="button" class="tree-toggle" :class="{ 'at-left': treeCollapsed }" :aria-label="treeCollapsed ? '展开树' : '收起树'" :title="treeCollapsed ? '展开模块树' : '收起模块树'" @click="toggleTree" />
      <main class="tag-main">
        <a-table :columns="columns" :data-source="pagedRows" :loading="loading" :pagination="{ current: pageNum, pageSize, total, showSizeChanger: true, showQuickJumper: true, showTotal: showTotalText, onChange: onPageChange, hideOnSinglePage: false }"
          :row-selection="rowSelection" :scroll="{ x: tableScrollWidth, y: 'calc(100vh - 360px)' }" table-layout="fixed" :row-key="rowKey" size="middle" class="tag-table zy-fill-table" @change="onTableChange">
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'index'">{{ (pageNum - 1) * pageSize + index + 1 }}</template>
            <template v-else-if="column.dataIndex === 'isCommon'">{{ record.isCommon === '1' ? '是' : '否' }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-switch :checked="record.status === '0'" checked-children="启用" un-checked-children="停用" @change="(value: any) => toggleStatus(asTagRow(record), value)" />
            </template>
            <template v-else-if="column.key === 'action'">
              <div class="ops-btns"><a-button type="primary" size="small" @click="openEdit(asTagRow(record))">编辑</a-button>
                <a-button v-if="can('system:tag:remove')" danger size="small" @click="confirmDelete(asTagRow(record))">删除</a-button></div>
            </template>
          </template>
          <template #summary>
            <a-table-summary fixed><a-table-summary-row>
              <a-table-summary-cell :index="0" :col-span="columns.length + 1" class="summary-cell">合计（筛选结果）　{{ total }} 条</a-table-summary-cell>
            </a-table-summary-row></a-table-summary>
          </template>
        </a-table>
      </main>
    </div>
    <!-- 新增允许选择功能归属；编辑锁定功能，避免迁移标签导致历史关联语义改变。 -->
    <a-modal v-model:open="visible" :title="editing ? '编辑标签' : '新增标签'" :mask-closable="false" :keyboard="false" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="功能归属" required>
          <a-input v-if="editing || creatingModule" v-model:value="form.moduleName" :disabled="editing" placeholder="请输入新功能名称" :maxlength="100" />
          <a-select v-else v-model:value="form.moduleKey" show-search option-filter-prop="label" placeholder="选择已有功能" @change="selectFormModule(String($event))">
            <a-select-option v-for="item in moduleOptions" :key="item.moduleKey" :value="item.moduleKey" :label="item.label">{{ item.label }}</a-select-option>
          </a-select>
          <template v-if="!editing && moduleOptions.length" #extra><a @click="switchModuleMode">{{ creatingModule ? '选择已有功能' : '新增功能归属' }}</a></template>
        </a-form-item>
        <a-form-item v-if="!editing" label="功能标识" required><a-input v-model:value="form.moduleKey" :disabled="!creatingModule" placeholder="英文标识，如 customer_master" /><div class="field-hint">供对应业务功能调用，使用小写字母、数字和下划线；选择已有功能时会自动沿用。</div></a-form-item>
        <a-form-item label="标签分组" required>
          <a-select v-model:value="form.groupId" :disabled="creatingModule" placeholder="请选择标签分组">
            <a-select-option v-for="item in formGroups" :key="item.groupId" :value="item.groupId">{{ item.groupName }}</a-select-option>
          </a-select>
          <div v-if="creatingModule" class="field-hint">新功能和标签将先归入默认分组，创建后可在分组管理中调整。</div>
        </a-form-item>
        <a-form-item label="标签名称" required><a-input v-model:value="form.tagName" :maxlength="100" /></a-form-item>
        <a-form-item label="常用提示"><a-switch v-model:checked="form.isCommon" checked-value="1" un-checked-value="0" /></a-form-item>
        <a-form-item v-if="editing" label="状态">
          <a-select v-model:value="form.status">
            <a-select-option value="0">启用</a-select-option>
            <a-select-option value="1">停用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="groupDialogOpen" :title="`管理功能分组${managedModule ? ' · ' + managedModule.label : ''}`" :footer="null" destroy-on-close>
      <div class="group-manager-head">
        <span class="field-hint">分组只在当前业务功能内生效。默认分组不可删除或重命名；其他分组需先移空才能删除。</span>
        <a-button type="primary" @click="openGroupAdd">新增分组</a-button>
      </div>
      <a-list :data-source="managedGroups" bordered>
        <template #renderItem="{ item }">
          <a-list-item>
            <span>{{ item.groupName }}<a-tag v-if="item.isDefault === '1'" color="blue" class="default-group-tag">默认</a-tag></span>
            <template #actions>
              <a v-if="item.isDefault !== '1'" @click="openGroupEdit(item)">重命名</a>
              <a v-if="item.isDefault !== '1'" class="danger-link" @click="confirmDeleteGroup(item)">删除</a>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>
    <a-modal v-model:open="groupFormOpen" :title="groupEditing ? '重命名分组' : '新增分组'" ok-text="保存" cancel-text="取消" @ok="saveGroup">
      <a-form layout="vertical"><a-form-item label="分组名称" required><a-input v-model:value="groupForm.groupName" :maxlength="100" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="batchEditOpen" title="批量编辑标签" ok-text="保存" cancel-text="取消" @ok="saveBatchEdit">
      <a-form layout="vertical">
        <a-form-item label="常用提示">
          <a-select v-model:value="batchIsCommon">
            <a-select-option value="unchanged">保持原值</a-select-option>
            <a-select-option value="1">是</a-select-option>
            <a-select-option value="0">否</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="batchStatus">
            <a-select-option value="unchanged">保持原值</a-select-option>
            <a-select-option value="0">启用</a-select-option>
            <a-select-option value="1">停用</a-select-option>
          </a-select>
        </a-form-item>
        <a-typography-text type="secondary">将修改所选的 {{ selectedRowKeys.length }} 个标签；保持原值的项目不会变更。</a-typography-text>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.zy-list-page { height: 100%; min-height: 0; display: flex; flex-direction: column; }
.tag-layout { flex: 1 1 0; min-height: 0 !important; }
.tag-main { display: flex; flex-direction: column; min-height: 0; }
.tag-page { padding: 0; }
.page-header { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.head-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tag-layout { position: relative; display: flex; gap: 0; align-items: stretch; min-height: calc(100vh - 300px); }

.tag-tree-panel { width: 260px; flex: 0 0 260px; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; overflow: hidden; transition: width .2s, opacity .2s; margin-right: 8px; }
.tag-tree-panel.collapsed { width: 0; flex-basis: 0; min-width: 0; opacity: 0; margin-right: 0; pointer-events: none; }
.tree-head { display: flex; align-items: center; min-height: 46px; padding: 10px 12px; border-bottom: 1px solid #F0F2F5; }
.tree-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.tree-clear { margin-left: auto; color: #1E5EFF; cursor: pointer; font-size: 13px; }
.tree-body { flex: 1; overflow: auto; padding: 8px; }
.tree-empty-hint { padding: 10px 8px; color: #86909C; font-size: 12px; line-height: 1.7; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.type-tag { padding: 1px 5px; line-height: 16px; border: 1px solid #D9DDE5; border-radius: 4px; color: #4E5969; font-size: 11px; }
.tree-count { min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: #E8F3FF; color: #1E5EFF; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; }
.tree-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: -4px; z-index: 6; width: 8px; min-width: 0; height: 36px; padding: 0; box-sizing: border-box; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.tree-toggle::before { content: ''; width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }

.tree-toggle.at-left::before { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.tree-toggle:hover { border-color: #1E5EFF; }
.tree-toggle:hover::before { border-right-color: #1E5EFF; }
.tree-toggle.at-left:hover::before { border-left-color: #1E5EFF; border-right-color: transparent; }
.tag-main { flex: 1; min-width: 0; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-spacer { flex: 1 1 auto; min-width: 8px; }
.col-set-panel { width: 220px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }
.tag-table { width: 100%; background: #fff; border-radius: 10px; }
.tag-table :deep(.ant-table) { font-size: 13px; }
.tag-table :deep(.ant-table-thead > tr > th), .tag-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.tag-table :deep(.ant-table-thead > tr > th:last-child), .tag-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
.tag-table :deep(.ant-table-thead > tr > th) { background: #F7F8FA; font-weight: 600; }
.tag-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-btns .ant-btn-sm { padding-inline: 5px; }
.summary-cell { font-weight: 600; background: #F7F8FA; }
.group-manager-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.default-group-tag { margin-left: 8px; }
.danger-link { color: #d9363e; }
</style>

<!-- 非 scoped：通过原生表头右侧拖拽柄调列宽，保持与功能模板实现一致。 -->
<style>
.tag-table .ant-table-thead > tr > th { position: relative; }
.tag-table .ant-table-thead > tr > th:not(.zy-th-noresize):not(.ops-th):not(.ant-table-cell-fix-left-first):not(.ant-table-cell-fix-left-last):not(.ant-table-cell-fix-right-first):not(.ant-table-cell-fix-right-last)::after { content: ''; position: absolute; right: -1px; top: 0; bottom: 0; width: 12px; cursor: col-resize; transition: background .12s; }
.tag-table .ant-table-thead > tr > th:not(.zy-th-noresize):not(.ops-th):not(.ant-table-cell-fix-left-first):not(.ant-table-cell-fix-left-last):not(.ant-table-cell-fix-right-first):not(.ant-table-cell-fix-right-last):hover { border-right-color: #C9CDD4; }
.tag-table .ant-table-thead > tr > th:not(.zy-th-noresize):not(.ops-th):not(.ant-table-cell-fix-left-first):not(.ant-table-cell-fix-left-last):not(.ant-table-cell-fix-right-first):not(.ant-table-cell-fix-right-last):active::after { background: rgba(30,94,255,.15); }
</style>
