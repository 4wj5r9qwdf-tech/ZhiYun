<script setup lang="ts">
import { computed, ref, onMounted, watch, nextTick } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { PlusOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import StandardPicker from '../components/StandardPicker.vue'
import { encodeCsv, parseCsv } from '../utils/csv'
import { downloadFoundationFile, foundationError, foundationRequest } from '../utils/foundation'
import { can } from '../utils/perms'

import { readPrecision, precisionKey, displayDecimal } from '../utils/decimalDisplay'
const bomPrecision = ref(readPrecision().bomUsage)
const rawUsage = ref(false)

type Option = { value: string; label: string }
type Pair = { colorId?: string; sizeId?: string }
type Part = { id: string; label: string; quantity: number; colors: Option[]; sizes: Option[]; excluded: Pair[] }
type Style = { id: string; styleCode: string; name: string; productionMode: string; status: string; categoryId: string; categoryName?: string; customerName?: string }
type PersonOption = { id: string; name: string; jobNo: string; org: string }
type AttributeValues = Record<string, string | boolean | null>
type PickerAttributeTemplate = { code: string; fields: { key: string; label: string; unit?: string }[] }
type MaterialVariant = { attributes?: AttributeValues; id: string; code: string; name: string }
type Material = { templateName?: string; specTemplateCode?: string; specValues?: AttributeValues; variantFields?: string[]; multiSpec?: boolean; variants?: MaterialVariant[]; id: string; code: string; name: string; inventoryUnitName: string; inventoryUnitId: string; materialUnits: { unitId: string; unitName?: string; referenceUnitName?: string; factor?: string; purchase?: boolean; mode: string; inventoryFactor?: string; usage: boolean }[]; categoryId: string; categoryLabel: string; specification?: string; model?: string; brand?: string; fabricWeightGsm?: string; fabricWidthCm?: string; composition?: string; colorsText?: string }
type Rule = Pair & { childId?: string; slot: string; slotId: string; usageType?: string; materialId?: string; materialVariantId?: string; materialColorId?: string;
  usage?: string; omitted: boolean; remark: string; material?: string; unit?: string; materialColor?: string; materialCategory?: string; materialSpec?: string; usageUnitId?: string; usageUnitName?: string; conversionFactor?: string }
type Version = { styleId?: string; styleCode?: string; styleName?: string; productionMode?: string; customerName?: string; categoryName?: string; preparerId?: string; id: string; bomCode: string; version: number; status: string; remark: string; revision: string; creatorName?: string; createTime?: string; updateTime?: string; updaterName?: string; releaserName?: string; releaseTime?: string }
type Context = { nextVersion?: number; usageTypes?: string[]; parts: Part[]; specs: { id: string; colorName: string; sizeName: string }[] }
type Demand = { part?: string; slot?: string; material: string; materialColor: string; unit: string; quantity: string }

const endpoint = '/rd/style-bom'
const styles = ref<Style[]>([])
const styleId = ref<string>()
type StyleCategory = { id: string; schemeId: string; schemeName: string; parentId?: string | null; name: string }
type StyleCategoryData = { rows: StyleCategory[]; restricted: boolean; schemeId: string; invalid: boolean }
type SidebarNode = { key: string; title: string; children: SidebarNode[] }
const styleCategories = ref<StyleCategory[]>([])
const styleScopeUnavailable = ref(false)
const styleScopeRestricted = ref(false)
const styleScopeLoaded = ref(false)
const sidebarCollapsed = ref(false)
const selectedTreeKey = ref('')
const expandedStyleKeys = ref<string[]>([])
const stylePickerOpen = ref(false)
const stylePickerSelected = ref<string[]>([])
const personPickerOpen = ref(false)
const personPickerSelected = ref<string[]>([])
const people = ref<PersonOption[]>([])
const preparerId = ref('')
const loginPreparer = ref<{ id: string; name: string }>()
const preparerLabel = computed(() => {
  const id = preparerId.value
  return people.value.find(person => person.id === id)?.name
    || (id === loginPreparer.value?.id ? loginPreparer.value?.name : undefined)
    || (id === current.value?.preparerId ? current.value?.creatorName : undefined)
    || '请选择制单人'
})
const personPickerItems = computed(() => people.value.map(person => ({ id: person.id, name: person.name, orgId: '', searchText: [person.jobNo,person.name,person.org].join(' ') })))
/** 只提供账号启用且未离职的员工，避免把已停用登录账号或离职人员选作制单人。 */
async function loadPreparerOptions() {
  const result = await foundationRequest<{ rows: any[] }>('get', '/system/person/list?pageNum=1&pageSize=500')
  people.value = (result.rows || [])
    .filter(person => String(person.account_status) === '0' && person.emp_status !== '离职')
    .map(person => ({
      id: String(person.user_id_text || person.user_id),
      name: person.name || person.nick_name || person.job_no || '未命名人员',
      jobNo: person.job_no || '',
      org: person.org_name || '',
    }))
}
const visibleCategoryIds = computed(() => new Set(styleCategories.value.map(item => item.id)))
const visibleStyles = computed(() => styleScopeLoaded.value && !styleScopeUnavailable.value
  ? styles.value.filter(item => visibleCategoryIds.value.has(item.categoryId)) : [])
const stylePickerTree = computed<PickerTreeNode[]>(() => {
  const schemes = new Map<string, PickerTreeNode>()
  const nodes = new Map<string, PickerTreeNode>()
  for (const category of styleCategories.value) {
    if (!schemes.has(category.schemeId)) schemes.set(category.schemeId, {
      orgId: 'scheme:' + category.schemeId, orgName: category.schemeName, children: [],
    })
    nodes.set(category.id, { orgId: category.id, orgName: category.name, children: [] })
  }
  for (const category of styleCategories.value) {
    const parent = category.parentId ? nodes.get(category.parentId) : undefined
    ;(parent || schemes.get(category.schemeId)!).children.push(nodes.get(category.id)!)
  }
  return [...schemes.values()]
})
const categoryFilter = ref<string>()
// 分类筛选包含子分类，并与左侧树的范围取交集。
// Category filter includes descendants and intersects with the sidebar scope.
const categoryFilterTree = computed(() => {
  const convert = (node: PickerTreeNode): any => ({ title: node.orgName, value: node.orgId, key: node.orgId, children: node.children.map(convert) })
  return stylePickerTree.value.map(convert)
})
const bomTree = computed<SidebarNode[]>(() => {
  /** 将服务端分类结构转换为侧栏树节点。 / Convert server category data into sidebar tree nodes. */
  function convert(node: PickerTreeNode): SidebarNode {
    const childCategories = node.children.map(convert)
    const styleNodes = visibleStyles.value.filter(style => style.categoryId === node.orgId)
      .map(style => ({ key: 'style:' + style.id, title: style.styleCode + ' · ' + style.name, children: [] }))
    return { key: node.orgId.startsWith('scheme:') ? node.orgId : 'category:' + node.orgId,
      title: node.orgName, children: [...childCategories, ...styleNodes] }
  }
  return stylePickerTree.value.map(convert)
})
const stylePickerItems = computed(() => visibleStyles.value.map(style => ({
  id: style.id, orgId: style.categoryId, name: style.name, code: style.styleCode,
  category: style.categoryName || '—', customer: style.customerName || '—',
  type: style.productionMode === 'single' ? '单款' : style.productionMode === 'combined' ? '合并生产套装' : '分开生产套装',
  disabled: style.status !== '0',
  searchText: [style.styleCode,style.name,style.categoryName,style.customerName].filter(Boolean).join(' '),
})))
const stylePickerColumns = [
  { title: '款号', dataIndex: 'code', key: 'code' }, { title: '款式名称', dataIndex: 'name', key: 'name' },
  { title: '款式类型', dataIndex: 'type', key: 'type' }, { title: '分类', dataIndex: 'category', key: 'category' },
  { title: '客户', dataIndex: 'customer', key: 'customer' },
]
/** 取得分类节点及所有后代，用于范围筛选。 / Resolve a category and its descendants for scoped filtering. */
function categoryDescendants(id: string) {
  const ids = new Set([id])
  let changed = true
  while (changed) {
    changed = false
    for (const category of styleCategories.value) if (category.parentId && ids.has(category.parentId) && !ids.has(category.id)) {
      ids.add(category.id); changed = true
    }
  }
  return ids
}
const visibleVersions = computed(() => {
  const key = selectedTreeKey.value
  const categoryIds = key.startsWith('category:') ? categoryDescendants(key.slice(9)) : null
  const schemeId = key.startsWith('scheme:') ? key.slice(7) : null
  const categoriesInScheme = schemeId ? new Set(styleCategories.value.filter(category => category.schemeId === schemeId).map(category => category.id)) : null
  const filterIds = categoryFilter.value && !categoryFilter.value.startsWith('scheme:') ? categoryDescendants(categoryFilter.value) : null
  const filterScheme = categoryFilter.value?.startsWith('scheme:') ? categoryFilter.value.slice(7) : null
  const allowed = new Set(visibleStyles.value.filter(style => (!categoryIds || categoryIds.has(style.categoryId))
    && (!categoriesInScheme || categoriesInScheme.has(style.categoryId)) && (!filterIds || filterIds.has(style.categoryId))
    && (!filterScheme || styleCategories.value.some(category => category.id === style.categoryId && category.schemeId === filterScheme))).map(style => style.id))
  return versions.value.filter(version => !!version.styleId && allowed.has(version.styleId))
})
/** 响应左侧款式树选择并刷新对应 BOM 版本。 / Handle style-tree selection and load its BOM versions. */
async function selectBomTree(keys: (string | number)[]) {
  const key = String(keys[0] || '')
  selectedTreeKey.value = key
  styleId.value = key.startsWith('style:') ? key.slice(6) : undefined
  await query()
}
/** 打开款式分类树选择弹窗并加载可访问款式。 / Open the category-tree style picker and load accessible styles. */
function openStylePicker() {
  stylePickerSelected.value = styleId.value ? [styleId.value] : []
  stylePickerOpen.value = true
}
/** 将确认款式设为 BOM 表头款式并加载规格范围。 / Set the confirmed style on the BOM header and load its specification scope. */
async function chooseStyle(ids: string[]) {
  const id = ids[0]
  if (!id || id === styleId.value) return
  const apply = async () => {
    try {
      const next = await foundationRequest<Context>('get', endpoint + '/context/' + id)
      styleId.value = id
      context.value = next
      if (versionNumber.value === suggestedVersion.value) versionNumber.value = next.nextVersion
      suggestedVersion.value = next.nextVersion
      rules.value = []
      selectedSpecs.value = {}
      batchKeys.value = []
      activeGroupId.value = next.parts[0]?.id || ''
    } catch (error) { message.error(foundationError(error)) }
  }
  if (rules.value.length) {
    Modal.confirm({ title: '更换款式？', content: '当前单据中已填写的用料将被清空。',
      okText: '更换并清空', cancelText: '继续编辑', onOk: apply })
  } else await apply()
}
const context = ref<Context>({ parts: [], specs: [] })
const versions = ref<Version[]>([])
const keyword = ref('')
const statusFilter = ref<string>()
const modeFilter = ref<string>()
const listColumns = [
  { key: 'bomCode', title: 'BOM 单号' },
  { key: 'styleCode', title: '款号' }, { key: 'styleName', title: '款式名称' },
  { key: 'productionMode', title: '款式类型' }, { key: 'categoryName', title: '款式分类' },
  { key: 'customerName', title: '客户' }, { key: 'version', title: '版本号', numeric: true },
  { key: 'status', title: '状态' }, { key: 'remark', title: '版本说明' },
  { key: 'creatorName', title: '创建人' }, { key: 'createTime', title: '创建时间' },
  { key: 'updateTime', title: '修改时间' }, { key: 'updaterName', title: '修改人', defaultVisible: false },
]
// 旧列偏好会将新增列追加到末尾；仅迁移一次，之后保留用户自己的排序。
// Move the newly introduced number column once without resetting other preferences.
try {
  const key = `rd_style_bom_list:${localStorage.getItem('userId') || 'anon'}`
  const marker = `${key}:bom-code-first-v1`
  if (!localStorage.getItem(marker)) {
    const saved = JSON.parse(localStorage.getItem(key) || '[]')
    if (Array.isArray(saved) && saved.length) {
      const existing = saved.find((column: { key?: string }) => column?.key === 'bomCode')
      const rest = saved.filter((column: { key?: string }) => column?.key !== 'bomCode')
      localStorage.setItem(key, JSON.stringify([{ ...existing, key: 'bomCode', visible: true }, ...rest]))
    }
    localStorage.setItem(marker, '1')
  }
} catch { /* 列偏好不可用时直接使用默认顺序。 / Fall back to the default order. */ }
const current = ref<Version>()
const remark = ref('')
const bomCode = ref('')
const versionNumber = ref<number | undefined>(undefined)
const suggestedVersion = ref<number | undefined>(undefined)
const autoGeneratedCode = ref('')
const rules = ref<Rule[]>([])
const materials = ref<Material[]>([])
type MaterialCategory = { id: string; parentId?: string | null; schemeId: string; schemeName: string; name: string }
type PickerTreeNode = { orgId: string; orgName: string; children: PickerTreeNode[] }
const materialCategories = ref<MaterialCategory[]>([])
const materialCategoriesRestricted = ref(false)
const materialDefaultInvalid = ref(false)
const materialCategoryScopeLoaded = ref(false)
const allowedMaterialCategoryIds = computed(() => new Set(materialCategories.value.map(item => item.id)))
const materialPickerTree = computed<PickerTreeNode[]>(() => {
  const schemes = new Map<string, PickerTreeNode>()
  const nodes = new Map<string, PickerTreeNode>()
  for (const category of materialCategories.value) {
    if (!schemes.has(category.schemeId)) schemes.set(category.schemeId, {
      orgId: 'scheme:' + category.schemeId, orgName: category.schemeName, children: [],
    })
    nodes.set(category.id, { orgId: category.id, orgName: category.name, children: [] })
  }
  for (const category of materialCategories.value) {
    const parent = category.parentId ? nodes.get(category.parentId) : undefined
    const scheme = schemes.get(category.schemeId)!
    ;(parent || scheme).children.push(nodes.get(category.id)!)
  }
  return [...schemes.values()]
})
const materialPickerOpen = ref(false)
const materialPickerSelected = ref<string[]>([])
const pickerPartId = ref('')
const pickerSpecKeys = ref<string[]>([])
const pickerScope = ref<'selected' | 'all'>('selected')
const pickerSpecs = computed(() => (specsByPart.value[pickerPartId.value] || [])
  .filter(spec => pickerScope.value === 'all' || pickerSpecKeys.value.includes(spec.key)))
/** 生成款式所属分类的完整路径文本。 / Build the full category path for a style. */
function categoryPath(categoryId: string) {
  const byId = new Map(materialCategories.value.map(item => [item.id, item]))
  let current = byId.get(categoryId)
  if (!current) return ''
  const schemeName = current.schemeName
  const names: string[] = []
  const seen = new Set<string>()
  while (current && !seen.has(current.id)) {
    seen.add(current.id)
    names.unshift(current.name)
    current = current.parentId ? byId.get(current.parentId) : undefined
  }
  return [schemeName, ...names].join(' / ')
}
const materialChoices = computed(() => materials.value.filter(item =>
  materialCategoryScopeLoaded.value && allowedMaterialCategoryIds.value.has(item.categoryId)))

/** 规格跟随物料档案，历史停用规格保留回显。 / Offer variants for the selected material and display historical values. */
function hasMaterialVariants(rule: Rule) {
  return !!rule.materialVariantId || !!materials.value.find(item => item.id === rule.materialId)?.multiSpec
}

function variantOptions(rule: Rule) {
  const options = (materials.value.find(item => item.id === rule.materialId)?.variants || [])
    .map(variant => ({ value: variant.id, label: variant.code === variant.name ? variant.name : variant.code + ' · ' + variant.name }))
  if (rule.materialVariantId && !options.some(item => item.value === rule.materialVariantId)) {
    options.unshift({ value: rule.materialVariantId, label: rule.materialSpec || '已选规格' })
  }
  return options
}

/** 更换规格时保留同一物料的颜色、单位和用量。 / Change only the variant and its display label. */
function setMaterialVariant(rule: Rule, id?: string) {
  if (!id) {
    rule.materialVariantId = undefined
    rule.materialSpec = undefined
    return
  }
  const variant = variantOptions(rule).find(item => item.value === id)
  if (!variant) return
  rule.materialVariantId = id
  rule.materialSpec = variant.label
}

// 所有目标规格均已有的物料才禁选，部分已有时只补到缺少的规格。
// Disable a material only when every target already uses it.
const usedMaterialIds = computed(() => {
  const targets = pickerSpecs.value.map(spec => new Set(matchedRules(pickerPartId.value, spec)
    .flatMap(rule => !rule.omitted && rule.materialId && (pickerScope.value === 'all' || !rule.materialVariantId) ? [rule.materialId] : [])))
  return new Set([...(targets[0] || [])].filter(id => targets.every(ids => ids.has(id))))
})
const pickerAttributeTemplates = ref<PickerAttributeTemplate[]>([])

/** 按模板展示共用和规格属性的名称与单位。 / Format attributes using the material template. */
function pickerAttributeLines(item: Material, variant?: MaterialVariant) {
  const fields = pickerAttributeTemplates.value.find(template => template.code === item.specTemplateCode)?.fields || []
  const values: Record<string, string | boolean | null | undefined> | undefined = variant ? variant.attributes : { ...item.specValues,
    fabricWeightGsm: item.fabricWeightGsm, fabricWidthCm: item.fabricWidthCm, composition: item.composition }
  return fields.flatMap(field => {
    if (Boolean(item.variantFields?.includes(field.key)) !== Boolean(variant)) return []
    const value = values?.[field.key]
    return value == null || value === '' ? [] : [field.label + '：' + (typeof value === 'boolean' ? (value ? '是' : '否') : value)
      + (field.unit ? ' ' + field.unit : '')]
  })
}

const materialPickerItems = computed(() => materialChoices.value.map(item => {
  const variants = (item.variants || []).map(variant => ({
    title: variant.code === variant.name ? variant.name : variant.name + '（' + variant.code + '）',
    attributes: pickerAttributeLines(item, variant),
  }))
  const colors = item.colorsText?.split('、').filter(Boolean) || []
  const attributes = pickerAttributeLines(item)
  const unitDetails = [item.inventoryUnitName + ' · 库存单位', ...item.materialUnits.map(unit => {
    const name = unit.unitName || units.value.find(option => option.id === unit.unitId)?.name || unit.unitId
    const uses = [unit.purchase ? '采购' : '', unit.usage ? '生产及领用' : ''].filter(Boolean).join('、')
    const conversion = unit.mode === 'VARIABLE' ? '按实际数量' : '1 ' + name + ' = ' + unit.factor + ' ' + (unit.referenceUnitName || item.inventoryUnitName)
    return [name, uses, conversion].filter(Boolean).join(' · ')
  })]
  return {
    id: item.id, orgId: item.categoryId, name: item.name, code: item.code,
    disabled: usedMaterialIds.value.has(item.id),
    selectionStatus: usedMaterialIds.value.has(item.id) ? '已添加' : '',
    category: categoryPath(item.categoryId) || item.categoryLabel,
    variants, colors, attributes, unitDetails,
    variantSummary: item.multiSpec ? (variants.length ? variants.length + ' 个规格' : '无启用规格') : '单规格',
    colorSummary: colors.length ? colors.length + ' 色' : '—',
    unitSummary: item.inventoryUnitName + (item.materialUnits.length ? ' +' + item.materialUnits.length : ''),
    templateName: item.templateName || '—',
    searchText: [item.code, item.name, item.model, item.categoryLabel, item.brand, item.templateName,
      ...colors, ...attributes, ...variants.flatMap(variant => [variant.title, ...variant.attributes])].filter(Boolean).join(' '),
  }
}))
const materialPickerColumns = [
  { title: '状态', dataIndex: 'selectionStatus', key: 'selectionStatus' },
  { title: '物料编码', dataIndex: 'code', key: 'code' },
  { title: '物料名称', dataIndex: 'name', key: 'name' },
  { title: '物料分类', dataIndex: 'category', key: 'category' },
  { title: '物料规格', dataIndex: 'variantSummary', key: 'variantSummary' },
  { title: '可用颜色', dataIndex: 'colorSummary', key: 'colorSummary' },
  { title: '单位配置', dataIndex: 'unitSummary', key: 'unitSummary' },
  { title: '属性模板', dataIndex: 'templateName', key: 'templateName' },
]

const materialColors = ref<Record<string, Option[]>>({})
const units = ref<{ id: string; name: string }[]>([])
const activeGroupId = ref('')
const copyOpen = ref(false)
const copyTargets = ref<string[]>([])

const loading = ref(false)
const saving = ref(false)
const open = ref(false)
/** 回车提交当前输入，再定位相邻物料行的同一字段。 / Commit on blur and move to the same field in the adjacent material row. */
async function moveBomInput(event: KeyboardEvent) {
  const input = event.target
  if (event.isComposing || event.keyCode === 229 || !(input instanceof HTMLInputElement)
    || !input.dataset.bomField || input.disabled || input.readOnly) return
  const container = input.closest('.bom-single-scroll, .bom-matrix-scroll')
  const row = input.closest('.bom-rule, tbody tr')
  if (!container || !row) return
  event.preventDefault()
  const rows = [...container.querySelectorAll('.bom-rule, tbody tr')]
  const step = event.shiftKey ? -1 : 1
  const cellIndex = input.closest('td')?.cellIndex
  const field = input.dataset.bomField
  const start = rows.indexOf(row)
  input.blur()
  await nextTick()
  const updatedRows = [...container.querySelectorAll('.bom-rule, tbody tr')]
  for (let index = start + step; index >= 0 && index < updatedRows.length; index += step) {
    const next = [...updatedRows[index]!.querySelectorAll<HTMLInputElement>('input[data-bom-field]')]
      .find(item => item.dataset.bomField === field && item.closest('td')?.cellIndex === cellIndex
        && !item.disabled && !item.readOnly)
    if (next) {
      next.focus()
      next.select()
      return
    }
  }
  if (input.isConnected) input.focus({ preventScroll: true })
}

const helpOpen = ref(false)
let previousListSelection: { styleId?: string; treeKey: string } | undefined
let previewSelection: { styleId?: string; context: Context } | undefined
const originalDocument = ref('')
const savedDraftPendingRefresh = ref<string>()
/** 提取当前表单中用于判断未保存变更的业务快照。 / Create a business snapshot used to detect unsaved form changes. */
function documentSnapshot() {
  return JSON.stringify({ styleId: styleId.value || null, bomCode: bomCode.value, version: versionNumber.value, preparerId: preparerId.value, remark: remark.value, rules: rules.value })
}
/** 打开制单人选择弹窗并读取可选员工。 / Open the preparer picker and load eligible employees. */
async function openPreparerPicker() {
  if (!people.value.length) {
    try {
      await loadPreparerOptions()
      if (!people.value.length) message.warning('暂无可选的在职人员，请检查人员账号状态和在职状态')
    } catch (error) {
      message.error(foundationError(error))
      return
    }
  }
  personPickerSelected.value = preparerId.value ? [preparerId.value] : []
  personPickerOpen.value = true
}
/** 用户离开未保存 BOM 编辑器前请求确认。 / Confirm before leaving an editor with unsaved BOM changes. */
function confirmLeave(): Promise<boolean> {
  if (!open.value || readonly.value || documentSnapshot() === originalDocument.value) return Promise.resolve(true)
  return new Promise(resolve => Modal.confirm({
    title: '当前单据有未保存的内容',
    content: '离开后本次修改不会保存。',
    okText: '放弃并离开', cancelText: '继续编辑',
    onOk: () => resolve(true), onCancel: () => resolve(false),
  }))
}
/** 关闭 BOM 编辑器并按未保存状态保护草稿内容。 / Close the BOM editor while guarding unsaved draft changes. */
async function closeEditor() {
  if (saving.value) return
  if (await confirmLeave()) {
    open.value = false
    if (previousListSelection) {
      styleId.value = previousListSelection.styleId
      selectedTreeKey.value = previousListSelection.treeKey
      previousListSelection = undefined
    }
    await query()
  }
}
onBeforeRouteLeave(async () => !saving.value && await confirmLeave())
const previewOpen = ref(false)
watch(previewOpen, value => {
  if (!value && previewSelection) {
    styleId.value = previewSelection.styleId
    context.value = previewSelection.context
    previewSelection = undefined
  }
})
const coverageOpen = ref(false)
const coverage = ref<{ checked: number; issueCount: number; issues: string[] }>()
const previewId = ref<string>()
const count = ref(1)
const setSpecId = ref<string>()
const colorId = ref<string>()
const sizeId = ref<string>()
const demand = ref<{ details: Demand[]; totals: Demand[]; gaps: string[] }>()
const currentStyle = computed(() => styles.value.find(style => style.id === styleId.value))
const isSet = computed(() => currentStyle.value != null && currentStyle.value.productionMode !== 'single')
const canEdit = computed(() => can('rd:bom:edit'))
const readonly = computed(() => !canEdit.value || current.value?.status === 'released')
const groups = computed(() => context.value.parts.map(part => ({ ...part, key: part.id } as Part & { key: string })).concat(
  isSet.value ? [{ id: '', key: 'shared', label: '整套共用（按套）', quantity: 1, colors: [], sizes: [], excluded: [] }] : []))
/** 构造符合当前分类授权的物料选项和搜索信息。 / Build material options and search metadata within category grants. */
function materialOptions(rule: Rule) {
  const options = materialChoices.value.map(item => ({
    value: item.id, label: [item.code, item.name, item.specification].filter(Boolean).join(' · '),
  }))
  if (rule.materialId && rule.material && !options.some(option => option.value === rule.materialId)) {
    options.unshift({ value: rule.materialId, label: [rule.material, rule.materialSpec].filter(Boolean).join(' · ') })
  }
  return options
}
const demandColumns = [
  { title: '归属', dataIndex: 'part' }, { title: '用料类型', dataIndex: 'usageType' },
  { title: '物料分类', dataIndex: 'materialCategory' },
  { title: '物料', dataIndex: 'material' }, { title: '物料规格 / 参数', dataIndex: 'materialSpec' }, { title: '物料颜色', dataIndex: 'materialColor' },
  { title: '需求数量', dataIndex: 'quantity' }, { title: '单位', dataIndex: 'unit' },
]

/** 统一包装 BOM 操作的加载状态和错误提示。 / Centralize loading state and error feedback for BOM actions. */
async function run(action: () => Promise<void>) {
  try { await action() } catch (error) { message.error(foundationError(error)) }
}

/** 按款式、版本及查询条件刷新 BOM 列表。 / Refresh the BOM list using style, version and current filters. */
async function query() {
  const id = styleId.value
  loading.value = true
  try {
    await run(async () => {
      const params = new URLSearchParams()
      if (id) params.set('styleId', id)
      if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
      if (statusFilter.value) params.set('status', statusFilter.value)
      if (modeFilter.value) params.set('mode', modeFilter.value)
      const [nextVersions, nextContext] = await Promise.all([
        foundationRequest<Version[]>('get', endpoint + '/list?' + params),
        id ? foundationRequest<Context>('get', endpoint + '/context/' + id) : Promise.resolve({ parts: [], specs: [] }),
      ])
      if (styleId.value !== id) return
      context.value = nextContext
      if (!nextContext.parts.some(part => part.id === activeGroupId.value)) activeGroupId.value = nextContext.parts[0]?.id || ''
      versions.value = nextVersions
    })
  } finally { loading.value = false }
}

/** 读取物料默认分类范围及可供 BOM 使用的资料。 / Load materials available to BOM within the configured category scope. */
async function loadMaterialOptions() {
  const [categoryData, options, configured] = await Promise.all([
    foundationRequest<{ rows: MaterialCategory[]; restricted: boolean }>('get', '/inventory/material/category-options'),
    foundationRequest<Material[]>('get', '/inventory/material/options'),
    foundationRequest<{ schemeId: string; invalid: boolean }>('get', '/inventory/material/default-category'),
  ])
  // 已配置时只展示对应分类类型；配置失效或无权限时明确提示，不降级为全部分类。
  const rows = (categoryData.rows || []).map(item => ({
    ...item, id: String(item.id), schemeId: String(item.schemeId),
    parentId: item.parentId == null ? null : String(item.parentId),
  }))
  const schemeId = configured.schemeId ? String(configured.schemeId) : ''
  materialCategories.value = configured.invalid ? [] : rows.filter(item => !schemeId || item.schemeId === schemeId)
  materialCategoriesRestricted.value = categoryData.restricted
  materialDefaultInvalid.value = configured.invalid
  materialCategoryScopeLoaded.value = true
  materials.value = options
}
/** 按筛选条件查询物料选择器结果并排除已全覆盖项。 / Search picker results and exclude materials already present in every target. */
async function findMaterials() {
  await run(loadMaterialOptions)
}

/** 保存前刷新档案颜色，识别录入期间失效的选项。 / Refresh allowed colors before saving. */
async function loadMaterialColors(id: string, refresh = false) {
  if (!refresh && materialColors.value[id]) return
  materialColors.value[id] = await foundationRequest<Option[]>('get', endpoint + '/material/' + id + '/colors')
}

/** 把选中的物料及可选规格、颜色和单位写入规则行。 / Apply selected material, variant, color and unit to a BOM rule. */
function selectMaterial(rule: Rule) {
  rule.materialColorId = undefined
  const picked = materials.value.find(item => item.id === rule.materialId)
  rule.unit = picked?.inventoryUnitName
  rule.usageUnitId = picked?.inventoryUnitId
  rule.usageUnitName = picked?.inventoryUnitName
  rule.conversionFactor = '1'
  rule.materialCategory = picked?.categoryLabel
  const variant = picked?.variants?.find(item => item.id === rule.materialVariantId)
  rule.materialSpec = variant ? variant.code + ' · ' + variant.name : picked?.specification
  rule.material = picked ? picked.code + ' · ' + picked.name : undefined
  if (rule.materialId) void run(() => loadMaterialColors(rule.materialId!))
}

/** 更换物料时同时更新规格及其颜色、单位选项。 / Replace the material and variant as a single choice. */
function changeMaterialChoice(rule: Rule, key?: string) {
  if (key && !materialChoices.value.some(item => item.id === key)) return
  if (rule.materialId === key) return
  // 换料后按新物料重新填写用量。 / Re-enter quantity for the replacement material.
  rule.materialId = key
  rule.materialVariantId = undefined
  rule.materialColor = undefined
  rule.usage = undefined
  selectMaterial(rule)
  if (!key) rule.conversionFactor = undefined
}

// 车间内网可能使用 HTTP，使用在非安全上下文也可调用的随机数接口。
function newPositionId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}

