<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const companyName = ref('')
const logoUrl = ref('')
const welcomeMsg = ref('')

onMounted(async () => {
  // 欢迎提示（登录成功跳转过来的）
  welcomeMsg.value = '欢迎，admin'

  try {
    const res: any = await api.get('/public/company')
    if (res.code === 200 && res.data) {
      companyName.value = res.data.company_name || ''
      logoUrl.value = res.data.logo_url || ''
    }
  } catch {
    companyName.value = '织云ERP'
  }
})

function handleLogout() {
  localStorage.removeItem('token')
  router.push({ name: 'Login' })
}
</script>

<template>
  <div class="home-page">
    <!-- 顶栏：公司名 + logo（跟随 is_default 公司，前端规范 A.1 展示层） -->
    <header class="topbar">
      <div class="topbar-left">
        <div class="company-logo">
          <img v-if="logoUrl" :src="logoUrl" :alt="companyName" />
          <div v-else class="logo-ph">织</div>
        </div>
        <span class="company-name">{{ companyName || '织云 ERP' }}</span>
      </div>
      <div class="topbar-right">
        <!-- 登录成功提示：右上角"欢迎，admin" -->
        <span class="welcome">{{ welcomeMsg }}</span>
        <button class="btn-logout" @click="handleLogout">退出</button>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="main-content">
      <div class="welcome-card">
        <h1>👋 {{ welcomeMsg }}</h1>
        <p>欢迎使用织云 ERP 服装代工厂生产管理系统</p>
        <p class="tip">第一刀骨架已就绪，后续业务模块将在此基础上开发。</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}
.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e5e8ef;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.company-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #1E5EFF;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.company-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.logo-ph {
  color: #fff;
  font-weight: 700;
  font-size: 16px;
}
.company-name {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.welcome {
  font-size: 14px;
  color: #4e5969;
}
.btn-logout {
  height: 32px;
  padding: 0 16px;
  background: #fff;
  border: 1px solid #e5e8ef;
  border-radius: 6px;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-logout:hover {
  border-color: #1E5EFF;
  color: #1E5EFF;
}
.main-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.welcome-card {
  background: #fff;
  border-radius: 12px;
  padding: 48px 64px;
  box-shadow: 0 2px 12px rgba(30, 94, 255, 0.06);
  text-align: center;
}
.welcome-card h1 {
  font-size: 28px;
  color: #1d2129;
  margin-bottom: 16px;
}
.welcome-card p {
  font-size: 15px;
  color: #4e5969;
  margin-bottom: 8px;
}
.welcome-card .tip {
  font-size: 13px;
  color: #86909c;
  margin-top: 16px;
}
</style>
