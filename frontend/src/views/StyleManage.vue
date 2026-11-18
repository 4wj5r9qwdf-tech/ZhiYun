<script setup lang="ts">
/** 款式主档使用公共列表与分类范围，供订单及样衣后续引用。 / Shared style catalog with employee taxonomy scope. */
import '../assets/standard-list-fill.css'
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import TagSelect from '../components/TagSelect.vue'
import StandardPicker from '../components/StandardPicker.vue'
import { getTagOptions } from '../api/tag'
import { foundationError, foundationRequest } from '../utils/foundation'
import { can } from '../utils/perms'

type Mode = 'single' | 'combined' | 'separate'
type Member = { childId?: string; roleName: string; quantity?: number }
type SpecPair = { colorId: string; sizeId: string }
type SetSpecItem = { childId: string; colorId?: string; sizeId?: string }
type SetSpec = { id?: string; draftKey?: string; colorName: string; sizeName: string; remark: string; items: SetSpecItem[] }
type Configuration = { setSpecs: SetSpec[]; members: Member[]; colorIds: string[]; sizeIds: string[]; excludedPairs: SpecPair[] }
type Reference = { value: string; label: string; disabled?: boolean }
type SpecOptions = { colors: Reference[]; sizes: Reference[]; sizeGroups: (Reference & { sizeIds: string[] })[] }
type StyleAttachment = { ossId?: string; category: string; fileName: string; file?: File; thumbnail?: string; isCover?: boolean; legacyCover?: boolean }
const attachmentCategories = [
  { value: 'main_label', label: '主标' },
  { value: 'size_label', label: '尺码标' }, { value: 'wash_label', label: '洗标' },
  { value: 'hang_tag', label: '吊牌' }, { value: 'tag_sticker', label: '吊牌贴纸' },
  { value: 'document', label: '文档附件' },
]
type Form = Configuration & {
  attachments: StyleAttachment[]
  productionMode: Mode; sameHanger: boolean
  styleCode: string; name: string; customerId?: string; categoryId?: string
  tagIds: string[]; season: string; description: string; coverOssId?: string; remark: string
}
type Row = Form & { id: string; customerName?: string; categoryName: string; status: string; tags: TagOption[]; membersRestricted: boolean; creatorName?: string; updaterName?: string; createTime?: string; updateTime?: string }
type Category = { id: string; schemeId: string; schemeName: string; parentId?: string; name: string; selectable: boolean }
type TreeNode = { value: string; title: string; selectable: boolean; children: TreeNode[] }
type Customer = { value: string; label: string; disabled?: boolean }
const endpoint = '/rd/style'
const tagFeature = 'rd_style'
type TagOption = { value: string; label: string; groupName: string; disabled?: boolean }
const selectedTagOptions = ref<TagOption[]>([])
const catalogTags = ref<TagOption[]>([])
const tagFilter = ref<string>()
const seasonFilter = ref<string>()
/** 季节使用统一选项；旧款式中的历史值继续可见。 */
const seasonOptions = computed(() => {
  const values = new Set(['春', '夏', '秋', '冬', '春夏', '秋冬', '四季'])
  const previous = rows.value.find(row => row.id === editId.value)?.season
  if (previous) values.add(previous)
  return [...values].map(value => ({ value, label: value }))
})
const seasonFilterOptions = computed(() => {
  const values = new Set(seasonOptions.value.map(item => item.value))
  for (const row of rows.value) if (row.season) values.add(row.season)
  return [...values].map(value => ({ value, label: value }))
})
const groupedTagFilters = computed(() => {
  const groups = new Map<string, TagOption[]>()
  for (const tag of catalogTags.value) {
    const items = groups.get(tag.groupName) || []
    items.push(tag)
    groups.set(tag.groupName, items)
  }
  return [...groups].map(([label, options]) => ({ label, options }))
})
const emptyForm = (): Form => ({
  productionMode: 'single', sameHanger: false, attachments: [], members: [], setSpecs: [], colorIds: [], sizeIds: [], excludedPairs: [], tagIds: [], styleCode: '', name: '', season: '', description: '', remark: '' })
const rows = ref<Row[]>([])
const listCovers = ref<Record<string, { ossId: string; url: string }>>({})
const keyword = ref('')
const status = ref<string>()
const sidebarCollapsed = ref(false)
const schemeFilter = ref<string>()
const visibleSchemeId = ref<string>()
const defaultSchemeId = ref<string>()
const categoryScopeUnavailable = ref(true)
const formSchemeId = ref<string>()
let lastDefaultKey: string | null = null
const categoryFilter = ref<string>()
const expandedKeys = ref<string[]>([])
const treeInitialized = ref(false)
const defaultCategoryLoaded = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const compositionRestricted = ref(false)
const specOptions = ref<SpecOptions>({ colors: [], sizes: [], sizeGroups: [] })
const saving = ref(false)
const show = ref(false)
const editId = ref<string>()
const form = ref<Form>(emptyForm())
/** 界面分两步选择，仍沿用服务端既有生产方式字段。 */
const styleKind = computed({
  get: () => form.value.productionMode === 'single' ? 'single' : 'set',
  set: (value: string) => {
    if (value === 'single' && form.value.setSpecs.length) {
      message.warning('请先移除套装规格，再切换为单款')
      return
    }
    form.value.productionMode = value === 'single' ? 'single' : 'combined'
  },
})
const original = ref('')
/** 预览地址仅用于界面展示，不参与未保存内容比较。 */
function formSnapshot() {
  return JSON.stringify(form.value, (key, value) => key === 'thumbnail' ? undefined : value)
}
const preview = ref('')
const attachmentPreview = ref('')
const colorPickerOpen = ref(false)
const sizePickerOpen = ref(false)
const pickerItems = (options: Reference[]) => options.map(item => ({
  id: item.value, name: item.label, orgId: '', searchText: item.label, disabled: item.disabled,
}))
const colorPickerItems = computed(() => pickerItems(specOptions.value.colors))
const sizePickerItems = computed(() => pickerItems(specOptions.value.sizes))
const availableSizeGroups = computed(() => specOptions.value.sizeGroups.filter(group => group.sizeIds.length > 0))
/** 将组内尺码合并到当前款式，保留已选尺码。 / Merge group sizes into the current style. */
function applySizeGroup(value: unknown) {
  const group = availableSizeGroups.value.find(item => item.value === value)
  if (group) form.value.sizeIds = [...new Set([...form.value.sizeIds, ...group.sizeIds])]
}
const chosenLabels = (ids: string[], options: Reference[]) =>
  ids.map(id => options.find(item => item.value === id)?.label || id)
