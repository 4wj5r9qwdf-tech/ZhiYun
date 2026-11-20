<!--
文件用途：提供织云 ERP 审批实例管理界面的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP 审批实例管理界面的查询、编辑、状态展示和用户操作界面。
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
/** 管理框架流程实例，查询运行中和已结束实例并查看流程轨迹。 / Manage workflow instances and inspect their history. */
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import ApprovalTimeline from '../components/ApprovalTimeline.vue'

const mode = ref<'running' | 'finished'>('running')
const flowName = ref(''), businessId = ref(''), startUserId = ref(''), nodeName = ref('')
const rows = ref<any[]>([]), loading = ref(false), page = ref(1), size = ref(20), total = ref(0)
const selected = ref<any>(), history = ref<any>(), variables = ref<Record<string, unknown>>({}), detailOpen = ref(false)
const columns = [
  { key: 'flowName', title: '流程名称', width: 190 }, { key: 'businessCode', title: '单据编号', width: 180 },
  { key: 'businessTitle', title: '单据标题', width: 220 }, { key: 'createByName', title: '发起人', width: 120 },
  { key: 'nodeName', title: '当前节点', width: 150 }, { key: 'flowStatusName', title: '流程状态', width: 110 },
  { key: 'createTime', title: '发起时间', width: 180 }, { key: 'activityStatusName', title: '运行状态', width: 100 },
]
const endpoint = computed(() => mode.value === 'running' ? '/workflow/instance/pageByRunning' : '/workflow/instance/pageByFinish')

/** 按当前状态和筛选条件分页查询流程实例。 / Query a page of instances for the selected status. */
async function load() {
  loading.value = true
  try {
    const res: any = await api.get(endpoint.value, { params: { pageNum: page.value, pageSize: size.value, flowName: flowName.value.trim() || undefined, businessId: businessId.value.trim() || undefined, startUserId: startUserId.value.trim() || undefined, nodeName: nodeName.value.trim() || undefined } })
    if (res.code !== 200) throw new Error(res.msg || '查询失败')
    const data = res.data || {}
    rows.value = (data.rows || []).map((row: any) => ({ ...row, id: String(row.id), businessId: String(row.businessId ?? ''), activityStatusName: row.activityStatus === 1 ? '运行中' : '已挂起' }))
    total.value = Number(data.total || 0)
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '流程实例查询失败') }
  finally { loading.value = false }
}

