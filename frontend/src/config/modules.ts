import { canEnter } from '../utils/perms'
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
  { key: 'rd', icon: 'ExperimentOutlined', label: '研发' },
  { key: 'person', icon: 'TeamOutlined', label: '人员' },
  { key: 'welfare', icon: 'SmileOutlined', label: '后勤' },
  { key: 'property', icon: 'BankOutlined', label: '物业资产' },
  { key: 'finance', icon: 'AccountBookOutlined', label: '财务' },
  { key: 'approve', icon: 'AuditOutlined', label: '审批' },
  { key: 'alert', icon: 'AlertOutlined', label: '预警' },
  { key: 'system', icon: 'SettingOutlined', label: '系统管理' },
]

// 功能卡片目录：moduleKey → 功能列表（key 唯一，route=路由 name，module=所属模块）
export interface FunctionDef { key: string; title: string; desc: string; icon: string; route: string; module: string; perm?: string }

export const MODULE_FUNCTIONS: FunctionDef[] = [
  { key: 'approval_categories', title: '流程类别', desc: '维护审批流程分类', icon: 'FolderOutlined', route: 'ApprovalCategories', module: 'approve', perm: 'workflow:category:list' },
  { key: 'approval_definitions', title: '流程定义', desc: '查看审批流程', icon: 'ApartmentOutlined', route: 'ApprovalDefinitions', module: 'approve', perm: 'workflow:definition:list' },
  { key: 'approval_instances', title: '流程实例', desc: '查看运行中与已结束的流程实例', icon: 'ClusterOutlined', route: 'ApprovalInstances', module: 'approve', perm: 'workflow:instance:list' },
  { key: 'approval_task_monitor', title: '任务监控', desc: '查询全量待办和已办任务', icon: 'FundViewOutlined', route: 'ApprovalTaskMonitor', module: 'approve', perm: 'workflow:task:list' },
  { key: 'workflow_spel', title: '流程表达式', desc: '维护审批流程使用的表达式', icon: 'FunctionOutlined', route: 'WorkflowSpelManage', module: 'approve', perm: 'workflow:spel:list' },
  { key: 'workflow_leave_demo', title: '请假流程示例', desc: '查看框架自带的流程联动示例', icon: 'ExperimentOutlined', route: 'WorkflowLeaveDemo', module: 'approve', perm: 'workflow:leave:list' },
  { key: 'approval_waiting', title: '我的待办', desc: '查看待处理审批任务', icon: 'ClockCircleOutlined', route: 'ApprovalWaiting', module: 'approve', perm: 'approval:waiting:list' },
  { key: 'approval_initiated', title: '我发起的', desc: '查看我提交的审批进度', icon: 'SendOutlined', route: 'ApprovalInitiated', module: 'approve', perm: 'approval:initiated:list' },
  { key: 'approval_copies', title: '我的抄送', desc: '查看收到的审批抄送', icon: 'CopyOutlined', route: 'ApprovalCopies', module: 'approve', perm: 'approval:copy:list' },
  { key: 'approval_finished', title: '我的已办', desc: '查看已处理审批任务', icon: 'CheckCircleOutlined', route: 'ApprovalFinished', module: 'approve', perm: 'approval:finished:list' },
  { key: 'approval_bindings', title: '审批对象配置', desc: '配置业务单据使用的审批流程', icon: 'SettingOutlined', route: 'ApprovalBindingManage', module: 'approve', perm: 'approval:binding:list' },
  { key: 'system_notice', title: '通知公告', desc: '维护和发布通知公告', icon: 'NotificationOutlined', route: 'NoticeManage', module: 'system', perm: 'system:notice:list' },
  { key: 'system_login_logs', title: '登录日志', desc: '查看登录记录与失败信息', icon: 'LoginOutlined', route: 'LoginLogs', module: 'system', perm: 'monitor:logininfo:list' },
  { key: 'system_code_generator', title: '代码生成', desc: '导入数据库表并预览或下载生成代码', icon: 'CodeOutlined', route: 'CodeGeneratorManage', module: 'system', perm: 'tool:gen:list' },
  { key: 'framework_admin_monitor', title: 'Admin 运行监控', desc: '打开框架独立服务监控中心', icon: 'DashboardOutlined', route: 'FrameworkAdminConsole', module: 'system', perm: 'monitor:admin:list' },
  { key: 'framework_snailjob', title: 'SnailJob 任务调度', desc: '打开框架独立任务调度控制台', icon: 'ScheduleOutlined', route: 'FrameworkSnailJobConsole', module: 'system', perm: 'system:snailjob:list' },
  { key: 'framework_snailai', title: 'SnailAI 智能体', desc: '打开框架独立智能体控制台', icon: 'RobotOutlined', route: 'FrameworkSnailAiConsole', module: 'system', perm: 'system:snailai:list' },
  { key: 'snailai_traces', title: '智能体对话记录', desc: '查看员工对话及工具执行情况', icon: 'FileSearchOutlined', route: 'SnailAiTraces', module: 'system', perm: 'system:snailai:trace:list' },
  { key: 'framework_api_docs', title: '接口文档', desc: '查看框架开放的 API 契约', icon: 'ApiOutlined', route: 'FrameworkApiDocs', module: 'system', perm: 'system:api-docs:list' },
  { key: 'monitor_online', title: '在线用户', desc: '系统维护查询', icon: 'TeamOutlined', route: 'OnlineUsers', module: 'system', perm: 'monitor:online:list' },
  { key: 'monitor_operlog', title: '系统日志', desc: '系统维护查询', icon: 'AuditOutlined', route: 'OperationLogs', module: 'system', perm: 'system-logs' },
  { key: 'menu-directory', title: '菜单管理', desc: '查看系统维护资料', icon: 'MenuOutlined', route: 'MenuDirectory', module: 'system', perm: 'system:menu:list' },
  { key: 'cache-overview', title: '缓存监控', desc: '查看系统维护资料', icon: 'DashboardOutlined', route: 'CacheOverview', module: 'system', perm: 'monitor:cache:list' },
  { key: 'dictionary', title: '字典管理', desc: '查看系统维护资料', icon: 'BookOutlined', route: 'DictionaryDirectory', module: 'system', perm: 'system:dict:list' },
  { key: 'parameters', title: '参数设置', desc: '查看系统维护资料', icon: 'ControlOutlined', route: 'ParameterDirectory', module: 'system', perm: 'system:config:list' },
  { key: 'files', title: '文件管理', desc: '查看系统维护资料', icon: 'FolderOpenOutlined', route: 'FileDirectory', module: 'system', perm: 'system:oss:list' },
  { key: 'storage-config', title: '文件配置管理', desc: '查看系统维护资料', icon: 'CloudServerOutlined', route: 'StorageDirectory', module: 'system', perm: 'system:ossConfig:list' },
  { key: 'clients', title: '客户端管理', desc: '查看系统维护资料', icon: 'LaptopOutlined', route: 'ClientDirectory', module: 'system', perm: 'system:client:list' },
  // 系统模块
  { key: 'company', title: '公司档案', desc: '公司名/logo/登录背景', icon: 'BankOutlined', route: 'Company', module: 'system' , perm: 'system:company:list' },
  { key: 'system_role', title: '角色管理', desc: '权限/数据范围分配', icon: 'SafetyCertificateOutlined', route: 'RoleManage', module: 'system' , perm: 'system:role:list' },
  { key: 'system_template', title: '功能模板', desc: '标准件展览：树/列表/列设置/分页等，后期按需搬', icon: 'LayoutOutlined', route: 'SystemTemplate', module: 'system' , perm: 'system:template:list' },
  { key: 'system_tag', title: '标签管理', desc: '按模块维护标签与常用提示', icon: 'TagsOutlined', route: 'TagManage', module: 'system' , perm: 'system:tag:list' },
  { key: 'system_color', title: '颜色管理', desc: '维护物料和款式共用颜色', icon: 'BgColorsOutlined', route: 'ColorManage', module: 'system', perm: 'system:color:list' },
  { key: 'system_size', title: '尺码管理', desc: '尺码档案与类型标签', icon: 'ColumnWidthOutlined', route: 'SizeManage', module: 'system', perm: 'system:size:list' },
  { key: 'system_size_group', title: '尺码组管理', desc: '维护尺码组合与顺序', icon: 'GroupOutlined', route: 'SizeGroupManage', module: 'system', perm: 'system:size-group:list' },
  { key: 'system_unit', title: '计量单位', desc: '全局单位档案与固定换算', icon: 'ColumnWidthOutlined', route: 'UnitManage', module: 'system', perm: 'system:unit:list' },
  { key: 'system_category', title: '分类管理', desc: '按业务体系维护多级分类', icon: 'ApartmentOutlined', route: 'CategoryManage', module: 'system', perm: 'system:category:list' },
  { key: 'system_country_region', title: '国家/地区', desc: '维护国家和地区基础资料', icon: 'GlobalOutlined', route: 'CountryRegionManage', module: 'system', perm: 'system:country:list' },
  { key: 'customer_orders', title: '客户订单', desc: '客户 PO、配码、交期和物料需求', icon: 'FileTextOutlined', route: 'CustomerOrderManage', module: 'order', perm: 'order:customer-order:list' },
  { key: 'material_demand_plans', title: '物料需求计划', desc: '核对订单算料、调整需求并提交计划审批', icon: 'FileSearchOutlined', route: 'MaterialDemandPlanManage', module: 'order', perm: 'order:material-demand-plan:list' },
  { key: 'customer_master', title: '客户档案', desc: '客户主档/联系人/地址', icon: 'TeamOutlined', route: 'CustomerManage', module: 'order', perm: 'system:customer:list' },
  // 人员模块
  { key: 'person_info', title: '人员信息', desc: '员工档案/建登录账号', icon: 'TeamOutlined', route: 'Person', module: 'person' , perm: 'system:person:list' },
  { key: 'person_org', title: '组织架构', desc: '部门/科室/小组统一管理', icon: 'ApartmentOutlined', route: 'OrgManage', module: 'person' , perm: 'system:org:list' },
  { key: 'person_post', title: '岗位管理', desc: '裁剪/缝纫/后整等岗位', icon: 'IdcardOutlined', route: 'PostManage', module: 'person' , perm: 'system:post:list' },
  // 物业资产模块
  { key: 'property_building', title: '建筑档案', desc: '厂房/行政楼/宿舍楼', icon: 'ShopOutlined', route: 'BuildingManage', module: 'property' , perm: 'system:building:list' },
  { key: 'rd_style', title: '款式档案', desc: '维护研发、样衣与订单共用款式', icon: 'TagsOutlined', route: 'StyleManage', module: 'rd', perm: 'rd:style:list' },
  { key: 'rd_bom', title: '款式 BOM', desc: '维护款式用料、核对颜色尺码并试算需求', icon: 'FileTextOutlined', route: 'StyleBomManage', module: 'rd', perm: 'rd:bom:list' },
  // 库存模块
  { key: 'inventory_warehouse', title: '仓库管理', desc: '维护仓库档案、类型标签与负责人', icon: 'DatabaseOutlined', route: 'WarehouseManage', module: 'stock', perm: 'inventory:warehouse:list' },
  { key: 'inventory_location', title: '仓库布局', desc: '维护库区、货架、货位及所属关系', icon: 'EnvironmentOutlined', route: 'WarehouseLayoutManage', module: 'stock', perm: 'inventory:location:list' },
  { key: 'inventory_material', title: '物料档案', desc: '维护库存、采购和BOM共用物料资料', icon: 'InboxOutlined', route: 'MaterialManage', module: 'stock', perm: 'inventory:material:list' },
]

// 取某模块的功能卡片
export function functionsOf(moduleKey: string): FunctionDef[] {
  // 按当前用户权限过滤（无 perm 标注人人可见；admin 通配）
  return MODULE_FUNCTIONS.filter(f => f.module === moduleKey && canOpenFunction(f.perm))
}

/** 左侧模块图标可见性：模块下有卡片但全部无权限 → 隐藏；未实装模块（无卡片）维持显示 */
export function visibleModules(): ModuleDef[] {
  return MODULES.filter(m => {
    const fs = MODULE_FUNCTIONS.filter(f => f.module === m.key)
    if (fs.length === 0) return true
    return fs.some(f => canOpenFunction(f.perm))
  })
}

// 取全部功能目录（工作台展示已加入的功能用）
export function functionMap(): Record<string, FunctionDef> {
  const m: Record<string, FunctionDef> = {}
  for (const f of MODULE_FUNCTIONS.filter(f => canOpenFunction(f.perm))) m[f.key] = f
  return m
}

export function canOpenFunction(perm?: string) {
  return perm === 'system-logs' ? ['monitor:operlog:list', 'monitor:logininfo:list'].some(canEnter) : canEnter(perm)
}
