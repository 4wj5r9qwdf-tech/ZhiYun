import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
    { path: '/workbench', name: 'Workbench', component: () => import('../views/Workbench.vue') },
    { path: '/home', name: 'Home', component: () => import('../views/Home.vue') },
    { path: '/system/company', name: 'Company', component: () => import('../views/Company.vue') },
  ],
})

// 路由守卫：需登录页面，未登录跳 /login
const needAuth = ['Workbench', 'Home', 'Company']
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (needAuth.includes(to.name as string) && !token) {
    return { name: 'Login' }
  }
  return true
})

export default router
