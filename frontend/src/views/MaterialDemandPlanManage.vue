<!--
文件用途：查看和维护订单派生物料需求计划，支持符合状态约束的人工调整与审批。
文件版本：1.0.0
功能目的：查看和维护订单派生物料需求计划，支持符合状态约束的人工调整与审批。
设计思路：计划是订单算料快照的可追溯派生物；调整独立记录，防止重新算料覆盖已确认的人工作业。
实现方法：查询计划和来源订单，按状态控制编辑/送审入口，通过计划接口保存修订并刷新结果。
关键约束：只有草稿可编辑；已提交或已审核计划不能被订单重新算料静默覆盖。
依赖与引用：Docs/规划/系统与流程/生产流程规划.md；sql/物料需求计划_20261117.sql
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
/** 管理客户订单审核后生成的物料需求计划。 / Manage material demand plans generated after customer order approval. */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import { can } from '../utils/perms'

import { displayDecimal, defaultPrecision, readPrecision, precisionKey, sumDecimal } from '../utils/decimalDisplay'
const showRaw = ref(false)
const displayRules = ref(defaultPrecision)
const base='/order/material-demand-plans'
const route=useRoute(),router=useRouter()
const permission=(action:string)=>can(`order:material-demand-plan:${action}`)
const statusLabels:Record<string,string>={draft:'草稿',submitted:'审批中',approved:'已审核',cancelled:'已取消'}
const calculationLabels:Record<string,string>={pending:'待算料',ready:'已完成',incomplete:'需核对',outdated:'来源已更新'}
const supplyLabels:Record<string,string>={customer:'客供',purchase:'自采',mixed:'客供＋自采'}
const columns=[
  {key:'planNo',title:'需求计划单号',width:190}, {key:'sourceOrderNo',title:'来源订单',width:170},
  {key:'customerName',title:'客户',width:150}, {key:'poNumbers',title:'客户 PO',width:180},
  {key:'lineCount',title:'物料需求行',width:115,numeric:true}, {key:'materialCount',title:'物料种类',width:105,numeric:true},
  {key:'calculationStatusLabel',title:'算料状态',width:110}, {key:'statusLabel',title:'单据状态',width:110},
  {key:'createdByName',title:'制单人',width:120}, {key:'createdAt',title:'制单时间',width:175}, {key:'updatedAt',title:'修改时间',width:175},
]
const itemColumns=[
  {key:'poNumber',title:'客户 PO',width:130}, {key:'sourceStyleCode',title:'款号',width:125},
  {key:'childStyleCode',title:'子款号',width:120}, {key:'colorName',title:'款式颜色',width:100}, {key:'sizeName',title:'尺码',width:80},
  {key:'orderQuantity',title:'订单数量',width:100,numeric:true}, {key:'materialCode',title:'物料编码',width:120},
  {key:'materialName',title:'物料名称',width:140}, {key:'categoryName',title:'物料分类',width:120},
  {key:'materialSpec',title:'物料规格',width:130}, {key:'materialColorName',title:'物料颜色',width:100},
  {key:'supplyModeLabel',title:'供料方式',width:110}, {key:'usageRaw',title:'BOM 单耗',width:105,numeric:true}, {key:'originalQuantity',title:'原始需求量',width:125,numeric:true},
  {key:'plannedQuantity',title:'计划需求量',width:150,numeric:true}, {key:'purchaseUnitText',title:'采购单位参考',width:200},
  {key:'bomVersion',title:'BOM版本',width:100}, {key:'poDueDate',title:'PO默认交期',width:120},
]
const listPage = ref(1), listPageSize = ref(20), listTotal = ref(0)
/** 切换每页条数后从第一页重新查询。 / Reload from page one when the page size changes. */
function changeListPage(page: number, size: number) {
  listPage.value = size === listPageSize.value ? page : 1
  listPageSize.value = size
  void load()
}
const rows=ref<any[]>([]),loading=ref(false),busy=ref(false), form=ref<any>(), tableKeyword=ref(''),tableStatus=ref<string>(),tableCalcStatus=ref<string>(),tablePo=ref('')
const applied=ref({keyword:'',status:undefined as string|undefined,calculationStatus:undefined as string|undefined,poNumber:''})
const sources=ref<any[]>([]),sourceOpen=ref(false),sourceKeyword=ref(''),sourceLoading=ref(false),activeSummary=ref<string[]>([])
const itemKeyword=ref(''),itemPo=ref<string>(),itemSupply=ref<string>(),adjustmentReason=ref(''),viewMode=ref<'summary'|'detail'>('summary')
const reasonOpen=ref(false),reasonText=ref(''),pendingAction=ref<{action:string;label:string}>()
const statusOptions=Object.entries(statusLabels).map(([value,label])=>({value,label}))
const calculationOptions=Object.entries(calculationLabels).map(([value,label])=>({value,label}))
const editable=computed(()=>!!form.value && form.value.status==='draft' && permission('edit'))
const hasChanges=computed(()=>!!form.value?.items?.some((item:any)=>String(item.plannedQuantity)!==item._savedQuantity))
const listRows=computed(()=>rows.value.map(row=>({...row,
  statusLabel:statusLabels[row.status]||row.status,calculationStatusLabel:calculationLabels[row.calculationStatus]||row.calculationStatus,
  createdAt:formatTime(row.createdAt),updatedAt:formatTime(row.updatedAt),
})))
const detailRows=computed(()=>{
  const items=form.value?.items||[]
  return items.filter((item:any)=>(!itemKeyword.value||[item.materialCode,item.materialName,item.materialSpec,item.categoryName,item.sourceStyleCode,item.childStyleCode].some((value:any)=>String(value||'').toLowerCase().includes(itemKeyword.value.trim().toLowerCase())))
    &&(!itemPo.value||item.poNumber===itemPo.value)&&(!itemSupply.value||item.supplyMode===itemSupply.value))
    .map((item:any)=>({...item,id:item.itemId,supplyModeLabel:supplyLabels[item.supplyMode]||item.supplyMode,purchaseUnitText:purchaseText(item.purchaseUnits)}))
})
// 汇总视图从当前明细取回原始十进制文本，再精确求和，避免浮点格式化改变计划数量。
// Build the summary from raw detail decimals so aggregation never rounds through floating point.
const summaryRows=computed(()=>{
  const byId = new Map<string,any>((form.value?.items || []).map((item:any)=>[item.itemId,item]))
  return (form.value?.summary||[]).map((group:any)=>{
    const sources=(group.sources||[]).map((item:any)=>byId.get(item.itemId) || item).filter((item:any)=>(!itemKeyword.value||[item.materialCode,item.materialName,item.materialSpec,item.categoryName].some((value:any)=>String(value||'').toLowerCase().includes(itemKeyword.value.trim().toLowerCase())))
      &&(!itemPo.value||item.poNumber===itemPo.value)&&(!itemSupply.value||item.supplyMode===itemSupply.value))
    return {...group,sources,unitPlaces:sources[0]?.unitPlaces,originalQuantity:sumDecimal(sources.map((item:any)=>item.originalQuantity)),plannedQuantity:sumDecimal(sources.map((item:any)=>item.plannedQuantity))}
      }).filter((row:any)=>row.sources.length)
})
const poOptions=computed(()=>[...new Set<string>((form.value?.items||[]).map((item:any)=>String(item.poNumber||'')).filter(Boolean))].map(value=>({value,label:value})))
const supplyOptions=computed(()=>[...new Set<string>((form.value?.items||[]).map((item:any)=>String(item.supplyMode||'')).filter(Boolean))].map(value=>({value,label:supplyLabels[value]||value})))
const directReview=computed(()=>form.value?.status==='submitted'&&!form.value?.workflowInstanceId&&permission('review'))

