<script setup lang="ts">
/**
 * 智能体（方案 A：ERP 内置，无外部框架）
 * - 工具=后端按【当前登录人】权限过滤的动作目录（sys_agent_action），无权限的动作根本看不到
 * - 工具调用=后端带本人 token 回环调 ERP 现成接口（数据权限/脱敏原样生效），全程留痕记工号
 * - 会话历史在前端 messages 数组（后端无状态），"新对话"清空
 */
import { onMounted, ref, nextTick } from 'vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import api from '../api'

interface ToolCall { tool: string; args?: Record<string, any>; status: string; ms?: number }
interface Msg { role: 'user' | 'assistant'; content: string; toolCalls?: ToolCall[] }

// 示例问题（展示用静态文案；点按即作为提问发出）
const examples = [
  '现在厂里有几栋建筑？都是什么用途？',
  '组织架构是什么样的？',
  '张三是谁，在哪个组？',
]

const messages = ref<Msg[]>([])
const input = ref('')
const sending = ref(false)
const actions = ref<any[]>([])

const listRef = ref<HTMLElement | null>(null)
async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

function actionName(key: string): string {
  return actions.value.find(a => a.action_key === key)?.action_name || key
}

async function loadActions() {
  try {
    const res: any = await api.get('/system/agent/actions')
    if (res.code === 200) actions.value = res.data || []
  } catch { actions.value = [] }
}

async function send(text?: string) {
  const q = (text ?? input.value).trim()
  if (!q || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: q })
  sending.value = true
  scrollBottom()
  try {
    // 带最近 10 轮上下文
    const history = messages.value.slice(-11).map((m) => ({ role: m.role, content: m.content }))
    const res: any = await api.post('/system/agent/chat', { messages: history })
    if (res.code === 200) {
      messages.value.push({ role: 'assistant', content: res.data?.answer || '（无返回）', toolCalls: res.data?.toolCalls || [] })
    } else {
      messages.value.push({ role: 'assistant', content: '出错：' + (res.msg || '未知错误') })
    }
  } catch (e: any) {
    messages.value.push({ role: 'assistant', content: '出错：' + (e?.response?.data?.msg || e?.message || '请求失败') })
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function askExample(text: string) {
  send(text)
}

function newChat() {
  messages.value = []
  input.value = ''
}

onMounted(() => {
  loadActions()
  scrollBottom()
})
</script>

<template>
  <div class="agent-page">
    <div class="agent-card">
      <div class="agent-head">
        <div class="agent-title">
          <RobotOutlined class="agent-icon" />
          <span>厂内助理</span>
          <span class="agent-sub">按你的权限查数据 · 全程留痕</span>
        </div>
        <a-button size="small" @click="newChat">新对话</a-button>
      </div>

      <div ref="listRef" class="agent-body">
        <div v-if="!messages.length" class="agent-empty">
          <p class="empty-tip">问厂里的事，比如：</p>
          <div class="example-chips">
            <a-tag
              v-for="(ex, i) in examples"
              :key="i"
              class="example-chip"
              @click="askExample(ex)"
            >{{ ex }}</a-tag>
          </div>
          <p v-if="actions.length" class="empty-actions">当前你可查：{{ actions.map((a: any) => a.action_name).join(' / ') }}</p>
          <p v-else class="empty-actions">当前没有可用的数据查询动作（联系超管在"分配权限"里给你的角色勾"智能体"）</p>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div v-if="m.role === 'assistant'" class="bubble">
            <div v-if="m.toolCalls?.length" class="tool-lines">
              <a-tag v-for="(t, j) in m.toolCalls" :key="j" size="small" :color="t.status === 'ok' ? 'green' : 'red'">
                调{{ actionName(t.tool) }}{{ t.status === 'ok' ? '✓' : '✗' }}
              </a-tag>
            </div>
            <div class="msg-content">{{ m.content }}</div>
          </div>
          <div v-else class="bubble user-bubble">
            <div class="msg-content">{{ m.content }}</div>
          </div>
        </div>

        <div v-if="sending" class="msg-row assistant"><div class="bubble"><span class="typing">正在查询…</span></div></div>
      </div>

      <div class="agent-foot">
        <a-textarea
          v-model:value="input"
          class="agent-input"
          :auto-size="{ minRows: 1, maxRows: 4 }"
          placeholder="问一句，比如：一号车间几层？（Enter 发送，Shift+Enter 换行）"
          @keydown="onKeyDown"
        />
        <a-button type="primary" class="send-btn" :loading="sending" :disabled="!input.trim()" @click="send()">发送</a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-page { height: 100%; display: flex; justify-content: center; padding: 16px; box-sizing: border-box; }
.agent-card {
  width: 100%; max-width: 780px; background: #fff; border-radius: 10px;
  box-shadow: 0 2px 8px rgba(29, 33, 41, .06); display: flex; flex-direction: column;
  height: calc(100vh - 210px); min-height: 420px;
}
.agent-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #F0F2F5; }
.agent-title { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; color: #1D2129; }
.agent-icon { color: #1E5EFF; font-size: 18px; }
.agent-sub { font-weight: 400; font-size: 12px; color: #86909C; margin-left: 4px; }
.agent-body { flex: 1; overflow-y: auto; padding: 16px; background: #FAFBFD; }
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row.user { justify-content: flex-end; }
.bubble {
  max-width: 82%; background: #fff; border: 1px solid #F0F2F5; border-radius: 8px;
  padding: 10px 12px; font-size: 13px; line-height: 1.7; color: #1D2129; word-break: break-word;
}
.user-bubble { background: #E8F0FF; border-color: #D4E3FF; }
.msg-content { white-space: pre-wrap; }
.tool-lines { margin-bottom: 6px; }
.typing { color: #86909C; font-size: 12px; }
.agent-foot { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #F0F2F5; background: #fff; align-items: flex-end; }
.agent-input { flex: 1; border-radius: 8px; font-size: 13px; }
.send-btn { height: 36px; }
.agent-empty { text-align: center; padding: 40px 16px; color: #4E5969; }
.empty-tip { font-size: 13px; margin-bottom: 12px; }
.example-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-bottom: 16px; }
.example-chip { cursor: pointer; border: 1px solid #D4E3FF; background: #F5F8FF; color: #1E5EFF; border-radius: 14px; }
.empty-actions { font-size: 12px; color: #86909C; }
</style>
