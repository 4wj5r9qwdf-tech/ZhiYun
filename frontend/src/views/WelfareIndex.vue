<template>
  <main class="module-page">
    <header>
      <a-typography-title :level="4">后勤</a-typography-title>
      <p>员工住宿与餐饮服务统一从这里进入。</p>
    </header>
    <a-card class="placeholder" :bordered="false">
      <a-empty description="后勤入口已建立，相关功能待开发">
        <template #image>
          <SmileOutlined class="empty-icon" />
        </template>
      </a-empty>
      <div class="planned">
        <a-tag>宿舍管理</a-tag>
        <a-tag>食堂管理</a-tag>
      </div>
    </a-card>
  </main>
</template>

<script setup lang="ts">
import { SmileOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.module-page { padding: 24px; }
header { margin-bottom: 20px; }
header :deep(.ant-typography) { margin-bottom: 4px; color: #1d2129; }
header p { margin: 0; color: #86909c; }
.placeholder { min-height: 300px; border-radius: 8px; }
.placeholder :deep(.ant-card-body) { display: flex; min-height: 260px; flex-direction: column; align-items: center; justify-content: center; }
.empty-icon { color: #b8c7e6; font-size: 48px; }
.planned { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.planned :deep(.ant-tag) { margin: 0; }
</style>