/** 统一检查 API 响应并保留用户的当前编辑上下文。 / Normalize API response handling while retaining the active editing context. */
async function call(method:'get'|'post'|'put',path:string,payload?:any,params?:any){const result:any=method==='get'?await api.get(base+path,{params}):method==='put'?await api.put(base+path,payload):await api.post(base+path,payload);if(result.code!==200)throw Error(result.msg||'操作失败');return result.data}
/** 将时间戳格式化为本地中文时间，无法解析时保留服务端文本。 / Format timestamps locally and preserve unparseable server values. */
function formatTime(value:any){if(!value)return '—';const date=new Date(value);return Number.isNaN(date.getTime())?String(value):date.toLocaleString('zh-CN',{hour12:false})}
/** 只格式化只读数量；单位精度缺失时保留原值，并允许用户查看未舍入值。 / Format read-only quantities while preserving raw values when unit precision is unavailable. */
function quantity(value:unknown, places?:number){return displayDecimal(value, places ?? 8, showRaw.value || places == null)}
/** 展示采购单位参考量；变动换算无默认系数时明确标为待实际换算。 / Show purchase-unit references and avoid inventing variable conversions. */
function purchaseText(units:any[]){if(!units?.length)return '—';return units.map(unit=>unit.quantity==null?`${unit.unitName}：待实际换算`:`${unit.approximate?'约 ':''}${quantity(unit.quantity,unit.unitPlaces)} ${unit.unitName}`).join('；')}