type Spec = Pair & { key: string; label: string }
const selectedSpecs = ref<Record<string, string>>({})
const copySources = ref<Record<string, string>>({})
const specsByPart = computed(() => Object.fromEntries(groups.value.map(group => {
  const colors = group.colors.length ? group.colors : [{ value: undefined, label: '' }]
  const sizes = group.sizes.length ? group.sizes : [{ value: undefined, label: '' }]
  const specs: Spec[] = colors.flatMap(color => sizes.filter(size =>
    !group.excluded.some(pair => pair.colorId === color.value && pair.sizeId === size.value)).map(size => ({
    key: JSON.stringify([color.value, size.value]), label: [color.label, size.label].filter(Boolean).join(' / ') || '通用',
    colorId: color.value, sizeId: size.value,
  })))
  return [group.id, specs]
})))
/** 判断当前款式规格是否已覆盖、缺料或存在冲突。 / Classify a style specification as covered, missing or conflicting. */
function specStatus(childId: string, spec: Spec) {
  const selected = matchedRules(childId, spec).filter(rule => !rule.omitted)
  if (!selected.length) return '待填写'
  return selected.every(rule => rule.materialId && rule.usage && rule.usageUnitId)
    ? '已填写' : '待完善'
}
/** 解析当前编辑的款式颜色尺码或套装规格。 / Resolve the active style color-size or set specification being edited. */
function activeSpec(childId: string) {
  const specs = specsByPart.value[childId] || []
  return specs.find(spec => spec.key === selectedSpecs.value[childId]) ?? specs[0]
}
/** 判断规则的物料、规格及色码条件是否完全匹配。 / Check whether a rule exactly matches material, variant and style-pair conditions. */
function exact(rule: Rule, spec: Pair) {
  return rule.colorId === spec.colorId && rule.sizeId === spec.sizeId
}
/** 筛选当前规格应显示的 BOM 用料规则。 / Filter BOM rules that apply to the active specification. */
function matchedRules(childId: string, spec: Pair, inheritedOnly = false) {
  const matched = new Map<string, Rule[]>()
  for (const rule of rules.value) {
    if ((rule.childId || '') !== childId || (rule.colorId && rule.colorId !== spec.colorId)
      || (rule.sizeId && rule.sizeId !== spec.sizeId) || (inheritedOnly && exact(rule, spec))) continue
    const previous = matched.get(rule.slotId) || []
    const priority = (item: Rule) => Number(!!item.colorId) + Number(!!item.sizeId)
    if (!previous.length || priority(rule) > priority(previous[0]!)) matched.set(rule.slotId, [rule])
    else if (priority(rule) === priority(previous[0]!)) previous.push(rule)
  }
  return [...matched.values()].flat()
}
const visibleRules = computed(() => Object.fromEntries(groups.value.map(group => {
  const spec = activeSpec(group.id)
  return [group.id, spec ? matchedRules(group.id, spec).filter(rule => !rule.omitted) : []]
})))
const batchMode = ref(false)
const batchFillMode = ref<'blank' | 'overwrite'>('blank')
const overwriteBatchValues = computed(() => batchFillMode.value === 'overwrite')
const batchKeys = ref<string[]>([])
watch(activeGroupId, () => { batchKeys.value = [] })
type BatchEntryMode = 'individual' | 'color' | 'size'
const batchEntryMode = ref<BatchEntryMode>('individual')
const batchModeOptions = [
  { value: 'individual', label: '逐列设置' },
  { value: 'color', label: '按颜色' },
  { value: 'size', label: '按尺码' },
]
const batchDimension = computed(() => batchEntryMode.value === 'color' ? 'colorId'
  : batchEntryMode.value === 'size' ? 'sizeId' : undefined)
