<!--
文件用途：提供织云 ERP SystemIndex的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP SystemIndex的查询、编辑、状态展示和用户操作界面。
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
<script setup lang="ts">
/**
 * 系统模块首页（内容区）：功能卡片网格（按用途分组）
 * 卡片由系统级组件 FunctionCardGrid 渲染（拖拽/拉伸/置顶/加工作台/待办红点）
 */
import { computed, ref, onMounted } from 'vue'
import api from '../api'
import FunctionCardGrid from '../components/FunctionCardGrid.vue'
import { functionsOf } from '../config/modules'

// 功能卡片目录统一维护在 config/modules.ts；icon 直接传 antd 图标名（FunctionCardGrid 渲染 antd 组件）
const cards = computed(() => functionsOf('system').map(f => ({
  key: f.key,
  title: f.title,
  desc: f.desc,
  icon: f.icon,
  route: f.route,
})))

const categories = [
  { title: '权限与入口', keys: ['system_role', 'menu-directory', 'clients'] },
  { title: '系统配置', keys: ['company', 'parameters', 'dictionary', 'system_template'] },
  { title: '业务基础资料', keys: ['system_unit', 'system_category', 'system_color', 'system_tag', 'system_size', 'system_size_group', 'system_country_region'] },
  { title: '文件与存储', keys: ['files', 'storage-config'] },
  { title: '运行监控', keys: ['monitor_online', 'monitor_operlog', 'system_login_logs', 'cache-overview', 'framework_admin_monitor'] },
  { title: '开发与扩展', keys: ['system_code_generator', 'framework_api_docs', 'framework_snailjob', 'framework_snailai', 'framework_spring_ai_alibaba'] },
]
const groups = computed(() => {
  const classified = new Set(categories.flatMap(group => group.keys))
  return [
    ...categories.map(group => ({ title: group.title, cards: cards.value.filter(card => group.keys.includes(card.key)) })),
    { title: '其他', cards: cards.value.filter(card => !classified.has(card.key)) },
  ].filter(group => group.cards.length > 0)
})

// 功能级待办数（无待办不显示红点）
const todoByMenu = ref<Record<string, number>>({})
onMounted(loadTodo)

async function loadTodo() {
  try {
    const res: any = await api.get('/system/profile/todo/count')
    if (res.code === 200) {
      const byMenu: Record<string, number> = {}
      for (const r of res.data || []) byMenu[r.menu_key] = Number(r.count) || 0
      todoByMenu.value = byMenu
    }
  } catch { /* ignore */ }
}
</script>

<template>
  <div>
    <p class="tip">拖拽卡片调顺序 · 拖动手柄独立调整宽高 · ⤒置顶 · ★加工作台 · 配置自动保存</p>
    <div v-for="group in groups" :key="group.title" class="group">
      <a-typography-title :level="4" class="group-title">{{ group.title }}</a-typography-title>
      <FunctionCardGrid module-key="system" :cards="group.cards" :todo="todoByMenu" />
    </div>
  </div>
</template>

<style scoped>
.group { margin-bottom: 24px; }
.group-title { font-weight: 600; color: #1D2129; margin-bottom: 12px; padding-left: 10px; border-left: 3px solid #1E5EFF; }
.tip { font-size: 12px; color: #86909C; margin-bottom: 12px; }
</style>