/** 按功能模板筛选列表并显示所有匹配的当前用户可见计划。 / Apply template filters to the current visible plan set. */
async function load(){loading.value=true;try{const data=await call('get','/list',undefined,{...applied.value,pageNum:listPage.value,pageSize:listPageSize.value});rows.value=data.rows;listTotal.value=data.total}catch(error:any){message.error(error?.response?.data?.msg||error.message||'加载需求计划失败')}finally{loading.value=false}}
/** 应用当前筛选条件并重置到第一页，避免筛选后停留在无效页码。 / Apply current filters from the first page. */
function query(){listPage.value=1;applied.value={keyword:tableKeyword.value.trim(),status:tableStatus.value,calculationStatus:tableCalcStatus.value,poNumber:tablePo.value.trim()};void load()}
/** 清除列表筛选后重新查询。 / Clear all list filters and reload the result set. */
function reset(){tableKeyword.value='';tableStatus.value=undefined;tableCalcStatus.value=undefined;tablePo.value='';query()}

/** 从列表或深链打开计划；显示参数读取失败时回退默认值，不阻断单据查看。 / Open a listed or deep-linked plan without making it depend on the display-settings service. */
async function open(row:any){
  busy.value=true
  try{
    const [data,config]=await Promise.all([
      call('get','/'+row.id),
      api.get('/system/config/configKey/'+precisionKey).catch(()=>undefined)
    ])
    displayRules.value=readPrecision(config?.data)
    showRaw.value=false
    form.value=prepare(data)
    viewMode.value='summary'
    itemKeyword.value=''
    itemPo.value=undefined
    itemSupply.value=undefined
    adjustmentReason.value=''
    activeSummary.value=[]
    await router.replace({query:{...route.query,planId:String(row.id)}})
  }catch(error:any){
    message.error(error?.response?.data?.msg||error.message||'读取需求计划失败')
  }finally{
    busy.value=false
  }
}
/** 把服务端十进制原值规范为字符串，并保存基线供编辑差异判断。 / Preserve exact decimal strings and record the baseline used for change detection. */
function prepare(data:any){const copy=structuredClone(data);copy.items=(copy.items||[]).map((item:any)=>({...item,originalQuantity:item.originalRaw ?? String(item.originalQuantity),plannedQuantity:item.plannedRaw ?? String(item.plannedQuantity),_savedQuantity:item.plannedRaw ?? String(item.plannedQuantity)}));return copy}
/** 关闭详情前保护未保存数量调整，确认后移除路由中的计划定位。 / Protect unsaved quantity edits before leaving the detail view. */
function close(){if(hasChanges.value){Modal.confirm({title:'放弃未保存的计划调整？',content:'当前修改的计划需求量尚未保存。',okText:'放弃修改',cancelText:'继续编辑',onOk:()=>{form.value=undefined;router.replace({query:{}})}});return}form.value=undefined;void router.replace({query:{}})}

/** 打开可补生成计划的已审核订单选择器并初始化关键词。 / Open the approved-order selector with a clean search field. */
async function openSources(){sourceOpen.value=true;sourceKeyword.value='';await loadSources()}
/** 根据关键词加载可补生成物料需求计划的已审核订单。 / Load approved source orders eligible for plan generation. */
async function loadSources(){sourceLoading.value=true;try{sources.value=await call('get','/eligible-orders',undefined,{keyword:sourceKeyword.value.trim()})||[]}catch(error:any){message.error(error?.response?.data?.msg||error.message||'查询已审核订单失败')}finally{sourceLoading.value=false}}
/** 由已审核订单幂等生成草稿计划，并打开新计划详情。 / Generate or reuse the draft for an approved order and open it. */
async function generate(row:any){busy.value=true;try{const result=await call('post','/generate/'+row.orderId,{});sourceOpen.value=false;message.success('已生成物料需求计划草稿');form.value=prepare(result);await load();await router.replace({query:{planId:String(result.id)}})}catch(error:any){message.error(error?.response?.data?.msg||error.message||'生成计划失败')}finally{busy.value=false}}

