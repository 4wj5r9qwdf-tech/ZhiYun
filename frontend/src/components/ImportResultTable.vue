<!--
文件用途：为织云 ERP 页面提供可复用的ImportResultTable展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的ImportResultTable展示或交互能力。
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
/** 上传校验的公共展示；记录只由业务侧提交，本组件不会操作数据。 */
import { computed } from 'vue'
const props = withDefaults(defineProps<{ rows: { line: number; code: string; name: string; error: string }[]; extraColumns?: { title: string; dataIndex: string; key: string; width?: number }[] }>(), { extraColumns: () => [] })
const columns = computed(() => [{ title:'行号',dataIndex:'line',key:'line',width:70 }, ...props.extraColumns, { title:'编码',dataIndex:'code',key:'code' }, { title:'名称',dataIndex:'name',key:'name' }, { title:'校验结果',dataIndex:'error',key:'error' }])
</script>
<template>
  <template v-if="rows.length">
    <p style="margin:12px 0">校验通过 {{ rows.filter(r => !r.error).length }} 条；失败 {{ rows.filter(r => r.error).length }} 条。</p>
    <a-table :columns="columns" :scroll="extraColumns.length ? { x: 900 } : undefined" :data-source="rows" row-key="line" size="small" :pagination="{ pageSize: 5 }"><template #bodyCell="{column,record}"><template v-if="column.key === 'error'"><span :style="{color: record.error ? '#f53f3f' : '#00a870'}">{{ record.error || '通过' }}</span></template></template></a-table>
  </template>
</template>
