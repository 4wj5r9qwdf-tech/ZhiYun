<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api, { CLIENT_ID } from '../api'
import { useFloatingDrag } from '../composables/useFloatingDrag'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { RobotOutlined, SendOutlined, PlusOutlined, CheckOutlined, CloseOutlined, PaperClipOutlined } from '@ant-design/icons-vue'

type UploadedImage = { uid: string; id?: string; name: string; previewUrl: string; kind?: "document"; status: 'uploading' | 'done' | 'error'; error?: string }

type Agent = {
  id: number
  name: string
  description?: string
  greeting?: string
  presetQuestions?: string[]
}

type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
  attachments?: Array<{ name: string; previewUrl: string; id?: string; error?: string; kind?: "document" }>
  approval?: {
    approvalId: string
    toolName: string
    description: string
    arguments: string
    status: 'pending' | 'approved' | 'rejected' | 'expired'
    loading?: boolean
  }
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'update:open', value: boolean): void }>()
const visible = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value)
})

const agents = ref<Agent[]>([])
const selectedAgentId = ref<number>()
const conversationId = ref('')
const messages = ref<ChatMessage[]>([])
const input = ref('')
const loadingAgents = ref(false)
const creatingConversation = ref(false)
const sending = ref(false)
const errorText = ref('')
const messageList = ref<HTMLElement>()
const floatingPanel = ref<HTMLElement>()
const panelDrag = useFloatingDrag(floatingPanel)
const fileInput = ref<HTMLInputElement>()
const attachments = ref<UploadedImage[]>([])
const uploadingImages = ref(false)
const screenshotQuestion = ref(false)
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyKeyword = ref('')
const historyAppliedKeyword = ref('')
const historyPage = ref(1)
const historyTotal = ref(0)
const historyRows = ref<Array<{ conversationId: string; title?: string; firstQuestion?: string; updateDt?: string; createDt?: string }>>([])
const previewImage = ref<{ name: string; url: string }>()
const previewUrls = new Set<string>()

/** 将模型回复按 Markdown 展示，并过滤不安全的 HTML。 / Render sanitized model Markdown. */
function renderReply(content: string) {
  return DOMPurify.sanitize(marked.parse(content, { async: false, breaks: true }) as string, {
    FORBID_TAGS: ['img', 'iframe', 'style', 'form'],
    FORBID_ATTR: ['style']
  })
}

function createPreviewUrl(blob: Blob) {
  const url = URL.createObjectURL(blob)
  previewUrls.add(url)
  return url
}

function handleEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape' || event.defaultPrevented || !visible.value || previewImage.value) return
  visible.value = false
  event.preventDefault()
  event.stopPropagation()
}
onMounted(() => window.addEventListener('keydown', handleEscape, true))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEscape, true)
  previewUrls.forEach(url => URL.revokeObjectURL(url)); previewUrls.clear()
})

watch(() => props.open, (open) => {
  if (open && agents.value.length === 0) void loadAgents()
})

async function loadAgents() {
  loadingAgents.value = true
  errorText.value = ''
  try {
    const result: any = await api.get('/snail-ai/agents')
    if (result.code !== 200) throw new Error(result.msg || '读取智能体失败')
    agents.value = result.data || []
    if (agents.value.length === 0) {
      errorText.value = '当前没有可用智能体，请先在 SnailAI 管理端配置织云智能体。'
      return
    }
    selectedAgentId.value = agents.value[0].id
    await restoreLatestConversation()
  } catch (error: any) {
    errorText.value = error?.response?.data?.msg || error?.message || '读取智能体失败'
  } finally {
    loadingAgents.value = false
  }
}

async function restoreLatestConversation() {
  if (selectedAgentId.value == null) return
  try {
    const result: any = await api.get('/snail-ai/conversations', { params: { agentId: selectedAgentId.value } })
    if (result.code !== 200) throw new Error(result.msg || '读取历史对话失败')
    const latest = (result.data || [])[0]
    if (latest?.conversationId) {
      conversationId.value = latest.conversationId
      const history: any = await api.get(`/snail-ai/conversations/${encodeURIComponent(latest.conversationId)}/messages`, {
        params: { agentId: selectedAgentId.value }
      })
      if (history.code !== 200) throw new Error(history.msg || '读取历史消息失败')
      messages.value = (history.data || []).map((item: any) => ({
        role: item.role === 'user' ? 'user' : 'assistant',
        content: item.content || '',
        attachments: (item.attachments || []).map((image: any) => ({ name: image.name || '图片', id: String(image.id), previewUrl: '' }))
      }))
      void restoreHistoryImages(latest.conversationId, selectedAgentId.value)
      await scrollToBottom()
      return
    }
  } catch (error: any) {
    errorText.value = error?.response?.data?.msg || error?.message || '读取历史对话失败'
  }
  await startNewConversation()
}

