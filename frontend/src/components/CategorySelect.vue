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
