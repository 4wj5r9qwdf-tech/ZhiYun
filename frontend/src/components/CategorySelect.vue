<!--
文件用途：为织云 ERP 页面提供可复用的CategorySelect展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的CategorySelect展示或交互能力。
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
 * 按稳定体系编码加载分类，历史停用节点回显、新引用禁用。
 * Resolve reusable category trees by stable scheme code while honoring ancestor availability.
 */
import { computed, ref, watch } from 'vue'
import { foundationRequest, foundationError } from '../utils/foundation'
const props=defineProps<{value?:string;schemeCode:string;disabled?:boolean;placeholder?:string}>()
const emit=defineEmits<{(e:'update:value',value:string|undefined):void}>()
type Node={id:string;parentId:string|null;name:string;selectable:boolean}
type TreeNode={value:string;title:string;disabled:boolean;children:TreeNode[]}
const rows=ref<Node[]>([]),loading=ref(false),error=ref('')
const tree=computed(()=>{const index=new Map<string,TreeNode>(rows.value.map(r=>[r.id,{value:r.id,title:r.name+(!r.selectable?'（停用）':''),disabled:!r.selectable,children:[]}]));const roots:TreeNode[]=[];for(const row of rows.value){const node=index.get(row.id)!;const parent=row.parentId?index.get(row.parentId):null;if(parent)parent.children.push(node);else roots.push(node)}return roots})
let generation=0
/** 切换体系隔离异步响应，不让旧数据混入新体系。 / Guard asynchronous namespace changes with request generations. */
async function reload(){const version=++generation;rows.value=[];error.value='';if(!props.schemeCode){loading.value=false;return}loading.value=true;try{const schemes=await foundationRequest<{id:string;code:string}[]>('get','/system/category/scheme-options');if(version!==generation)return;const scheme=schemes.find(s=>s.code===props.schemeCode);if(!scheme)throw new Error('尚未建立当前业务分类体系，请先在分类管理中配置');const result=await foundationRequest<Node[]>('get','/system/category/options',{schemeId:scheme.id});if(version===generation)rows.value=result}catch(e){if(version===generation)error.value=foundationError(e)}finally{if(version===generation)loading.value=false}}
/** 仅传输稳定字符串ID。 / Emit exact category identifiers. */
function change(value:unknown){emit('update:value',value==null?undefined:String(value))}
watch(()=>props.schemeCode,reload,{immediate:true})
defineExpose({reload})
</script>
<template><div class="category-select"><a-tree-select :value="value" :disabled="disabled" :loading="loading" :tree-data="tree" :placeholder="placeholder||'请选择分类'" allow-clear show-search tree-node-filter-prop="title" tree-default-expand-all style="width:100%" @change="change" /><a-typography-text v-if="error" type="danger">{{ error }} <a @click="reload">重试</a></a-typography-text></div></template>