/** 查询当前员工历史会话；加载更多使用服务端分页。 / Load the current employee's paginated conversations. */
async function loadHistory(reset = true) {
  if (selectedAgentId.value == null || historyLoading.value) return
  const agentId = selectedAgentId.value
  const page = reset ? 1 : historyPage.value + 1
  const keyword = reset ? historyKeyword.value.trim() : historyAppliedKeyword.value
  historyLoading.value = true
  try {
    const result: any = await api.get('/snail-ai/conversations/history', { params: {
      agentId, page, size: 20, keyword
    } })
    if (result.code !== 200) throw new Error(result.msg || '读取历史对话失败')
    if (selectedAgentId.value !== agentId) return
    historyRows.value = reset ? (result.data?.rows || []) : [...historyRows.value, ...(result.data?.rows || [])]
    historyPage.value = page
    historyAppliedKeyword.value = keyword
    historyTotal.value = Number(result.data?.total || 0)
  } catch (error: any) { errorText.value = error?.response?.data?.msg || error?.message || '读取历史对话失败' }
  finally { historyLoading.value = false }
}

function showHistory() {
  historyOpen.value = !historyOpen.value
  if (historyOpen.value) void loadHistory()
}

/** 切换时先读取成功再替换当前会话，避免覆盖未发送的输入。 / Switch only after a successful fetch. */
async function openConversation(id: string) {
  if (sending.value || uploadingImages.value || creatingConversation.value || selectedAgentId.value == null) return
  if (id === conversationId.value) { historyOpen.value = false; return }
  if (input.value.trim() || attachments.value.length) { errorText.value = '请先发送或清空当前输入和附件，再切换对话。'; return }
  const agentId = selectedAgentId.value
  creatingConversation.value = true
  errorText.value = ''
  try {
    const result: any = await api.get(`/snail-ai/conversations/${encodeURIComponent(id)}/messages`, { params: { agentId } })
    if (result.code !== 200) throw new Error(result.msg || '读取历史消息失败')
    if (selectedAgentId.value !== agentId) return
    conversationId.value = id
    messages.value = (result.data || []).map((item: any) => ({
      role: item.role === 'user' ? 'user' : 'assistant', content: item.content || '',
      attachments: (item.attachments || []).map((image: any) => ({ name: image.name || '图片', id: String(image.id), previewUrl: '' }))
    }))
    historyOpen.value = false
    void restoreHistoryImages(id, agentId)
    await scrollToBottom()
  } catch (error: any) { errorText.value = error?.response?.data?.msg || error?.message || '读取历史消息失败' }
  finally { creatingConversation.value = false }
}

/** 历史图片通过当前员工身份逐张加载，链接不暴露框架资源地址。 / Fetch history images using the employee session. */
async function restoreHistoryImages(id: string, agentId: number) {
  const history = messages.value
  for (const message of history) {
    for (const image of message.attachments || []) {
      if (!image.id || conversationId.value !== id || selectedAgentId.value !== agentId) return
      try {
        const blob: any = await api.get(`/snail-ai/conversations/${encodeURIComponent(id)}/images/${encodeURIComponent(image.id)}`, {
          params: { agentId }, responseType: 'blob'
        })
        if (!(blob instanceof Blob) || !blob.type.startsWith('image/')) throw new Error('图片格式无效')
        if (conversationId.value !== id || selectedAgentId.value !== agentId) return
        image.previewUrl = createPreviewUrl(blob)
      } catch {
        image.error = '历史图片加载失败'
      }
    }
  }
}

