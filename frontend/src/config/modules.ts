/**
 * 全站目录配置（系统级，集中维护）：
 * - modules：左侧竖排模块导航（Home/Workbench 共用）
 * - moduleFunctions：各模块的功能卡片目录（SystemIndex/PersonIndex/Workbench 共用）
 * 图标统一用 ant-design 图标组件名（字符串），渲染时动态解析（见 renderIcon 助手）。
 */

// antd 图标组件名（字符串），渲染处用 @ant-design/icons-vue 的动态解析
export interface ModuleDef { key: string; icon: string; label: string }

export const MODULES: ModuleDef[] = [
  { key: 'workbench', icon: 'DashboardOutlined', label: '工作台' },
  { key: 'order', icon: 'ShoppingOutlined', label: '订单' },
  { key: 'prod', icon: 'ApartmentOutlined', label: '生产' },
  { key: 'stock', icon: 'DatabaseOutlined', label: '库存' },
  { key: 'person', icon: 'TeamOutlined', label: '人员' },
  { key: 'dorm', icon: 'HomeOutlined', label: '宿舍' },
  { key: 'canteen', icon: 'SmileOutlined', label: '食堂' },
  { key: 'vehicle', icon: 'CarOutlined', label: '车辆' },
  { key: 'property', icon: 'BankOutlined', label: '物业资产' },
  { key: 'finance', icon: 'AccountBookOutlined', label: '财务' },
  { key: 'approve', icon: 'AuditOutlined', label: '审批' },
  { key: 'alert', icon: 'AlertOutlined', label: '预警' },
  { key: 'system', icon: 'SettingOutlined', label: '系统' },
]

// 功能卡片目录：moduleKey → 功能列表（key 唯一，route=路由 name，module=所属模块）
export interface FunctionDef { key: string; title: string; desc: string; icon: string; route: string; module: string }

export const MODULE_FUNCTIONS: FunctionDef[] = [
  // 系统模块
  { key: 'company', title: '公司档案', desc: '公司名/logo/登录背景', icon: 'BankOutlined', route: 'Company', module: 'system' },
  // 人员模块
  { key: 'person_info', title: '人员信息', desc: '员工档案/建登录账号', icon: 'TeamOutlined', route: 'Person', module: 'person' },
  { key: 'person_dept', title: '部门管理', desc: '厂→车间→区域', icon: 'ApartmentOutlined', route: 'DeptManage', module: 'person' },
  { key: 'person_group', title: '小组管理', desc: '小组挂车间/区域下', icon: 'ClusterOutlined', route: 'GroupManage', module: 'person' },
  { key: 'person_post', title: '岗位管理', desc: '裁剪/缝纫/后整等岗位', icon: 'IdcardOutlined', route: 'PostManage', module: 'person' },
  // 物业资产模块
  { key: 'property_building', title: '建筑档案', desc: '厂房/行政楼/宿舍楼', icon: 'ShopOutlined', route: 'BuildingManage', module: 'property' },
]

// 取某模块的功能卡片
export function functionsOf(moduleKey: string): FunctionDef[] {
  return MODULE_FUNCTIONS.filter(f => f.module === moduleKey)
}

// 取全部功能目录（工作台展示已加入的功能用）
export function functionMap(): Record<string, FunctionDef> {
  const m: Record<string, FunctionDef> = {}
  for (const f of MODULE_FUNCTIONS) m[f.key] = f
  return m
}