const previewUrls = new Set<string>()
/** 创建用于图片预览的临时地址并登记以便释放。 / Create a temporary image-preview URL and track it for cleanup. */
function imageUrl(blob: Blob) { const url = URL.createObjectURL(blob); previewUrls.add(url); return url }
/** 为附件列表加载受权限保护的缩略图。 / Load authorized thumbnails for style attachments. */
async function loadAttachmentThumbnails(styleId: string, items: StyleAttachment[]) {
  await Promise.all(items.filter(item => item.category !== 'document' && item.ossId && !item.legacyCover).map(async item => {
    try {
      const blob = await api.get(endpoint + '/' + styleId + '/attachments/' + item.ossId, { responseType: 'blob' }) as unknown as Blob
      item.thumbnail = imageUrl(blob)
    } catch { /* 预览失败时保留文件名与手动预览入口。 */ }
  }))
}
type SetDraft = {
  form: Form; editId?: string; original: string; preview: string; schemeId?: string
  tagOptions: TagOption[]; target: Member; appendOnSave: boolean
}
const setDraft = ref<SetDraft>()
const categories = ref<Category[]>([])
const customers = ref<Customer[]>([])
const restricted = ref(false)
const canAdd = computed(() => can('rd:style:add'))
const canEdit = computed(() => can('rd:style:edit'))
const canRemove = computed(() => can('rd:style:remove'))
const columns = [
  { key: 'cover', title: '主图', width: 100 },
  { key: 'styleCode', title: '款号', width: 124 },
  { key: 'name', title: '款式名称', width: 150 },
  { key: 'productionMode', title: '款式类型', width: 126 },
  { key: 'setProduction', title: '生产方式', width: 120 },
  { key: 'categoryName', title: '款式分类', width: 125 },
  { key: 'customerName', title: '客户', width: 140 },
  { key: 'season', title: '季节', width: 100 },
  { key: 'tags', title: '款式标签', width: 140 },
  { key: 'colors', title: '颜色', width: 120 },
  { key: 'sizes', title: '尺码', width: 120 },
  { key: 'composition', title: '套装组成', width: 160 },
  { key: 'status', title: '状态', width: 94 },
  { key: 'sameHanger', title: '同架生产', defaultVisible: false },
  { key: 'parentSets', title: '所属套装', defaultVisible: false },
  { key: 'description', title: '款式说明', defaultVisible: false },
  { key: 'remark', title: '备注', defaultVisible: false },
  { key: 'creatorName', title: '创建人', defaultVisible: false },
  { key: 'createTime', title: '创建时间', defaultVisible: false },
  { key: 'updaterName', title: '修改人', defaultVisible: false },
  { key: 'updateTime', title: '修改时间', defaultVisible: false },
]
/** 分类祖先只来自当前人员可见的分类路径。 / Show the authorized category path. */
function categoryPath(categoryId?: string) {
  const path: string[] = []
  const visited = new Set<string>()
  let node = categories.value.find(item => item.id === categoryId)
  while (node && !visited.has(node.id)) {
    visited.add(node.id)
    path.unshift(node.name)
    node = categories.value.find(item => item.id === node?.parentId)
  }
  return path.join(' / ')
}
/** 按颜色或尺码档案将 ID 列表格式化为名称。 / Resolve color or size identifiers to display names. */
function specText(ids: string[], options: Reference[]) {
  return chosenLabels(ids || [], options).join('、') || '—'
}
/** 汇总被该款式作为子款使用的套装信息。 / Find sets that reference this style as a component. */
function parentSets(row: Row) {
  return rows.value.filter(item => item.productionMode !== 'single' && item.members?.some(member => member.childId === row.id))
    .map(item => item.styleCode + ' · ' + item.name).join('、') || '—'
}
/** 主图通过授权接口读取，并在替换或离开页面时释放地址。 / Load private covers through the authorized endpoint. */
async function loadListCovers(catalog: Row[]) {
  const valid = new Map(catalog.filter(row => row.coverOssId).map(row => [row.id, row.coverOssId!]))
  for (const [id, entry] of Object.entries(listCovers.value)) {
    if (valid.get(id) !== entry.ossId) { URL.revokeObjectURL(entry.url); delete listCovers.value[id] }
  }
  await Promise.all(catalog.filter(row => row.coverOssId && !listCovers.value[row.id]).map(async row => {
    try {
      const blob = await api.get(endpoint + '/' + row.id + '/cover', { responseType: 'blob' }) as unknown as Blob
      if (rows.value.some(item => item.id === row.id && item.coverOssId === row.coverOssId)) {
        listCovers.value[row.id] = { ossId: row.coverOssId!, url: URL.createObjectURL(blob) }
      }
    } catch { /* 图片不可用时显示占位，列表仍可使用。 */ }
  }))
}
/** 打开列表主图预览并确保读取当前行的私有图片。 / Open the list-cover preview using the authorized private image. */
async function previewListCover(row: Row) {
  try {
    const blob = await api.get(endpoint + '/' + row.id + '/cover', { responseType: 'blob' }) as unknown as Blob
    if (attachmentPreview.value) URL.revokeObjectURL(attachmentPreview.value)
    attachmentPreview.value = URL.createObjectURL(blob)
  } catch (error) { message.error(foundationError(error)) }
}
const filtered = computed(() => {
  const search = keyword.value.trim().toLocaleLowerCase()
  if (categoryScopeUnavailable.value) return []
  return rows.value.filter(row => (!defaultSchemeId.value || categories.value.some(category => category.id === row.categoryId && category.schemeId === defaultSchemeId.value)) && (!schemeFilter.value || categories.value.some(category => category.id === row.categoryId && category.schemeId === schemeFilter.value)) &&
    (!selectedCategoryIds.value || selectedCategoryIds.value.has(row.categoryId || '')) && (!seasonFilter.value || row.season === seasonFilter.value) && (!tagFilter.value || row.tagIds.includes(tagFilter.value)) && (!status.value || row.status === status.value) &&
    [row.styleCode, row.name, row.customerName].some(value => value?.toLocaleLowerCase().includes(search)))
})
const formCategoryTree = computed(() => {
  const scheme = defaultSchemeId.value ?? formSchemeId.value
  const source = categoryScopeUnavailable.value ? [] : scheme ? categories.value.filter(row => row.schemeId === scheme) : categories.value
  const types = new Map<string, TreeNode>()
  const nodes = new Map<string, TreeNode>()
  for (const row of source) {
    if (!types.has(row.schemeId)) types.set(row.schemeId, { value: 'type-' + row.schemeId, title: row.schemeName, selectable: false, children: [] })
    nodes.set(row.id, { value: row.id, title: row.name, selectable: row.selectable, children: [] })
  }
  for (const row of source) {
    const parent = (row.parentId && nodes.get(row.parentId)) || types.get(row.schemeId)
    parent?.children.push(nodes.get(row.id)!)
  }
  return [...types.values()]
})
/** 切换款式分类类型并重置不兼容的分类选择。 / Change the style category scheme and clear incompatible category selections. */
function changeFormScheme(value: unknown) {
  formSchemeId.value = typeof value === 'string' ? value : undefined
  const selected = categories.value.find(row => row.id === form.value.categoryId)
  if (formSchemeId.value && selected && selected.schemeId !== formSchemeId.value) form.value.categoryId = undefined
}
/** 分类树包含授权路径祖先；选中节点时展示其下全部可见款式。 */
const allStyleTree = computed(() => {
  const schemes = new Map<string, { key: string; title: string; children: any[] }>()
  const nodes = new Map<string, { key: string; title: string; children: any[] }>()
  for (const category of categories.value) {
    if (!schemes.has(category.schemeId)) schemes.set(category.schemeId, { key: 'scheme:' + category.schemeId, title: category.schemeName, children: [] })
    nodes.set(category.id, { key: 'category:' + category.id, title: category.name, children: [] })
  }
  for (const category of categories.value) {
    const parent = (category.parentId && nodes.get(category.parentId)) || schemes.get(category.schemeId)
    parent?.children.push(nodes.get(category.id)!)
  }
  return [...schemes.values()]
})
const schemeOptions = computed(() => allStyleTree.value.map(node => ({ value: node.key.slice(7), label: node.title })))
const scopedSchemeOptions = computed(() => categoryScopeUnavailable.value ? [] : schemeOptions.value.filter(option => !defaultSchemeId.value || option.value === defaultSchemeId.value))
const styleTree = computed(() => {
  if (categoryScopeUnavailable.value) return []
  const scheme = defaultSchemeId.value ?? visibleSchemeId.value
  return scheme ? allStyleTree.value.filter(node => node.key === 'scheme:' + scheme) : allStyleTree.value
})

/** 切换分类树显示范围且不扩大人员授权边界。 / Change the visible tree scheme without expanding employee access. */
function changeVisibleScheme(value?: unknown) {
  const selected = typeof value === 'string' && value ? value : undefined
  visibleSchemeId.value = selected ?? defaultSchemeId.value
  schemeFilter.value = visibleSchemeId.value
  categoryFilter.value = undefined
}
/** 重置分类树筛选和高亮状态。 / Reset category-tree filters and selection state. */
function resetStyleTree() {
  changeVisibleScheme()
}

/** 只在初次进入或参数发生变化时应用默认值，保留用户手动切换的分类。 */
async function refreshDefaultCategory() {
  try {
    const value = await foundationRequest<{schemeId: string; invalid: boolean}>('get', endpoint + '/default-category')
    const scheme = value.schemeId && allStyleTree.value.some(node => node.key === 'scheme:' + value.schemeId)
      ? value.schemeId : ''
    const key = scheme + ':' + String(value.invalid)
    defaultSchemeId.value = scheme || undefined
    categoryScopeUnavailable.value = value.invalid || Boolean(value.schemeId && !scheme)
    if (!defaultCategoryLoaded.value || lastDefaultKey !== key) {
      visibleSchemeId.value = defaultSchemeId.value
      schemeFilter.value = visibleSchemeId.value
      categoryFilter.value = undefined
      if (categoryScopeUnavailable.value) message.warning('款式配置的分类类型当前不可用，请联系管理员核对参数或权限。')
    }
    lastDefaultKey = key
    defaultCategoryLoaded.value = true
  } catch {
    if (!defaultCategoryLoaded.value) {
      message.warning('款式分类参数读取失败，请重新查询。')
    }
  }
}
const selectedTreeKeys = computed(() => categoryFilter.value ? ['category:' + categoryFilter.value] : schemeFilter.value ? ['scheme:' + schemeFilter.value] : [])
const selectedCategoryIds = computed(() => {
  if (!categoryFilter.value) return null
  const ids = new Set([categoryFilter.value])
  let changed = true
  while (changed) {
    changed = false
    for (const category of categories.value) if (category.parentId && ids.has(category.parentId) && !ids.has(category.id)) {
      ids.add(category.id); changed = true
    }
  }
  return ids
})
/** 按所选分类节点及其可见后代过滤款式列表。 / Filter styles by the selected category and visible descendants. */
function selectStyleTree(keys: (string | number)[]) {
  const key = String(keys[0] || '')
  if (key.startsWith('scheme:')) { schemeFilter.value = key.slice(7); categoryFilter.value = undefined }
  else if (key.startsWith('category:')) {
    const category = categories.value.find(item => item.id === key.slice(9))
    schemeFilter.value = category?.schemeId; categoryFilter.value = category?.id
  } else { schemeFilter.value = visibleSchemeId.value; categoryFilter.value = undefined }
}

const changingStyleKind = computed(() => {
  const current = rows.value.find(row => row.id === editId.value)
  return current && (current.productionMode === 'single') !== (form.value.productionMode === 'single')
})
const hasCategory = computed(() => !categoryScopeUnavailable.value && categories.value.some(row => row.selectable && (!defaultSchemeId.value || row.schemeId === defaultSchemeId.value)))

/** 读取当前人员可见款式和可选资料。 / Load catalog rows and reference options. */
async function load() {
  loading.value = true
  try {
    const [catalog, scope, customerOptions, tagResponse, references] = await Promise.all([
      foundationRequest<Row[]>('get', endpoint + '/list'),
      foundationRequest<{ rows: Category[]; restricted: boolean }>('get', endpoint + '/category-options'),
      foundationRequest<Customer[]>('get', endpoint + '/customer-options'),
      getTagOptions(tagFeature),
      foundationRequest<SpecOptions>('get', endpoint + '/spec-options'),
    ])
    rows.value = catalog
    void loadListCovers(catalog)
    specOptions.value = references
    catalogTags.value = ((tagResponse as unknown as { data: { tagId: string; tagName: string; groupName: string }[] }).data || [])
      .map(tag => ({ value: tag.tagId, label: tag.tagName, groupName: tag.groupName }))
    categories.value = scope.rows.map(category => ({ ...category, id: String(category.id), schemeId: String(category.schemeId), parentId: category.parentId ? String(category.parentId) : undefined }))
    if (!treeInitialized.value && categories.value.length) {
      expandedKeys.value = allStyleTree.value.map(node => node.key)
      treeInitialized.value = true
    }
    if (!defaultCategoryLoaded.value) await refreshDefaultCategory()
    restricted.value = scope.restricted
    customers.value = customerOptions
  } catch (error) {
    rows.value = []
    categories.value = []
    customers.value = []
    message.error(foundationError(error))
  } finally {
    loading.value = false
  }
}