/** 保存草稿数量变更；计划人员调整需求时必须说明业务原因。 / Save draft quantity edits and require a business reason for every changed quantity. */
async function saveQuantities(){if(!form.value)return;if(!hasChanges.value){message.info('计划数量没有变化');return}if(!adjustmentReason.value.trim()){message.warning('请填写计划数量调整原因');return}busy.value=true;try{const payload={revision:form.value.revision,reason:adjustmentReason.value.trim(),items:form.value.items.filter((item:any)=>String(item.plannedQuantity)!==item._savedQuantity).map((item:any)=>({itemId:item.itemId,plannedQuantity:item.plannedQuantity}))};form.value=prepare(await call('put','/'+form.value.id+'/quantities',payload));adjustmentReason.value='';message.success('计划数量和调整记录已保存');await load()}catch(error:any){message.error(error?.response?.data?.msg||error.message||'保存计划失败')}finally{busy.value=false}}

/** 执行单据状态操作并在成功后刷新列表和详情。 / Run a plan lifecycle action and reload the latest document after success. */
function transition(action:string,label:string){if(!form.value)return;if(['cancel','return','request_unapprove'].includes(action)){pendingAction.value={action,label};reasonText.value='';reasonOpen.value=true;return}void runTransition(action,label,'')}
async function runTransition(action:string,label:string,reason:string){if(!form.value)return;busy.value=true;try{form.value=prepare(await call('post','/'+form.value.id+'/transition',{revision:form.value.revision,action,reason}));message.success(label+'成功');await load()}catch(error:any){message.error(error?.response?.data?.msg||error.message||label+'失败')}finally{busy.value=false}}
/** 提交需要说明原因的单据动作；空原因时不执行状态变更。 / Confirm a reason-required action only after a nonblank explanation is supplied. */
function confirmReason(){if(!reasonText.value.trim()){message.warning('请填写操作原因');return}const action=pendingAction.value;if(!action)return;reasonOpen.value=false;void runTransition(action.action,action.label,reasonText.value.trim())}

/** 从计划详情穿透到对应客户订单。 / Navigate from a plan to its linked customer order. */
function openOrder(){if(!form.value?.orderId)return;router.push({name:'CustomerOrderManage',query:{orderId:form.value.orderId}})}
/** 展开或折叠某一物料汇总组的订单明细。 / Toggle the source-line expansion for one material group. */
function toggleSummary(key:string,open:boolean){const set=new Set(activeSummary.value);open?set.add(key):set.delete(key);activeSummary.value=[...set]}

onMounted(async()=>{await load();const id=String(route.query.planId||'');if(/^\d+$/.test(id))await open({id})})
</script>

