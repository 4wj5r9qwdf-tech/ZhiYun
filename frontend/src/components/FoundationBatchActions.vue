<script setup lang="ts">
/**
 * 单位和分类共用批量操作：按依赖深度排序，明确区分成功、跳过和失败。
 * Execute confirmed foundation batches in dependency order with explicit partial outcomes.
 */
import { ref } from 'vue'
import Modal from 'ant-design-vue/es/modal'
import BatchResultModal from './BatchResultModal.vue'
import { foundationError } from '../utils/foundation'
const props=defineProps<{rows:{id:string;name:string;status:string;depth?:number}[];canEdit:boolean;canRemove:boolean;update:(row:any,status:string)=>Promise<unknown>;remove:(row:any)=>Promise<unknown>}>()
const emit=defineEmits<{(e:'done'):void}>()
const open=ref(false),saving=ref(false),target=ref('1'),resultOpen=ref(false),succeeded=ref(0),skipped=ref(0),failures=ref<{name:string;reason:string}[]>([]),resultTitle=ref('')
/** 先处理子级删除/停用，启用则先处理父级；一次快照固定本次范围。 / Order a fixed selection by dependency direction. */
async function run(deleting=false){if(saving.value)return;saving.value=true;succeeded.value=0;skipped.value=0;failures.value=[];resultTitle.value=deleting?'批量删除结果':'批量启停结果';const targets=[...props.rows].sort((a,b)=>(deleting||target.value==='1'?-1:1)*((a.depth||1)-(b.depth||1)));try{for(const row of targets){if(!deleting&&row.status===target.value){skipped.value++;continue}try{if(deleting)await props.remove(row);else await props.update(row,target.value);succeeded.value++}catch(e){failures.value.push({name:row.name,reason:foundationError(e)})}}emit('done');open.value=false;resultOpen.value=true}finally{saving.value=false}}
/** 删除前展示选中对象，后端仍逐条保护下级和引用。 / Require explicit identity review before a destructive batch. */
function confirmDelete(){Modal.confirm({title:`删除选中的${props.rows.length}条档案？`,content:props.rows.map(r=>r.name).join('、'),okText:'确认删除',cancelText:'取消',okButtonProps:{danger:true},onOk:()=>run(true)})}
</script>
<template>
  <a-button v-if="rows.length&&canEdit" :disabled="saving" @click="open=true">批量启停</a-button><a-button v-if="rows.length&&canRemove" danger :disabled="saving" @click="confirmDelete">批量删除</a-button>
  <a-modal v-model:open="open" title="批量启停" :mask-closable="false" :keyboard="false" :closable="!saving" :confirm-loading="saving" :cancel-button-props="{disabled:saving}" @ok="run(false)"><p>将选中的 {{ rows.length }} 条记录调整为目标状态；相同状态跳过。停用按下级优先、启用按上级优先处理。</p><a-select v-model:value="target" :options="[{value:'0',label:'启用'},{value:'1',label:'停用'}]" style="width:100%" /></a-modal>
  <BatchResultModal v-model:open="resultOpen" :title="`${resultTitle}（跳过 ${skipped} 条）`" :succeeded="succeeded" :failures="failures" />
</template>
