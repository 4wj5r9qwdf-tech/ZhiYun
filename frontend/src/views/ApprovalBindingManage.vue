<script setup lang="ts">
/** 业务审批对象只绑定已发布的流程，具体审批节点由流程引擎管理。 */
import { computed, onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
type Flow = { flowCode: string; flowName: string; version: string; businessType: string }
type Binding = { businessType: string; flowCode: string; updateTime: string }
const types = ref<Record<string, string>>({}), flows = ref<Flow[]>([]), bindings = ref<Binding[]>([])
const loading = ref(false), saving = ref(false), editing = ref(''), selection = ref<string>()
const rows = computed(() => Object.entries(types.value).map(([id, name]) => {
  const binding = bindings.value.find(item => item.businessType === id)
  const flow = flows.value.find(item => item.flowCode === binding?.flowCode)
  return { id, name, available: id === 'customer_order', flowCode: binding?.flowCode || '', flowName: flow?.flowName || (binding ? '流程不可用，请重新配置' : id === 'customer_order' ? '未绑定（按审核权限审核）' : '待接入'), updateTime: binding?.updateTime || '—' }
}))
const columns = [{ key: 'name', title: '业务单据', width: 180 }, { key: 'flowName', title: '审批流程', width: 240 }, { key: 'flowCode', title: '流程编码', width: 180 }, { key: 'updateTime', title: '更新时间', width: 180 }]
const availableFlows = computed(() => flows.value.filter(flow => flow.businessType === editing.value))
async function load() {
  loading.value = true
  try {
    const result: any = await api.get('/system/approval-bindings')
    if (result.code !== 200) throw new Error(result.msg || '加载失败')
    types.value = result.data.types || {}; flows.value = result.data.flows || []; bindings.value = result.data.bindings || []
  } catch (error: any) { message.error(error?.response?.data?.msg || error?.message || '加载审批配置失败') }
  finally { loading.value = false }
}
function edit(row: any) { editing.value = row.id; selection.value = row.flowCode || undefined }
function removeBinding(row:any) {
  Modal.confirm({ title:'解除流程绑定', content:`解除后，新提交的${row.name}由具有单据审核权限的人员审核；已发起的审批继续按原流程办理。`, okText:'确认解除', cancelText:'取消', onOk: async () => {
    const result:any = await api.delete(`/system/approval-bindings/${row.id}`)
    if (result.code !== 200) throw new Error(result.msg || '解除失败')
    message.success('已解除流程绑定'); await load()
  } })
}
async function save() {
  if (!selection.value) { message.warning('请选择已发布的审批流程'); return }
  saving.value = true
  try {
    const result: any = await api.put(`/system/approval-bindings/${editing.value}`, { flowCode: selection.value })
    if (result.code !== 200) throw new Error(result.msg || '保存失败')
    message.success('流程绑定已保存'); editing.value = ''; await load()
  } catch (error: any) { message.error(error?.response?.data?.msg || error?.message || '保存审批配置失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters><a-alert type="info" show-icon message="业务单据未绑定流程时，由拥有对应单据审核权限的人员审核；绑定后按流程审批。物料需求计划和生产任务单的审批仍待接入。" /></template>
    <FoundationTable storage-key="approval_binding_list" :columns="columns" :rows="rows" :loading="loading" :action-width="120">
      <template #actions><a-button @click="load">刷新</a-button></template>
      <template #cell="{ column, row }">{{ row[column.key] || '—' }}</template>
      <template #action="{ row }"><a-space v-if="can('system:config:edit')"><a-button size="small" type="primary" :disabled="!row.available" @click="edit(row)">{{ row.available ? (row.flowCode ? '更换流程' : '设置流程') : '待接入' }}</a-button><a-button v-if="row.available && row.flowCode" size="small" @click="removeBinding(row)">解除绑定</a-button></a-space></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal :open="!!editing" :title="`设置${types[editing] || ''}审批流程`" :confirm-loading="saving" :mask-closable="false" @ok="save" @cancel="editing = ''">
    <p>选择该单据已发布并启用的流程。保存后，新提交的单据使用所选流程；已发起的审批继续按原流程办理。</p>
    <a-select v-model:value="selection" style="width:100%" placeholder="请选择审批流程" show-search option-filter-prop="label" :options="availableFlows.map(flow => ({ value: flow.flowCode, label: `${flow.flowName}（版本 ${flow.version}）` }))" />
    <p v-if="!availableFlows.length" class="hint">该单据没有可用流程，请先到流程定义新增并发布。</p>
  </a-modal>
</template>
<style scoped>.hint { margin-top: 12px; color: #ad6800; }</style>