<template>
  <StandardListLayout v-if="!form">
    <template #filters>
      <label class="zy-filter-field"><span>计划单号 / 订单号 / 客户</span><a-input v-model:value="tableKeyword" allow-clear placeholder="请输入计划单号、来源订单或客户" @press-enter="query" /></label>
      <label class="zy-filter-field"><span>客户 PO</span><a-input v-model:value="tablePo" allow-clear placeholder="筛选 PO" @press-enter="query" /></label>
      <label class="zy-filter-field"><span>算料状态</span><a-select v-model:value="tableCalcStatus" allow-clear placeholder="全部状态" style="width:145px" :options="calculationOptions" /></label>
      <label class="zy-filter-field"><span>单据状态</span><a-select v-model:value="tableStatus" allow-clear placeholder="全部状态" style="width:130px" :options="statusOptions" /></label>
    </template>
    <template #actions><a-tooltip title="列表仅展示本账号有权查看的计划。若找不到已知单据，请联系管理员核对物料分类权限。"><a-button type="link">可见范围说明</a-button></a-tooltip><a-button v-if="permission('edit')" type="primary" @click="openSources">从已审核订单生成</a-button><a-button type="primary" ghost @click="query">查询</a-button><a-button @click="reset">重置</a-button></template>
    <FoundationTable storage-key="material_demand_plan_list" :server-total="listTotal" :server-page="listPage" :server-page-size="listPageSize" @page-change="changeListPage" :columns="columns" :rows="listRows" :loading="loading" :action-width="110">
      <template #cell="{column,row}"><a v-if="column.key==='planNo'" @click="open(row)">{{ row.planNo }}</a><a v-else-if="column.key==='sourceOrderNo'" @click="router.push({name:'CustomerOrderManage',query:{orderId:row.orderId}})">{{ row.sourceOrderNo }}</a><a-tag v-else-if="column.key==='statusLabel'" :color="row.status==='approved'?'green':row.status==='submitted'?'blue':row.status==='cancelled'?'default':'orange'">{{ row.statusLabel }}</a-tag><a-tag v-else-if="column.key==='calculationStatusLabel'" :color="row.calculationStatus==='ready'?'green':row.calculationStatus==='incomplete'||row.calculationStatus==='outdated'?'orange':'default'">{{ row.calculationStatusLabel }}</a-tag><span v-else>{{ row[column.key] ?? '—' }}</span></template>
      <template #action="{row}"><a-button type="link" size="small" @click="open(row)">{{ row.status==='draft'&&permission('edit')?'编辑':'查看' }}</a-button></template>
    </FoundationTable>
  </StandardListLayout>

  <div v-else class="mrp-editor">
    <header class="mrp-toolbar">
      <a-space wrap><a-button @click="close">返回列表</a-button><div class="toolbar-title"><strong>物料需求计划</strong><span>{{ form.planNo }}</span></div><a-tag :color="form.status==='approved'?'green':form.status==='submitted'?'blue':form.status==='cancelled'?'default':'orange'">{{ statusLabels[form.status]||form.status }}</a-tag><a-tag :color="form.calculationStatus==='ready'?'green':'orange'">{{ calculationLabels[form.calculationStatus]||form.calculationStatus }}</a-tag><a-tag v-if="form.reverseApprovalPending" color="processing">反审核审批中</a-tag><router-link v-if="form.reverseApprovalPending && form.reverseApproval?.instanceId" :to="{name:'ApprovalInitiated',query:{instanceId:form.reverseApproval.instanceId}}">查看反审核进度</router-link></a-space>
      <a-space wrap>
        <a-button v-if="editable&&hasChanges" :loading="busy" @click="saveQuantities">保存计划调整</a-button>
        <a-button v-if="editable" type="primary" :disabled="hasChanges" :loading="busy" @click="transition('submit','提交审批')">提交审批</a-button>
        <a-button v-if="form.status==='submitted'&&!form.workflowInstanceId&&permission('edit')" :loading="busy" @click="transition('unsubmit','撤回提交')">撤回</a-button>
        <template v-if="directReview"><a-button danger :loading="busy" @click="transition('return','退回')">退回</a-button><a-button type="primary" :loading="busy" @click="transition('approve','审核通过')">审核通过</a-button></template>
        <a-button v-if="editable" danger ghost :loading="busy" @click="transition('cancel','取消计划')">取消计划</a-button>
        <a-button v-if="form.status==='approved' && permission('edit') && !form.reverseApprovalPending" danger :loading="busy" @click="transition('request_unapprove','申请反审核')">申请反审核</a-button>
      </a-space>
    </header>
    <div class="mrp-content">
      <section class="mrp-summary-head">
        <div class="mrp-doc-title"><div><span class="eyebrow">需求计划单号</span><strong>{{ form.planNo }}</strong></div><a-button type="link" @click="openOrder">查看来源订单 {{ form.sourceOrderNo }}</a-button></div>
        <div class="mrp-doc-fields">
          <div class="doc-field"><span>客户</span><strong>{{ form.document?.customerName||'—' }}</strong></div>
          <div class="doc-field"><span>订单日期</span><strong>{{ form.document?.date||'—' }}</strong></div>
          <div class="doc-field"><span>归属公司</span><strong>{{ form.document?.companyName||'—' }}</strong></div>
          <div class="doc-field"><span>订单修订</span><strong>R{{ form.orderRevision }}</strong></div>
          <div class="doc-field"><span>客户 PO</span><strong>{{ (form.document?.pos||[]).length }} 个</strong></div>
          <div class="doc-field"><span>计划制单人</span><strong>{{ form.createdByName||'—' }}</strong></div>
          <div class="doc-field"><span>创建时间</span><strong>{{ formatTime(form.createdAt) }}</strong></div>
          <div class="doc-field"><span>修改时间</span><strong>{{ formatTime(form.updatedAt) }}</strong></div>
        </div>
        <div class="po-strip"><span>PO 交付与供料</span><a-tag v-for="(po,index) in form.document?.pos||[]" :key="index">{{ po.number||'未命名 PO' }} · {{ po.due||'未设交期' }} · {{ supplyLabels[po.supply||form.document?.supply]||'待设置供料' }}</a-tag></div>
      </section>

      <a-alert v-if="form.calculationStatus!=='ready'" type="warning" show-icon class="mrp-alert">
        <template #message>当前计划没有完整的订单算料明细，不能提交审批。</template>
        <template #description><span v-if="form.calculationMessage">{{ form.calculationMessage }}。 </span>请打开来源客户订单，按已发布 BOM 完成算料并保存。保存后本计划会自动同步；如本计划已有人工调整，需先取消旧计划再重新算料生成新计划。</template>
      </a-alert>

      <section class="mrp-detail-section">
        <div class="detail-heading"><div><h2>物料需求明细</h2><p>库存单位需求量为计划主数量；采购单位仅展示物料档案换算参考，按实际换算的单位需要收货时实测。</p></div>
          <a-space wrap><label class="detail-filter"><span>PO</span><a-select v-model:value="itemPo" allow-clear placeholder="全部 PO" :options="poOptions" /></label><label class="detail-filter"><span>供料方式</span><a-select v-model:value="itemSupply" allow-clear placeholder="全部" :options="supplyOptions" /></label><a-input v-model:value="itemKeyword" allow-clear placeholder="搜索物料、规格或款号" style="width:230px" /><a-checkbox v-model:checked="showRaw">查看原值</a-checkbox><a-radio-group v-model:value="viewMode" button-style="solid"><a-radio-button value="summary">按物料汇总</a-radio-button><a-radio-button value="detail">按订单色码明细</a-radio-button></a-radio-group></a-space>
        </div>
        <a-alert v-if="editable&&hasChanges" type="info" show-icon class="adjust-alert">
          <template #message>计划需求数量已修改，保存时必须说明原因。</template>
          <template #description><a-input v-model:value="adjustmentReason" :maxlength="500" allow-clear placeholder="填写本次计划数量调整原因" /></template>
        </a-alert>
        <a-empty v-if="!form.items?.length" description="暂无已保存的物料需求明细" />
        <template v-else-if="viewMode==='summary'">
          <div v-for="group in summaryRows" :key="group.key" class="summary-group">
            <button class="summary-toggle" type="button" @click="toggleSummary(group.key,!activeSummary.includes(group.key))"><span class="expand-mark">{{ activeSummary.includes(group.key)?'−':'+' }}</span><span class="summary-main"><strong>{{ group.materialCode }} · {{ group.materialName }}</strong><small>{{ group.categoryName||'未分类' }} · {{ group.materialSpec||'无规格' }} · {{ group.materialColorName||'未指定颜色' }}</small></span><span class="summary-meta"><a-tag>{{ supplyLabels[group.supplyMode]||group.supplyMode }}</a-tag><span>需求汇总 <b>{{ quantity(group.plannedQuantity,group.unitPlaces) }} {{ group.inventoryUnitName }}</b></span><small>原始 {{ quantity(group.originalQuantity,group.unitPlaces) }} {{ group.inventoryUnitName }} · {{ group.sources.length }} 行来源</small></span></button>
            <div v-if="activeSummary.includes(group.key)" class="summary-table-wrap"><a-table size="small" bordered :pagination="false" row-key="itemId" :data-source="group.sources" :scroll="{x:1100}"><a-table-column title="客户 PO" data-index="poNumber"/><a-table-column title="款号 / 子款" key="style"><template #default="{record}">{{ record.childStyleCode||record.sourceStyleCode }}<span v-if="record.childStyleName"> · {{ record.childStyleName }}</span></template></a-table-column><a-table-column title="款式颜色 / 尺码" key="spec"><template #default="{record}">{{ [record.colorName,record.sizeName].filter(Boolean).join(' / ')||'—' }}</template></a-table-column><a-table-column title="物料规格 / 颜色" key="materialSpec"><template #default="{record}">{{ [record.materialSpec,record.materialColorName].filter(Boolean).join(' / ')||'—' }}</template></a-table-column><a-table-column title="原始需求" key="original"><template #default="{record}">{{ quantity(record.originalQuantity,record.unitPlaces) }} {{ record.inventoryUnitName }}</template></a-table-column><a-table-column title="计划需求" key="planned"><template #default="{record}"><a-input-number v-if="editable" v-model:value="record.plannedQuantity" :min="0" string-mode :precision="8" style="width:145px"/><span v-else>{{ quantity(record.plannedQuantity,record.unitPlaces) }}</span> {{ record.inventoryUnitName }}</template></a-table-column><a-table-column title="采购单位参考" key="purchase"><template #default="{record}">{{ purchaseText(record.purchaseUnits) }}</template></a-table-column></a-table></div>
          </div>
          <a-empty v-if="!summaryRows.length" description="当前筛选条件下没有物料需求" />
        </template>
        <FoundationTable v-else storage-key="material_demand_plan_detail" :columns="itemColumns" :rows="detailRows" :loading="busy" :action-width="90">
          <template #cell="{column,row}"><a-tag v-if="column.key==='supplyModeLabel'">{{ row.supplyModeLabel }}</a-tag><span v-else-if="column.key==='usageRaw'">{{ quantity(row.usageRaw,displayRules.bomUsage) }}</span><span v-else-if="column.key==='originalQuantity'">{{ quantity(row.originalQuantity,row.unitPlaces) }} {{ row.inventoryUnitName }}</span><a-input-number v-else-if="column.key==='plannedQuantity'&&editable" v-model:value="form.items.find((item:any)=>item.itemId===row.itemId).plannedQuantity" :min="0" string-mode :precision="8" style="width:132px"/><span v-else-if="column.key==='plannedQuantity'">{{ quantity(row.plannedQuantity,row.unitPlaces) }} {{ row.inventoryUnitName }}</span><span v-else-if="column.key==='purchaseUnitText'">{{ row.purchaseUnitText }}</span><span v-else>{{ row[column.key]??'—' }}</span></template>
          <template #action="{row}"><a-tooltip v-if="row.adjustmentReason" :title="row.adjustmentReason"><span>有调整</span></a-tooltip><span v-else>—</span></template>
        </FoundationTable>
      </section>

      <a-collapse v-if="form.adjustments?.length" class="history-collapse"><a-collapse-panel key="history" :header="`数量调整记录（${form.adjustments.length}）`"><a-table size="small" bordered :pagination="{pageSize:10}" :data-source="form.adjustments" row-key="adjustmentId" :columns="[{title:'时间',dataIndex:'changedAt',key:'changedAt'},{title:'操作人',dataIndex:'changedByName',key:'changedByName'},{title:'客户 PO',dataIndex:'poNumber',key:'poNumber'},{title:'物料',dataIndex:'materialName',key:'materialName'},{title:'物料规格',dataIndex:'materialSpec',key:'materialSpec'},{title:'原计划量',dataIndex:'previousQuantity',key:'previousQuantity'},{title:'新计划量',dataIndex:'plannedQuantity',key:'plannedQuantity'},{title:'调整原因',dataIndex:'reason',key:'reason'}]" /></a-collapse-panel></a-collapse>
    </div>
  </div>

  <a-modal :open="sourceOpen" title="从已审核订单生成需求计划" :width="980" :footer="null" :mask-closable="false" @cancel="sourceOpen=false">
    <p class="source-hint">计划按订单自动生成；此处只用于补建部署前已审核的订单或漏建计划。每张订单只允许一张有效需求计划。</p>
    <div class="source-search"><a-input v-model:value="sourceKeyword" allow-clear placeholder="搜索客户订单号或客户" @press-enter="loadSources"/><a-button type="primary" :loading="sourceLoading" @click="loadSources">查询</a-button></div>
    <a-table size="small" bordered :loading="sourceLoading" :data-source="sources" row-key="orderId" :pagination="{pageSize:10,showTotal:(n:number)=>`共 ${n} 条`}" :columns="[{title:'客户订单',dataIndex:'orderNo',key:'orderNo'},{title:'客户',dataIndex:'customerName',key:'customerName'},{title:'订单日期',dataIndex:'orderDate',key:'orderDate'},{title:'PO 数量',dataIndex:'poCount',key:'poCount'},{title:'操作',key:'action',width:110}]"><template #bodyCell="{column,record}"><a-button v-if="column.key==='action'" type="link" size="small" @click="generate(record)">生成草稿</a-button><span v-else>{{ record[String(column.key || '')]??'—' }}</span></template><template #emptyText><a-empty description="没有待补建计划的已审核订单"/></template><template #title><div class="source-table-title"><span>已审核且未生成有效需求计划的订单</span></div></template></a-table>
    <div class="source-actions"><a-button @click="sourceOpen=false">关闭</a-button></div>
  </a-modal>
  <a-modal :open="reasonOpen" :title="pendingAction?.label || '填写操作原因'" :ok-text="pendingAction?.action==='request_unapprove'?'提交审批':'确认'" cancel-text="取消" :confirm-loading="busy" :mask-closable="false" @ok="confirmReason" @cancel="reasonOpen=false"><p v-if="pendingAction?.action==='request_unapprove'">反审核必须由独立审批流程批准。审批通过前计划保持已审核；通过后计划退回草稿，可继续调整或取消。申请原因会保留在反审核记录中。</p><p v-else>操作原因会随需求计划保留，供后续追溯。</p><a-textarea v-model:value="reasonText" :maxlength="500" :rows="4" show-count :placeholder="pendingAction?.action==='request_unapprove'?'请填写反审核原因':'请填写原因'" /></a-modal>