/** 在款式弹窗中定位资料分区。 / Navigate within the style editor. */
function goToStyleSection(section: string) {
  document.getElementById('style-form-' + section)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function clearPreview() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
}

/** 初始化空白款式档案，并应用有效的页面分类定位。 / Initialize a blank style and apply a valid initial category context. */
function openAdd(previous?: Form) {
  clearPreview()
  editId.value = undefined
  selectedTagOptions.value = []
  compositionRestricted.value = false
  form.value = previous ? { ...previous, attachments: [], members: [], setSpecs: [], styleCode: '', name: '', coverOssId: undefined } : emptyForm()
  // 用户定位到具体且可用的分类时，新建款式带出该分类；子款仍单独选择。
  if (!previous && !setDraft.value && categories.value.some(category => category.id === categoryFilter.value && category.selectable)) {
    form.value.categoryId = categoryFilter.value
  }
  const selected = categories.value.find(category => category.id === form.value.categoryId)
  if (defaultSchemeId.value && selected?.schemeId !== defaultSchemeId.value) form.value.categoryId = undefined
  formSchemeId.value = defaultSchemeId.value ?? selected?.schemeId
  original.value = formSnapshot()
  show.value = true
}

/** 通过受权限保护的接口读取现有主图，切换表单时丢弃迟到响应。 / Load the authenticated cover for the current form only. */
async function openEdit(row: Row) {
  if (detailLoading.value) return false
  detailLoading.value = true
  let configuration: Configuration & { restricted: boolean }
  let loadedAttachments: StyleAttachment[] = []
  try {
    const [tags, detail, attachments] = await Promise.all([
      foundationRequest<TagOption[]>('get', endpoint + '/' + row.id + '/tags'),
      foundationRequest<Configuration & { restricted: boolean }>('get', endpoint + '/' + row.id + '/configuration'),
      foundationRequest<StyleAttachment[]>('get', endpoint + '/' + row.id + '/attachments'),
    ])
    selectedTagOptions.value = tags
    configuration = detail
    loadedAttachments = attachments
    const existingCover = loadedAttachments.find(item => item.category === 'style_image' && item.ossId === row.coverOssId)
    if (existingCover) existingCover.isCover = true
    else if (row.coverOssId) loadedAttachments.unshift({ ossId: row.coverOssId, category: 'style_image', fileName: '原款式主图', isCover: true, legacyCover: true })
  } catch (error) {
    message.error(foundationError(error))
    return false
  } finally { detailLoading.value = false }
  clearPreview()
  compositionRestricted.value = configuration.restricted
  editId.value = row.id
  form.value = { attachments: loadedAttachments, members: configuration.members, setSpecs: configuration.setSpecs || [], colorIds: configuration.colorIds, sizeIds: configuration.sizeIds,
    excludedPairs: configuration.excludedPairs, productionMode: row.productionMode, sameHanger: row.sameHanger,
    tagIds: [...row.tagIds], styleCode: row.styleCode, name: row.name,
    customerId: row.customerId || undefined, categoryId: row.categoryId, season: row.season,
    description: row.description, coverOssId: row.coverOssId || undefined, remark: row.remark }
  formSchemeId.value = categories.value.find(category => category.id === row.categoryId)?.schemeId ?? defaultSchemeId.value
  original.value = formSnapshot()
  void loadAttachmentThumbnails(row.id, form.value.attachments)
  const targetForm = form.value
  show.value = true
  if (!row.coverOssId) return true
  try {
    const blob = await api.get(endpoint + '/' + row.id + '/cover', { responseType: 'blob' }) as unknown as Blob
    if (show.value && form.value === targetForm && form.value.coverOssId === row.coverOssId) {
      preview.value = URL.createObjectURL(blob)
      const item = targetForm.attachments.find(image => image.legacyCover)
      if (item) item.thumbnail = imageUrl(blob)
    }
  } catch (error) {
    if (show.value && form.value === targetForm) message.error(foundationError(error))
  }
  return true
}
const customerOptions = computed(() => {
  const id = form.value.customerId
  const previous = rows.value.find(row => row.id === editId.value)
  if (id && previous?.customerId === id && !customers.value.some(customer => customer.value === id)) {
    return [...customers.value, { value: id, label: (previous.customerName || '原客户') + '（已停用）', disabled: true }]
  }
  return customers.value
})

/** 保留停用的历史选项名称，添加子款时只允许选择启用的单款。 */
function memberOptions(member: Member) {
  return rows.value.filter(row => row.id !== editId.value && row.productionMode === 'single'
    && (row.id === member.childId || (row.status === '0' && (!defaultSchemeId.value || categories.value.some(category => category.id === row.categoryId && category.schemeId === defaultSchemeId.value)) && !form.value.members.some(item => item.childId === row.id))))
    .map(row => ({ value: row.id, label: row.styleCode + ' · ' + row.name + (row.status === '1' ? '（已停用）' : '') }))
}

/** 选择子款时带出其分类名称，允许在套装中调整显示名称。 / Suggest the child category for the set. */
function selectMember(member: Member, childId: unknown) {
  const child = rows.value.find(row => row.id === childId)
  member.roleName = child?.categoryName || ''
}

/** 保留套装草稿，复用完整款式表单编辑子款；子款作为独立档案保存。 */
async function openMemberEditor(member: Member, edit: boolean, appendOnSave = false) {
  if (setDraft.value || detailLoading.value || saving.value) return
  const row = edit ? rows.value.find(item => item.id === member.childId) : undefined
  if (edit && !row) return
  const parent: SetDraft = { form: form.value, editId: editId.value, original: original.value,
    preview: preview.value, schemeId: formSchemeId.value, tagOptions: selectedTagOptions.value, target: member, appendOnSave }
  setDraft.value = parent
  preview.value = ''
  if (row) {
    if (!await openEdit(row)) restoreSetDraft()
    return
  }
  openAdd()
  form.value.customerId = parent.form.customerId
  form.value.season = parent.form.season
  original.value = formSnapshot()
}

/** 恢复套装编辑前的主档草稿，避免子款弹窗覆盖父表单。 / Restore the parent set draft after editing a child record. */
function restoreSetDraft(savedChildId?: string) {
  const draft = setDraft.value
  if (!draft) return
  clearPreview()
  form.value = draft.form
  formSchemeId.value = draft.schemeId
  if (savedChildId) {
    const changedChild = draft.target.childId !== savedChildId
    draft.target.childId = savedChildId
    if (changedChild || !draft.target.roleName.trim()) selectMember(draft.target, savedChildId)
    if (draft.appendOnSave) draft.form.members.push(draft.target)
  }
  editId.value = draft.editId
  original.value = draft.original
  preview.value = draft.preview
  selectedTagOptions.value = draft.tagOptions
  setDraft.value = undefined
}

// 通过详情接口读取子款规格，避免依赖列表当前页的数据。
const childConfigurations = ref<Record<string, Configuration>>({})
const loadingChildSpecs = ref(false)
let childSpecRequest = 0
async function loadChildSpecs(ids: string[]) {
  const request = ++childSpecRequest
  loadingChildSpecs.value = true
  childConfigurations.value = {}
  try {
    const details = await Promise.all(ids.map(async id => [id,
      await foundationRequest<Configuration>('get', endpoint + '/' + id + '/configuration')] as const))
    if (request === childSpecRequest) {
      childConfigurations.value = Object.fromEntries(details)
    }
  } catch (error) {
    message.error(foundationError(error))
  } finally {
    if (request === childSpecRequest) loadingChildSpecs.value = false
  }
}
const selectedChildIds = computed(() => form.value.productionMode === 'single' || compositionRestricted.value
  ? [] : form.value.members.flatMap(member => member.childId ? [member.childId] : []))
watch(() => selectedChildIds.value.join(','), () => void loadChildSpecs(selectedChildIds.value))
/** 读取子款颜色尺码并构造套装配套候选值。 / Load child color-size options for set-pairing selection. */
function childSpecOptions(item: SetSpecItem, type: 'color' | 'size') {
  const detail = childConfigurations.value[item.childId]
  if (!detail) return []
  return type === 'color'
    ? specOptions.value.colors.filter(color => detail.colorIds.includes(color.value) && !color.disabled)
    : specOptions.value.sizes.filter(size => item.colorId && detail.sizeIds.includes(size.value) && !size.disabled
      && !detail.excludedPairs.some(pair => pair.colorId === item.colorId && pair.sizeId === size.value))
}
/** 检查套装规格是否完整匹配所有组成子款。 / Check that a set specification includes valid pairings for every member. */
function alignSetSpec(spec: SetSpec) {
  spec.items = selectedChildIds.value.map(childId => spec.items.find(item => item.childId === childId) || { childId })
}
let nextSpecDraft = 0
const expandedSetSpecs = ref<string[]>([])
/** 新增一个套装配色配码方案及其子款组合。 / Add a set color-size scheme and its child-style pairings. */
function addSetSpec(source?: SetSpec) {
  const draftKey = 'new-' + ++nextSpecDraft
  // 复制配套选择，规格名称留空以便录入新的配色或尺码。
  const spec: SetSpec = { draftKey, colorName: '', sizeName: '', remark: source?.remark ?? '',
    items: source?.items.map(item => ({ ...item })) ?? [] }
  alignSetSpec(spec)
  form.value.setSpecs.push(spec)
  expandedSetSpecs.value = [draftKey]
}
/** 生成子款已选颜色尺码的名称。 / Build the selected color-size label for a child style. */
function childSpecName(childId: string) {
  return rows.value.find(row => row.id === childId)?.name
    || form.value.members.find(member => member.childId === childId)?.roleName || '已移除子款'
}
const childrenWithoutPairs = computed(() => selectedChildIds.value.filter(childId =>
  childConfigurations.value[childId] && !childSpecOptions({ childId }, 'color').some(color =>
    childSpecOptions({ childId, colorId: color.value }, 'size').length)))
