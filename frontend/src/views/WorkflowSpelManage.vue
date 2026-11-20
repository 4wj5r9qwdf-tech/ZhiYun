<!--
文件用途：提供织云 ERP WorkflowSpelManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP WorkflowSpelManage的查询、编辑、状态展示和用户操作界面。
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
/** 维护框架工作流的动态表达式配置。 / Maintain workflow expression definitions. */
import { onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'

const rows=ref<any[]>([]),loading=ref(false),page=ref(1),size=ref(20),total=ref(0),keyword=ref(''),opened=ref(false),saving=ref(false)
const form=reactive<any>({id:undefined,componentName:'',methodName:'',methodParams:'',viewSpel:'',status:'0',remark:''})
const columns=[{key:'viewSpel',title:'表达式',width:260},{key:'componentName',title:'组件名称',width:180},{key:'methodName',title:'方法名',width:180},{key:'methodParams',title:'方法参数',width:200},{key:'statusName',title:'状态',width:100},{key:'createTime',title:'创建时间',width:180}]
async function load(){loading.value=true;try{const r:any=await api.get('/workflow/spel/list',{params:{pageNum:page.value,pageSize:size.value,viewSpel:keyword.value.trim()||undefined}});if(r.code!==200)throw new Error(r.msg||'查询失败');rows.value=(r.data?.rows||[]).map((x:any)=>({...x,id:String(x.id),statusName:String(x.status)==='0'?'正常':'停用'}));total.value=Number(r.data?.total||0)}catch(e:any){message.error(e?.response?.data?.msg||e.message||'加载失败')}finally{loading.value=false}}
async function edit(row?:any){Object.assign(form,{id:undefined,componentName:'',methodName:'',methodParams:'',viewSpel:'',status:'0',remark:''});if(row){try{const r:any=await api.get('/workflow/spel/'+row.id);if(r.code!==200)throw new Error(r.msg||'读取失败');Object.assign(form,r.data,{id:String(r.data.id)})}catch(e:any){message.error(e?.response?.data?.msg||e.message||'读取失败');return}}opened.value=true}
async function save(){if(!String(form.viewSpel||'').trim())return message.warning('请填写表达式');saving.value=true;try{const body={...form,id:form.id||undefined};const r:any=form.id?await api.put('/workflow/spel',body):await api.post('/workflow/spel',body);if(r.code!==200)throw new Error(r.msg||'保存失败');opened.value=false;message.success('保存成功');await load()}catch(e:any){message.error(e?.response?.data?.msg||e.message||'保存失败')}finally{saving.value=false}}
function remove(row:any){Modal.confirm({title:'删除表达式',content:`确认删除表达式“${row.viewSpel}”吗？`,okText:'删除',cancelText:'取消',okType:'danger',async onOk(){const r:any=await api.delete('/workflow/spel/'+row.id);if(r.code!==200)throw new Error(r.msg||'删除失败');message.success('已删除');await load()}})}
function reset(){keyword.value='';page.value=1;void load()}
onMounted(load)
</script>
<template><StandardListLayout><template #filters><label class="zy-filter-field"><span>表达式</span><a-input v-model:value="keyword" allow-clear placeholder="按表达式查询" @press-enter="page=1;load()" /></label></template><FoundationTable page-toolbar storage-key="workflow_spel" :columns="columns" :rows="rows" :loading="loading" :server-total="total" :server-page="page" :server-page-size="size" :action-width="130" @page-change="(p,s)=>{page=p;size=s;load()}"><template #actions><a-button v-if="can('workflow:spel:add')" type="primary" @click="edit()">新增</a-button><a-button type="primary" @click="page=1;load()">查询</a-button><a-button @click="reset">重置</a-button></template><template #cell="{column,row}"><a-tag v-if="column.key==='statusName'" :color="row.status==='0'?'green':'default'">{{row.statusName}}</a-tag><code v-else-if="column.key==='viewSpel'">{{row.viewSpel}}</code><span v-else>{{row[column.key]||'—'}}</span></template><template #action="{row}"><a-button v-if="can('workflow:spel:edit')" type="link" size="small" @click="edit(row)">编辑</a-button><a-button v-if="can('workflow:spel:remove')" type="link" danger size="small" @click="remove(row)">删除</a-button></template></FoundationTable></StandardListLayout><a-modal :open="opened" :title="form.id?'编辑流程表达式':'新增流程表达式'" :confirm-loading="saving" :mask-closable="false" :keyboard="false" ok-text="保存" cancel-text="取消" @ok="save" @cancel="opened=false"><a-form layout="vertical"><div class="form-grid"><a-form-item label="表达式" required><a-input v-model:value="form.viewSpel" placeholder="例如 ${initiator}" /></a-form-item><a-form-item label="状态"><a-select v-model:value="form.status" :options="[{value:'0',label:'正常'},{value:'1',label:'停用'}]" /></a-form-item><a-form-item label="组件名称"><a-input v-model:value="form.componentName" /></a-form-item><a-form-item label="方法名"><a-input v-model:value="form.methodName" /></a-form-item><a-form-item label="方法参数"><a-input v-model:value="form.methodParams" /></a-form-item><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="3" /></a-form-item></div><a-alert type="info" show-icon message="表达式会被流程节点配置引用。修改或停用前，请确认没有正在使用的流程定义依赖此表达式。" /></a-form></a-modal></template>
<style scoped>.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.form-grid :deep(.ant-form-item){margin-bottom:12px}</style>
