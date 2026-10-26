<script setup lang="ts">
/**
 * 系统模块布局壳（/system）：左侧竖排模块导航 + 顶栏(TopBar) + 内容区(<router-view>)
 * 内容区按子路由渲染：'' → SystemIndex(功能卡片)，'company' → Company(公司档案)
 * 左侧导航和顶栏常驻，子页面切换不丢导航。
 */
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import message from 'ant-design-vue/es/message'
import * as AntIcons from '@ant-design/icons-vue'
import TopBar from '../components/TopBar.vue'
import { visibleModules, type ModuleDef } from '../config/modules'
import { useTabsStore } from '../store/tabs'

const router = useRouter()
const route = useRoute()
const tabsStore = useTabsStore()

// 左侧模块导航（目录统一维护在 config/modules.ts）
const modules = visibleModules()

// 动态解析 antd 图标组件（icon 存的是组件名字符串）
const Icon = (name: string) => (AntIcons as any)[name]

const activeModule = computed(() => {
  if (route.path.startsWith('/person')) return 'person'
  if (route.path.startsWith('/property')) return 'property'
  return 'system'
})

function navModule(key: string) {
  if (key === 'workbench') router.push({ name: 'Workbench' })
  else if (key === 'system') router.push({ name: 'SystemIndex' })
  else if (key === 'person') router.push({ name: 'PersonIndex' })
  else if (key === 'property') router.push({ name: 'PropertyIndex' })
  else {
    const label = (modules.find((m: ModuleDef) => m.key === key) as ModuleDef | undefined)?.label ?? key
    message.info(`「${label}」模块开发中`)
  }
}

// ===== 多标签页 =====
function switchTab(name: string) {
  const t = tabsStore.tabs.find(x => x.name === name)
  if (t) router.push(t.path)
}

function closeTab(name: string) {
  const next = tabsStore.closeTab(name)
  // 若关的是当前页，跳到下一个 tab；没有 tab 了回当前模块首页
  if (route.name === name) {
    if (next) {
      const t = tabsStore.tabs.find(x => x.name === next)
      if (t) { router.push(t.path); return }
    }
    router.push({ name: moduleHomeRoute.value })
  }
}

// 当前所在模块的首页路由（关完所有 tab 回这里）
const moduleHomeRoute = computed(() => {
  if (route.path.startsWith('/person')) return 'PersonIndex'
  if (route.path.startsWith('/property')) return 'PropertyIndex'
  return 'SystemIndex'
})

// ===== tab 右键菜单（关闭当前/右侧/左侧/其他/全部）=====
const menu = ref<{ show: boolean; x: number; y: number; name: string }>({ show: false, x: 0, y: 0, name: '' })

function onTabContext(e: MouseEvent, name: string) {
  e.preventDefault()
  menu.value = { show: true, x: e.clientX, y: e.clientY, name }
}
function hideMenu() { menu.value = { ...menu.value, show: false } }
// 点任意处关闭菜单
if (typeof window !== 'undefined') window.addEventListener('click', hideMenu)

