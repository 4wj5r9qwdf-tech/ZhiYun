<script setup lang="ts">
/**
 * 系统模块布局壳（/system）：左侧竖排模块导航 + 顶栏(TopBar) + 内容区(<router-view>)
 * 内容区按子路由渲染：'' → SystemIndex(功能卡片)，'company' → Company(公司档案)
 * 左侧导航和顶栏常驻，子页面切换不丢导航。
 */
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import message from 'ant-design-vue/es/message'
import * as AntIcons from '@ant-design/icons-vue'
import TopBar from '../components/TopBar.vue'
import { MODULES, type ModuleDef } from '../config/modules'

const router = useRouter()
const route = useRoute()

// 左侧模块导航（目录统一维护在 config/modules.ts）
const modules = MODULES

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

      <div class="content">
        <div class="module-title">模块 / {{ activeModule === 'person' ? '人员' : activeModule === 'property' ? '物业资产' : '系统' }}</div>
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
.module-title { font-size: 14px; color: #4E5969; margin-bottom: 16px; }
</style>
