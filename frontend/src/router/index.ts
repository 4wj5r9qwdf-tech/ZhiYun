import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
    // /home = 工作台（登录默认首页）
    { path: '/home', name: 'Workbench', component: () => import('../views/Workbench.vue') },
    // /system = 系统模块布局（左侧导航+顶栏），子路由在内容区渲染
    {
      path: '/system',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'SystemIndex', component: () => import('../views/SystemIndex.vue') },
        { path: 'company', name: 'Company', component: () => import('../views/Company.vue') },
      ],
    },
    // /person = 人员模块布局（左侧导航+顶栏），子路由在内容区渲染
    //   '' → PersonIndex（人员模块功能卡片：人员信息等）
    //   'person-info' → Person（人员信息列表，功能页）
    {
      path: '/person',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'PersonIndex', component: () => import('../views/PersonIndex.vue') },
        { path: 'person-info', name: 'Person', component: () => import('../views/Person.vue') },
      ],
    },
  ],
})

// 路由守卫：需登录页面，未登录跳 /login
const needAuth = ['Workbench', 'SystemIndex', 'Company', 'PersonIndex', 'Person']
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (needAuth.includes(to.name as string) && !token) {
    return { name: 'Login' }
  }
  return true
})

export default router