async function startNewConversation() {
  if (selectedAgentId.value == null) return
  if (input.value.trim() || attachments.value.length) { errorText.value = '请先发送或清空当前输入和附件，再新建对话。'; return }
  creatingConversation.value = true
  errorText.value = ''
  try {
    const result: any = await api.post('/snail-ai/conversations', { agentId: selectedAgentId.value, title: 'ERP 智能问答' })
    if (result.code !== 200 || !result.data?.conversationId) throw new Error(result.msg || '新建对话失败')
    conversationId.value = result.data.conversationId
    historyOpen.value = false
    messages.value = []
    const agent = agents.value.find(item => item.id === selectedAgentId.value)
    if (agent?.greeting) messages.value.push({ role: 'assistant', content: agent.greeting })
  } catch (error: any) {
    errorText.value = error?.response?.data?.msg || error?.message || '新建对话失败'
  } finally {
    creatingConversation.value = false
    await scrollToBottom()
  }
}

async function changeAgent(value: unknown) {
  const optionValue = value && typeof value === 'object' && 'value' in value
    ? (value as { value?: unknown }).value
    : value
  if ((typeof optionValue !== 'string' && typeof optionValue !== 'number') || !Number.isFinite(Number(optionValue))) return
  const agentId = Number(optionValue)
  selectedAgentId.value = agentId
  conversationId.value = ''
  messages.value = []
  await startNewConversation()
}

async function sendMessage(text = input.value) {
  const content = text.trim()
  if ((!content && attachments.value.length === 0) || sending.value || uploadingImages.value || attachments.value.some(item => item.status !== 'done') || !conversationId.value || selectedAgentId.value == null) return
  if (screenshotQuestion.value && attachments.value.length >= 4) {
    errorText.value = '截图需要占用一个图片位置，请先移除一张图片。'
    return
  }
  sending.value = true
  errorText.value = ''
  let screenshot: { id: string; name: string; previewUrl: string } | undefined
  if (screenshotQuestion.value) {
    try {
      screenshot = await captureCurrentPage()
    } catch (error: any) {
      errorText.value = error?.response?.data?.msg || error?.message || '页面截图失败，请重试。'
      sending.value = false
      return
    }
  }
  input.value = ''
  const sentAttachments = attachments.value.map(item => ({ name: item.name, previewUrl: item.previewUrl, kind: item.kind }))
  const attachmentIds = attachments.value.flatMap(item => item.id ? [item.id] : [])
  if (screenshot) {
    sentAttachments.push({ name: screenshot.name, previewUrl: screenshot.previewUrl, kind: undefined })
    attachmentIds.push(screenshot.id)
  }
  messages.value.push({ role: 'user', content, attachments: sentAttachments })
  attachments.value = []
  const answer = reactive<ChatMessage>({ role: 'assistant', content: '' })
  messages.value.push(answer)
  await scrollToBottom()

  const token = sessionStorage.getItem('token')
  try {
    const response = await fetch('/api/snail-ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : '',
        clientid: CLIENT_ID
      },
      body: JSON.stringify({
        agentId: selectedAgentId.value,
        conversationId: conversationId.value,
        content,
        attachmentIds
      })
    })
    if (response.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('userId')
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      window.location.href = '/login'
      return
    }
    if (!response.ok || !response.body) {
      const body: any = await response.json().catch(() => null)
      throw new Error(body?.msg || 'AI 请求失败')
    }
    await readEventStream(response.body, answer)
    if (!answer.content && !messages.value.some(item => item.approval?.status === 'pending')) {
      answer.content = '本次没有收到可显示的回复，请稍后重试。'
    }
  } catch (error: any) {
    if (!answer.content) answer.content = error?.message || 'AI 请求失败，请稍后重试。'
    errorText.value = ''
  } finally {
    sending.value = false
    // 确认仅代表放行，完成后引导查看模型回复，不把结束误认为业务成功。
    // Approval only permits execution; completion itself does not prove business success.
    for (const item of messages.value) {
      if (item.approval?.status === 'approved' && item.content === '已确认，正在执行。') {
        item.content = '本轮处理已结束，请查看下方回复和业务结果。'
      }
    }
    await scrollToBottom()
  }
}

