<script setup lang="ts">
/**
 * 顶栏公共组件：公司名logo + 全局搜索(a-auto-complete) + 通知铃铛(a-badge+a-dropdown) + 当前登录人(用户菜单)
 * 被 Workbench / Home 复用。搜索命中"功能/人员/单据"跳转。
 * 业务逻辑与接口调用保持不变，仅 UI 控件换成 ant-design-vue。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import message from 'ant-design-vue/es/message'
import { BellOutlined, LogoutOutlined } from '@ant-design/icons-vue'
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

// 搜索（a-auto-complete：v-model:value + :options + @select）
const keyword = ref('')
const suggestions = ref<any[]>([])

// 用户菜单（当前登录人 + 退出）
const userMenuOpen = ref(false)

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
      // 缓存当前登录人 userId（DataTable 等按用户持久化配置用）——必须保留
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

// 打开通知面板：拉取消息列表（下拉 open 变化触发，逻辑与原 toggleMsg 一致）
async function onMsgOpenChange(open: boolean) {
  showMsg.value = open
  if (open) {
    try {
      const res: any = await api.get('/system/profile/messages')
      if (res.code === 200) messages.value = res.data || []
    } catch { messages.value = [] }
  }
}

// 全局搜索：输入≥1字节触发（后端支持中文），命中跳转
async function onSearch(v: string) {
  const kw = (v ?? '').trim()
  if (!kw) { suggestions.value = []; return }
  emit('search', kw)
  try {
    const res: any = await api.get('/system/profile/search', { params: { keyword: kw } })
    if (res.code === 200) suggestions.value = res.data || []
  } catch { suggestions.value = [] }
}

// 兼容 AutoComplete @select 的两种签名：只带 value（字符串），或带 value+option（对象）
function pickSug(vOrS: any, opt?: any) {
  const s: any = opt && typeof opt === 'object' ? opt : vOrS
  if (!s || typeof s !== 'object') { keyword.value = ''; return }
  keyword.value = ''
  if (s.type === 'person') {
    // 命中人员 → 跳人员模块（后续人员档案页），先提示（逻辑不变）
    message.info(`命中人员：${s.nick_name}（工号 ${s.user_name}）——人员档案页开发后将跳转到该档案`)
  }
}

function onUserMenuClick({ key }: { key: string | number }) {
  if (key === 'logout') {
    userMenuOpen.value = false
    logout()
  }
}

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

    <!-- 全局搜索：a-auto-complete（命中功能/人员/单据跳转） -->
    <div class="top-search">
      <a-auto-complete
        v-model:value="keyword"
        :options="suggestions"
        allow-clear
        placeholder="搜索功能 / 人员 / 单据... 如：公司档案、张丽、13800138000"
        @search="onSearch"
        @select="pickSug"
      >
        <!-- 自定义下拉项：图标 + 名称 + 副标题（人员显示工号，其余显示电话等） -->
        <template #option="s">
          <div class="sug-item">
            <span class="sug-ico">{{ s.type === 'person' ? '👤' : '📄' }}</span>
            <span class="sug-name">{{ s.nick_name || s.title }}</span>
            <span class="sug-sub">{{ s.type === 'person' ? '工号 ' + s.user_name : (s.phone || '') }}</span>
          </div>
        </template>
      </a-auto-complete>
    </div>

    <div class="tools">
      <!-- 通知铃铛：a-badge 未读角标 + a-dropdown 下拉面板 -->
      <a-dropdown :trigger="['click']" placement="bottomRight" @open-change="onMsgOpenChange">
        <a-badge :count="unread" :overflow-count="99" class="bell-badge">
          <a-button type="text" class="bell-btn">
            <bell-outlined />
          </a-button>
        </a-badge>
        <template #overlay>
          <div class="msg-panel">
            <div class="msg-title">通知 <span v-if="unread > 0" class="msg-unread">{{ unread }} 条未读</span></div>
            <div v-if="messages.length === 0" class="msg-empty">暂无通知</div>
            <div v-for="m in messages" :key="m.message_id" class="msg-item" :class="{ unread: m.read_flag === '0' }">
              <div class="msg-item-title">{{ m.title || '（无标题）' }}</div>
              <div class="msg-item-body">{{ m.message || '' }}</div>
              <div class="msg-item-time">{{ (m.create_time || '').slice(0, 16) }}</div>
            </div>
          </div>
        </template>
      </a-dropdown>

      <!-- 当前登录人：a-dropdown 用户菜单（头像+昵称，下拉含"退出"） -->
      <a-dropdown v-model:open="userMenuOpen" :trigger="['click']" placement="bottomRight">
        <span class="user">
          <a-avatar :size="30" class="avatar">{{ firstChar }}</a-avatar>
          <span class="uname">{{ userName || '...' }}</span>
        </span>
        <template #overlay>
          <a-menu @click="onUserMenuClick">
            <a-menu-item key="info" disabled>
              <span class="menu-info">{{ userName }}<span v-if="userNo" class="menu-sub">工号 {{ userNo }}</span></span>
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout">
              <logout-outlined /> 退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
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

.top-search { flex: 1; max-width: 520px; margin: 0 18px; }
.top-search :deep(.ant-select-selector) { border-radius: 8px !important; }
.shortcut { background: #F0F2F5; color: #86909C; font-size: 11px; padding: 2px 8px; border-radius: 4px; margin-left: 4px; }
/* 自定义下拉建议项（覆盖 antd 默认 option 样式） */
.sug-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; font-size: 13px; }
.sug-ico { font-size: 15px; }
.sug-name { color: #1D2129; }
.sug-sub { color: #86909C; font-size: 12px; margin-left: auto; }

.tools { margin-left: auto; display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.bell-badge { display: flex; align-items: center; }
.bell-btn { display: flex; align-items: center; font-size: 18px; color: #4E5969; }
.msg-panel { width: 320px; max-height: 380px; overflow: auto; background: #fff; border: 1px solid #E5E8EF; border-radius: 8px; box-shadow: 0 8px 24px rgba(29,33,41,.12); }
.msg-title { padding: 10px 12px; font-weight: 600; font-size: 14px; border-bottom: 1px solid #F0F2F5; color: #1D2129; }
.msg-unread { font-size: 12px; color: #F53F3F; font-weight: 400; margin-left: 6px; }
.msg-empty { padding: 24px; text-align: center; color: #86909C; font-size: 13px; }
.msg-item { padding: 10px 12px; border-bottom: 1px solid #F7F8FA; }
.msg-item.unread { background: #F5F8FF; }
.msg-item-title { font-size: 13px; font-weight: 600; color: #1D2129; margin-bottom: 2px; }
.msg-item-body { font-size: 12px; color: #4E5969; margin-bottom: 2px; }
.msg-item-time { font-size: 11px; color: #C9CDD4; }

.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.avatar { background: #1E5EFF; color: #fff; font-size: 13px; font-weight: 600; }
.uname { font-size: 14px; color: #1D2129; }
.menu-info { display: flex; flex-direction: column; }
.menu-sub { font-size: 12px; color: #86909C; }
</style>
