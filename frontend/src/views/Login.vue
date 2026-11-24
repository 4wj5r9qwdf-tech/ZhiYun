<!--
文件用途：展示公司品牌信息，采集账号密码并提交登录。
文件版本：1.0.2
功能目的：提供织云 ERP 登录界面，支持点击登录及在页面任意位置按回车登录。
设计思路：A/B 品牌背景模式共用同一表单；页面级回车转交表单原生提交，使自动填入的账号密码也能提交并复用表单校验。
实现方法：表单校验成功后调用登录 API，保存会话信息、加载权限并跳转工作台；校验失败时保留字段与页面错误提示。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：Codex（智能体）
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-24
修改标记：ZY-ERP-20261124-登录回车提交；A/B 模式下页面任意位置回车均提交
最近测试时间：2026-11-24
测试人员：Codex（智能体）
测试状态：TypeScript 检查、Vite 生产构建、开发转换与键盘处理静态断言通过；共享浏览器不可用，实际按键未实测
测试范围与记录位置：vue-tsc -b、vite build、Vite Login.vue 开发转换、页面级回车/自动填充值同步/监听清理静态断言、git diff --check
-->
<script setup lang="ts">
import { ensurePerms, resetPerms } from '../utils/perms'
/**
 * 登录页：左侧品牌区（背景图 A/B 模式、logo、公司名、宣传语）不变
 * 右侧表单改用 ant-design-vue（a-form + a-input + a-button + a-alert）
 * handleLogin 业务逻辑（/auth/login、存 token、1s 后跳 Workbench）完全不变
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import api, { CLIENT_ID } from '../api'
import { useTabsStore } from '../store/tabs'

const router = useRouter()

// 公司信息（从后端 /public/company 读，不写死代码）
const companyName = ref('')
const logoUrl = ref('')
const theme = ref('')
const slogan = ref('')
const loginBgUrl = ref('')
const loginBgMode = ref('A')  // A=铺满左侧品牌区 / B=铺满整页
const nameColor = ref('#FFFFFF')  // 登录页公司名颜色（公司档案里可配，暗背景默认白）

// 登录表单
// 登录表单（绑定 a-form :model，校验状态随输入/blur 自动清除）
const loginForm = ref({ username: '', password: '' })
const formRef = ref()
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

// 表单校验规则（用户名/密码非空）
const rules: any = {
  username: [{ required: true, message: '请输入工号/手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(async () => {
  // A/B 只改变品牌背景布局，页面级快捷键挂在同一个 Login 视图上，确保两种模式一致。
  document.addEventListener('keydown', handleLoginKeydown, true)
  try {
    const res: any = await api.get('/public/company')
    if (res.code === 200 && res.data) {
      companyName.value = res.data.company_name || ''
      logoUrl.value = res.data.logo_url || ''
      theme.value = res.data.theme || ''
      slogan.value = res.data.slogan || ''
      loginBgUrl.value = res.data.login_bg_url || ''
      loginBgMode.value = res.data.login_bg_mode || 'A'
      nameColor.value = res.data.name_color || '#FFFFFF'
    }
  } catch {
    companyName.value = '织云ERP'
  }
})

onBeforeUnmount(() => {
  // 登录页离开时移除全局监听，避免键盘事件影响其他页面。
  document.removeEventListener('keydown', handleLoginKeydown, true)
})

function syncRememberedCredentials() {
  // 浏览器自动填充有时只更新原生 input；在提交前把其值同步回 Vue 表单模型。
  const formElement = formRef.value?.$el as HTMLFormElement | undefined
  const usernameInput = formElement?.querySelector<HTMLInputElement>('input[autocomplete="username"]')
  const passwordInput = formElement?.querySelector<HTMLInputElement>('input[autocomplete="current-password"]')
  if (usernameInput) loginForm.value.username = usernameInput.value
  if (passwordInput) loginForm.value.password = passwordInput.value
}

function handleLoginKeydown(event: KeyboardEvent) {
  // 仅处理无修饰键的回车，并跳过输入法选字确认，避免打断中文输入。
  if (event.key !== 'Enter' || event.isComposing || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
  const formElement = formRef.value?.$el as HTMLFormElement | undefined
  if (!formElement) return

  // 阻止浏览器再隐式提交一次；requestSubmit 会沿用 a-form 的校验与 finish 事件。
  event.preventDefault()
  syncRememberedCredentials()
  formElement.requestSubmit()
}

async function handleLogin() {
  // 连续按回车时忽略重复提交，避免登录请求并发。
  if (loading.value) return
  // 手动校验（不依赖 a-form validate，避免静默 reject 无提示）
  if (!loginForm.value.username.trim() || !loginForm.value.password) {
    errorMsg.value = '请输入工号/手机号和密码'
    // 触发一次校验让红字显示
    formRef.value?.validate().catch(() => {})
    return
  }
  errorMsg.value = ''
  loading.value = true
  successMsg.value = ''
  try {
    // RuoYi-Vue-Plus 6.x 登录：clientId 取 sys_client 表 pc 客户端
    const res: any = await api.post('/auth/login', {
      clientId: CLIENT_ID,
      grantType: 'password',
      username: loginForm.value.username.trim(),
      password: loginForm.value.password,
    })
    if (res.code === 200 && res.data?.access_token) {
      sessionStorage.setItem('token', res.data.access_token)
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      const profile: any = await api.get('/system/profile/info')
      if (profile.code !== 200 || profile.data?.user_id == null) throw new Error('无法获取登录用户')
      sessionStorage.setItem('userId', String(profile.data.user_id))
      localStorage.setItem('userId', String(profile.data.user_id))
      useTabsStore().reload()
    resetPerms()   // 换账号双保险：登录一律重拉权限
    await ensurePerms()
      successMsg.value = '登录成功，正在跳转...'
      // 登录成功提示：绿色提示条（延迟 1s 跳转，让用户看到提示）
      // 首页=工作台
      setTimeout(() => {
        router.push({ name: 'Workbench' })
      }, 1000)
    } else {
      errorMsg.value = res.msg || '登录失败'
    }
  } catch (e: any) {
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userId')
    localStorage.removeItem('userId')
    useTabsStore().clearSession()
    errorMsg.value = e?.response?.data?.msg || '登录失败，请检查网络'
  } finally {
    loading.value = false
  }
}

function handleLoginValidationFailed() {
  // Ant Design Vue 会在必填校验失败时阻止 finish 事件；保留原有的页面级错误提示。
  errorMsg.value = '请输入工号/手机号和密码'
}
</script>

<template>
  <div class="login-page" :class="'mode-' + loginBgMode">
    <!-- 左侧：公司品牌区（不变） -->
    <div class="brand-side">
      <!-- 模式A/B：有背景图则铺背景（A=只铺左侧品牌区，B=铺整页），无则用默认工业蓝渐变 -->
      <div
        v-if="loginBgUrl"
        class="brand-bg"
        :style="{ backgroundImage: 'url(' + loginBgUrl + ')' }"
      ></div>
      <div class="brand-content">
        <div class="brand-logo">
          <img v-if="logoUrl" :src="logoUrl" :alt="companyName" />
          <div v-else class="logo-placeholder">织</div>
        </div>
        <h1 class="brand-name" :style="{ color: nameColor }">{{ companyName || '织云 ERP' }}</h1>
        <p class="brand-slogan">{{ slogan || '服装代工厂生产管理系统' }}</p>
      </div>
    </div>

    <!-- 右侧：登录表单区（antd 控件） -->
    <div class="form-side">
      <div class="form-card">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-subtitle">请输入工号/手机号和密码</p>

        <!-- 成功提示 -->
        <a-alert
          v-if="successMsg"
          class="msg"
          type="success"
          show-icon
          :message="successMsg"
        />
        <!-- 错误提示 -->
        <a-alert
          v-if="errorMsg"
          class="msg"
          type="error"
          show-icon
          :message="errorMsg"
        />

        <!-- A/B 品牌模式共用表单；点击走 submit 按钮，页面任意位置回车由全局处理器转交此表单。 -->
        <a-form
          ref="formRef"
          :model="loginForm"
          layout="vertical"
          @finish="handleLogin"
          @finishFailed="handleLoginValidationFailed"
        >
          <a-form-item label="工号 / 手机号" name="username" :rules="rules.username">
            <a-input
              v-model:value="loginForm.username"
              placeholder="6位工号 或 11位手机号"
              autocomplete="username"
            />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="rules.password">
            <a-input-password
              v-model:value="loginForm.password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </a-form-item>
          <a-button
            type="primary"
            block
            size="large"
            html-type="submit"
            :loading="loading"
          >
            登 录
          </a-button>
        </a-form>

        <div class="footer-tip">织云 ERP · 服装代工厂生产管理系统</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 工业蓝主题（前端规范 B 节：主色 #1E5EFF）—— 布局与品牌区样式不变 */
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  position: relative;
}
.brand-side {
  flex: 1;
  background: linear-gradient(135deg, #1E5EFF 0%, #0f3db8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  position: relative;
}
/* 背景图层：铺满左侧品牌区（A/B 模式共用），叠加半透明遮罩保证文字可读 */
.brand-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
.brand-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
}
/* 模式B：背景图铺满整页，登录卡片悬浮在背景上 */
.mode-B .brand-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
}
.mode-B .brand-side {
  background: transparent;
  z-index: 1;
}
/* mode-B：品牌信息(logo/公司名/宣传语)浮在铺满的背景图之上，白字加投影保证可读 */
.mode-B .brand-content {
  position: relative;
  z-index: 2;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.6);
}
.mode-B .brand-logo {
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
}
.mode-B .form-side {
  position: relative;
  z-index: 1;
  background: transparent;
}
.mode-B .form-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
}
.brand-content {
  position: relative;
  z-index: 2;              /* 浮在 brand-bg 之上：A 模式 static 会被 absolute 背景盖住（logo/公司名隐形根因） */
  text-align: center;
  padding: 40px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.55);  /* 浅色背景图上文字可读（配色任意都有投影兜底） */
}
.brand-logo {
  width: 88px;
  height: 88px;
  margin: 0 auto 24px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.logo-placeholder {
  font-size: 44px;
  font-weight: 700;
  color: #fff;
}
.brand-name {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
}
.brand-slogan {
  font-size: 16px;
  opacity: 0.85;
  letter-spacing: 4px;
}
.form-side {
  width: 480px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
}
.form-card {
  width: 100%;
  max-width: 360px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(30, 94, 255, 0.08);
}
.form-title {
  font-size: 24px;
  font-weight: 700;
  color: #1d2129;
  margin-bottom: 6px;
}
.form-subtitle {
  font-size: 14px;
  color: #86909c;
  margin-bottom: 28px;
}
.msg {
  margin-bottom: 16px;
}
.form-card :deep(.ant-input-affix-wrapper) {
  border-radius: 8px;
}
.form-card :deep(.ant-btn) {
  border-radius: 8px;
  font-size: 16px;
  letter-spacing: 4px;
  margin-top: 4px;
}
.footer-tip {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #c9cdd4;
}
</style>