/** 汇总套装规格中各子款的颜色尺码搭配。 / Summarize child pairings in a set specification. */
function setSpecSummary(spec: SetSpec) {
  return spec.items.map(item => {
    const color = specOptions.value.colors.find(option => option.value === item.colorId)?.label
    const size = specOptions.value.sizes.find(option => option.value === item.sizeId)?.label
    return childSpecName(item.childId) + '：' + (color && size ? color + ' / ' + size : '待配套')
  }).join('；')
}
/** 判断套装规格是否缺少组成子款或有效配套。 / Detect missing members or invalid pairings in a set specification. */
function setSpecNeedsAlignment(spec: SetSpec) {
  return spec.items.length !== selectedChildIds.value.length
    || spec.items.some(item => !selectedChildIds.value.includes(item.childId))
}

/** 调整套装组成子款的展示和处理顺序。 / Reorder set members for display and downstream processing. */
function moveMember(index: number, step: number) {
  const members = form.value.members
  const next = index + step
  if (next < 0 || next >= members.length) return
  const [member] = members.splice(index, 1)
  members.splice(next, 0, member)
}

/** 设置单款可用颜色尺码组合并保持排除关系一致。 / Update available single-style pairs while preserving exclusions. */
function setAvailable(colorId: string, available: string[]) {
  const selected = new Set(available)
  form.value.excludedPairs = form.value.excludedPairs.filter(pair => pair.colorId !== colorId)
    .concat(form.value.sizeIds.filter(sizeId => !selected.has(sizeId)).map(sizeId => ({ colorId, sizeId })))
}

/** 取得所选尺码组中可用于当前款式的尺码。 / Resolve sizes from the selected size group for the current style. */
function availableSizes(colorId: string) {
  return form.value.sizeIds.filter(sizeId => !form.value.excludedPairs.some(pair => pair.colorId === colorId && pair.sizeId === sizeId))
}

/** 移除规格时同步清理相关的组合排除项。 / Remove exclusions for a deselected specification. */
function removeSpec(kind: 'color' | 'size', id: string) {
  if (kind === 'color') {
    form.value.colorIds = form.value.colorIds.filter(value => value !== id)
    form.value.excludedPairs = form.value.excludedPairs.filter(pair => pair.colorId !== id)
  } else {
    form.value.sizeIds = form.value.sizeIds.filter(value => value !== id)
    form.value.excludedPairs = form.value.excludedPairs.filter(pair => pair.sizeId !== id)
  }
}

/** 附件先保存在表单草稿，保存款式时上传并绑定当前档案。 */
function chooseAttachments(event: Event, category: string) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files || [])]
  input.value = ''
  for (const file of files) {
    const allowed = category === 'document' ? /\.(png|jpe?g|pdf|docx|xlsx|pptx)$/i : /\.(png|jpe?g)$/i
    if (!allowed.test(file.name) || file.size > 10 * 1024 * 1024) {
      message.warning('图片支持 PNG、JPG，文档还支持 PDF、DOCX、XLSX、PPTX，单个文件不超过10 MB')
      continue
    }
    if (form.value.attachments.length >= 100) { message.warning('最多添加100个附件'); break }
    const isCover = category === 'style_image' && !form.value.attachments.some(item => item.category === 'style_image' && item.isCover)
    form.value.attachments.push({ category, fileName: file.name, file, isCover, thumbnail: category === 'document' ? undefined : imageUrl(file) })
  }
}

/** 主图属于图片列表；移除主图后选择下一张，避免留下指向已移除文件的引用。 */
function setCover(selected: StyleAttachment) {
  for (const item of form.value.attachments) if (item.category === 'style_image') item.isCover = item === selected
}
function removeGalleryItem(index: number) {
  const [removed] = form.value.attachments.splice(index, 1)
  if (removed?.isCover) {
    const next = form.value.attachments.find(item => item.category === 'style_image')
    if (next) next.isCover = true
  }
}

/** 私有图片经授权接口读取后使用临时地址预览。 */
function closeAttachmentPreview() {
  if (attachmentPreview.value) URL.revokeObjectURL(attachmentPreview.value)
  attachmentPreview.value = ''
}

/** 打开款式附件预览并释放上一次临时地址。 / Preview a style attachment and release the previous temporary URL. */
async function previewAttachment(item: StyleAttachment) {
  if (item.category === 'document') return downloadAttachment(item)
  try {
    const blob = item.file || (item.thumbnail ? await (await fetch(item.thumbnail)).blob() : null) || (editId.value && item.ossId
      ? await api.get(endpoint + '/' + editId.value + (item.legacyCover ? '/cover' : '/attachments/' + item.ossId), { responseType: 'blob' }) as unknown as Blob
      : null)
    if (!blob) return
    if (attachmentPreview.value) URL.revokeObjectURL(attachmentPreview.value)
    attachmentPreview.value = URL.createObjectURL(blob)
  } catch (error) { message.error(foundationError(error)) }
}

/** 通过受权限保护的文件接口下载款式附件。 / Download a style attachment through its authorized file endpoint. */
async function downloadAttachment(item: StyleAttachment) {
  if (item.file) {
    const url = URL.createObjectURL(item.file)
    const link = document.createElement('a')
    link.href = url; link.download = item.fileName; link.click()
    URL.revokeObjectURL(url)
    return
  }
  if (!editId.value || !item.ossId) return
  try {
    const blob = await api.get(endpoint + '/' + editId.value + '/attachments/' + item.ossId, { responseType: 'blob' }) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = item.fileName; link.click()
    URL.revokeObjectURL(url)
  } catch (error) { message.error(foundationError(error)) }
}

/** 关闭款式编辑窗口前确认是否丢弃未保存资料。 / Confirm before discarding unsaved style data when closing. */
function close() {
  if (saving.value || detailLoading.value) return
  const discard = () => {
    if (setDraft.value) restoreSetDraft()
    else { show.value = false; clearPreview() }
  }
  if (formSnapshot() === original.value) discard()
  else Modal.confirm({
    title: setDraft.value ? '放弃子款未保存的修改并返回套装？' : '放弃未保存的修改？',
    okText: setDraft.value ? '放弃并返回' : '放弃修改',
    cancelText: '继续编辑',
    onOk: discard,
  })
}

/** 上传成功的文件保留引用，保存失败重试时不重复上传。 */
async function save(andAdd = false) {
  if (saving.value || compositionRestricted.value) return
  if (!form.value.name.trim() || !form.value.styleCode.trim() || !form.value.categoryId) {
    message.warning('请填写款号、款式名称和款式分类')
    return
  }
  const single = form.value.productionMode === 'single'
  if (!single && (!form.value.members.length || form.value.members.some(member =>
    !member.childId || !member.roleName.trim() || !Number.isInteger(member.quantity) || Number(member.quantity) < 1))) {
    message.warning('请为套装选择子款，并填写子款类别和每套件数')
    return
  }
  if (!single && form.value.setSpecs.some(spec => !spec.colorName.trim() || !spec.sizeName.trim()
    || setSpecNeedsAlignment(spec) || spec.items.some(item => !item.colorId || !item.sizeId))) {
    message.warning('请填写配色名称、套装标称尺码，并补齐每个子款的颜色和尺码')
    return
  }
  saving.value = true
  try {
    for (const item of form.value.attachments) {
      if (!item.file) continue
      const body = new FormData()
      body.append('file', item.file)
      const uploaded = await foundationRequest<{ ossId: string }>('post', endpoint + '/attachments', body)
      item.ossId = uploaded.ossId
      item.file = undefined
    }
    const id = editId.value
    form.value.coverOssId = form.value.attachments.find(item => item.category === 'style_image' && item.isCover)?.ossId
    const payload = {
      ...form.value,
      attachments: form.value.attachments.map(item => ({ ossId: item.ossId, category: item.category })),
      sameHanger: form.value.productionMode === 'combined' && form.value.sameHanger,
      members: single ? [] : form.value.members,
      setSpecs: single ? [] : form.value.setSpecs.map(({ id, colorName, sizeName, remark, items }) => ({ id, colorName, sizeName, remark, items })),
      excludedPairs: form.value.excludedPairs.filter(pair =>
        form.value.colorIds.includes(pair.colorId) && form.value.sizeIds.includes(pair.sizeId)),
    }
    const savedId = await foundationRequest<string>(id ? 'put' : 'post', id ? endpoint + '/' + id : endpoint, payload)
    message.success('款式已保存')
    if (setDraft.value) {
      await load()
      restoreSetDraft(savedId)
      void loadChildSpecs(selectedChildIds.value)
      return
    }
    if (andAdd && !id) openAdd(form.value)
    else { show.value = false; clearPreview() }
    await load()
  } catch (error) { message.error(foundationError(error)) }
  finally { saving.value = false }
}

/** 对影响子款或规格的变更请求用户确认。 / Confirm changes that affect set members or specifications. */
function confirmChange(row: Row, remove = false) {
  const next = row.status === '0' ? '1' : '0'
  const action = remove ? '删除' : next === '0' ? '启用' : '停用'
  Modal.confirm({
    title: action + '款式',
    content: '确认' + action + '“' + row.name + '（' + row.styleCode + '）”？',
    okText: '确认' + action, cancelText: '取消', okButtonProps: { danger: remove },
    onOk: async () => {
      try {
        await foundationRequest(remove ? 'delete' : 'put',
          endpoint + '/' + row.id + (remove ? '' : '/status'), remove ? undefined : { status: next })
        await load()
      } catch (error) { message.error(foundationError(error)); throw error }
    },
  })
}
onMounted(load)
let activatedOnce = false
onActivated(() => {
  if (activatedOnce) {
    void load().then(() => refreshDefaultCategory())
  }
  activatedOnce = true
})
onBeforeUnmount(() => {
  clearPreview()
  if (attachmentPreview.value) URL.revokeObjectURL(attachmentPreview.value)
  if (setDraft.value?.preview) URL.revokeObjectURL(setDraft.value.preview)
  for (const url of previewUrls) URL.revokeObjectURL(url)
  for (const entry of Object.values(listCovers.value)) URL.revokeObjectURL(entry.url)
})
</script>

