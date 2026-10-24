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
  const token = localStorage.getItem('token')
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
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default api
