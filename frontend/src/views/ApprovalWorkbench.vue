<script setup lang="ts">
/** 提供审批待办、已办处理和流程配置入口，并复用业务单据详情。 / Provide approval tasks, completed work and workflow setup linked to business documents. */
/** 使用框架分页接口，流程配置和审批办理均以服务端权限校验为准。 */
import { computed, onMounted, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import { useRoute, useRouter } from 'vue-router'
const props = defineProps<{ kind: 'definition' | 'waiting' | 'finished' | 'copy' }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false), rows = ref<any[]>([]), total = ref(0), page = ref(1), pageSize = ref(20), keyword = ref('')
const definitionView = ref<'published'|'draft'>('published')
const definitions = computed(() => props.kind === 'definition')
const taskColumns = computed(() => [{key:'businessTypeLabel',title:'单据类型',width:120},{key:'businessCodeLabel',title:'单据编号',width:180},{key:'createByName',title:'发起人',width:120},{key:'flowName',title:'流程名称',width:160},{key:'nodeName',title:'审批节点',width:160},{key:'flowStatusName',title:'流程状态',width:120},{key:'createTime',title:'节点到达时间',width:180},...(props.kind==='finished'?[{key:'updateTime',title:'办理时间',width:180}]:[])])
function rowBusinessType(row:any){if(row.businessType==='customer_order'||row.businessType==='material_demand_plan')return row.businessType;const title=String(row.businessTitle||'');if(title.startsWith('客户订单 '))return 'customer_order';if(title.startsWith('物料需求计划 '))return 'material_demand_plan';return ''}
const businessTypeNames:Record<string,string>={customer_order:'客户订单',material_demand_plan:'物料需求计划'}
const taskRows = computed(() => rows.value.map(row => {const type=rowBusinessType(row),title=String(row.businessTitle||'');return {...row,businessType:type,businessTypeLabel:businessTypeNames[type]||'其他单据',businessCodeLabel:row.businessCode || (type ? title.slice(type==='customer_order'?5:7) : title) || '—'}}))
/** 从审批任务跳转到对应业务单据并处理无效关联。 / Navigate from an approval task to its business document and handle stale links. */
async function openDocument(row:any) { try { const type=rowBusinessType(row);const perm=type==='material_demand_plan'?'order:material-demand-plan:list':'order:customer-order:list';if(!can(perm)){message.warning(`您没有${businessTypeNames[type]||'该单据'}查看权限，请联系管理员授权`);return}const res:any=await api.get('/approval/customer-orders/' + (props.kind === 'finished' ? 'finished/' : props.kind === 'copy' ? 'copies/by-history/' : 'tasks/') + row.id);if(res.code!==200)throw Error(res.msg);if(type==='material_demand_plan'){const plan:any=await api.get('/order/material-demand-plans/'+res.data.businessId);if(plan.code!==200)throw Error(plan.msg||'需求计划不存在或超出访问范围');await router.push({name:'MaterialDemandPlanManage',query:{planId:res.data.businessId}});return}const document:any=await api.get('/order/customer-orders/'+res.data.businessId);if(document.code!==200)throw Error(document.msg || '订单不存在或超出访问范围');await router.push({name:'CustomerOrderManage',query:{orderId:res.data.businessId}}) } catch(e:any){message.error(e?.response?.data?.msg || e.message || '无法查看单据')} }
const columns = computed(() => definitions.value ? [
  { title: '流程名称', dataIndex: 'flowName', key: 'flowName' }, { title: '流程编码', dataIndex: 'flowCode', key: 'flowCode' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 100 }, { title: '发布状态', dataIndex: 'isPublish', key: 'isPublish', width: 120 },
  { title: '启用状态', dataIndex: 'activityStatus', key: 'activityStatus', width: 120 }, { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
  { title: '操作', key: 'definitionActions', width: 230 },
] : [
  { title: '单据', dataIndex: 'businessTitle', key: 'businessTitle' }, { title: '流程名称', dataIndex: 'flowName', key: 'flowName' }, { title: '节点名称', dataIndex: 'nodeName', key: 'nodeName' },
  { title: '流程编码', dataIndex: 'flowCode', key: 'flowCode' }, { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  ...(props.kind === 'waiting' ? [{title:'操作',key:'action',width:100}] : []),
])
const designerId = ref<string>()
const designerReadonly = ref(true)
const definitionImport = ref<HTMLInputElement>()
const definitionActions = [
  ['copy', '复制'], ['publish', '发布'], ['unPublish', '取消发布'],
  ['activate', '启用'], ['suspend', '挂起'], ['export', '导出'], ['remove', '删除'],
] as const

/** 通过框架原有权限控制流程维护入口。 / Use the framework's existing permissions for definition maintenance. */
function definitionActionVisible(action: string, row: any) {
  const published = Number(row.isPublish) === 1
  if (action === 'publish' || action === 'unPublish') return can('workflow:definition:publish') && (action === 'unPublish' ? published : Number(row.isPublish) === 0)
  if (action === 'activate' || action === 'suspend') return can('workflow:definition:active') && (action === 'suspend') === (Number(row.activityStatus) === 1)
  return can('workflow:definition:' + action) && (action !== 'remove' || !published)
}

/** 调用框架维护接口并更新列表。 / Invoke the framework maintenance endpoint and refresh the list. */
async function maintainDefinition(action: string, row: any) {
  const base = '/workflow/definition'
  try {
    let result: any
    if (action === 'export') {
      const blob: any = await api.post(base + '/exportDef/' + row.id, null, {responseType:'blob'})
      if (blob.type.includes('application/json')) {
        const body = JSON.parse(await blob.text())
        if ('code' in body && body.code !== 200) throw Error(body.msg)
      }
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = row.flowCode + '.json'; anchor.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      return
    }
    if (action === 'copy') result = await api.post(base + '/copy/' + row.id)
    else if (action === 'remove') result = await api.delete(base + '/' + row.id)
    else if (action === 'activate' || action === 'suspend') result = await api.put(base + '/active/' + row.id, null, {params:{active:action === 'activate'}})
    else result = await api.put(base + '/' + action + '/' + row.id)
    if (result.code !== 200 || result.data === false) throw Error(result.msg || '流程操作失败')
    if (action === 'copy' || action === 'unPublish') definitionView.value = 'draft'
    if (action === 'publish') definitionView.value = 'published'
    message.success('操作成功')
    await load()
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '流程操作失败') }
}

/** 导入框架生成的流程文件。 / Import a workflow definition file produced by the framework. */
async function importDefinition(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const form = new FormData()
    form.append('file', file)
    const result:any = await api.post('/workflow/definition/importDef', form)
    if (result.code !== 200 || result.data === false) throw Error(result.msg || '导入失败')
    definitionView.value = 'draft'
    message.success('导入成功')
    await load()
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '导入失败') }
}
const canSetup = computed(() => ['workflow:definition:add', 'workflow:definition:edit', 'workflow:definition:query'].every(key => can(key)))
const setupOpen = ref(false)
const setupBusy = ref(false)
const flowName = ref('')
const businessType = ref<string>()
const designerModel = ref<'CLASSICS' | 'MIMIC'>('CLASSICS')
/** 载入新增审批流程所需的审批人和流程配置数据。 / Load reference data for creating an approval workflow. */
async function openSetup() {
  businessType.value = undefined
  designerModel.value = 'CLASSICS'
  flowName.value = ''
  setupOpen.value = true
}
/** 创建可编辑的初始草稿并立即打开框架设计器。 / Create an editable draft and open the framework designer. */
async function publishSetup() {
  const name = flowName.value.trim()
  const userId = localStorage.getItem('userId')
  if (!businessType.value || !name) { message.warning('请选择单据功能并填写流程名称'); return }
  if (!userId || !/^\d+$/.test(userId)) { message.error('无法获取当前人员信息，请重新登录'); return }
  setupBusy.value = true
  try {
    const result:any = await api.post('/approval/customer-orders/setup', {
      businessType: businessType.value, name, draft:true, modelValue:designerModel.value,
      steps:[{name:'审批',assignee:userId,unanimous:false,copyUsers:[]}],
    })
    if (result.code !== 200 || !result.data?.id) throw new Error(result.msg || '创建流程失败')
    setupOpen.value = false
    definitionView.value = 'draft'
    designerReadonly.value = false
    designerId.value = String(result.data.id)
    await load()
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '创建流程失败') }
  finally { setupBusy.value = false }
}

