<script setup lang="ts">
/** 通用功能页布局：筛选、操作、可选侧栏和主体均由业务通过插槽提供。 */
withDefaults(defineProps<{ collapsed?: boolean }>(), { collapsed: false })
const emit = defineEmits<{ (e: 'update:collapsed', value: boolean): void }>()
</script>
<template>
  <div class="std-page">
    <div v-if="$slots.filters" class="std-filters"><slot name="filters" /></div>
    <div v-if="$slots.actions" class="std-actions"><slot name="actions" /></div>
    <div class="std-body" :class="{ 'has-sidebar': $slots.sidebar }">
      <aside v-if="$slots.sidebar" class="std-sidebar" :class="{ collapsed }"><slot name="sidebar" /></aside>
      <button v-if="$slots.sidebar" type="button" class="std-toggle" :class="{ 'at-left': collapsed }" :aria-label="collapsed ? '展开分类' : '收起分类'" :title="collapsed ? '展开分类' : '收起分类'" @click="emit('update:collapsed', !collapsed)"><span /></button>
      <main class="std-main"><slot /></main>
    </div>
  </div>
</template>
<style scoped>
.std-page { box-sizing: border-box; height: 100%; min-height: 0; padding: 12px 16px; display: flex; flex-direction: column; }
.std-filters { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.std-actions { margin-bottom: 12px; }
.std-body { display: flex; flex: 1; align-items: stretch; min-height: 0; position: relative; }
.std-body.has-sidebar { padding-left: 0; }
.std-sidebar { width: 260px; flex: none; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; overflow: hidden; transition: width .2s, opacity .2s; margin-right: 8px; }
.std-sidebar.collapsed { width: 0; height: 0; min-height: 0; opacity: 0; margin-right: 0; pointer-events: none; }
.std-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: -4px; z-index: 6; width: 8px; height: 40px; padding: 0; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.std-toggle span { width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }
.std-toggle.at-left span { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.std-toggle:hover { border-color: #1E5EFF; }.std-main { flex: 1; min-width: 0; }
</style>
