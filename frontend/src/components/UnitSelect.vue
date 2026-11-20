<!--
文件用途：为织云 ERP 页面提供可复用的UnitSelect展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的UnitSelect展示或交互能力。
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
 * 业务单位公共选择器：历史停用值可回显，新选择只允许有效单位。
 * Reusable unit selector preserves historical labels and disables unavailable references.
 */
import { computed, onMounted, ref } from 'vue'
import { foundationRequest, foundationError } from '../utils/foundation'
const props=defineProps<{value?:string;dimension?:string;inventoryOnly?:boolean;disabled?:boolean;placeholder?:string}>()
const emit=defineEmits<{(e:'update:value',value:string|undefined):void}>()
const rows=ref<{id:string;code:string;name:string;symbol:string;dimension:string;baseUnitId:string|null;selectable:boolean}[]>([]),loading=ref(false),error=ref('')
const options=computed(()=>rows.value.filter(r=>(!props.inventoryOnly||!r.baseUnitId||r.id===props.value)&&(!props.dimension||r.dimension===props.dimension||r.id===props.value)).map(r=>({value:r.id,label:`${r.name}${r.symbol?`（${r.symbol}）`:''}${r.baseUnitId?'（辅助单位）':''}${!r.selectable?'（停用）':''}`,disabled:!r.selectable||Boolean(props.dimension&&r.dimension!==props.dimension)||Boolean(props.inventoryOnly&&r.baseUnitId&&r.id!==props.value)})))
/** 按登录员工读取公共选项，失败明确提示并允许重试。 / Load authenticated dictionary options without hiding request failures. */
async function reload(){loading.value=true;error.value='';try{rows.value=await foundationRequest('get','/system/unit/options')}catch(e){error.value=foundationError(e)}finally{loading.value=false}}
/** 规范化ID为字符串，不经Number丢精度。 / Preserve exact identifiers during selection. */
function change(value:unknown){emit('update:value',value==null?undefined:String(value))}
onMounted(reload)
defineExpose({reload})
</script>
<template><div class="unit-select"><a-select :value="value" :disabled="disabled" :loading="loading" :options="options" :placeholder="placeholder||'请选择计量单位'" allow-clear show-search option-filter-prop="label" style="width:100%" @change="change" /><a-typography-text v-if="error" type="danger">{{ error }} <a @click="reload">重试</a></a-typography-text></div></template>
