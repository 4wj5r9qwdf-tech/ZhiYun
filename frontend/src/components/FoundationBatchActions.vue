<!--
文件用途：为织云 ERP 页面提供可复用的FoundationBatchActions展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的FoundationBatchActions展示或交互能力。
设计思路：以属性接收调用页面提供的数据和配置，以事件返回用户操作；组件不决定业务授权。
实现方法：封装稳定视觉结构与通用交互，由业务页面传入数据并处理事件。
关键约束：不能绕过调用页或服务端的数据权限。
依赖与引用：Docs/规范/前端规范.md；frontend/src/views/SystemTemplate.vue
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
