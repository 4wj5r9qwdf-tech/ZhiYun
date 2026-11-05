<template>
  <main class="module-page">
    <header>
      <a-typography-title :level="4">研发</a-typography-title>
      <p>管理款式研发、技术资料与样衣任务。</p>
    </header>
    <a-card class="placeholder" :bordered="false">
      <a-empty description="研发模块已建立，功能正在规划实施中">
        <template #image>
          <ExperimentOutlined class="empty-icon" />
        </template>
      </a-empty>
      <div class="planned">
        <span>后续功能</span>
        <a-tag>款式档案</a-tag>
        <a-tag>技术资料</a-tag>
        <a-tag>样衣任务</a-tag>
        <a-tag>研发看板</a-tag>
      </div>
    </a-card>
  </main>
</template>

<script setup lang="ts">
import { ExperimentOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.module-page { padding: 24px; }
header { margin-bottom: 20px; }
header :deep(.ant-typography) { margin-bottom: 4px; color: #1d2129; }
header p { margin: 0; color: #86909c; }
.placeholder { min-height: 300px; border-radius: 8px; }
.placeholder :deep(.ant-card-body) { display: flex; min-height: 260px; flex-direction: column; align-items: center; justify-content: center; }
.empty-icon { color: #b8c7e6; font-size: 48px; }
.planned { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 18px; color: #86909c; font-size: 13px; }
.planned :deep(.ant-tag) { margin: 0; }
</style>
