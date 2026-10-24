<script setup lang="ts">
/**
 * 顶栏公共组件：公司名logo + 全局搜索 + 通知铃铛(未读角标+下拉) + 当前登录人(关联token)
 * 被 Workbench / Home 复用。搜索命中"功能/人员/单据"跳转。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const emit = defineEmits<{ (e: 'search', keyword: string): void }>()
const router = useRouter()

const companyName = ref('')
const logoUrl = ref('')
const userName = ref('')       // 真实登录人 nick_name
const userNo = ref('')         // 真实登录人工号 user_name
const firstChar = ref('织')

// 通知
const messages = ref<any[]>([])
const unread = ref(0)
const showMsg = ref(false)

// 搜索
const keyword = ref('')
const suggestions = ref<any[]>([])
const showSug = ref(false)

let msgTimer: any = null

onMounted(async () => {
  await Promise.all([loadCompany(), loadUser(), loadUnread()])
  msgTimer = setInterval(loadUnread, 60000) // 每分钟刷未读
})

onBeforeUnmount(() => { if (msgTimer) clearInterval(msgTimer) })

async function loadCompany() {
  try {
    const res: any = await api.get('/public/company')
    if (res.code === 200 && res.data) {
      companyName.value = res.data.company_name || ''
      logoUrl.value = res.data.logo_url || ''
    }
  } catch { companyName.value = '织云ERP' }
}

async function loadUser() {
  try {
    const res: any = await api.get('/system/profile/info')
    if (res.code === 200 && res.data) {
      userName.value = res.data.nick_name || res.data.user_name || ''
      userNo.value = res.data.user_name || ''
      firstChar.value = (userName.value || '织').slice(0, 1)
      // 缓存当前登录人 userId（DataTable 等按用户持久化配置用）
      if (res.data.user_id != null) localStorage.setItem('userId', String(res.data.user_id))
    }
  } catch { /* ignore */ }
}

async function loadUnread() {
  try {
    const res: any = await api.get('/system/profile/messages/unread')
    if (res.code === 200) unread.value = res.data || 0
  } catch { /* ignore */ }
}

async function toggleMsg() {
  showMsg.value = !showMsg.value
  if (showMsg.value) {
    try {
      const res: any = await api.get('/system/profile/messages')
      if (res.code === 200) messages.value = res.data || []
    } catch { messages.value = [] }
  }
}

// 全局搜索：输入≥1字节触发（后端支持中文），命中跳转
async function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) { suggestions.value = []; showSug.value = false; return }
  emit('search', kw)
  try {
    const res: any = await api.get('/system/profile/search', { params: { keyword: kw } })
    if (res.code === 200) suggestions.value = res.data || []
  } catch { suggestions.value = [] }
  showSug.value = suggestions.value.length > 0
}

function pickSug(s: any) {
  showSug.value = false
  keyword.value = ''
  if (s.type === 'person') {
    // 命中人员 → 跳人员模块（后续人员档案页），先提示
    alert(`命中人员：${s.nick_name}（工号 ${s.user_name}）\n人员档案页开发后将跳转到该档案`)
  }
}

function clearSug() { setTimeout(() => { showSug.value = false }, 200) }

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  router.push({ name: 'Login' })
}
</script>

