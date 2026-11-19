<script setup lang="ts">
/** 框架流程监控中的全量待办和已办查询。 / Monitor workflow tasks across users. */
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import ApprovalTimeline from '../components/ApprovalTimeline.vue'

const mode = ref<'waiting'|'finished'>('waiting')
const keyword = ref(''), applicant = ref(''), rows = ref<any[]>([]), loading = ref(false), page = ref(1), size = ref(20), total = ref(0)
const selected = ref<any>(), detailOpen = ref(false), history = ref<any>(), assigneeId = ref<string>(), users = ref<any[]>([]), reassignOpen = ref(false), saving = ref(false)
const endpoint = computed(() => mode.value === 'waiting' ? '/workflow/task/pageByAllTaskWait' : '/workflow/task/pageByAllTaskFinish')
const columns = [
  {key:'businessCode',title:'单据编号',width:180},{key:'businessTitle',title:'单据标题',width:220},{key:'flowName',title:'流程名称',width:180},
  {key:'nodeName',title:'当前节点',width:145},{key:'createByName',title:'发起人',width:120},{key:'assigneeNames',title:'办理人',width:150},
  {key:'flowStatusName',title:'流程状态',width:110},{key:'createTime',title:'创建时间',width:180},
]

/** 查询框架全量任务列表。 / Query framework-wide pending or completed tasks. */
async function load() {
  loading.value=true
  try {
    const res:any=await api.get(endpoint.value,{params:{pageNum:page.value,pageSize:size.value,businessCode:keyword.value.trim()||undefined,businessTitle:keyword.value.trim()||undefined,flowName:keyword.value.trim()||undefined,createByName:applicant.value.trim()||undefined}})
    if(res.code!==200)throw new Error(res.msg||'查询失败')
    rows.value=(res.data?.rows||[]).map((row:any)=>({...row,id:String(row.id),businessId:String(row.businessId||'')}));total.value=Number(res.data?.total||0)
  }catch(error:any){message.error(error?.response?.data?.msg||error.message||'流程任务查询失败')}
  finally{loading.value=false}
}
function changeMode(value:'waiting'|'finished'){mode.value=value;page.value=1;void load()}
function search(){page.value=1;void load()}
function reset(){keyword.value='';applicant.value='';search()}
async function openDetail(row:any){selected.value=row;history.value=undefined;detailOpen.value=true;try{const r:any=await api.get('/workflow/instance/flowHisTaskList/'+encodeURIComponent(row.businessId));if(r.code!==200)throw new Error(r.msg||'读取流程轨迹失败');history.value=r.data}catch(error:any){message.error(error?.response?.data?.msg||error.message||'读取流程轨迹失败')}}
async function openReassign(row:any){selected.value=row;assigneeId.value=undefined;users.value=[];try{const r:any=await api.get('/system/user/list',{params:{pageNum:1,pageSize:200,status:'0'}});if(r.code!==200)throw new Error(r.msg||'人员列表读取失败');users.value=(r.data?.rows||[]).map((u:any)=>({value:String(u.userId),label:`${u.nickName||u.userName}（${u.userName||u.userId}）`}));reassignOpen.value=true}catch(error:any){message.error(error?.response?.data?.msg||error.message||'读取人员列表失败')}}
async function reassign(){if(!selected.value||!assigneeId.value)return message.warning('请选择新的办理人');saving.value=true;try{const r:any=await api.put('/workflow/task/updateAssignee/'+assigneeId.value,[selected.value.id]);if(r.code!==200)throw new Error(r.msg||'改派失败');message.success('办理人已调整');reassignOpen.value=false;await load()}catch(error:any){message.error(error?.response?.data?.msg||error.message||'改派失败')}finally{saving.value=false}}
onMounted(load)
</script>

<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>流程/单据</span><a-input v-model:value="keyword" allow-clear placeholder="流程名称、单据编号或标题" @press-enter="search" /></label><label class="zy-filter-field"><span>发起人</span><a-input v-model:value="applicant" allow-clear placeholder="输入发起人姓名" @press-enter="search" /></label><a-segmented :value="mode" :options="[{label:'全量待办',value:'waiting'},{label:'全量已办',value:'finished'}]" @change="value=>changeMode(value as 'waiting'|'finished')" /></template>
    <FoundationTable page-toolbar storage-key="workflow_task_monitor" :columns="columns" :rows="rows" :loading="loading" :server-total="total" :server-page="page" :server-page-size="size" :action-width="160" @page-change="(p,s)=>{page=p;size=s;load()}"><template #actions><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></template><template #cell="{column,row}"><a-tag v-if="column.key==='flowStatusName'" color="blue">{{row.flowStatusName||'—'}}</a-tag><span v-else>{{row[column.key]||'—'}}</span></template><template #action="{row}"><a-button type="link" size="small" @click="openDetail(row)">查看进度</a-button><a-button v-if="mode==='waiting'&&can('workflow:task:edit')" type="link" size="small" @click="openReassign(row)">改派</a-button></template></FoundationTable>
  </StandardListLayout>
  <a-drawer :open="detailOpen" :title="selected?.businessTitle||'审批任务进度'" width="680" @close="detailOpen=false"><a-descriptions v-if="selected" bordered size="small" :column="2"><a-descriptions-item label="单据编号">{{selected.businessCode||selected.businessId||'—'}}</a-descriptions-item><a-descriptions-item label="流程名称">{{selected.flowName||'—'}}</a-descriptions-item><a-descriptions-item label="当前节点">{{selected.nodeName||'—'}}</a-descriptions-item><a-descriptions-item label="发起人">{{selected.createByName||'—'}}</a-descriptions-item><a-descriptions-item label="办理人">{{selected.assigneeNames||'—'}}</a-descriptions-item><a-descriptions-item label="状态">{{selected.flowStatusName||'—'}}</a-descriptions-item></a-descriptions><a-divider orientation="left">审批记录</a-divider><a-spin v-if="!history" /><ApprovalTimeline v-else :items="history.list || []" /></a-drawer>
  <a-modal :open="reassignOpen" title="调整当前节点办理人" :confirm-loading="saving" :mask-closable="false" :keyboard="false" ok-text="确认改派" cancel-text="取消" @ok="reassign" @cancel="reassignOpen=false"><a-alert type="warning" show-icon message="改派会改变当前待办的处理人，请确认选中的流程单据和新办理人。" style="margin-bottom:16px"/><a-form-item label="新办理人" required><a-select v-model:value="assigneeId" show-search option-filter-prop="label" placeholder="选择人员" :options="users" /></a-form-item></a-modal>
</template>
<style scoped>.json-view{white-space:pre-wrap;overflow:auto;max-height:55vh;padding:12px;background:#f6f8fa;border:1px solid #e5e8ef;border-radius:6px;font-size:12px}</style>