const batchScope = ref<string>()
const batchAvailableSpecs = computed(() => (specsByPart.value[activeGroupId.value] || [])
  .filter(spec => !batchDimension.value || (batchScope.value !== undefined
    && (spec[batchDimension.value] || '') === batchScope.value)))
const batchSpecs = computed(() => batchAvailableSpecs.value.filter(spec => batchKeys.value.includes(spec.key)))
watch(activeGroupId, () => { batchScope.value = undefined })

/** 切换只重置勾选范围，不写入用料。 / Reset selection scope without changing BOM data. */
function changeBatchEntryMode(mode: BatchEntryMode) {
  batchFillMode.value = 'blank'
  batchEntryMode.value = mode
  batchScope.value = undefined
  batchKeys.value = []
}

/** 选中一个颜色或尺码后带出该组的有效规格。 / Select valid specifications within one group. */
function changeBatchScope(value: string) {
  batchScope.value = value
  batchKeys.value = batchAvailableSpecs.value.map(spec => spec.key)
}

// 每个规格只计算一次有效用料，表格各行共享结果。
// Resolve effective rules once per selected specification.
const batchRules = computed(() => new Map(batchSpecs.value.map(spec => [
  spec.key, matchedRules(activeGroupId.value, spec).filter(rule => !rule.omitted),
])))


type MatrixRow = { key: string; source: Rule; cells: Map<string, Rule[]> }
const matrixRows = computed(() => {
  const rows = new Map<string, MatrixRow>()
  for (const spec of batchSpecs.value) {
    for (const rule of batchRules.value.get(spec.key) || []) {
      // 同一用料行的物料颜色、单位等有差异时分行展示，避免跨规格填错。
      // Separate differing material details even when they share a position ID.
      const key = JSON.stringify([rule.slotId, rule.materialId, rule.usageUnitId,
        rule.usageType, rule.conversionFactor, rule.remark])
      const row = rows.get(key) || { key, source: rule, cells: new Map<string, Rule[]>() }
      const cell = row.cells.get(spec.key) || []
      cell.push(rule)
      row.cells.set(spec.key, cell)
      rows.set(key, row)
    }
  }
  return [...rows.values()]
})

/** 区分可填写的空格与已有不同明细的规格。 / Keep conflicting details out of quantity edits. */
function matrixCellBlocked(row: MatrixRow, spec: Spec) {
  const effective = batchRules.value.get(spec.key) || []
  const matches = effective.filter(rule => rule.slotId === row.source.slotId)
  if (row.cells.has(spec.key)) return matches.length > 1
  return matches.length > 0 || (!hasMaterialVariants(row.source) && effective.some(rule => rule.materialId === row.source.materialId))
}

/** 只写入指定规格，继承行保留给其他规格。 / Write exact overrides without changing inherited rules. */
function writeMatrixUsage(row: MatrixRow, specs: Spec[], value: string, field: 'usage' | 'materialVariantId' | 'materialColorId' = 'usage') {
  const changes = specs.filter(spec => !matrixCellBlocked(row, spec)
    && (row.cells.get(spec.key)?.[0]?.[field] || '') !== value)
  const additions = changes.filter(spec => !rules.value.some(rule => rule.slotId === row.source.slotId && exact(rule, spec)))
  if (rules.value.length + additions.length > 1000) {
    message.warning('BOM 用料最多 1000 行，请减少本次填写的规格')
    return
  }
  for (const spec of changes) {
    const existing = rules.value.findIndex(rule => rule.slotId === row.source.slotId && exact(rule, spec))
    const next = { ...(row.cells.get(spec.key)?.[0] || row.source), colorId: spec.colorId, sizeId: spec.sizeId }
    if (!row.cells.has(spec.key)) {
      next.usage = undefined
      if (batchEntryMode.value !== 'size' || matrixSharedValue(row, 'materialVariantId') === undefined) {
        next.materialVariantId = undefined
        if (hasMaterialVariants(next)) next.materialSpec = undefined
      }
      if (batchEntryMode.value !== 'color' || matrixSharedValue(row, 'materialColorId') === undefined) {
        next.materialColorId = undefined
        next.materialColor = undefined
      }
    }
    if (field === 'materialVariantId') {
      if (value && (batchRules.value.get(spec.key) || []).some(rule => rule.slotId !== next.slotId
        && rule.materialId === next.materialId && rule.materialVariantId === value)) {
        message.warning('「' + spec.label + '」已使用此物料规格，请选择其他规格')
        continue
      }
      setMaterialVariant(next, value)
    }
    else if (field === 'materialColorId') {
      next.materialColorId = value || undefined
      next.materialColor = materialColors.value[next.materialId || '']?.find(color => color.value === value)?.label
    } else next.usage = value
    if (existing < 0) rules.value.push(next)
    else rules.value.splice(existing, 1, next)
  }
}

/** 共用值不一致时返回 undefined，空值仍可与混合值区分。 / Distinguish mixed values from a shared empty value. */
function matrixSharedValue(row: MatrixRow, field: 'materialColorId' | 'materialVariantId') {
  const values = new Set([...row.cells.values()].flat().map(rule => rule[field] || ''))
  return values.size === 1 ? [...values][0] : undefined
}

/** 仅更新此行已使用物料的规格，保留其他规格的用料。 / Update shared details only for populated cells in this row. */
function updateMatrixDetails(row: MatrixRow, field: 'color' | 'unit' | 'factor' | 'variant', value?: string) {
  const targets = batchSpecs.value.flatMap(spec => {
    const cells = row.cells.get(spec.key)
    return cells?.length === 1 && !matrixCellBlocked(row, spec) ? [{ spec, rule: cells[0]! }] : []
  })
  const additions = targets.filter(({ rule, spec }) => !exact(rule, spec))
  if (rules.value.length + additions.length > 1000) {
    message.warning('BOM 用料最多 1000 行，请减少本次设置的规格')
    return
  }
  if (!targets.length) return
  if (field === 'variant' && value && targets.some(({ spec, rule }) => (batchRules.value.get(spec.key) || []).some(other =>
    other.slotId !== rule.slotId && other.materialId === rule.materialId && other.materialVariantId === value))) {
    message.warning('选中的成衣规格中已有此物料规格，请核对重复用料')
    return
  }
  if (field === 'variant' && ((value || '') === matrixSharedValue(row, 'materialVariantId'))) return
  if (field === 'unit' && (!value || value === row.source.usageUnitId)) return
  if (field === 'factor' && value === (row.source.conversionFactor || '')) return
  if (field === 'color' && (value || '') === matrixSharedValue(row, 'materialColorId')) return

  const apply = () => {
    for (const { spec, rule } of targets) {
      const next: Rule = { ...rule, colorId: spec.colorId, sizeId: spec.sizeId }
      if (field === 'color') {
        next.materialColorId = value
        next.materialColor = materialColors.value[next.materialId || '']?.find(color => color.value === value)?.label
      } else if (field === 'variant') {
        setMaterialVariant(next, value)
      } else if (field === 'unit') {
        next.usageUnitId = value
        changeUnit(next)
        next.usage = undefined
      } else {
        next.conversionFactor = value
      }
      if (exact(rule, spec)) rules.value.splice(rules.value.indexOf(rule), 1, next)
      else rules.value.push(next)
    }
  }
  const sharedField = field === 'variant' ? 'materialVariantId' : field === 'color' ? 'materialColorId' : undefined
  if (sharedField && matrixSharedValue(row, sharedField) === undefined) {
    Modal.confirm({
      title: '统一本行已有设置？',
      content: '将覆盖本行以下成衣规格的' + (field === 'variant' ? '物料规格' : '物料颜色') + '：'
        + targets.filter(({ rule }) => (rule[sharedField] || '') !== (value || '')).map(({ spec }) => spec.label).join('、'),
      okText: '确认应用', cancelText: '取消', onOk: apply,
    })
  } else if (field === 'unit' && targets.some(({ rule }) => rule.usage?.trim())) {
    Modal.confirm({
      title: '更换用量单位？',
      content: '将更新本行 ' + targets.length + ' 个规格的单位，已填写的用量需要按新单位重新录入。',
      okText: '更换并清空用量', cancelText: '取消', onOk: apply,
    })
  } else apply()
}