/** 截取当前页面可见区域，助手自身不进入截图。 / Capture the viewport without the assistant UI. */
async function captureCurrentPage() {
  const { default: html2canvas } = await import('html2canvas')
  const canvas = await html2canvas(document.documentElement, {
    backgroundColor: '#ffffff',
    useCORS: true,
    scale: Math.min(window.devicePixelRatio || 1, 1.5),
    x: window.scrollX,
    y: window.scrollY,
    width: window.innerWidth,
    height: window.innerHeight,
    windowWidth: window.innerWidth,
    windowHeight: window.innerHeight,
    scrollX: window.scrollX,
    scrollY: window.scrollY,
    ignoreElements: element => element.hasAttribute('data-ai-capture-exclude')
  })
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(
    result => result ? resolve(result) : reject(new Error('截图生成失败，请重试。')), 'image/jpeg', 0.85
  ))
  if (blob.size > 5 * 1024 * 1024) throw new Error('截图超过 5 MB，请缩小页面后重试。')
  const name = '当前页面截图.jpg'
  const form = new FormData()
  form.append('file', new File([blob], name, { type: 'image/jpeg' }))
  const result: any = await api.post('/snail-ai/attachments', form)
  if (result.code !== 200 || !result.data?.id) throw new Error(result.msg || '截图上传失败')
  return { id: result.data.id as string, name, previewUrl: createPreviewUrl(blob) }
}

async function handleImageSelection(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  await uploadImages(files)
}

/** 支持从系统剪贴板粘贴截图，沿用图片上传和员工归属校验。 / Upload clipboard images through the existing path. */
function handlePaste(event: ClipboardEvent) {
  const imageItems = Array.from(event.clipboardData?.items || []).filter(item => item.kind === 'file' && item.type.startsWith('image/'))
  if (!imageItems.length) return
  event.preventDefault()
  if (sending.value || uploadingImages.value || !conversationId.value) {
    errorText.value = '请等待当前操作完成后再粘贴图片。'
    return
  }
  const files = imageItems.flatMap(item => {
    const file = item.getAsFile()
    if (!file) return []
    const extension = item.type === 'image/png' ? 'png' : item.type === 'image/jpeg' ? 'jpg' : ''
    return [extension ? new File([file], `粘贴的截图-${Date.now()}.${extension}`, { type: item.type }) : file]
  })
  void uploadImages(files)
}

async function uploadImages(files: File[]) {
  const remaining = Math.max(0, 4 - attachments.value.length)
  if (files.length > remaining) errorText.value = '每条消息最多添加 4 个附件。'
  const selected = files.slice(0, remaining)
  if (!selected.length) return
  uploadingImages.value = true
  for (const file of selected) {
    const isImage = /\.(png|jpe?g)$/i.test(file.name)
    const sizeLimitMb = isImage ? 5 : /\.pdf$/i.test(file.name) ? 50 : 10
    const image: UploadedImage = {
      uid: crypto.randomUUID(), name: file.name,
      previewUrl: isImage ? createPreviewUrl(file) : "", kind: isImage ? undefined : "document", status: 'uploading'
    }
    attachments.value.push(image)
    const pending = attachments.value[attachments.value.length - 1]!
    if (file.size > sizeLimitMb * 1024 * 1024 || (!isImage && !/\.(pdf|docx|xlsx|pptx|md|txt|csv)$/i.test(file.name))) {
      pending.status = 'error'
      pending.error = file.size > sizeLimitMb * 1024 * 1024 ? `超过 ${sizeLimitMb} MB` : '不支持该文件格式'
      continue
    }
    const form = new FormData()
    form.append('file', file)
    form.append('agentId', String(selectedAgentId.value))
    form.append('conversationId', conversationId.value)
    try {
      const result: any = await api.post('/snail-ai/attachments', form)
      if (result.code !== 200 || !result.data?.id) throw new Error(result.msg || '上传失败')
      pending.id = result.data.id
      pending.status = 'done'
    } catch (error: any) {
      pending.status = 'error'
      pending.error = error?.response?.data?.msg || error?.message || '上传失败'
    }
  }
  uploadingImages.value = false
}

function removeAttachment(uid: string) {
  const image = attachments.value.find(item => item.uid === uid)
  if (image) { URL.revokeObjectURL(image.previewUrl); previewUrls.delete(image.previewUrl) }
  attachments.value = attachments.value.filter(item => item.uid !== uid)
}

async function readEventStream(stream: ReadableStream<Uint8Array>, answer: ChatMessage) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        handleEventBlock(block, answer)
        boundary = buffer.indexOf('\n\n')
      }
      await scrollToBottom()
    }
    buffer += decoder.decode()
    if (buffer.trim()) handleEventBlock(buffer, answer)
  } finally {
    reader.releaseLock()
  }
}

