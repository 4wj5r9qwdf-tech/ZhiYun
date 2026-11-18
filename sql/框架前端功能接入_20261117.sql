-- 框架前端能力接入：恢复原有菜单入口并补充新控制台权限。
-- 保留已存在菜单ID和角色授权；新入口仅登记为可分配权限，不默认授予任何角色。
BEGIN;

-- 在应用的审批和系统管理目录下补齐框架页面入口。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.menu_id,v.parent_id,v.menu_name,v.order_num,v.path,v.component,'N','Y','C','0','0',v.perms,'#',now()
FROM (VALUES
 (1763300000000000001::bigint,1763200000000000000::bigint,'流程实例',60,'instances','approval/instances','workflow:instance:list'),
 (1763300000000000002::bigint,1763200000000000000::bigint,'任务监控',70,'task-monitor','approval/task-monitor','workflow:task:list'),
 (1763300000000000003::bigint,1763200000000000000::bigint,'流程表达式',80,'expressions','approval/expressions','workflow:spel:list'),
 (1763300000000000004::bigint,1763200000000000000::bigint,'请假流程示例',90,'leave-demo','approval/leave-demo','workflow:leave:list'),
 (1763300000000000011::bigint,1761400000000000001::bigint,'通知公告',110,'notice','system/notice/index','system:notice:list'),
 (1763300000000000012::bigint,1761400000000000001::bigint,'登录日志',120,'loginInfo','monitor/loginInfo/index','monitor:logininfo:list'),
 (1763300000000000013::bigint,1761400000000000001::bigint,'代码生成',130,'generator','tool/gen/index','tool:gen:list'),
 (1763300000000000014::bigint,1761400000000000001::bigint,'Admin 监控',140,'admin-monitor','monitor/admin/index','monitor:admin:list'),
 (1763300000000000015::bigint,1761400000000000001::bigint,'SnailJob 任务调度',150,'snailjob','system/snailjob/index','system:snailjob:list'),
 (1763300000000000016::bigint,1761400000000000001::bigint,'SnailAI 智能体',160,'snailai','system/snailai/index','system:snailai:list'),
 (1763300000000000017::bigint,1761400000000000001::bigint,'接口文档',170,'api-docs','system/api-docs/index','system:api-docs:list')
) AS v(menu_id,parent_id,menu_name,order_num,path,component,perms)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=v.perms)
ON CONFLICT(menu_id) DO NOTHING;

-- 复用旧框架菜单记录和角色关系，迁移到当前系统目录并启用入口。
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程实例',order_num=60,path='instances',component='approval/instances',menu_type='C',visible='0',status='0'
WHERE perms='workflow:instance:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='任务监控',order_num=70,path='task-monitor',component='approval/task-monitor',menu_type='C',visible='0',status='0'
WHERE perms='workflow:task:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程表达式',order_num=80,path='expressions',component='approval/expressions',menu_type='C',visible='0',status='0'
WHERE perms='workflow:spel:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='请假流程示例',order_num=90,path='leave-demo',component='approval/leave-demo',menu_type='C',visible='0',status='0'
WHERE perms='workflow:leave:list';

UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='通知公告',order_num=110,path='notice',component='system/notice/index',menu_type='C',visible='0',status='0'
WHERE perms='system:notice:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='登录日志',order_num=120,path='loginInfo',component='monitor/loginInfo/index',menu_type='C',visible='0',status='0'
WHERE perms='monitor:logininfo:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='代码生成',order_num=130,path='generator',component='tool/gen/index',menu_type='C',visible='0',status='0'
WHERE perms='tool:gen:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='Admin 监控',order_num=140,path='admin-monitor',component='monitor/admin/index',menu_type='C',visible='0',status='0'
WHERE perms='monitor:admin:list';

-- SnailJob、SnailAI 与接口文档入口；升级时按固定菜单 ID 对齐，不复制已有权限标识。
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='SnailJob 任务调度',order_num=150,path='snailjob',component='system/snailjob/index',menu_type='C',visible='0',status='0',perms='system:snailjob:list'
WHERE menu_id=1763300000000000015;
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='SnailAI 智能体',order_num=160,path='snailai',component='system/snailai/index',menu_type='C',visible='0',status='0',perms='system:snailai:list'
WHERE menu_id=1763300000000000016;
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='接口文档',order_num=170,path='api-docs',component='system/api-docs/index',menu_type='C',visible='0',status='0',perms='system:api-docs:list'
WHERE menu_id=1763300000000000017;

