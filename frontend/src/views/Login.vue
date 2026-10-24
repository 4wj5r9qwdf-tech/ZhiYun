<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()

// 公司信息（从后端 /public/company 读，不写死代码）
const companyName = ref('')
const logoUrl = ref('')
const theme = ref('')
const slogan = ref('')
const loginBgUrl = ref('')
const loginBgMode = ref('A')  // A=铺满左侧品牌区 / B=铺满整页

// 登录表单
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

onMounted(async () => {
  try {
    const res: any = await api.get('/public/company')
    if (res.code === 200 && res.data) {
      companyName.value = res.data.company_name || ''
      logoUrl.value = res.data.logo_url || ''
      theme.value = res.data.theme || ''
      slogan.value = res.data.slogan || ''
      loginBgUrl.value = res.data.login_bg_url || ''
      loginBgMode.value = res.data.login_bg_mode || 'A'
    }
  } catch {
    companyName.value = '织云ERP'
  }
})

async function handleLogin() {
  if (!username.value || !password.value) {
    errorMsg.value = '请输入工号/手机号和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    // RuoYi-Vue-Plus 6.x 登录：clientId 取 sys_client 表 pc 客户端
    const res: any = await api.post('/auth/login', {
      clientId: 'e5cd7e4891bf95d1d19206ce24a7b32e',
      grantType: 'password',
      username: username.value.trim(),
      password: password.value,
    })
    if (res.code === 200 && res.data?.access_token) {
      localStorage.setItem('token', res.data.access_token)
      successMsg.value = '登录成功，正在跳转...'
      // 登录成功提示：右上角弹"欢迎，"+ 绿色提示条（延迟 1s 跳转，让用户看到提示）
      setTimeout(() => {
        router.push({ name: 'Home' })
      }, 1000)
    } else {
      errorMsg.value = res.msg || '登录失败'
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.msg || '登录失败，请检查网络'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page" :class="'mode-' + loginBgMode">
    <!-- 左侧：公司品牌区 -->
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
        <h1 class="brand-name">{{ companyName || '织云 ERP' }}</h1>
        <p class="brand-slogan">{{ slogan || '服装代工厂生产管理系统' }}</p>
      </div>
    </div>

    <!-- 右侧：登录表单区 -->
    <div class="form-side">
      <div class="form-card">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-subtitle">请输入工号/手机号和密码</p>

        <!-- 成功提示 -->
        <div v-if="successMsg" class="msg msg-success">
          <span class="msg-icon">✓</span>{{ successMsg }}
        </div>
        <!-- 错误提示 -->
        <div v-if="errorMsg" class="msg msg-error">
          <span class="msg-icon">✕</span>{{ errorMsg }}
        </div>

        <form @submit.prevent="handleLogin">
          <div class="input-group">
            <label>工号 / 手机号</label>
            <input
              v-model="username"
              type="text"
              placeholder="6位工号 或 11位手机号"
              autocomplete="username"
            />
          </div>
          <div class="input-group">
            <label>密码</label>
            <input
              v-model="password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </div>
          <button type="submit" class="btn-login" :disabled="loading">
            {{ loading ? '登录中...' : '登 录' }}
          </button>
        </form>

        <div class="footer-tip">织云 ERP · 服装代工厂生产管理系统</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 工业蓝主题（前端规范 B 节：主色 #1E5EFF） */
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
  text-align: center;
  padding: 40px;
}
.brand-logo {
  width: 88px;
  height: 88px;
  margin: 0 auto 24px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.15);
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
.input-group {
  margin-bottom: 20px;
}
.input-group label {
  display: block;
  font-size: 14px;
  color: #4e5969;
  margin-bottom: 8px;
  font-weight: 500;
}
.input-group input {
  width: 100%;
  height: 44px;
  border: 1px solid #e5e8ef;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 14px;
  color: #1d2129;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.input-group input:focus {
  border-color: #1E5EFF;
  box-shadow: 0 0 0 3px rgba(30, 94, 255, 0.1);
}
.input-group input::placeholder {
  color: #c9cdd4;
}
.btn-login {
  width: 100%;
  height: 46px;
  background: #1E5EFF;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  letter-spacing: 4px;
  transition: background 0.2s;
}
.btn-login:hover:not(:disabled) {
  background: #0f3db8;
}
.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.msg {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.msg-success {
  background: #e8faf3;
  color: #00b388;
  border: 1px solid #b2e8d4;
}
.msg-error {
  background: #ffece8;
  color: #f53f3f;
  border: 1px solid #fcc4bd;
}
.msg-icon {
  font-weight: 700;
}
.footer-tip {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #c9cdd4;
}
</style>