function handleEventBlock(block: string, answer: ChatMessage) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (dataLines.length === 0) return
  const raw = dataLines.join('\n')
  let payload: any
  try { payload = JSON.parse(raw) } catch { payload = { data: raw } }

  if (eventName === 'text' || payload.type === 'text') {
    answer.content += payload.data || ''
    return
  }
  if (eventName === 'approval-required' || payload.type === 'approval_required') {
    messages.value.push({
      role: 'assistant',
      content: '需要你确认以下 ERP 操作。确认前不会执行。',
      approval: {
        approvalId: payload.approvalId,
        toolName: payload.toolName,
        description: payload.description || '',
        arguments: payload.arguments || '',
        status: 'pending'
      }
    })
    // 后续流式回复应显示在确认卡之后，避免结果出现在用户当前视线之外。
    // Keep the streamed response after the approval card in chronological order.
    const answerIndex = messages.value.indexOf(answer)
    if (answerIndex >= 0) {
      messages.value.splice(answerIndex, 1)
      messages.value.push(answer)
    }
    return
  }
  if (eventName === 'error' || payload.type === 'error') {
    answer.content += payload.message || payload.data || 'AI 服务暂时不可用。'
  }
}

async function resolveApproval(item: ChatMessage, approved: boolean) {
  const approval = item.approval
  if (!approval || approval.status !== 'pending' || approval.loading) return
  approval.loading = true
  try {
    const result: any = await api.post('/snail-ai/approvals/resolve', {
      approvalId: approval.approvalId,
      approved
    })
    if (result.code !== 200) throw new Error(result.msg || '确认失败')
    approval.status = approved ? 'approved' : 'rejected'
    item.content = approved ? '已确认，正在执行。' : '已拒绝，操作未执行。'
  } catch (error: any) {
    approval.status = 'expired'
    item.content = error?.response?.data?.msg || error?.message || '确认已失效，操作未执行。'
  } finally {
    approval.loading = false
    await scrollToBottom()
  }
}

function formatArguments(raw: string) {
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}
</script>

