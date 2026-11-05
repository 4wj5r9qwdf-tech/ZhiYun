<script setup lang="ts">
/**
 * 分类体系和多级分类的统一管理，业务体系独立，选树节点筛选自身及下级。
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
const schemeOptions=computed(()=>schemes.value.map(s=>({value:s.id,label:`${s.name}${s.status==='1'?'（已停用）':''}`})))
const columns=[{key:'code',title:'分类编码',width:140},{key:'name',title:'分类名称',width:160},{key:'parentName',title:'上级分类',width:150},{key:'path',title:'分类路径',width:260},{key:'depth',title:'层级',width:90,numeric:true},{key:'status',title:'状态',width:90},{key:'sortOrder',title:'排序',width:90,numeric:true},{key:'remark',title:'备注',width:200}]
/** 根据稳定ID反查当前路径，防御历史异常数据环路。 / Resolve display paths without recursive cycles. */
function lineage(row:Category) {const path:Category[]=[];const seen=new Set<string>();let cursor:Category|undefined=row;while(cursor&&!seen.has(cursor.id)){seen.add(cursor.id);path.unshift(cursor);cursor=rows.value.find(r=>r.id===cursor!.parentId)}return path}
/** 取选中节点及所有后代，树筛选不局限当前分页。 / Find the complete descendant set for tree filtering. */
function descendants(id:string) {return rows.value.filter(r=>lineage(r).some(n=>n.id===id)).map(r=>r.id)}
const filtered=computed(()=>{const ids=selectedNode.value?new Set(descendants(selectedNode.value)):null;const q=keyword.value.trim().toLowerCase();return rows.value.filter(r=>(!ids||ids.has(r.id))&&(!q||[r.code,r.name,r.remark].some(s=>s.toLowerCase().includes(q)))&&(!status.value||r.status===status.value)).map(r=>({...r,parentName:rows.value.find(p=>p.id===r.parentId)?.name||'根分类',path:lineage(r).map(p=>p.name).join(' / '),depth:lineage(r).length}))})
type TreeNode={key:string;title:string;children:TreeNode[]}
const tree=computed(()=>{const map=new Map<string,TreeNode>(rows.value.map(r=>[r.id,{key:r.id,title:`${r.name}${r.status==='1'?'（停用）':''}`,children:[]}]));const roots:TreeNode[]=[];rows.value.forEach(r=>{const node=map.get(r.id)!;const parent=r.parentId?map.get(r.parentId):null;if(parent)parent.children.push(node);else roots.push(node)});return roots})
let loadVersion=0
/** 切换体系时隔离异步响应，防止旧请求覆盖新选择。 / Apply results only to the currently selected scheme. */
async function loadCategories() {const version=++loadVersion;rows.value=[];selected.value=[];selectedNode.value=undefined;if(!schemeId.value)return;loading.value=true;try{const data=await request<Category[]>('get','/system/category/list',{schemeId:schemeId.value});if(version===loadVersion){rows.value=data;expanded.value=data.map(r=>r.id)}}catch(e){message.error(foundationError(e))}finally{if(version===loadVersion)loading.value=false}}
/** 刷新当前体系节点，保留仍存在的树筛选。 / Refresh persisted nodes while retaining valid tree focus. */
async function refresh() {if(!schemeId.value)return;loading.value=true;try{rows.value=await request('get','/system/category/list',{schemeId:schemeId.value});if(!rows.value.some(r=>r.id===selectedNode.value))selectedNode.value=undefined;expanded.value=rows.value.map(r=>r.id)}catch(e){message.error(foundationError(e))}finally{loading.value=false}}
/** 读取所有体系，不擅自默认选中某个业务范围。 / Load schemes without silently restricting to the first one. */
async function loadSchemes() {try{schemes.value=await request('get','/system/category/schemes');if(schemeId.value&&!schemes.value.some(s=>s.id===schemeId.value)){schemeId.value=undefined;rows.value=[]}}catch(e){message.error(foundationError(e))}}
/** 重置当前体系内的搜索和树选择。 / Clear filters while retaining the explicitly chosen namespace. */
function reset() {keyword.value='';status.value=undefined;selectedNode.value=undefined;selected.value=[];collapsed.value=false}
const manageOpen=ref(false),formOpen=ref(false),mode=ref<'scheme'|'category'>('category'),editId=ref(''),saving=ref(false),initial=ref(''),helpOpen=ref(false),importOpen=ref(false)
type Form={id:string;code:string;name:string;schemeId:string;parentId:string|null;maxDepth:number;sortOrder:number;status:string;remark:string}
/** 构造无身份的新记录。 / Create a fresh scheme or node draft. */
function emptyForm():Form{return{id:'',code:'',name:'',schemeId:'',parentId:null,maxDepth:5,sortOrder:0,status:'0',remark:''}}
const form=ref<Form>(emptyForm())
const codeInput=ref<{focus:()=>void}>()
/** 聚焦编码便于连续录入，等待弹窗完成渲染。 / Focus the editable code after rendering a fresh draft. */
async function focusCode(){await nextTick();codeInput.value?.focus()}
/** 每个体系独立保存成功新增内容。 / Isolate continuous-entry defaults by user and scheme. */
function defaults(){return createSavedFormDefaults(sessionStorage,`category:add:${localStorage.getItem('userId')||'anon'}:${schemeId.value}`,emptyForm,['id','code'])}
/** 新增分类使用当前明确父级；新增体系不继承节点字段。 / Apply explicit namespace context to new drafts. */
function openAdd(kind:'scheme'|'category',parent?:string){mode.value=kind;editId.value='';form.value=kind==='category'?{...defaults().load(),schemeId:schemeId.value||'',parentId:parent||selectedNode.value||null}:emptyForm();initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode()}
/** 编辑从已保存值复制。 / Edit a detached domain snapshot. */
function openEdit(kind:'scheme'|'category',row:Scheme|Category){mode.value=kind;editId.value=row.id;form.value={...emptyForm(),...row};initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode()}
/** 关闭前保护未保存内容。 / Confirm before discarding edited drafts. */
function closeForm(){if(saving.value)return;if(initial.value===JSON.stringify(form.value)){formOpen.value=false;return}Modal.confirm({title:'放弃未保存的修改？',okText:'放弃修改',cancelText:'继续编辑',onOk:()=>{formOpen.value=false}})}
/** 清空草稿但保留当前业务体系上下文。 / Reset the draft without losing the active namespace. */
/** 可选父级清空即根分类。 / Normalize the optional parent identifier. */
function setParent(value: unknown){form.value.parentId=value?String(value):null}
function clearForm(){form.value={...emptyForm(),schemeId:schemeId.value||''}}
const parentOptions=computed(()=>{const excluded=editId.value?new Set(descendants(editId.value)):new Set<string>();return rows.value.filter(r=>!excluded.has(r.id)&&(r.status==='0'||r.id===form.value.parentId)).map(r=>({value:r.id,label:lineage(r).map(n=>n.name).join(' / '),disabled:r.status!=='0'}))})
/** 保存体系或分类后刷新真实数据，再允许连续新增。 / Persist first and update successful defaults only after acceptance. */
async function save(again=false){if(saving.value)return;if(!form.value.code.trim()||!form.value.name.trim()){message.warning('请填写编码和名称');return}saving.value=true;try{const url=mode.value==='scheme'?'/system/category/schemes':'/system/category';const id=await request<string>(editId.value?'put':'post',editId.value?`${url}/${editId.value}`:url,form.value);if(mode.value==='scheme'){await loadSchemes();if(!editId.value){schemeId.value=id;await loadCategories()}}else{if(!editId.value)defaults().save(form.value);await refresh()}message.success('保存成功');if(again&&mode.value==='category'){form.value={...defaults().load(),schemeId:schemeId.value||'',parentId:form.value.parentId};initial.value=JSON.stringify(form.value);void focusCode()}else formOpen.value=false}catch(e){message.error(foundationError(e))}finally{saving.value=false}}
/** 明确目标后删除，服务端保护下级和业务外键。 / Confirm and delete only unreferenced structures. */
function remove(kind:'scheme'|'category',row:Scheme|Category){Modal.confirm({title:`删除${kind==='scheme'?'分类体系':'分类'}「${row.name}」？`,content:`编码：${row.code}。存在下级或业务引用时不能删除。`,okText:'删除',okButtonProps:{danger:true},cancelText:'取消',onOk:async()=>{try{await request('delete',`/system/category/${kind==='scheme'?'schemes/':''}${row.id}`);if(kind==='scheme')await loadSchemes();else await refresh()}catch(e){message.error(foundationError(e));throw e}}})}
/** 状态变更保留层级和历史引用，父级停用按服务端约束处理。 / Change availability without rewriting historical references. */
function toggle(kind:'scheme'|'category',row:Scheme|Category){Modal.confirm({title:`${row.status==='0'?'停用':'启用'}「${row.name}」？`,content:kind==='scheme'?'停用体系后，体系下所有分类均不可供新业务选择；历史关联保留。':'停用分类前需先停用下级，历史关联保留。',okText:'确认',cancelText:'取消',onOk:async()=>{try{await request('put',`/system/category/${kind==='scheme'?'schemes/':''}${row.id}`,{...row,status:row.status==='0'?'1':'0'});if(kind==='scheme')await loadSchemes();else await refresh()}catch(e){message.error(foundationError(e));throw e}}})}
const batchRows=computed(()=>rows.value.filter(r=>selected.value.includes(r.id)).map(r=>({...r,depth:lineage(r).length})))
/** 批量启停沿用节点完整校验。 / Validate each selected node through the standard contract. */
async function batchUpdate(row:Category,status:string){await request('put',`/system/category/${row.id}`,{...row,status})}
/** 批量删除依然不能绕过引用和叶节点限制。 / Preserve reference guards during batch deletion. */
async function batchRemove(row:Category){await request('delete',`/system/category/${row.id}`)}
/** 刷新真实结果并清空过期选择。 / Reset selection after the completed batch. */
function batchDone(){selected.value=[];void refresh()}
const headers=['分类编码','分类名称','上级分类编码','排序','状态','备注']
/** 文件归属固定为当前体系，不接受跨体系节点迁移。 / Bind imported nodes to the explicitly selected scheme. */
function parseImport(f:string[]){return{code:f[0],name:f[1],parentCode:f[2],sortOrder:f[3]||'0',status:f[4]==='停用'||f[4]==='1'?'1':f[4]==='启用'||!f[4]||f[4]==='0'?'0':f[4],remark:f[5],schemeId:schemeId.value}}
/** 按父级在前顺序解析编码到稳定ID，失败逐条反馈。 / Resolve existing or earlier imported parents by code. */
async function importCategory(payload:Record<string,any>){const parent=payload.parentCode?rows.value.find(r=>r.code===payload.parentCode):null;if(payload.parentCode&&!parent)throw new Error('上级分类不存在，请先导入父级');const id=await request<string>('post','/system/category',{...payload,parentId:parent?.id||null});rows.value.push({...payload,id,schemeId:String(schemeId.value),parentId:parent?.id||null,sortOrder:Number(payload.sortOrder)} as Category)}
/** 导出当前结果，按深度排序便于重新导入父子关系。 / Export ancestors before descendants for safe reconstruction. */
function exportRows(){downloadFoundationFile(`${currentScheme.value?.name||'分类'}分类.csv`,encodeCsv([headers,...[...filtered.value].sort((a,b)=>a.depth-b.depth).map(r=>[r.code,r.name,rows.value.find(p=>p.id===r.parentId)?.code||'',String(r.sortOrder),r.status==='0'?'启用':'停用',r.remark])]))}
onMounted(loadSchemes)
</script>
<template>
  <StandardListLayout v-model:collapsed="collapsed">
    <template #filters><a-select v-model:value="schemeId" :options="schemeOptions" placeholder="请选择分类体系" show-search option-filter-prop="label" style="width:230px" @change="loadCategories" /><a-input v-model:value="keyword" placeholder="搜索分类编码 / 名称" allow-clear style="width:240px"><template #prefix><SearchOutlined /></template></a-input><a-select v-model:value="status" allow-clear placeholder="状态" :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" style="width:110px" /><a-button @click="manageOpen=true">分类体系</a-button></template>
    <template #sidebar><div class="tree-head"><strong>{{ currentScheme?.name||'分类树' }}</strong><a-button size="small" @click="selectedNode=undefined;selected=[]">重置</a-button></div><div class="tree-tip">选择节点查看自身及下级分类</div><a-tree v-if="tree.length" block-node :tree-data="tree" :selected-keys="selectedNode?[selectedNode]:[]" :expanded-keys="expanded" @select="keys=>{selectedNode=keys.length?String(keys[0]):undefined;selected=[]}" @expand="keys=>expanded=keys.map(String)" /><a-empty v-else :description="schemeId?'尚未建立分类':'请先选择分类体系'" style="margin-top:36px" /></template>
    <div class="category-main"><a-alert v-if="currentScheme?.status==='1'" type="warning" message="当前分类体系已停用，历史分类可查看；请先启用体系再维护节点。" style="margin-bottom:12px" />
      <FoundationTable storage-key="category-list" :columns="columns" :rows="filtered" :loading="loading" v-model:selected-keys="selected">
        <template #actions><a-button v-if="can('system:category:add')" type="primary" :disabled="!schemeId||currentScheme?.status!=='0'" @click="openAdd('category')"><PlusOutlined />新增</a-button><a-button type="primary" :disabled="!schemeId" @click="refresh">查询</a-button><a-button @click="reset">重置</a-button><a-button v-if="can('system:category:add')" :disabled="!schemeId||currentScheme?.status!=='0'" @click="importOpen=true">导入</a-button><a-button :disabled="!schemeId" @click="exportRows">导出</a-button><FoundationBatchActions :rows="batchRows" :can-edit="can('system:category:edit')&&currentScheme?.status==='0'" :can-remove="can('system:category:remove')" :update="batchUpdate" :remove="batchRemove" @done="batchDone" /><a-button type="link" @click="helpOpen=true">使用指引</a-button></template>
        <template #cell="{column,row}"><a-tag v-if="column.key==='status'" :color="row.status==='0'?'green':'default'">{{ row.status==='0'?'启用':'停用' }}</a-tag><span v-else>{{ row[column.key] ?? '—' }}</span></template>
        <template #action="{row}"><a-space><a-button v-if="can('system:category:edit')" type="primary" size="small" :disabled="currentScheme?.status!=='0'" @click="openEdit('category',row)">编辑</a-button><a-button v-if="can('system:category:add')" size="small" :disabled="currentScheme?.status!=='0'||row.status!=='0'||row.depth>=currentScheme.maxDepth" @click="openAdd('category',row.id)">新增下级</a-button><a-dropdown v-if="can('system:category:edit')||can('system:category:remove')"><a-button size="small">更多</a-button><template #overlay><a-menu><a-menu-item v-if="can('system:category:edit')" :disabled="currentScheme?.status!=='0'" @click="toggle('category',row)">{{ row.status==='0'?'停用':'启用' }}</a-menu-item><a-menu-item v-if="can('system:category:remove')" danger @click="remove('category',row)">删除</a-menu-item></a-menu></template></a-dropdown></a-space></template>
      </FoundationTable>
    </div>
  </StandardListLayout>
  <a-modal v-model:open="manageOpen" title="分类体系" :width="900" :mask-closable="false" :keyboard="false" :footer="null"><a-alert type="info" show-icon message="每种业务使用独立分类体系，例如物料分类、款式分类。节点不能跨体系移动；体系编码是业务集成标识，创建后不可修改。" style="margin-bottom:16px" /><a-button v-if="can('system:category:add')" type="primary" style="margin-bottom:12px" @click="openAdd('scheme')"><PlusOutlined />新增体系</a-button><a-table :data-source="schemes" :columns="[{key:'code',dataIndex:'code',title:'体系编码'},{key:'name',dataIndex:'name',title:'体系名称'},{key:'maxDepth',dataIndex:'maxDepth',title:'最大层级'},{key:'status',title:'状态'},{key:'action',title:'操作',width:220}]" row-key="id" :pagination="{pageSize:10}"><template #bodyCell="{column,record}"><template v-if="column.key==='status'">{{ record.status==='0'?'启用':'停用' }}</template><a-space v-if="column.key==='action'"><a-button v-if="can('system:category:edit')" type="primary" size="small" @click="openEdit('scheme',record as Scheme)">编辑</a-button><a-button v-if="can('system:category:edit')" size="small" @click="toggle('scheme',record as Scheme)">{{ record.status==='0'?'停用':'启用' }}</a-button><a-button v-if="can('system:category:remove')" danger size="small" @click="remove('scheme',record as Scheme)">删除</a-button></a-space></template></a-table></a-modal>
  <a-modal :open="formOpen" :title="`${editId?'编辑':'新增'}${mode==='scheme'?'分类体系':'分类'}`" :width="720" :mask-closable="false" :keyboard="false" :closable="!saving" :body-style="{maxHeight:'68vh',overflowY:'auto',overflowX:'hidden'}" @cancel="closeForm"><a-alert v-if="mode==='category'&&!editId" type="info" :message="`所属体系：${currentScheme?.name}；已带入上次成功内容或当前选择的上级，请检查后保存。`" style="margin-bottom:16px" /><a-form layout="vertical" :disabled="saving"><a-row :gutter="20"><a-col :span="12"><a-form-item :label="mode==='scheme'?'体系编码':'分类编码'" required><a-input ref="codeInput" v-model:value="form.code" :disabled="mode==='scheme'&&!!editId" :maxlength="40" /></a-form-item></a-col><a-col :span="12"><a-form-item :label="mode==='scheme'?'体系名称':'分类名称'" required><a-input v-model:value="form.name" :maxlength="80" /></a-form-item></a-col><a-col :span="12"><a-form-item v-if="mode==='category'" label="上级分类"><a-select :value="form.parentId ?? undefined" @change="setParent" allow-clear show-search option-filter-prop="label" :options="parentOptions" placeholder="不选则为根分类" /></a-form-item><a-form-item v-else label="最大层级" required><a-input-number v-model:value="form.maxDepth" :min="1" :max="20" :precision="0" style="width:100%" /></a-form-item></a-col><a-col :span="12"><a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" :max="999999" :precision="0" style="width:100%" /></a-form-item></a-col><a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" /></a-form-item></a-col></a-row></a-form><template #footer><a-button v-if="!editId&&mode==='category'" :disabled="saving" @click="clearForm">清空内容</a-button><a-button :disabled="saving" @click="closeForm">取消</a-button><a-button v-if="!editId&&mode==='category'" :loading="saving" @click="save(true)">保存并新增</a-button><a-button type="primary" :loading="saving" @click="save(false)">保存</a-button></template></a-modal>
  <FoundationImport v-model:open="importOpen" title="分类导入" :headers="headers" help="记录导入当前选定的体系；状态填启用或停用，留空默认启用。先导入父级，再导入下级；上级分类编码留空表示根分类。" :parse="parseImport" :submit="importCategory" @done="refresh" />
  <a-modal v-model:open="helpOpen" title="分类管理使用指引" :footer="null"><a-typography-paragraph>先打开“分类体系”新增业务体系，例如“物料分类”；选中体系后，新增根分类“面料”，再通过“新增下级”建立“针织面料”等节点。</a-typography-paragraph><a-typography-paragraph>左树选择节点会筛选自身及下级；重置清除树和搜索筛选，仍保留当前明确选择的体系。体系可以配置最大层级，各业务使用独立分类树。</a-typography-paragraph><a-typography-paragraph>停用体系会使全部下属分类不可用于新业务；单个分类停用前先停用下级。分类名称可改，历史业务仍关联稳定ID；已有引用不能删除。</a-typography-paragraph></a-modal>
</template>
<style scoped>.category-main{height:100%;min-height:0;display:flex;flex-direction:column}.tree-head{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-bottom:1px solid #f0f2f5}.tree-tip{font-size:12px;color:#86909c;padding:10px 14px}.category-main :deep(.foundation-table){flex:1}:deep(.std-sidebar .ant-tree){overflow:auto;padding:0 8px 12px}</style>