</template>

<style scoped>
.mrp-editor{height:100%;min-height:0;display:flex;flex-direction:column;background:#f6f8fb}.mrp-toolbar{flex:none;min-height:56px;padding:8px 16px;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#fff;border-bottom:1px solid #e8edf4}.toolbar-title{display:flex;align-items:baseline;gap:10px;margin-left:4px}.toolbar-title strong{font-size:16px;color:#172b4d}.toolbar-title span{font-size:13px;color:#526581;font-variant-numeric:tabular-nums}.mrp-content{flex:1;min-height:0;overflow:auto;padding:16px 18px 24px}.mrp-summary-head,.mrp-detail-section{background:#fff;border:1px solid #e6ebf2;border-radius:10px;padding:16px 18px}.mrp-doc-title{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:14px;border-bottom:1px solid #eef1f5}.mrp-doc-title>div{display:flex;align-items:center;gap:12px}.eyebrow,.doc-field span,.po-strip>span{font-size:12px;color:#8391a7}.mrp-doc-title strong{font-size:18px;color:#1b2b45;font-variant-numeric:tabular-nums}.mrp-doc-fields{display:grid;grid-template-columns:repeat(4,minmax(140px,1fr));gap:14px 26px;padding:16px 0}.doc-field{display:flex;flex-direction:column;gap:5px;min-width:0}.doc-field strong{font-weight:500;color:#253650;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.po-strip{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding-top:12px;border-top:1px solid #eef1f5}.mrp-alert,.adjust-alert{margin-top:12px}.mrp-detail-section{margin-top:12px;min-height:350px;display:flex;flex-direction:column}.detail-heading{display:flex;justify-content:space-between;align-items:flex-end;gap:14px;flex-wrap:wrap;margin-bottom:14px}.detail-heading h2{margin:0 0 4px;font-size:16px;color:#1b2b45}.detail-heading p{margin:0;color:#7b899d;font-size:12px}.detail-filter{display:inline-flex;align-items:center;gap:6px;color:#53657d;font-size:12px}.detail-filter :deep(.ant-select){width:145px}.summary-group{border:1px solid #e4e9f0;border-radius:8px;margin:8px 0;overflow:hidden}.summary-toggle{width:100%;padding:12px 14px;display:flex;align-items:center;gap:12px;border:0;background:#fff;text-align:left;cursor:pointer}.summary-toggle:hover{background:#f3f7ff}.expand-mark{width:22px;height:22px;border-radius:50%;display:grid;place-items:center;background:#edf3ff;color:#245bdb;font-weight:700}.summary-main{display:flex;flex-direction:column;gap:4px;min-width:200px;flex:1}.summary-main strong{color:#1d2e48}.summary-main small,.summary-meta small{color:#8491a4}.summary-meta{display:flex;align-items:flex-end;justify-content:flex-end;gap:12px;flex-wrap:wrap;color:#526581;font-size:12px}.summary-meta b{font-size:15px;color:#1c5bd6}.summary-table-wrap{padding:0 12px 12px}.history-collapse{margin-top:12px;background:#fff}.source-hint{color:#697b94}.source-search{display:flex;gap:8px;margin-bottom:12px}.source-search :deep(.ant-input){max-width:380px}.source-table-title{color:#51647d;font-weight:500}.source-actions{display:flex;justify-content:flex-end;margin-top:14px}
@media(max-width:960px){.mrp-doc-fields{grid-template-columns:repeat(2,minmax(130px,1fr))}.mrp-toolbar{align-items:flex-start;flex-direction:column}.summary-toggle{align-items:flex-start}.summary-meta{justify-content:flex-start}}
</style>
