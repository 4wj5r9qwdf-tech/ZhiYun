<!--
文件用途：提供织云 ERP SnailAiTraces的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP SnailAiTraces的查询、编辑、状态展示和用户操作界面。
设计思路：使用 Vue 3 响应式状态与项目公共布局；页面编排交互，服务端负责业务规则和最终授权。
实现方法：加载选项/列表，按当前状态驱动表单和弹窗，调用业务 API 后刷新并反馈结果。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
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
/** 智能体对话与工具调用诊断。 / Diagnose agent conversations and tool calls. */
import { onMounted, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import api from '../api'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'

const rows = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref<string>()
const detail = ref<any>()
const detailOpen = ref(false)
const imageStates = ref<Record<string, { loading: boolean; error: string; images: Array<{name: string; mimeType: string; content: string}> }>>({})
const previewImage = ref<{name: string; src: string}>()
const columns = [
  { key: 'last_at', title: '最近对话时间', width: 180 },
  { key: 'nickname', title: '员工', width: 120 },
  { key: 'question_preview', title: '首条提问', width: 320 },
  { key: 'turn_count', title: '轮数', width: 90 },
  { key: 'status', title: '最近状态', width: 100 },
  { key: 'failed_count', title: '失败轮数', width: 100 },
  { key: 'conversation_id', title: '对话编号', width: 220 }
]
const statuses: Record<string, string> = { RUNNING: '进行中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', NOT_APPROVED: '未确认' }

async function load() {
  loading.value = true
  try {
    const result: any = await api.get('/snail-ai/admin/traces/conversations', { params: { pageNum: page.value, pageSize: size.value, keyword: keyword.value.trim() || undefined, status: status.value } })
    if (result.code !== 200) throw new Error(result.msg || '读取对话记录失败')
    rows.value = (result.data?.rows || []).map((row: any) => ({ ...row, id: String(row.trace_id) }))
    total.value = Number(result.data?.total || 0)
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取对话记录失败') }
  finally { loading.value = false }
}

async function showDetail(row: any) {
  try {
    const result: any = await api.get(`/snail-ai/admin/traces/conversations/${encodeURIComponent(row.id)}`)
    if (result.code !== 200) throw new Error(result.msg || '读取详情失败')
    detail.value = result.data
    detailOpen.value = true
    imageStates.value = {}
  } catch (error: any) { message.error(error?.response?.data?.msg || error.message || '读取详情失败') }
}

async function loadTraceImages(traceId: string) {
  imageStates.value[traceId] = { loading: true, error: '', images: [] }
  const currentDetail = detail.value
  try {
    const result: any = await api.get(`/snail-ai/admin/traces/${encodeURIComponent(traceId)}/images`, { timeout: 60000 })
    if (result.code !== 200) throw new Error(result.msg || '图片加载失败')
    if (detail.value !== currentDetail) return
    imageStates.value[traceId] = { loading: false, error: result.data?.length ? '' : '无法唯一确认本轮图片关联，或原图已不可用。', images: result.data || [] }
  } catch (error: any) { if (detail.value === currentDetail) imageStates.value[traceId] = { loading: false, error: error?.response?.data?.msg || error.message || '图片加载失败', images: [] } }
}

function hasAttachments(value: unknown) {
  try { return Array.isArray(JSON.parse(String(value || '[]'))) && JSON.parse(String(value || '[]')).length > 0 } catch { return false }
}

function formatted(value: unknown) {
  if (!value) return '无'
  try { return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2) }
  catch { return String(value) }
}

onMounted(load)
</script>

<template>
  <StandardListLayout>
    <template #filters>
      <label class="zy-filter-field"><span>关键词</span><a-input v-model:value="keyword" allow-clear placeholder="员工、内容或对话编号" @press-enter="page=1;load()" /></label>
      <label class="zy-filter-field"><span>包含状态</span><a-select v-model:value="status" allow-clear placeholder="全部" style="width:140px" :options="[{value:'RUNNING',label:'进行中'},{value:'SUCCEEDED',label:'已完成'},{value:'FAILED',label:'失败'},{value:'CANCELLED',label:'已取消'}]" /></label>
    </template>
    <FoundationTable page-toolbar storage-key="snailai_traces" :columns="columns" :rows="rows" :loading="loading" :server-total="total" :server-page="page" :server-page-size="size" :action-width="90" @page-change="(p,s)=>{page=p;size=s;load()}">
      <template #actions><a-button type="primary" @click="page=1;load()">查询</a-button><a-button @click="keyword='';status=undefined;page=1;load()">重置</a-button></template>
      <template #cell="{column,row}"><span>{{ column.key === 'status' ? (statuses[row.status] || row.status) : (row[column.key] ?? '—') }}</span></template>
      <template #action="{row}"><a-button type="link" size="small" @click="showDetail(row)">查看</a-button></template>
    </FoundationTable>
  </StandardListLayout>
  <a-modal v-model:open="detailOpen" title="智能体对话详情" :width="1200" :footer="null" centered>
    <template v-if="detail">
      <a-descriptions bordered size="small" :column="2">
        <a-descriptions-item label="员工">{{ detail.nickname }}</a-descriptions-item>
        <a-descriptions-item label="对话编号">{{ detail.conversation_id }}</a-descriptions-item>
      </a-descriptions>
      <div class="turn-list">
        <section v-for="(turn, index) in detail.turns" :key="turn.trace_id" class="turn">
          <div class="turn-heading"><strong>第 {{ Number(index) + 1 }} 轮</strong><span>{{ turn.started_at }} · {{ statuses[turn.status] || turn.status }} · {{ turn.duration_ms }} 毫秒</span></div>
          <h3>员工提问</h3><pre>{{ turn.question || '无文字' }}</pre>
          <h3>智能体回复</h3><pre>{{ turn.answer || '暂无回复' }}</pre>
          <template v-if="turn.error_message"><h3>错误信息</h3><pre>{{ turn.error_message }}</pre></template>
          <template v-if="hasAttachments(turn.attachment_summary)">
            <h3>附件</h3><pre>{{ formatted(turn.attachment_summary) }}</pre>
            <a-button size="small" :loading="imageStates[turn.trace_id]?.loading" @click="loadTraceImages(String(turn.trace_id))">查看图片</a-button>
            <p v-if="imageStates[turn.trace_id]?.error" class="image-error">{{ imageStates[turn.trace_id]?.error }}</p>
            <div v-if="imageStates[turn.trace_id]?.images.length" class="trace-images">
              <button v-for="(image, imageIndex) in imageStates[turn.trace_id]?.images" :key="imageIndex" type="button" class="trace-image" :title="image.name" @click="previewImage = {name:image.name,src:`data:${image.mimeType};base64,${image.content}`}"><img :src="`data:${image.mimeType};base64,${image.content}`" :alt="image.name" /><span>{{ image.name }}</span></button>
            </div>
          </template>
          <a-collapse><a-collapse-panel key="tools" :header="`本轮工具调用（${turn.tools?.length || 0}）及可见工具`">
            <pre>{{ formatted(turn.visible_tools) }}</pre>
            <a-empty v-if="!turn.tools?.length" description="没有记录到工具调用" />
            <div v-for="tool in turn.tools" :key="tool.event_id"><strong>{{ tool.tool_name }} · {{ statuses[tool.status] || tool.status }}</strong><p>耗时：{{ tool.duration_ms }} 毫秒</p><pre v-if="tool.error_message">{{ tool.error_message }}</pre></div>
          </a-collapse-panel></a-collapse>
        </section>
      </div>
    </template>
  </a-modal>
  <a-modal :open="!!previewImage" :title="previewImage?.name || '图片预览'" :footer="null" :width="1000" centered @cancel="previewImage=undefined"><img v-if="previewImage" class="large-image" :src="previewImage.src" :alt="previewImage.name" /></a-modal>
</template>

<style scoped>
h3 { margin: 22px 0 8px; font-weight: 600; }
.turn-list { max-height: 70vh; overflow-y: auto; margin-top: 16px; padding-right: 6px; }
.turn { margin-bottom: 16px; padding: 16px; border: 1px solid #e7ebf2; border-radius: 10px; }
.turn-heading { display: flex; justify-content: space-between; gap: 12px; color: #52627a; flex-wrap: wrap; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #f7f8fa; padding: 12px; border-radius: 6px; }
.trace-images { display: flex; flex-wrap: wrap; gap: 12px; }
.trace-image { display: flex; flex-direction: column; gap: 4px; width: 180px; padding: 6px; border: 1px solid #e5e8ef; border-radius: 8px; background: #fff; cursor: zoom-in; }
.trace-image img { width: 100%; height: 110px; object-fit: contain; }
.trace-image span { width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: left; }
.large-image { display: block; max-width: 100%; max-height: 75vh; margin: auto; object-fit: contain; }
.image-error { color: #ad6800; }
</style>