<template>
  <StandardListLayout v-model:collapsed="sidebarCollapsed">
    <template #filters>
      <label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" allow-clear placeholder="款号、款式或客户" style="width:300px" @press-enter="load" /></label>
      <label class="zy-filter-field"><span>状态</span><a-select v-model:value="status" allow-clear placeholder="全部状态" style="width:132px">
        <a-select-option value="0">启用</a-select-option>
        <a-select-option value="1">停用</a-select-option>
      </a-select></label>
      <label class="zy-filter-field"><span>季节</span><a-select v-model:value="seasonFilter" :options="seasonFilterOptions" allow-clear show-search option-filter-prop="label" placeholder="全部季节" style="width:150px" /></label>
      <label class="zy-filter-field"><span>款式标签</span><a-select v-model:value="tagFilter" :options="groupedTagFilters" allow-clear show-search option-filter-prop="label" placeholder="全部款式标签" style="width:170px" /></label>
    </template>
    <template #sidebar>
      <div class="style-tree-head"><strong>款式分类</strong><a-button size="small" @click="resetStyleTree">重置</a-button></div>
      <div class="style-tree-tip">{{ defaultSchemeId ? '展示系统参数指定的分类类型' : '选择分类类型或分类，查看其下款式' }}</div>
      <div v-if="!defaultSchemeId && !categoryScopeUnavailable" class="style-tree-selector"><a-select :value="visibleSchemeId" :options="schemeOptions" allow-clear show-search option-filter-prop="label" placeholder="全部授权分类类型" style="width:100%" @change="changeVisibleScheme" /></div>
      <a-tree v-if="styleTree.length" block-node :tree-data="styleTree" :selected-keys="selectedTreeKeys" :expanded-keys="expandedKeys" @select="selectStyleTree" @expand="keys => expandedKeys=keys.map(String)" />
      <a-empty v-else :description="categoryScopeUnavailable ? '配置的分类范围不可用，请检查系统参数或分类权限' : restricted ? '暂无可见分类，请联系管理员' : '尚未建立分类'" style="margin-top:36px" />
    </template>
    <a-alert v-if="restricted" type="info" show-icon message="当前仅显示您有权查看的分类和款式；如未找到资料，请联系管理员核对人员分类权限。" style="margin-bottom:12px" />
    <FoundationTable page-toolbar storage-key="rd_style_list" :columns="columns" :rows="filtered" :loading="loading" :action-width="120">
      <template #actions>
        <a-tooltip :title="hasCategory ? '' : '请先建立可用分类，或联系管理员核对您的分类权限'">
          <span><a-button v-if="canAdd" type="primary" :disabled="loading || !hasCategory" @click="openAdd()"><template #icon><PlusOutlined /></template>新增</a-button></span>
        </a-tooltip>
        <a-button type="primary" :loading="loading" @click="load"><template #icon><SearchOutlined /></template>查询</a-button>
        <a-button @click="keyword=''; status=undefined; tagFilter=undefined; seasonFilter=undefined; resetStyleTree(); load()">重置</a-button>

        <a-typography-text v-if="restricted" type="secondary">仅展示您有权查看的款式分类及资料</a-typography-text>
      </template>
      <template #cell="{ column, row }">
        <a-switch v-if="column.key==='status'" :checked="row.status==='0'" :disabled="!canEdit" checked-children="启用" un-checked-children="停用" @change="confirmChange(row)" />
        <button v-else-if="column.key==='cover' && listCovers[row.id]" type="button" class="list-cover" title="预览款式主图" @click="previewListCover(row)"><img :src="listCovers[row.id].url" alt="款式主图" /></button>
        <span v-else-if="column.key==='cover'" class="list-cover-empty">暂无图片</span>
        <template v-else-if="column.key==='productionMode'">{{ row.productionMode === 'single' ? '单款' : '套装' }}</template>
        <template v-else-if="column.key==='setProduction'">{{ row.productionMode === 'single' ? '—' : row.productionMode === 'combined' ? '合并生产' : '分开生产' }}</template>
        <a-tooltip v-else-if="column.key==='categoryName'" :title="categoryPath(row.categoryId)"><span>{{ row.categoryName || '—' }}</span></a-tooltip>
        <a-popover v-else-if="column.key==='tags' && row.tags?.length" trigger="click">
          <template #content><div v-for="tag in row.tags" :key="tag.value">{{ tag.groupName }}：{{ tag.label }}</div></template>
          <a>{{ row.tags.slice(0, 2).map((tag: TagOption) => tag.label).join('、') }}{{ row.tags.length > 2 ? ' 等' + row.tags.length + '项' : '' }}</a>
        </a-popover>
        <template v-else-if="column.key==='tags'">—</template>
        <a-tooltip v-else-if="column.key==='colors' || column.key==='sizes'" :title="row.productionMode === 'single' ? specText(column.key === 'colors' ? row.colorIds : row.sizeIds, column.key === 'colors' ? specOptions.colors : specOptions.sizes) : ''">
          <span class="list-ellipsis">{{ row.productionMode === 'single' ? specText(column.key === 'colors' ? row.colorIds : row.sizeIds, column.key === 'colors' ? specOptions.colors : specOptions.sizes) : '见子款' }}</span>
        </a-tooltip>
        <a-popover v-else-if="column.key==='composition' && row.productionMode !== 'single'" trigger="click">
          <template #content>
            <div v-for="member in row.members" :key="member.childId" class="composition-detail">
              <strong>{{ member.roleName }} ×{{ member.quantity }}</strong>
              <template v-if="rows.find(item => item.id === member.childId)">
                <div>{{ rows.find(item => item.id === member.childId)?.styleCode }} · {{ rows.find(item => item.id === member.childId)?.name }}</div>
                <div>颜色：{{ specText(rows.find(item => item.id === member.childId)?.colorIds || [], specOptions.colors) }}</div>
                <div>尺码：{{ specText(rows.find(item => item.id === member.childId)?.sizeIds || [], specOptions.sizes) }}</div>
              </template>
            </div>
            <div v-if="row.membersRestricted">部分子款超出您的分类权限，请联系管理员。</div>
            <div v-if="!row.members">列表数据需要刷新，请点击查询。</div>
            <div v-else-if="!row.members.length && !row.membersRestricted">尚无子款</div>
          </template>
          <a>{{ !row.members ? '请刷新列表' : row.members.map((member: Member) => member.roleName + ' ×' + member.quantity).join('、') || (row.membersRestricted ? '部分不可见' : '暂无子款') }}{{ row.membersRestricted && row.members?.length ? '（部分不可见）' : '' }}</a>
        </a-popover>
        <template v-else-if="column.key==='composition'">—</template>
        <template v-else-if="column.key==='sameHanger'">{{ row.productionMode === 'combined' ? (row.sameHanger ? '是' : '否') : '—' }}</template>
        <a-tooltip v-else-if="column.key==='parentSets'" :title="parentSets(row)"><span class="list-ellipsis">{{ parentSets(row) }}</span></a-tooltip>
        <template v-else>{{ row[String(column.key)] || '—' }}</template>
      </template>
      <template #action="{ row }">
        <a-space :size="6">
          <a-button type="primary" size="small" @click="openEdit(row)">{{ canEdit ? '编辑' : '查看' }}</a-button>
          <a-button v-if="canRemove" danger size="small" @click="confirmChange(row, true)">删除</a-button>
        </a-space>
      </template>
    </FoundationTable>
  </StandardListLayout>

  <a-modal v-if="setDraft" wrap-class-name="style-editor-modal" :open="true" :z-index="990"
    :title="setDraft.editId ? '编辑套装' : '新增套装'" :width="1120" :footer="null" :closable="false"
    :keyboard="false" :mask-closable="false">
    <div class="parent-draft-view">
      <div class="parent-draft-nav"><strong>款式档案</strong><p>套装草稿</p><span>款式类型</span><span>基础资料</span><span>套装组成</span><span>说明与附件</span></div>
      <div class="parent-draft-content">
        <section class="form-section"><div class="section-heading"><div><strong>基础资料</strong><p>套装草稿保留在此</p></div></div>
          <div class="parent-draft-fields"><div><small>款号</small><p>{{ setDraft.form.styleCode || '尚未填写' }}</p></div><div><small>款式名称</small><p>{{ setDraft.form.name || '尚未填写' }}</p></div></div>
        </section>
        <section class="form-section"><div class="section-heading"><div><strong>套装组成</strong><p>返回后可继续编辑套装资料</p></div></div>
          <div v-for="(member, index) in setDraft.form.members" :key="index" class="parent-member">
            <strong>子款 {{ index + 1 }}</strong><span>{{ member.roleName || '待填写类别' }}</span><span>每套 {{ member.quantity || 0 }} 件</span>
          </div>
        </section>
      </div>
    </div>
  </a-modal>
  <a-modal wrap-class-name="style-editor-modal" :open="show" :title="setDraft ? (editId ? '编辑子款' : '新建子款') : editId ? (canEdit ? '编辑款式' : '查看款式') : '新增款式'" :width="1120" :mask-closable="false" :closable="!saving" @cancel="close">
    <div class="style-editor-layout">
      <aside class="style-editor-nav">
        <div class="style-nav-intro"><span>款式档案</span><strong>{{ setDraft ? '维护子款资料' : editId ? '维护款式档案' : '创建款式档案' }}</strong><p>从款式类型开始，按需完善规格和附件。</p></div>
        <nav aria-label="款式资料分区">
          <a href="#style-form-kind" @click.prevent="goToStyleSection('kind')"><b>01</b><span>款式类型<small>单款与套装</small></span></a>
          <a href="#style-form-basic" @click.prevent="goToStyleSection('basic')"><b>02</b><span>基础资料<small>款号、分类与标签</small></span></a>
          <a href="#style-form-spec" @click.prevent="goToStyleSection('spec')"><b>03</b><span>{{ styleKind === 'single' ? '可用规格' : '套装组成' }}<small>{{ styleKind === 'single' ? '颜色与尺码' : '子款及生产要求' }}</small></span></a>
          <a v-if="styleKind === 'set'" href="#style-form-set-spec" @click.prevent="goToStyleSection('set-spec')"><b>04</b><span>套装规格<small>配色与尺码搭配</small></span></a>
          <a href="#style-form-extra" @click.prevent="goToStyleSection('extra')"><b>{{ styleKind === 'set' ? '05' : '04' }}</b><span>说明与附件<small>款式图片、标识与文档</small></span></a>
        </nav>
        <div class="style-nav-footer">带 <span>*</span> 的项目为必填项</div>
      </aside>
      <div class="style-editor-content">
    <a-alert v-if="setDraft" show-icon type="info" class="editor-tip"
      :message="editId ? '保存会更新此子款的共用档案，随后返回套装草稿。' : '子款会先保存为独立档案并带回套装草稿，套装组成在套装保存后生效。'" />
    <a-alert v-if="compositionRestricted" show-icon type="warning" message="部分子款不在您的分类可见范围内，当前档案仅供查看。" />
    <a-form class="style-editor" layout="vertical" :disabled="saving || detailLoading || compositionRestricted || (!!editId && !canEdit)">
      <section id="style-form-kind" class="form-section">
      <div class="section-heading"><div><strong>款式类型</strong><p>先选择单款或套装，套装再选择生产方式。</p></div></div>
      <a-form-item class="style-kind-field" label="款式类型" required>
        <a-radio-group v-model:value="styleKind" :disabled="!!setDraft" class="style-kind-options">
          <a-radio-button value="single">
            <span class="kind-icon">单</span>
            <span class="kind-copy"><strong>单款</strong><small>独立的服装款式，可设置颜色和尺码</small></span>
            <span class="kind-check" aria-hidden="true">✓</span>
          </a-radio-button>
          <a-radio-button value="set">
            <span class="kind-icon">套</span>
            <span class="kind-copy"><strong>套装</strong><small>由上衣、下装等多个子款组成</small></span>
            <span class="kind-check" aria-hidden="true">✓</span>
          </a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="styleKind === 'set'" label="套装生产方式" required>
        <a-radio-group v-model:value="form.productionMode" class="production-options">
          <a-radio value="combined"><strong>合并生产</strong><span>子款一起裁剪、生产并下发吊挂。</span></a-radio>
          <a-radio value="separate"><strong>分开生产</strong><span>各子款分别裁剪、生产，可整套或单独下单。</span></a-radio>
        </a-radio-group>
      </a-form-item>
      </section>
      <section id="style-form-basic" class="form-section">
      <div class="section-heading"><div><strong>基础资料</strong><p>{{ setDraft ? '填写这个子款自己的分类、标签及其他资料。' : '填写当前款式的基本信息。' }}</p></div></div>
      <div class="style-fields">
        <a-form-item label="款号" required><a-input v-model:value="form.styleCode" :disabled="!!editId" :maxlength="60" placeholder="创建后固定" /></a-form-item>
        <a-form-item label="款式名称" required><a-input v-model:value="form.name" :maxlength="120" /></a-form-item>
        <a-form-item label="客户"><a-select v-model:value="form.customerId" :options="customerOptions" show-search option-filter-prop="label" allow-clear placeholder="自主研发可留空" /></a-form-item>
        <a-form-item label="分类类型">
          <a-select :value="defaultSchemeId ?? formSchemeId" :options="scopedSchemeOptions" :disabled="!!defaultSchemeId || categoryScopeUnavailable" allow-clear show-search option-filter-prop="label" placeholder="全部授权分类类型" @change="changeFormScheme" />
        </a-form-item>
        <a-form-item label="款式分类" required>
          <a-tree-select v-model:value="form.categoryId" :tree-data="formCategoryTree" show-search tree-node-filter-prop="title" placeholder="请选择有权使用的分类" />
        </a-form-item>
        <a-form-item label="季节"><a-select v-model:value="form.season" :options="seasonOptions" show-search allow-clear option-filter-prop="label" placeholder="选择季节" /></a-form-item>
      </div>
      <a-form-item class="style-tags-field" label="款式标签">
        <TagSelect :key="String(!!setDraft) + (editId || 'new')" v-model="form.tagIds" :module-key="tagFeature" module-name="款式档案" :selected-options="selectedTagOptions" />
      </a-form-item>
      </section>
      <a-alert v-if="changingStyleKind" class="editor-tip" show-icon type="warning"
        :message="form.productionMode === 'single' ? '保存后将解除原套装的子款关联，子款档案会保留。' : '保存后将清除原单款的颜色尺码设置。'" />
      <template v-if="form.productionMode === 'single'">
        <section id="style-form-spec" class="form-section"><div class="section-heading"><div><strong>可用规格</strong><p>可先保存款式，之后再补充颜色和尺码。</p></div></div>
        <div class="spec-grid">
          <a-form-item class="spec-panel" label="可用颜色">
            <div class="selection-field"><a-button @click="colorPickerOpen = true">选择颜色</a-button><span v-if="!form.colorIds.length">尚未选择</span><a-tag v-for="id in form.colorIds" :key="id" closable @close.prevent="removeSpec('color', id)">{{ chosenLabels([id], specOptions.colors)[0] }}</a-tag></div>
          </a-form-item>
          <a-form-item class="spec-panel" label="可用尺码">
            <div class="selection-field">
              <a-button @click="sizePickerOpen = true">选择尺码</a-button>
              <a-select :value="undefined" :options="availableSizeGroups" show-search option-filter-prop="label"
                aria-label="按尺码组带入" placeholder="按尺码组带入" not-found-content="暂无含启用尺码的尺码组"
                class="size-group-select" @change="applySizeGroup" />
            </div>
            <div class="selection-field">
              <span v-if="!form.sizeIds.length">尚未选择</span>
              <a-tag v-for="id in form.sizeIds" :key="id" closable @close.prevent="removeSpec('size', id)">{{ chosenLabels([id], specOptions.sizes)[0] }}</a-tag>
            </div>
          </a-form-item>
        </div>
        <a-collapse v-if="form.colorIds.length && form.sizeIds.length" class="spec-pairs">
          <a-collapse-panel key="combinations" header="有效颜色尺码组合">
            <a-typography-paragraph type="secondary">默认每种颜色可用全部已选尺码，取消勾选即可排除不存在的组合。</a-typography-paragraph>
            <a-form-item v-for="colorId in form.colorIds" :key="colorId" :label="specOptions.colors.find(item => item.value === colorId)?.label">
              <a-checkbox-group :value="availableSizes(colorId)"
                :options="specOptions.sizes.filter(item => form.sizeIds.includes(item.value)).map(({ label, value }) => ({ label, value }))"
                @change="setAvailable(colorId, $event.map(String))" />
            </a-form-item>
          </a-collapse-panel>
        </a-collapse>
        </section>
      </template>
      <template v-else>
        <section id="style-form-spec" class="form-section">
        <div class="set-section-header">
          <div class="section-heading"><div><strong>套装组成</strong><p>维护套装包含的子款及每套件数。</p></div></div>
          <div v-if="!compositionRestricted && (!editId || canEdit)" class="member-toolbar">
          <a-button :disabled="form.members.length >= 100" @click="form.members.push({ roleName: '', quantity: 1 })">添加已有子款</a-button>
          <a-button v-if="canAdd" type="primary" ghost :disabled="form.members.length >= 100" @click="openMemberEditor({ roleName: '', quantity: 1 }, false, true)">新建子款资料</a-button>
          </div>
        </div>
        <a-form-item v-if="form.productionMode === 'combined'" class="hanger-setting">
          <div class="hanger-setting-content">
            <div><strong>必须同架生产</strong><p>需要控制色差时开启，记录所有子款使用同一衣架生产的要求。</p></div>
            <a-switch v-model:checked="form.sameHanger" aria-label="必须同架生产" />
          </div>
        </a-form-item>
        <div v-if="!form.members.length" class="member-empty">
          <strong>还没有添加子款</strong><span>添加已有单款，或新建上衣、下装、帽子等子款。</span>
        </div>
        <div v-for="(member, index) in form.members" :key="index" class="member-card">
          <div class="member-heading"><strong>子款 {{ index + 1 }}</strong><a-space><a-button :disabled="index === 0" size="small" @click="moveMember(index, -1)">上移</a-button><a-button :disabled="index === form.members.length - 1" size="small" @click="moveMember(index, 1)">下移</a-button><a-button danger size="small" @click="form.members.splice(index, 1)">移除</a-button></a-space></div>
          <div class="member-row">
          <a-form-item :label="'子款 ' + (index + 1)" required>
            <a-select v-model:value="member.childId" :options="memberOptions(member)" show-search option-filter-prop="label" placeholder="选择已有单款" @change="value => selectMember(member, value)" />
          </a-form-item>
          <a-form-item label="子款类别" required><a-input v-model:value="member.roleName" :maxlength="60" placeholder="选择子款后带出，可修改" /></a-form-item>
          <a-form-item label="每套件数" required><a-input-number v-model:value="member.quantity" :min="1" :max="2147483647" :precision="0" /></a-form-item>
          </div>
          <div v-if="!compositionRestricted && canEdit && member.childId" class="member-actions"><a-button @click="openMemberEditor(member, true)">编辑子款资料</a-button><span>可维护子款的分类、标签、颜色、尺码及其他基础资料</span></div>
        </div>
        </section>
        <section id="style-form-set-spec" class="form-section">
          <div class="set-section-header">
            <div class="section-heading"><div><strong>套装规格</strong><p>配色名称和标称尺码用于识别整套规格，下方为各子款实际使用的颜色和尺码。</p></div></div>
            <a-button v-if="!compositionRestricted && (!editId || canEdit)"
              :disabled="!selectedChildIds.length || selectedChildIds.length !== form.members.length || form.setSpecs.length >= 100"
              @click="addSetSpec()">新增套装规格</a-button>
          </div>
          <a-alert v-if="compositionRestricted" type="warning" message="部分子款超出分类权限，无法查看完整套装规格。" />
          <a-alert v-if="childrenWithoutPairs.length" type="warning" show-icon
            :message="'请先为以下子款补齐可用颜色尺码：' + childrenWithoutPairs.map(childSpecName).join('、')" />
          <div v-if="!compositionRestricted && !form.setSpecs.length" class="member-empty">尚未设置套装规格</div>
          <a-collapse v-if="form.setSpecs.length" v-model:activeKey="expandedSetSpecs">
            <a-collapse-panel v-for="(spec, index) in form.setSpecs" :key="spec.id || spec.draftKey">
              <template #header>
                <strong>{{ [spec.colorName, spec.sizeName].filter(Boolean).join(' / ') || '新套装规格' }}</strong>
                <div>{{ setSpecSummary(spec) }}</div>
              </template>
              <div class="member-row">
                <a-form-item label="配色名称" required><a-input v-model:value="spec.colorName" :maxlength="120" placeholder="例如黑白配" /></a-form-item>
                <a-form-item label="套装标称尺码" required><a-input v-model:value="spec.sizeName" :maxlength="120" placeholder="例如 S（整套尺码）" /></a-form-item>
                <a-form-item label="备注"><a-input v-model:value="spec.remark" :maxlength="500" /></a-form-item>
              </div>
              <a-alert v-if="setSpecNeedsAlignment(spec)" type="warning" message="套装组成已变化，请同步子款后补齐配套。">
                <template #action><a-button size="small" @click="alignSetSpec(spec)">同步子款</a-button></template>
              </a-alert>
              <div v-for="item in spec.items" :key="item.childId" class="member-row">
                <a-form-item label="子款">
                  <span>{{ childSpecName(item.childId) }} · 每套 {{ form.members.find(member => member.childId === item.childId)?.quantity || 0 }} 件</span>
                </a-form-item>
                <a-form-item label="子款颜色" required>
                  <a-select v-model:value="item.colorId" :options="childSpecOptions(item, 'color')" :loading="loadingChildSpecs"
                    show-search option-filter-prop="label" @change="item.sizeId = undefined" />
                </a-form-item>
                <a-form-item label="子款尺码" required>
                  <a-select v-model:value="item.sizeId" :options="childSpecOptions(item, 'size')" :loading="loadingChildSpecs"
                    show-search option-filter-prop="label" :disabled="!item.colorId" />
                </a-form-item>
              </div>
              <a-space>
                <a-button :disabled="form.setSpecs.length >= 100" @click="addSetSpec(spec)">复制配套</a-button>
                <a-button danger @click="form.setSpecs.splice(index, 1)">移除套装规格</a-button>
              </a-space>
            </a-collapse-panel>
          </a-collapse>
        </section>
      </template>
      <section id="style-form-extra" class="form-section"><div class="section-heading"><div><strong>说明与附件</strong><p>添加当前款式的图片和补充说明。</p></div></div>
      <div class="gallery">
        <div class="gallery-heading"><div><span class="attachment-kicker">款式影像</span><br /><strong>款式图片</strong><p>上传款式的正面、背面和细节图，选择其中一张作为主图。</p></div>
          <label v-if="!compositionRestricted && (!editId || canEdit)" class="attachment-upload"><input type="file" multiple accept=".png,.jpg,.jpeg" @change="chooseAttachments($event, 'style_image')" />添加图片</label>
        </div>
        <div class="gallery-list">
          <div v-for="(item, index) in form.attachments" v-show="item.category === 'style_image'" :key="index" class="gallery-item">
            <button type="button" class="gallery-image" @click="previewAttachment(item)"><img v-if="item.thumbnail" :src="item.thumbnail" :alt="item.fileName" /><span v-else>点击预览</span></button>
            <div class="gallery-caption" :title="item.fileName">{{ item.fileName }}</div>
            <div class="gallery-actions"><a-tag v-if="item.isCover" color="blue">主图</a-tag><a v-else-if="!compositionRestricted && (!editId || canEdit)" @click="setCover(item)">设为主图</a><a v-if="!compositionRestricted && (!editId || canEdit)" class="gallery-remove" @click="removeGalleryItem(index)">移除</a></div>
          </div>
          <span v-if="!form.attachments.some(item => item.category === 'style_image')" class="attachment-empty">尚未添加款式图片</span>
        </div>
      </div>
      <div class="attachment-title"><strong>标识与文档</strong><span>按类别管理相关文件</span></div>
      <div class="attachment-groups">
        <div v-for="category in attachmentCategories" :key="category.value" class="attachment-group">
          <div class="attachment-heading"><strong>{{ category.label }}</strong>
            <label v-if="!compositionRestricted && (!editId || canEdit)" class="attachment-upload">
              <input type="file" multiple :accept="category.value === 'document' ? '.png,.jpg,.jpeg,.pdf,.docx,.xlsx,.pptx' : '.png,.jpg,.jpeg'" @change="chooseAttachments($event, category.value)" />
              添加文件
            </label>
          </div>
          <div v-for="(item, index) in form.attachments" v-show="item.category === category.value" :key="index" class="attachment-item">
            <img v-if="item.thumbnail" :src="item.thumbnail" class="attachment-thumbnail" alt="点击预览" @click="previewAttachment(item)" />
            <a @click="previewAttachment(item)">{{ item.fileName }}</a>
            <a-button v-if="!compositionRestricted && (!editId || canEdit)" type="link" danger size="small" @click="form.attachments.splice(index, 1)">移除</a-button>
          </div>
          <span v-if="!form.attachments.some(item => item.category === category.value)" class="attachment-empty">尚未添加</span>
        </div>
      </div>
      <p class="attachment-hint">图片支持 PNG、JPG；文档支持 PDF、DOCX、XLSX、PPTX，单个文件不超过 10 MB。</p>
      <a-form-item label="款式说明"><a-textarea v-model:value="form.description" :maxlength="1000" :rows="2" /></a-form-item>
      <a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" /></a-form-item>
      </section>
    </a-form>
      </div>
    </div>
    <template #footer>
      <a-button :disabled="saving" @click="close">{{ setDraft ? '不保存并返回套装' : editId && !canEdit ? '关闭' : '取消' }}</a-button>
      <a-button v-if="!compositionRestricted && (!editId || canEdit)" type="primary" :loading="saving" @click="save()">{{ setDraft ? '保存子款并返回套装' : '保存' }}</a-button>
      <a-button v-if="!editId && canAdd && !setDraft" type="primary" ghost :loading="saving" @click="save(true)">保存并新增</a-button>
    </template>
  </a-modal>
  <StandardPicker v-model:open="colorPickerOpen" v-model="form.colorIds" title="选择可用颜色" :tree="[]" :items="colorPickerItems" hide-tree multiple allow-empty search-placeholder="搜索颜色名称或编码" />
  <StandardPicker v-model:open="sizePickerOpen" v-model="form.sizeIds" title="选择可用尺码" :tree="[]" :items="sizePickerItems" hide-tree multiple allow-empty search-placeholder="搜索尺码名称或编码">
    <template #filters="{ addSelected }"><a-select :value="undefined" :options="availableSizeGroups" show-search option-filter-prop="label" placeholder="选择尺码组，带入组内启用尺码" not-found-content="暂无含启用尺码的尺码组" style="width:100%" @change="value => addSelected(availableSizeGroups.find(group => group.value === value)?.sizeIds || [])" /></template>
  </StandardPicker>
  <a-modal :open="!!attachmentPreview" title="图片预览" :footer="null" @cancel="closeAttachmentPreview">
    <img :src="attachmentPreview" alt="款式附件预览" style="display:block;max-width:100%;max-height:70vh;margin:auto" />
  </a-modal>
