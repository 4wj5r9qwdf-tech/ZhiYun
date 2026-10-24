import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
    { path: '/home', name: 'Home', component: () => import('../views/Home.vue') },
  ],
})

// 路由守卫：/home 需登录，未登录跳 /login
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.name === 'Home' && !token) {
    return { name: 'Login' }
  }
  return true
})

export default router
