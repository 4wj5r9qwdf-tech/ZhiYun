import { ensurePerms, can } from '../utils/perms'
import { createRouter, createWebHistory } from 'vue-router'
import { useTabsStore } from '../store/tabs'

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
        { path: 'company', name: 'Company', component: () => import('../views/Company.vue'), meta: { title: '公司档案', tab: true } },
        { path: 'role', name: 'RoleManage', component: () => import('../views/RoleManage.vue'), meta: { title: '角色管理', tab: true } },
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
        { path: 'person-info', name: 'Person', component: () => import('../views/Person.vue'), meta: { title: '人员信息', tab: true } },
        { path: 'dept', name: 'DeptManage', component: () => import('../views/DeptManage.vue'), meta: { title: '部门管理', tab: true } },
        { path: 'group', name: 'GroupManage', component: () => import('../views/GroupManage.vue'), meta: { title: '小组管理', tab: true } },
        { path: 'post', name: 'PostManage', component: () => import('../views/PostManage.vue'), meta: { title: '岗位管理', tab: true } },
      ],
    },
    // /property = 物业资产模块布局（左侧导航+顶栏），子路由在内容区渲染
    //   '' → PropertyIndex（物业资产功能卡片：建筑档案等）
    //   'building' → BuildingManage（建筑档案页）
    {
      path: '/property',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'PropertyIndex', component: () => import('../views/PropertyIndex.vue'), meta: { title: '物业资产' } },
        { path: 'building', name: 'BuildingManage', component: () => import('../views/BuildingManage.vue'), meta: { title: '建筑档案', tab: true } },
      ],
    },
  ],
})

// 路由守卫：需登录页面，未登录跳 /login；并按路由 meta.title 更新浏览器标签标题
// 路由级权限映射：无权限直接访问 URL 也拦（卡片显隐只是第一层）
const ROUTE_PERMS: Record<string, string> = {
  Person: 'system:person:list', DeptManage: 'system:dept:list', GroupManage: 'system:dept:list',
  PostManage: 'system:post:list', RoleManage: 'system:role:list', BuildingManage: 'system:building:list',
  Company: 'system:company:list',
}
const needAuth = ['Workbench', 'SystemIndex', 'Company', 'RoleManage', 'PersonIndex', 'Person', 'DeptManage', 'GroupManage', 'PostManage', 'PropertyIndex', 'BuildingManage']
router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (needAuth.includes(to.name as string) && !token) {
    return { name: 'Login' }
  }
  // 所有需登录页面统一预拉权限（首页/工作台等无 perm 映射的入口也要就绪，否则卡片全隐身）
  if (token && needAuth.includes(to.name as string)) {
    await ensurePerms()
  }
  const need = ROUTE_PERMS[to.name as string]
  if (need && token && !can(need)) {
    return { name: 'Workbench' }
  }
  // 浏览器标签标题：织云 ERP - <页面标题>
  const t = to.meta?.title as string | undefined
  document.title = t ? `织云 ERP - ${t}` : '织云 ERP'
  return true
})

// 多标签页同步：进入带 meta.tab 的功能页 → 开 tab；进入模块首页 → 不开（但保留已开 tab，仅切激活）
router.afterEach((to) => {
  const tabs = useTabsStore()
  if (to.meta?.tab && to.name) {
    tabs.openTab({ name: String(to.name), title: String(to.meta.title || to.name), path: to.fullPath })
  } else if (to.name) {
    // 模块首页/工作台：不清 tab，仅在该 name 恰好是 tab 时同步激活（一般不会）
    tabs.setActive(String(to.name))
  }
})

export default router
