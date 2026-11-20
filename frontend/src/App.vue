<!--
文件用途：支撑织云 ERP 前端的App启动、路由、布局或样式。
文件版本：1.0.0
功能目的：支撑织云 ERP 前端的App启动、路由、布局或样式。
设计思路：集中管理全局基础行为；业务页面遵循统一模板，权限由服务端复核。
实现方法：由 Vite/Vue 入口加载并向业务页面提供公共依赖或行为。
关键约束：不得与实际 API、权限和页面布局契约冲突。
依赖与引用：Docs/规范/前端规范.md；frontend/src/App.vue；frontend/src/router/index.ts
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-20
修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
最近测试时间：本次整理未执行功能测试
测试人员：本次整理未执行
测试状态：待功能回归；历史测试记录待追溯
测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
-->
<script setup lang="ts">
/**
 * 全局：a-config-provider 统一 antd 主题（工业蓝 #1E5EFF / 圆角8 / 控件高36 / 紧凑算法）
 * 视觉定制走 Design Token，不覆盖组件样式。
 */
import theme from 'ant-design-vue/es/theme'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ErpAiAssistant from './components/ErpAiAssistant.vue'
import { useFloatingDrag } from './composables/useFloatingDrag'

const route = useRoute()
const assistantOpen = ref(false)
const floatingButton = ref<HTMLElement>()
const ballDrag = useFloatingDrag(floatingButton)
function toggleAssistant(event: MouseEvent) {
  if (ballDrag.dragged.value && event.detail !== 0) return
  assistantOpen.value = !assistantOpen.value
}
const showAssistant = computed(() => route.name !== 'Login' && !!sessionStorage.getItem('token'))
watch(() => route.name, () => { if (route.name === 'Login') assistantOpen.value = false })

// 紧凑算法：收紧间距/控件尺寸，整体更轻（符合"卡片化、现代美学"）
const themeConfig = {
  token: {
    colorPrimary: '#1E5EFF',   // 工业蓝（品牌主色）
    colorInfo: '#1E5EFF',
    borderRadius: 8,           // 全局圆角，贴卡片化
    controlHeight: 36,         // 控件高度
    colorBgLayout: '#F5F7FA',  // 布局底色（preview 背景）
  },
  algorithm: [theme.defaultAlgorithm, theme.compactAlgorithm],
}

// 全站中文（分页"X 条/页"、日期、空态等 antd 文案统一中文化）
const localeCN = zhCN
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="localeCN">
    <router-view />
    <template v-if="showAssistant">
      <button ref="floatingButton" class="ai-floating-entry" :class="{ active: assistantOpen }" :style="ballDrag.style.value" data-ai-capture-exclude type="button" :aria-label="assistantOpen ? '收起织云智能助手' : '打开织云智能助手'" title="拖动调整位置，点击打开助手" @pointerdown="ballDrag.start" @click="toggleAssistant">
        <span class="ai-floating-glow" aria-hidden="true"><span>✦</span></span>
      </button>
      <ErpAiAssistant v-model:open="assistantOpen" />
    </template>
  </a-config-provider>
</template>

<style>
/* antd 4.x CSS-in-JS 自动注入，无需全局 reset.css */
.ai-floating-entry { position: fixed; z-index: 20001; right: 170px; top: 7px; width: 42px; height: 42px; padding: 4px; border: 0; border-radius: 50%; cursor: grab; touch-action: none; user-select: none; background: conic-gradient(from 20deg, #62e6ff, #566aff, #d96cf4, #ffb2d3, #62e6ff); box-shadow: 0 5px 20px #365cdc55, 0 0 0 3px #fff9; }
.ai-floating-entry:active { cursor: grabbing; }
.ai-floating-entry:hover { transform: translateY(-2px); box-shadow: 0 11px 32px #365cdc80, 0 0 0 5px #fff9; }
.ai-floating-entry:focus-visible { outline: 3px solid #1e5eff; outline-offset: 4px; }
.ai-floating-glow { display: grid; place-items: center; width: 100%; height: 100%; border-radius: 50%; color: white; font-size: 21px; background: radial-gradient(circle at 30% 25%, #b1f8ff, #5875ff 40%, #a140ee 70%, #f085c9); background-size: 150% 150%; text-shadow: 0 0 14px white; animation: ai-flow 4s ease-in-out infinite alternate; }
.ai-floating-entry.active .ai-floating-glow { animation-duration: 1.8s; box-shadow: inset 0 0 16px #ffffffa0, 0 0 18px #8f6dff9c; }
.ai-floating-glow span { animation: ai-breathe 3s ease-in-out infinite; }
@keyframes ai-flow { from { background-position: 0% 0%; filter: hue-rotate(0deg); } to { background-position: 100% 100%; filter: hue-rotate(35deg); } }
@keyframes ai-breathe { 50% { transform: scale(1.13); opacity: .8; } }
@media (prefers-reduced-motion: reduce) { .ai-floating-glow, .ai-floating-entry.active .ai-floating-glow, .ai-floating-glow span { animation: none; } }
</style>
