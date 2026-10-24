<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '../api'

const router = useRouter()
const route = useRoute()
const companyName = ref('')
const logoUrl = ref('')
const userName = ref('admin')

// 左侧竖排模块导航（preview/人员信息演示.html 布局）
// 后续业务模块加到这即可，权限过滤后期在角色里配
const modules = [
  { key: 'order', icon: '📦', label: '订单' },
  { key: 'prod', icon: '🏭', label: '生产' },
  { key: 'stock', icon: '📊', label: '库存' },
  { key: 'person', icon: '👥', label: '人员' },
  { key: 'dorm', icon: '🏠', label: '宿舍' },
  { key: 'canteen', icon: '🍜', label: '食堂' },
  { key: 'vehicle', icon: '🚗', label: '车辆' },
  { key: 'property', icon: '🏢', label: '物业资产' },
  { key: 'finance', icon: '💰', label: '财务' },
  { key: 'approve', icon: '✅', label: '审批' },
  { key: 'alert', icon: '🔔', label: '预警' },
  { key: 'workbench', icon: '📋', label: '工作台' },
  { key: 'system', icon: '⚙️', label: '系统' },
]
const activeModule = ref('system')

// 系统模块功能卡片（公司档案放这）
const systemCards = [
  { key: 'company', title: '公司档案', desc: '公司名/logo/登录背景', icon: '🏛️', route: 'Company' },
]

