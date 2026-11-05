<script setup lang="ts">
/**
 * 业务单位公共选择器：历史停用值可回显，新选择只允许有效单位。
 * Reusable unit selector preserves historical labels and disables unavailable references.
 */
import { computed, onMounted, ref } from 'vue'
import { foundationRequest, foundationError } from '../utils/foundation'
const props=defineProps<{value?:string;dimension?:string;disabled?:boolean;placeholder?:string}>()
const emit=defineEmits<{(e:'update:value',value:string|undefined):void}>()
const rows=ref<{id:string;code:string;name:string;symbol:string;dimension:string;selectable:boolean}[]>([]),loading=ref(false),error=ref('')
const options=computed(()=>rows.value.filter(r=>!props.dimension||r.dimension===props.dimension||r.id===props.value).map(r=>({value:r.id,label:`${r.name}${r.symbol?`（${r.symbol}）`:''}${!r.selectable?'（停用）':''}`,disabled:!r.selectable||Boolean(props.dimension&&r.dimension!==props.dimension)})))
/** 按登录员工读取公共选项，失败明确提示并允许重试。 / Load authenticated dictionary options without hiding request failures. */
async function reload(){loading.value=true;error.value='';try{rows.value=await foundationRequest('get','/system/unit/options')}catch(e){error.value=foundationError(e)}finally{loading.value=false}}
/** 规范化ID为字符串，不经Number丢精度。 / Preserve exact identifiers during selection. */
function change(value:unknown){emit('update:value',value==null?undefined:String(value))}
onMounted(reload)
defineExpose({reload})
</script>
<template><div class="unit-select"><a-select :value="value" :disabled="disabled" :loading="loading" :options="options" :placeholder="placeholder||'请选择计量单位'" allow-clear show-search option-filter-prop="label" style="width:100%" @change="change" /><a-typography-text v-if="error" type="danger">{{ error }} <a @click="reload">重试</a></a-typography-text></div></template>
