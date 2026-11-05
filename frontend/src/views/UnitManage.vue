<script setup lang="ts">
/**
 * 全局计量单位档案；固定换算与物料专属换算明确分开，单位不是库存数量。
 * Manage shared units and explicit fixed conversion roots without material-specific assumptions.
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
type Unit = { id: string; code: string; name: string; symbol: string; dimension: string; decimalPlaces: number; baseUnitId: string | null; factor: string; sortOrder: number; status: string; remark: string }
const dimensions = [{ value:'length',label:'长度' },{ value:'mass',label:'质量' },{ value:'area',label:'面积' },{ value:'volume',label:'体积' },{ value:'time',label:'时间' },{ value:'count',label:'计数' },{ value:'other',label:'其他' }]
const columns = [{key:'code',title:'单位编码',width:120},{key:'name',title:'单位名称',width:120},{key:'symbol',title:'符号',width:90},{key:'dimensionName',title:'计量维度',width:100},{key:'decimalPlaces',title:'小数位数',width:100,numeric:true},{key:'conversionText',title:'固定换算关系',width:200},{key:'status',title:'状态',width:100},{key:'sortOrder',title:'排序',width:90,numeric:true},{key:'remark',title:'备注',width:160}]
const rows = ref<Unit[]>([]), loading = ref(false), keyword = ref(''), dimension = ref<string>(), status = ref<string>(), selected = ref<string[]>([])
const helpOpen = ref(false), formOpen = ref(false), saving = ref(false), importOpen = ref(false), editId = ref(''), initial = ref('')
/** 构造独立单位默认值，不自动假设包装换算。 / Start with an independent unit rather than an invented conversion. */
function emptyForm(): Unit { return {id:'',code:'',name:'',symbol:'',dimension:'count',decimalPlaces:0,baseUnitId:null,factor:'1',sortOrder:0,status:'0',remark:''} }
const form = ref<Unit>(emptyForm())
const codeInput=ref<{focus:()=>void}>()
type ChildDraft = { id:string; code:string; name:string; symbol:string; decimalPlaces:number; factor:string; saved:boolean; error:string; original:Unit|null; snapshot:string; detached:boolean }
const childRows=ref<ChildDraft[]>([]), createdRootId=ref(''), newType=ref<'stock'|'auxiliary'>('stock'), initialChildren=ref('[]')
/** 添加待创建的换算单位。 / Add a conversion unit draft. */
function addChild(){childRows.value.push({id:'',code:'',name:'',symbol:'',decimalPlaces:form.value.dimension==='time'?8:3,factor:'',saved:false,error:'',original:null,snapshot:'',detached:false})}
/** 聚焦编码便于连续录入，等待弹窗完成渲染。 / Focus the editable code after rendering a fresh draft. */
async function focusCode(){await nextTick();codeInput.value?.focus()}
const defaults = createSavedFormDefaults(sessionStorage, `unit:add:${localStorage.getItem('userId') || 'anon'}`, emptyForm, ['id','code'])
/** 获取可读参照单位名。 / Resolve stable IDs to current dictionary labels. */
function rootName(id: string | null) { return rows.value.find(r=>r.id===id)?.name || '未知参照单位' }
/** 去除十进制末尾补零，仅改变展示，不转换为浮点数。 / Trim decimal padding without numeric precision loss. */
function displayFactor(value:string){return value.includes('.')?value.replace(/0+$/,'').replace(/\.$/,''):value}
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter(r=>(!query || [r.code,r.name,r.symbol,r.remark].some(v=>v.toLowerCase().includes(query))) && (!dimension.value || r.dimension===dimension.value) && (!status.value || r.status===status.value)).map(r=>({...r,dimensionName:dimensions.find(d=>d.value===r.dimension)?.label,conversionText:r.baseUnitId?`1 ${r.name} = ${displayFactor(r.factor)} ${rootName(r.baseUnitId)}`:'库存单位'}))
})
const roots = computed(() => rows.value.filter(r=>!r.baseUnitId && r.dimension===form.value.dimension && r.id!==editId.value && (r.status==='0' || r.id===form.value.baseUnitId)).map(r=>({value:r.id,label:`${r.name}（${r.code}）`,disabled:r.status!=='0'})))
/** 加载真实目录，网络失败保留当前界面并反馈原因。 / Load the shared directory through authenticated APIs. */
async function load() { loading.value=true; try {rows.value=await request<Unit[]>('get','/system/unit/list')} catch(e){message.error(foundationError(e))} finally{loading.value=false} }
/** 清除全部筛选和选择。 / Reset all active filters and batch targets. */
function reset() { keyword.value='';dimension.value=undefined;status.value=undefined;selected.value=[] }
/** 新增沿用上次成功业务内容，主键和编码始终清空。 / Reuse safe successful defaults for continuous entry. */
function openAdd() { editId.value='';newType.value='stock';createdRootId.value='';childRows.value=[];initialChildren.value='[]';form.value=emptyForm();initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode() }
/** 编辑从真实记录复制，不污染表格行。 / Edit a detached persisted snapshot. */
function openEdit(row: Unit) {
  createdRootId.value='';editId.value=row.id;form.value={...row}
  childRows.value=row.baseUnitId?[]:rows.value.filter(r=>r.baseUnitId===row.id).map(r=>({id:r.id,code:r.code,name:r.name,symbol:r.symbol,decimalPlaces:r.decimalPlaces,factor:displayFactor(r.factor),saved:false,error:'',original:{...r},snapshot:JSON.stringify([r.code,r.name,r.symbol,r.decimalPlaces,displayFactor(r.factor)]),detached:false}))
  initialChildren.value=JSON.stringify(childRows.value);initial.value=JSON.stringify(form.value);formOpen.value=true;void focusCode()
}
/** 未保存关闭前提示，保存期间阻止关闭。 / Protect unsaved form content. */
function closeForm() { if(saving.value)return; if(initial.value===JSON.stringify(form.value)&&initialChildren.value===JSON.stringify(childRows.value)&&!createdRootId.value){formOpen.value=false;return} Modal.confirm({title:'放弃未保存的修改？',okText:'放弃修改',cancelText:'继续编辑',onOk:()=>{formOpen.value=false}}) }
/** 清空新增内容，不影响已保存档案。 / Clear only the current unsaved draft. */
function clearForm() {form.value=emptyForm();childRows.value=[]}
/** 改维度时清空不再适用的参照关系。 / Drop incompatible conversion settings when dimension changes. */
/** 参照选择允许清空，恢复独立单位系数。 / Normalize an optional conversion root. */
function setBase(value: unknown) {form.value.baseUnitId=value?String(value):null;form.value.factor='1';if(!editId.value&&form.value.baseUnitId){const root=rows.value.find(r=>r.id===form.value.baseUnitId);if(root){form.value.dimension=root.dimension;if(root.dimension==='time')form.value.decimalPlaces=8}}}
/** 新增时选择单位用途，避免缓存带入错误的参照关系。 / Reset reference fields when switching new unit type. */
function changeNewType(){form.value.baseUnitId=null;form.value.factor='1';childRows.value=[]}
function changeDimension() {form.value.baseUnitId=null;form.value.factor='1';if(form.value.dimension==='time'){if(!editId.value)form.value.decimalPlaces=8;childRows.value.filter(r=>!r.id).forEach(r=>{r.decimalPlaces=8})}}
/** 校验并保存后才写入成功缓存，连续新增不重复保存同一主键。 / Persist before updating defaults or reopening a fresh create draft. */
async function save(again=false) {
  if(saving.value)return
  if(!form.value.code.trim() || !form.value.name.trim()){message.warning('请填写单位编码和名称');return}
  if(!editId.value&&newType.value==='auxiliary'&&!form.value.baseUnitId){message.warning('请选择库存单位');return}
  if(form.value.baseUnitId&&childRows.value.length){message.warning('换算单位不能再添加下级，请先移除关联换算单位');return}
  if(childRows.value.some(r=>!r.saved&&!r.detached&&(!r.code.trim()||!r.name.trim()||!r.factor.trim()))){message.warning('请填写各换算单位的编码、名称和系数');return}
  saving.value=true
  try {
    let rootId=createdRootId.value
    if(!rootId){rootId=await request<string>(editId.value?'put':'post',editId.value?`/system/unit/${editId.value}`:'/system/unit',form.value);createdRootId.value=rootId}
    let failed=0
    for(const child of childRows.value.filter(r=>!r.saved)){
      child.error=''
      const values=JSON.stringify([child.code,child.name,child.symbol,child.decimalPlaces,child.factor])
      if(child.id&&!child.detached&&values===child.snapshot)continue
      try{const payload=child.detached?{...child.original,baseUnitId:null,factor:'1'}:{...(child.original||emptyForm()),code:child.code.trim(),name:child.name.trim(),symbol:child.symbol.trim(),dimension:form.value.dimension,decimalPlaces:child.decimalPlaces,baseUnitId:rootId,factor:child.factor.trim()};await request(child.id?'put':'post',child.id?`/system/unit/${child.id}`:'/system/unit',payload);child.saved=true}
      catch(e){child.error=foundationError(e);failed++}
    }
    await load()
    if(failed){message.warning(`库存单位已保存，${failed} 个换算单位未保存，请修改后重试`);return}
    if(!editId.value)defaults.save(form.value)
    message.success('保存成功');createdRootId.value='';childRows.value=[]
    if(again){form.value={...defaults.load(),baseUnitId:null,factor:'1'};initial.value=JSON.stringify(form.value);void focusCode()}else formOpen.value=false
  }catch(e){message.error(foundationError(e))}finally{saving.value=false}
}
/** 删除前明确展示目标，服务端保护已有业务引用。 / Confirm identity before protected deletion. */
function remove(row: Unit) {Modal.confirm({title:`删除单位「${row.name}」？`,content:`编码：${row.code}。被引用的单位不能删除。`,okText:'删除',okButtonProps:{danger:true},cancelText:'取消',onOk:async()=>{try{await request('delete',`/system/unit/${row.id}`);await load();selected.value=selected.value.filter(id=>id!==row.id)}catch(e){message.error(foundationError(e));throw e}}})}
/** 启停复用完整保存契约和后端引用约束。 / Update lifecycle through the same validated mutation contract. */
function toggle(row: Unit) {Modal.confirm({title:`${row.status==='0'?'停用':'启用'}单位「${row.name}」？`,content:'停用保留历史显示，并阻止新增业务引用。',okText:'确认',cancelText:'取消',onOk:async()=>{try{await request('put',`/system/unit/${row.id}`,{...row,status:row.status==='0'?'1':'0'});await load()}catch(e){message.error(foundationError(e));throw e}}})}
const batchRows=computed(()=>rows.value.filter(r=>selected.value.includes(r.id)).map(r=>({...r,depth:r.baseUnitId?2:1})))
/** 批量写入复用同一后端契约。 / Delegate batch mutations to the existing validated API. */
async function batchUpdate(row:Unit,status:string){await request('put',`/system/unit/${row.id}`,{...row,status})}
/** 批量删除仍逐条执行引用保护。 / Keep server-side reference checks for every selected record. */
async function batchRemove(row:Unit){await request('delete',`/system/unit/${row.id}`)}
/** 批量完成后清空选择并刷新真实状态。 / Reload after partial or complete batch execution. */
function batchDone(){selected.value=[];void load()}
type ConversionDraft = { unitId:string|undefined; factor:string; saved:boolean; error:string; existing:boolean; initialFactor:string; detached:boolean }
const convertOpen=ref(false),conversionRootId=ref<string>(),conversionSaving=ref(false),conversionRows=ref<ConversionDraft[]>([])
const inventoryUnitOptions=computed(()=>rows.value.filter(r=>r.status==='0'&&!r.baseUnitId).map(r=>({value:r.id,label:`${r.name}（${r.code}）`})))
const conversionRootLocked=computed(()=>conversionSaving.value||conversionRows.value.some(r=>r.saved))
/** 根据所选库存单位列出同维度的已有单位，实际可否修改由后端引用保护决定。 / Select existing same-dimension units and retain server-side reference checks. */
const conversionOptions=computed(()=>{const root=rows.value.find(r=>r.id===conversionRootId.value);return rows.value.filter(r=>root&&r.id!==root.id&&r.dimension===root.dimension&&(r.status==='0'||r.baseUnitId===root.id)).map(r=>({value:r.id,label:`${r.name}（${r.code}）${r.status==='1'?' · 已停用':''}`}))})
function openConversionSetup(){conversionRootId.value=undefined;conversionRows.value=[];convertOpen.value=true}
/** 选中库存单位后读取全部已有辅助单位，保留精确的十进制系数。 / Load all existing child units with exact string factors. */
function loadConversionRows(){conversionRows.value=rows.value.filter(r=>r.baseUnitId===conversionRootId.value).map(r=>({unitId:r.id,factor:displayFactor(r.factor),initialFactor:displayFactor(r.factor),existing:true,saved:false,error:'',detached:false}))}
function addConversionRow(){conversionRows.value.push({unitId:undefined,factor:'',initialFactor:'',existing:false,saved:false,error:'',detached:false})}
function removeConversionRow(index:number){if(!conversionRows.value[index]?.saved)conversionRows.value.splice(index,1)}
/** 逐项更新已有单位，保留成功和失败状态供重试。 / Update existing units individually and retain failures. */
async function saveConversionRows(){
  const root=rows.value.find(r=>r.id===conversionRootId.value&&!r.baseUnitId&&r.status==='0')
  if(!root){message.warning('请选择启用的独立参照单位作为库存单位');return}
  const pending=conversionRows.value.filter(r=>!r.saved&&(r.detached||!r.existing||displayFactor(r.factor.trim())!==r.initialFactor))
  if(!pending.length){message.info('没有需要保存的换算系数');return}
  conversionSaving.value=true;let succeeded=0,failed=0
  try{for(const draft of pending){draft.error='';const unit=rows.value.find(r=>r.id===draft.unitId)
    if(!unit||(!draft.detached&&!draft.factor.trim())){draft.error='请选择单位并填写系数';failed++;continue}
    if(conversionRows.value.filter(r=>r.unitId===draft.unitId).length>1){draft.error='单位不能重复选择';failed++;continue}
    try{await request('put',`/system/unit/${unit.id}`,{...unit,baseUnitId:draft.detached?null:root.id,factor:draft.detached?'1':draft.factor.trim()});draft.saved=true;succeeded++}catch(e){draft.error=foundationError(e);failed++}
  }await load();if(failed)message.warning(`${succeeded} 项已保存，${failed} 项未成功，请修正后重试`);else{message.success(`已设置 ${succeeded} 项换算关系`);convertOpen.value=false}}
  finally{conversionSaving.value=false}
}
const headers=['单位编码','单位名称','符号','计量维度','小数位数','参照单位编码','换算系数','排序','状态','备注']
/** CSV字段映射保持编码和系数为字符串，维度使用明确机器键。 / Parse text codes and exact ratios without numeric ID coercion. */
function parseImport(fields:string[]) {return {code:fields[0],name:fields[1],symbol:fields[2],dimension:dimensions.find(d=>d.label===fields[3]||d.value===fields[3])?.value||fields[3]||'count',decimalPlaces:fields[4]||(fields[3]==='时间'||fields[3]==='time'?'8':'0'),baseCode:fields[5],factor:fields[6]||'1',sortOrder:fields[7]||'0',status:fields[8]==='停用'||fields[8]==='1'?'1':fields[8]==='启用'||!fields[8]||fields[8]==='0'?'0':fields[8],remark:fields[9]}}
/** 参照编码解析到稳定ID；先导入根单位，再导入换算单位。 / Resolve previously existing or imported roots before each create. */
async function importUnit(payload:Record<string,any>) {const base=payload.baseCode?rows.value.find(r=>r.code===payload.baseCode):null;if(payload.baseCode&&!base)throw new Error('参照单位不存在，请先导入独立参照单位');const id=await request<string>('post','/system/unit',{...payload,baseUnitId:base?.id||null});rows.value.push({...emptyForm(),...payload,id,baseUnitId:base?.id||null,decimalPlaces:Number(payload.decimalPlaces),sortOrder:Number(payload.sortOrder)})}
/** 导出当前筛选结果，保留可再次导入的单位编码关系。 / Export the filtered directory using the import contract. */
function exportRows() {downloadFoundationFile('计量单位.csv',encodeCsv([headers,...filtered.value.map(r=>[r.code,r.name,r.symbol,dimensions.find(d=>d.value===r.dimension)?.label||r.dimension,String(r.decimalPlaces),rows.value.find(x=>x.id===r.baseUnitId)?.code||'',r.factor,String(r.sortOrder),r.status==='0'?'启用':'停用',r.remark])]))}
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters><a-input v-model:value="keyword" placeholder="搜索单位编码 / 名称 / 符号" allow-clear style="width:260px"><template #prefix><SearchOutlined /></template></a-input><a-select v-model:value="dimension" :options="dimensions" placeholder="计量维度" allow-clear style="width:140px" /><a-select v-model:value="status" placeholder="状态" allow-clear :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" style="width:110px" /></template>
    <FoundationTable storage-key="unit-list-v2" :action-width="120" :columns="columns" :rows="filtered" :loading="loading" v-model:selected-keys="selected">
      <template #actions><a-button v-if="can('system:unit:add')" type="primary" @click="openAdd"><PlusOutlined />新增</a-button><a-button type="primary" @click="load">查询</a-button><a-button @click="reset">重置</a-button><a-button v-if="can('system:unit:add')" @click="importOpen=true">导入</a-button><a-button @click="exportRows">导出</a-button><FoundationBatchActions :rows="batchRows" :can-edit="can('system:unit:edit')" :can-remove="can('system:unit:remove')" :update="batchUpdate" :remove="batchRemove" @done="batchDone" /><a-button v-if="can('system:unit:edit')" @click="openConversionSetup">批量设置换算系数</a-button><a-button type="link" @click="helpOpen=true">使用指引</a-button></template>
      <template #cell="{column,row}"><a-switch v-if="column.key==='status'" :checked="row.status==='0'" :disabled="!can('system:unit:edit')" checked-children="启用" un-checked-children="停用" @change="toggle(row)" /><span v-else>{{ row[column.key] ?? '—' }}</span></template>
      <template #action="{row}"><div class="unit-ops"><a-button v-if="can('system:unit:edit')" type="primary" size="small" @click="openEdit(row)">编辑</a-button><a-button v-if="can('system:unit:remove')" danger size="small" @click="remove(row)">删除</a-button></div></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal :open="formOpen" :title="editId?'编辑计量单位':'新增计量单位'" :width="920" :mask-closable="false" :keyboard="false" :closable="!saving" class="unit-dialog" :body-style="{maxHeight:'68vh',overflowY:'auto',overflowX:'hidden'}" @cancel="closeForm">
    <a-alert v-if="!editId" message="使用“保存并新增”可带入刚保存的内容；编码需重新填写。" type="info" show-icon style="margin-bottom:16px" />
    <a-alert v-if="createdRootId" type="warning" show-icon message="单位已保存；请修正未成功的辅助单位后再次保存。" style="margin-bottom:16px" />
    <div v-if="!editId" class="unit-type"><span class="unit-type__label">单位类型</span><a-radio-group v-model:value="newType" :disabled="saving||!!createdRootId" @change="changeNewType"><a-radio value="stock">库存单位</a-radio><a-radio value="auxiliary">辅助单位</a-radio></a-radio-group><span class="unit-type__hint">库存单位用于库存计量，辅助单位用于换算。</span></div>
    <section class="unit-section"><div class="unit-section__heading"><span class="unit-section__title">{{ editId ? (form.baseUnitId ? '辅助单位信息' : '库存单位信息') : (newType === 'stock' ? '库存单位信息' : '辅助单位信息') }}</span><span class="unit-section__hint">带 * 为必填项</span></div>
    <a-form layout="vertical" :disabled="saving||!!createdRootId">
      <a-row :gutter="20">
        <a-col :span="8"><a-form-item label="单位编码" required><a-input ref="codeInput" v-model:value="form.code" :maxlength="40" placeholder="例如 M" /></a-form-item></a-col>
        <a-col :span="8"><a-form-item label="单位名称" required><a-input v-model:value="form.name" :maxlength="80" placeholder="例如 米" /></a-form-item></a-col>
        <a-col :span="8"><a-form-item label="符号"><a-input v-model:value="form.symbol" :maxlength="20" placeholder="例如 m" /></a-form-item></a-col>
        <a-col :span="8"><a-form-item label="计量维度" required><a-select v-model:value="form.dimension" :options="dimensions" :disabled="!editId&&newType==='auxiliary'" @change="changeDimension" /></a-form-item></a-col>
        <a-col :span="8"><a-form-item label="小数位数" :required="form.dimension!=='time'" :extra="form.dimension==='time' ? '时间维度由系统处理，工时按时分秒显示。' : undefined"><a-input-number v-model:value="form.decimalPlaces" :min="0" :max="8" :precision="0" :disabled="form.dimension==='time'" style="width:100%" /></a-form-item></a-col>
        <a-col :span="8"><a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" :max="999999" :precision="0" style="width:100%" /></a-form-item></a-col>
        <template v-if="editId||newType==='auxiliary'">
          <a-col :span="12"><a-form-item label="库存单位" :required="!editId"><a-select :value="form.baseUnitId ?? undefined" :allow-clear="!!editId" show-search option-filter-prop="label" :options="editId ? roots : inventoryUnitOptions" placeholder="请选择库存单位" @change="setBase" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="换算系数" required><a-input v-model:value="form.factor" :disabled="!form.baseUnitId" placeholder="1本单位 = 系数 × 参照单位" /></a-form-item></a-col>
        </template>
        <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" /></a-form-item></a-col>
      </a-row>
    </a-form></section>
    <section v-if="(!editId&&newType==='stock')||(!!editId&&!form.baseUnitId)" class="unit-section unit-section--aux">
      <div class="unit-section__heading"><div><span class="unit-section__title">辅助单位</span><span class="unit-count">{{ childRows.length }} 项</span></div><a-button v-if="can('system:unit:add')" :disabled="saving" @click="addChild"><PlusOutlined />添加辅助单位</a-button></div>
      <div class="unit-section__description">计量维度：{{ dimensions.find(d => d.value === form.dimension)?.label }}。1 个辅助单位 = 换算系数 × 库存单位，例如 1 厘米 = 0.01 米。</div>
      <div class="unit-section__tip">移除绑定后，辅助单位将转为库存单位；该行未保存的修改不会带入，已有业务引用时可能无法变更。</div>
      <a-row v-if="childRows.length" :gutter="8" class="unit-row-head"><a-col :span="4">编码</a-col><a-col :span="4">名称</a-col><a-col :span="3">符号</a-col><a-col :span="3">小数位数</a-col><a-col :span="4">换算系数</a-col><a-col :span="6">状态 / 操作</a-col></a-row>
      <div v-for="(child,index) in childRows" :key="index" class="unit-row"><a-row :gutter="8" align="middle">
        <a-col :span="4"><a-input v-model:value="child.code" placeholder="例如 CM" :maxlength="40" :disabled="saving||child.saved||child.detached" /></a-col>
        <a-col :span="4"><a-input v-model:value="child.name" placeholder="例如 厘米" :maxlength="80" :disabled="saving||child.saved||child.detached" /></a-col>
        <a-col :span="3"><a-input v-model:value="child.symbol" placeholder="例如 cm" :maxlength="20" :disabled="saving||child.saved||child.detached" /></a-col>
        <a-col :span="3"><a-input-number v-model:value="child.decimalPlaces" :min="0" :max="8" :precision="0" style="width:100%" :disabled="saving||child.saved||child.detached||form.dimension==='time'" /></a-col>
        <a-col :span="4"><a-input v-model:value="child.factor" placeholder="例如 0.01" :disabled="saving||child.saved||child.detached" /></a-col>
        <a-col :span="6"><a-tag v-if="child.saved" color="green">{{ child.detached ? '已转为库存单位' : '已保存' }}</a-tag><template v-else><a-tag v-if="child.detached" color="orange">保存后转为库存单位</a-tag><a-button v-if="child.id" type="link" :danger="!child.detached" :disabled="saving" @click="child.detached=!child.detached;child.error=''">{{ child.detached ? '撤销移除' : '移除绑定' }}</a-button><a-button v-else type="link" danger :disabled="saving" @click="childRows.splice(index,1)">移除</a-button></template></a-col>
      </a-row><div v-if="child.error" class="unit-row__error">{{ child.error }}</div></div>
      <div v-if="!childRows.length" class="unit-empty">暂无辅助单位，可按实际业务需要添加。</div>
    </section>
    <a-alert type="warning" show-icon message="箱、卷、套不能默认换算成件或米；重量与长度换算应由物料或批次另行定义。已有引用后不能改变单位的换算语义和精度。" />
    <template #footer><a-button v-if="!editId" :disabled="saving||!!createdRootId" @click="clearForm">清空内容</a-button><a-button :disabled="saving" @click="closeForm">取消</a-button><a-button v-if="!editId" :loading="saving" @click="save(true)">保存并新增</a-button><a-button type="primary" :loading="saving" @click="save(false)">保存</a-button></template>
  </a-modal>
  <FoundationImport v-model:open="importOpen" title="计量单位导入" :headers="headers" help="先导入独立参照单位，再导入换算单位；维度填长度、质量、面积、体积、时间、计数或其他；状态填启用或停用。参照单位编码可空，独立单位系数必须为1。换算系数方向是“1个本单位 = 系数 × 参照单位”，例如厘米参照米时填0.01。时间维度的小数位数可留空，系统默认8位；已有导出值会保留。状态留空默认启用。" :parse="parseImport" :submit="importUnit" @done="load" />
  <a-modal v-model:open="convertOpen" title="批量设置换算系数" :width="760" :mask-closable="false" :keyboard="false" :closable="!conversionSaving" class="unit-dialog">
    <a-alert type="info" show-icon message="选择库存单位及已有换算单位，填写系数。例：库存单位为米，选择厘米，系数填0.01。移除绑定后将转为库存单位；已被业务引用的单位可能无法修改换算关系。" style="margin-bottom:16px" />
    <div class="unit-section"><div class="unit-section__heading"><span class="unit-section__title">选择库存单位</span></div><a-form-item label="库存单位" required><a-select v-model:value="conversionRootId" :options="inventoryUnitOptions" placeholder="选择独立参照单位" :disabled="conversionRootLocked" @change="loadConversionRows" /></a-form-item></div>
    <div class="unit-section unit-section--aux"><div class="unit-section__heading"><span class="unit-section__title">设置辅助单位及系数</span><a-button :disabled="conversionSaving" @click="addConversionRow"><PlusOutlined />添加一行</a-button></div>
    <a-row v-for="(row,index) in conversionRows" :key="index" :gutter="12" style="margin-bottom:12px"><a-col :span="9"><a-select v-model:value="row.unitId" show-search option-filter-prop="label" :options="conversionOptions" placeholder="选择已有单位" style="width:100%" :disabled="row.existing||row.saved||conversionSaving" /></a-col><a-col :span="6"><a-input v-model:value="row.factor" placeholder="换算系数" :disabled="row.detached||row.saved||conversionSaving" /></a-col><a-col :span="9"><a-tag v-if="row.saved" color="green">{{ row.detached ? '已转为库存单位' : '已保存' }}</a-tag><template v-else><a-tag v-if="row.detached" color="orange">保存后转为库存单位</a-tag><a-button v-if="row.existing" type="link" :danger="!row.detached" :disabled="conversionSaving" @click="row.detached=!row.detached;row.error=''">{{ row.detached ? '撤销移除' : '移除绑定' }}</a-button><a-button v-else type="link" danger :disabled="conversionSaving" @click="removeConversionRow(index)">移除</a-button></template><a-typography-text v-if="row.error" type="danger">{{ row.error }}</a-typography-text></a-col></a-row>
    <div v-if="conversionRootId&&!conversionRows.length" class="unit-empty">该库存单位暂无辅助单位，可添加已有单位。</div>
    </div>
    <template #footer><a-button :disabled="conversionSaving" @click="convertOpen=false">关闭</a-button><a-button type="primary" :loading="conversionSaving" @click="saveConversionRows">保存全部</a-button></template>
  </a-modal>
  <a-modal v-model:open="helpOpen" title="计量单位使用指引" :width="680" :footer="null"><a-typography-paragraph>建议先录入实际业务会用到的库存基本单位，例如个（或件）、千克、米、升、平方米；先统一“个”和“件”等相近叫法，避免同一含义重复建档。物料建档时再为每种物料选择自己的库存基本单位。</a-typography-paragraph><a-typography-paragraph>新增时可选择库存单位或辅助单位；单独新增辅助单位时选择所属库存单位。新增或编辑库存单位时，可在下方添加辅助单位；编辑时也可修改已有辅助单位。已有单位可通过“批量设置换算系数”选择并设置换算关系。例如库存单位为米，厘米和毫米的系数分别填0.01和0.001。系数方向为“1个辅助单位等于多少库存单位”。</a-typography-paragraph><a-typography-paragraph>“箱”“包”“卷”“套”等包装或业务单位，只有所有适用物料的比例都固定一致时才建立全局换算；每种物料每箱件数、每卷长度等不同规格，应由物料或对应业务单独定义，不要在全局单位中假定比例。</a-typography-paragraph><a-typography-paragraph>小数位数表示该单位数量的展示精度；作为换算目标单位时，换算结果按目标单位的小数位数采用四舍五入（HALF_UP）。后续库存、物料和单据功能必须读取单位精度，使用十进制定点计算并明确输入校验、存储精度和舍入节点；不能用浮点数、固定两位小数或在中间计算时提前舍入。单位精度一旦被子单位或业务引用，不可修改；需要改变时新增单位并迁移业务口径。</a-typography-paragraph><a-typography-paragraph>停用只影响新业务选择，历史业务仍保留单位信息。先停用辅助单位，再停用其库存单位。</a-typography-paragraph></a-modal>
