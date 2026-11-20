<!--
文件用途：提供织云 ERP CodeGeneratorManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP CodeGeneratorManage的查询、编辑、状态展示和用户操作界面。
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
/** 接入框架代码生成器的表结构导入、配置、预览和下载能力。 / Connect the framework generator import, configure, preview, and download workflows. */
import { computed, onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'

const rows=ref<any[]>([]),loading=ref(false),page=ref(1),size=ref(20),total=ref(0),keyword=ref(''),selectedKeys=ref<string[]>([])
const importOpen=ref(false),importLoading=ref(false),dataNames=ref<string[]>([]),dataName=ref(''),dbTables=ref<any[]>([]),dbPage=ref(1),dbSize=ref(20),dbTotal=ref(0),dbKeyword=ref(''),dbSelected=ref<string[]>([])
const editOpen=ref(false),editSaving=ref(false),form=reactive<any>({}),fieldRows=ref<any[]>([])
const previewOpen=ref(false),previewLoading=ref(false),previewFiles=ref<Record<string,string>>({}),previewKey=ref('')
const columns=[{key:'tableName',title:'表名称',width:240},{key:'tableComment',title:'表说明',width:220},{key:'className',title:'实体名称',width:180},{key:'tplCategory',title:'模板类型',width:120},{key:'createTime',title:'导入时间',width:180}]
const previewKeys=computed(()=>Object.keys(previewFiles.value))

/** 查询已导入代码生成表。 / Load imported generator tables. */
async function load(){loading.value=true;try{const r:any=await api.get('/tool/gen/list',{params:{pageNum:page.value,pageSize:size.value,tableName:keyword.value.trim()||undefined}});if(r.code!==200)throw new Error(r.msg||'查询失败');rows.value=(r.data?.rows||[]).map((x:any)=>({...x,id:String(x.tableId)}));total.value=Number(r.data?.total||0)}catch(e:any){message.error(e?.response?.data?.msg||e.message||'加载代码生成表失败')}finally{loading.value=false}}
/** 打开数据库表导入弹窗并获取数据源。 / Load available data sources for importing database tables. */
async function openImport(){importOpen.value=true;dbSelected.value=[];dbKeyword.value='';dbPage.value=1;try{const r:any=await api.get('/tool/gen/getDataNames');if(r.code!==200)throw new Error(r.msg||'数据源读取失败');dataNames.value=Array.isArray(r.data)?r.data:[];if(!dataName.value)dataName.value=dataNames.value[0]||'';await loadDbTables()}catch(e:any){message.error(e?.response?.data?.msg||e.message||'获取数据源失败')}}
async function loadDbTables(){if(!dataName.value)return;importLoading.value=true;try{const r:any=await api.get('/tool/gen/db/list',{params:{pageNum:dbPage.value,pageSize:dbSize.value,dataName:dataName.value,tableName:dbKeyword.value.trim()||undefined}});if(r.code!==200)throw new Error(r.msg||'数据库表读取失败');dbTables.value=(r.data?.rows||[]).map((x:any)=>({...x,id:String(x.tableName)}));dbTotal.value=Number(r.data?.total||0)}catch(e:any){message.error(e?.response?.data?.msg||e.message||'读取数据库表失败')}finally{importLoading.value=false}}
async function importTables(){if(!dbSelected.value.length)return message.warning('请至少选择一张表');importLoading.value=true;try{const r:any=await api.post('/tool/gen/importTable',null,{params:{tables:dbSelected.value.join(','),dataName:dataName.value}});if(r.code!==200)throw new Error(r.msg||'导入失败');message.success(`已导入 ${dbSelected.value.length} 张表`);importOpen.value=false;await load()}catch(e:any){message.error(e?.response?.data?.msg||e.message||'导入表结构失败')}finally{importLoading.value=false}}

/** 拉取表级设置和字段设置并打开配置界面。 / Fetch table and column settings for editing. */
async function edit(row:any){try{const r:any=await api.get('/tool/gen/'+row.id);if(r.code!==200)throw new Error(r.msg||'读取配置失败');Object.assign(form,r.data?.info||{});form.tableId=String(form.tableId);fieldRows.value=(r.data?.rows||[]).map((x:any)=>({...x,columnId:String(x.columnId),tableId:String(x.tableId)}));editOpen.value=true}catch(e:any){message.error(e?.response?.data?.msg||e.message||'读取代码生成配置失败')}}
async function saveConfig(){editSaving.value=true;try{const payload={...form,tableId:form.tableId,columns:fieldRows.value};const r:any=await api.put('/tool/gen',payload);if(r.code!==200)throw new Error(r.msg||'保存失败');message.success('生成配置已保存');editOpen.value=false;await load()}catch(e:any){message.error(e?.response?.data?.msg||e.message||'保存代码生成配置失败')}finally{editSaving.value=false}}
/** 预览框架生成代码，按返回文件路径切换内容。 / Preview generated files returned by the framework. */
async function preview(row:any){previewOpen.value=true;previewLoading.value=true;previewFiles.value={};try{const r:any=await api.get('/tool/gen/preview/'+row.id);if(r.code!==200)throw new Error(r.msg||'预览失败');previewFiles.value=r.data||{};previewKey.value=Object.keys(previewFiles.value)[0]||''}catch(e:any){message.error(e?.response?.data?.msg||e.message||'代码预览失败')}finally{previewLoading.value=false}}
async function download(row:any){try{const blob:any=await api.get('/tool/gen/download/'+row.id,{responseType:'blob'});if(blob?.type?.includes('application/json'))throw new Error('生成下载失败');const url=URL.createObjectURL(blob as Blob);const a=document.createElement('a');a.href=url;a.download=`${row.tableName||'generated-code'}.zip`;a.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(e:any){message.error(e?.response?.data?.msg||e.message||'生成代码下载失败')}}
/** 统一处理 Ant Table 的字段路径，兼容框架列定义中可选或数组型 dataIndex。 / Normalize Ant Table field paths so optional and array dataIndex values remain type-safe. */
function columnField(column: { dataIndex?: unknown }): string { return typeof column.dataIndex === 'string' ? column.dataIndex : Array.isArray(column.dataIndex) ? column.dataIndex.map(String).join('.') : '' }
/** 将数据库表选择键归一化为字符串，避免 bigint 表名或主键发生精度转换。 / Normalize table selection keys to strings to preserve identifier precision. */
function changeDbSelection(keys: (string | number)[]) { dbSelected.value = keys.map(String) }
function remove(row:any){Modal.confirm({title:'删除生成配置',content:`删除“${row.tableName}”的代码生成配置？不会删除业务表。`,okText:'删除',cancelText:'取消',okType:'danger',async onOk(){const r:any=await api.delete('/tool/gen/'+row.id);if(r.code!==200)throw new Error(r.msg||'删除失败');message.success('生成配置已删除');await load()}})}
async function syncDb(row:any){Modal.confirm({title:'同步表结构',content:`从数据源重新读取“${row.tableName}”的数据库字段。已配置字段会按框架规则更新，确认继续？`,okText:'同步',cancelText:'取消',async onOk(){const r:any=await api.get('/tool/gen/synchDb/'+row.id);if(r.code!==200)throw new Error(r.msg||'同步失败');message.success('表结构已同步');await load()}})}
function search(){page.value=1;void load()}
onMounted(load)
</script>

<template>
  <StandardListLayout>
    <template #filters><label class="zy-filter-field"><span>数据表</span><a-input v-model:value="keyword" allow-clear placeholder="输入表名搜索" @press-enter="search" /></label></template>
    <FoundationTable page-toolbar storage-key="framework_generator" :columns="columns" :rows="rows" :loading="loading" :selected-keys="selectedKeys" :server-total="total" :server-page="page" :server-page-size="size" :action-width="280" @update:selected-keys="selectedKeys=$event" @page-change="(p,s)=>{page=p;size=s;load()}">
      <template #actions><a-button v-if="can('tool:gen:import')" type="primary" @click="openImport">导入数据库表</a-button><a-button type="primary" @click="search">查询</a-button><a-button @click="keyword='';search()">重置</a-button></template>
      <template #cell="{column,row}"><a-tag v-if="column.key==='tplCategory'">{{row.tplCategory==='tree'?'树表':'单表'}}</a-tag><span v-else>{{row[column.key]||'—'}}</span></template>
      <template #action="{row}"><a-button v-if="can('tool:gen:edit')" type="link" size="small" @click="edit(row)">配置</a-button><a-button v-if="can('tool:gen:preview')" type="link" size="small" @click="preview(row)">预览</a-button><a-button v-if="can('tool:gen:code')" type="link" size="small" @click="download(row)">下载代码</a-button><a-dropdown><a-button type="link" size="small">更多</a-button><template #overlay><a-menu><a-menu-item v-if="can('tool:gen:edit')" @click="syncDb(row)">同步表结构</a-menu-item><a-menu-item v-if="can('tool:gen:remove')" danger @click="remove(row)">删除配置</a-menu-item></a-menu></template></a-dropdown></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal :open="importOpen" title="导入数据库表" width="940px" :confirm-loading="importLoading" :mask-closable="false" :keyboard="false" ok-text="导入选中表" cancel-text="取消" @ok="importTables" @cancel="importOpen=false"><div class="import-toolbar"><label>数据源<a-select v-model:value="dataName" style="width:240px" :options="dataNames.map(x=>({value:x,label:x}))" @change="dbPage=1;loadDbTables()" /></label><label>表名<a-input v-model:value="dbKeyword" allow-clear placeholder="表名或表说明" @press-enter="dbPage=1;loadDbTables()" /></label><a-button @click="dbPage=1;loadDbTables()">查询</a-button></div><a-table row-key="id" :columns="[{title:'表名',dataIndex:'tableName'},{title:'表说明',dataIndex:'tableComment'},{title:'已导入',dataIndex:'已导入'}]" :data-source="dbTables" :loading="importLoading" :row-selection="{selectedRowKeys:dbSelected,onChange:changeDbSelection}" :pagination="{current:dbPage,pageSize:dbSize,total:dbTotal,showSizeChanger:true,onChange:(p:number,s:number)=>{dbPage=p;dbSize=s;loadDbTables()}}" /></a-modal>
  <a-modal :open="editOpen" title="代码生成配置" width="1120px" :confirm-loading="editSaving" :mask-closable="false" :keyboard="false" ok-text="保存配置" cancel-text="取消" @ok="saveConfig" @cancel="editOpen=false"><a-tabs><a-tab-pane key="base" tab="基础信息"><div class="form-grid"><a-form-item label="表名称"><a-input :value="form.tableName" disabled /></a-form-item><a-form-item label="表说明"><a-input v-model:value="form.tableComment" /></a-form-item><a-form-item label="实体类名称"><a-input v-model:value="form.className" /></a-form-item><a-form-item label="模板类型"><a-select v-model:value="form.tplCategory" :options="[{value:'crud',label:'单表 CRUD'},{value:'tree',label:'树表 CRUD'}]" /></a-form-item><a-form-item label="前端模板"><a-select v-model:value="form.frontendType" :options="[{value:'vue',label:'Vue'},{value:'react',label:'React'}]" /></a-form-item><a-form-item label="生成包路径"><a-input v-model:value="form.packageName" /></a-form-item><a-form-item label="模块名"><a-input v-model:value="form.moduleName" /></a-form-item><a-form-item label="业务名"><a-input v-model:value="form.businessName" /></a-form-item><a-form-item label="功能名称"><a-input v-model:value="form.functionName" /></a-form-item><a-form-item label="作者"><a-input v-model:value="form.functionAuthor" /></a-form-item><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></div></a-tab-pane><a-tab-pane key="columns" tab="字段配置"><a-alert type="info" show-icon message="字段属性将决定生成的新增、编辑、查询和列表表单；请在生成代码并用于业务前复核字段含义。" style="margin-bottom:12px"/><a-table size="small" :pagination="false" :data-source="fieldRows" row-key="columnId" :scroll="{x:1500,y:440}" :columns="[{title:'列名',dataIndex:'columnName',fixed:'left',width:150},{title:'字段说明',dataIndex:'columnComment',width:180},{title:'Java 属性',dataIndex:'javaField',width:150},{title:'Java 类型',dataIndex:'javaType',width:110},{title:'必填',dataIndex:'isRequired',width:80},{title:'新增',dataIndex:'isInsert',width:80},{title:'编辑',dataIndex:'isEdit',width:80},{title:'列表',dataIndex:'isList',width:80},{title:'查询',dataIndex:'isQuery',width:80},{title:'查询方式',dataIndex:'queryType',width:110},{title:'控件类型',dataIndex:'htmlType',width:120},{title:'字典类型',dataIndex:'dictType',width:150}]"><template #bodyCell="{column,record}"><a-input v-if="['columnComment','javaField','dictType'].includes(columnField(column))" v-model:value="record[columnField(column)]" size="small"/><a-select v-else-if="['isRequired','isInsert','isEdit','isList','isQuery'].includes(columnField(column))" v-model:value="record[columnField(column)]" size="small" :options="[{value:'1',label:'是'},{value:'0',label:'否'}]"/><a-select v-else-if="columnField(column)==='queryType'" v-model:value="record.queryType" size="small" :options="['EQ','NE','GT','GTE','LT','LTE','LIKE','BETWEEN'].map(v=>({value:v,label:v}))"/><a-select v-else-if="columnField(column)==='htmlType'" v-model:value="record.htmlType" size="small" :options="['input','textarea','select','checkbox','radio','datetime','image','upload','editor'].map(v=>({value:v,label:v}))"/><span v-else>{{record[columnField(column)]||'—'}}</span></template></a-table></a-tab-pane></a-tabs></a-modal>
  <a-modal :open="previewOpen" title="生成代码预览" width="1100px" :footer="null" :mask-closable="false" :keyboard="false" @cancel="previewOpen=false"><a-spin :spinning="previewLoading"><a-tabs v-if="previewKeys.length" v-model:active-key="previewKey" tab-position="left"><a-tab-pane v-for="path in previewKeys" :key="path" :tab="path"><pre class="code-preview">{{previewFiles[path]}}</pre></a-tab-pane></a-tabs><a-empty v-else description="暂无预览内容"/></a-spin></a-modal>
</template>
<style scoped>.import-toolbar{display:flex;align-items:center;gap:14px;margin-bottom:14px}.import-toolbar label{display:flex;align-items:center;gap:8px}.form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0 14px}.code-preview{height:68vh;overflow:auto;margin:0;padding:14px;background:#101828;color:#e6edf3;border-radius:8px;font:12px/1.6 ui-monospace,SFMono-Regular,monospace;white-space:pre}</style>