<template>
  <Teleport to="body">
    <aside v-show="visible" ref="floatingPanel" :style="panelDrag.style.value" data-ai-capture-exclude class="ai-floating-panel" aria-label="织云智能助手" @paste="handlePaste">
      <div class="modal-title" title="按住标题栏拖动面板" @pointerdown="panelDrag.start"><span>织云智能助手</span><div class="panel-controls" data-no-drag>
      <a-button size="small" :disabled="!selectedAgentId || sending || creatingConversation" @click="showHistory">历史对话</a-button>
      <a-button size="small" :disabled="!selectedAgentId || creatingConversation || sending" @click="startNewConversation">
        <plus-outlined /> 新对话
      </a-button>
      <button type="button" class="panel-close" aria-label="关闭智能助手" @click="visible = false">×</button>
      </div></div>

    <div class="assistant-shell">
      <section v-if="historyOpen" class="history-panel" aria-label="我的历史对话">
        <div class="history-heading"><strong>我的历史对话</strong><a-button type="text" size="small" @click="historyOpen = false">返回对话</a-button></div>
        <div class="history-search"><a-input v-model:value="historyKeyword" allow-clear placeholder="搜索对话内容" @press-enter="loadHistory()" /><a-button :loading="historyLoading" @click="loadHistory()">搜索</a-button></div>
        <div class="history-list">
          <div v-if="historyLoading && !historyRows.length" class="history-empty">正在加载…</div>
          <div v-else-if="!historyRows.length" class="history-empty">没有找到历史对话</div>
          <button v-for="item in historyRows" :key="item.conversationId" type="button" class="history-item" :class="{ selected: item.conversationId === conversationId }" :disabled="creatingConversation || sending" @click="openConversation(item.conversationId)">
            <strong>{{ item.firstQuestion || item.title || '未命名对话' }}</strong><span>{{ (item.updateDt || item.createDt || '').replace('T', ' ').slice(0, 19) }}</span>
          </button>
          <a-button v-if="historyRows.length < historyTotal" block :loading="historyLoading" @click="loadHistory(false)">加载更多</a-button>
        </div>
        <a-alert v-if="errorText" type="error" show-icon :message="errorText" />
      </section>
      <template v-else>
      <div class="assistant-toolbar">
        <div class="assistant-identity"><span class="identity-mark">✦</span><div><strong>织云智核</strong><small>你的 ERP 业务助手</small></div></div>
        <a-select
          :value="selectedAgentId"
          :loading="loadingAgents"
          :disabled="agents.length < 2 || sending"
          class="agent-select"
          placeholder="选择智能体"
          @change="changeAgent"
        >
          <a-select-option v-for="agent in agents" :key="agent.id" :value="agent.id">
            {{ agent.name }}
          </a-select-option>
        </a-select>
      </div>

      <div ref="messageList" class="assistant-messages">
        <div v-if="loadingAgents || creatingConversation" class="assistant-state">正在连接智能体…</div>
        <div v-else-if="messages.length === 0 && !errorText" class="assistant-state">
          <robot-outlined class="assistant-empty-icon" />
          <div>可以问我织云 ERP 中的业务问题</div>
          <div class="assistant-subtitle">我会按当前员工权限查询；任何写入操作都会先等你确认。</div>
        </div>
        <div v-for="(item, index) in messages" :key="index" class="chat-row" :class="item.role">
          <div class="chat-avatar">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="chat-card">
            <div v-if="item.content && item.role === 'assistant' && !item.approval" class="chat-content markdown-content" v-html="renderReply(item.content)" />
            <div v-else-if="item.content" class="chat-content">{{ item.content }}</div>
            <div v-if="item.attachments?.length" class="sent-images">
              <template v-for="(image, imageIndex) in item.attachments" :key="image.id || image.previewUrl || imageIndex">
                <button v-if="image.previewUrl" type="button" class="image-preview-button" :aria-label="`放大查看 ${image.name}`" @click="previewImage = {name:image.name,url:image.previewUrl}"><img :src="image.previewUrl" :alt="image.name" /></button>
                <span v-else-if="image.kind === 'document'" class="history-image-state"><paper-clip-outlined /> {{ image.name }}</span>
                <span v-else class="history-image-state">{{ image.error || '图片加载中…' }}</span>
              </template>
            </div>
            <div v-if="item.role === 'assistant' && sending && index === messages.length - 1 && !item.content" class="typing-indicator">
              正在思考…
            </div>
            <div v-if="item.approval" class="approval-card">
              <div class="approval-title">{{ item.approval.toolName }}</div>
              <div v-if="item.approval.description" class="approval-description">{{ item.approval.description }}</div>
              <pre class="approval-arguments">{{ formatArguments(item.approval.arguments) }}</pre>
              <div v-if="item.approval.status === 'pending'" class="approval-actions">
                <a-button type="primary" size="small" :loading="item.approval.loading" @click="resolveApproval(item, true)">
                  <check-outlined /> 确认执行
                </a-button>
                <a-button size="small" :disabled="item.approval.loading" @click="resolveApproval(item, false)">
                  <close-outlined /> 拒绝
                </a-button>
              </div>
              <div v-else class="approval-status">{{ item.content }}</div>
            </div>
          </div>
        </div>
        <a-alert v-if="errorText" type="error" show-icon :message="errorText" />
      </div>

      <div class="assistant-compose">
        <div v-if="agents.find(agent => agent.id === selectedAgentId)?.presetQuestions?.length" class="preset-questions">
          <a-tag
            v-for="question in agents.find(agent => agent.id === selectedAgentId)?.presetQuestions?.slice(0, 3) || []"
            :key="question"
            :color="sending ? undefined : 'blue'"
            @click="sendMessage(question)"
          >{{ question }}</a-tag>
        </div>
        <div v-if="attachments.length" class="attachment-list">
          <div v-for="image in attachments" :key="image.uid" class="attachment-item">
            <img v-if="image.previewUrl" :src="image.previewUrl" :alt="image.name" />
            <paper-clip-outlined v-else />
            <div class="attachment-info"><span :title="image.name">{{ image.name }}</span><small>{{ image.status === 'uploading' ? '正在上传…' : image.status === 'done' ? '已就绪' : image.error }}</small></div>
            <button type="button" class="attachment-remove" aria-label="移除附件" @click="removeAttachment(image.uid)">×</button>
          </div>
        </div>
        <div class="compose-box">
        <a-textarea
          v-model:value="input"
          :disabled="sending || uploadingImages || !conversationId"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          placeholder="输入 ERP 业务问题…（Enter 发送，Shift+Enter 换行）"
          @keydown.enter.exact.prevent="sendMessage()"
        />
        <div class="compose-footer">
          <div class="compose-actions">
            <input ref="fileInput" class="file-input" type="file" accept=".png,.jpg,.jpeg,.pdf,.docx,.xlsx,.pptx,.md,.txt,.csv" multiple @change="handleImageSelection" />
            <a-button size="small" :disabled="sending || uploadingImages || attachments.length >= 4 || !conversationId" @click="fileInput?.click()"><paper-clip-outlined /> 添加附件</a-button>
            <a-checkbox v-model:checked="screenshotQuestion" :disabled="sending || uploadingImages">截图提问</a-checkbox>
            <span>最多 4 个附件，截图占 1 个</span>
          </div>
          <a-button type="primary" :disabled="(!input.trim() && !attachments.length) || !conversationId || uploadingImages || attachments.some(item => item.status !== 'done')" :loading="sending" @click="sendMessage()">
            <send-outlined /> 发送
          </a-button>
        </div>
        </div>
        <div class="compose-hint">涉及业务修改时，请确认操作内容</div>
      </div>
      </template>
    </div>
    </aside>
    <a-modal :open="!!previewImage" :title="previewImage?.name || '图片预览'" :footer="null" :width="1000" :z-index="20020" centered @cancel="previewImage = undefined">
      <img v-if="previewImage" class="full-image-preview" :src="previewImage.url" :alt="previewImage.name" />
    </a-modal>
  </Teleport>
