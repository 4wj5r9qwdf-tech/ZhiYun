<script setup lang="ts">
/** 展示本人发起的业务审批、办理时间轴、抄送状态及催审入口。 / Show initiated business approvals, their timeline, copy status and reminder action. */
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import { useRoute, useRouter } from 'vue-router'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import ApprovalTimeline from '../components/ApprovalTimeline.vue'
import { can } from '../utils/perms'
const rows=ref<any[]>([]), loading=ref(false), selected=ref<any>(), history=ref<any[]>([]), copies=ref<any[]>([]), business=ref<any>()
const router=useRouter()
const route=useRoute()
const states:Record<string,string>={starting:'发起中',pending:'审批中',approved:'已通过',rejected:'已退回',cancelled:'已取消'}
/** 格式化审批时间，空值时返回一致的占位文本。 / Format approval timestamps and provide a consistent empty-value label. */
function formatTime(value:any) { if (!value) return '—'; const d=new Date(value); return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN',{hour12:false}) }
const keyword=ref(''), stateFilter=ref<string>(), typeFilter=ref<string>(), applied=ref({keyword:'',state:undefined as string|undefined,type:undefined as string|undefined})
const columns=[{title:'单据类型',key:'typeLabel',width:120},{title:'单据编号',key:'orderNo',width:180},{title:'审批状态',key:'stateLabel',width:120},{title:'当前节点',key:'currentNode',width:160},{title:'当前处理人',key:'currentAssignees',width:160},{title:'提交时间',key:'submittedAt',width:180},{title:'上次催审',key:'lastReminder',width:180}]
const businessTypes=[{value:'customer_order',label:'客户订单'},{value:'customer_order_unapprove',label:'客户订单反审核'},{value:'material_demand_plan',label:'物料需求计划'},{value:'material_demand_plan_unapprove',label:'物料需求计划反审核'}]
const typeNames=Object.fromEntries(businessTypes.map(item=>[item.value,item.label])) as Record<string,string>
const filteredRows=computed(()=>rows.value.filter(row=>(!applied.value.keyword || String(row.businessNo||row.orderNo||'').toLowerCase().includes(applied.value.keyword.toLowerCase())) && (!applied.value.state || row.state===applied.value.state) && (!applied.value.type || row.businessType===applied.value.type)).map(row=>({...row,id:row.instanceId,orderNo:row.businessNo||row.orderNo,typeLabel:typeNames[row.businessType]||row.businessType,stateLabel:states[row.state] || row.state,submittedAt:formatTime(row.createTime),lastReminder:formatTime(row.lastRemindedAt)})))
/** 按当前筛选条件重新查询我发起的审批。 / Reload initiated approvals using the current filters. */
function query() { applied.value={keyword:keyword.value.trim(),state:stateFilter.value,type:typeFilter.value} }
/** 清空筛选条件并恢复默认查询结果。 / Clear filters and restore the default initiated-approval query. */
function reset() { keyword.value='';stateFilter.value=undefined;typeFilter.value=undefined;query() }
/** 穿透打开审批关联的业务单据，无法穿透时显示原因。 / Open the linked business document and report when navigation is unavailable. */
function openDocument(row:any) { const type=String(row.businessType||'').replace('_unapprove','');if(type==='customer_order'){if(!can('order:customer-order:list')){message.warning('您没有客户订单查看权限，请联系管理员授权');return}void router.push({name:'CustomerOrderManage',query:{orderId:row.businessId}});return}if(type==='material_demand_plan'){if(!can('order:material-demand-plan:list')){message.warning('您没有物料需求计划查看权限，请联系管理员授权');return}void router.push({name:'MaterialDemandPlanManage',query:{planId:row.businessId}});return}message.warning('该单据暂不支持跳转') }
/** 读取我发起的审批列表及其当前办理进度。 / Load approvals initiated by the caller with current progress. */
async function load() { loading.value=true; try { const r:any=await api.get('/approval/customer-orders/initiated'); if(r.code!==200) throw Error(r.msg); rows.value=r.data||[] } catch(e:any){message.error(e.message||'加载失败')} finally{loading.value=false} }
/** 加载选中审批的时间轴和抄送状态。 / Load the selected approval timeline and carbon-copy status. */
async function open(row:any) { try { const r:any=await api.get('/approval/customer-orders/initiated/'+row.instanceId); if(r.code!==200) throw Error(r.msg); history.value=r.data.history||[]; copies.value=r.data.copies||[]; business.value=r.data.business||{}; selected.value=row } catch(e:any){message.error(e.message||'加载失败')} }
/** 重新确认当前待办人后提交催审并更新记录。 / Revalidate current assignees before sending and recording a reminder. */
async function remind(row:any) { try { const r:any=await api.post('/approval/customer-orders/initiated/'+row.instanceId+'/remind'); if(r.code!==200) throw Error(r.msg); message.success('已发送催审提醒'); await load() } catch(e:any){message.error(e?.response?.data?.msg || e.message || '催审失败')} }
onMounted(async () => { await load(); const id=String(route.query.instanceId || ''); if (/^\d+$/.test(id)) { const row=rows.value.find(item=>String(item.instanceId)===id); if (row) await open(row); else message.warning('审批记录不存在或无权查看') } })
</script>
<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>单据编号</span><a-input v-model:value="keyword" allow-clear placeholder="搜索单据编号" @press-enter="query" /></label><label class="zy-filter-field"><span>单据类型</span><a-select v-model:value="typeFilter" allow-clear placeholder="全部类型" style="width:160px" :options="businessTypes" /></label><label class="zy-filter-field"><span>审批状态</span><a-select v-model:value="stateFilter" allow-clear placeholder="全部状态" style="width:140px" :options="Object.entries(states).map(([value,label])=>({value,label}))" /></label></template>
    <FoundationTable page-toolbar storage-key="approval_initiated_list" :columns="columns" :rows="filteredRows" :loading="loading" :action-width="160">
      <template #actions><a-button type="primary" @click="query">查询</a-button><a-button @click="reset">重置</a-button></template>
      <template #cell="{column,row}"><a v-if="column.key==='orderNo'" @click="openDocument(row)">{{ row.orderNo }}</a><a-tag v-else-if="column.key==='stateLabel'" :color="row.state==='approved'?'green':row.state==='rejected'?'red':row.state==='pending'?'blue':'default'">{{ row.stateLabel }}</a-tag><span v-else>{{ row[column.key] || '—' }}</span></template>
      <template #action="{row}"><a-button type="link" size="small" @click="open(row)">查看进度</a-button><a-button v-if="row.state==='pending' && row.currentAssignees" type="link" size="small" @click="remind(row)">催审</a-button></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal :open="!!selected" title="审批进度" :width="680" :footer="null" @cancel="selected=undefined">
    <div v-if="selected" class="progress-summary"><div><span>单据编号</span><a @click="openDocument(selected)">{{ selected.orderNo }}</a></div><a-tag color="blue">{{ states[selected.state] || selected.state }}</a-tag></div>
    <a-alert v-if="business?.reversal?.reason || selected?.reversalReason" type="warning" show-icon message="反审核申请原因" :description="business?.reversal?.reason || selected?.reversalReason" />
    <div class="progress-content">
      <ApprovalTimeline :items="history" :pending-node="selected?.state === 'pending' ? (selected.currentNode || '当前审批节点') : ''" :pending-assignees="selected?.currentAssignees" />
      <section class="copy-section"><h4>抄送记录</h4><p class="copy-hint">节点办理时生成抄送记录；当前不提供阅读状态。</p><div v-if="copies.length" class="copy-list"><div v-for="(copy,index) in copies" :key="`${copy.id}-${copy.userId}-${index}`" class="copy-row"><div><strong>{{ copy.userName }}</strong><span>{{ String(copy.nodeName || '审批节点').replace('【抄送】','') }}</span></div><span>已抄送 · {{ formatTime(copy.sentAt) }}</span></div></div><a-empty v-else :image="false" description="暂无抄送记录" /></section>
    </div>
  </a-modal>
</template>
<style scoped>
.progress-summary { display:flex; align-items:center; justify-content:space-between; padding:12px 14px; background:#f5f7fb; border-radius:8px; margin-bottom:20px; }
.progress-summary span { color:#86909c; margin-right:10px; }.progress-summary strong { color:#1d2129; }
.progress-content { max-height:60vh; overflow:auto; padding:4px 6px; }
.timeline-heading { display:flex; align-items:center; gap:10px; margin-bottom:5px; }.timeline-meta { color:#86909c; }.timeline-meta span { margin-left:12px; }
.timeline-opinion { margin-top:8px; padding:9px 12px; background:#f7f8fa; border-radius:6px; white-space:pre-wrap; word-break:break-word; }.timeline-opinion span { display:block; color:#86909c; margin-bottom:3px; }
.copy-section { margin-top:20px; border-top:1px solid #e5e8ef; padding-top:16px; }.copy-section h4 { margin:0 0 4px; }.copy-hint { color:#86909c; font-size:12px; margin:0 0 12px; }.copy-row { display:flex; justify-content:space-between; gap:12px; padding:9px 0; border-bottom:1px solid #f0f1f3; }.copy-row div span { color:#86909c; margin-left:10px; }.copy-row > span { color:#86909c; font-size:12px; }
</style>