const current = ref<any>()
const order = ref<any>()
const opinion = ref('')
const processing = ref(false)
const backOptions = ref<{value:string;label:string}[]>([])
const backNode = ref<string>()
const taskRules = ref<{code:string; show:boolean}[]>([])
const taskRatio = ref(0)
const runtimePeople = ref<{value:string; label:string}[]>([])
const runtimeUsers = ref<string[]>([])
const runtimeAction = ref('')
const defaultCopies = ref<{userId:string; nickName:string}[]>([])
const editCopies = ref(false)
const manualCopies = ref<{userId:string;nickName:string}[]>([])
const runtimeActions = [
  {value:'delegateTask',label:'委派',rule:'trust'},
  {value:'transferTask',label:'转办',rule:'transfer'},
  {value:'addSignature',label:'加签',rule:'addSign'},
  {value:'reductionSignature',label:'减签',rule:'subSign'},
  {value:'termination',label:'终止审批',rule:'termination'},
]

/** 按引擎节点配置显示操作，未配置时沿用框架默认值。 / Use node-specific actions and fall back to framework defaults. */
function taskActionEnabled(code: string) {
  return taskRules.value.length ? taskRules.value.some(rule => rule.code === code && rule.show) : ['back','copy','termination','file'].includes(code)
}

/** 查询当前任务可选人员，减签仅允许从节点的当前办理人中选择。 / Load task candidates and restrict removal to current assignees. */
async function chooseRuntimeAction(action: string) {
  if (!current.value) return
  try {
    runtimeUsers.value = []
    if (action !== 'termination') {
      const id = current.value.id
      const result:any = await api.get(action === 'reductionSignature'
        ? '/workflow/task/currentTaskAllUser/' + id : '/approval/customer-orders/tasks/' + id + '/people')
      if (result.code !== 200) throw Error(result.msg)
      runtimePeople.value = action === 'reductionSignature'
        ? result.data.map((user:any) => ({value:String(user.userId),label:user.nickName || String(user.userId)}))
        : result.data
      if (action === 'copy') runtimeUsers.value = (editCopies.value ? manualCopies.value : defaultCopies.value).map(user => String(user.userId))
    }
    runtimeAction.value = action
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '无法加载审批人员') }
}

