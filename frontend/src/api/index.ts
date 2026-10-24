import axios from 'axios'

// 后端地址（开发阶段直连，代理见 vite.config.ts）
const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截：带 token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
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