/** 按勾选范围填充单个字段，覆盖已有值前确认。 / Confirm before replacing populated cells. */
function fillMatrixField(row: MatrixRow, value: unknown, field: 'usage' | 'materialVariantId' | 'materialColorId' = 'usage') {
  if (typeof value !== 'string' || !value.trim()) return
  const nextValue = value.trim()
  if (field === 'usage' && (!/^\d+(?:\.\d{1,8})?$/.test(nextValue) || !/[1-9]/.test(nextValue) || Number(nextValue) >= 1e9)) {
    message.warning('用量须大于零、小于十亿，最多八位小数')
    return
  }
  const targets = batchSpecs.value.filter(spec => {
    const previous = row.cells.get(spec.key)?.[0]?.[field]?.trim()
    return !matrixCellBlocked(row, spec) && previous !== nextValue && (overwriteBatchValues.value || !previous)
  })
  if (!targets.length) {
    message.info(overwriteBatchValues.value ? '当前勾选范围没有需要更新的值' : '当前勾选范围没有可填充的空白')
    return
  }
  const replaced = targets.filter(spec => row.cells.get(spec.key)?.[0]?.[field]?.trim())
  const apply = () => writeMatrixUsage(row, targets, nextValue, field)
  if (!replaced.length) {
    apply()
    return
  }
  const fieldLabel = { usage: '用量', materialVariantId: '物料规格', materialColorId: '物料颜色' }[field]
  Modal.confirm({
    title: '覆盖已填的' + fieldLabel + '？',
    content: '本次将更新本行 ' + targets.length + ' 个成衣规格的' + fieldLabel + '，其中 ' + replaced.length + ' 个已有值。',
    okText: '确认覆盖', cancelText: '取消', onOk: apply,
  })
}
/** 取得物料档案中允许用于 BOM 的单位集合。 / Resolve units enabled for BOM use by the material master. */
function allowedUnits(rule: Rule) {
  const material = materials.value.find(item => item.id === rule.materialId)
  if (!material) return rule.usageUnitId ? [{ value: rule.usageUnitId, label: rule.usageUnitName || rule.unit || '已选单位' }] : []
  const ids = [material.inventoryUnitId, ...material.materialUnits.filter(unit => unit.usage).map(unit => unit.unitId)]
  return ids.map(id => ({ value: id, label: units.value.find(unit => unit.id === id)?.name || (id === material.inventoryUnitId ? material.inventoryUnitName : id) }))
}
/** 判断单位是否需要逐规格录入实际换算系数。 / Determine whether a unit requires a per-specification conversion factor. */
function variableUnit(rule: Rule) {
  return materials.value.find(item => item.id === rule.materialId)?.materialUnits
    .some(unit => unit.unitId === rule.usageUnitId && unit.mode === 'VARIABLE')
}
/** 切换用料单位并更新换算快照或变量换算值。 / Change a material unit and update its fixed or variable conversion snapshot. */
function changeUnit(rule: Rule) {
  const material = materials.value.find(item => item.id === rule.materialId)
  const item = material?.materialUnits.find(unit => unit.unitId === rule.usageUnitId)
  rule.conversionFactor = rule.usageUnitId === material?.inventoryUnitId ? '1' : item?.mode === 'FIXED' ? item.inventoryFactor || undefined : undefined
  rule.usageUnitName = units.value.find(unit => unit.id === rule.usageUnitId)?.name
}
/** 打开物料多选弹窗并预置当前 BOM 规格范围。 / Open the material multi-picker with the current BOM specification scope. */
async function openMaterialPicker(childId: string) {
  const spec = activeSpec(childId)
  const targets = batchMode.value && childId ? batchSpecs.value : spec ? [spec] : []
  if (!targets.length) return
  pickerPartId.value = childId
  pickerScope.value = 'selected'
  pickerSpecKeys.value = targets.map(item => item.key)
  materialPickerSelected.value = []
  try {
    const [, templates] = await Promise.all([loadMaterialOptions(), foundationRequest<PickerAttributeTemplate[]>('get', '/inventory/material/spec-templates')])
    pickerAttributeTemplates.value = templates
    materialPickerOpen.value = true
  } catch (error) {
    message.error(foundationError(error))
  }
}
/** 将已选物料批量加入当前规则或批量填写矩阵。 / Add selected materials to the current rule set or batch-entry matrix. */
function addSelectedMaterials(ids: string[]) {
  const childId = pickerPartId.value
  const targets = pickerSpecs.value
  if (!targets.length) { message.warning('当前规格已变化，请重新选择物料'); return }
  const pending: Rule[] = []
  for (const id of new Set(ids)) {
    const choice = materialChoices.value.find(item => item.id === id)
    if (!choice) continue
    const missing = targets.filter(spec => !matchedRules(childId, spec).some(rule => !rule.omitted && rule.materialId === choice.id && (pickerScope.value === 'all' || !rule.materialVariantId)))
    if (!missing.length) continue
    const source: Rule = { childId: childId || undefined, slot: '', slotId: newPositionId(), omitted: false,
      remark: '', materialId: choice.id }
    selectMaterial(source)
    pending.push(...missing.map(spec => ({ ...source, colorId: spec.colorId, sizeId: spec.sizeId })))
  }
  if (!pending.length) { message.warning('所选物料已添加或不可用，请重新选择'); return }
  if (rules.value.length + pending.length > 1000) {
    message.warning('BOM 用料最多 1000 行，请减少本次选择')
    return
  }
  rules.value.push(...pending)
  message.success('已新增 ' + pending.length + ' 条用料，请填写用量')
}
/** 编辑继承到当前规格的共用物料，不改动其他规格快照。 / Edit a material inherited by this specification without changing other snapshots. */
function editInherited(rule: Rule, spec: Spec) {
  rules.value.push({ ...rule, colorId: spec.colorId, sizeId: spec.sizeId })
}
/** 从当前 BOM 规格移除指定用料规则。 / Remove one material rule from the active BOM specification. */
function removeRule(rule: Rule, childId: string, spec: Spec) {
  const inherited = matchedRules(childId, spec, true).some(item => item.slotId === rule.slotId && !item.omitted)
  if (exact(rule, spec)) rules.value.splice(rules.value.indexOf(rule), 1)
  if (inherited) rules.value.push({ ...rule, colorId: spec.colorId, sizeId: spec.sizeId, omitted: true })
}
/** 仅移除勾选规格的当前用料，继承用料通过排除规则处理。 / Remove this material only from selected specifications. */
function removeMatrixMaterial(row: MatrixRow) {
  if (readonly.value || saving.value) return
  const childId = activeGroupId.value
  const snapshot = documentSnapshot()
  const targets = batchSpecs.value.flatMap(spec => {
    const rule = row.cells.get(spec.key)?.[0]
    return rule && !matrixCellBlocked(row, spec) ? [{ rule, spec }] : []
  })
  if (!targets.length) return
  const netAdditions = targets.reduce((count, { rule, spec }) => {
    const inherited = matchedRules(childId, spec, true).some(item => item.slotId === rule.slotId && !item.omitted)
    return count + Number(inherited) - Number(exact(rule, spec))
  }, 0)
  if (rules.value.length + netAdditions > 1000) {
    message.warning('BOM 用料规则最多 1000 行，请缩小移除范围')
    return
  }
  Modal.confirm({
    title: '移除所选规格的用料？',
    content: '将从 ' + targets.length + ' 个已勾选的成衣规格中移除「' + (row.source.material || row.source.materialId) + '」。',
    okText: '确认移除', okType: 'danger', cancelText: '取消',
    onOk: () => {
      if (readonly.value || saving.value || !open.value || snapshot !== documentSnapshot()) {
        message.warning('当前 BOM 已变化，请重新操作')
        return
      }
      for (const { rule, spec } of targets) removeRule(rule, childId, spec)
      message.success('已移除 ' + targets.length + ' 个规格的用料')
    },
  })
}
/** 打开目标颜色尺码选择以复制当前用料配置。 / Choose target style pairs for copying the current material setup. */
function openCopyTargets() {
  const source = activeSpec(activeGroupId.value)
  if (!source || !matchedRules(activeGroupId.value, source).some(rule => !rule.omitted)) {
    message.warning('当前规格尚未配置用料')
    return
  }
  copyTargets.value = []
  copyOpen.value = true
}
/** 复制物料配置到所选规格并保留各规格独立用量。 / Copy material setup to selected pairs while keeping their usage quantities independent. */
function copyToSelected() {
  const childId = activeGroupId.value
  const source = activeSpec(childId)
  if (!source || !copyTargets.value.length) return
  const sourceRules = matchedRules(childId, source).map(rule => ({ ...rule }))
  if (new Set(sourceRules.map(rule => rule.slotId)).size !== sourceRules.length) {
    message.warning('来源规格有冲突，请先核对用料')
    return
  }
  const targets = (specsByPart.value[childId] || []).filter(spec => copyTargets.value.includes(spec.key) && spec.key !== source.key)
  const apply = () => {
    for (const target of targets) {
      const inherited = matchedRules(childId, target, true)
      rules.value = rules.value.filter(rule => rule.childId !== childId || !exact(rule, target))
      const copied = new Map(sourceRules.map(rule => [rule.slotId, rule]))
      for (const rule of inherited) {
        if (!copied.has(rule.slotId)) copied.set(rule.slotId, { ...rule, omitted: true })
      }
      rules.value.push(...[...copied.values()].map(rule => ({ ...rule, colorId: target.colorId, sizeId: target.sizeId })))
    }
    copyOpen.value = false
    message.success('已复制到 ' + targets.length + ' 个规格')
  }
  if (targets.some(target => matchedRules(childId, target).some(rule => !rule.omitted))) {
    Modal.confirm({ title: '替换所选规格已有用料？', content: '本次将复制到 ' + targets.length + ' 个颜色尺码。',
      okText: '替换', cancelText: '取消', onOk: apply })
  } else apply()
}

/** 加载 BOM 版本并初始化全部颜色尺码用料状态。 / Load a BOM version and initialize its color-size material state. */
async function edit(row?: Version, copy = false) {
  const initialSelection = { styleId: styleId.value, treeKey: selectedTreeKey.value }
  await run(async () => {
    const editorStyleId = row?.styleId || styleId.value
    const nextContext = editorStyleId
      ? await foundationRequest<Context>('get', endpoint + '/context/' + editorStyleId) : undefined
    const detail = row ? await foundationRequest<Version & { rules: Rule[] }>('get', endpoint + '/' + row.id) : undefined
    const suggestion = !detail || copy ? await foundationRequest<string>('get', endpoint + '/next-code') : ''
    if (nextContext) {
      styleId.value = editorStyleId
      context.value = nextContext
    }
    savedDraftPendingRefresh.value = undefined
    current.value = copy ? undefined : detail
    suggestedVersion.value = current.value ? undefined : nextContext?.nextVersion
    versionNumber.value = current.value?.version ?? suggestedVersion.value
    autoGeneratedCode.value = suggestion
    bomCode.value = suggestion || detail?.bomCode || ''
    // 新单从登录资料取制单人，避免受人员列表分页影响。 / Use the authenticated profile for new documents.
    if (!current.value) {
      const profile = await foundationRequest<{ user_id: string | number; nick_name?: string; user_name?: string }>('get', '/system/profile/info')
      loginPreparer.value = { id: String(profile.user_id), name: profile.nick_name || profile.user_name || '' }
    }
    preparerId.value = current.value ? current.value.preparerId || '' : loginPreparer.value?.id || ''
    rules.value = (detail?.rules ?? []).map(rule => ({ ...rule,
      childId: rule.childId ?? undefined, colorId: rule.colorId ?? undefined, sizeId: rule.sizeId ?? undefined,
      materialId: rule.materialId ?? undefined, materialVariantId: rule.materialVariantId ?? undefined, materialColorId: rule.materialColorId ?? undefined,
      usageUnitId: rule.usageUnitId ?? undefined }))
    activeGroupId.value = context.value.parts[0]?.id || ''
    selectedSpecs.value = {}
    batchKeys.value = []
    batchMode.value = false
    changeBatchEntryMode('individual')
    copySources.value = {}
    remark.value = detail?.remark ?? ''
    previousListSelection = initialSelection
    open.value = true
    originalDocument.value = documentSnapshot()
    if (!readonly.value) {
      void findMaterials()
      for (const id of new Set(rules.value.flatMap(rule => rule.materialId ? [rule.materialId] : []))) {
        void run(() => loadMaterialColors(id))
      }
    }
  })
}

/** 按成衣规格检查实际生效的用料，包含继承行。 / Check effective material variants per garment specification. */
function findDuplicateMaterialVariant() {
  for (const group of groups.value) {
    for (const spec of specsByPart.value[group.id] || []) {
      const seen = new Set<string>()
      for (const rule of matchedRules(group.id, spec)) {
        if (rule.omitted || !rule.materialId || !rule.materialVariantId) continue
        const key = JSON.stringify([rule.materialId, rule.materialVariantId])
        if (seen.has(key)) return { group, spec, rule }
        seen.add(key)
      }
    }
  }
}

const bomCsvInput = ref<HTMLInputElement>()
const bomCsvHeader = ['款号', '子款款号', '成衣颜色', '成衣尺码', '用料组', '用料位置', '用途', '物料编码', '物料规格编码', '物料颜色', '单位', '用量', '换算系数', '排除', '备注', '物料分类', '物料名称']

// 页面和模板共用逐列说明。 / Share column guidance between the page and the CSV template.
const bomCsvGuide = [
  ['选填', '选好款式后自动带出。手填时须与当前 BOM 的款号一致。'],
  ['按情况填写', '套装子款用料填写子款款号。单款或整套共用用料留空。'],
  ['选填', '填成衣档案中的颜色名称，例如黑色。留空表示适用所有成衣颜色。整套共用留空。'],
  ['选填', '填成衣档案中的尺码名称，例如 XL。留空表示适用所有成衣尺码。整套共用留空。'],
  ['按情况填写', '普通用料留空。通用用料与其颜色尺码例外使用同一个自定编号，例如 A01。排除行必填。'],
  ['选填', '填写用料位置，例如领口。同一用料组的位置须一致。'],
  ['选填', '可填面布、里布、衬布、网眼、辅料、标识、包装、其他。'],
  ['普通用料必填', '填物料档案中的编码。排除行留空。'],
  ['多规格物料必填', '填该物料的规格编码。单规格物料或排除行留空。'],
  ['选填', '需要指定颜色时，填该物料档案允许的颜色名称。'],
  ['选填', '填库存单位或该物料允许领用的辅助单位名称。留空使用库存单位。'],
  ['普通用料必填', '填单件用量，整套共用时填每套用量。大于零且小于十亿，最多八位小数。排除行留空。'],
  ['按情况填写', '使用按实际数量换算的辅助单位时必填。填写每一用量单位等于多少库存单位，最多十二位小数且大于零。固定换算留空。'],
  ['选填', '普通用料留空或填否。指定色码不使用同组用料时填是，并填写用料组及至少一个成衣色码字段。'],
  ['选填', '填写用料补充说明，最多 500 字。'],
  ['系统带出', '导出时展示分类路径，导入时按物料档案识别，可留空。'],
  ['系统带出', '导出时展示物料名称，导入以物料编码匹配，可留空。'],
] as const
const bomCsvGuideMarker = '【填写说明】'

/** 模板预填当前款号，其他内容由制单人填写。 / Prefill the style code in the blank template. */
function downloadBomTemplate() {
  const row = bomCsvHeader.map(() => '')
  row[0] = currentStyle.value?.styleCode || ''
  downloadFoundationFile('BOM用料导入模板.csv', encodeCsv([
    bomCsvHeader,
    bomCsvGuide.map(([requirement]) => bomCsvGuideMarker + requirement),
    bomCsvGuide.map(([, description]) => bomCsvGuideMarker + description),
    row,
  ]))
}

/** 用档案编码和名称导出整单。 / Export the document with business codes and names. */
function exportBomCsv() {
  if (!currentStyle.value) return
  const positions = new Map<string, number>()
  const rows = rules.value.map(rule => {
    const group = groups.value.find(item => item.id === rule.childId)
    const material = materials.value.find(item => item.id === rule.materialId)
    const variant = material?.variants?.find(item => item.id === rule.materialVariantId)
    if (!positions.has(rule.slotId)) positions.set(rule.slotId, positions.size + 1)
    return [
      currentStyle.value!.styleCode, isSet.value ? group?.label.split(' · ')[0] : '',
      group?.colors.find(item => item.value === rule.colorId)?.label,
      group?.sizes.find(item => item.value === rule.sizeId)?.label,
      positions.get(rule.slotId), rule.slot, rule.usageType,
      material?.code || rule.material?.split(' · ')[0],
      variant?.code || (rule.materialVariantId ? rule.materialSpec?.split(' · ')[0] : ''),
      rule.materialColor || materialColors.value[rule.materialId || '']?.find(item => item.value === rule.materialColorId)?.label,
      rule.usageUnitName || rule.unit, rule.usage, rule.conversionFactor, rule.omitted ? '是' : '否',
      rule.remark, rule.materialCategory, material?.name || rule.material,
    ].map(value => {
      const text = String(value ?? '')
      return /^[=+@-]/.test(text) ? "'" + text : text
    })
  })
  downloadFoundationFile('BOM-' + (bomCode.value || '用料明细') + '.csv', encodeCsv([bomCsvHeader, ...rows]))
}

/** 先校验整份文件，再一次性替换草稿用料。 / Validate the entire file before replacing draft rules. */
async function importBomCsv(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || readonly.value || saving.value || !styleId.value) return
  const snapshot = documentSnapshot()
  try {
    if (file.size > 2 * 1024 * 1024) throw new Error('请选择不超过 2 MB 的 CSV 文件')
    const [header, ...body] = parseCsv(await file.text())
    const records = body.filter(({ fields }) =>
      !fields.every(value => value.startsWith(bomCsvGuideMarker))
      && !(fields[0] === currentStyle.value?.styleCode && fields.slice(1).every(value => !value)))
    if (header?.fields.join(',') !== bomCsvHeader.join(',')) throw new Error('请使用本页面下载的用料模板或导出的 BOM CSV 文件')
    if (!records.length || records.length > 1000) throw new Error('文件须包含 1 至 1000 条用料明细')
    await loadMaterialOptions()
    const choices = new Map(materialChoices.value.map(item => [item.code, item]))
    const keys = new Set<string>()
    const positions = new Map<string, string>()
    const colorIds = new Set(records.filter(row => row.fields[9])
      .flatMap(row => choices.get(row.fields[7] || '')?.id || []))
    await Promise.all([...colorIds].map(id => loadMaterialColors(id, true)))
    const pending: Rule[] = records.map(({ line, fields }) => {
      const [style, childCode, colorName, sizeName, position, slot, usageType, materialCode, variantCode, materialColorName, unitName, usage, factor, excluded, remark] =
        fields.map(value => /^'[=+@-]/.test(value) ? value.slice(1) : value)
      const fail = (reason: string): never => { throw new Error('第 ' + line + ' 行：' + reason) }
      if (fields.length !== bomCsvHeader.length || (style && style !== currentStyle.value?.styleCode)) fail('字段不完整或款号与当前款式不一致')
      const group = isSet.value
        ? groups.value.find(item => childCode ? item.label.split(' · ')[0] === childCode : !item.id)
        : groups.value[0]
      if (!group || (!isSet.value && childCode)) return fail('子款款号无效')
      const colors = group.colors.filter(item => item.label === colorName)
      const sizes = group.sizes.filter(item => item.label === sizeName)
      if ((colorName && colors.length !== 1) || (sizeName && sizes.length !== 1)) fail('成衣颜色或尺码不存在，或名称重复')
      const colorId = colors[0]?.value
      const sizeId = sizes[0]?.value
      if (colorId && sizeId && group.excluded.some(item => item.colorId === colorId && item.sizeId === sizeId)) fail('成衣颜色尺码组合已排除')
      const positionKey = JSON.stringify([group.id, position || '行-' + line])
      const slotId = positions.get(positionKey) || newPositionId()
      positions.set(positionKey, slotId)
      const key = JSON.stringify([colorId, sizeId, slotId])
      if (keys.has(key)) fail('同一用料组内的成衣颜色尺码重复')
      keys.add(key)
      if (excluded && excluded !== '是' && excluded !== '否') fail('排除列请填写是或否')
      if (excluded === '是' && (!position || (!colorId && !sizeId))) fail('排除行须填写用料组及成衣颜色或尺码')
      const rule: Rule = { childId: group.id || undefined, colorId, sizeId,
        slotId, slot: slot || '', usageType: usageType || undefined, omitted: excluded === '是', remark: remark || '' }
      if (rule.omitted) return rule
      const material = choices.get(materialCode || '')
      if (!material) return fail('物料编码不存在或不可选')
      const variant = material.variants?.find(item => item.code === variantCode)
      if ((variantCode || material.multiSpec) && !variant) fail('请填写此物料有效的规格编码')
      const matchingColors = materialColors.value[material.id]?.filter(item => item.label === materialColorName) || []
      if (materialColorName && matchingColors.length !== 1) fail('物料颜色不可用或名称重复')
      const color = matchingColors[0]
      rule.materialId = material.id
      const matchingUnits = allowedUnits(rule).filter(item => unitName
        ? item.label === unitName || material.materialUnits.some(config => config.unitId === item.value && config.unitName === unitName)
        : item.value === material.inventoryUnitId)
      const unit = matchingUnits[0]
      if (!unit || matchingUnits.length !== 1) return fail('用量单位不可用或名称重复')
      if (!usage || (!/^\d+(?:\.\d{1,8})?$/.test(usage) || !/[1-9]/.test(usage) || Number(usage) >= 1e9)) fail('用量须大于零、小于十亿，最多八位小数')
      if (factor && (!/^\d+(?:\.\d{1,12})?$/.test(factor) || !/[1-9]/.test(factor))) fail('换算系数须大于零，最多十二位小数')
      rule.usageUnitId = unit.value
      if (variableUnit(rule) && !factor) fail('此辅助单位须填写换算系数')
      Object.assign(rule, { material: material.code + ' · ' + material.name, materialCategory: material.categoryLabel,
        materialVariantId: variant?.id, materialSpec: variant ? variant.code + ' · ' + variant.name : material.specification,
        materialColorId: color?.value, materialColor: color?.label, usageUnitId: unit.value,
        usageUnitName: unit.label, unit: material.inventoryUnitName, usage: usage || undefined, conversionFactor: factor || undefined })
      return rule
    })
    const apply = () => {
      if (!open.value || readonly.value || saving.value || snapshot !== documentSnapshot()) {
        message.warning('当前 BOM 已变化，请重新导入')
        return
      }
      rules.value = pending
      message.success('已导入 ' + pending.length + ' 条用料，请核对后保存')
    }
    if (rules.value.length) {
      Modal.confirm({ title: '替换当前 BOM 的全部用料？', content: '将导入 ' + pending.length + ' 条用料。确认后可继续编辑，保存时才会写入单据。',
        okText: '替换用料', cancelText: '取消', onOk: apply })
    } else apply()
  } catch (error) {
    message.error(foundationError(error))
  }
}