</template>

<style scoped>
.style-tree-head { display:flex; align-items:center; justify-content:space-between; padding:12px 14px; border-bottom:1px solid #f0f2f5; }
.style-tree-tip { padding:10px 14px 4px; color:#86909c; font-size:12px; }
.style-tree-selector { padding: 4px 12px 8px; }
.list-cover { width: 48px; height: 48px; padding: 0; border: 1px solid #e5eaf2; border-radius: 6px; background: #f8faff; overflow: hidden; cursor: pointer; }
.list-cover img { width: 100%; height: 100%; object-fit: cover; }
.list-cover-empty { color: #98a5b5; font-size: 12px; }
.list-ellipsis { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.composition-detail { padding: 5px 0; border-bottom: 1px solid #eef1f5; }
:deep(.std-sidebar .ant-tree) { overflow:auto; padding:0 8px 12px; }

.style-editor-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); height: min(720px, calc(100dvh - 150px)); min-height: 0; background: #f6f8fb; }
.parent-draft-view { display: grid; grid-template-columns: 220px minmax(0, 1fr); height: min(720px, calc(100dvh - 150px)); min-height: 0; background: #f6f8fb; }
.parent-draft-nav { display: flex; flex-direction: column; gap: 20px; padding: 28px 24px; background: #fff; color: #405473; }
.parent-draft-nav p, .parent-draft-fields small { color: #8998ad; }
.parent-draft-content { overflow: auto; padding: 20px 24px; }
.parent-draft-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.parent-member { display: flex; gap: 20px; padding: 14px; margin-bottom: 10px; background: #f8faff; border: 1px solid #e5eaf2; border-radius: 8px; }
.style-editor-nav { display: flex; flex-direction: column; padding: 24px 14px 20px; background: #fff; border-right: 1px solid #edf0f5; }
.style-nav-intro { padding: 0 10px 18px; border-bottom: 1px solid #edf0f5; }
.style-nav-intro > span { display: block; color: #527bc0; font-size: 12px; font-weight: 600; }
.style-nav-intro strong { display: block; margin-top: 10px; color: #263854; font-size: 16px; }
.style-nav-intro p { margin: 10px 0 0; color: #8998ad; font-size: 12px; line-height: 1.6; }
.style-editor-nav nav { display: grid; gap: 4px; margin-top: 16px; }
.style-editor-nav nav a { display: flex; align-items: center; gap: 12px; padding: 10px; border-radius: 8px; color: #344660; font-weight: 600; }
.style-editor-nav nav a:hover, .style-editor-nav nav a:focus-visible { background: #eef4ff; }
.style-editor-nav nav b { display: grid; place-items: center; width: 32px; height: 32px; flex: none; border-radius: 9px; background: #edf3ff; color: #3970ce; font-size: 12px; }
.style-editor-nav nav small { display: block; margin-top: 2px; color: #94a1b4; font-size: 12px; font-weight: 400; }
.style-nav-footer { margin: auto 10px 0; color: #91a0b4; font-size: 12px; }
.style-nav-footer span { color: #e45c5c; }
.style-editor-content { min-width: 0; overflow-y: auto; overscroll-behavior: contain; padding: 20px 24px 8px; scroll-behavior: smooth; }
.style-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 16px; }
.style-editor :deep(.ant-form-item) { margin-bottom: 16px; }
.style-tags-field { margin-bottom: 12px; }
.style-editor { counter-reset: section; }
.style-kind-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; width: 100%; }
.style-kind-options :deep(.ant-radio-button-wrapper) { display: flex; align-items: center; gap: 12px; width: 100%; height: auto; min-height: 66px; padding: 10px 14px; border: 1px solid #dfe6f0; border-radius: 10px; box-shadow: none; background: #fff; white-space: normal; }
.style-kind-options :deep(.ant-radio-button-wrapper > span:last-child) { display: flex; align-items: center; gap: 12px; width: 100%; min-width: 0; }
.style-kind-options :deep(.ant-radio-button-wrapper:not(:first-child)::before) { display: none; }
.style-kind-options :deep(.ant-radio-button-wrapper-checked) { border-color: #3578e5; background: #f3f7ff; box-shadow: 0 0 0 1px #3578e5; }
.style-kind-options :deep(.ant-radio-button-wrapper-disabled) { opacity: .65; cursor: not-allowed; }
.kind-icon { display: grid; place-items: center; width: 44px; height: 44px; flex: none; border-radius: 12px; background: #eaf2ff; color: #3971c5; font-weight: 700; font-size: 17px; }
.kind-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4px; line-height: 1.4; }
.kind-copy strong { color: #273b59; font-size: 15px; }
.kind-copy small { color: #788aa2; font-size: 12px; }
.kind-check { display: grid; place-items: center; width: 22px; height: 22px; flex: none; border: 1px solid #cfd9e7; border-radius: 50%; color: transparent; }
.style-kind-options :deep(.ant-radio-button-wrapper-checked) .kind-check { color: #fff; background: #3578e5; border-color: #3578e5; }
.spec-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.spec-panel { margin: 0; padding: 16px; border: 1px solid #e4eaf3; border-radius: 10px; background: #f8faff; }
.spec-panel :deep(.ant-form-item-label > label) { font-weight: 600; color: #283a54; }
.spec-panel .selection-field { align-content: flex-start; }
.style-editor :deep(.ant-form-item-label > label) { font-weight: 500; color: #34445b; }
.style-editor :deep(.ant-input), .style-editor :deep(.ant-select-selector) { border-radius: 6px !important; }

.editor-tip, .spec-pairs { margin-bottom: 12px; }
.form-section { counter-increment: section; padding: 18px 20px 6px; margin-bottom: 12px; border: 1px solid #e6ebf2; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(30, 55, 90, .035); }
.section-heading { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 14px; } .section-heading::before { content: counter(section, decimal-leading-zero); display: grid; place-items: center; width: 32px; height: 32px; flex: none; color: #3569bf; background: #edf4ff; border-radius: 9px; font-weight: 700; font-size: 12px; } .section-heading strong { font-size: 16px; color: #20304a; } .section-heading p { margin: 3px 0 0; color: #7d8ba0; font-size: 12px; }
.production-options { display: flex; gap: 12px; flex-wrap: wrap; } .production-options :deep(.ant-radio-wrapper) { display: flex; align-items: flex-start; padding: 12px; border: 1px solid #e5eaf2; border-radius: 8px; flex: 1; min-width: 250px; } .production-options span span { display: block; color: #86909c; font-size: 12px; margin-top: 4px; }
.member-card { padding: 14px; margin-bottom: 12px; border: 1px solid #e5eaf2; border-radius: 8px; background: #fafcff; } .member-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.set-section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.member-toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.hanger-setting :deep(.ant-form-item-control-input-content) { min-width: 0; }
.hanger-setting-content { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px; border: 1px solid #e5eaf2; border-radius: 8px; background: #f8faff; }
.hanger-setting-content strong { color: #273b59; font-weight: 600; }
.hanger-setting-content p { margin: 3px 0 0; color: #7d8ba0; font-size: 12px; line-height: 1.5; }
.hanger-setting-content :deep(.ant-switch) { flex: none; }
.member-empty { display: flex; flex-direction: column; align-items: center; gap: 5px; margin-bottom: 14px; padding: 20px 14px; border: 1px dashed #dce5f0; border-radius: 8px; color: #8191a7; text-align: center; font-size: 12px; }
.member-empty strong { color: #526780; font-size: 13px; }
.member-row { display: grid; grid-template-columns: minmax(150px, 2fr) minmax(110px, 1fr) 120px; gap: 12px; }
.member-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; } .member-actions span { color: #86909c; font-size: 12px; }
.gallery { margin: 0 0 18px; padding: 14px; border: 1px solid #e5eaf2; border-radius: 8px; background: #fafcff; }
.gallery-heading { display: flex; justify-content: space-between; gap: 12px; align-items: start; } .gallery-heading p { margin: 4px 0 12px; color: #86909c; font-size: 12px; }
.gallery-list { display: flex; flex-wrap: wrap; gap: 12px; } .gallery-item { width: 130px; min-width: 0; } .gallery-image { width: 130px; height: 100px; border: 1px solid #e5eaf2; border-radius: 6px; background: #fff; color: #86909c; cursor: pointer; padding: 0; overflow: hidden; }
.gallery-image img { width: 100%; height: 100%; object-fit: contain; } .gallery-caption { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: 5px 0; font-size: 12px; } .gallery-actions { display: flex; justify-content: space-between; align-items: center; font-size: 12px; } .gallery-remove { color: #d9363e; }
.attachment-title { display: flex; align-items: baseline; gap: 10px; margin: 4px 0 12px; color: #273852; } .attachment-title span, .attachment-kicker { color: #8696ad; font-size: 12px; } .attachment-kicker { letter-spacing: .08em; }
.attachment-groups { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; margin-bottom: 8px; }
.attachment-group { padding: 12px; border: 1px solid #e5eaf2; border-radius: 8px; min-width: 0; }
.attachment-heading, .attachment-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.attachment-item a { overflow-wrap: anywhere; } .attachment-upload { color: #1677ff; cursor: pointer; } .attachment-upload input { display: none; }
.attachment-empty, .attachment-hint { color: #86909c; font-size: 12px; } .attachment-hint { margin-bottom: 16px; }
.selection-field { min-height: 36px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; } .selection-field > span { color: #86909c; }
.size-group-select { width: 160px; max-width: 100%; }
.attachment-thumbnail { width: 48px; height: 48px; object-fit: cover; border-radius: 6px; cursor: pointer; flex: none; } .preview-image { display: block; max-width: 100%; max-height: 70vh; margin: auto; }
.cover { cursor: pointer; width: 110px; height: 92px; object-fit: contain; background: #f5f7fa; border-radius: 6px; }
@media (max-width: 1000px) { .style-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .style-editor-layout { grid-template-columns: 1fr; } .style-editor-nav { display: none; } .style-editor-content { padding: 14px; } }
@media (max-width: 760px) { .parent-draft-view { grid-template-columns: 1fr; } .parent-draft-nav { display: none; } .parent-draft-content { padding: 14px; } }
@media (max-width: 620px) { .style-fields, .spec-grid, .member-row, .style-kind-options { grid-template-columns: 1fr; } }
</style>

<style>
.style-editor-modal { display: flex; align-items: center; justify-content: center; padding: 12px; }
.style-editor-modal .ant-modal { top: auto; margin: 0; padding-bottom: 0; max-width: 100%; }
.style-editor-modal .ant-modal-content { display: flex; flex-direction: column; max-height: calc(100dvh - 24px); overflow: hidden; border-radius: 14px; }
.style-editor-modal .ant-modal-header { padding: 20px 24px 16px; margin: -20px -24px 0; border-bottom: 1px solid #edf0f5; }
.style-editor-modal .ant-modal-title { color: #20304a; font-size: 18px; font-weight: 650; }
.style-editor-modal .ant-modal-body { display: flex; flex-direction: column; flex: 1 1 auto; min-height: 0; overflow: hidden; margin: 0 -24px; padding: 0; background: #f4f7fb; }
.style-editor-modal .style-editor-layout { flex: 1 1 auto; max-height: 100%; }
.style-editor-modal .ant-modal-footer { flex: none; padding-top: 12px; border-top: 1px solid #edf0f5; }
</style>
