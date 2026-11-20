<!--
文件用途：提供织云 ERP CategoryManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP CategoryManage的查询、编辑、状态展示和用户操作界面。
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
/**
 * 分类类型和分类节点的统一管理。分类类型本身不分层，类型下的分类节点可建立多级树。
 * Maintain isolated taxonomy schemes and hierarchical nodes through shared foundation contracts.
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
import FoundationImport from '../components/FoundationImport.vue'
import FoundationBatchActions from '../components/FoundationBatchActions.vue'
import { foundationRequest as request, foundationError, downloadFoundationFile } from '../utils/foundation'
import { createSavedFormDefaults } from '../utils/savedFormDefaults'
import { encodeCsv } from '../utils/csv'
import { can } from '../utils/perms'
type Scheme = {id:string;code:string;name:string;maxDepth:number;sortOrder:number;status:string;remark:string}
type Category = {id:string;code:string;name:string;schemeId:string;parentId:string|null;sortOrder:number;status:string;remark:string}
const schemes=ref<Scheme[]>([]),rows=ref<Category[]>([]),schemeId=ref<string>(),selectedNode=ref<string>(),expanded=ref<string[]>([]),collapsed=ref(false),keyword=ref(''),status=ref<string>(),loading=ref(false),selected=ref<string[]>([])
const currentScheme=computed(()=>schemes.value.find(s=>s.id===schemeId.value))
const columns=[{key:'schemeName',title:'分类类型',width:150},{key:'code',title:'分类编码',width:140},{key:'name',title:'分类名称',width:160},{key:'parentName',title:'上级分类',width:150},{key:'path',title:'分类路径',width:260},{key:'depth',title:'层级',width:90,numeric:true},{key:'status',title:'状态',width:90},{key:'sortOrder',title:'排序',width:90,numeric:true},{key:'remark',title:'备注',width:200}]
/** 根据稳定ID反查当前路径，防御历史异常数据环路。 / Resolve display paths without recursive cycles. */
function lineage(row:Category) {const path:Category[]=[];const seen=new Set<string>();let cursor:Category|undefined=row;while(cursor&&!seen.has(cursor.id)){seen.add(cursor.id);path.unshift(cursor);cursor=rows.value.find(r=>r.id===cursor!.parentId)}return path}
/** 取选中节点及所有后代，树筛选不局限当前分页。 / Find the complete descendant set for tree filtering. */
function descendants(id:string) {return rows.value.filter(r=>lineage(r).some(n=>n.id===id)).map(r=>r.id)}
const filtered=computed(()=>{const typeKey=selectedNode.value?.startsWith('scheme:')?selectedNode.value.slice(7):undefined;const ids=selectedNode.value&&!typeKey?new Set(descendants(selectedNode.value)):null;const q=keyword.value.trim().toLowerCase();return rows.value.filter(r=>(!typeKey||r.schemeId===typeKey)&&(!ids||ids.has(r.id))&&(!q||[r.code,r.name,r.remark,schemes.value.find(s=>s.id===r.schemeId)?.name||''].some(s=>s.toLowerCase().includes(q)))&&(!status.value||r.status===status.value)).map(r=>({...r,schemeName:schemes.value.find(s=>s.id===r.schemeId)?.name||'未知类型',parentName:rows.value.find(p=>p.id===r.parentId)?.name||'根分类',path:lineage(r).map(p=>p.name).join(' / '),depth:lineage(r).length}))})
type TreeNode={key:string;title:string;children:TreeNode[]}
const tree=computed(()=>schemes.value.map(s=>{const schemeKey=`scheme:${s.id}`;const root:TreeNode={key:schemeKey,title:`${s.name}（${s.code}）${s.status==='1'?'（停用）':''}`,children:[]};const ownRows=rows.value.filter(r=>r.schemeId===s.id);const map=new Map<string,TreeNode>(ownRows.map(r=>[r.id,{key:r.id,title:`${r.name}${r.status==='1'?'（停用）':''}`,children:[]}]));ownRows.forEach(r=>{const node=map.get(r.id)!;const parent=r.parentId?map.get(r.parentId):undefined;if(parent)parent.children.push(node);else root.children.push(node)});return root}))
let loadVersion=0
/** 并行读取全部分类类型节点，旧响应不能覆盖新一轮完整数据。 / Load every type's nodes and reject stale responses. */
async function loadCategories() {const version=++loadVersion;loading.value=true;try{const groups=await Promise.all(schemes.value.map(s=>request<Category[]>('get','/system/category/list',{schemeId:s.id})));if(version===loadVersion){rows.value=groups.flat();expanded.value=[...schemes.value.map(s=>`scheme:${s.id}`),...rows.value.map(r=>r.id)];if(selectedNode.value&&!selectedNode.value.startsWith('scheme:')&&!rows.value.some(r=>r.id===selectedNode.value)){selectedNode.value=undefined;schemeId.value=undefined}}}catch(e){message.error(foundationError(e))}finally{if(version===loadVersion)loading.value=false}}
/** 刷新全部分类节点并保留仍有效的树选择。 / Refresh all nodes while retaining a valid tree selection. */
async function refresh() {await loadCategories()}
/** 读取所有类型和节点，不预先限定到单一业务分类。 / Load every type and its complete node hierarchy. */
async function loadSchemes() {try{schemes.value=await request('get','/system/category/schemes');if(schemeId.value&&!schemes.value.some(s=>s.id===schemeId.value)){schemeId.value=undefined;selectedNode.value=undefined}if(selectedNode.value?.startsWith('scheme:')&&!schemes.value.some(s=>`scheme:${s.id}`===selectedNode.value))selectedNode.value=undefined;await loadCategories()}catch(e){message.error(foundationError(e))}}
/** 按树节点切换筛选范围；类型根节点筛整类，分类节点筛自身与后代。 / Filter by a type root or a category subtree. */
function selectTreeNode(keys:unknown[]){selected.value=[];const key=keys.length?String(keys[0]):undefined;selectedNode.value=key;if(!key){schemeId.value=undefined;return}schemeId.value=key.startsWith('scheme:')?key.slice(7):rows.value.find(r=>r.id===key)?.schemeId}
/** 清除类型和节点筛选，恢复全部分类。 / Clear tree and text filters to show all categories. */
function reset() {keyword.value='';status.value=undefined;selectedNode.value=undefined;schemeId.value=undefined;selected.value=[];collapsed.value=false}
const manageOpen=ref(false),formOpen=ref(false),mode=ref<'scheme'|'category'>('category'),editId=ref(''),saving=ref(false),initial=ref(''),helpOpen=ref(false),importOpen=ref(false)
const autoCodeEnabled=ref(true),suggestedCode=ref(''),codeEditedByUser=ref(false)
let codePreviewRequest=0
type Form={id:string;code:string;name:string;schemeId:string;parentId:string|null;maxDepth:number;sortOrder:number;status:string;remark:string}
/** 构造无身份的新记录。 / Create a fresh scheme or node draft. */
function emptyForm():Form{return{id:'',code:'',name:'',schemeId:'',parentId:null,maxDepth:5,sortOrder:0,status:'0',remark:''}}
const form=ref<Form>(emptyForm())
const codeInput=ref<{focus:()=>void}>()
/** 聚焦编码便于连续录入，等待弹窗完成渲染。 / Focus the editable code after rendering a fresh draft. */
async function focusCode(){await nextTick();codeInput.value?.focus()}
/** 每个分类类型独立保存成功新增内容。 / Isolate continuous-entry defaults by user and scheme. */
function defaults(){return createSavedFormDefaults(sessionStorage,`category:add:${localStorage.getItem('userId')||'anon'}:${schemeId.value}`,emptyForm,['id','code'])}
/** 新增分类使用当前明确父级；新增分类类型不继承节点字段。 / Apply explicit namespace context to new drafts. */
function openAdd(kind:'scheme'|'category',parent?:string,targetSchemeId?:string){mode.value=kind;editId.value='';suggestedCode.value='';codeEditedByUser.value=false;if(kind==='category'){const parentRow=parent?rows.value.find(r=>r.id===parent):undefined;schemeId.value=String(targetSchemeId||parentRow?.schemeId||schemeId.value||'');const selectedCategory=selectedNode.value&&!selectedNode.value.startsWith('scheme:')?selectedNode.value:null;form.value={...defaults().load(),schemeId:schemeId.value,parentId:parent||selectedCategory||null}}else form.value=emptyForm();initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode();if(kind==='category'&&schemeId.value)void refreshCodePreview()}
/** 编辑从已保存值复制。 / Edit a detached domain snapshot. */
function openEdit(kind:'scheme'|'category',row:Scheme|Category){mode.value=kind;editId.value=row.id;suggestedCode.value='';codeEditedByUser.value=false;if(kind==='category'){schemeId.value=(row as Category).schemeId;selectedNode.value=row.id}form.value={...emptyForm(),...row};initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode()}
/** 获取该类型的下一个编码建议；只有用户尚未编辑输入时才自动填入。 / Load the next editable suggestion without overwriting user input. */
async function refreshCodePreview(){const requestId=++codePreviewRequest;suggestedCode.value='';if(!formOpen.value||mode.value!=='category'||editId.value||!schemeId.value)return;try{const result=await request<{enabled:boolean;code:string}>('get','/system/category/next-code',{schemeId:schemeId.value,parentId:form.value.parentId||undefined});if(requestId!==codePreviewRequest||!formOpen.value||mode.value!=='category'||editId.value)return;autoCodeEnabled.value=result.enabled===true;suggestedCode.value=String(result.code||'');if(autoCodeEnabled.value&&suggestedCode.value&&!codeEditedByUser.value)form.value.code=suggestedCode.value}catch(e){if(requestId===codePreviewRequest)message.error(foundationError(e))}}
/** 关闭前保护未保存内容。 / Confirm before discarding edited drafts. */
function closeForm(){if(saving.value)return;const close=()=>{codePreviewRequest++;formOpen.value=false};if(initial.value===JSON.stringify(form.value)){close();return}Modal.confirm({title:'放弃未保存的修改？',okText:'放弃修改',cancelText:'继续编辑',onOk:close})}
/** 清空草稿但保留当前业务分类类型上下文。 / Reset the draft without losing the active namespace. */
/** 可选父级清空即根分类。 / Normalize the optional parent identifier. */
function setParent(value: unknown){form.value.parentId=value?String(value):null;if(!codeEditedByUser.value){form.value.code='';void refreshCodePreview()}}
function clearForm(){form.value={...emptyForm(),schemeId:schemeId.value||''};codeEditedByUser.value=false;if(mode.value==='category')void refreshCodePreview()}
const parentOptions=computed(()=>{const excluded=editId.value?new Set(descendants(editId.value)):new Set<string>();return rows.value.filter(r=>r.schemeId===form.value.schemeId&&!excluded.has(r.id)&&(r.status==='0'||r.id===form.value.parentId)).map(r=>({value:r.id,label:lineage(r).map(n=>n.name).join(' / '),disabled:r.status!=='0'}))})
/** 行级权限和层级限制按所属分类类型计算。 / Resolve row actions against each row's own type. */
function rowScheme(row:Category){return schemes.value.find(s=>s.id===row.schemeId)}
function rowEditable(row:Category){return can('system:category:edit')&&rowScheme(row)?.status==='0'}
function rowCanAddChild(row:Category){const owner=rowScheme(row);return can('system:category:add')&&owner?.status==='0'&&row.status==='0'&&lineage(row).length<owner.maxDepth}
/** 保存分类类型或分类后刷新真实数据，再允许连续新增。 / Persist first and update successful defaults only after acceptance. */
async function save(again=false){if(saving.value)return;if(!form.value.code.trim()||!form.value.name.trim()){message.warning('请填写编码和名称');return}saving.value=true;try{const creatingScheme=mode.value==='scheme'&&!editId.value;const creatingCategory=mode.value==='category'&&!editId.value;const url=mode.value==='scheme'?'/system/category/schemes':'/system/category';const payload={...form.value,...(creatingCategory?{autoGeneratedCode:autoCodeEnabled.value&&!codeEditedByUser.value?suggestedCode.value:''}:{})};await request<string>(editId.value?'put':'post',editId.value?`${url}/${editId.value}`:url,payload);if(mode.value==='scheme'){await loadSchemes();if(creatingScheme){selectedNode.value=undefined;schemeId.value=undefined;manageOpen.value=false}}else{if(creatingCategory)defaults().save(form.value);await refresh()}message.success(creatingScheme?'分类类型已创建，已显示在左侧分类树中':'保存成功');if(again&&creatingCategory){form.value={...defaults().load(),schemeId:schemeId.value||'',parentId:form.value.parentId,code:''};suggestedCode.value='';codeEditedByUser.value=false;initial.value=JSON.stringify(form.value);void focusCode();void refreshCodePreview()}else{codePreviewRequest++;formOpen.value=false}}catch(e){message.error(foundationError(e))}finally{saving.value=false}}
/** 明确目标后删除，服务端保护下级和业务外键。 / Confirm and delete only unreferenced structures. */
function remove(kind:'scheme'|'category',row:Scheme|Category){const content=kind==='scheme'?`分类类型编码：${row.code}。删除将永久移除空类型，操作前后的审计记录仍保留；类型下存在任何分类记录（包括已删除分类）或业务引用时不能删除。`:`分类编码：${row.code}。存在下级或业务引用时不能删除。`;Modal.confirm({title:`删除${kind==='scheme'?'分类类型':'分类'}「${row.name}」？`,content,okText:'删除',okButtonProps:{danger:true},cancelText:'取消',onOk:async()=>{try{await request('delete',`/system/category/${kind==='scheme'?'schemes/':''}${row.id}`);if(kind==='scheme')await loadSchemes();else await refresh()}catch(e){message.error(foundationError(e));throw e}}})}
/** 状态变更保留层级和历史引用，父级停用按服务端约束处理。 / Change availability without rewriting historical references. */
function toggle(kind:'scheme'|'category',row:Scheme|Category){Modal.confirm({title:`${row.status==='0'?'停用':'启用'}「${row.name}」？`,content:kind==='scheme'?'停用分类类型后，类型下所有分类均不可供新业务选择；历史关联保留。':'停用分类前需先停用下级，历史关联保留。',okText:'确认',cancelText:'取消',onOk:async()=>{try{await request('put',`/system/category/${kind==='scheme'?'schemes/':''}${row.id}`,{...row,status:row.status==='0'?'1':'0'});if(kind==='scheme')await loadSchemes();else await refresh()}catch(e){message.error(foundationError(e));throw e}}})}
/** 状态列开关按目标状态确认并落库；操作列不重复提供启停入口。 / Confirm status changes from the status column. */
function toggleCategoryStatus(row:Category,checked:boolean){const target=checked?'0':'1';Modal.confirm({title:`${checked?'启用':'停用'}「${row.name}」？`,content:'停用分类前需先停用下级，历史关联保留。',okText:'确认',cancelText:'取消',onOk:async()=>{try{await request('put',`/system/category/${row.id}`,{...row,status:target});await refresh()}catch(e){message.error(foundationError(e));throw e}}})}
const batchRows=computed(()=>rows.value.filter(r=>selected.value.includes(r.id)).map(r=>({...r,depth:lineage(r).length})))
/** 批量启停沿用节点完整校验。 / Validate each selected node through the standard contract. */
async function batchUpdate(row:Category,status:string){await request('put',`/system/category/${row.id}`,{...row,status})}
/** 批量删除依然不能绕过引用和叶节点限制。 / Preserve reference guards during batch deletion. */
async function batchRemove(row:Category){await request('delete',`/system/category/${row.id}`)}
/** 刷新真实结果并清空过期选择。 / Reset selection after the completed batch. */
function batchDone(){selected.value=[];void refresh()}
const headers=['分类类型编码','分类编码','分类名称','上级分类编码','排序','状态','备注']
/** 文件归属固定为当前分类类型，不接受跨分类类型节点迁移。 / Bind imported nodes to the explicitly selected scheme. */
function parseImport(f:string[]){const owner=f[0]?schemes.value.find(s=>s.code===f[0]):currentScheme.value;if(!owner)throw new Error('请填写有效的分类类型编码；先在左侧选中类型时可省略此列');return{schemeId:owner.id,code:f[1],name:f[2],parentCode:f[3],sortOrder:f[4]||'0',status:f[5]==='停用'||f[5]==='1'?'1':f[5]==='启用'||!f[5]||f[5]==='0'?'0':f[5],remark:f[6]}}
/** 编码只在分类类型内唯一，批量导入按类型加编码识别重复项。 / Scope duplicate detection to the category type. */
function importDuplicateKey(payload:Record<string,any>){return`${payload.schemeId}:${payload.code}`}
/** 按父级在前顺序解析编码到稳定ID，失败逐条反馈。 / Resolve existing or earlier imported parents by code. */
async function importCategory(payload:Record<string,any>){const parent=payload.parentCode?rows.value.find(r=>r.schemeId===payload.schemeId&&r.code===payload.parentCode):null;if(payload.parentCode&&!parent)throw new Error('该分类类型下的上级分类不存在，请先导入父级');const id=await request<string>('post','/system/category',{...payload,parentId:parent?.id||null});rows.value.push({...payload,id,schemeId:String(payload.schemeId),parentId:parent?.id||null,sortOrder:Number(payload.sortOrder)} as Category)}
/** 导出当前结果，按深度排序便于重新导入父子关系。 / Export ancestors before descendants for safe reconstruction. */
function exportRows(){downloadFoundationFile(`${currentScheme.value?.name||'全部'}分类.csv`,encodeCsv([headers,...[...filtered.value].sort((a,b)=>a.schemeId.localeCompare(b.schemeId)||a.depth-b.depth).map(r=>[schemes.value.find(s=>s.id===r.schemeId)?.code||'',r.code,r.name,rows.value.find(p=>p.schemeId===r.schemeId&&p.id===r.parentId)?.code||'',String(r.sortOrder),r.status==='0'?'启用':'停用',r.remark])]))}
onMounted(loadSchemes)
</script>
<template>
  <StandardListLayout v-model:collapsed="collapsed">
    <template #filters><a-tag color="blue">{{ selectedNode?currentScheme?.name:'全部分类类型' }}</a-tag><label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" placeholder="搜索分类编码 / 名称" allow-clear style="width:240px"><template #prefix><SearchOutlined /></template></a-input></label><label class="zy-filter-field"><span>状态</span><a-select v-model:value="status" allow-clear placeholder="状态" :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" style="width:110px" /></label><a-button @click="manageOpen=true">分类类型</a-button></template>
    <template #sidebar><div class="tree-head"><strong>全部分类类型</strong><a-button size="small" @click="selectedNode=undefined;schemeId=undefined;selected=[]">重置</a-button></div><div class="tree-tip">展开类型查看分类；选择类型或分类节点筛选右侧列表</div><a-tree v-if="tree.length" block-node :tree-data="tree" :selected-keys="selectedNode?[selectedNode]:[]" :expanded-keys="expanded" @select="selectTreeNode" @expand="keys=>expanded=keys.map(String)" /><a-empty v-else description="尚未建立分类类型" style="margin-top:36px" /></template>
    <div class="category-main"><a-alert v-if="currentScheme?.status==='1'" type="warning" message="当前分类类型已停用，历史分类可查看；请先启用分类类型再维护节点。" style="margin-bottom:12px" />
      <FoundationTable page-toolbar storage-key="category-list" :columns="columns" :rows="filtered" :loading="loading" v-model:selected-keys="selected">
        <template #actions><a-button v-if="can('system:category:add')" type="primary" :disabled="!schemeId||currentScheme?.status!=='0'" @click="openAdd('category')"><PlusOutlined />新增</a-button><a-button type="primary" @click="refresh">查询</a-button><a-button @click="reset">重置</a-button><a-button v-if="can('system:category:add')" :disabled="!schemes.some(s=>s.status==='0')" @click="importOpen=true">导入</a-button><a-button :disabled="!filtered.length" @click="exportRows">导出</a-button><FoundationBatchActions :rows="batchRows" :can-edit="can('system:category:edit')&&batchRows.length>0&&batchRows.every(r=>rowScheme(r)?.status==='0')" :can-remove="can('system:category:remove')" :update="batchUpdate" :remove="batchRemove" @done="batchDone" /><a-button type="link" @click="helpOpen=true">使用指引</a-button></template>
        <template #cell="{column,row}"><a-switch v-if="column.key==='status'" :checked="row.status==='0'" checked-children="启用" un-checked-children="停用" :disabled="!rowEditable(row)" @change="value=>toggleCategoryStatus(row,Boolean(value))" /><span v-else>{{ row[column.key] ?? '—' }}</span></template>
        <template #action="{row}"><a-space><a-button v-if="can('system:category:edit')" type="primary" size="small" :disabled="!rowEditable(row)" @click="openEdit('category',row)">编辑</a-button><a-button v-if="can('system:category:add')" size="small" :disabled="!rowCanAddChild(row)" @click="openAdd('category',row.id,row.schemeId)">新增下级</a-button><a-button v-if="can('system:category:remove')" danger size="small" @click="remove('category',row)">删除</a-button></a-space></template>
      </FoundationTable>
    </div>
  </StandardListLayout>
  <a-modal v-model:open="manageOpen" title="分类类型" :width="900" :mask-closable="false" :keyboard="false" :footer="null"><a-alert type="info" show-icon message="分类类型本身不分层，用于区分物料分类、款式分类、设备分类等业务目录；分类最大层级限制的是类型下分类节点的深度。节点不能跨类型移动；类型编码是业务集成标识，创建后不可修改。全部分类类型会同时显示在左侧树中。" style="margin-bottom:16px" /><a-button v-if="can('system:category:add')" type="primary" style="margin-bottom:12px" @click="openAdd('scheme')"><PlusOutlined />新增分类类型</a-button><a-table :data-source="schemes" :columns="[{key:'code',dataIndex:'code',title:'类型编码'},{key:'name',dataIndex:'name',title:'类型名称'},{key:'maxDepth',dataIndex:'maxDepth',title:'分类最大层级'},{key:'status',title:'状态'},{key:'action',title:'操作',width:160}]" row-key="id" :pagination="{pageSize:10}"><template #bodyCell="{column,record}"><a-switch v-if="column.key==='status'" :checked="record.status==='0'" checked-children="启用" un-checked-children="停用" :disabled="!can('system:category:edit')" @change="toggle('scheme',record as Scheme)" /><a-space v-if="column.key==='action'"><a-button v-if="can('system:category:edit')" size="small" @click="openEdit('scheme',record as Scheme)">编辑</a-button><a-button v-if="can('system:category:remove')" danger size="small" @click="remove('scheme',record as Scheme)">删除</a-button></a-space></template></a-table></a-modal>
  <a-modal :open="formOpen" :title="`${editId?'编辑':'新增'}${mode==='scheme'?'分类类型':'分类'}`" :width="720" :mask-closable="false" :keyboard="false" :closable="!saving" :body-style="{maxHeight:'68vh',overflowY:'auto',overflowX:'hidden'}" @cancel="closeForm"><a-alert v-if="mode==='category'&&!editId" type="info" :message="`所属分类类型：${currentScheme?.name}；${form.parentId?'编码以上级分类编码为前缀、同级递增':'根分类编码以类型编码为前缀'}，生成值可编辑；保存并新增会沿用当前父级，请检查后保存。${autoCodeEnabled?'':'自动生成已关闭，请手动填写编码。'}`" style="margin-bottom:16px" /><a-form layout="vertical" :disabled="saving"><a-row :gutter="20"><a-col :span="12"><a-form-item :label="mode==='scheme'?'类型编码':'分类编码'" required><a-input ref="codeInput" v-model:value="form.code" :disabled="mode==='scheme'&&!!editId" :maxlength="40" :placeholder="mode==='category'?(autoCodeEnabled?'按分类类型编码自动生成，可修改':'自动生成已关闭，请手动填写'):'请输入编码'" @input="mode==='category'&&(codeEditedByUser=true)" /></a-form-item></a-col><a-col :span="12"><a-form-item :label="mode==='scheme'?'类型名称':'分类名称'" required><a-input v-model:value="form.name" :maxlength="80" /></a-form-item></a-col><a-col :span="12"><a-form-item v-if="mode==='category'" label="上级分类"><a-select :value="form.parentId ?? undefined" @change="setParent" allow-clear show-search option-filter-prop="label" :options="parentOptions" placeholder="不选则为根分类" /></a-form-item><a-form-item v-else label="分类最大层级" required><a-input-number v-model:value="form.maxDepth" :min="1" :max="20" :precision="0" style="width:100%" /></a-form-item></a-col><a-col :span="12"><a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" :max="999999" :precision="0" style="width:100%" /></a-form-item></a-col><a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" /></a-form-item></a-col></a-row></a-form><template #footer><a-button v-if="!editId&&mode==='category'" :disabled="saving" @click="clearForm">清空内容</a-button><a-button :disabled="saving" @click="closeForm">取消</a-button><a-button v-if="!editId&&mode==='category'" :loading="saving" @click="save(true)">保存并新增</a-button><a-button type="primary" :loading="saving" @click="save(false)">保存</a-button></template></a-modal>
  <FoundationImport v-model:open="importOpen" title="分类导入" :headers="headers" help="每行填写分类类型编码；状态填启用或停用，留空默认启用。先导入父级，再导入下级；上级分类编码留空表示根分类。" :parse="parseImport" :submit="importCategory" :duplicate-key="importDuplicateKey" @done="refresh" />
  <a-modal v-model:open="helpOpen" title="分类管理使用指引" :footer="null"><a-typography-paragraph>左侧按分类类型分组展示全部分类树。展开一个类型后，点击类型名称可查看该类型全部节点；点击分类节点可筛选自身及下级。新增根分类前先点击所属类型，新增下级可直接从分类行操作。</a-typography-paragraph><a-typography-paragraph>分类类型本身不分层；分类节点可以按业务需要建立上下级。右侧列表默认展示全部类型的分类；重置会清除树、搜索和状态筛选。</a-typography-paragraph><a-typography-paragraph>停用分类类型会使全部下属分类不可用于新业务；单个分类停用前先停用下级。分类名称可改，历史业务仍关联稳定ID；已有引用不能删除。</a-typography-paragraph></a-modal>
</template>
<style scoped>.category-main{height:100%;min-height:0;display:flex;flex-direction:column}.tree-head{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-bottom:1px solid #f0f2f5}.tree-tip{font-size:12px;color:#86909c;padding:10px 14px}.category-main :deep(.foundation-table){flex:1}:deep(.std-sidebar .ant-tree){overflow:auto;padding:0 8px 12px}</style>