/** 执行本次节点操作，抄送选择随通过请求提交。 / Execute the task action and submit manual copies with approval. */
async function submitRuntimeAction() {
  if (!current.value || processing.value) return
  const action = runtimeAction.value
  const users = runtimeUsers.value
  if (action === 'copy') {
    manualCopies.value = users.map(userId => ({userId,nickName:runtimePeople.value.find(user => user.value === userId)?.label.split(' · ')[0] || userId}))
    editCopies.value = true
    runtimeAction.value = ''
    return
  }
  if (action === 'termination' && !opinion.value.trim()) { message.warning('请在审批意见中填写终止原因'); return }
  if (action !== 'termination' && !users.length) { message.warning('请选择人员'); return }
  processing.value = true
  try {
    const taskId = current.value.id
    const note = opinion.value.trim()
    const result:any = action === 'termination'
      ? await api.post('/workflow/task/terminationTask', {taskId,comment:note})
      : await api.post('/workflow/task/taskOperation/' + action, {taskId,userId:users[0],userIds:users,message:note,messageType:['1']})
    if (result.code !== 200 || result.data === false) throw Error(result.msg || '办理失败')
    runtimeAction.value = ''
    current.value = undefined
    message.success('审批操作已提交')
    await load()
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '办理失败') }
  finally { processing.value = false }
}
/** 打开办理页面并加载审批单据及时间轴。 / Open the handling view with the business document and workflow timeline. */
async function openTask(row:any) {
  try {
    const result:any = await api.get('/approval/customer-orders/' + (props.kind === 'finished' ? 'finished/' : props.kind === 'copy' ? 'copies/by-history/' : 'tasks/') + row.id)
    if (result.code !== 200) throw new Error(result.msg)
    const nodes:any = props.kind === 'waiting' ? await api.get('/workflow/task/getBackTaskNode/' + row.id + '/' + encodeURIComponent(row.nodeCode || result.data.nodeCode)) : {code:200,data:[]}
    if (props.kind === 'waiting') {
      const task:any = await api.get('/workflow/task/getTask/' + row.id)
      if (task.code !== 200) throw Error(task.msg)
      taskRules.value = task.data?.buttonList || []
      taskRatio.value = Number(task.data?.nodeRatio || 0)
      defaultCopies.value = task.data?.copyList || []
    }
    editCopies.value = false
    runtimeUsers.value = []
    backOptions.value = nodes.code === 200 ? (nodes.data || []).map((n:any) => ({value:n.nodeCode,label:n.nodeName})) : []
    backNode.value = undefined
    opinion.value = ''
    order.value = result.data
    current.value = {...row,businessType:result.data.businessType||rowBusinessType(row)}
  } catch (error:any) { message.error(error?.response?.data?.msg || error.message || '审批单据加载失败') }
}
/** 提交通过或退回意见并刷新待办状态。 / Submit an approval or return decision and refresh task state. */
async function decide(back:boolean) {
  if (!current.value || processing.value) return
  if (back && (!backNode.value || !opinion.value.trim())) {
    message.warning('请选择退回节点并填写意见')
    return
  }
  processing.value = true
  try {
    const result:any = await api.post(back ? '/workflow/task/backProcess' : '/workflow/task/completeTask', {
      taskId: current.value.id, message: opinion.value.trim(), messageType:['1'],
      ...(back ? {nodeCode:backNode.value} : editCopies.value ? {flowCopyList:manualCopies.value} : {}),
    })
    if (result.code !== 200) throw new Error(result.msg)
    current.value = undefined
    message.success('审批意见已提交')
    await load()
  } catch (error:any) { message.error(error.message || '办理失败') }
  finally { processing.value = false }
}
/** 按待办、已办类型查询审批任务和单据摘要。 / Load approval tasks and document summaries for the selected inbox. */
async function load() {
  loading.value = true
  const url = definitions.value ? (definitionView.value === 'draft' ? '/workflow/definition/unPublishList' : '/workflow/definition/list') : props.kind === 'waiting' ? '/workflow/task/pageByTaskWait' : props.kind === 'copy' ? '/workflow/task/pageByTaskCopy' : '/workflow/task/pageByTaskFinish'
  try {
    const res: any = await api.get(url, { params: { pageNum: page.value, pageSize: pageSize.value, flowName: keyword.value.trim() || undefined } })
    if (res.code !== 200) throw new Error(res.msg || '加载失败')
    rows.value = res.data?.rows || []; total.value = Number(res.data?.total || 0)
  } catch (err: any) { message.error(err?.response?.data?.msg || err?.message || '加载失败') }
  finally { loading.value = false }
}
/** 应用当前关键字和状态筛选并重置页码。 / Apply current keyword and status filters from the first page. */
function query() { page.value = 1; void load() }
/** 处理分页参数变更并重新加载当前列表。 / Apply pagination changes and reload the current task list. */
function change(pagination: any) { page.value = pagination.current; pageSize.value = pagination.pageSize; void load() }
watch(() => props.kind, () => { page.value = 1; keyword.value = ''; void load() })
watch(() => route.query.taskId, value => { const taskId=String(value || ''); if (props.kind === 'waiting' && /^\d+$/.test(taskId)) void openTask({id:taskId}) }, {immediate:true})
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>流程名称</span><a-input v-model:value="keyword" allow-clear placeholder="请输入流程名称" @press-enter="query" /></label><label v-if="definitions" class="zy-filter-field"><span>发布状态</span><a-select v-model:value="definitionView" style="width:160px" :options="[{value:'published',label:'已发布'},{value:'draft',label:'草稿与失效版本'}]" @change="query" /></label></template>
    <FoundationTable v-if="!definitions" page-toolbar :storage-key="`approval_${kind}_list`" :columns="taskColumns" :rows="taskRows" :loading="loading" :action-width="120" :server-total="total" :server-page="page" :server-page-size="pageSize" @page-change="(p,s)=>{page=p;pageSize=s;load()}">
      <template #actions><a-button type="primary" @click="query">查询</a-button><a-button @click="keyword='';query()">重置</a-button></template>
      <template #cell="{column,row}"><a v-if="column.key==='businessCodeLabel' && row.businessType" @click="openDocument(row)">{{ row.businessCodeLabel }}</a><span v-else>{{ (column.key === 'flowStatusName' ? (row.flowStatusName || ({waiting:'审批中',finish:'已完成',termination:'已终止',invalid:'已作废'} as Record<string,string>)[row.flowStatus]) : row[column.key]) || '—' }}</span></template>
      <template #action="{row}"><a-button v-if="kind==='waiting' && row.businessType && can('approval:waiting:handle')" type="link" size="small" @click="openTask(row)">办理</a-button><a-button v-else-if="(kind==='finished' || kind==='copy') && row.businessType" type="link" size="small" @click="openTask(row)">查看进度</a-button><span v-else>—</span></template>
    </FoundationTable>
    <div v-else class="list">

      <a-button v-if="definitions && canSetup" type="primary" :loading="setupBusy" @click="openSetup">新增</a-button>
      <a-button v-if="can('workflow:definition:import')" @click="definitionImport?.click()">导入</a-button>
      <input ref="definitionImport" type="file" accept=".json" hidden @change="importDefinition" />
      <a-button type="primary" @click="query">查询</a-button><a-button @click="keyword='';query()">重置</a-button>

      <a-table row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="{current:page,pageSize,total,showSizeChanger:true,showTotal:(n:number)=>`共 ${n} 条`}" @change="change">
        <template #bodyCell="{ column, record }"><template v-if="column.key === 'definitionActions'">
          <a-space>
            <a-button v-if="can('workflow:definition:query')" type="link" size="small" @click="designerId=String(record.id);designerReadonly=!can('workflow:definition:edit') || Number(record.isPublish)!==0">{{ can('workflow:definition:edit') && Number(record.isPublish)===0 ? '设计' : '查看流程' }}</a-button>
            <a-dropdown v-if="definitionActions.some(([action]) => definitionActionVisible(action, record))">
              <a-button type="link" size="small">更多</a-button>
              <template #overlay><a-menu>
                <template v-for="[action,label] in definitionActions" :key="action">
                  <a-menu-item v-if="definitionActionVisible(action, record)">
                    <a-popconfirm v-if="['remove','unPublish','suspend'].includes(action)" :title="'确认' + label + '此流程？'" @confirm="maintainDefinition(action, record)"><span>{{ label }}</span></a-popconfirm>
                    <span v-else @click="maintainDefinition(action, record)">{{ label }}</span>
                  </a-menu-item>
                </template>
              </a-menu></template>
            </a-dropdown>
          </a-space>
        </template><template v-else-if="column.key === 'isPublish'">{{ Number(record.isPublish) === 1 ? '已发布' : Number(record.isPublish) === 9 ? '已失效' : '未发布' }}</template><template v-else-if="column.key === 'activityStatus'">{{ Number(record.activityStatus) === 1 ? '启用' : '停用' }}</template><a-button v-else-if="column.key === 'action' && rowBusinessType(record)" size="small" @click="openTask(record)">办理</a-button></template>
      </a-table>
    </div>
  </StandardListLayout>
  <a-modal :open="!!designerId" title="流程设计" width="96vw" :footer="null" :mask-closable="false" @cancel="designerId=undefined;load()">
    <iframe v-if="designerId" :src="'/workflow-designer.html?id=' + designerId + '&disabled=' + designerReadonly" title="Warm-Flow 流程设计器" style="height:76vh;width:100%;border:0" />
  </a-modal>
  <a-modal :open="setupOpen" title="新增审批流程" :width="760" ok-text="进入流程设计" cancel-text="取消" :confirm-loading="setupBusy" :mask-closable="false" @ok="publishSetup" @cancel="!setupBusy && (setupOpen = false)">
    <a-form layout="vertical">
      <a-form-item label="单据功能" required><a-select v-model:value="businessType" placeholder="请选择单据功能" :options="[{value:'customer_order',label:'客户订单'},{value:'material_demand_plan',label:'物料需求计划'}]" /></a-form-item>
      <a-form-item label="流程名称" required><a-input v-model:value="flowName" placeholder="请输入流程名称" :maxlength="40" /></a-form-item>
      <a-form-item label="设计器模型" required>
        <a-radio-group v-model:value="designerModel">
          <a-radio value="CLASSICS">经典模型</a-radio>
          <a-radio value="MIMIC">仿钉钉模型</a-radio>
        </a-radio-group>
      </a-form-item>
      <p class="setup-hint">模型在创建草稿时确定。进入设计器后可设置审批节点与审批人。</p>
    </a-form>
  </a-modal>
  <a-modal :open="!!runtimeAction" :title="runtimeActions.find(item=>item.value===runtimeAction)?.label || '选择抄送人'" :confirm-loading="processing" @cancel="runtimeAction=''" @ok="submitRuntimeAction">
    <p v-if="runtimeAction==='termination'">终止后当前审批将结束，{{ businessTypeNames[order?.businessType] || '业务单据' }}退回草稿。请确认审批意见已填写终止原因。</p>
    <a-select v-else-if="['delegateTask','transferTask'].includes(runtimeAction)" :value="runtimeUsers[0]" show-search option-filter-prop="label" :options="runtimePeople" style="width:100%" placeholder="请选择人员" @change="value => runtimeUsers=[String(value)]" />
    <a-select v-else v-model:value="runtimeUsers" mode="multiple" show-search option-filter-prop="label" :options="runtimePeople" style="width:100%" placeholder="请选择人员" />
  </a-modal>
  <a-modal :open="!!current" :title="(kind === 'copy' ? '抄送的' : kind === 'waiting' ? '审批' : '查看') + (businessTypeNames[order?.businessType] || '业务单据') + ' ' + (order?.orderNo || order?.planNo || '')" :width="'min(1600px, 96vw)'" :closable="!processing" :mask-closable="false" :footer="null" @cancel="current = undefined">
    <div class="approval-layout"><section class="approval-history"><h3>审批进度</h3>
      <a-timeline><a-timeline-item v-for="(item,index) in order?.history || []" :key="index" :color="item.action === 'REJECT' ? 'red' : 'green'"><strong>{{ item.nodeName || '审批节点' }}</strong><div>{{ item.approver || '系统' }} · {{ item.action === 'REJECT' ? '退回' : item.nodeName === '申请人' ? '提交审批' : item.action === 'PASS' ? '通过' : '记录' }}</div><small>{{ item.updateTime ? new Date(item.updateTime).toLocaleString('zh-CN',{hour12:false}) : '' }}</small><div v-if="item.message" class="approval-opinion">{{ item.message }}</div></a-timeline-item>
        <a-timeline-item v-if="kind === 'waiting' && can('approval:waiting:handle')" color="blue"><strong>{{ order?.nodeName || '当前节点' }} · 待处理</strong><a-form layout="vertical"><a-form-item label="审批意见"><a-textarea v-model:value="opinion" :maxlength="500" /></a-form-item><a-form-item v-if="backOptions.length && taskActionEnabled('back')" label="退回节点"><a-select v-model:value="backNode" :options="backOptions" placeholder="选择退回节点" /></a-form-item><a-form-item v-if="taskActionEnabled('copy')" label="通过时抄送">
          <a-button @click="chooseRuntimeAction('copy')">选择抄送人</a-button>
          <p>{{ (editCopies ? manualCopies : defaultCopies).map(user => user.nickName || user.userId).join('、') || '未选择' }}</p>
        </a-form-item>
        <a-space wrap><a-button v-for="action in runtimeActions.filter(item => taskActionEnabled(item.rule) && (!['addSign','subSign'].includes(item.rule) || taskRatio > 0))" :key="action.value" :danger="action.value==='termination'" :disabled="processing" @click="chooseRuntimeAction(action.value)">{{ action.label }}</a-button></a-space>
        <a-space wrap><a-button :disabled="processing" @click="current = undefined">取消办理</a-button><a-button v-if="backOptions.length && taskActionEnabled('back')" danger :disabled="processing" @click="decide(true)">退回</a-button><a-button type="primary" :loading="processing" @click="decide(false)">通过</a-button></a-space></a-form></a-timeline-item>
    </a-timeline></section><section class="approval-document"><h3>{{ businessTypeNames[String(order?.businessType || '')] || '业务单据' }}详情</h3>
    <template v-if="order?.businessType==='material_demand_plan'">
      <a-descriptions v-if="order" bordered size="small" :column="3">
        <a-descriptions-item label="需求计划单号">{{ order.planNo || '—' }}</a-descriptions-item><a-descriptions-item label="来源客户订单">{{ order.sourceOrderNo || '—' }}</a-descriptions-item><a-descriptions-item label="客户">{{ order.document?.customerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="订单日期">{{ order.document?.date || '—' }}</a-descriptions-item><a-descriptions-item label="计划状态">{{ order.status==='submitted'?'审批中':order.status==='approved'?'已审核':order.status==='cancelled'?'已取消':'草稿' }}</a-descriptions-item><a-descriptions-item label="算料状态">{{ order.calculationStatus==='ready'?'已完成':order.calculationStatus==='pending'?'待算料':'需核对' }}</a-descriptions-item>
        <a-descriptions-item label="PO 数量">{{ order.document?.pos?.length || 0 }}</a-descriptions-item><a-descriptions-item label="订单修订">R{{ order.orderRevision }}</a-descriptions-item><a-descriptions-item label="计划创建时间">{{ order.createdAt ? new Date(order.createdAt).toLocaleString('zh-CN',{hour12:false}) : '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-table class="approval-po-list" size="small" bordered :pagination="{pageSize:10}" :scroll="{x:1200}" :data-source="order?.items || []" row-key="itemId" :columns="[{title:'客户 PO',dataIndex:'poNumber',key:'poNumber'},{title:'款号 / 子款',key:'style'},{title:'款式颜色 / 尺码',key:'styleSpec'},{title:'物料分类',dataIndex:'categoryName',key:'categoryName'},{title:'物料编码',dataIndex:'materialCode',key:'materialCode'},{title:'物料名称',dataIndex:'materialName',key:'materialName'},{title:'规格 / 颜色',key:'materialSpec'},{title:'供料方式',key:'supplyMode'},{title:'原始需求',key:'original'},{title:'计划需求',key:'planned'}]"><template #bodyCell="{column,record}"><span v-if="column.key==='style'">{{ record.childStyleCode || record.sourceStyleCode }}<span v-if="record.childStyleName"> · {{ record.childStyleName }}</span></span><span v-else-if="column.key==='styleSpec'">{{ [record.colorName,record.sizeName].filter(Boolean).join(' / ') || '—' }}</span><span v-else-if="column.key==='materialSpec'">{{ [record.materialSpec,record.materialColorName].filter(Boolean).join(' / ') || '—' }}</span><span v-else-if="column.key==='supplyMode'">{{ ({customer:'客供',purchase:'自采',mixed:'客供＋自采'} as Record<string,string>)[record.supplyMode] || '—' }}</span><span v-else-if="column.key==='original'">{{ record.originalQuantity }} {{ record.inventoryUnitName }}</span><span v-else-if="column.key==='planned'">{{ record.plannedQuantity }} {{ record.inventoryUnitName }}</span><span v-else>{{ record[String(column.key || '')] || '—' }}</span></template></a-table>
      <a-alert v-if="order?.reviewNote" class="approval-opinion" type="info" show-icon message="最近审核意见" :description="order.reviewNote" />
    </template>
    <template v-else>
    <a-descriptions v-if="order" bordered size="small" :column="3">
      <a-descriptions-item label="客户">{{ order.document?.customerName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="公司">{{ order.document?.companyName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="订单日期">{{ order.document?.date || '—' }}</a-descriptions-item>
      <a-descriptions-item label="业务员">{{ order.document?.salesName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="跟单员">{{ order.document?.followerName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="默认供料">{{ ({customer:'客供',purchase:'自采',mixed:'客供＋自采'} as Record<string,string>)[order.document?.supply] || '—' }}</a-descriptions-item>
      <a-descriptions-item label="PO 数量">{{ order.document?.pos?.length || 0 }}</a-descriptions-item>
      <a-descriptions-item label="客户参考号">{{ order.document?.reference || '—' }}</a-descriptions-item>
      <a-descriptions-item label="目的地">{{ order.document?.destination || '—' }}</a-descriptions-item>
      <a-descriptions-item label="订单备注" :span="3">{{ order.document?.remark || '—' }}</a-descriptions-item>
    </a-descriptions>
    <div class="approval-po-list"><div v-for="(po,index) in order?.document?.pos || []" :key="index" class="approval-po">
      <strong>PO {{ po.number || Number(index) + 1 }}</strong>
      <a-descriptions bordered size="small" :column="3"><a-descriptions-item label="PO 日期">{{ po.date || '—' }}</a-descriptions-item><a-descriptions-item label="默认交期">{{ po.due || '—' }}</a-descriptions-item><a-descriptions-item label="默认下线日期">{{ po.offlineDate || '—' }}</a-descriptions-item><a-descriptions-item label="供料安排">{{ ({customer:'客供',purchase:'自采',mixed:'客供＋自采'} as Record<string,string>)[po.supply || order?.document?.supply] || '—' }}</a-descriptions-item><a-descriptions-item label="目的地">{{ po.destination || order?.document?.destination || '—' }}</a-descriptions-item><a-descriptions-item label="备注">{{ po.remark || '—' }}</a-descriptions-item></a-descriptions>
      <a-table size="small" bordered :pagination="false" :scroll="{x:700}" :data-source="po.lines || []" :row-key="(row:any, i?:number) => row.key || i || 0" :columns="[{title:'款式',dataIndex:'styleName',key:'styleName'},{title:'颜色尺码 / 套装配套',key:'spec'},{title:'数量',dataIndex:'quantity',key:'quantity'},{title:'加工单价',dataIndex:'unitPrice',key:'unitPrice'},{title:'加工金额',dataIndex:'processingAmount',key:'processingAmount'}]"><template #bodyCell="{column,record}"><template v-if="column.key==='spec'"><template v-if="record.setSpecId"><div v-for="(part,i) in record.configuration || []" :key="i">{{ [part.childLabel || '子款',part.colorName || '—',part.sizeName || '—',part.quantity == null ? '' : part.quantity+'件/套'].filter(Boolean).join(' / ') }}</div></template><span v-else>{{ [record.colorName || '—',record.sizeName || '—'].join(' / ') }}</span></template><template v-else-if="column.key==='unitPrice' || column.key==='processingAmount'">{{ record[column.key] ?? '待填写' }}</template></template></a-table>
      <div v-if="po.deliveries?.length" class="delivery-summary"><strong>交付批次</strong><div v-for="(batch,i) in po.deliveries" :key="i">第 {{ Number(i)+1 }} 批：{{ batch.date || '日期未填' }} · {{ batch.destination || po.destination || order?.document?.destination || '目的地未填' }} · 分配数量 {{ Object.values(batch.allocations || {}).reduce((sum:number,n:any)=>sum+Number(n || 0),0) }}</div></div><div v-else class="delivery-summary">按默认交期一次交付</div>
    </div></div>
    </template></section></div>
  </a-modal>
</template>
<style scoped>
.steps-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:8px 0 12px; }
.approval-layout { display:grid; grid-template-columns:minmax(280px, 30%) minmax(0,1fr); gap:20px; height:calc(100vh - 160px); min-height:300px; }
.approval-history,.approval-document { overflow:auto; padding:4px 12px; }.approval-history { border-right:1px solid #e5e8ef; }.approval-history h3,.approval-document h3 { margin-bottom:18px; }.approval-history small { color:#86909c; }.approval-opinion { background:#f5f7fb; padding:8px; border-radius:6px; margin:8px 0; white-space:pre-wrap; }.approval-history :deep(.ant-form) { margin-top:12px; }
@media(max-width:760px) { .approval-layout { grid-template-columns:1fr; }.approval-history { border-right:0; } }
.steps-heading strong { color:#1d2129; font-size:15px; }
.steps-heading p, .setup-hint { color:#86909c; margin:4px 0 0; font-size:12px; }
.steps-list { max-height:42vh; overflow:auto; padding-right:4px; }
.setup-step { padding:12px 14px; margin-bottom:10px; border:1px solid #e5e8ef; border-radius:8px; background:#f9fafc; }
.step-title { display:flex; align-items:center; gap:8px; margin-bottom:10px; }
.step-title button { margin-left:auto; padding:0 4px; height:auto; }
.step-number { display:inline-flex; align-items:center; justify-content:center; width:24px; height:24px; border-radius:50%; color:#165dff; background:#e8f0ff; font-weight:600; }
.step-fields { display:grid; grid-template-columns:minmax(0, 1fr) minmax(0, 1.4fr); gap:12px; }
.step-fields label { display:flex; flex-direction:column; gap:5px; color:#4e5969; }
.step-fields :deep(.ant-select) { width:100%; }
.assignee-field { display:flex; flex-direction:column; gap:5px; color:#4e5969; }
.assignee-tags { display:flex; flex-wrap:wrap; gap:4px; }
.picker-hint { color:#86909c; margin-bottom:8px; }
.picker-layout { display:flex; gap:12px; }
.picker-org { width:210px; flex:none; border:1px solid #e5e8ef; border-radius:6px; padding:8px; max-height:360px; overflow:auto; }
.picker-content { flex:1; min-width:0; }
.picker-options { max-height:300px; min-height:120px; overflow:auto; margin-top:12px; border:1px solid #e5e8ef; border-radius:6px; padding:8px 12px; }
.picker-option { padding:6px 0; }
.picker-empty { color:#86909c; text-align:center; padding:30px; }
.picker-selected { margin-top:12px; color:#4e5969; }
.picker-selected a { margin-left:12px; }
.step-rule { display:flex; align-items:center; flex-wrap:wrap; gap:6px 12px; margin-top:10px; }
.step-rule > span { color:#86909c; font-size:12px; }
.step-copy { display:flex; flex-direction:column; gap:5px; margin-top:12px; color:#4e5969; }
.step-copy small { color:#86909c; }
.setup-hint { margin-top:8px; }
@media (max-width:600px) { .step-fields { grid-template-columns:1fr; } }
.approval-po-list { overflow:visible; }.delivery-summary { padding:10px 0; color:#4e5969; }
.approval-po { margin: 12px 0; } .approval-po strong { display:block; margin-bottom:8px; } .list { height: 100%; background: #fff; overflow: auto; padding: 12px; }.list :deep(.ant-alert) { margin-bottom: 12px; }</style>