onMounted(async () => {
  // 根据当前路由高亮
  if (route.name === 'Company') activeModule.value = 'system'
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

function navModule(key: string) {
  activeModule.value = key
  // 系统模块：内容区显示功能卡片；其他模块后续开发，先回首页欢迎卡
  if (key !== 'system' && route.name !== 'Home') {
    router.push({ name: 'Home' })
  }
}

function openCard(c: any) {
  if (c.route) router.push({ name: c.route })
}

function handleLogout() {
  localStorage.removeItem('token')
  router.push({ name: 'Login' })
}
</script>

<template>
  <div class="layout">
    <!-- 左侧竖排模块导航（72px，preview 布局） -->
    <aside class="sidebar">
      <div class="logo">织</div>
      <div
        v-for="m in modules"
        :key="m.key"
        class="nav-item"
        :class="{ active: activeModule === m.key }"
        @click="navModule(m.key)"
      >
        <span class="ico">{{ m.icon }}</span>
        <span class="lbl">{{ m.label }}</span>
      </div>
    </aside>

    <!-- 右侧主区 -->
    <div class="main">
      <!-- 顶栏（56px）：公司名logo + 全局搜索 + 通知 + 用户 -->
      <header class="topbar">
        <div class="company">
          <div class="clogo">
            <img v-if="logoUrl" :src="logoUrl" :alt="companyName" />
            <div v-else class="clogo-ph">织</div>
          </div>
          <span class="cname">{{ companyName || '织云 ERP' }}</span>
        </div>
        <!-- 全局搜索框（前端规范：常驻顶栏，Ctrl+K 唤起） -->
        <div class="top-search">
          <input type="text" placeholder="搜索功能 / 人员 / 单据... 如：员工档案、张丽、010203、裁剪单" />
          <span class="shortcut">Ctrl K</span>
        </div>
        <div class="tools">
          <span class="bell">🔔<i class="badge">4</i></span>
          <div class="user">
            <div class="avatar">王</div>
            <span class="uname">{{ userName }}</span>
          </div>
          <button class="btn-logout" @click="handleLogout">退出</button>
        </div>
      </header>

      <!-- 内容区 -->
      <div class="content">
        <!-- 系统模块：功能卡片（公司档案在这） -->
        <template v-if="activeModule === 'system'">
          <div class="module-title">模块 / 系统</div>
          <div class="group">
            <div class="group-title">基础配置</div>
            <div class="card-grid">
              <div v-for="c in systemCards" :key="c.key" class="card" @click="openCard(c)">
                <div class="card-ico">{{ c.icon }}</div>
                <div class="card-title">{{ c.title }}</div>
                <div class="card-desc">{{ c.desc }}</div>
              </div>
            </div>
          </div>
        </template>

        <!-- 其他模块：暂未开发提示（首页） -->
        <template v-else>
          <router-view />
          <div v-if="route.name === 'Home'" class="placeholder">
            <div class="ph-icon">🚧</div>
            <p>「{{ modules.find(m => m.key === activeModule)?.label }}」模块开发中</p>
            <p class="tip">当前已开发：系统管理 → 公司档案</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 工业蓝主题（preview/人员信息演示.html 取色：主色 #1E5EFF） */
.layout { display: flex; height: 100vh; overflow: hidden; }

/* 左侧竖排模块导航 72px */
.sidebar {
  width: 72px;
  background: #fff;
  border-right: 1px solid #E5E8EF;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 12px;
  flex-shrink: 0;
  overflow-y: auto;
}
.logo {
  width: 44px; height: 44px; border-radius: 10px;
  background: #1E5EFF; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; margin-bottom: 14px; flex-shrink: 0;
}
.nav-item {
  width: 60px; height: 58px; border-radius: 8px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: pointer; margin-bottom: 5px; color: #4E5969; gap: 2px; flex-shrink: 0;
}
.nav-item:hover { background: #E8F0FF; }
.nav-item.active { background: #1E5EFF; color: #fff; }
.nav-item .ico { font-size: 19px; line-height: 1; }
.nav-item .lbl { font-size: 11px; }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

/* 顶栏 56px */
.topbar {
  height: 56px; background: #fff; border-bottom: 1px solid #E5E8EF;
  display: flex; align-items: center; padding: 0 20px; gap: 16px; flex-shrink: 0;
}
.company { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.clogo {
  width: 26px; height: 26px; border-radius: 6px; background: #1E5EFF;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.clogo img { width: 100%; height: 100%; object-fit: contain; }
.clogo-ph { color: #fff; font-weight: 700; font-size: 13px; }
.cname { font-size: 16px; font-weight: 600; color: #1D2129; }

/* 全局搜索框（顶栏常驻，preview 布局） */
.top-search { position: relative; flex: 1; max-width: 520px; margin: 0 18px; }
.top-search input {
  width: 100%; border: 1px solid #E5E8EF; border-radius: 8px;
  padding: 8px 60px 8px 36px; font-size: 13px; outline: none; background: #fff; box-sizing: border-box;
}
.top-search input:focus { border-color: #1E5EFF; }
.shortcut {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  background: #F0F2F5; color: #86909C; font-size: 11px; padding: 2px 8px; border-radius: 4px;
}

.tools { margin-left: auto; display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.bell { position: relative; cursor: pointer; font-size: 18px; }
.bell .badge {
  position: absolute; top: -6px; right: -8px; background: #F53F3F; color: #fff;
  font-size: 10px; min-width: 15px; height: 15px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; font-style: normal;
}
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.avatar {
  width: 30px; height: 30px; border-radius: 50%; background: #1E5EFF; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600;
}
.uname { font-size: 14px; color: #1D2129; }
.btn-logout {
  height: 30px; padding: 0 12px; background: #fff; border: 1px solid #E5E8EF; border-radius: 6px;
  color: #4E5969; font-size: 13px; cursor: pointer;
}
.btn-logout:hover { border-color: #1E5EFF; color: #1E5EFF; }

/* 内容区 */
.content { flex: 1; overflow: auto; background: #F5F7FA; padding: 16px 20px; }
.module-title { font-size: 14px; color: #4E5969; margin-bottom: 16px; }
.group { margin-bottom: 24px; }
.group-title {
  font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 12px;
  padding-left: 10px; border-left: 3px solid #1E5EFF;
}
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; }
.card {
  background: #fff; border-radius: 10px; padding: 20px 16px; cursor: pointer;
  box-shadow: 0 2px 8px rgba(29,33,41,.06); transition: all 0.2s; border: 1px solid transparent;
}
.card:hover { border-color: #1E5EFF; box-shadow: 0 4px 16px rgba(30,94,255,.12); transform: translateY(-2px); }
.card-ico {
  width: 44px; height: 44px; border-radius: 10px; background: #E8F0FF;
  display: flex; align-items: center; justify-content: center; font-size: 22px; margin-bottom: 12px;
}
.card-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 4px; }
.card-desc { font-size: 12px; color: #86909C; }

.placeholder {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 100px 0; color: #86909C;
}
.ph-icon { font-size: 48px; margin-bottom: 16px; }
.placeholder p { font-size: 15px; margin-bottom: 6px; }
.placeholder .tip { font-size: 13px; color: #C9CDD4; }
</style>
