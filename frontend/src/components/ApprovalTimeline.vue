<script setup lang="ts">
/** 统一展示审批节点、办理人、时间和意见。 / Render approval history and pending nodes consistently. */
import { computed } from 'vue'
const props = withDefaults(defineProps<{ items?: any[]; pendingNode?: string; pendingAssignees?: string }>(), { items: () => [], pendingNode: '', pendingAssignees: '' })
const records = computed(() => [...props.items].filter(item => item.flowStatus !== 'copy').sort((a,b) => {
  const pendingA = a.flowStatus === 'waiting' && !a.updateTime
  const pendingB = b.flowStatus === 'waiting' && !b.updateTime
  if (pendingA !== pendingB) return pendingA ? 1 : -1
  return String(a.updateTime || a.createTime || '').localeCompare(String(b.updateTime || b.createTime || ''))
}))
function pending(item:any) { return item.flowStatus === 'waiting' && !item.updateTime }
function action(item:any) {
  if (pending(item)) return '待处理'
  const value = item.action || item.skipType
  if (item.nodeName === '开始') return '发起流程'
  if (item.nodeName === '申请人' && value === 'PASS') return '提交审批'
  return ({PASS:'通过',REJECT:'退回',NONE:'记录'} as Record<string,string>)[value] || item.flowStatusName || value || '记录'
}
function time(value:any) { if (!value) return '—'; const d = new Date(value); return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', {hour12:false}) }
</script>
<template>
  <a-timeline v-if="records.length || pendingNode">
    <a-timeline-item v-for="(item,index) in records" :key="item.id || index" :color="pending(item) ? 'blue' : (item.action || item.skipType) === 'REJECT' ? 'red' : 'green'">
      <div class="timeline-heading"><strong>{{ item.nodeName || '审批节点' }}</strong><a-tag :color="pending(item) ? 'blue' : (item.action || item.skipType) === 'REJECT' ? 'red' : 'green'">{{ action(item) }}</a-tag></div>
      <div class="timeline-meta">{{ pending(item) ? '等待 ' : '' }}{{ item.approverName || item.approver || '系统' }}{{ pending(item) ? ' 办理' : '' }}<span v-if="!pending(item)">{{ time(item.updateTime || item.createTime) }}</span></div>
      <div v-if="item.message" class="timeline-opinion"><span>审批意见</span>{{ item.message }}</div>
    </a-timeline-item>
    <a-timeline-item v-if="pendingNode && !records.some(pending)" color="blue"><div class="timeline-heading"><strong>{{ pendingNode }}</strong><a-tag color="blue">待处理</a-tag></div><div class="timeline-meta">等待 {{ pendingAssignees || '处理人待确定' }} 办理</div></a-timeline-item>
  </a-timeline>
  <a-empty v-else description="暂无审批记录" />
</template>
<style scoped>
.timeline-heading{display:flex;align-items:center;gap:10px;margin-bottom:5px}.timeline-meta{color:#86909c}.timeline-meta span{margin-left:12px}.timeline-opinion{margin-top:8px;padding:9px 12px;background:#f7f8fa;border-radius:6px;white-space:pre-wrap;word-break:break-word}.timeline-opinion span{display:block;color:#86909c;margin-bottom:3px}
</style>
