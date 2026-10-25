<script setup lang="ts">
/**
 * 全局：a-config-provider 统一 antd 主题（工业蓝 #1E5EFF / 圆角8 / 控件高36 / 紧凑算法）
 * 视觉定制走 Design Token，不覆盖组件样式。
 */
import theme from 'ant-design-vue/es/theme'

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
</script>

<template>
  <a-config-provider :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<style>
/* antd 4.x CSS-in-JS 自动注入，无需全局 reset.css */
</style>
