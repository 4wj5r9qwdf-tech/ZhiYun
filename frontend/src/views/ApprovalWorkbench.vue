<script setup lang="ts">
/** 使用框架分页接口，流程配置和审批办理均以服务端权限校验为准。 */
import { computed, onMounted, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import { useRoute, useRouter } from 'vue-router'
const props = defineProps<{ kind: 'definition' | 'waiting' | 'finished' }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false), rows = ref<any[]>([]), total = ref(0), page = ref(1), pageSize = ref(20), keyword = ref('')
const definitions = computed(() => props.kind === 'definition')
const taskColumns = computed(() => [{key:'businessTypeLabel',title:'单据类型',width:120},{key:'businessCodeLabel',title:'单据编号',width:180},{key:'createByName',title:'发起人',width:120},{key:'flowName',title:'流程名称',width:160},{key:'nodeName',title:'审批节点',width:160},{key:'flowStatusName',title:'流程状态',width:120},{key:'createTime',title:'节点到达时间',width:180},...(props.kind==='finished'?[{key:'updateTime',title:'办理时间',width:180}]:[])])
const taskRows = computed(() => rows.value.map(row => ({...row,businessTypeLabel:row.businessTitle?.startsWith('客户订单 ') ? '客户订单' : '其他单据',businessCodeLabel:row.businessCode || (row.businessTitle?.startsWith('客户订单 ') ? row.businessTitle.slice(5) : row.businessTitle) || '—'})))
async function openDocument(row:any) { if (props.kind !== 'waiting') { message.info('已办单据穿透尚未接入'); return } try { const res:any=await api.get('/approval/customer-orders/tasks/'+row.id); if(res.code!==200) throw Error(res.msg); await router.push({name:'CustomerOrderManage',query:{orderId:res.data.businessId}}) } catch(e:any){message.error(e?.response?.data?.msg || e.message || '无法查看单据')} }
const columns = computed(() => definitions.value ? [
  { title: '流程名称', dataIndex: 'flowName', key: 'flowName' }, { title: '流程编码', dataIndex: 'flowCode', key: 'flowCode' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 100 }, { title: '发布状态', dataIndex: 'isPublish', key: 'isPublish', width: 120 },
  { title: '启用状态', dataIndex: 'activityStatus', key: 'activityStatus', width: 120 }, { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
] : [
  { title: '单据', dataIndex: 'businessTitle', key: 'businessTitle' }, { title: '流程名称', dataIndex: 'flowName', key: 'flowName' }, { title: '节点名称', dataIndex: 'nodeName', key: 'nodeName' },
  { title: '流程编码', dataIndex: 'flowCode', key: 'flowCode' }, { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  ...(props.kind === 'waiting' ? [{title:'操作',key:'action',width:100}] : []),
])
const canSetup = computed(() => ['workflow:definition:add', 'workflow:definition:publish', 'system:config:edit'].every(key => can(key)))
const setupOpen = ref(false)
const setupBusy = ref(false)
const flowName = ref('')
const businessType = ref<string>()
const assignees = ref<{value:string;label:string;kind:string;orgId:string;parentId:string}[]>([])
const newStep = () => ({name: '', assignee: undefined as string | undefined, unanimous: false, copyUsers: [] as string[]})
const steps = ref([newStep()])
const pickerIndex = ref<number>()
const pickerKind = ref('人员')
const pickerSearch = ref('')
const pickerValues = ref<string[]>([])
const pickerPurpose = ref<'approval'|'copy'>('approval')
const selectedOrg = ref<string>()
const orgTree = computed(() => {
  const nodes = assignees.value.filter(x => x.kind === '部门').map(x => ({key:x.orgId,title:x.label.slice(5), parentId:x.parentId, children:[] as any[]}))
  const map = new Map(nodes.map(x => [x.key,x])); const roots:any[] = []
  for (const node of nodes) { const parent = map.get(node.parentId); if (parent && parent !== node) parent.children.push(node); else roots.push(node) }
  return roots
})
function orgDescendants(id:string):string[] { const result=[id]; for (const item of assignees.value.filter(x => x.kind === '部门' && x.parentId === id && x.orgId !== id)) result.push(...orgDescendants(item.orgId)); return result }
const pickerItems = computed(() => assignees.value.filter(item => item.kind === pickerKind.value && item.label.toLowerCase().includes(pickerSearch.value.trim().toLowerCase()) && (pickerKind.value !== '人员' || !selectedOrg.value || orgDescendants(selectedOrg.value).includes(item.orgId))))
function openAssigneePicker(index:number, purpose:'approval'|'copy' = 'approval') {
  pickerIndex.value = index
  pickerPurpose.value = purpose
  pickerKind.value = '人员'
  pickerSearch.value = ''
  selectedOrg.value = undefined
  pickerValues.value = purpose === 'copy' ? [...(steps.value[index]?.copyUsers || [])] : steps.value[index]?.assignee?.split(',').filter(Boolean) || []
}
function confirmAssigneePicker() {
  if (pickerIndex.value == null) return
  if (pickerPurpose.value === 'copy') steps.value[pickerIndex.value]!.copyUsers = [...pickerValues.value]
  else steps.value[pickerIndex.value]!.assignee = pickerValues.value.length ? pickerValues.value.join(',') : undefined
  pickerIndex.value = undefined
}
function assigneeLabel(value:string) { return assignees.value.find(item => item.value === value)?.label || value }
async function openSetup() {
  setupBusy.value = true
  try {
    const result:any = await api.get('/approval/customer-orders/setup')
    if (result.code !== 200) throw new Error(result.msg)
    assignees.value = result.data.map((item:any) => ({value:String(item.value),label:item.kind + ' · ' + item.label,kind:item.kind,orgId:String(item.org_id || ''),parentId:String(item.parent_id || '')}))
    steps.value = [newStep()]
    businessType.value = undefined
    flowName.value = ''
    setupOpen.value = true
  } catch (error:any) { message.error(error.message || '审批人加载失败') }
  finally { setupBusy.value = false }
}
async function publishSetup() {
  if (!businessType.value || !flowName.value.trim() || steps.value.some(step => !step.name.trim() || !step.assignee)) {
    message.warning('请选择单据功能，填写流程名称、节点名称并选择审批人')
    return
  }
  setupBusy.value = true
  try {
    const result:any = await api.post('/approval/customer-orders/setup', {businessType:businessType.value,name:flowName.value.trim(),steps:steps.value})
    if (result.code !== 200) throw new Error(result.msg)
    setupOpen.value = false
    message.success('流程已发布，请在审批对象配置中选择使用的流程')
    await load()
  } catch (error:any) { message.error(error.message || '流程发布失败') }
  finally { setupBusy.value = false }
}
const current = ref<any>()
const order = ref<any>()
const opinion = ref('')
const processing = ref(false)
const backOptions = ref<{value:string;label:string}[]>([])
const backNode = ref<string>()
async function openTask(row:any) {
  try {
    const result:any = await api.get('/approval/customer-orders/tasks/' + row.id)
    if (result.code !== 200) throw new Error(result.msg)
    const nodes:any = await api.get('/workflow/task/getBackTaskNode/' + row.id + '/' + encodeURIComponent(row.nodeCode || result.data.nodeCode))
    backOptions.value = nodes.code === 200 ? (nodes.data || []).map((n:any) => ({value:n.nodeCode,label:n.nodeName})) : []
    backNode.value = undefined
    opinion.value = ''
    order.value = result.data
    current.value = row
  } catch (error:any) { message.error(error.message || '订单加载失败') }
}
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
      ...(back ? {nodeCode:backNode.value} : {}),
    })
    if (result.code !== 200) throw new Error(result.msg)
    current.value = undefined
    message.success('审批意见已提交')
    await load()
  } catch (error:any) { message.error(error.message || '办理失败') }
  finally { processing.value = false }
}
async function load() {
  loading.value = true
  const url = definitions.value ? '/workflow/definition/list' : props.kind === 'waiting' ? '/workflow/task/pageByTaskWait' : '/workflow/task/pageByTaskFinish'
  try {
    const res: any = await api.get(url, { params: { pageNum: page.value, pageSize: pageSize.value, flowName: keyword.value.trim() || undefined } })
    if (res.code !== 200) throw new Error(res.msg || '加载失败')
    rows.value = res.data?.rows || []; total.value = Number(res.data?.total || 0)
  } catch (err: any) { message.error(err?.response?.data?.msg || err?.message || '加载失败') }
  finally { loading.value = false }
}
function query() { page.value = 1; void load() }
function change(pagination: any) { page.value = pagination.current; pageSize.value = pagination.pageSize; void load() }
watch(() => props.kind, () => { page.value = 1; keyword.value = ''; void load() })
watch(() => route.query.taskId, value => { const taskId=String(value || ''); if (props.kind === 'waiting' && /^\d+$/.test(taskId)) void openTask({id:taskId}) }, {immediate:true})
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>流程名称</span><a-input v-model:value="keyword" allow-clear placeholder="请输入流程名称" @press-enter="query" /></label></template>
    <FoundationTable v-if="!definitions" page-toolbar :storage-key="`approval_${kind}_list`" :columns="taskColumns" :rows="taskRows" :loading="loading" :action-width="120" :server-total="total" :server-page="page" :server-page-size="pageSize" @page-change="(p,s)=>{page=p;pageSize=s;load()}">
      <template #actions><a-button type="primary" @click="query">查询</a-button><a-button @click="keyword='';query()">重置</a-button></template>
      <template #cell="{column,row}"><a v-if="column.key==='businessCodeLabel' && row.businessTypeLabel==='客户订单'" @click="openDocument(row)">{{ row.businessCodeLabel }}</a><span v-else>{{ row[column.key] || '—' }}</span></template>
      <template #action="{row}"><a-button v-if="kind==='waiting' && row.businessTypeLabel==='客户订单'" type="link" size="small" @click="openTask(row)">办理</a-button><span v-else>—</span></template>
    </FoundationTable>
    <div v-else class="list">
      <a-button v-if="definitions && canSetup" type="primary" :loading="setupBusy" @click="openSetup">新增</a-button>
      <a-button type="primary" @click="query">查询</a-button><a-button @click="keyword='';query()">重置</a-button>

      <a-table row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="{current:page,pageSize,total,showSizeChanger:true,showTotal:(n:number)=>`共 ${n} 条`}" @change="change">
        <template #bodyCell="{ column, record }"><template v-if="column.key === 'isPublish'">{{ Number(record.isPublish) === 1 ? '已发布' : Number(record.isPublish) === 9 ? '已失效' : '未发布' }}</template><template v-else-if="column.key === 'activityStatus'">{{ Number(record.activityStatus) === 1 ? '启用' : '停用' }}</template><a-button v-else-if="column.key === 'action' && record.businessTitle?.startsWith('客户订单 ')" size="small" @click="openTask(record)">办理</a-button></template>
      </a-table>
    </div>
  </StandardListLayout>
  <a-modal :open="setupOpen" title="新增审批流程" :width="760" ok-text="发布流程" cancel-text="取消" :confirm-loading="setupBusy" :mask-closable="false" @ok="publishSetup" @cancel="!setupBusy && (setupOpen = false)">
    <a-form layout="vertical">
      <a-form-item label="单据功能" required><a-select v-model:value="businessType" placeholder="请选择单据功能" :options="[{value:'customer_order',label:'客户订单'}]" /></a-form-item>
      <a-form-item label="流程名称" required><a-input v-model:value="flowName" placeholder="请输入流程名称" :maxlength="40" /></a-form-item>
      <div class="steps-heading"><div><strong>审批节点</strong><p>按顺序依次审批，退回后由申请人修改。</p></div><a-button :disabled="steps.length >= 12" @click="steps.push(newStep())">添加节点</a-button></div>
      <div class="steps-list">
        <div v-for="(step, i) in steps" :key="i" class="setup-step">
          <div class="step-title"><span class="step-number">{{ i + 1 }}</span><strong>第 {{ i + 1 }} 步</strong><a-button type="link" danger :disabled="steps.length === 1" @click="steps.splice(i,1)">移除</a-button></div>
          <div class="step-fields"><label><span>节点名称</span><a-input v-model:value="step.name" aria-label="节点名称" placeholder="例如：主管审批" :maxlength="40" /></label><div class="assignee-field"><span>审批人</span><a-button block @click="openAssigneePicker(i)">{{ step.assignee ? `已选 ${step.assignee.split(',').length} 项，点击修改` : '选择审批人' }}</a-button><div v-if="step.assignee" class="assignee-tags"><a-tag v-for="value in step.assignee.split(',')" :key="value" closable @close="step.assignee = step.assignee?.split(',').filter(v => v !== value).join(',') || undefined">{{ assigneeLabel(value) }}</a-tag></div></div></div>
          <div class="step-rule"><a-checkbox v-model:checked="step.unanimous">所有人通过</a-checkbox><span>选择岗位或角色时，勾选后需该节点所有审批人通过。</span></div>
          <div class="step-copy"><span>默认抄送人</span><a-button block @click="openAssigneePicker(i,'copy')">{{ step.copyUsers.length ? `已选 ${step.copyUsers.length} 人，点击修改` : '选择抄送人' }}</a-button><div class="assignee-tags"><a-tag v-for="value in step.copyUsers" :key="value" closable @close="step.copyUsers = step.copyUsers.filter(v => v !== value)">{{ assigneeLabel(value) }}</a-tag></div><small>该节点通过或退回时抄送所选人员；抄送人不参与审批。</small></div>
        </div>
      </div>
      <p class="setup-hint">发布后，请到审批对象配置中选择使用的流程。</p>
    </a-form>
  </a-modal>
  <a-modal :open="pickerIndex != null" :title="pickerPurpose === 'copy' ? '选择抄送人' : '选择审批人'" :width="800" ok-text="确定" cancel-text="取消" :mask-closable="false" @ok="confirmAssigneePicker" @cancel="pickerIndex = undefined">
    <p class="picker-hint">{{ pickerPurpose === 'copy' ? '选择接收抄送的人员，可按组织筛选。' : '可跨类型多选；部门表示部门人员，岗位和角色匹配相应人员。' }}</p>
    <a-tabs v-if="pickerPurpose === 'approval'" v-model:active-key="pickerKind"><a-tab-pane v-for="kind in ['人员','部门','岗位','角色']" :key="kind" :tab="kind" /></a-tabs>
    <div class="picker-layout"><aside v-if="pickerKind === '人员'" class="picker-org"><strong>组织架构</strong><a-button type="link" size="small" @click="selectedOrg = undefined">全部</a-button><a-tree :tree-data="orgTree" :selected-keys="selectedOrg ? [selectedOrg] : []" default-expand-all block-node @select="keys => selectedOrg = keys.length ? String(keys[0]) : undefined" /></aside><div class="picker-content">
    <a-input v-model:value="pickerSearch" allow-clear placeholder="搜索当前类型" />
    <div class="picker-options"><a-tree v-if="pickerKind === '部门' && !pickerSearch" :tree-data="orgTree" checkable check-strictly default-expand-all :checked-keys="{checked:pickerValues.filter(v => v.startsWith('org:')).map(v => v.slice(4)),halfChecked:[]}" @check="keys => pickerValues = [...pickerValues.filter(v => !v.startsWith('org:')), ...((keys as any).checked || []).map((v:string) => 'org:' + v)]" /><a-checkbox-group v-else v-model:value="pickerValues"><div v-for="item in pickerItems" :key="item.value" class="picker-option"><a-checkbox :value="item.value">{{ item.label.slice(item.label.indexOf('·') + 1).trim() }}</a-checkbox></div></a-checkbox-group><div v-if="!pickerItems.length" class="picker-empty">暂无可选项</div></div></div></div>
    <div class="picker-selected">已选 {{ pickerValues.length }} 项 <a v-if="pickerValues.length" @click="pickerValues = []">清空</a></div>
  </a-modal>
  <a-modal :open="!!current" :title="'审批客户订单 ' + (order?.orderNo || '')" :width="'min(1600px, 96vw)'" :closable="!processing" :mask-closable="false" :footer="null" @cancel="current = undefined">
    <div class="approval-layout"><section class="approval-history"><h3>审批进度</h3>
      <a-timeline><a-timeline-item v-for="(item,index) in order?.history || []" :key="index" :color="item.action === 'REJECT' ? 'red' : 'green'"><strong>{{ item.nodeName || '审批节点' }}</strong><div>{{ item.approver || '系统' }} · {{ item.action === 'REJECT' ? '退回' : item.nodeName === '申请人' ? '提交审批' : item.action === 'PASS' ? '通过' : '记录' }}</div><small>{{ item.updateTime ? new Date(item.updateTime).toLocaleString('zh-CN',{hour12:false}) : '' }}</small><div v-if="item.message" class="approval-opinion">{{ item.message }}</div></a-timeline-item>
        <a-timeline-item color="blue"><strong>{{ order?.nodeName || '当前节点' }} · 待处理</strong><a-form layout="vertical"><a-form-item label="审批意见"><a-textarea v-model:value="opinion" :maxlength="500" /></a-form-item><a-form-item v-if="backOptions.length" label="退回节点"><a-select v-model:value="backNode" :options="backOptions" placeholder="选择退回节点" /></a-form-item><a-space><a-button :disabled="processing" @click="current = undefined">取消办理</a-button><a-button v-if="backOptions.length" danger :disabled="processing" @click="decide(true)">退回</a-button><a-button type="primary" :loading="processing" @click="decide(false)">通过</a-button></a-space></a-form></a-timeline-item>
      </a-timeline></section><section class="approval-document"><h3>客户订单详情</h3>
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
    </section></div>
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
