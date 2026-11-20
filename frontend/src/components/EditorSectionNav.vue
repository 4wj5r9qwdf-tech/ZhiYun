<!--
文件用途：为织云 ERP 页面提供可复用的EditorSectionNav展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的EditorSectionNav展示或交互能力。
设计思路：以属性接收调用页面提供的数据和配置，以事件返回用户操作；组件不决定业务授权。
实现方法：封装稳定视觉结构与通用交互，由业务页面传入数据并处理事件。
关键约束：不能绕过调用页或服务端的数据权限。
依赖与引用：Docs/规范/前端规范.md；frontend/src/views/SystemTemplate.vue
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
/** 左侧编号导航仅定位表单分区，不限制用户的填写顺序。 */
defineProps<{ eyebrow: string; title: string; description: string; sections: { key: string; title: string; hint: string }[]; active: string }>()
defineEmits<{ select: [key: string] }>()
</script>

<template>
  <aside class="editor-section-nav" aria-label="表单分区导航">
    <div class="nav-intro"><span>{{ eyebrow }}</span><strong>{{ title }}</strong><p>{{ description }}</p></div>
    <nav><button v-for="(section, index) in sections" :key="section.key" type="button" :class="{ active: active === section.key }" :aria-current="active === section.key ? 'location' : undefined" @click="$emit('select', section.key)"><b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ section.title }}<small>{{ section.hint }}</small></span></button></nav>
    <div class="nav-footer">带 <span>*</span> 的项目为必填项</div>
  </aside>
</template>

<style scoped>
.editor-section-nav { display:flex; flex-direction:column; min-height:0; overflow:auto; padding:24px 14px 20px; background:#fff; border-right:1px solid #edf0f5; }
.nav-intro { padding:0 10px 18px; border-bottom:1px solid #edf0f5; }
.nav-intro > span { display:block; color:#527bc0; font-size:12px; font-weight:600; }
.nav-intro strong { display:block; margin-top:10px; color:#263854; font-size:16px; }
.nav-intro p { margin:10px 0 0; color:#8998ad; font-size:12px; line-height:1.6; }
nav { display:grid; gap:4px; margin-top:16px; }
nav button { display:flex; align-items:center; gap:12px; padding:10px; border:0; border-radius:8px; background:transparent; text-align:left; color:#344660; font-weight:600; cursor:pointer; }
nav button:hover,nav button:focus-visible,nav button.active { background:#eef4ff; }
nav b { display:grid; place-items:center; width:32px; height:32px; flex:none; border-radius:9px; background:#edf3ff; color:#3970ce; font-size:12px; }
nav small { display:block; margin-top:2px; color:#94a1b4; font-size:12px; font-weight:400; }
.nav-footer { margin:auto 10px 0; padding-top:16px; color:#91a0b4; font-size:12px; }
.nav-footer span { color:#e45c5c; }
@media(max-width:760px) { .editor-section-nav { padding:8px; border-right:0; border-bottom:1px solid #edf0f5; } .nav-intro,.nav-footer { display:none; } nav { display:flex; overflow-x:auto; margin:0; } nav button { white-space:nowrap; } nav small { display:none; } }
</style>