/** 续编时同步服务器分配的单号、版本号和最新修订标识。 / Refresh the saved draft identity for continued editing. */
async function refreshSavedDraft(id: string) {
  const detail = await foundationRequest<Version>('get', endpoint + '/' + id)
  const savedSnapshot = JSON.parse(originalDocument.value)
  const savedPreparerId = detail.preparerId || ''
  current.value = detail
  if (bomCode.value === savedSnapshot.bomCode) bomCode.value = detail.bomCode
  if (versionNumber.value === savedSnapshot.version) versionNumber.value = detail.version
  if (preparerId.value === savedSnapshot.preparerId) preparerId.value = savedPreparerId
  autoGeneratedCode.value = ''
  suggestedVersion.value = undefined
  originalDocument.value = JSON.stringify({
    ...savedSnapshot,
    bomCode: detail.bomCode, version: detail.version, preparerId: savedPreparerId,
  })
  savedDraftPendingRefresh.value = undefined
}

/** 保存当前 BOM 草稿并保留编辑页面及服务端修订信息。 / Save the BOM draft while retaining the editor and server revision state. */
async function save(release: boolean) {
  if (saving.value) return
  if (savedDraftPendingRefresh.value) {
    saving.value = true
    try {
      await run(async () => {
        await refreshSavedDraft(savedDraftPendingRefresh.value!)
        message.success('草稿信息已同步，请继续操作')
      })
    } finally { saving.value = false }
    return
  }
  if (versionNumber.value != null && (!Number.isInteger(versionNumber.value) || versionNumber.value < 1 || versionNumber.value > 2147483647)) {
    message.warning('版本号必须是 1 至 2147483647 的整数')
    return
  }
  if (!styleId.value || loading.value || !context.value.parts.length) {
    message.warning('请先选择款式并等待资料加载完成')
    return
  }
  const duplicate = findDuplicateMaterialVariant()
  if (duplicate) {
    const { group, spec, rule } = duplicate
    batchMode.value = false
    activeGroupId.value = group.id
    selectedSpecs.value[group.id] = spec.key
    const variant = variantOptions(rule).find(item => item.value === rule.materialVariantId)?.label || rule.materialSpec
    message.warning('「' + group.label + ' / ' + spec.label + '」中「'
      + (rule.material || rule.materialId) + ' · ' + (variant || rule.materialVariantId)
      + '」重复，请合并用量或改选其他物料规格')
    return
  }
  for (const rule of rules.value) {
    if (rule.omitted) continue
    if (hasMaterialVariants(rule) && !rule.materialVariantId) {
      message.warning('请选择「' + (rule.material || '多规格物料') + '」的具体规格')
      return
    }
    const quantity = String(rule.usage || '').trim()
    if (!rule.materialId || !rule.usageUnitId || !/^(?:\d+)(?:\.\d{1,8})?$/.test(quantity) || !/[1-9]/.test(quantity)) {
      batchMode.value = false
      activeGroupId.value = rule.childId || ''
      const spec = (specsByPart.value[activeGroupId.value] || []).find(item => exact(rule, item))
      if (spec) selectedSpecs.value[activeGroupId.value] = spec.key
      message.warning('请填写物料、用量单位和大于零的用量（最多八位小数）')
      return
    }
    if (variableUnit(rule) && (!rule.conversionFactor || !/^(?:\d+)(?:\.\d{1,12})?$/.test(rule.conversionFactor) || !/[1-9]/.test(rule.conversionFactor))) {
      message.warning('请填写大于零的换算系数（最多十二位小数）')
      return
    }
  }
  saving.value = true
  try {
    await run(async () => {
      const coloredRules = rules.value.filter(rule => !rule.omitted && rule.materialId && rule.materialColorId)
      await Promise.all([...new Set(coloredRules.map(rule => rule.materialId!))]
        .map(id => loadMaterialColors(id, true)))
      const invalid = coloredRules.find(rule =>
        !materialColors.value[rule.materialId!]?.some(color => color.value === rule.materialColorId))
      if (invalid) {
        batchMode.value = false
        activeGroupId.value = invalid.childId || ''
        const spec = (specsByPart.value[activeGroupId.value] || []).find(item => exact(invalid, item))
        if (spec) selectedSpecs.value[activeGroupId.value] = spec.key
        message.warning('「' + (invalid.material || invalid.materialId) + '」的原物料颜色已不可用，请重新选择或清除该颜色')
        return
      }
      const payload = { id: current.value?.id, revision: current.value?.revision, styleId: styleId.value,
        version: versionNumber.value, suggestedVersion: suggestedVersion.value, bomCode: bomCode.value, autoGeneratedCode: autoGeneratedCode.value, preparerId: preparerId.value || null, remark: remark.value, release, rules: rules.value.map(rule => ({
          childId: rule.childId, slot: rule.slot, slotId: rule.slotId, usageType: rule.usageType, colorId: rule.colorId, sizeId: rule.sizeId,
          materialId: rule.materialId, materialVariantId: rule.materialVariantId, materialColorId: rule.materialColorId, usage: rule.usage || null,
          usageUnitId: rule.usageUnitId, conversionFactor: rule.conversionFactor || null,
          omitted: rule.omitted, remark: rule.remark,
        })) }
      const savedId = await foundationRequest<string>('post', endpoint, payload)
      if (!release) {
        savedDraftPendingRefresh.value = savedId
        originalDocument.value = documentSnapshot()
        try {
          await refreshSavedDraft(savedId)
          message.success('草稿已保存，可继续编辑')
        } catch {
          message.warning('草稿已保存，暂未同步单据信息。再次点击保存可重试同步。')
        }
        return
      }
      open.value = false
      selectedTreeKey.value = styleId.value ? 'style:' + styleId.value : ''
      const selected = styles.value.find(item => item.id === styleId.value)
      if (selected) {
        const ancestors: string[] = []
        let category = styleCategories.value.find(item => item.id === selected.categoryId)
        const visited = new Set<string>()
        while (category && !visited.has(category.id)) {
          visited.add(category.id)
          ancestors.push('category:' + category.id, 'scheme:' + category.schemeId)
          category = styleCategories.value.find(item => item.id === category?.parentId)
        }
        expandedStyleKeys.value = [...new Set([...expandedStyleKeys.value, ...ancestors])]
      }
      previousListSelection = undefined
      message.success('BOM 已发布')
      await query()
    })
  } finally { saving.value = false }
}

/** 检查发布版本覆盖范围并定位缺失规格或冲突规则。 / Check release coverage and identify missing pairs or conflicting rules. */
async function checkCoverage(row: Version) {
  coverage.value = undefined
  coverageOpen.value = true
  await run(async () => { coverage.value = await foundationRequest('get', endpoint + '/' + row.id + '/coverage') })
}
/** 为物料规则行补齐显示字段、单位和规格快照。 / Hydrate a material-rule row with labels, units and variant snapshots. */
async function prepareRow(row: Version) {
  if (row.styleId && row.styleId !== styleId.value) {
    const loaded = await foundationRequest<Context>('get', endpoint + '/context/' + row.styleId)
    styleId.value = row.styleId
    context.value = loaded
  }
}
/** 开始加载 BOM 试算结果并保留当前视图上下文。 / Load BOM preview results while preserving the current view context. */
async function beginPreview(row: Version) {
  const original = { styleId: styleId.value, context: context.value }
  try { await prepareRow(row) } catch (error) { message.error(foundationError(error)); return }
  previewSelection = original
  previewId.value = row.id
  count.value = 1
  setSpecId.value = undefined
  colorId.value = undefined
  sizeId.value = undefined
  demand.value = undefined
  previewOpen.value = true
}
/** 按款式颜色尺码展开用料明细并生成汇总视图。 / Expand material details by style pair and produce the summary view. */
async function preview() {
  demand.value = undefined
  await run(async () => {
    const params = new URLSearchParams({ count: String(count.value) })
    if (setSpecId.value) params.set('setSpecId', setSpecId.value)
    if (colorId.value) params.set('colorId', colorId.value)
    if (sizeId.value) params.set('sizeId', sizeId.value)
    demand.value = await foundationRequest('get', endpoint + '/' + previewId.value + '/preview?' + params)
  })
}
onMounted(() => void run(async () => {
  // 显示参数不可用时使用统一默认值，避免权限或配置服务问题阻断 BOM 列表和详情。
  // Fall back to the shared default so a config lookup cannot block BOM access.
  try {
    bomPrecision.value = readPrecision(await foundationRequest<string>('get', '/system/config/configKey/'+precisionKey)).bomUsage
  } catch {
    bomPrecision.value = readPrecision().bomUsage
  }
  const [catalog, unitOptions, categoryData] = await Promise.all([
    foundationRequest<Style[]>('get', '/rd/style-bom/styles'),
    foundationRequest<{ id: string; name: string }[]>('get', '/system/unit/options'),
    foundationRequest<StyleCategoryData>('get', '/rd/style-bom/style-categories'),
  ])
  styles.value = catalog.map(item => ({ ...item, id: String(item.id), categoryId: String(item.categoryId) }))
  units.value = unitOptions
  styleScopeUnavailable.value = categoryData.invalid
  styleScopeRestricted.value = categoryData.restricted
  styleCategories.value = categoryData.invalid ? [] : (categoryData.rows || [])
    .map(item => ({ ...item, id: String(item.id), schemeId: String(item.schemeId), parentId: item.parentId == null ? null : String(item.parentId) }))
    .filter(item => !categoryData.schemeId || item.schemeId === String(categoryData.schemeId))
  styleScopeLoaded.value = true
  expandedStyleKeys.value = bomTree.value.map(node => node.key)
  await query()
}))
</script>

