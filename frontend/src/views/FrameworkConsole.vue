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