function menuAction(action: string) {
  const name = menu.value.name
  hideMenu()
  let nav: string | null = null
  if (action === 'current') { closeTab(name); return }
  else if (action === 'right') nav = tabsStore.closeRight(name)
  else if (action === 'left') nav = tabsStore.closeLeft(name)
  else if (action === 'others') nav = tabsStore.closeOthers(name)
  else if (action === 'all') { tabsStore.closeAll(); router.push({ name: moduleHomeRoute.value }); return }
  // left/right/others：若当前页被关，导航到保留的锚点
  if (nav && route.name === nav) return           // 已在目标页
  if (nav) { const t = tabsStore.tabs.find(x => x.name === nav); if (t) { router.push(t.path); return } }
  // 当前页可能被关掉但 nav 为空（如关闭其他后当前不在保留列表）→ 回锚点或模块首页
  if (!tabsStore.tabs.some(t => t.name === route.name)) {
    const anchor = tabsStore.tabs.find(x => x.name === name)
    router.push(anchor ? anchor.path : { name: moduleHomeRoute.value })
  }
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">织</div>
      <div
        v-for="m in modules"
        :key="m.key"
        class="nav-item"
        :class="{ active: activeModule === m.key }"
        @click="navModule(m.key)"
      >
        <component :is="Icon(m.icon)" class="ico" />
        <span class="lbl">{{ m.label }}</span>
      </div>
    </aside>

    <div class="main">
      <TopBar />

      <!-- 多标签页：打开的功能页以 tab 呈现，点标签切换、× 关闭、右键菜单批量关 -->
      <div v-if="tabsStore.tabs.length" class="tab-bar">
        <div
          v-for="t in tabsStore.tabs"
          :key="t.name"
          class="tab"
          :class="{ active: tabsStore.activeName === t.name }"
          @click="switchTab(t.name)"
          @contextmenu="onTabContext($event, t.name)"
        >
          <span class="tab-title">{{ t.title }}</span>
          <span class="tab-close" @click.stop="closeTab(t.name)">×</span>
        </div>
      </div>

      <!-- tab 右键菜单 -->
      <Teleport to="body">
        <div
          v-if="menu.show"
          class="tab-menu"
          :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
          @click.stop
        >
          <div class="tab-menu-item" @click="menuAction('current')">关闭当前</div>
          <div class="tab-menu-item" @click="menuAction('right')">关闭右侧</div>
          <div class="tab-menu-item" @click="menuAction('left')">关闭左侧</div>
          <div class="tab-menu-item" @click="menuAction('others')">关闭其他</div>
          <div class="tab-menu-item danger" @click="menuAction('all')">关闭全部</div>
        </div>
      </Teleport>

      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout { display: flex; height: 100vh; overflow: hidden; }
.sidebar { width: 72px; background: #fff; border-right: 1px solid #E5E8EF; display: flex; flex-direction: column; align-items: center; padding-top: 12px; flex-shrink: 0; overflow-y: auto; }
.logo { width: 44px; height: 44px; border-radius: 10px; background: #1E5EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; margin-bottom: 14px; flex-shrink: 0; }
.nav-item { position: relative; width: 60px; height: 58px; border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; margin-bottom: 5px; color: #4E5969; gap: 2px; flex-shrink: 0; transition: background-color .2s, color .2s; }
.nav-item:hover { background: #E8F0FF; color: #1E5EFF; }
.nav-item.active { background: #1E5EFF; color: #fff; }
.nav-item .ico { font-size: 19px; line-height: 1; }
.nav-item .lbl { font-size: 11px; }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.content { flex: 1; overflow: auto; background: #F5F7FA; padding: 16px 20px; }

/* 多标签页 */
.tab-bar { display: flex; align-items: center; gap: 4px; background: #fff; border-bottom: 1px solid #E5E8EF; padding: 6px 12px 0; flex-shrink: 0; overflow-x: auto; }
.tab { display: flex; align-items: center; gap: 6px; padding: 7px 12px; font-size: 13px; color: #4E5969; background: #F5F7FA; border: 1px solid #E5E8EF; border-bottom: none; border-radius: 6px 6px 0 0; cursor: pointer; white-space: nowrap; user-select: none; }
.tab:hover { color: #1E5EFF; }
.tab.active { background: #fff; color: #1E5EFF; font-weight: 500; border-color: #E5E8EF; box-shadow: 0 -1px 0 #fff inset; margin-bottom: -1px; padding-bottom: 8px; }
.tab-close { display: inline-flex; align-items: center; justify-content: center; width: 15px; height: 15px; border-radius: 50%; font-size: 13px; line-height: 1; color: #86909C; }
.tab-close:hover { background: #E5E8EF; color: #1D2129; }

/* tab 右键菜单 */
.tab-menu { position: fixed; z-index: 1050; min-width: 130px; background: #fff; border: 1px solid #E5E8EF; border-radius: 8px; box-shadow: 0 6px 24px rgba(29,33,41,.14); padding: 4px; }
.tab-menu-item { padding: 7px 12px; font-size: 13px; color: #1D2129; border-radius: 6px; cursor: pointer; user-select: none; }
.tab-menu-item:hover { background: #F2F3F5; }
.tab-menu-item.danger { color: #F53F3F; }
.tab-menu-item.danger:hover { background: #FFECE8; }
</style>