</template>

<style scoped>
.ai-floating-panel { position: fixed; z-index: 20002; right: 22px; top: 60px; width: min(500px, calc(100vw - 32px)); height: min(690px, calc(100vh - 76px)); min-width: min(320px, calc(100vw - 32px)); min-height: min(320px, calc(100vh - 76px)); max-width: calc(100vw - 16px); max-height: calc(100vh - 16px); resize: both; overflow: hidden; display: flex; flex-direction: column; padding: 18px; border: 1px solid #e3eaf7; border-radius: 20px; background: #fff; box-shadow: 0 20px 70px #233d7340; }
.modal-title { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 12px; font-weight: 600; color: #172b4d; cursor: grab; touch-action: none; user-select: none; }
.modal-title:active { cursor: grabbing; }
.panel-controls { display: flex; align-items: center; gap: 8px; }
.panel-close { border: 0; background: transparent; color: #8996aa; cursor: pointer; font-size: 23px; line-height: 1; padding: 2px 4px; }
.panel-close:hover { color: #25385b; }
.assistant-shell { display: flex; flex: 1; flex-direction: column; min-height: 0; }
.history-panel { display: flex; flex: 1; min-height: 0; flex-direction: column; gap: 12px; }
.history-heading, .history-search { display: flex; align-items: center; gap: 8px; }
.history-heading { justify-content: space-between; }
.history-search :deep(.ant-input-affix-wrapper) { flex: 1; }
.history-list { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.history-empty { padding: 28px; color: #8996aa; text-align: center; }
.history-item { display: flex; flex-direction: column; gap: 4px; width: 100%; padding: 11px 12px; border: 1px solid #edf1f7; border-radius: 9px; background: #fff; text-align: left; cursor: pointer; }
.history-item:hover, .history-item.selected { background: #f2f6ff; border-color: #d6e3ff; }
.history-item strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #263247; }
.history-item span { color: #8996aa; font-size: 12px; }
.assistant-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 0 18px; border-bottom: 1px solid #edf1f7; }
.assistant-identity { display: flex; align-items: center; gap: 12px; }
.identity-mark { display: grid; place-items: center; width: 42px; height: 42px; color: white; font-size: 21px; border-radius: 14px; background: linear-gradient(145deg, #246bff, #604de7); box-shadow: 0 6px 16px #315ff933; }
.assistant-identity strong, .assistant-identity small { display: block; }
.assistant-identity strong { font-size: 15px; color: #172b4d; }
.assistant-identity small { margin-top: 3px; color: #8996aa; font-size: 12px; }
.agent-select { width: 175px; }
.assistant-messages { flex: 1; min-height: 0; overflow-y: auto; padding: 24px 4px; display: flex; flex-direction: column; gap: 22px; }
.assistant-state { margin: auto; text-align: center; color: #667085; line-height: 1.8; padding: 20px; }
.assistant-empty-icon { font-size: 32px; color: #1e5eff; margin-bottom: 8px; }
.assistant-subtitle { font-size: 12px; color: #98a2b3; }
.chat-row { display: flex; gap: 8px; align-items: flex-start; }
.chat-row.user { flex-direction: row-reverse; }
.chat-avatar { width: 34px; height: 34px; flex: 0 0 34px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(145deg, #246bff, #604de7); color: white; font-size: 11px; font-weight: 600; }
.chat-row.user .chat-avatar { background: #eaf7f0; color: #18864b; }
.chat-card { max-width: calc(100% - 46px); background: #f5f7fb; border: 1px solid #eef1f7; border-radius: 4px 16px 16px 16px; padding: 13px 16px; }
.chat-row.user .chat-card { background: #eaf1ff; border-color: #e1eaff; border-radius: 16px 4px 16px 16px; }
.chat-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; color: #263247; }
.sent-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.sent-images img { max-width: 220px; max-height: 180px; object-fit: contain; border-radius: 10px; }
.image-preview-button { display: block; padding: 0; border: 0; background: transparent; cursor: zoom-in; }
.image-preview-button:focus-visible { outline: 2px solid #1e5eff; outline-offset: 3px; }
.history-image-state { padding: 8px; color: #73809a; font-size: 12px; }
.full-image-preview { display: block; max-width: 100%; max-height: 75vh; margin: auto; object-fit: contain; }
.markdown-content :deep(p) { margin: 0 0 8px; }
.markdown-content { white-space: normal; }
.markdown-content :deep(p:last-child) { margin-bottom: 0; }
.markdown-content :deep(ul), .markdown-content :deep(ol) { padding-left: 22px; margin: 4px 0 8px; }
.markdown-content :deep(pre) { overflow: auto; padding: 8px; border-radius: 6px; background: #e9edf5; white-space: pre-wrap; }
.markdown-content :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
.markdown-content :deep(td), .markdown-content :deep(th) { border: 1px solid #d9e1ef; padding: 4px 8px; }
.typing-indicator { color: #98a2b3; font-size: 12px; }
.approval-card { margin-top: 8px; width: min(390px, 100%); border: 1px solid #f5cf8a; border-radius: 8px; padding: 10px; background: #fffaf0; }
.approval-title { font-weight: 600; color: #663c00; }
.approval-description { margin-top: 4px; color: #6b7280; font-size: 12px; }
.approval-arguments { margin: 8px 0; max-height: 220px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 11px; color: #344054; background: #fff; border-radius: 4px; padding: 8px; }
.approval-actions { display: flex; gap: 8px; }
.approval-status { margin-top: 6px; color: #667085; font-size: 12px; }
.assistant-compose { padding-top: 12px; }
.compose-box { border: 1px solid #dce4f1; border-radius: 16px; padding: 8px 12px 10px; background: #fff; box-shadow: 0 6px 24px #193e790d; }
.compose-box:focus-within { border-color: #789dfb; box-shadow: 0 0 0 3px #245fff12; }
.compose-box :deep(textarea.ant-input) { border: 0; box-shadow: none; padding: 8px 2px; resize: none; }
.attachment-list { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.attachment-item { position: relative; display: flex; align-items: center; gap: 8px; width: 210px; min-width: 0; padding: 6px; border: 1px solid #e7ebf2; border-radius: 10px; background: #fff; }
.attachment-item img { width: 42px; height: 42px; flex: 0 0 42px; object-fit: cover; border-radius: 7px; background: #f3f5f9; }
.attachment-info { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.attachment-info span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: #344054; }
.attachment-info small { color: #8491a5; font-size: 11px; }
.attachment-item:has(small:not(:empty)) small { color: #8491a5; }
.attachment-remove { position: absolute; top: -6px; right: -6px; width: 20px; height: 20px; border: 0; border-radius: 50%; background: #667085; color: white; cursor: pointer; line-height: 20px; }
.file-input { display: none; }
.compose-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: #98a2b3; font-size: 11px; }
.compose-hint { text-align: center; color: #a1acbb; font-size: 11px; padding-top: 9px; }
.preset-questions { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.preset-questions :deep(.ant-tag) { cursor: pointer; max-width: 100%; white-space: normal; }
.compose-footer { margin-top: 8px; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #98a2b3; font-size: 11px; }
</style>
