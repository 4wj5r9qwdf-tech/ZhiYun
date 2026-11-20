<!--
文件用途：提供织云 ERP WelfareIndex的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP WelfareIndex的查询、编辑、状态展示和用户操作界面。
设计思路：使用 Vue 3 响应式状态与项目公共布局；页面编排交互，服务端负责业务规则和最终授权。
实现方法：加载选项/列表，按当前状态驱动表单和弹窗，调用业务 API 后刷新并反馈结果。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
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