<template>
  <div class="bom-page">
    <StandardListLayout v-if="!open" v-model:collapsed="sidebarCollapsed">
      <template #filters>
        <label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" placeholder="BOM 单号、款号、款式或客户" allow-clear style="width: 230px" @press-enter="query" /></label>
        <label class="zy-filter-field"><span>状态</span><a-select v-model:value="statusFilter" allow-clear placeholder="状态" style="width: 125px" :options="[{value:'draft',label:'草稿'},{value:'released',label:'已发布'}]" /></label>
        <label class="zy-filter-field"><span>款式类型</span><a-select v-model:value="modeFilter" allow-clear placeholder="款式类型" style="width: 145px" :options="[{value:'single',label:'单款'},{value:'combined',label:'合并生产套装'},{value:'separate',label:'分开生产套装'}]" /></label>
        <label class="zy-filter-field"><span>款式分类（含下级）</span><a-tree-select v-model:value="categoryFilter" :tree-data="categoryFilterTree" allow-clear show-search tree-default-expand-all tree-node-filter-prop="title" placeholder="款式分类（含下级）" style="width: 190px" /></label>
      </template>
      <template #sidebar>
        <div class="tree-heading"><strong>款式分类</strong><a-button size="small" @click="selectedTreeKey = ''; styleId = undefined; query()">重置</a-button></div>
        <a-alert v-if="styleScopeUnavailable" type="warning" message="默认分类无效或无权查看，请联系管理员。" />
        <a-alert v-else-if="styleScopeRestricted" type="info" message="仅显示有权查看的分类和款式。" />
        <a-tree v-if="bomTree.length" block-node :tree-data="bomTree" :selected-keys="selectedTreeKey ? [selectedTreeKey] : []"
          :expanded-keys="expandedStyleKeys" @select="selectBomTree" @expand="keys => expandedStyleKeys = keys.map(String)" />
        <a-empty v-else description="暂无可见分类" />
      </template>
      <FoundationTable page-toolbar storage-key="rd_style_bom_list" :columns="listColumns" :rows="visibleVersions" :loading="loading" :action-width="330">
        <template #actions>
          <a-button v-if="canEdit" type="primary" :disabled="loading" @click="edit()"><PlusOutlined />新增 BOM</a-button>
          <a-button type="primary" @click="query">查询</a-button>
          <a-button @click="styleId = undefined; selectedTreeKey = ''; keyword = ''; statusFilter = undefined; modeFilter = undefined; categoryFilter = undefined; query()">重置</a-button>
        </template>
        <template #cell="{ column, row }">
          <template v-if="column.key === 'productionMode'">{{ row.productionMode === 'single' ? '单款' : row.productionMode === 'combined' ? '合并生产套装' : '分开生产套装' }}</template>
          <a-tag v-else-if="column.key === 'status'" :color="row.status === 'released' ? 'green' : 'blue'">{{ row.status === 'released' ? '已发布' : '草稿' }}</a-tag>
          <template v-else-if="column.key === 'createTime' || column.key === 'updateTime'">{{ row[column.key] ? row[column.key].slice(0, 19).replace('T', ' ') : '—' }}</template>
          <template v-else>{{ row[column.key] ?? '—' }}</template>
        </template>
        <template #action="{ row }">
          <a-space :size="2">
            <a-button type="link" size="small" @click="edit(row)">{{ canEdit && row.status === 'draft' ? '编辑' : '查看' }}</a-button>
            <a-button v-if="canEdit" type="link" size="small" @click="edit(row, true)">复制为新版本</a-button>
            <a-button type="link" size="small" @click="checkCoverage(row)">规格核对</a-button>
            <a-button type="link" size="small" @click="beginPreview(row)">用量试算</a-button>
          </a-space>
        </template>
      </FoundationTable>
    </StandardListLayout>
    <section v-else class="bom-document">
      <a-form layout="vertical" :disabled="readonly || saving" class="bom-editor">
        <div class="bom-header">
          <a-form-item label="BOM 单号" required class="bom-header-code"><a-input v-model:value="bomCode" :maxlength="60" placeholder="自动生成，可修改" /></a-form-item>
          <a-form-item label="款式" required class="bom-header-style">
            <div class="style-field">
              <span :title="currentStyle ? currentStyle.styleCode + ' · ' + currentStyle.name : ''">{{ currentStyle ? currentStyle.styleCode + ' · ' + currentStyle.name : '请选择款式或套装' }}</span>
              <a-button v-if="!readonly" :disabled="!!current || loading" @click="openStylePicker">
                {{ currentStyle ? '更换款式' : '选择款式' }}
              </a-button>
            </div>
          </a-form-item>
          <a-form-item label="版本号" class="bom-version-number">
            <a-input-number v-model:value="versionNumber" :min="1" :max="2147483647" placeholder="自动分配" />
          </a-form-item>
          <div class="meta-field bom-version-field">
            <label>状态</label>
            <div class="bom-header-status">
              <a-tag :color="current?.status === 'released' ? 'green' : 'blue'">{{ current?.status === 'released' ? '已发布' : '草稿' }}</a-tag>
            </div>
          </div>
          <div class="document-meta">
            <div v-if="currentStyle?.categoryName" class="meta-field"><label>款式分类</label><span :title="currentStyle.categoryName">{{ currentStyle.categoryName }}</span></div>
            <div v-if="currentStyle?.customerName" class="meta-field"><label>客户</label><span :title="currentStyle.customerName">{{ currentStyle.customerName }}</span></div>
            <div class="meta-field"><label>制单人 <b>*</b></label><a-input :value="preparerLabel" readonly :disabled="readonly" placeholder="请选择制单人" @click="openPreparerPicker"><template #suffix><a-button type="link" size="small" :disabled="readonly" @click.stop="openPreparerPicker">选择</a-button></template></a-input></div>
            <div class="meta-field"><label>制单时间</label><span>{{ current?.createTime ? current.createTime.slice(0, 19).replace('T', ' ') : (current ? '历史未记录' : '保存时记录') }}</span></div>
            <a-form-item label="版本说明" class="bom-header-remark"><a-input v-model:value="remark" :maxlength="500" placeholder="可填写用途或调整说明" /></a-form-item>
            <div v-if="current?.releaseTime" class="meta-field"><label>发布人</label><span>{{ current.releaserName || '历史未记录' }}</span></div>
          </div>
        </div>
        <div class="bom-workspace-toolbar">
          <div class="header-actions">
            <a-button v-if="!readonly && styleId && (batchMode || (activeGroupId && (specsByPart[activeGroupId]?.length || 0) > 1))"
              :type="batchMode ? 'primary' : 'default'" :aria-pressed="batchMode"
              @click="batchMode = !batchMode; batchKeys = batchMode && activeSpec(activeGroupId) ? [activeSpec(activeGroupId)!.key] : []">
              {{ batchMode ? '返回单规格' : '批量填用量' }}
            </a-button>
            <a-button v-if="!readonly && styleId" type="primary" :disabled="batchMode && !!activeGroupId ? !batchSpecs.length : !activeSpec(activeGroupId)" @click="openMaterialPicker(activeGroupId)">选择物料</a-button>
            <a-button v-if="!readonly && !batchMode && styleId && (specsByPart[activeGroupId]?.length || 0) > 1" :disabled="!visibleRules[activeGroupId]?.length" @click="openCopyTargets">复制当前用料到其他规格</a-button>
            <input ref="bomCsvInput" type="file" accept=".csv,text/csv" v-show="false" @change="importBomCsv" />
            <a-button v-if="!readonly" @click="downloadBomTemplate">下载用料模板</a-button>
            <a-button v-if="!readonly" :disabled="!styleId || loading" @click="bomCsvInput?.click()">导入用料</a-button>
            <a-button :disabled="!styleId || !rules.length" @click="exportBomCsv">导出用料</a-button>
            <a-button @click="helpOpen = true">使用指引</a-button>
          </div>
        </div>

        <a-spin :spinning="loading">
        <a-empty v-if="!styleId" description="选择款式后，维护各颜色尺码的用料。" :image-style="{ height: '60px' }">
          <a-button type="primary" @click="openStylePicker">选择款式</a-button>
        </a-empty>
        <template v-else>
        <a-alert v-if="rules.some(rule => rule.childId && !groups.some(group => group.id === rule.childId))"
          type="warning" message="有用料属于已移除子款，请核对后清除这些旧规则。" />
        <a-button v-if="!readonly && rules.some(rule => rule.childId && !groups.some(group => group.id === rule.childId))" danger
          @click="rules = rules.filter(rule => !rule.childId || groups.some(group => group.id === rule.childId))">移除已离开套装的子款用料</a-button>
        <div class="bom-workspace">
          <aside class="bom-sidebar">
            <div class="bom-sidebar-title"><strong>颜色尺码</strong><span>选择规格填写用料</span></div>
            <section v-for="group in groups" :key="group.key" class="sidebar-group">
              <strong>{{ group.label }}</strong>
              <template v-if="batchMode && group.id && group.id === activeGroupId">
                <a-select class="bom-entry-mode" :value="batchEntryMode" :options="batchModeOptions" aria-label="批量录入模式"
                  @change="(value: unknown) => changeBatchEntryMode(value as BatchEntryMode)" />
                <p class="bom-mode-help">{{ batchEntryMode === 'individual' ? '每列分别选物料规格、颜色和填用量。'
                  : batchEntryMode === 'color' ? '先选成衣颜色。物料颜色按行设置，规格和用量按列填。'
                  : '先选成衣尺码。物料规格按行设置，颜色和用量按列填。' }}</p>
                <a-select v-if="batchDimension" class="bom-entry-mode" :value="batchScope"
                  :aria-label="batchDimension === 'colorId' ? '成衣颜色' : '成衣尺码'"
                  :placeholder="batchDimension === 'colorId' ? '选择成衣颜色' : '选择成衣尺码'"
                  :options="(batchDimension === 'colorId' ? group.colors : group.sizes).length
                    ? (batchDimension === 'colorId' ? group.colors : group.sizes) : [{ value: '', label: '通用' }]"
                  @change="(value: unknown) => changeBatchScope(String(value))" />
                <a-button v-if="batchAvailableSpecs.length" type="link" size="small"
                  @click="batchKeys = batchSpecs.length === batchAvailableSpecs.length ? [] : batchAvailableSpecs.map(spec => spec.key)">
                  {{ batchSpecs.length === batchAvailableSpecs.length ? '取消全选' : '全选当前范围' }}
                </a-button>
                <a-checkbox-group v-model:value="batchKeys" class="bom-batch-specs">
                  <a-checkbox v-for="spec in batchAvailableSpecs" :key="spec.key" :value="spec.key">
                    {{ spec.label }} · {{ specStatus(group.id, spec) }}
                  </a-checkbox>
                </a-checkbox-group>
              </template>
              <template v-else>
              <button v-for="spec in specsByPart[group.id]" :key="spec.key" type="button"
                class="spec-option" :class="{ active: activeGroupId === group.id && activeSpec(group.id)?.key === spec.key }"
                @click="activeGroupId = group.id; selectedSpecs[group.id] = spec.key; delete copySources[group.id]; if (!group.id || (specsByPart[group.id]?.length || 0) < 2) batchMode = false">
                <span>{{ spec.label }}</span>
                <small>{{ specStatus(group.id, spec) }}</small>
              </button>
              </template>
            </section>
          </aside>
          <div class="bom-workspace-main" :class="{ 'bom-workspace-main-matrix': batchMode && activeGroupId, 'bom-workspace-main-single': !batchMode || !activeGroupId }">
        <section v-for="group in groups.filter(item => item.id === activeGroupId)" :key="group.key" class="bom-group">
          <div v-if="group.id" class="spec-toolbar">
            <strong>{{ group.label }}<template v-if="!batchMode"> · {{ activeSpec(group.id)?.label || '没有有效规格' }}</template></strong>
          </div>
          <template v-if="batchMode && group.id">
            <a-empty v-if="!batchSpecs.length || !matrixRows.length"
              :description="batchSpecs.length ? '点击选择物料，开始录入用量' : '在左侧勾选要录入的颜色尺码'" />
            <div v-else class="bom-matrix-scroll" @keydown.enter="moveBomInput">
              <table class="bom-matrix" :class="{ 'bom-matrix-shared': batchEntryMode !== 'individual' }"
                :style="{ '--bom-spec-count': batchSpecs.length }">
                <colgroup>
                  <col class="bom-matrix-material-col" />
                  <col class="bom-matrix-category-col" />
                  <col v-if="batchEntryMode === 'size' || batchEntryMode === 'color'" class="bom-matrix-shared-col" />
                  <col class="bom-matrix-unit-col" />
                  <col v-for="spec in batchSpecs" :key="spec.key" />
                  <col class="bom-matrix-fill-col" />
                </colgroup>
                <thead><tr>
                  <th scope="col">物料明细</th>
                  <th scope="col">物料分类</th>
                  <th v-if="batchEntryMode === 'size'" scope="col">物料规格</th>
                  <th v-if="batchEntryMode === 'color'" scope="col">物料颜色</th>
                  <th scope="col">用量单位</th>
                  <th v-for="spec in batchSpecs" :key="spec.key" scope="col" class="bom-matrix-spec">{{ spec.label }}</th>
                  <th scope="col">
                    批量填充
                    <a-select v-model:value="batchFillMode" size="small" aria-label="批量填充方式"
                      :options="[{ value: 'blank', label: '填空白' }, { value: 'overwrite', label: '覆盖已填' }]" />
                  </th>
                </tr></thead>
                <tbody>
                  <tr v-for="row in matrixRows" :key="row.key">
                    <th scope="row">
                      {{ row.source.material || row.source.materialId }}
                      <small>{{ [!hasMaterialVariants(row.source) && row.source.materialSpec, row.source.usageType].filter(Boolean).join(' · ') }}</small>
                      <small v-if="row.source.remark">{{ row.source.remark }}</small>
                      <a-button v-if="!readonly" size="small" type="link" danger @click="removeMatrixMaterial(row)">移除</a-button>
                    </th>
                    <td class="bom-matrix-category">{{ row.source.materialCategory || '—' }}</td>
                    <td v-if="batchEntryMode === 'size'">
                      <a-select :dropdown-match-select-width="false" v-if="hasMaterialVariants(row.source)" size="small" allow-clear show-search option-filter-prop="label"
                        aria-label="批量选择物料规格" :placeholder="matrixSharedValue(row, 'materialVariantId') === undefined ? '不一致' : '选择规格'" :value="matrixSharedValue(row, 'materialVariantId') || undefined"
                        :options="variantOptions(row.source)" @change="(value: unknown) => updateMatrixDetails(row, 'variant', typeof value === 'string' ? value : undefined)" />
                      <span v-else>—</span>
                    </td>
                    <td v-if="batchEntryMode === 'color'">
                      <a-select :dropdown-match-select-width="false" size="small" allow-clear aria-label="批量设置物料颜色" :placeholder="matrixSharedValue(row, 'materialColorId') === undefined ? '不一致' : '选择颜色'"
                        :value="matrixSharedValue(row, 'materialColorId') || undefined" :options="materialColors[row.source.materialId || ''] || []"
                        @change="(value: unknown) => updateMatrixDetails(row, 'color', typeof value === 'string' ? value : undefined)" />
                    </td>
                    <td>
                      <a-select :dropdown-match-select-width="false" size="small" aria-label="批量设置用量单位" placeholder="选择单位"
                        :value="row.source.usageUnitId" :options="allowedUnits(row.source)"
                        @change="(value: unknown) => updateMatrixDetails(row, 'unit', typeof value === 'string' ? value : undefined)" />
                      <a-input v-if="variableUnit(row.source)" size="small" inputmode="decimal" aria-label="批量设置换算系数"
                        :default-value="row.source.conversionFactor || ''" placeholder="换算系数"
                        @blur="event => updateMatrixDetails(row, 'factor', (event.target as HTMLInputElement).value.trim())" />
                    </td>
                    <td v-for="spec in batchSpecs" :key="spec.key">
                      <span v-if="matrixCellBlocked(row, spec)" title="已有其他用料明细，请在单规格中编辑">已有其他明细</span>
                      <div v-else class="bom-matrix-cell">
                      <a-select :dropdown-match-select-width="false" v-if="batchEntryMode !== 'size' && hasMaterialVariants(row.source)" size="small" allow-clear show-search option-filter-prop="label"
                        :aria-label="spec.label + '物料规格'" placeholder="物料规格" :value="row.cells.get(spec.key)?.[0]?.materialVariantId"
                        :options="variantOptions(row.cells.get(spec.key)?.[0] || row.source)"
                        @change="(value: unknown) => writeMatrixUsage(row, [spec], typeof value === 'string' ? value : '', 'materialVariantId')" />
                      <a-select :dropdown-match-select-width="false" v-if="batchEntryMode !== 'color' && materialColors[row.source.materialId || '']?.length" size="small" allow-clear
                        :aria-label="spec.label + '物料颜色'" placeholder="物料颜色" :value="row.cells.get(spec.key)?.[0]?.materialColorId"
                        :options="materialColors[row.source.materialId || ''] || []"
                        @change="(value: unknown) => writeMatrixUsage(row, [spec], typeof value === 'string' ? value : '', 'materialColorId')" />
                      <span v-if="readonly">{{ displayDecimal(row.cells.get(spec.key)?.[0]?.usage,bomPrecision,rawUsage) }}</span>
                      <a-input v-else data-bom-field="usage" :key="row.cells.get(spec.key)?.[0]?.usage || ''"
                        :default-value="row.cells.get(spec.key)?.[0]?.usage || ''" size="small" inputmode="decimal"
                        :aria-label="spec.label + '用量'" placeholder="填写用量"
                        @blur="event => writeMatrixUsage(row, [spec], (event.target as HTMLInputElement).value)" />
                      </div>
                    </td>
                    <td>
                      <div class="bom-matrix-cell">
                        <a-select :dropdown-match-select-width="false" v-if="batchEntryMode !== 'size' && hasMaterialVariants(row.source)" size="small"
                          show-search option-filter-prop="label" aria-label="批量填充物料规格" :value="undefined" :placeholder="overwriteBatchValues ? '统一规格' : '补空白规格'"
                          :options="variantOptions({ ...row.source, materialVariantId: undefined })"
                          @select="(value: unknown) => fillMatrixField(row, value, 'materialVariantId')" />
                        <a-select :dropdown-match-select-width="false" v-if="batchEntryMode !== 'color' && materialColors[row.source.materialId || '']?.length" size="small"
                          show-search option-filter-prop="label" aria-label="批量填充物料颜色" :value="undefined" :placeholder="overwriteBatchValues ? '统一颜色' : '补空白颜色'"
                          :options="materialColors[row.source.materialId || ''] || []"
                          @select="(value: unknown) => fillMatrixField(row, value, 'materialColorId')" />
                      <a-input-search size="small" inputmode="decimal" aria-label="批量填充用量"
                        placeholder="填写用量" enter-button="应用" @search="value => fillMatrixField(row, value)" />
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="matrixRows.length" class="bom-matrix-note">行设置应用到已勾选且已有该物料的列。默认只填当前字段的空白。选择「覆盖已填」可同时更新已有值和空白。切换模式或取消勾选会保留已录内容。</p>
          </template>
          <template v-else>
          <div v-if="visibleRules[group.id]?.length" class="bom-single-scroll" data-hover-grid @keydown.enter="moveBomInput">
          <div class="bom-rule-head"><span data-hover-column="1">物料分类</span><span data-hover-column="2">物料</span><span data-hover-column="3">物料规格</span><span data-hover-column="4">物料颜色</span><span data-hover-column="5">用量单位</span><span data-hover-column="6">{{ group.id ? '单件用量' : '每套用量' }}</span><span data-hover-column="7">备注</span><span v-if="!readonly" data-hover-column="8">操作</span></div>
          <template v-for="(rule, ruleIndex) in visibleRules[group.id]" :key="rules.indexOf(rule)">
            <div v-if="activeSpec(group.id)" class="bom-rule" :class="{ 'bom-rule-striped': ruleIndex % 2 === 1 }">
              <template v-if="!exact(rule, activeSpec(group.id)!) && !readonly">
                <span class="bom-rule-category" data-hover-column="1" :title="rule.materialCategory">{{ rule.materialCategory || '—' }}</span>
                <span class="bom-inherited-material">{{ [rule.material, rule.materialSpec, rule.materialColor, rule.usage, rule.usageUnitName || rule.unit].filter(Boolean).join(' · ') }}</span>
                <a-button size="small" @click="editInherited(rule, activeSpec(group.id)!)">调整当前规格</a-button>
              </template>
              <template v-else>
              <span class="bom-rule-category" data-hover-column="1" :title="rule.materialCategory">{{ rule.materialCategory || '—' }}</span>
              <a-form-item v-if="!rule.omitted" data-hover-column="2" label="物料" required><a-select :value="rule.materialId || undefined" allow-clear placeholder="选择物料" show-search option-filter-prop="label" :options="materialOptions(rule)" @change="(value: unknown) => changeMaterialChoice(rule, typeof value === 'string' ? value : undefined)" /></a-form-item>
              <a-form-item data-hover-column="3" label="物料规格">
                <a-select v-if="hasMaterialVariants(rule)" allow-clear show-search option-filter-prop="label"
                  aria-data-hover-column="3" label="物料规格" placeholder="选择物料规格" :value="rule.materialVariantId" :options="variantOptions(rule)"
                  @change="(value: unknown) => setMaterialVariant(rule, typeof value === 'string' ? value : undefined)" />
                <span v-else>—</span>
              </a-form-item>
              <a-form-item v-if="!rule.omitted" data-hover-column="4" label="物料颜色">
                <a-input v-if="readonly" :value="rule.materialColor || '—'" />
                <a-select v-else v-model:value="rule.materialColorId" allow-clear
                  :status="rule.materialColorId && materialColors[rule.materialId || ''] && !materialColors[rule.materialId || '']?.some(color => color.value === rule.materialColorId) ? 'error' : undefined"
                  :options="materialColors[rule.materialId || ''] || []" />
              </a-form-item>
              <a-form-item v-if="!rule.omitted" data-hover-column="5" label="用量单位" required>
                <a-select v-model:value="rule.usageUnitId" :options="allowedUnits(rule)" @change="changeUnit(rule)" />
              </a-form-item>
              <a-form-item v-if="!rule.omitted" data-hover-column="6" :label="group.id ? '单件用量' : '每套用量'" required>
                <span v-if="readonly">{{ displayDecimal(rule.usage,bomPrecision,rawUsage) }} {{ rule.usageUnitName || rule.unit }}</span>
                <a-input v-else data-bom-field="usage" v-model:value="rule.usage" inputmode="decimal" :suffix="rule.usageUnitName || rule.unit" />
              </a-form-item>
              <a-form-item data-hover-column="7" label="备注"><a-input data-bom-field="remark" v-model:value="rule.remark" :maxlength="500" /></a-form-item>
              <a-form-item v-if="!rule.omitted && variableUnit(rule)" class="bom-conversion" :label="'每单位等于多少' + (rule.unit || '库存单位')" required>
                <a-input data-bom-field="factor" v-model:value="rule.conversionFactor" inputmode="decimal" placeholder="填写换算系数" />
              </a-form-item>
              </template>
              <div v-if="!readonly" class="rule-actions" data-hover-column="8">
                <a-button size="small" danger @click="removeRule(rule, group.id, activeSpec(group.id)!)">移除</a-button>
              </div>
            </div>
          </template>
          </div>
          <a-empty v-else description="当前规格尚无用料" :image-style="{ height: '28px' }" />
          </template>
        </section>
          </div>
        </div>
        </template>
        </a-spin>
      </a-form>
      <footer class="document-footer">
        <span v-if="styleId && context.parts.length">已填写 {{ groups.filter(group => group.id).reduce((sum, group) => sum + (specsByPart[group.id] || []).filter(spec => specStatus(group.id, spec) === '已填写').length, 0) }} / {{ groups.filter(group => group.id).reduce((sum, group) => sum + (specsByPart[group.id] || []).length, 0) }} 个规格</span>
        <span v-else>请选择款式后维护用料</span>
        <a-space>
          <a-checkbox v-if="readonly" v-model:checked="rawUsage">查看用量原值</a-checkbox>
          <a-button :disabled="saving" @click="closeEditor">{{ readonly ? '关闭' : '取消' }}</a-button>
          <template v-if="!readonly">
            <a-button :loading="saving" :disabled="!styleId || loading" @click="save(false)">保存草稿</a-button>
            <a-button type="primary" :loading="saving" :disabled="!styleId || loading" @click="Modal.confirm({ title: '发布 BOM', content: '发布后该版本只读。发布前系统将检查颜色尺码用料。', okText: '发布', cancelText: '取消', onOk: () => save(true) })">发布</a-button>
          </template>
        </a-space>
      </footer>
      <StandardPicker v-model:open="personPickerOpen" v-model="personPickerSelected" title="选择制单人" :tree="[]" :items="personPickerItems" :columns="[{title:'工号',dataIndex:'jobNo',key:'jobNo'},{title:'姓名',dataIndex:'name',key:'name'},{title:'组织',dataIndex:'org',key:'org'}]" hide-tree search-placeholder="搜索工号、姓名或组织" @confirm="ids => preparerId = ids[0] || ''" />
    </section>
    <StandardPicker v-model:open="stylePickerOpen" v-model="stylePickerSelected" title="选择款式"
      :width="'min(1150px, 94vw)'" :page-size="20" :page-size-options="['10','20','50']"
      :tree="stylePickerTree" :items="stylePickerItems" :columns="stylePickerColumns" tree-title="款式分类"
      search-placeholder="搜索款号、款式名称、客户或分类" @confirm="chooseStyle">
      <template #filters>
        <a-alert v-if="styleScopeUnavailable" type="warning" show-icon message="默认分类无效或无权查看，请联系管理员。" />
        <a-alert v-else-if="styleScopeRestricted" type="info" show-icon message="仅显示有权查看的分类和款式。" />
      </template>
    </StandardPicker>
    <StandardPicker v-model:open="materialPickerOpen" v-model="materialPickerSelected" title="选择物料"
      multiple bordered dim-disabled-rows :width="'min(1450px, 94vw)'" :page-size="20" :page-size-options="['10', '20', '50', '100']" :tree="materialPickerTree" :items="materialPickerItems" :columns="materialPickerColumns"
      tree-title="物料分类" search-placeholder="搜索物料编码、名称、分类或规格"
      @confirm="addSelectedMaterials">
      <template #cell="{ column, item, text }">
        <a-popover v-if="column.key === 'variantSummary' && item.variants.length" title="可选物料规格" :trigger="['hover', 'focus', 'click']">
          <template #content><div class="bom-picker-details">
            <div v-for="variant in item.variants" :key="variant.title">
              <strong>{{ variant.title }}</strong>
              <div v-for="attribute in variant.attributes" :key="attribute">{{ attribute }}</div>
            </div>
          </div></template>
          <a-button type="link" size="small">{{ text }}</a-button>
        </a-popover>
        <a-popover v-else-if="(column.key === 'colorSummary' && item.colors.length) || column.key === 'unitSummary' || (column.key === 'templateName' && item.attributes.length)"
          :title="column.key === 'templateName' ? '共用属性' : column.title" :trigger="['hover', 'focus', 'click']">
          <template #content><div class="bom-picker-details">
            <div v-for="line in (column.key === 'colorSummary' ? item.colors : column.key === 'unitSummary' ? item.unitDetails : item.attributes)" :key="line">{{ line }}</div>
          </div></template>
          <a-button type="link" size="small">{{ text }}</a-button>
        </a-popover>
        <template v-else>{{ text || '—' }}</template>
      </template>
      <template #filters>
        <div v-if="(specsByPart[pickerPartId]?.length || 0) > 1">
          <a-radio-group v-model:value="pickerScope" aria-label="添加物料范围">
            <a-radio value="selected">当前选中规格</a-radio>
            <a-radio value="all">全部颜色尺码</a-radio>
          </a-radio-group>
          <div v-if="pickerScope === 'all'">适用于当前款或子款的 {{ pickerSpecs.length }} 个有效规格，仅补缺少的物料。</div>
        </div>
        <a-alert v-if="materialDefaultInvalid" type="warning" show-icon
          message="物料默认分类无效或您无权查看，请联系管理员检查参数设置和分类权限。" />
        <a-alert v-else-if="materialCategoriesRestricted" type="info" show-icon
          message="仅显示您有权查看的分类和物料，如需其他分类请联系管理员。" />
      </template>
    </StandardPicker>
    <a-modal v-model:open="helpOpen" title="款式 BOM 使用指引" :width="640" :z-index="1100"
      :mask-closable="false" :keyboard="false">
      <div class="bom-help-content">
        <h4>用料模板怎么填</h4>
        <p>单规格和批量填写共用模板。表头下方两行是填写说明，可以保留。请从第 4 行开始填写，每行录入一条用料。导入会替换当前单据的全部用料，核对后再保存。</p>
        <table class="bom-import-guide">
          <thead><tr><th scope="col">列名</th><th scope="col">填写要求</th><th scope="col">填写内容</th></tr></thead>
          <tbody>
            <tr v-for="([requirement, description], index) in bomCsvGuide" :key="bomCsvHeader[index]">
              <th scope="row">{{ bomCsvHeader[index] }}</th><td>{{ requirement }}</td><td>{{ description }}</td>
            </tr>
          </tbody>
        </table>
        <h4>共用物料</h4>
        <p>在「选择物料」中选择「全部颜色尺码」，可一次把物料加到当前款或子款缺少该物料的规格。已有用料保留，新增行的物料规格、颜色和用量可以逐个填写或批量设置。</p>
        <h4>单个颜色尺码录入</h4>
        <p>在左侧选成衣颜色尺码，再添加物料。多规格物料须选具体规格；物料未配置颜色时可留空。填写用量单位和单件用量。</p>
        <h4>批量填用量</h4>
        <ul>
          <li><strong>逐列设置</strong>：各列分别选物料规格、颜色和用量。</li>
          <li><strong>按颜色</strong>：先选同一成衣颜色，物料颜色按行设置，规格和用量按列填写。</li>
          <li><strong>按尺码</strong>：先选同一成衣尺码，物料规格按行设置，颜色和用量按列填写。</li>
        </ul>
        <p>右侧「批量填充」默认只补空白。选择「覆盖已填」后，可批量修改已填写的用量、物料颜色或规格。切换模式或取消勾选会保留已录内容。</p>
        <h4>键盘操作</h4>
        <p>Tab 和 Shift + Tab 按顺序切换输入控件。填写用量时，Enter 跳到下一物料行的同一列，Shift + Enter 跳到上一行。下拉框可用方向键选择，回车确认。</p>
        <h4>套装用料</h4>
        <p>子款用料按单件填写，整套共用物料按套填写。</p>
      </div>
      <template #footer><a-button type="primary" @click="helpOpen = false">关闭</a-button></template>
    </a-modal>
    <a-modal v-model:open="copyOpen" title="复制用料到其他颜色尺码" :width="680" :z-index="1100"
      ok-text="复制" cancel-text="取消" :ok-button-props="{ disabled: !copyTargets.length }" @ok="copyToSelected">
      <p>当前来源：{{ activeSpec(activeGroupId)?.label }}</p>
      <a-checkbox-group v-model:value="copyTargets" class="copy-targets"
        :options="(specsByPart[activeGroupId] || []).filter(spec => spec.key !== activeSpec(activeGroupId)?.key)
          .map(spec => ({ value: spec.key, label: spec.label + (matchedRules(activeGroupId, spec).some(rule => !rule.omitted) ? '（已有用料）' : '') }))" />
    </a-modal>
    <a-modal v-model:open="coverageOpen" title="规格核对" :width="780" :footer="null">
      <template v-if="coverage">
        <a-alert :type="coverage.issueCount ? 'warning' : 'success'" show-icon
          :message="'已核对 ' + coverage.checked + ' 项，发现 ' + coverage.issueCount + ' 处问题'" />
        <ul v-if="coverage.issues.length" class="coverage-issues">
          <li v-for="(issue, index) in coverage.issues" :key="index">{{ issue }}</li>
        </ul>
        <p v-if="coverage.issueCount > coverage.issues.length">仅显示前 {{ coverage.issues.length }} 项</p>
      </template>
    </a-modal>
    <a-modal v-model:open="previewOpen" title="用量试算" :width="940" :footer="null" :mask-closable="false">
      <a-form layout="inline" class="preview-controls">
        <a-form-item v-if="isSet" label="套装规格"><a-select v-model:value="setSpecId" class="preview-option"
          :options="context.specs.map(spec => ({ value: spec.id, label: spec.colorName + ' / ' + spec.sizeName }))" /></a-form-item>
        <template v-else>
          <a-form-item v-if="context.parts[0]?.colors.length" label="颜色"><a-select v-model:value="colorId" class="preview-option" :options="context.parts[0].colors" @change="sizeId = undefined" /></a-form-item>
          <a-form-item v-if="context.parts[0]?.sizes.length" label="尺码"><a-select v-model:value="sizeId" class="preview-option" :options="context.parts[0].sizes.filter(option => !context.parts[0].excluded.some(pair => pair.colorId === colorId && pair.sizeId === option.value))" /></a-form-item>
        </template>
        <a-form-item :label="isSet ? '套数' : '件数'"><a-input-number v-model:value="count" :min="1" :max="10000000" :precision="0" /></a-form-item>
        <a-button type="primary" @click="preview">试算</a-button>
      </a-form>
      <template v-if="demand">
        <a-alert v-if="demand.gaps.length" type="warning" :message="demand.gaps.join('；')" />
        <a-tabs><a-tab-pane key="total" tab="物料汇总"><a-table :columns="demandColumns.slice(3)" :data-source="demand.totals" :pagination="false" size="small" /></a-tab-pane>
          <a-tab-pane key="details" tab="子款用料明细"><a-table :columns="demandColumns" :data-source="demand.details" :pagination="false" size="small" /></a-tab-pane></a-tabs>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.bom-page { height: 100%; min-height: 0; }