-- 确保框架端点所需的可分配操作权限存在；不自动写入 sys_role_menu。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.menu_id,parent.menu_id,v.menu_name,v.order_num,'#','','N','Y','F','0','0',v.perms,'#',now()
FROM (VALUES
 (1763300000000000101::bigint,'实例查询','workflow:instance:query','workflow:instance:list',1),
 (1763300000000000102::bigint,'实例变量查看','workflow:instance:variableQuery','workflow:instance:list',2),
 (1763300000000000103::bigint,'实例变量修改','workflow:instance:variable','workflow:instance:list',3),
 (1763300000000000104::bigint,'实例暂停恢复','workflow:instance:active','workflow:instance:list',4),
 (1763300000000000105::bigint,'实例删除','workflow:instance:remove','workflow:instance:list',5),
 (1763300000000000106::bigint,'实例作废','workflow:instance:invalid','workflow:instance:list',6),
 (1763300000000000107::bigint,'实例撤销','workflow:instance:cancel','workflow:instance:list',7),
 (1763300000000000108::bigint,'任务改派','workflow:task:edit','workflow:task:list',1),
 (1763300000000000109::bigint,'表达式查看','workflow:spel:query','workflow:spel:list',1),
 (1763300000000000110::bigint,'表达式新增','workflow:spel:add','workflow:spel:list',2),
 (1763300000000000111::bigint,'表达式修改','workflow:spel:edit','workflow:spel:list',3),
 (1763300000000000112::bigint,'表达式删除','workflow:spel:remove','workflow:spel:list',4),
 (1763300000000000113::bigint,'请假示例查看','workflow:leave:query','workflow:leave:list',1),
 (1763300000000000114::bigint,'请假示例新增','workflow:leave:add','workflow:leave:list',2),
 (1763300000000000115::bigint,'请假示例修改','workflow:leave:edit','workflow:leave:list',3),
 (1763300000000000116::bigint,'请假示例删除','workflow:leave:remove','workflow:leave:list',4),
 (1763300000000000117::bigint,'通知公告详情','system:notice:query','system:notice:list',1),
 (1763300000000000118::bigint,'通知公告新增','system:notice:add','system:notice:list',2),
 (1763300000000000119::bigint,'通知公告修改','system:notice:edit','system:notice:list',3),
 (1763300000000000120::bigint,'通知公告删除','system:notice:remove','system:notice:list',4),
 (1763300000000000121::bigint,'代码生成查看','tool:gen:query','tool:gen:list',1),
 (1763300000000000122::bigint,'代码生成配置','tool:gen:edit','tool:gen:list',2),
 (1763300000000000123::bigint,'代码生成删除','tool:gen:remove','tool:gen:list',3),
 (1763300000000000124::bigint,'代码生成导入','tool:gen:import','tool:gen:list',4),
 (1763300000000000125::bigint,'代码生成预览','tool:gen:preview','tool:gen:list',5),
 (1763300000000000126::bigint,'代码下载','tool:gen:code','tool:gen:list',6),
 (1763300000000000127::bigint,'登录日志删除','monitor:logininfo:remove','monitor:logininfo:list',1),
 (1763300000000000128::bigint,'登录日志导出','monitor:logininfo:export','monitor:logininfo:list',2),
 (1763300000000000129::bigint,'登录账户解锁','monitor:logininfo:unlock','monitor:logininfo:list',3)
) AS v(menu_id,menu_name,perms,parent_perm,order_num)
JOIN sys_menu parent ON parent.perms=v.parent_perm AND parent.menu_type='C'
WHERE NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms=v.perms)
ON CONFLICT(menu_id) DO NOTHING;

-- 将已存在的按钮授权节点挂到对应功能页面下，保留其 menu_id 和角色授权关系。
UPDATE sys_menu child SET parent_id=parent.menu_id,status='0'
FROM sys_menu parent
WHERE child.menu_type='F'
  AND ((child.perms LIKE 'workflow:instance:%' AND parent.perms='workflow:instance:list')
    OR (child.perms LIKE 'workflow:task:%' AND parent.perms='workflow:task:list')
    OR (child.perms LIKE 'workflow:spel:%' AND parent.perms='workflow:spel:list')
    OR (child.perms LIKE 'workflow:leave:%' AND parent.perms='workflow:leave:list')
    OR (child.perms LIKE 'system:notice:%' AND parent.perms='system:notice:list')
    OR (child.perms LIKE 'tool:gen:%' AND parent.perms='tool:gen:list')
    OR (child.perms LIKE 'monitor:logininfo:%' AND parent.perms='monitor:logininfo:list'));

COMMIT;
