// 文件用途：集中实现前端index请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端index请求或共享处理能力。
// 设计思路：抽离跨页面复用逻辑，以明确输入输出减少重复；不替代服务端校验。
// 实现方法：提供 API 封装、格式化工具或组合状态供业务页面调用。
// 关键约束：不得伪造业务成功或改变服务端数据口径。
// 依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md
// 初始开发人员：JY（织云项目自研内容）
// 初始开发时间：待追溯（不以补录时间代替原始开发时间）
// 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
// 最近修改人员：JY
// AI辅助工具：Codex（智能体）
// 最近修改时间：2026-11-20
// 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
// 最近测试时间：本次整理未执行功能测试
// 测试人员：本次整理未执行
// 测试状态：待功能回归；历史测试记录待追溯
// 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
import axios from 'axios'

// RuoYi-Vue-Plus 6.x 登录客户端ID（sys_client 表 pc 客户端，登录与请求都要带）
export const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e'

// 后端地址（开发阶段直连，代理见 vite.config.ts）
const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截：带 token + clientid（RuoYi 6.x 校验客户端ID与Token匹配）
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers.clientid = CLIENT_ID
  return config
})

// 响应拦截：401 跳登录
api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('userId')
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default api
