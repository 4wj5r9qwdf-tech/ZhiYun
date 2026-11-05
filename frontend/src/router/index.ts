import { canOpenFunction } from '../config/modules'
import { ensurePerms } from '../utils/perms'
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
        { path: 'online', name: 'OnlineUsers', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'online' }, meta: { title: '在线用户', tab: true } },
        { path: 'operlog', name: 'OperationLogs', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'logs' }, meta: { title: '系统日志', tab: true } },
        { path: 'loginInfo', redirect: { name: 'OperationLogs' } },
        { path: 'menu-directory', name: 'MenuDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'menu-directory' }, meta: { title: '菜单管理', tab: true } },
        { path: 'cache-overview', name: 'CacheOverview', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'cache-overview' }, meta: { title: '缓存监控', tab: true } },
        { path: 'dictionary', name: 'DictionaryDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'dictionary' }, meta: { title: '字典管理', tab: true } },
        { path: 'parameters', name: 'ParameterDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'parameters' }, meta: { title: '参数设置', tab: true } },
        { path: 'files', name: 'FileDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'files' }, meta: { title: '文件管理', tab: true } },
        { path: 'storage-config', name: 'StorageDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'storage-config' }, meta: { title: '文件配置管理', tab: true } },
        { path: 'clients', name: 'ClientDirectory', component: () => import('../views/SystemMonitor.vue'), props: { kind: 'clients' }, meta: { title: '客户端管理', tab: true } },
        { path: '', name: 'SystemIndex', component: () => import('../views/SystemIndex.vue'), meta: { title: '系统管理' } },
        { path: 'company', name: 'Company', component: () => import('../views/Company.vue'), meta: { title: '公司档案', tab: true } },
        { path: 'role', name: 'RoleManage', component: () => import('../views/RoleManage.vue'), meta: { title: '角色管理', tab: true } },
        { path: 'template', name: 'SystemTemplate', component: () => import('../views/SystemTemplate.vue'), meta: { title: '功能模板', tab: true } },
        { path: 'tag', name: 'TagManage', component: () => import('../views/TagManage.vue'), meta: { title: '标签管理', tab: true } },
        { path: 'size', name: 'SizeManage', component: () => import('../views/SizeManage.vue'), meta: { title: '尺码管理', tab: true } },
        { path: 'size-group', name: 'SizeGroupManage', component: () => import('../views/SizeGroupManage.vue'), meta: { title: '尺码组管理', tab: true } },
        { path: 'country-region', name: 'CountryRegionManage', component: () => import('../views/CountryRegionManage.vue'), meta: { title: '国家/地区', tab: true } },
      ],
    },
    { path: '/order', component: () => import('../views/Home.vue'), children: [{ path: '', name: 'OrderIndex', component: () => import('../views/OrderIndex.vue'), meta: { title: '订单' } }, { path: 'customer', name: 'CustomerManage', component: () => import('../views/CustomerManage.vue'), meta: { title: '客户档案', tab: true } }] },
    { path: '/stock', component: () => import('../views/Home.vue'), children: [
      { path: '', name: 'StockIndex', component: () => import('../views/StockIndex.vue'), meta: { title: '库存' } },
      { path: 'warehouse', name: 'WarehouseManage', component: () => import('../views/WarehouseManage.vue'), meta: { title: '仓库管理', tab: true } },
      { path: 'locations', name: 'WarehouseLocationManage', component: () => import('../views/WarehouseLocationManage.vue'), meta: { title: '库位管理', tab: true } },
    ] },
    { path: '/rd', component: () => import('../views/Home.vue'), children: [
      { path: '', name: 'RAndDIndex', component: () => import('../views/RAndDIndex.vue'), meta: { title: '研发' } },
    ] },
    { path: '/welfare', component: () => import('../views/Home.vue'), children: [
      { path: '', name: 'WelfareIndex', component: () => import('../views/WelfareIndex.vue'), meta: { title: '后勤' } },
    ] },
    // /person = 人员模块布局（左侧导航+顶栏），子路由在内容区渲染
    //   '' → PersonIndex（人员模块功能卡片：人员信息等）
    //   'person-info' → Person（人员信息列表，功能页）
    {
      path: '/person',
      component: () => import('../views/Home.vue'),
      children: [
        { path: '', name: 'PersonIndex', component: () => import('../views/PersonIndex.vue'), meta: { title: '人员' } },
        { path: 'person-info', name: 'Person', component: () => import('../views/Person.vue'), meta: { title: '人员信息', tab: true } },
        { path: 'org', name: 'OrgManage', component: () => import('../views/OrgManage.vue'), meta: { title: '组织架构', tab: true } },
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
  ClientDirectory: 'system:client:list',
  StorageDirectory: 'system:ossConfig:list',
  FileDirectory: 'system:oss:list',
  ParameterDirectory: 'system:config:list',
  DictionaryDirectory: 'system:dict:list',
  CacheOverview: 'monitor:cache:list',
  MenuDirectory: 'system:menu:list',
  OperationLogs: 'system-logs',
  OnlineUsers: 'monitor:online:list',
  Person: 'system:person:list', OrgManage: 'system:org:list',
  PostManage: 'system:post:list', RoleManage: 'system:role:list', SystemTemplate: 'system:template:list', TagManage: 'system:tag:list', BuildingManage: 'system:building:list',
  WarehouseManage: 'inventory:warehouse:list', WarehouseLocationManage: 'inventory:location:list',
  SizeManage: 'system:size:list', SizeGroupManage: 'system:size-group:list',
  Company: 'system:company:list', CustomerManage: 'system:customer:list', CountryRegionManage: 'system:country:list',
}
const needAuth = ['ClientDirectory', 'StorageDirectory', 'FileDirectory', 'ParameterDirectory', 'DictionaryDirectory', 'CacheOverview', 'MenuDirectory', 'LoginLogs', 'OperationLogs', 'OnlineUsers', 'SizeManage', 'SizeGroupManage', 'OrderIndex', 'CustomerManage', 'Workbench', 'SystemIndex', 'Company', 'RoleManage', 'SystemTemplate', 'TagManage', 'CountryRegionManage', 'PersonIndex', 'Person', 'OrgManage', 'PostManage', 'PropertyIndex', 'BuildingManage', 'StockIndex', 'WarehouseManage', 'WarehouseLocationManage', 'RAndDIndex', 'WelfareIndex']
router.beforeEach(async (to) => {
  const token = sessionStorage.getItem('token')
  if (needAuth.includes(to.name as string) && !token) {
    return { name: 'Login' }
  }
  // 所有需登录页面统一预拉权限（首页/工作台等无 perm 映射的入口也要就绪，否则卡片全隐身）
  if (token && needAuth.includes(to.name as string)) {
    await ensurePerms()
    const tabs = useTabsStore()
    tabs.tabs = tabs.tabs.filter(tab => {
      const required = ROUTE_PERMS[tab.name]
      const route = router.resolve(tab.path)
      return route.name === tab.name && (!required || canOpenFunction(required))
    })
    tabs.persist()
  }
  const need = ROUTE_PERMS[to.name as string]
  if (need && token && !canOpenFunction(need)) {
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