/** 同时读取流程轨迹与变量，详情只展示框架返回的信息。 / Load the engine history and variables for inspection. */
async function openDetail(row: any) {
  selected.value = row; history.value = undefined; variables.value = {}; detailOpen.value = true
  try {
    const [his, vars] = await Promise.all([
      api.get('/workflow/instance/flowHisTaskList/' + encodeURIComponent(row.businessId)),
      can('workflow:instance:variableQuery') ? api.get('/workflow/instance/instanceVariable/' + row.id) : Promise.resolve({ code: 403, data: {} }),
    ]) as any[]
    if (his.code !== 200) throw new Error(his.msg || '读取流程轨迹失败')
    history.value = his.data
    if (vars.code === 200) variables.value = vars.data || {}
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取流程详情失败') }
}

/** 暂停或恢复流程实例。 / Suspend or resume an instance. */
async function toggleActivity(row: any) {
  const active = row.activityStatus !== 1
  Modal.confirm({ title: active ? '恢复流程' : '暂停流程', content: `确认${active ? '恢复' : '暂停'}流程“${row.flowName || row.businessCode || row.id}”吗？`, okText: '确认', cancelText: '取消', async onOk() {
    const res: any = await api.put('/workflow/instance/active/' + row.id, null, { params: { active } })
    if (res.code !== 200) throw new Error(res.msg || '操作失败')
    message.success(active ? '流程已恢复' : '流程已暂停')
    await load()
  } })
}

/** 删除流程记录前明确区分运行中实例和历史实例接口。 / Delete using the endpoint matching the instance state. */
function remove(row: any) {
  Modal.confirm({ title: '删除流程实例', content: `删除后无法从流程实例列表恢复，确认删除“${row.businessCode || row.businessTitle || row.id}”吗？`, okText: '删除', cancelText: '取消', okType: 'danger', async onOk() {
    const apiPath = mode.value === 'running' ? '/workflow/instance/deleteByInstanceIds/' : '/workflow/instance/deleteHisByInstanceIds/'
    const res: any = await api.delete(apiPath + row.id)
    if (res.code !== 200) throw new Error(res.msg || '删除失败')
    message.success('流程实例已删除')
    await load()
  } })
}

function search() { page.value = 1; void load() }
function reset() { flowName.value = ''; businessId.value = ''; startUserId.value = ''; nodeName.value = ''; search() }
function setMode(value: 'running' | 'finished') { mode.value = value; page.value = 1; void load() }
onMounted(load)
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <label class="zy-filter-field"><span>流程名称</span><a-input v-model:value="flowName" allow-clear placeholder="输入流程名称" @press-enter="search" /></label>
      <label class="zy-filter-field"><span>单据编号</span><a-input v-model:value="businessId" allow-clear placeholder="输入业务单据编号" @press-enter="search" /></label>
      <label class="zy-filter-field"><span>发起人 ID</span><a-input v-model:value="startUserId" allow-clear placeholder="输入发起人" @press-enter="search" /></label>
      <label class="zy-filter-field"><span>当前节点</span><a-input v-model:value="nodeName" allow-clear placeholder="输入节点名称" @press-enter="search" /></label>
      <a-segmented :value="mode" :options="[{label:'运行中',value:'running'},{label:'已结束',value:'finished'}]" @change="value => setMode(value as 'running'|'finished')" />
    </template>
    <FoundationTable page-toolbar storage-key="workflow_instance_admin" :columns="columns" :rows="rows" :loading="loading" :server-total="total" :server-page="page" :server-page-size="size" :action-width="190" @page-change="(p,s)=>{page=p;size=s;load()}">
      <template #actions><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></template>
      <template #cell="{column,row}"><a-tag v-if="column.key==='flowStatusName'" color="blue">{{ row.flowStatusName || '—' }}</a-tag><a-tag v-else-if="column.key==='activityStatusName'" :color="row.activityStatus===1?'green':'orange'">{{ row.activityStatusName }}</a-tag><span v-else>{{ row[column.key] || '—' }}</span></template>
      <template #action="{row}"><a-button type="link" size="small" @click="openDetail(row)">查看</a-button><a-button v-if="mode==='running' && can('workflow:instance:active')" type="link" size="small" @click="toggleActivity(row)">{{ row.activityStatus===1?'暂停':'恢复' }}</a-button><a-button v-if="can('workflow:instance:remove')" type="link" danger size="small" @click="remove(row)">删除</a-button></template>
    </FoundationTable>
  </StandardListLayout>
  <a-drawer :open="detailOpen" :title="selected?.businessTitle || selected?.flowName || '流程实例详情'" width="720" @close="detailOpen=false">
    <a-descriptions v-if="selected" bordered size="small" :column="2"><a-descriptions-item label="流程名称">{{ selected.flowName || '—' }}</a-descriptions-item><a-descriptions-item label="流程编码">{{ selected.flowCode || '—' }}</a-descriptions-item><a-descriptions-item label="单据编号">{{ selected.businessCode || selected.businessId || '—' }}</a-descriptions-item><a-descriptions-item label="发起人">{{ selected.createByName || '—' }}</a-descriptions-item><a-descriptions-item label="流程状态">{{ selected.flowStatusName || '—' }}</a-descriptions-item><a-descriptions-item label="当前节点">{{ selected.nodeName || '—' }}</a-descriptions-item><a-descriptions-item label="创建时间">{{ selected.createTime || '—' }}</a-descriptions-item><a-descriptions-item label="流程版本">{{ selected.version || '—' }}</a-descriptions-item></a-descriptions>
    <a-divider orientation="left">流程轨迹</a-divider><a-spin v-if="!history" /><ApprovalTimeline v-else :items="history.list || []" />
    <template v-if="can('workflow:instance:variableQuery')"><a-divider orientation="left">流程变量</a-divider><pre class="json-view">{{ JSON.stringify(variables, null, 2) }}</pre></template>
  </a-drawer>
</template>

<style scoped>
.json-view{white-space:pre-wrap;overflow:auto;max-height:42vh;padding:12px;background:#f6f8fa;border:1px solid #e5e8ef;border-radius:6px;color:#344054;font-size:12px}
</style>