.tree-heading { display: flex; align-items: center; justify-content: space-between; padding: 14px 12px 8px; }
:deep(.std-sidebar .ant-tree) { overflow: auto; padding: 0 8px 12px; }
.style-field { min-height: 36px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 3px 4px 3px 12px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; }
.style-field span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bom-document { height: 100%; min-height: 0; display: flex; flex-direction: column; background: #f5f7fa; }
.document-meta { display: grid; grid-column: 1 / -1; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; padding-top: 10px; border-top: 1px solid #edf0f5; }
.meta-field { min-width: 0; min-height: 48px; display: flex; flex-direction: column; justify-content: center; gap: 2px; padding: 4px 9px; border: 1px solid #d9dfe8; border-radius: 6px; background: #fff; }
.meta-field label { color: #86909c; font-size: 11px; line-height: 1.1; }
.meta-field span { overflow: hidden; color: #1d2939; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.meta-field :deep(.ant-input) { height: 21px; padding: 0; border: 0; border-radius: 0; box-shadow: none; background: transparent; color: #1d2939; font-size: 12px; }
.meta-field :deep(.ant-input[readonly]) { cursor: pointer; }
.meta-field label b { color: #f5222d; font-weight: 400; }
.document-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 20px; background: #fff; border-top: 1px solid #e6ebf2; color: #758398; }
.document-help { max-width: 420px; color: #4e5969; line-height: 1.7; }
.document-help p { margin: 0 0 8px; }
.document-help p:last-child { margin-bottom: 0; }
.style-select { width: min(420px, 100%); }
.bom-editor { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; padding: 18px 22px; }
.bom-header { display: grid; grid-template-columns: minmax(240px, 320px) minmax(320px, 460px) minmax(160px, 190px); justify-content: start; align-items: center; gap: 10px; padding: 0; background: transparent; border: 0; border-radius: 0; }
.bom-header :deep(.ant-form-item) { min-width: 0; min-height: 60px; margin: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px; padding: 6px 10px; border: 1px solid #d9dfe8; border-radius: 6px; background: #fff; }
.bom-header :deep(.ant-form-item-label) { padding: 0; line-height: 1.2; }
.bom-header :deep(.ant-form-item-label > label) { height: auto; color: #86909c; font-size: 12px; }
.bom-header-style, .bom-header-remark, .bom-header-code { width: auto; max-width: none; }
.bom-header :deep(.ant-input), .bom-header :deep(.style-field) { height: 24px; min-height: 24px; padding: 0; border: 0; border-radius: 0; box-shadow: none; background: transparent; }
.bom-header :deep(.style-field) { padding-left: 0; }
.bom-header :deep(.style-field .ant-btn) { height: 24px; padding: 0 6px; }
.bom-version-field { min-height: 60px; }
.header-actions-field { min-width: 0; min-height: 60px; display: flex; flex-direction: column; justify-content: center; gap: 4px; padding: 6px 10px; border: 1px solid #d9dfe8; border-radius: 6px; background: #fff; }
.header-actions-field > label { color: #86909c; font-size: 12px; line-height: 1.2; }
.header-actions { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.bom-picker-details { max-width: min(360px, 80vw); max-height: 300px; overflow: auto; overflow-wrap: anywhere; }
.bom-picker-details > div + div { margin-top: 4px; }
.bom-workspace-toolbar { display: flex; justify-content: flex-start; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 14px; padding: 10px 12px; background: #fff; border: 1px solid #e6ebf2; border-radius: 8px; }
.bom-workspace-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: 12px; }
.bom-workspace-heading strong { color: #1d2939; font-size: 15px; }
.bom-workspace-heading span { color: #86909c; font-size: 12px; }
.bom-header-status { display: flex; align-items: center; min-height: 24px; gap: 6px; color: #1d2939; white-space: nowrap; }
.bom-header-status :deep(.ant-tag) { margin-right: 0; }
.bom-help-button { align-self: center; }
.bom-help-content { max-height: 65vh; overflow-y: auto; }
.bom-import-guide { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
.bom-import-guide th, .bom-import-guide td { padding: 6px 8px; border: 1px solid #e6ebf2; text-align: left; vertical-align: top; }
.bom-import-guide th, .bom-import-guide td:first-of-type { white-space: nowrap; }
.bom-help-content h4 { margin: 0 0 6px; }
.bom-editor > :deep(.ant-spin-nested-loading) { flex: 1 1 0; height: 0; min-height: 0; display: flex; flex-direction: column; }
.bom-editor :deep(.ant-spin-container) { flex: 1 1 0; height: 100%; min-height: 0; display: flex; flex-direction: column; }
.bom-editor > :deep(.ant-spin-nested-loading > .ant-spin-container > .ant-empty) { margin: auto; }
.copy-targets { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; max-height: 50vh; overflow: auto; }
.bom-workspace { display: flex; flex: 1; gap: 14px; min-height: 0; margin-top: 14px; align-items: stretch; }
.bom-sidebar { flex: 0 0 260px; min-height: 0; overflow: auto; padding: 12px; background: #fff; border: 1px solid #e6ebf2; border-radius: 8px; }
.bom-sidebar-title { display: flex; flex-direction: column; gap: 2px; padding: 2px 4px 12px; margin-bottom: 12px; border-bottom: 1px solid #edf0f5; }
.bom-sidebar-title strong { color: #1d2939; font-size: 15px; }
.bom-sidebar-title span { color: #86909c; font-size: 12px; }
.bom-entry-mode { width: 100%; margin-top: 6px; }
.bom-mode-help { margin: 6px 0; color: #697586; }
.bom-matrix-cell { display: grid; grid-template-columns: minmax(0, 1fr); gap: 4px; }
.bom-batch-specs { display: grid; gap: 8px; margin-top: 8px; }
.bom-batch-specs :deep(.ant-checkbox-wrapper) { margin-inline-start: 0; }
/* 横向和纵向由同一个表格容器滚动，表头和首列才能同时固定。
   Use one scroll container for sticky headers on both axes. */
.bom-workspace-main.bom-workspace-main-single,
.bom-workspace-main.bom-workspace-main-matrix { display: flex; flex-direction: column; overflow: hidden; }
.bom-workspace-main-single .bom-group,
.bom-workspace-main-matrix .bom-group { display: flex; flex-direction: column; min-height: 0; flex: 1; }
.bom-workspace-main-single .spec-toolbar,
.bom-workspace-main-matrix .spec-toolbar,
.bom-matrix-note { flex-shrink: 0; }
.bom-single-scroll,
.bom-matrix-scroll { flex: 1 1 auto; min-height: 0; overflow: auto; overscroll-behavior: contain; }
@media (max-width: 850px) {
  .bom-workspace-main-single .bom-single-scroll,
  .bom-workspace-main-matrix .bom-matrix-scroll { max-height: 55vh; }
}
/* 各列从最小宽度起排，空间有余时由表格分配。 / Size columns from their minimum widths and share extra space. */
/* 固定列宽与偏移共用变量，色码列平分剩余空间。 / Share widths and offsets for pinned columns. */
.bom-matrix {
  --bom-material-width: 172px;
  --bom-category-width: 104px;
  --bom-shared-width: 0px;
  --bom-unit-width: 100px;
  --bom-fill-width: 140px;
  --bom-category-left: var(--bom-material-width);
  --bom-shared-left: calc(var(--bom-category-left) + var(--bom-category-width));
  --bom-unit-left: calc(var(--bom-shared-left) + var(--bom-shared-width));
  width: 100%;
  min-width: calc(var(--bom-unit-left) + var(--bom-unit-width) + var(--bom-fill-width) + var(--bom-spec-count) * 96px);
  table-layout: fixed;
  border-spacing: 0;
  text-align: left;
}
.bom-matrix-shared { --bom-shared-width: 104px; }
.bom-matrix-material-col { width: var(--bom-material-width); }
.bom-matrix-category-col { width: var(--bom-category-width); }
.bom-matrix-shared-col { width: var(--bom-shared-width); }
.bom-matrix-unit-col { width: var(--bom-unit-width); }
.bom-matrix-fill-col { width: var(--bom-fill-width); }
.bom-matrix th, .bom-matrix td { padding: 8px; border-right: 1px solid #e6ebf2; border-bottom: 1px solid #e6ebf2; overflow-wrap: anywhere; background: #fff; }
.bom-matrix thead th { position: sticky; top: 0; z-index: 2; background: #f7f9fc; }
.bom-matrix tr > :nth-child(-n+3),
.bom-matrix-shared tr > :nth-child(4),
.bom-matrix tr > :last-child { position: sticky; z-index: 3; }
.bom-matrix tr > :first-child { left: 0; }
.bom-matrix tr > :nth-child(2) { left: var(--bom-category-left); }
.bom-matrix tr > :nth-child(3) { left: var(--bom-shared-left); }
.bom-matrix-shared tr > :nth-child(4) { left: var(--bom-unit-left); }
.bom-matrix tr > :last-child { right: 0; }
.bom-matrix thead tr > :nth-child(-n+3),
.bom-matrix-shared thead tr > :nth-child(4),
.bom-matrix thead tr > :last-child { z-index: 4; }
.bom-matrix tbody tr:nth-child(even) > * { background-color: #eaf0f8; }
.bom-matrix tbody tr:hover > *, .bom-matrix tbody tr:focus-within > * { background-color: #c8e0ff; }
.bom-matrix :deep(.ant-select),
.bom-matrix :deep(.ant-input),
.bom-matrix :deep(.ant-input-search) { width: 100%; min-width: 0; }
.bom-matrix td > :deep(.ant-select + .ant-input) { margin-top: 4px; }

.bom-matrix small { display: block; color: #697586; font-weight: normal; }
.bom-matrix-note { margin: 10px 0 0; color: #697586; }
.sidebar-group + .sidebar-group { margin-top: 18px; }
.spec-option { width: 100%; display: flex; align-items: center; justify-content: space-between; padding: 9px 10px; margin-top: 5px; border: 0; border-radius: 6px; background: transparent; text-align: left; cursor: pointer; }
.spec-option small { color: #86909c; }
.spec-option.active { color: #1677ff; background: #e6f4ff; font-weight: 600; }
.bom-workspace-main { flex: 1; min-width: 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; }
.bom-group { margin: 0; padding: 20px; border: 1px solid #e6ebf2; border-radius: 8px; background: #fff; }
.bom-group-title strong { font-size: 16px; }
.bom-rule :deep(.ant-form-item) { margin-bottom: 0; }
.bom-rule :deep(.ant-form-item-label) { display: none; }
.bom-rule :deep(.ant-select) { width: 100%; }
.spec-toolbar { margin-bottom: 8px; line-height: 22px; overflow-wrap: anywhere; }
.bom-group-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
/* 表头和用料行共享列宽，在同一容器内滚动。 / Share column tracks and scroll container. */
.bom-rule-head, .bom-rule {
  display: grid;
  grid-template-columns: minmax(132px, 1.15fr) minmax(200px, 2.15fr) minmax(105px, .85fr) minmax(84px, .75fr)
    minmax(84px, .72fr) minmax(96px, .8fr) minmax(108px, 1fr) 60px;
  min-width: 940px;
  align-items: stretch;
  border-inline-start: 1px solid #e6ebf2;
  border-bottom: 1px solid #e6ebf2;
}
.bom-rule-striped { background-color: #eaf0f8; }
.bom-rule:hover, .bom-rule:focus-within { background-color: #c8e0ff; }
.bom-rule-head { position: sticky; top: 0; z-index: 2; color: #697586; font-size: 12px; background: #f7f9fc; }
.bom-rule-head > *, .bom-rule > * { min-width: 0; padding: 8px 6px; border-inline-end: 1px solid #e6ebf2; }
.bom-rule-category { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bom-inherited-material { grid-column: 2 / 7; overflow-wrap: anywhere; }
.bom-rule :deep(.bom-conversion) { grid-column: 2 / 5; grid-row: 2; }
.bom-rule :deep(.bom-conversion .ant-form-item-label) { display: block; }
@media (max-width: 1250px) { .bom-header { grid-template-columns: repeat(3, minmax(0, 1fr)); } .header-actions-field { grid-column: span 2; } .document-meta { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 850px) { .bom-editor { overflow: auto; } .bom-header { grid-template-columns: minmax(0, 1fr); } .header-actions-field { grid-column: auto; } .document-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); } .bom-workspace { flex: none; min-height: 65vh; flex-direction: column; } .bom-sidebar { flex: none; max-height: 220px; } .bom-workspace-main { overflow: visible; } }
/* 单据头字段名保持在控件外，填写或选中后仍可辨认字段含义。 */
.bom-header :deep(.ant-form-item) { min-height: 32px; display: block; padding: 0; border: 0; background: transparent; }
.bom-header :deep(.ant-form-item-row) { display: flex; flex-flow: row nowrap; align-items: center; }
.bom-header :deep(.ant-form-item-label) { flex: none; padding: 0 6px 0 0; }
.bom-header :deep(.ant-form-item-label > label) { color: #4e5969; font-size: 14px; }
.bom-header :deep(.ant-form-item-control) { flex: 1; min-width: 0; }
.bom-header :deep(.ant-input) { height: 32px; padding: 4px 11px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; }
.bom-header :deep(.style-field) { min-height: 32px; height: 32px; padding: 2px 4px 2px 10px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; }
.document-meta { padding-top: 0; border-top: 0; }
.meta-field { min-height: 32px; flex-direction: row; align-items: center; justify-content: flex-start; gap: 6px; padding: 0; border: 0; background: transparent; }
.meta-field label, .header-actions-field > label { flex: none; color: #4e5969; font-size: 14px; white-space: nowrap; }
.meta-field > span:not(.ant-input-affix-wrapper) { box-sizing: border-box; flex: 1; min-width: 0; height: 32px; padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: 6px; background: #f5f5f5; line-height: 20px; }
.meta-field :deep(.ant-input-affix-wrapper) { flex: 1; min-width: 0; height: 32px; }
.meta-field :deep(.ant-input-affix-wrapper .ant-input) { height: auto; padding: 0; border: 0; }
.bom-version-field { min-height: 32px; }
.bom-header-status { box-sizing: border-box; height: 32px; padding: 0 10px; border: 1px solid #d9d9d9; border-radius: 6px; background: #f5f5f5; }
.header-actions-field { min-height: 32px; padding: 0; border: 0; background: transparent; justify-content: center; }
/* 按内容长度分配只读字段宽度，避免短字段占用过多空间。 */
.document-meta { grid-template-columns: minmax(150px, 210px) minmax(190px, 260px) minmax(170px, 220px) minmax(220px, 260px) minmax(240px, 340px) minmax(140px, 190px); }
@media (max-width: 1250px) { .document-meta { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 850px) { .document-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
/* 单据头在桌面端使用整行宽度，避免固定列宽造成右侧空白、字段提前换行。
   Let the document header use the full desktop width so fields do not wrap early. */
.bom-header { width: 100%; box-sizing: border-box; grid-template-columns: minmax(280px, 1.1fr) minmax(420px, 1.8fr) minmax(220px, .8fr); }
.document-meta { width: 100%; box-sizing: border-box; grid-template-columns: minmax(150px, .85fr) minmax(220px, 1.15fr) minmax(200px, 1.05fr) minmax(245px, 1.25fr) minmax(260px, 1.35fr) minmax(200px, 1fr); }
@media (max-width: 1250px) {
  .bom-header { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .document-meta { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 850px) {
  .bom-header { grid-template-columns: minmax(0, 1fr); }
  .document-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
/* 按字段内容安排宽度，并让所有字段共享行空间。 / Flow all document fields together. */
.bom-header { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; }
.document-meta { display: contents; }
.bom-header-code { flex: 0 1 280px; }
.bom-header-style { flex: 1 1 360px; }
.bom-version-field { flex: 0 0 auto; }
.bom-version-number { flex: 0 1 180px; }
.bom-version-number :deep(.ant-input-number) { width: 100%; }
.bom-version-number :deep(.ant-input-number-input) { height: 30px; }
.document-meta > .meta-field { flex: 1 1 200px; }
.document-meta > .bom-header-remark { flex: 1.4 1 280px; }
@media (max-width: 850px) {
  .bom-header-code, .bom-header-style, .document-meta > .meta-field, .document-meta > .bom-header-remark { flex: 1 1 220px; }
}
.rule-actions { grid-column: 8; grid-row: 1; display: flex; justify-content: flex-end; }
.coverage-issues { max-height: 55vh; overflow: auto; padding: 12px 24px; }
.preview-controls { gap: 8px; margin-bottom: 12px; }
.preview-option { min-width: 110px; }
</style>
