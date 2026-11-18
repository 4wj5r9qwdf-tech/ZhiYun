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
