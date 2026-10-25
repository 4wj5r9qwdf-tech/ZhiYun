import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
    // /home = 工作台（登录默认首页）
    { path: '/home', name: 'Workbench', component: () => import('../views/Workbench.vue'), meta: { title: '工作台' } },
    // /system = 系统模块布局（左侧导航+顶栏），子路由在内容区渲染
    {
      path: '/system',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'SystemIndex', component: () => import('../views/SystemIndex.vue'), meta: { title: '系统' } },
        { path: 'company', name: 'Company', component: () => import('../views/Company.vue'), meta: { title: '公司档案' } },
      ],
    },
    // /person = 人员模块布局（左侧导航+顶栏），子路由在内容区渲染
    //   '' → PersonIndex（人员模块功能卡片：人员信息等）
    //   'person-info' → Person（人员信息列表，功能页）
    {
      path: '/person',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'PersonIndex', component: () => import('../views/PersonIndex.vue'), meta: { title: '人员' } },
        { path: 'person-info', name: 'Person', component: () => import('../views/Person.vue'), meta: { title: '人员信息' } },
        { path: 'dept', name: 'DeptManage', component: () => import('../views/DeptManage.vue'), meta: { title: '部门管理' } },
        { path: 'group', name: 'GroupManage', component: () => import('../views/GroupManage.vue'), meta: { title: '小组管理' } },
        { path: 'post', name: 'PostManage', component: () => import('../views/PostManage.vue'), meta: { title: '岗位管理' } },
        { path: 'plant', name: 'BuildingManage', component: () => import('../views/BuildingManage.vue'), meta: { title: '建筑档案' } },
      ],
    },
  ],
})

// 路由守卫：需登录页面，未登录跳 /login；并按路由 meta.title 更新浏览器标签标题
const needAuth = ['Workbench', 'SystemIndex', 'Company', 'PersonIndex', 'Person', 'DeptManage', 'GroupManage', 'PostManage', 'BuildingManage']
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (needAuth.includes(to.name as string) && !token) {
    return { name: 'Login' }
  }
  // 浏览器标签标题：织云 ERP - <页面标题>
  const t = to.meta?.title as string | undefined
  document.title = t ? `织云 ERP - ${t}` : '织云 ERP'
  return true
})

export default router
