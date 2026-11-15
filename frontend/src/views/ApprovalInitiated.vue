<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import { useRouter } from 'vue-router'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
const rows=ref<any[]>([]), loading=ref(false), selected=ref<any>(), history=ref<any[]>([])
const router=useRouter()
const states:Record<string,string>={starting:'发起中',pending:'审批中',approved:'已通过',rejected:'已退回',cancelled:'已取消'}
const actionLabels:Record<string,string>={PASS:'通过',REJECT:'退回',NONE:'记录'}
function actionText(item:any) { if (item.nodeName==='开始') return '发起流程'; if (item.nodeName==='申请人' && item.action==='PASS') return '提交审批'; return actionLabels[item.action] || item.action || '记录' }
function formatTime(value:any) { if (!value) return '—'; const d=new Date(value); return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN',{hour12:false}) }
const keyword=ref(''), stateFilter=ref<string>(), typeFilter=ref<string>(), applied=ref({keyword:'',state:undefined as string|undefined,type:undefined as string|undefined})
const columns=[{title:'单据类型',key:'typeLabel',width:120},{title:'单据编号',key:'orderNo',width:180},{title:'审批状态',key:'stateLabel',width:120},{title:'当前节点',key:'currentNode',width:160},{title:'当前处理人',key:'currentAssignees',width:160},{title:'提交时间',key:'submittedAt',width:180},{title:'上次催审',key:'lastReminder',width:180}]
const filteredRows=computed(()=>rows.value.filter(row=>(!applied.value.keyword || String(row.orderNo||'').toLowerCase().includes(applied.value.keyword.toLowerCase())) && (!applied.value.state || row.state===applied.value.state) && (!applied.value.type || row.businessType===applied.value.type)).map(row=>({...row,id:row.instanceId,typeLabel:row.businessType==='customer_order'?'客户订单':row.businessType,stateLabel:states[row.state] || row.state,submittedAt:formatTime(row.createTime),lastReminder:formatTime(row.lastRemindedAt)})))
function query() { applied.value={keyword:keyword.value.trim(),state:stateFilter.value,type:typeFilter.value} }
function reset() { keyword.value='';stateFilter.value=undefined;typeFilter.value=undefined;query() }
function openDocument(row:any) { if(row.businessType==='customer_order') router.push({name:'CustomerOrderManage',query:{orderId:row.businessId}}); else message.warning('该单据暂不支持跳转') }
async function load() { loading.value=true; try { const r:any=await api.get('/approval/customer-orders/initiated'); if(r.code!==200) throw Error(r.msg); rows.value=r.data||[] } catch(e:any){message.error(e.message||'加载失败')} finally{loading.value=false} }
async function open(row:any) { try { const r:any=await api.get('/approval/customer-orders/initiated/'+row.instanceId); if(r.code!==200) throw Error(r.msg); history.value=r.data.history||[]; selected.value=row } catch(e:any){message.error(e.message||'加载失败')} }
async function remind(row:any) { try { const r:any=await api.post('/approval/customer-orders/initiated/'+row.instanceId+'/remind'); if(r.code!==200) throw Error(r.msg); message.success('已发送催审提醒'); await load() } catch(e:any){message.error(e?.response?.data?.msg || e.message || '催审失败')} }
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>单据编号</span><a-input v-model:value="keyword" allow-clear placeholder="搜索单据编号" @press-enter="query" /></label><label class="zy-filter-field"><span>单据类型</span><a-select v-model:value="typeFilter" allow-clear placeholder="全部类型" style="width:140px" :options="[{value:'customer_order',label:'客户订单'}]" /></label><label class="zy-filter-field"><span>审批状态</span><a-select v-model:value="stateFilter" allow-clear placeholder="全部状态" style="width:140px" :options="Object.entries(states).map(([value,label])=>({value,label}))" /></label></template>
    <FoundationTable page-toolbar storage-key="approval_initiated_list" :columns="columns" :rows="filteredRows" :loading="loading" :action-width="160">
      <template #actions><a-button type="primary" @click="query">查询</a-button><a-button @click="reset">重置</a-button></template>
      <template #cell="{column,row}"><a v-if="column.key==='orderNo'" @click="openDocument(row)">{{ row.orderNo }}</a><a-tag v-else-if="column.key==='stateLabel'" :color="row.state==='approved'?'green':row.state==='rejected'?'red':row.state==='pending'?'blue':'default'">{{ row.stateLabel }}</a-tag><span v-else>{{ row[column.key] || '—' }}</span></template>
      <template #action="{row}"><a-button type="link" size="small" @click="open(row)">查看进度</a-button><a-button v-if="row.state==='pending' && row.currentAssignees" type="link" size="small" @click="remind(row)">催审</a-button></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal :open="!!selected" title="审批进度" :width="680" :footer="null" @cancel="selected=undefined">
    <div v-if="selected" class="progress-summary"><div><span>单据编号</span><a @click="openDocument(selected)">{{ selected.orderNo }}</a></div><a-tag color="blue">{{ states[selected.state] || selected.state }}</a-tag></div>
    <div class="progress-content">
      <a-timeline v-if="history.length || selected?.state === 'pending'">
        <a-timeline-item v-for="(item,index) in history" :key="index" :color="item.action === 'REJECT' ? 'red' : 'green'">
          <div class="timeline-heading"><strong>{{ item.nodeName || '审批节点' }}</strong><a-tag :color="item.action === 'REJECT' ? 'red' : 'green'">{{ actionText(item) }}</a-tag></div>
          <div class="timeline-meta">{{ item.approver || '系统' }} <span>{{ formatTime(item.updateTime) }}</span></div>
          <div v-if="item.message" class="timeline-opinion"><span>审批意见</span>{{ item.message }}</div>
        </a-timeline-item>
        <a-timeline-item v-if="selected?.state === 'pending'" color="blue"><div class="timeline-heading"><strong>{{ selected.currentNode || '当前审批节点' }}</strong><a-tag color="blue">待处理</a-tag></div><div class="timeline-meta">等待 {{ selected.currentAssignees || '处理人待确定' }} 办理</div></a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无审批记录" />
    </div>
  </a-modal>
</template>
<style scoped>
.progress-summary { display:flex; align-items:center; justify-content:space-between; padding:12px 14px; background:#f5f7fb; border-radius:8px; margin-bottom:20px; }
.progress-summary span { color:#86909c; margin-right:10px; }.progress-summary strong { color:#1d2129; }
.progress-content { max-height:60vh; overflow:auto; padding:4px 6px; }
.timeline-heading { display:flex; align-items:center; gap:10px; margin-bottom:5px; }.timeline-meta { color:#86909c; }.timeline-meta span { margin-left:12px; }
.timeline-opinion { margin-top:8px; padding:9px 12px; background:#f7f8fa; border-radius:6px; white-space:pre-wrap; word-break:break-word; }.timeline-opinion span { display:block; color:#86909c; margin-bottom:3px; }
</style>