</template>

<style scoped>
.unit-ops { display:flex; align-items:center; gap:6px; white-space:nowrap; }
.unit-ops :deep(.ant-btn-sm) { padding-inline:5px; }
.unit-type { display:flex; align-items:center; gap:20px; flex-wrap:wrap; padding:12px 16px; margin-bottom:16px; background:#f5f8ff; border:1px solid #e5edff; border-radius:8px; }
.unit-type__label { color:#1f2937; font-weight:600; }
.unit-type__hint, .unit-section__hint { color:#8c98a8; font-size:12px; }
.unit-section { margin-bottom:16px; padding:16px 18px 2px; border:1px solid #e8edf3; border-radius:8px; background:#fff; }
.unit-section--aux { padding-bottom:16px; background:#fafbfc; }
.unit-section__heading { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:16px; }
.unit-section__title { color:#202b3a; font-size:15px; font-weight:600; padding-left:10px; border-left:3px solid #1677ff; }
.unit-count { margin-left:10px; color:#8c98a8; font-size:12px; }
.unit-section__description { color:#526273; font-size:13px; line-height:1.7; }
.unit-section__tip { color:#8a6d3b; font-size:12px; line-height:1.6; margin:4px 0 12px; }
.unit-row-head { color:#728196; font-size:12px; padding:0 8px; margin:12px 0 6px; }
.unit-row { background:#fff; border:1px solid #e8edf3; border-radius:6px; padding:10px 8px; margin-bottom:8px; }
.unit-row__error { color:#ff4d4f; font-size:12px; margin-top:6px; overflow-wrap:anywhere; }
.unit-empty { margin-top:12px; padding:22px 12px; color:#98a3b1; text-align:center; border:1px dashed #dce3ec; border-radius:6px; background:#fff; }
.unit-dialog :deep(.ant-form-item) { margin-bottom:16px; }
@media (max-width: 700px) { .unit-type { gap:8px; } .unit-row-head { display:none; } .unit-row :deep(.ant-col) { flex:0 0 50%; max-width:50%; margin-bottom:8px; } }
</style>
