<script setup lang="ts">
/** 上传校验的公共展示；记录只由业务侧提交，本组件不会操作数据。 */
defineProps<{ rows: { line: number; code: string; name: string; error: string }[] }>()
</script>
<template>
  <template v-if="rows.length">
    <p style="margin:12px 0">校验通过 {{ rows.filter(r => !r.error).length }} 条；失败 {{ rows.filter(r => r.error).length }} 条。</p>
    <a-table :columns="[{title:'行号',dataIndex:'line',key:'line',width:70},{title:'编码',dataIndex:'code',key:'code'},{title:'名称',dataIndex:'name',key:'name'},{title:'校验结果',dataIndex:'error',key:'error'}]" :data-source="rows" row-key="line" size="small" :pagination="{ pageSize: 5 }"><template #bodyCell="{column,record}"><template v-if="column.key === 'error'"><span :style="{color: record.error ? '#f53f3f' : '#00a870'}">{{ record.error || '通过' }}</span></template></template></a-table>
  </template>
</template>