<template>
  <header class="topbar">
    <div class="company">
      <div class="clogo">
        <img v-if="logoUrl" :src="logoUrl" :alt="companyName" />
        <div v-else class="clogo-ph">织</div>
      </div>
      <span class="cname">{{ companyName || '织云 ERP' }}</span>
    </div>

    <!-- 全局搜索（命中功能/人员/单据跳转） -->
    <div class="top-search">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索功能 / 人员 / 单据... 如：公司档案、张丽、13800138000"
        @input="onSearch"
        @focus="onSearch"
        @blur="clearSug"
      />
      <span class="shortcut">Ctrl K</span>
      <!-- 搜索建议 -->
      <div v-if="showSug" class="sug-list">
        <div v-for="s in suggestions" :key="s.user_id" class="sug-item" @mousedown.prevent="pickSug(s)">
          <span class="sug-ico">{{ s.type === 'person' ? '👤' : '📄' }}</span>
          <span class="sug-name">{{ s.nick_name || s.title }}</span>
          <span class="sug-sub">{{ s.type === 'person' ? '工号 ' + s.user_name : (s.phone || '') }}</span>
        </div>
      </div>
    </div>

    <div class="tools">
      <!-- 通知铃铛：未读角标 + 下拉列表 -->
      <div class="bell-wrap">
        <span class="bell" @click="toggleMsg">🔔<i v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</i></span>
        <div v-if="showMsg" class="msg-panel">
          <div class="msg-title">通知 <span v-if="unread > 0" class="msg-unread">{{ unread }} 条未读</span></div>
          <div v-if="messages.length === 0" class="msg-empty">暂无通知</div>
          <div v-for="m in messages" :key="m.message_id" class="msg-item" :class="{ unread: m.read_flag === '0' }">
            <div class="msg-item-title">{{ m.title || '（无标题）' }}</div>
            <div class="msg-item-body">{{ m.message || '' }}</div>
            <div class="msg-item-time">{{ (m.create_time || '').slice(0, 16) }}</div>
          </div>
        </div>
      </div>

      <!-- 当前登录人（关联 token 真实账户） -->
      <div class="user">
        <div class="avatar">{{ firstChar }}</div>
        <span class="uname">{{ userName || '...' }}</span>
      </div>
      <button class="btn-logout" @click="logout">退出</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: 56px; background: #fff; border-bottom: 1px solid #E5E8EF;
  display: flex; align-items: center; padding: 0 20px; gap: 16px; flex-shrink: 0;
  position: relative; z-index: 20;
}
.company { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.clogo { width: 26px; height: 26px; border-radius: 6px; background: #1E5EFF; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.clogo img { width: 100%; height: 100%; object-fit: contain; }
.clogo-ph { color: #fff; font-weight: 700; font-size: 13px; }
.cname { font-size: 16px; font-weight: 600; color: #1D2129; }

.top-search { position: relative; flex: 1; max-width: 520px; margin: 0 18px; }
.top-search input { width: 100%; border: 1px solid #E5E8EF; border-radius: 8px; padding: 8px 60px 8px 36px; font-size: 13px; outline: none; background: #fff; box-sizing: border-box; }
.top-search input:focus { border-color: #1E5EFF; }
.shortcut { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); background: #F0F2F5; color: #86909C; font-size: 11px; padding: 2px 8px; border-radius: 4px; }
.sug-list { position: absolute; top: 42px; left: 0; right: 0; background: #fff; border: 1px solid #E5E8EF; border-radius: 8px; box-shadow: 0 8px 24px rgba(29,33,41,.12); max-height: 280px; overflow: auto; z-index: 30; }
.sug-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; font-size: 13px; }
.sug-item:hover { background: #E8F0FF; }
.sug-ico { font-size: 15px; }
.sug-name { color: #1D2129; }
.sug-sub { color: #86909C; font-size: 12px; margin-left: auto; }

.tools { margin-left: auto; display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.bell-wrap { position: relative; }
.bell { position: relative; cursor: pointer; font-size: 18px; }
.bell .badge { position: absolute; top: -6px; right: -8px; background: #F53F3F; color: #fff; font-size: 10px; min-width: 15px; height: 15px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-style: normal; }
.msg-panel { position: absolute; right: -80px; top: 32px; width: 320px; background: #fff; border: 1px solid #E5E8EF; border-radius: 8px; box-shadow: 0 8px 24px rgba(29,33,41,.12); z-index: 30; max-height: 380px; overflow: auto; }
.msg-title { padding: 10px 12px; font-weight: 600; font-size: 14px; border-bottom: 1px solid #F0F2F5; color: #1D2129; }
.msg-unread { font-size: 12px; color: #F53F3F; font-weight: 400; margin-left: 6px; }
.msg-empty { padding: 24px; text-align: center; color: #86909C; font-size: 13px; }
.msg-item { padding: 10px 12px; border-bottom: 1px solid #F7F8FA; }
.msg-item.unread { background: #F5F8FF; }
.msg-item-title { font-size: 13px; font-weight: 600; color: #1D2129; margin-bottom: 2px; }
.msg-item-body { font-size: 12px; color: #4E5969; margin-bottom: 2px; }
.msg-item-time { font-size: 11px; color: #C9CDD4; }

.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.avatar { width: 30px; height: 30px; border-radius: 50%; background: #1E5EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; }
.uname { font-size: 14px; color: #1D2129; }
.btn-logout { height: 30px; padding: 0 12px; background: #fff; border: 1px solid #E5E8EF; border-radius: 6px; color: #4E5969; font-size: 13px; cursor: pointer; }
.btn-logout:hover { border-color: #1E5EFF; color: #1E5EFF; }
</style>
