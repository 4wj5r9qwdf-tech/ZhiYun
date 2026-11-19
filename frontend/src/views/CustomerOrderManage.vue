<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import DatePicker from 'ant-design-vue/es/date-picker'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import EditorSectionNav from '../components/EditorSectionNav.vue'
import { can } from '../utils/perms'
import { useRoute } from 'vue-router'

const base = '/order/customer-orders'
const route = useRoute()
const permissions = (action: string) => can('order:customer-order:' + action)
const statuses: Record<string,string> = { draft:'草稿', submitted:'待审核', approved:'已审核', cancelled:'已取消' }
const supplyOptions = [{value:'customer',label:'客供'}, {value:'purchase',label:'自采'}, {value:'mixed',label:'客供＋自采',disabled:true}]
const orderSupplyOptions = [
  {value:'customer',label:'默认客供'},
  {value:'purchase',label:'默认自采'},
  {value:'mixed',label:'客供＋自采'},
]
const columns = [
  {key:'orderNo',title:'订单号'}, {key:'customerName',title:'客户'}, {key:'poNames',title:'客户 PO'},
  {key:'styles',title:'款式'}, {key:'supplySummary',title:'订单默认供料'}, {key:'quantity',title:'订购数量'}, {key:'salesName',title:'业务员'},
  {key:'followerName',title:'跟单员'}, {key:'date',title:'订单日期'}, {key:'due',title:'最近交期'}, {key:'processingAmount',title:'加工金额'}, {key:'demandState',title:'算料状态'}, {key:'status',title:'状态'},
  {key:'updatedAt',title:'修改时间',defaultVisible:false},
]
const rows = ref<any[]>([])
const options = ref<any>({})
const form = ref<any>()
const reverseApprovalOpen = ref(false)
const reverseApprovalReason = ref('')
const plans = ref<any[]>([])
const demandSaved = ref(false)
const demandView = ref<'summary'|'detail'>('summary')
const demandPoFilter = ref<string>()
// PO 选项来自当前试算快照，避免订单未保存修改造成筛选范围与结果不一致。
const demandPoOptions = computed(() => [...new Set<string>(plans.value.map((plan:any)=>String(plan.po || '')).filter(Boolean))].map(value=>({value,label:value})))
const filteredPlans = computed(() => demandPoFilter.value ? plans.value.filter((plan:any)=>String(plan.po || '')===demandPoFilter.value) : plans.value)
watch(demandPoOptions, items=>{ if (demandPoFilter.value && !items.some(item=>item.value===demandPoFilter.value)) demandPoFilter.value=undefined })
const keyword = ref('')
const status = ref<string>()
const active = ref('0')
const busy = ref(false)
const dirty = ref(false)
const reason = ref('')
const batchUnitPrice = ref<string>()
const batchQuantity = ref<number>()
const batchSelection = ref<string[]>([])
const lineColorFilter = ref<string>()
const lineSizeFilter = ref<string>()
// 金额按分进行整数计算，避免浮点小数误差；空单价表示待填写。
function amountCents(line:any): bigint | null {
  const price = String(line.unitPrice ?? '').trim()
  if (!/^\d+(\.\d{1,2})?$/.test(price) || !Number.isInteger(Number(line.quantity)) || Number(line.quantity)<1) return null
  const [whole, fraction=''] = price.split('.')
  return (BigInt(whole) * 100n + BigInt(fraction.padEnd(2,'0'))) * BigInt(line.quantity)
}
/** 将整数分格式化为人民币两位小数，避免浮点误差。 / Format integer cents as two-decimal currency without floating-point arithmetic. */
function formatCents(value:bigint) { return `${value / 100n}.${String(value % 100n).padStart(2,'0')}` }
const orderAmount = computed(() => {
  const lines = form.value?.pos?.flatMap((p:any) => p.lines || []) || []
  const amounts = lines.map(amountCents)
  return { value:formatCents(amounts.reduce((sum:bigint, amount:bigint|null) => sum + (amount ?? 0n), 0n)), missing:amounts.filter((v:bigint|null)=>v===null).length, count:lines.length }
})
/** 只对勾选的颜色尺码批量写入加工单价。 / Apply a batch price only to the checked color-size lines. */
function applyBatchPrice() {
  if (batchUnitPrice.value == null || !/^\d+(\.\d{1,2})?$/.test(String(batchUnitPrice.value))) { message.warning('请填写有效加工单价'); return }
  const targetPo=po.value, keys=new Set(batchSelection.value), price=String(batchUnitPrice.value)
  const targets=(targetPo?.lines || []).filter((line:any)=>keys.has(line.key))
  if (!targets.length) { message.warning('请先勾选需要设置的规格'); return }
  Modal.confirm({ title:'批量设置加工单价', content:`将已勾选的 ${targets.length} 个规格的单价设置为 ${price}，原单价会被覆盖。`, okText:'确认设置', cancelText:'取消', onOk:()=>{
    for (const line of targetPo.lines) if (keys.has(line.key)) line.unitPrice=price
    touch()
  } })
}
/** 仅修改勾选规格；已分配的交付数量不得超过新订购量。 */
function applyBatchQuantity() {
  const quantity = batchQuantity.value
  if (quantity == null || !Number.isInteger(quantity) || quantity < 1 || quantity > 10000000) { message.warning('请输入 1 至 10000000 的整数数量'); return }
  const targetPo = po.value
  const keys=new Set(batchSelection.value)
  const targets=(targetPo?.lines || []).filter((line:any)=>keys.has(line.key))
  if (!targets.length) { message.warning('请先勾选需要设置的规格'); return }
  const exceeds = targets.filter((line:any) => (targetPo.deliveries || []).reduce((sum:number, batch:any) => sum + Number(batch.allocations?.[line.key] || 0), 0) > quantity)
  if (exceeds.length) { message.warning(`有 ${exceeds.length} 个规格的已分配交付数量超过 ${quantity}，请先调整交付批次`); return }
  Modal.confirm({ title:'批量设置订购数量', content:`将已勾选的 ${targets.length} 个规格的订购数量设置为 ${quantity}，原数量会被覆盖。`, okText:'确认设置', cancelText:'取消', onOk:()=>{
    // 再次校验弹窗打开期间的批次分配，避免覆盖后出现超量。
    if ((targetPo.lines || []).some((line:any) => keys.has(line.key) && (targetPo.deliveries || []).reduce((sum:number, batch:any) => sum + Number(batch.allocations?.[line.key] || 0),0) > quantity)) { message.warning('交付批次数量已变化，请先调整分配'); return }
    targetPo.lines.forEach((line:any) => { if (keys.has(line.key)) line.quantity=quantity }); touch()
  } })
}
function defaultOfflineDate(due:string) {
  if (!due) return undefined
  const date = new Date(`${due}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return undefined
  date.setUTCDate(date.getUTCDate() - (Number(options.value.offlineLeadDays) || 0))
  return date.toISOString().slice(0,10)
}
/** 将颜色或尺码主键解析为展示名称，并区分未设置与档案失效。 / Resolve a color or size ID and distinguish an unset value from a stale reference. */
function referenceName(source:any[] | undefined, id:any, emptyLabel:string, missingLabel:string) {
  if (id == null || id === '') return emptyLabel
  return source?.find(item=>String(item.value)===String(id))?.label || missingLabel
}
/** 将订单规格转换为可读摘要；单款明确展示色码，套装明确展示套装配色配码。 / Format order specifications with explicit single-style color-size or set color-size labels. */
function orderLineSpecification(line:any) {
  if (line.setSpecId) {
    const setName=[line.setSpecColorName,line.setSpecSizeName].filter(Boolean).join(' / ')
    return setName ? `套装配色/配码：${setName}` : '套装规格名称待补充'
  }
  const colors=options.value.specs?.colors
  const sizes=options.value.specs?.sizes
  const color=line.colorName || referenceName(colors,line.colorId,'无颜色','颜色资料已失效')
  const size=line.sizeName || referenceName(sizes,line.sizeId,'无尺码','尺码资料已失效')
  return `颜色：${color}　尺码：${size}`
}
/** 按款式及 PO 分组订单行，款式卡片内不再逐行重复 PO 标识。 / Group order lines by style and PO so the style popover does not repeat PO labels on every row. */
function orderStyleDetails(pos:any[]) {
  const groups = new Map<string, any>()
  for (const po of pos) for (const line of po.lines || []) {
    const key = String(line.styleId || line.styleCode || line.styleName || 'unknown')
    let group=groups.get(key)
    if (!group) {
      group={key,name:line.styleName || line.styleCode || '未命名款式',productionMode:line.productionMode,poGroups:[],poMap:new Map<string,any>()}
      groups.set(key,group)
    }
    const poKey=String(po.number || '未命名 PO')
    let poGroup=group.poMap.get(poKey)
    if (!poGroup) {
      poGroup={number:po.number,lines:[]}
      group.poMap.set(poKey,poGroup)
      group.poGroups.push(poGroup)
    }
    poGroup.lines.push({...line,specification:orderLineSpecification(line)})
  }
  return [...groups.values()].map(({poMap,...group})=>({...group,poGroups:group.poGroups.map((po:any)=>({...po,
    quantity:po.lines.reduce((sum:number,line:any)=>sum+Number(line.quantity||0),0),
    unit:po.lines[0]?.productionMode==='single'?'件':'套',
    amount:orderLinesAmount(po.lines),
  }))}))
}
/** 将 PO 明细压缩为款式级汇总，PO 卡片只展示 PO 视角的总量，不重复展开颜色尺码。 / Summarize PO lines by style so the PO popover shows totals without duplicating color-size details. */
function orderPoStyleSummaries(po:any) {
  const styles=new Map<string,any>()
  for (const line of po.lines || []) {
    const key=String(line.styleId || line.styleCode || line.styleName || 'unknown')
    let group=styles.get(key)
    if (!group) {
      group={key,name:line.styleName || line.styleCode || '未命名款式',productionMode:line.productionMode,lines:[]}
      styles.set(key,group)
    }
    group.lines.push(line)
  }
  return [...styles.values()].map(group=>({
    key:group.key,
    name:group.name,
    productionMode:group.productionMode,
    specCount:group.lines.length,
    quantity:group.lines.reduce((sum:number,line:any)=>sum+Number(line.quantity||0),0),
    unit:group.productionMode==='single'?'件':'套',
    amount:orderLinesAmount(group.lines),
  }))
}
/** 计算一组订单规格的加工金额；存在未填写单价时不显示不完整的合计。 / Sum processing amounts only when every line has a valid unit price. */
function orderLinesAmount(lines:any[]) {
  const values=lines.map(amountCents)
  return values.length && values.every((value:bigint|null)=>value!==null)
    ? formatCents(values.reduce((sum:bigint,value:bigint|null)=>sum+(value ?? 0n),0n)) : undefined
}
/** 为 PO 悬浮卡片标明供料来源，区分 PO 单独设置与继承订单默认。 / Label whether a PO sourcing mode is overridden or inherited. */
function poSupplySource(po:any) { return po.supply ? 'PO 单独设置' : '继承订单默认' }
function orderUnit(line:any) { return line.productionMode==='single'?'件':'套' }
/** 区分单款、合并生产套装与分开生产套装。 / Distinguish single styles and the two set production modes. */
function productionModeLabel(mode:string) {
  return mode==='single'?'单款':mode==='combined'?'合并生产套装':mode==='separate'?'分开生产套装':'套装'
}
function orderLinePriceSummary(line:any) {
  const amount=amountCents(line)
  return line.unitPrice == null ? '' : `单价 ${line.unitPrice}/${orderUnit(line)}${amount===null?'':` · 金额 ${formatCents(amount)}`}`
}
/** 更新指定 PO 交期，并按参数重算默认下线日期。 / Update one PO due date and recalculate its suggested offline date. */
function changeDue(p:any, value:string) {
  if (!p.offlineDate || p.offlineDate === p._autoOfflineDate) { p.offlineDate=defaultOfflineDate(value); p._autoOfflineDate=p.offlineDate }
  touch()
}
const styleId = ref<string>()
const config = ref<any>()
const selected = ref<string[]>([])
const stylePicker = ref(false)
const styleKeyword = ref('')
const category = ref<string>()
const categoryExpanded = ref<string[]>([])
const orderSection = ref('basic')
const orderSections = [
  { key:'basic', title:'订单信息', hint:'客户、人员与供料' },
  { key:'po', title:'客户 PO', hint:'PO 信息与默认交期' },
  { key:'styles', title:'款式与规格', hint:'选择款式并填写数量' },
  { key:'delivery', title:'交付批次', hint:'交期与分批数量' },
  { key:'plan', title:'用料试算', hint:'按 BOM 计算订单物料需求' },
]
// 在订单编辑区内定位分组，保留同一 PO 的输入和滚动状态。
function goToOrderSection(key: string) {
  orderSection.value = key
  document.getElementById(`order-section-${key}`)?.scrollIntoView({ behavior:'smooth', block:'start' })
}
const readonly = computed(() => form.value?.status !== 'draft' || !permissions('edit'))
const po = computed(() => form.value?.pos[Number(active.value)])
// 普通款按本款色码筛选；套装按其子款配套中包含的颜色和尺码筛选。
function lineDimensionValues(line:any, dimension:'colorId'|'sizeId'): string[] {
  const values=line.setSpecId ? (line.configuration || []).map((child:any)=>child[dimension]) : [line[dimension]]
  return [...new Set<string>(values.map((value:any)=>value == null ? '__none__' : String(value)))]
}
function dimensionOptions(dimension:'colorId'|'sizeId') {
  const ids=new Set<string>((po.value?.lines || []).flatMap((line:any)=>lineDimensionValues(line,dimension)))
  const source=dimension==='colorId' ? options.value.specs?.colors : options.value.specs?.sizes
  return [...ids].map(id=>({value:id,label:id==='__none__' ? (dimension==='colorId'?'无颜色':'无尺码') : source?.find((item:any)=>String(item.value)===id)?.label || '已失效资料'}))
}
const filteredLines = computed(() => (po.value?.lines || []).filter((line:any)=> {
  if (!lineColorFilter.value && !lineSizeFilter.value) return true
  const candidates=line.setSpecId ? (line.configuration || []) : [line]
  return candidates.some((item:any)=>
    (!lineColorFilter.value || (item.colorId == null ? '__none__' : String(item.colorId))===lineColorFilter.value) &&
    (!lineSizeFilter.value || (item.sizeId == null ? '__none__' : String(item.sizeId))===lineSizeFilter.value))
}))
const selectedLines = computed(() => (po.value?.lines || []).filter((line:any)=>batchSelection.value.includes(line.key)))
const visibleSelectionCount = computed(() => filteredLines.value.filter((line:any)=>batchSelection.value.includes(line.key)).length)
/** 全选或取消当前筛选下可见的订单规格行。 / Select or clear only the visible lines under the active filters. */
function selectVisible(checked:boolean) {
  const keys=new Set(batchSelection.value)
  filteredLines.value.forEach((line:any)=>checked ? keys.add(line.key) : keys.delete(line.key))
  batchSelection.value=[...keys]
}
/** 切换单条颜色尺码的批量操作选择状态。 / Toggle one color-size line in the batch-operation selection. */
function selectLine(key:string, checked:boolean) {
  const keys=new Set(batchSelection.value); checked ? keys.add(key) : keys.delete(key); batchSelection.value=[...keys]
}
watch(active,()=>{ batchSelection.value=[]; lineColorFilter.value=undefined; lineSizeFilter.value=undefined })
const styleOptions = computed(() => (options.value.styles || []).filter((s:any) =>
  (!category.value || categoryIds.value.has(s.categoryId)) &&
  [s.styleCode,s.name].some((v:string) => String(v).toLowerCase().includes(styleKeyword.value.toLowerCase()))))
const categoryRows = computed(() => options.value.categories?.rows || [])
const categoryIds = computed(() => {
  const included = new Set<string>()
  if (!category.value) return included
  included.add(category.value)
  let changed = true
  while (changed) {
    changed = false
    for (const row of categoryRows.value) {
      if (row.parentId && included.has(String(row.parentId)) && !included.has(String(row.id))) {
        included.add(String(row.id))
        changed = true
      }
    }
  }
  return included
})
const categoryTree = computed(() => {
  const schemes = new Map<string,any>()
  const nodes = new Map<string,any>()
  for (const row of categoryRows.value) {
    const schemeId = String(row.schemeId)
    if (!schemes.has(schemeId)) schemes.set(schemeId,{key:'scheme:'+schemeId,title:row.schemeName,selectable:false,children:[]})
    nodes.set(String(row.id),{key:String(row.id),title:row.name,selectable:row.selectable,disabled:!row.selectable,children:[]})
  }
  for (const row of categoryRows.value) {
    const node=nodes.get(String(row.id))
    if (row.parentId && nodes.has(String(row.parentId))) nodes.get(String(row.parentId)).children.push(node)
    else schemes.get(String(row.schemeId)).children.push(node)
  }
  return [...schemes.values()]
})
type ChildSpec = { childId: string; colorId: string | null; sizeId: string | null; quantity: number; childLabel?: string; colorName?: string; sizeName?: string }
type OrderSpecOption = { value: string; label: string; setSpecId?: string; configuration?: ChildSpec[]; colorId?: string | null; sizeId?: string | null }

const pairs = computed<OrderSpecOption[]>(() => {
  if (!config.value || !styleId.value) return []
  const style = options.value.styles.find((s:any)=>s.id===styleId.value)
  if (style?.productionMode !== 'single') return config.value.specs.map((s:any)=>({
    value:s.id,label:[s.colorName,s.sizeName].filter(Boolean).join(' / '),setSpecId:s.id,
    // 配套来自套装规格，每套件数来自套装组成。 / Use saved set selections and component quantities.
    configuration: (s.items || []).map((child: ChildSpec) => ({
      ...child,
      quantity: child.quantity ?? config.value.parts.find((part:any) => String(part.id) === String(child.childId))?.quantity,
    })),
  }))
  const part = config.value.parts[0]
  const colors = part.colors.length ? part.colors : [{value:null,label:'无颜色'}]
  const sizes = part.sizes.length ? part.sizes : [{value:null,label:'无尺码'}]
  return colors.flatMap((c:any)=>sizes.filter((z:any)=>!part.excluded.some((e:any)=>e.colorId===c.value && e.sizeId===z.value))
    .map((z:any)=>({ value:c.value+'/'+z.value,label:c.label+' / '+z.label,colorId:c.value,sizeId:z.value })))
})
// 已加入当前 PO 的规格不能重复添加，其他规格在选款后默认勾选。
const existingPairValues = computed(() => new Set<string>((po.value?.lines || [])
  .filter((line:any) => line.styleId === styleId.value)
  .map((line:any) => String(line.setSpecId || ((line.colorId ?? null) + '/' + (line.sizeId ?? null))))))
const availablePairs = computed(() => pairs.value.filter(pair => !existingPairValues.value.has(String(pair.value))))
const groupedPairs = computed(() => {
  const groups = new Map<string, OrderSpecOption[]>()
  for (const pair of pairs.value) {
    const label = pair.label.split(' / ')[0] || '无颜色'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label)!.push(pair)
  }
  return [...groups.entries()].map(([label, items]) => ({ label, items }))
})

// 统一处理业务响应，失败时保留录入内容。
async function call(method: 'get'|'post', path: string, payload?: any) {
  const response: any = await (method==='get' ? api.get(base+path,{params:payload}) : api.post(base+path,payload))
  if (response.code!==200) throw new Error(response.msg || '操作失败')
  return response.data
}
/** 统一处理单据异步操作期间的重复提交和错误提示。 / Guard asynchronous document actions against duplicate submissions and surface errors consistently. */
async function run(action: () => Promise<void>) {
  busy.value=true
  try { await action() } catch (error:any) { message.error(error.message || '操作失败') }
  finally { busy.value=false }
}
/** 读取订单、PO、人员和款式选择所需数据。 / Load the order and its PO, personnel and style reference data. */
async function load() {
  const data = await call('get','/list',{keyword:keyword.value,status:status.value})
  rows.value = data.map((row:any)=>{
    const doc=row.document
    const pos=doc.pos || []
    const lines=pos.flatMap((p:any)=>p.lines)
    // 列表展示订单设置的默认值，PO 的独立设置通过提示完整说明；不依赖是否已经算料。
    const labels:Record<string,string>={customer:'客供',purchase:'自采',mixed:'客供＋自采'}
    const supplySummary=labels[doc.supply] || '未设置'
    const supplyDetail=['订单默认：'+supplySummary,...pos.map((p:any)=>`PO ${p.number || '未命名'}：${labels[p.supply || doc.supply] || '未设置'}${p.supply ? '（单独设置）' : '（跟随订单）'}`)].join('\n')
    const styleDetails=orderStyleDetails(pos)
    const poDetails=pos.map((item:any)=>({...item,styleSummaries:orderPoStyleSummaries(item)}))
    return {...row,...doc,supplySummary,supplyDetail,poDetails,poNames:pos.map((p:any)=>p.number).join('、'),poSummary:pos.length ? `${pos.length} 个 PO` : '暂无 PO',
      styles:[...new Set(lines.map((l:any)=>l.styleName))].join('、'),styleDetails,styleSummary:styleDetails.length ? `${styleDetails.length} 款式 · ${lines.length} 个规格` : '暂无款式',
      quantity:[['single','件'],['set','套']].map(([mode,unit])=>{
        const n=lines.filter((l:any)=>(l.productionMode==='single'?'single':'set')===mode).reduce((sum:number,l:any)=>sum+l.quantity,0)
        return n ? n+unit : ''
      }).filter(Boolean).join(' / '),
      due:pos.flatMap((p:any)=>p.deliveries?.length?p.deliveries.map((d:any)=>d.date):[p.due]).filter(Boolean).sort()[0],
      processingAmount:lines.length && lines.every((line:any)=>amountCents(line)!==null) ? formatCents(lines.reduce((sum:bigint,line:any)=>sum+amountCents(line)!,0n)) : '待完善'}
  })
}
onMounted(()=>run(async()=>{ await load(); const id=String(route.query.orderId || ''); if (/^\d+$/.test(id)) await open({id}) }))

// 从服务端回填版本，保存后仍留在录单页。
function hydrate(row:any) {
  form.value={...row.document,id:row.id,status:row.status,revision:row.revision,orderNo:row.orderNo,workflowManaged:row.workflowManaged,
    reverseApprovalPending:row.reverseApprovalPending,reverseApprovalInstanceId:row.reverseApprovalInstanceId,
    customerId:row.customerId,companyId:row.companyId,salesId:row.salesId,followerId:row.followerId}
  // 空目的地代表跟随订单；明确填写的 PO 目的地作为独立值保留。
  for (const item of form.value.pos || []) if (!item.destination) item.destination=undefined
  plans.value=row.demand || []
  demandSaved.value=plans.value.length>0
  dirty.value=false
}
/** 初始化新增或编辑订单，并按编辑状态保护已审核单据。 / Initialize an order editor while protecting approved orders from edits. */
function open(row?:any) {
  return run(async()=>{
    options.value=await call('get','/options')
    batchSelection.value=[]; lineColorFilter.value=undefined; lineSizeFilter.value=undefined
    demandPoFilter.value=undefined
    active.value='0'
    reason.value=''
    if (row) hydrate(await call('get','/'+row.id))
    else {
      const code=await call('get','/next-code')
      const defaultSales = options.value.salesPeople?.some((p:any)=>p.value===options.value.currentUser) ? options.value.currentUser : undefined
      form.value={status:'draft',orderNo:code,suggestedCode:code,salesId:defaultSales,
        companyId:options.value.companies[0]?.value,date:new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'}),pos:[],sources:{},supply:'customer'}
      plans.value=[]
      demandSaved.value=false
      addPo()
      dirty.value=false
    }
  })
}
/** 新增 PO 分组并继承订单级交期、目的地和供料默认值。 / Add a PO and inherit order-level due date, destination and sourcing defaults. */
function addPo() {
  form.value.pos.push({number:'',date:form.value.date,lines:[],deliveries:[]})
  active.value=String(form.value.pos.length-1)
  touch()
}
/** 标记可编辑订单已修改，供离开页面时提醒未保存内容。 / Mark an editable order dirty so unsaved changes can be guarded. */
function touch() { if (!readonly.value) dirty.value=true }
/** 删除指定 PO 及其下属规格、交付安排。 / Remove a PO together with its specification lines and delivery allocations. */
function removePo() {
  if (!window.confirm('移除当前 PO 及其全部明细？')) return
  form.value.pos.splice(Number(active.value),1)
  batchSelection.value=[]; lineColorFilter.value=undefined; lineSizeFilter.value=undefined
  active.value='0'
  touch()
}
/** 打开带产品分类树的款式选择弹窗。 / Open the style picker with its product-category tree. */
function pick() {
  styleId.value=undefined
  config.value=undefined
  selected.value=[]
  styleKeyword.value=''
  category.value=undefined
  categoryExpanded.value=categoryTree.value.map((node:any)=>node.key)
  stylePicker.value=true
}
/** 加载单款或套装规格，并默认勾选可下单的有效组合。 / Load single-style or set specifications and preselect valid orderable combinations. */
function selectStyle(id:string) {
  styleId.value=id
  config.value=undefined
  selected.value=[]
  return run(async()=>{
    const result = await call('get','/styles/'+id)
    if (styleId.value === id && stylePicker.value) {
      config.value = result
      selected.value = availablePairs.value.map(pair => pair.value)
    }
  })
}
// 内网 HTTP 下也能为规格生成稳定的交付分配键。
function newLineKey() {
  const bytes=crypto.getRandomValues(new Uint8Array(16))
  bytes[6]=(bytes[6]! & 15) | 64
  bytes[8]=(bytes[8]! & 63) | 128
  const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('')
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-')
}
/** 将所选颜色尺码或套装规格加入当前 PO。 / Add selected color-size or set specifications to the current PO. */
function addLines() {
  const style=options.value.styles.find((s:any)=>s.id===styleId.value)
  for (const pair of pairs.value.filter((p:any)=>selected.value.includes(p.value))) {
    if (po.value.lines.some((l:any)=>l.styleId===styleId.value && (l.setSpecId || ((l.colorId??null)+'/'+(l.sizeId??null)))===pair.value)) continue
    po.value.lines.push({...pair,key:newLineKey(), styleId:styleId.value,styleName:style.styleCode+' · '+style.name,
      productionMode:style.productionMode,quantity:1})
  }
  touch()
  stylePicker.value=false
}
// 优先显示订单快照中的配套，款号名称和色码从当前可用档案解析。
function childSpecLabel(child: ChildSpec) {
  const part = config.value?.parts.find((item:any) => String(item.id) === String(child.childId))
  const style = options.value.styles?.find((item:any) => String(item.id) === String(child.childId))
  const name = child.childLabel || (style ? [style.styleCode,style.name].filter(Boolean).join(' · ') : part?.label || '子款')
  const color = child.colorName || part?.colors?.find((item:any) => String(item.value) === String(child.colorId))?.label
    || options.value.specs?.colors?.find((item:any) => String(item.value) === String(child.colorId))?.label
  const size = child.sizeName || part?.sizes?.find((item:any) => String(item.value) === String(child.sizeId))?.label
    || options.value.specs?.sizes?.find((item:any) => String(item.value) === String(child.sizeId))?.label
  return [name,color || '—',size || '—',child.quantity == null ? '—' : child.quantity+' 件/套'].join(' / ')
}
/** 生成订单明细展示用的款式规格名称。 / Build the style-specification label shown in an order line. */
function spec(line:any) {
  const t=options.value.specs || {}
  if (line.setSpecId) return line.configuration?.map(childSpecLabel).join('；') || line.label || '套装规格'
  return [t.colors?.find((s:any)=>s.value===String(line.colorId))?.label,
    t.sizes?.find((s:any)=>s.value===String(line.sizeId))?.label].filter(Boolean).join(' / ') || line.label || '无色码'
}
/** 从订单规格行中定位算料结果对应的来源明细。 / Find the order specification line referenced by a demand result. */
function planLine(plan:any) { return form.value?.pos?.flatMap((p:any)=>p.lines || []).find((line:any)=>String(line.key)===String(plan.lineKey)) }
/** 组合款式、颜色和尺码形成用料计划明细标签。 / Build a demand-line label from its style, color and size. */
function planLabel(plan:any) {
  const line=planLine(plan)
  return line ? `${line.styleName} · ${spec(line)}` : '订单规格已不可用'
}
// 试算数量由服务端以十进制字符串返回；按字符串精确相加，避免浮点汇总误差。
function sumDecimal(values:unknown[]): string | null {
  const parts=values.map(value=>String(value ?? '').trim())
  if (parts.some(value=>!/^\d+(\.\d+)?$/.test(value))) return null
  const scale=Math.max(0,...parts.map(value=>(value.split('.')[1] || '').length))
  const total=parts.reduce((sum,value)=>{ const [whole,fraction='']=value.split('.'); return sum+BigInt(whole+fraction.padEnd(scale,'0')) },0n)
  if (!scale) return total.toString()
  const digits=total.toString().padStart(scale+1,'0')
  return (digits.slice(0,-scale)+'.'+digits.slice(-scale)).replace(/\.?0+$/,'')
}
// 对汇总后的库存数量换算一次，避免累加逐行舍入的采购数量。
function convertPurchaseQuantity(quantity:string, factor:string): { value:string; approximate:boolean } | null {
  if (!/^\d+(\.\d+)?$/.test(quantity) || !/^\d+(\.\d+)?$/.test(factor)) return null
  const [qw,qf='']=quantity.split('.'), [fw,ff='']=factor.split('.')
  const numerator=BigInt(qw+qf)*10n**BigInt(ff.length+8)
  const denominator=BigInt(fw+ff)*10n**BigInt(qf.length)
  if (denominator===0n) return null
  const remainder=numerator%denominator
  const result=numerator/denominator+(remainder*2n>=denominator ? 1n : 0n)
  const digits=result.toString().padStart(9,'0')
  return {value:(digits.slice(0,-8)+'.'+digits.slice(-8)).replace(/\.?0+$/,''), approximate:remainder!==0n}
}
/** 按物料采购单位汇总数量，并保留库存单位数量供核对。 / Summarize purchase-unit quantities while retaining inventory quantities for reconciliation. */
function summarizePurchaseUnits(details:any[], quantity:string|null): string[] {
  if (details.some(detail=>!Array.isArray(detail.purchaseUnits))) return ['历史试算缺少采购单位快照']
  const signature=(units:any[])=>JSON.stringify(units.map(unit=>[String(unit.unitId),String(unit.unitName),String(unit.mode),sumDecimal([unit.inventoryFactor])]).sort((a,b)=>String(a[0]).localeCompare(String(b[0]))))
  const units=details[0]?.purchaseUnits || []
  if (details.some(detail=>signature(detail.purchaseUnits)!==signature(units))) return ['采购单位或换算关系不一致，请查看明细']
  if (!units.length) return ['未设置采购单位']
  return units.map((unit:any)=>{
    if (unit.mode!=='FIXED') return `${unit.unitName}：按实际换算`
    const converted=quantity==null ? null : convertPurchaseQuantity(quantity,String(unit.inventoryFactor ?? ''))
    return converted ? `${converted.approximate ? '约 ' : ''}${converted.value} ${unit.unitName}` : `${unit.unitName}：无法换算`
  })
}
const materialSummary = computed(() => {
  const groups=new Map<string,any>()
  for (const plan of filteredPlans.value) for (const [index,detail] of (plan.details || []).entries()) {
    // 严格按物料和库存单位汇总；缺失标识的旧数据不与其他记录合并。
    const materialKey=detail.materialId ? String(detail.materialId) : `legacy:${plan.lineKey}:${index}`
    const unitKey=detail.unitId ? String(detail.unitId) : `name:${detail.unit || ''}`
    const key=JSON.stringify([materialKey,unitKey])
    if (!groups.has(key)) groups.set(key,{key,material:detail.material,unit:detail.unit,quantities:[],details:[],variants:new Map()})
    const group=groups.get(key)
    const supply=materialSupply(plan,detail) || ''
    const variantKey=JSON.stringify([detail.materialVariantId || '',detail.materialSpec || '',detail.materialColorId || '',detail.materialColor || '',supply])
    if (!group.variants.has(variantKey)) group.variants.set(variantKey,{key:variantKey,spec:detail.materialSpec,color:detail.materialColor,supply,quantities:[],details:[]})
    group.quantities.push(detail.quantity); group.details.push(detail)
    group.variants.get(variantKey).quantities.push(detail.quantity); group.variants.get(variantKey).details.push(detail)
  }
  return [...groups.values()].map(group=>{ const quantity=sumDecimal(group.quantities); return {...group,quantity,purchaseSummary:summarizePurchaseUnits(group.details,quantity),variants:[...group.variants.values()].map((variant:any)=>{ const subtotal=sumDecimal(variant.quantities); return {...variant,quantity:subtotal,purchaseSummary:summarizePurchaseUnits(variant.details,subtotal)} })} })
})
const supplyLabels:Record<string,string>={customer:'客供',purchase:'自采',mixed:'客供＋自采'}
/** 移除 PO 内指定款式规格并同步清理交付分配。 / Remove one PO specification and its related delivery allocations. */
function removeLine(key:string) {
  const index=po.value.lines.findIndex((line:any)=>line.key===key)
  if (index<0) return
  const [line]=po.value.lines.splice(index,1)
  batchSelection.value=batchSelection.value.filter(item=>item!==key)
  for (const batch of po.value.deliveries) delete batch.allocations[line.key]
  touch()
}
/** 保存订单草稿及 PO 明细，并保留服务端返回的修订号。 / Save the order draft and PO lines while retaining the returned server revision. */
async function save() {
  const current=active.value
  hydrate(await call('post','',form.value))
  active.value=current
  message.success('草稿已保存')
}
/** 试算订单物料需求并区分按物料汇总和款式色码明细。 / Preview material demand in material-summary and style-specification detail views. */
function preview() {
  return run(async()=>{
    if (!readonly.value && (dirty.value || !form.value.id)) await save()
    plans.value=await call('get','/'+form.value.id+'/demand')
  })
}
/** 在订单审核后、BOM 就绪时重新计算需求快照。 / Recalculate the demand snapshot after approval once the BOM is available. */
function calculateApprovedDemand(recalculate=false) {
  return run(async()=>{
    const wasSaved=demandSaved.value
    const result=await call('post','/'+form.value.id+'/demand/calculate',{revision:form.value.revision,recalculate})
    if (result.saved) {
      const current=active.value
      hydrate(result.order)
      active.value=current
      message.success(recalculate ? '重新算料完成，原快照已保留' : '用料计算完成并保存快照')
    } else {
      if (!wasSaved) { plans.value=result.plans || []; demandSaved.value=false }
      const issues=(result.plans || []).filter((item:any)=>item.issue || item.gaps?.length).map((item:any)=>`PO ${item.po}：${item.issue || item.gaps.join('；')}`)
      Modal.warning({title:'用料尚不完整',content:(issues.slice(0,5).join('；') || '请先完善 BOM') + (wasSaved ? '。原用料快照未改变。' : '')})
    }
  })
}
/** 向服务端请求试算或正式重算，并展示版本差异。 / Request a preview or persisted recalculation and display version changes. */
function requestDemandCalculation(recalculate:boolean) {
  if (!recalculate) return calculateApprovedDemand(false)
  Modal.confirm({title:'确认重新算料？',content:'将使用当前已发布 BOM 重新计算；完整后保存新快照并保留原快照。有关联需求计划时需先处理计划。',okText:'重新算料',cancelText:'取消',onOk:()=>calculateApprovedDemand(true)})
}
/** 从订单列表发起算料并回到当前订单上下文。 / Start demand calculation from the order list and retain the order context. */
async function calculateFromList(row:any) {
  await open(row)
  if (!form.value || String(form.value.id)!==String(row.id)) return
  await nextTick()
  goToOrderSection('plan')
  requestDemandCalculation(demandSaved.value)
}
// 草稿显示最新继承值，已提交订单按物料计划快照展示。
function materialSupply(plan:any, detail:any) {
  if (readonly.value) return detail.supply
  const owner = form.value.pos.find((p:any)=>p.lines.some((line:any)=>line.key===plan.lineKey))
  const mode = owner?.supply || form.value.supply
  return mode==='mixed' ? 'mixed' : form.value.sources?.[detail.sourceKey] || mode || detail.supply
}
/** 识别订单是否同时采用客供与自采供料安排。 / Detect whether the order combines customer-supplied and self-purchased materials. */
function mixedSupply(plan:any) {
  const owner = form.value.pos.find((p:any)=>p.lines.some((line:any)=>line.key===plan.lineKey))
  return (owner?.supply || form.value.supply)==='mixed'
}
/** 覆盖单项物料的供料方式并标记需求计划需重新计算。 / Override sourcing for one material and mark demand for recalculation. */
function changeMaterialSupply(key:string, value:string) {
  const sources = (form.value.sources ||= {})
  if (value) sources[key]=value
  else delete sources[key]
  touch()
}
/** 执行订单提交、审核或取消等状态流转并同步审批结果。 / Perform order submit, review or cancellation transitions and synchronize approval state. */
function transition(action:string) {
  return run(async()=>{
    if (action==='submit' && (dirty.value || !form.value.id)) await save()
    hydrate(await call('post','/'+form.value.id+'/transition',{revision:form.value.revision,action,reason:reason.value}))
    reason.value=''
    message.success('订单状态已更新')
  })
}
/** 按状态执行反审核或反提交，并保护已派生的后续单据。 / Reverse approval or submission only when downstream documents permit it. */
function reverseOrder() {
  Modal.confirm({
    title: '确认反提交？',
    content: '订单将返回草稿，提交时生成的物料计划快照将清除，再次提交时重新计算。',
    okText: '确认反提交', cancelText:'取消',
    onOk: () => transition('unsubmit'),
  })
}
/** 提交客户订单反审核申请；审批通过前订单仍保持已审核。 / Submit an order-reversal request and keep the order approved until approval completes. */
async function requestReverseApproval() {
  if(!form.value?.id || busy.value)return
  const reason=reverseApprovalReason.value.trim()
  if(!reason){message.warning('请填写反审核原因');return}
  busy.value=true
  try {
    const result:any=await api.post(`${base}/${form.value.id}/reverse-approval`,{revision:form.value.revision,reason})
    if(result.code!==200)throw new Error(result.msg||'发起反审核审批失败')
    hydrate(result.data)
    reverseApprovalOpen.value=false
    reverseApprovalReason.value=''
    message.success('反审核申请已提交；审批通过前订单仍保持已审核')
  } catch(error:any) { message.error(error?.response?.data?.msg||error.message||'发起反审核审批失败') }
  finally { busy.value=false }
}
/** 关闭订单编辑器前确认是否丢弃未保存变更。 / Confirm before discarding unsaved changes when closing the order editor. */
function close() {
  if (dirty.value && !window.confirm('离开后，本次未保存的修改将丢失。确定返回？')) return
  form.value=undefined
  return run(load)
}
</script>

<template>
  <StandardListLayout v-if="!form">
    <template #filters>
      <label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" allow-clear placeholder="订单号、客户、PO 或款号" @press-enter="run(load)" /></label>
      <label class="zy-filter-field"><span>订单状态</span><a-select v-model:value="status" allow-clear style="width:130px" :options="Object.entries(statuses).map(([value,label])=>({value,label}))" /></label>
    </template>
    <FoundationTable page-toolbar storage-key="customer_order_list" :columns="columns" :rows="rows" :loading="busy" :action-width="170">
      <template #actions>
        <a-button v-if="permissions('edit')" type="primary" @click="open()">新增订单</a-button>
        <a-button type="primary" @click="run(load)">查询</a-button>
        <a-button @click="keyword=''; status=undefined; run(load)">重置</a-button>
      </template>
      <template #cell="{column,row}">
        <a-tag v-if="column.key==='status'" :color="row.status==='approved'?'green':row.status==='submitted'?'gold':'default'">{{ statuses[row.status] }}</a-tag>
        <a-tag v-else-if="column.key==='demandState'" :color="row.demandState==='ready'?'green':row.demandState==='outdated'?'orange':row.demandState==='unavailable'?'red':'default'">{{ ({unplanned:'未算料',ready:'已算料',outdated:'BOM 已更新',unavailable:'BOM 不可用',unknown:'待核对'} as Record<string,string>)[row.demandState] || '待核对' }}</a-tag>
        <a-popover v-else-if="column.key==='poNames'" title="客户 PO 明细" :trigger="['hover', 'focus', 'click']">
          <template #content>
            <div class="order-associated-detail order-po-detail">
              <section v-for="(po,index) in row.poDetails || []" :key="po.number || index">
                <div class="order-associated-section-title"><strong>PO {{ po.number || Number(index)+1 }}</strong><span>{{ po.lines?.length || 0 }} 个下单规格</span></div>
                <div class="order-associated-po-facts">
                  <div><span>PO 日期</span><strong>{{ po.date || '未填写' }}</strong></div>
                  <div><span>默认交期</span><strong>{{ po.due || '未填写' }}</strong></div>
                  <div><span>计划下线</span><strong>{{ po.offlineDate || '未填写' }}</strong></div>
                  <div><span>供料安排</span><strong>{{ ({customer:'客供',purchase:'自采',mixed:'客供＋自采'} as Record<string,string>)[po.supply || row.supply] || '未设置' }}<small>{{ poSupplySource(po) }}</small></strong></div>
                  <div v-if="po.destination"><span>交货地点</span><strong>{{ po.destination }}</strong></div>
                </div>
                <div v-if="po.styleSummaries?.length" class="order-associated-po-summary">
                  <div class="order-associated-po-summary-heading">款式汇总</div>
                  <div v-for="style in po.styleSummaries" :key="style.key" class="order-associated-po-summary-row">
                    <div><strong>{{ style.name }}</strong><small>{{ productionModeLabel(style.productionMode) }} · {{ style.specCount }} 个规格</small></div>
                    <div><strong>{{ style.quantity }} {{ style.unit }}</strong><small v-if="style.amount != null">加工金额 {{ style.amount }}</small></div>
                  </div>
                </div>
                <span v-else class="order-associated-meta">尚未添加款式规格</span>
                <div v-if="po.remark" class="order-associated-meta">PO 备注：{{ po.remark }}</div>
              </section>
              <a-empty v-if="!row.poDetails?.length" description="暂无客户 PO" />
            </div>
          </template>
          <button type="button" class="order-associated-trigger">{{ row.poSummary }}</button>
        </a-popover>
        <a-popover v-else-if="column.key==='styles'" title="订单款式与规格" :trigger="['hover', 'focus', 'click']">
          <template #content>
            <div class="order-associated-detail order-style-detail">
              <section v-for="style in row.styleDetails || []" :key="style.key">
                <div class="order-associated-style-title"><strong>{{ style.name }}</strong><a-tag>{{ productionModeLabel(style.productionMode) }}</a-tag></div>
                <div v-for="(po,index) in style.poGroups" :key="po.number || index" class="order-associated-style-po">
                  <div class="order-associated-style-po-title"><strong>PO {{ po.number || Number(index)+1 }}</strong><span>本 PO {{ po.quantity }} {{ po.unit }}<template v-if="po.amount != null"> · 加工金额 {{ po.amount }}</template></span></div>
                  <div v-for="(line,lineIndex) in po.lines" :key="line.key || lineIndex" class="order-associated-lines order-style-lines">
                    <div class="order-associated-line-main">
                      <div class="order-associated-line-title"><strong>{{ line.specification }}</strong><small v-if="line.setSpecId">套装规格</small></div>
                      <div class="order-associated-line-quantity"><strong>{{ line.quantity }} {{ orderUnit(line) }}</strong><small v-if="line.unitPrice != null">{{ orderLinePriceSummary(line) }}</small></div>
                    </div>
                    <div v-if="line.setSpecId && line.configuration?.length" class="order-associated-children">
                      <span v-for="(child,childIndex) in line.configuration" :key="child.childId || childIndex">{{ childSpecLabel(child) }}</span>
                    </div>
                  </div>
                </div>
              </section>
              <a-empty v-if="!row.styleDetails?.length" description="暂无款式规格" />
            </div>
          </template>
          <button type="button" class="order-associated-trigger">{{ row.styleSummary }}</button>
        </a-popover>
        <a-popover v-else-if="column.key==='supplySummary'" title="供料安排" :trigger="['hover', 'focus', 'click']"><template #content><div class="order-associated-detail"><div class="order-associated-supply"><strong>订单默认</strong><span>{{ row.supplySummary }}</span></div><div v-for="(po,index) in row.pos || []" :key="po.number || index" class="order-associated-supply"><strong>PO {{ po.number || Number(index)+1 }}</strong><span>{{ ({customer:'客供',purchase:'自采',mixed:'客供＋自采'} as Record<string,string>)[po.supply || row.supply] || '未设置' }}<small>{{ po.supply ? '单独设置' : '跟随订单默认' }}</small></span></div></div></template><button type="button" class="order-associated-trigger">{{ row.supplySummary }}</button></a-popover>
        <span v-else :title="String(row[column.key] ?? '')">{{ row[column.key] ?? '—' }}</span>
      </template>
      <template #action="{row}"><a-space :size="4"><a-button type="link" size="small" @click="open(row)">{{ row.status==='draft' && permissions('edit') ? '编辑' : '查看' }}</a-button><a-button v-if="row.status==='approved' && permissions('edit')" type="link" size="small" @click="calculateFromList(row)">{{ row.demandState==='unplanned' ? '算料' : '重新算料' }}</a-button></a-space></template>
    </FoundationTable>
  </StandardListLayout>
  <section v-else class="order-editor">
    <header class="order-bar">
      <a-space><a-button @click="close">返回列表</a-button><strong>{{ form.id ? form.orderNo : '新增客户订单' }}</strong><a-tag>{{ statuses[form.status] }}</a-tag><a-tag v-if="form.reverseApprovalPending" color="processing">反审核审批中</a-tag><router-link v-if="form.reverseApprovalPending && form.reverseApprovalInstanceId" :to="{name:'ApprovalInitiated',query:{instanceId:form.reverseApprovalInstanceId}}">查看反审核进度</router-link></a-space>
      <a-space>
        <a-button v-if="!readonly" :loading="busy" @click="run(save)">保存草稿</a-button>
        <router-link v-if="form.workflowManaged && form.status==='submitted'" :to="{name:'ApprovalWaiting'}">查看审批待办</router-link>
        <a-button v-if="!readonly" type="primary" :loading="busy" @click="transition('submit')">提交审核</a-button>
        <a-button v-if="!form.workflowManaged && form.status==='submitted' && permissions('review')" type="primary" :loading="busy" @click="transition('approve')">审核通过</a-button>
        <a-button v-if="!form.workflowManaged && form.status==='submitted' && permissions('edit')" :loading="busy" @click="reverseOrder()">反提交</a-button>
        <a-button v-if="form.status==='approved' && permissions('edit') && !form.reverseApprovalPending" :loading="busy" @click="reverseApprovalReason='';reverseApprovalOpen=true">申请反审核</a-button>
      </a-space>
    </header>
    <div class="order-editor-layout">
    <EditorSectionNav eyebrow="客户订单" :title="form.id ? '维护订单' : '创建客户订单'" description="按订单、PO 和款式规格依次完善资料。" :sections="orderSections" :active="orderSection" @select="goToOrderSection" />
    <main class="order-body" @input="touch">
      <a-form layout="vertical" :disabled="readonly">
        <div id="order-section-basic" class="order-section order-section-title"><strong>订单信息</strong><span>填写客户、责任人员及订单默认安排</span></div>
        <div class="order-fields order-header-fields">
          <a-form-item label="订单号"><a-input v-model:value="form.orderNo" /></a-form-item>
          <a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="options.customers" show-search option-filter-prop="label" @change="touch" /></a-form-item>
          <a-form-item label="归属公司" required><a-select v-model:value="form.companyId" :options="options.companies" :disabled="readonly || !!form.id" @change="touch" /></a-form-item>
          <a-form-item label="客户订单日期" required><DatePicker v-model:value="form.date" value-format="YYYY-MM-DD" @change="touch" /></a-form-item>
          <a-form-item label="业务员" required><a-select v-model:value="form.salesId" :options="options.salesPeople" show-search option-filter-prop="label" placeholder="选择已设置业务员职责的人员" @change="touch" /></a-form-item>
          <a-form-item label="跟单员"><a-select v-model:value="form.followerId" :options="options.followers" show-search option-filter-prop="label" allow-clear placeholder="选择已设置跟单员职责的人员" @change="touch" /></a-form-item>
          <a-form-item label="合同号 / 客户参考号"><a-input v-model:value="form.reference" /></a-form-item>
          <a-form-item label="默认目的地"><a-input v-model:value="form.destination" /></a-form-item>
          <a-form-item label="订单默认供料"><a-select v-model:value="form.supply" :options="orderSupplyOptions" placeholder="沿用原规格设置" @change="touch" /></a-form-item>
          <a-form-item label="订单备注" class="order-remark"><a-input v-model:value="form.remark" placeholder="填写订单备注" /></a-form-item>
        </div>
        <div id="order-section-po" class="order-section"><strong>客户 PO</strong><a-button v-if="!readonly" @click="addPo">新增 PO</a-button></div>
        <a-tabs v-model:active-key="active">
          <a-tab-pane v-for="(p,i) in form.pos" :key="String(i)" :tab="p.number || 'PO '+(Number(i)+1)" />
        </a-tabs>
        <template v-if="po">
          <div class="order-fields order-po-fields">
            <a-form-item label="客户 PO 号" required><a-input v-model:value="po.number" /></a-form-item>
            <a-form-item label="PO 日期"><DatePicker v-model:value="po.date" value-format="YYYY-MM-DD" @change="touch" /></a-form-item>
            <a-form-item label="默认交期"><DatePicker v-model:value="po.due" value-format="YYYY-MM-DD" @change="()=>changeDue(po,po.due)" /></a-form-item>
            <a-form-item label="默认下线日期"><DatePicker v-model:value="po.offlineDate" value-format="YYYY-MM-DD" @change="()=>{ po._autoOfflineDate=undefined; touch() }" /><small class="order-field-hint">按交期提前 {{ options.offlineLeadDays ?? 0 }} 天建议，可修改</small></a-form-item>
            <a-form-item label="目的地"><a-input :value="po.destination ?? form.destination ?? ''" allow-clear placeholder="填写目的地" @update:value="(value:string)=>{ po.destination=value; touch() }" /><small v-if="po.destination == null && form.destination" class="order-field-hint">跟随订单默认目的地</small><a v-else-if="po.destination && !readonly" class="order-field-hint" @click="po.destination=undefined; touch()">恢复跟随订单</a></a-form-item>
            <a-form-item label="PO 供料安排"><a-select :value="po.supply || form.supply || undefined" allow-clear :options="orderSupplyOptions" placeholder="请选择供料安排" @change="(value:any)=>{ po.supply=typeof value === 'string' ? value : undefined; touch() }" /><small v-if="!po.supply && form.supply" class="order-field-hint">跟随订单默认供料</small><a v-else-if="po.supply && !readonly" class="order-field-hint" @click="po.supply=undefined; touch()">恢复跟随订单</a></a-form-item>
            <a-form-item label="PO 备注"><a-input v-model:value="po.remark" /></a-form-item>
          </div>
          <div id="order-section-styles" class="order-section"><strong>款式与规格</strong><a-space v-if="!readonly"><a-button @click="removePo">移除当前 PO</a-button><a-button type="primary" @click="pick">选择款式与规格</a-button></a-space></div>
          <div class="line-batch-toolbar">
            <div class="line-filters"><span>筛选规格</span><a-select v-model:value="lineColorFilter" allow-clear show-search option-filter-prop="label" placeholder="全部颜色" :options="dimensionOptions('colorId')" /><a-select v-model:value="lineSizeFilter" allow-clear show-search option-filter-prop="label" placeholder="全部尺码" :options="dimensionOptions('sizeId')" /><a-button v-if="lineColorFilter || lineSizeFilter" type="link" @click="lineColorFilter=undefined; lineSizeFilter=undefined">清除筛选</a-button><small>套装按子款配套筛选</small></div>
            <div v-if="!readonly" class="line-batch-actions"><span>已勾选 <strong>{{ selectedLines.length }}</strong> 项<template v-if="selectedLines.length > visibleSelectionCount">（含筛选外 {{ selectedLines.length - visibleSelectionCount }} 项）</template></span><a-button type="link" size="small" :disabled="!selectedLines.length" @click="batchSelection=[]">清空勾选</a-button><a-input-number v-model:value="batchQuantity" :min="1" :max="10000000" :precision="0" placeholder="订购数量" /><a-button :disabled="!selectedLines.length" @click="applyBatchQuantity">设置数量</a-button><a-input-number v-model:value="batchUnitPrice" string-mode :min="0" :max="99999999.99" :precision="2" placeholder="加工单价" /><a-button :disabled="!selectedLines.length" @click="applyBatchPrice">设置单价</a-button></div>
          </div>
          <div class="order-table">
            <table><thead><tr><th v-if="!readonly" class="line-check"><a-checkbox :checked="!!filteredLines.length && visibleSelectionCount===filteredLines.length" :indeterminate="visibleSelectionCount>0 && visibleSelectionCount<filteredLines.length" :disabled="!filteredLines.length" aria-label="勾选当前筛选结果" @change="(e:any)=>selectVisible(e.target.checked)" /></th><th>款式</th><th>颜色尺码 / 套装配套</th><th>订购数量</th><th>加工单价</th><th>加工金额</th><th v-if="!readonly">操作</th></tr></thead>
              <tbody><tr v-for="line in filteredLines" :key="line.key">
                <td v-if="!readonly" class="line-check"><a-checkbox :checked="batchSelection.includes(line.key)" :aria-label="'选择' + line.styleName + ' ' + spec(line)" @change="(e:any)=>selectLine(line.key,e.target.checked)" /></td>
                <td>{{ line.styleName }}</td>
                <td>
                  <ul v-if="line.setSpecId && line.configuration?.length" class="set-child-list">
                    <li v-for="child in line.configuration" :key="child.childId">{{ childSpecLabel(child) }}</li>
                  </ul>
                  <template v-else>{{ spec(line) }}</template>
                </td>
                <td><a-input-number v-model:value="line.quantity" :min="1" :max="10000000" :precision="0" @change="touch" /> {{ line.productionMode==='single'?'件':'套' }}</td>
                <td><a-input-number v-model:value="line.unitPrice" string-mode :min="0" :max="99999999.99" :precision="2" placeholder="待填写" @change="touch" /> / {{ line.productionMode==='single'?'件':'套' }}</td>
                <td>{{ amountCents(line) === null ? '待填写单价' : formatCents(amountCents(line)!) }}</td>
                <td v-if="!readonly"><a-button type="link" danger @click="removeLine(line.key)">移除</a-button></td>
              </tr></tbody>
            </table>
            <a-empty v-if="!filteredLines.length" :description="po.lines.length ? '当前筛选条件下没有规格' : '请选择款式与规格，再填写订购数量'" />
          </div>
          <div id="order-section-delivery" class="order-section"><strong>交付批次</strong><a-button v-if="!readonly" @click="po.deliveries.push({date:po.due,destination:po.destination || form.destination,allocations:{}});touch()">新增交付批次</a-button></div>
          <p v-if="!po.deliveries.length">当前 PO 按默认交期一次交付。需要分批时，添加批次并分别分配各规格数量。</p>
          <div v-for="(batch,i) in po.deliveries" :key="i" class="order-batch">
            <a-space wrap><strong>批次 {{ Number(i)+1 }}</strong><DatePicker v-model:value="batch.date" placeholder="交付日期" value-format="YYYY-MM-DD" @change="touch" />
              <a-input v-model:value="batch.destination" placeholder="批次目的地" />
              <a-button v-if="!readonly" type="link" danger @click="po.deliveries.splice(i,1);touch()">移除批次</a-button></a-space>
            <div class="order-fields"><a-form-item v-for="line in po.lines" :key="line.key" :label="line.styleName+' · '+spec(line)">
              <a-input-number v-model:value="batch.allocations[line.key]" :min="0" :max="line.quantity" :precision="0" placeholder="分配数量" @change="touch" />
            </a-form-item></div>
          </div>
        </template>
      </a-form>
      <div class="order-amount">整单加工金额：<strong>{{ orderAmount.count ? orderAmount.value : '—' }}</strong><span v-if="orderAmount.missing">（{{ orderAmount.missing }} 项未填写单价，金额暂不完整）</span></div>
      <div id="order-section-plan" class="order-section"><strong>订单用料试算</strong><a-space wrap><label v-if="plans.length" class="demand-po-filter"><span>客户 PO</span><a-select v-model:value="demandPoFilter" allow-clear show-search option-filter-prop="label" placeholder="全部 PO" :options="demandPoOptions" /></label><a-radio-group v-if="plans.length" v-model:value="demandView" button-style="solid" size="small"><a-radio-button value="summary">按物料汇总</a-radio-button><a-radio-button value="detail">按款式规格明细</a-radio-button></a-radio-group><a-button v-if="!readonly" :loading="busy" @click="preview">保存并试算用料</a-button><a-button v-if="form.status==='approved' && permissions('edit')" type="primary" :loading="busy" @click="requestDemandCalculation(demandSaved)">{{ demandSaved ? '重新算料' : '算料并保存快照' }}</a-button></a-space></div>
      <a-alert type="info" show-icon message="本区用于按 BOM 试算订单用料，不在这里提交物料需求计划。订单审核后系统会生成需求计划草稿；计划数量调整和提交流程请到“物料需求计划”中办理。" />
      <a-alert v-if="['submitted','approved'].includes(form.status) && !demandSaved" type="info" show-icon message="订单可以先审核；BOM 完成后，在已审核订单中进行算料。用料完整后才会保存快照。" />
      <a-alert v-if="dirty && plans.length && !readonly" type="warning" show-icon message="订单有未保存的修改，当前用料结果可能不是最新数据，请重新保存并试算。" />
      <a-alert v-if="!readonly && form.pos.some((p:any)=>(p.supply || form.supply)==='mixed')"
        type="info" show-icon message="客供＋自采：物料允许按客供或自采入库，实际来源在收货时选择。" />
      <template v-if="demandView==='summary' && plans.length">
        <a-alert v-for="plan in filteredPlans.filter((item:any)=>item.issue || item.gaps?.length)" :key="plan.lineKey" type="warning" show-icon :message="'PO '+plan.po+' · '+planLabel(plan)+'：'+(plan.issue || plan.gaps.join('；'))" />
        <p class="demand-view-hint">当前范围：{{ demandPoFilter ? 'PO '+demandPoFilter : '全部 PO' }}。按物料和库存单位汇总，各颜色、规格及供料方式分别列出；采购单位数量为换算参考。</p>
        <div v-for="group in materialSummary" :key="group.key" class="order-batch demand-summary">
          <div class="demand-summary-header"><strong>{{ group.material }}</strong><div class="demand-summary-totals"><span>库存总需求 <b>{{ group.quantity ?? '数量异常，无法汇总' }}</b> {{ group.unit }}</span><span v-for="purchase in group.purchaseSummary" :key="purchase">采购单位参考：{{ purchase }}</span></div></div>
          <div class="order-table"><table><thead><tr><th>物料规格</th><th>物料颜色</th><th>供料方式</th><th>库存单位需求小计</th><th>采购单位参考小计</th></tr></thead><tbody><tr v-for="variant in group.variants" :key="variant.key"><td>{{ variant.spec || '—' }}</td><td>{{ variant.color || '—' }}</td><td>{{ supplyLabels[variant.supply] || '待确定' }}</td><td>{{ variant.quantity ?? '数量异常' }} {{ group.unit }}</td><td><div v-for="purchase in variant.purchaseSummary" :key="purchase">{{ purchase }}</div></td></tr></tbody></table></div>
        </div>
        <a-empty v-if="!materialSummary.length" description="当前试算没有可汇总的物料" />
      </template>
      <p v-if="demandView==='detail' && plans.length" class="demand-view-hint">当前范围：{{ demandPoFilter ? 'PO '+demandPoFilter : '全部 PO' }}</p>
      <div v-for="plan in demandView==='detail' ? filteredPlans : []" :key="plan.lineKey" class="order-batch">
        <strong>PO {{ plan.po }} · {{ planLabel(plan) }}</strong>
        <a-alert v-if="plan.issue || plan.gaps?.length" type="warning" :message="plan.issue || plan.gaps.join('；')" />
        <div v-if="plan.details?.length" class="order-table"><table><thead><tr><th>物料</th><th>规格</th><th>物料颜色</th><th>部位</th><th>库存单位需求</th><th>采购单位参考</th><th>供料方式</th></tr></thead>
          <tbody><tr v-for="(d,i) in plan.details" :key="i">
            <td>{{ d.material }}</td><td>{{ d.materialSpec }}</td><td>{{ d.materialColor }}</td><td>{{ d.part }}</td><td>{{ d.quantity }} {{ d.unit }}</td><td><template v-if="d.purchaseUnits?.length"><div v-for="unit in d.purchaseUnits" :key="unit.unitId">{{ unit.quantity == null ? '按实际换算' : (unit.approximate ? '约 ' : '') + unit.quantity }} {{ unit.unitName }}</div></template><span v-else>按库存单位 {{ d.unit }}</span></td>
            <td><a-select :value="materialSupply(plan,d)" allow-clear :disabled="readonly || mixedSupply(plan)" :options="supplyOptions" style="width:140px"
              @change="(v:any)=>{ changeMaterialSupply(d.sourceKey,v) }" /></td>
          </tr></tbody></table></div>
      </div>
      <a-alert v-if="form.reviewNote" type="info" :message="form.reviewNote" />
      <a-space v-if="(!form.workflowManaged && form.status==='submitted' && permissions('review')) || (!form.workflowManaged && form.id && ['draft','submitted'].includes(form.status) && permissions('cancel'))">
        <a-input v-model:value="reason" placeholder="退回或取消原因" />
        <a-button v-if="!form.workflowManaged && form.status==='submitted' && permissions('review')" :disabled="!reason.trim() || busy" @click="transition('return')">退回修改</a-button>
        <a-button v-if="!form.workflowManaged && form.id && ['draft','submitted'].includes(form.status) && permissions('cancel')" danger :disabled="!reason.trim() || busy" @click="transition('cancel')">取消订单</a-button>
      </a-space>
    </main>
    </div>
    <a-modal v-model:open="stylePicker" title="选择款式与规格" :width="1200" :body-style="{ padding: '0' }" :mask-closable="false" :ok-text="`加入当前 PO（${selected.length}）`" cancel-text="取消" :ok-button-props="{disabled:!selected.length || busy}" @ok="addLines">
      <div class="style-picker">
        <aside class="style-category-pane">
          <div class="picker-pane-title">款式分类</div><p class="picker-hint">选择分类筛选款式</p>
          <a-alert v-if="options.categories?.invalid" type="warning" show-icon message="款式默认分类类型不可用或不在可见范围，请检查系统参数和人员分类权限。" />
          <a-alert v-if="options.categories?.restricted" type="info" show-icon message="仅显示你有权限查看的分类和款式" />
          <a-tree :tree-data="categoryTree" :expanded-keys="categoryExpanded" :selected-keys="category ? [category] : []" block-node
            @expand="keys=>categoryExpanded=keys.map(String)" @select="keys=>category=String(keys[0] || '') || undefined" />
        </aside>
        <div class="style-list-pane">
          <div class="picker-pane-title">选择款式 <span>{{ styleOptions.length }}</span></div>
          <a-input v-model:value="styleKeyword" allow-clear placeholder="搜索当前分类下的款号或款式名称" />
          <div class="style-results">
            <button v-for="item in styleOptions" :key="item.id" type="button" class="style-result" :class="{active:styleId===item.id}" @click="selectStyle(item.id)">
              <span><strong>{{ item.styleCode }}</strong><small>{{ item.name }}</small></span><a-tag>{{ item.productionMode==='single'?'单款':'套装' }}</a-tag>
            </button>
            <a-empty v-if="!styleOptions.length" description="当前分类下没有可选款式" />
          </div>
        </div>
        <div class="style-picker-content">
          <template v-if="styleId">
            <div class="spec-heading"><div><div class="picker-pane-title">{{ pairs[0]?.setSpecId ? '选择套装规格' : '选择颜色尺码' }}</div><p class="picker-hint">{{ options.styles?.find((item:any)=>item.id===styleId)?.styleCode }} · 默认选中可添加的规格</p></div><span class="selection-count">已选 {{ selected.length }} / {{ availablePairs.length }}</span></div>
            <div v-if="pairs.length" class="spec-actions"><a-button type="link" size="small" :disabled="!availablePairs.length" @click="selected=availablePairs.map(pair=>pair.value)">全选可添加</a-button><a-button type="link" size="small" :disabled="!selected.length" @click="selected=[]">清空选择</a-button><span v-if="existingPairValues.size">已加入当前 PO 的规格不可重复添加</span></div>
            <div class="spec-scroll">
            <a-checkbox-group v-model:value="selected" v-if="pairs[0]?.setSpecId" class="set-spec-options">
              <div v-for="pair in pairs" :key="pair.value" class="set-spec-option" :class="{chosen:selected.includes(pair.value), unavailable:existingPairValues.has(String(pair.value))}">
                <a-checkbox :value="pair.value" :disabled="existingPairValues.has(String(pair.value))">{{ pair.label }}<span v-if="existingPairValues.has(String(pair.value))" class="already-added">已添加</span></a-checkbox>
                <ul class="set-child-list">
                  <li v-for="child in pair.configuration" :key="child.childId">{{ childSpecLabel(child) }}</li>
                </ul>
              </div>
            </a-checkbox-group>
            <a-checkbox-group v-else v-model:value="selected" class="style-spec-options">
              <div v-for="group in groupedPairs" :key="group.label" class="spec-color-group"><div class="spec-color-title">{{ group.label }} <span>{{ group.items.length }} 个尺码</span></div><div class="spec-size-grid"><label v-for="pair in group.items" :key="pair.value" class="spec-size" :class="{chosen:selected.includes(pair.value), unavailable:existingPairValues.has(String(pair.value))}"><a-checkbox :value="pair.value" :disabled="existingPairValues.has(String(pair.value))">{{ pair.label.split(' / ').slice(1).join(' / ') || '无尺码' }}</a-checkbox><small v-if="existingPairValues.has(String(pair.value))">已添加</small></label></div></div>
            </a-checkbox-group>
            <a-empty v-if="!pairs.length && !busy" description="该款式尚未维护可用规格" />
            </div>
          </template>
          <a-empty v-else description="请先选择款式" />
        </div>
      </div>
    </a-modal>
    <a-modal v-model:open="reverseApprovalOpen" title="申请客户订单反审核" ok-text="提交审批" cancel-text="取消" :confirm-loading="busy" :mask-closable="false" @ok="requestReverseApproval">
      <a-alert type="info" show-icon message="反审核需经过独立审批流程" description="提交后订单仍保持已审核；审批通过后订单退回草稿，重新提交时会重新走客户订单审批。请先处理所有关联的物料需求计划。" />
      <a-form-item label="反审核原因" required style="margin-top:16px"><a-textarea v-model:value="reverseApprovalReason" :maxlength="500" :rows="4" show-count placeholder="请说明申请反审核的原因" /></a-form-item>
    </a-modal>
  </section>
</template>

<style scoped>
.order-associated-trigger { max-width:100%; padding:0; border:0; background:transparent; color:#1e5eff; cursor:pointer; font:inherit; text-align:left; white-space:nowrap; }
.order-associated-trigger:hover,.order-associated-trigger:focus-visible { color:#1648c5; text-decoration:underline; outline:none; }
:global(.ant-popover .order-associated-detail) { max-width:440px; max-height:360px; overflow:auto; }
:global(.ant-popover .order-associated-detail.order-po-detail),:global(.ant-popover .order-associated-detail.order-style-detail) { width:min(560px, calc(100vw - 48px)); max-width:560px; max-height:440px; padding-right:4px; }
:global(.ant-popover .order-associated-detail section) { min-width:320px; padding:8px 0; border-bottom:1px solid #eef1f5; }
:global(.ant-popover .order-associated-detail section:first-child) { padding-top:0; }
:global(.ant-popover .order-associated-detail section:last-child) { border-bottom:0; padding-bottom:0; }
:global(.ant-popover .order-associated-meta) { margin-top:4px; color:#738198; font-size:12px; }
:global(.ant-popover .order-associated-section-title),:global(.ant-popover .order-associated-style-title),:global(.ant-popover .order-associated-style-po-title) { display:flex; align-items:center; justify-content:space-between; gap:12px; }
:global(.ant-popover .order-associated-section-title > span),:global(.ant-popover .order-associated-style-po-title > span) { color:#738198; font-size:12px; }
:global(.ant-popover .order-associated-po-facts) { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:7px 16px; margin-top:8px; padding:9px 10px; background:#f7f9fc; border-radius:6px; }
:global(.ant-popover .order-associated-po-facts > div) { display:flex; justify-content:space-between; align-items:flex-start; gap:10px; min-width:0; font-size:12px; }
:global(.ant-popover .order-associated-po-facts > div > span) { color:#738198; white-space:nowrap; }
:global(.ant-popover .order-associated-po-facts > div > strong) { text-align:right; overflow-wrap:anywhere; }
:global(.ant-popover .order-associated-po-facts small) { display:block; color:#8290a5; font-weight:400; }
:global(.ant-popover .order-associated-po-summary) { margin-top:10px; }
:global(.ant-popover .order-associated-po-summary-heading) { margin-bottom:4px; color:#738198; font-size:12px; }
:global(.ant-popover .order-associated-po-summary-row) { display:flex; justify-content:space-between; gap:16px; padding:6px 2px; border-bottom:1px dashed #e9edf3; }
:global(.ant-popover .order-associated-po-summary-row:last-child) { border-bottom:0; }
:global(.ant-popover .order-associated-po-summary-row > div) { display:flex; flex-direction:column; gap:2px; min-width:0; }
:global(.ant-popover .order-associated-po-summary-row > div:last-child) { align-items:flex-end; text-align:right; flex:none; }
:global(.ant-popover .order-associated-po-summary-row small) { color:#8290a5; font-size:12px; }
:global(.ant-popover .order-associated-lines) { margin-top:8px; padding-left:10px; border-left:2px solid #dbe7ff; }
:global(.ant-popover .order-associated-line) { padding:6px 0; border-bottom:1px dashed #e9edf3; }
:global(.ant-popover .order-associated-line:last-child) { border-bottom:0; }
:global(.ant-popover .order-associated-line-main) { display:flex; justify-content:space-between; align-items:flex-start; gap:18px; }
:global(.ant-popover .order-associated-line-title) { display:flex; flex-direction:column; gap:2px; min-width:0; }
:global(.ant-popover .order-associated-line-title > span) { color:#596579; }
:global(.ant-popover .order-associated-line-title small),:global(.ant-popover .order-associated-line-quantity small),:global(.ant-popover .order-associated-children) { color:#8290a5; font-size:12px; }
:global(.ant-popover .order-associated-line-quantity) { display:flex; flex-direction:column; align-items:flex-end; flex:none; text-align:right; }
:global(.ant-popover .order-associated-children) { display:grid; gap:2px; margin:5px 0 0 8px; padding-left:8px; border-left:1px solid #e6eaf0; }
:global(.ant-popover .order-associated-style-po) { margin-top:8px; }
:global(.ant-popover .order-associated-style-po-title) { padding:5px 8px; background:#f7f9fc; border-radius:4px; }
:global(.ant-popover .order-associated-style-lines) { margin-top:0; }
:global(.ant-popover .order-associated-style-title .ant-tag) { margin:0; }
</style>

<style scoped>
.order-editor { display:flex; flex-direction:column; height:100%; min-height:0; gap:0; background:#fff; }
.order-editor-layout { display:grid; grid-template-columns:190px minmax(0,1fr); flex:1; min-height:0; }
.order-bar,.order-section { display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap; }
.order-bar { flex:none; padding:12px 16px; border-bottom:1px solid #e5e7eb; }
.order-body { flex:1; min-height:0; overflow:auto; padding:18px 20px 28px; }
.order-fields { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:0 16px; }
.order-header-fields { grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); }
.order-po-fields { grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); }
.order-fields :deep(.ant-form-item) { min-width:0; margin-bottom:16px; }
.order-fields :deep(.ant-picker),.order-fields :deep(.ant-select),.order-fields :deep(.ant-input-number) { width:100%; }
.order-remark { grid-column:span 2; }
.order-section { margin:20px 0 12px; padding-top:16px; border-top:1px solid #edf0f5; }
.order-section strong { font-size:15px; color:#1d2939; }
.order-section-title { justify-content:flex-start; gap:12px; margin-top:0; padding-top:0; border:0; }
.order-section-title span { font-size:12px; color:#86909c; }
.order-field-hint { color:#86909c; font-size:12px; }
.order-amount { margin:14px 0; padding:12px 16px; background:#f4f7fd; color:#344660; border-radius:6px; }
.order-amount strong { color:#1e5eff; font-size:16px; }
.order-amount span { color:#a66a19; font-size:12px; }
.demand-view-hint { margin:12px 0; color:#7b8a9e; font-size:12px; }
.demand-po-filter { display:inline-flex; align-items:center; gap:8px; color:#53657d; font-size:13px; }
.demand-po-filter :deep(.ant-select) { width:170px; }
.demand-summary-header { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:8px; margin-bottom:10px; }
.demand-summary-header span { color:#53657d; }
.demand-summary-header b { color:#1e5eff; font-size:16px; }
.demand-summary-totals { display:flex; align-items:center; flex-wrap:wrap; justify-content:flex-end; gap:8px 16px; }
.demand-summary .order-table { margin-bottom:0; }
.order-body :deep(.ant-tabs-nav) { margin-bottom:16px; }
.order-table :deep(.ant-empty) { padding:14px 0; margin:0; border:1px solid #e5e7eb; border-top:0; }
@media (max-width:760px) { .order-editor-layout { grid-template-columns:1fr; grid-template-rows:auto minmax(0,1fr); } .order-remark { grid-column:auto; } .order-body { padding:14px; } }
.order-table { overflow:auto; margin-bottom:12px; }
.line-batch-toolbar { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:10px 16px; margin-bottom:10px; padding:10px 12px; border:1px solid #e8edf5; border-radius:6px; background:#fafbfe; }
.line-filters,.line-batch-actions { display:flex; flex-wrap:wrap; align-items:center; gap:8px; }
.line-filters > span,.line-batch-actions > span { color:#4a5970; font-size:13px; }
.line-filters :deep(.ant-select) { width:140px; }
.line-filters small { color:#8998ad; }
.line-batch-actions strong { color:#1e5eff; }
.line-batch-actions :deep(.ant-input-number) { width:110px; }
.order-table th.line-check,.order-table td.line-check { width:44px; min-width:44px; text-align:center; }
.order-table table { width:100%; border-collapse:collapse; }
.order-table th,.order-table td { padding:7px 10px; border:1px solid #e5e7eb; text-align:left; }
.order-table th { background:#f2f5fa; }
.order-table tr:nth-child(even) { background:#f7f9fc; }
.order-batch { padding:12px; border:1px solid #e5e7eb; margin-bottom:10px; border-radius:6px; }
.style-choice { display:block; width:100%; margin:12px 0; }
.style-picker { display:grid; grid-template-columns:210px 300px minmax(0,1fr); height:min(620px,calc(100dvh - 180px)); min-height:280px; }
.style-category-pane { min-height:0; overflow:auto; padding:18px 14px; border-right:1px solid #e5e7eb; }
.style-list-pane { display:flex; flex-direction:column; gap:12px; min-width:0; min-height:0; padding:18px 14px; border-right:1px solid #e5e7eb; background:#fafbfe; }
.style-picker-content { display:flex; flex-direction:column; gap:10px; min-width:0; min-height:0; padding:18px; }
.picker-pane-title { font-size:14px; font-weight:600; color:#263854; }
.picker-pane-title > span { color:#98a5b5; font-size:12px; font-weight:400; }
.picker-hint { margin:5px 0 12px; color:#8a98aa; font-size:12px; }
.style-results { display:flex; flex:1; min-height:0; flex-direction:column; gap:6px; overflow:auto; }
.style-result { display:flex; justify-content:space-between; align-items:center; gap:8px; width:100%; padding:9px 11px; text-align:left; background:#fff; border:1px solid #e5e7eb; border-radius:6px; cursor:pointer; }
.style-result.active { border-color:#1e5eff; background:#f0f5ff; }
.style-result span { display:flex; flex-direction:column; gap:3px; }
.style-result small { color:#86909c; }
.spec-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.spec-heading .picker-hint { margin-bottom:0; }
.selection-count { color:#3971c5; font-size:12px; white-space:nowrap; }
.spec-actions { display:flex; align-items:center; flex-wrap:wrap; gap:4px; padding:4px 0; border-bottom:1px solid #edf0f5; }
.spec-actions :deep(.ant-btn) { padding-inline:5px; }
.spec-actions span,.already-added { color:#98a5b5; font-size:12px; }
.already-added { margin-left:8px; }
.spec-scroll { flex:1; min-height:0; overflow:auto; padding:2px 2px 12px; }
.style-spec-options,.set-spec-options { display:grid; gap:12px; width:100%; }
.spec-color-group { padding:12px; border:1px solid #e5eaf2; border-radius:8px; }
.spec-color-title { margin-bottom:10px; color:#344660; font-weight:600; }
.spec-color-title span { margin-left:6px; color:#98a5b5; font-size:12px; font-weight:400; }
.spec-size-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(110px,1fr)); gap:8px; }
.spec-size { display:flex; justify-content:space-between; align-items:center; gap:4px; min-width:0; padding:8px; border:1px solid #e5eaf2; border-radius:6px; cursor:pointer; }
.spec-size small { color:#98a5b5; white-space:nowrap; }
.set-spec-option { border:1px solid #e5eaf2; border-radius:8px; padding:12px; }
.spec-size.chosen,.set-spec-option.chosen { border-color:#8db2f2; background:#f3f7ff; }
.spec-size.unavailable,.set-spec-option.unavailable { background:#f6f7f9; color:#a9b2bf; cursor:not-allowed; }
.set-child-list { margin:4px 0 0; padding-left:20px; line-height:1.7; }
@media(max-width:900px) { .style-picker { grid-template-columns:155px 210px minmax(0,1fr); } }
@media(max-width:650px) { .style-picker { grid-template-columns:1fr 1fr; grid-template-rows:minmax(100px,35%) minmax(0,1fr); } .style-picker-content { grid-column:1 / -1; border-top:1px solid #e5e7eb; padding:12px; } .style-category-pane,.style-list-pane { padding:10px; } }
</style>
