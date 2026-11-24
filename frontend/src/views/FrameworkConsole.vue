<!--
文件用途：提供织云 ERP FrameworkConsole的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP FrameworkConsole的查询、编辑、状态展示和用户操作界面。
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
/** 在织云系统管理中提供框架独立控制台入口；不复用或转发系统登录令牌。 / Provide links to separate framework consoles without forwarding ERP credentials. */
import { computed } from 'vue'

const props=defineProps<{kind:string}>()
const consoles:Record<string,{title:string;port:number;path:string;description:string}>={
  admin:{title:'Spring Boot Admin 运行监控',port:9090,path:'/admin',description:'查看已注册服务的健康状态、运行指标和日志端点。监控台使用 ERP 超管账号认证，在独立控制台单独登录。'},
  snailjob:{title:'SnailJob 任务调度',port:8800,path:'/snail-job/',description:'管理框架 SnailJob 的调度任务、执行器和运行记录。任务调度服务需要单独启动。'},
  snailai:{title:'SnailAI 智能体管理',port:8900,path:'/snail-ai/',description:'管理框架 SnailAI 的智能体、知识库与相关运行配置。智能服务需要单独启动。'},
  api:{title:'框架接口文档',port:8080,path:'/swagger-ui/index.html',description:'查看当前部署版本开放的 API 契约与请求结构。接口文档仅用于开发联调，实际调用仍受后端认证和权限控制。'},
}
const kind=computed(()=>props.kind||'admin')
const current=computed(()=>consoles[kind.value]||consoles.admin)
const configuredUrl=computed(()=>{
  const key=({admin:'VITE_FRAMEWORK_ADMIN_URL',snailjob:'VITE_SNAILJOB_URL',snailai:'VITE_SNAILAI_URL',api:'VITE_FRAMEWORK_API_DOCS_URL'} as Record<string,string>)[kind.value]
  const configured=(import.meta.env as Record<string,string|undefined>)[key]
  return configured||`${location.protocol}//${location.hostname}:${current.value.port}${current.value.path}`
})
function openConsole(){window.open(configuredUrl.value,'_blank','noopener,noreferrer')}
</script>
<template><section class="console-page"><a-card class="console-card"><template #title><span class="console-title">{{current.title}}</span></template><a-result status="info" title="框架功能入口已接入" :sub-title="current.description"><template #extra><a-button type="primary" @click="openConsole">新窗口打开</a-button></template></a-result><a-alert type="warning" show-icon :message="kind==='api'?'接口文档不代表接口开放访问；请求仍需按后端认证、角色和功能权限执行。':kind==='admin'?'监控台凭据与 ERP 超管账号一致，但独立控制台仍需单独登录；本入口不会转发 ERP 登录令牌。':'该框架功能使用独立服务和自身访问认证。本入口不会把 ERP 登录令牌发送到独立服务，请使用部署环境对应的服务地址与账号。'"/><div class="address">当前入口：<code>{{configuredUrl}}</code></div></a-card></section></template>
<style scoped>.console-page{height:100%;padding:12px 0}.console-card{max-width:900px;margin:0 auto}.console-title{font-size:16px}.address{margin-top:18px;color:#667085;font-size:13px}.address code{margin-left:8px}</style>
