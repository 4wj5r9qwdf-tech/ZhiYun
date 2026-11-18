-- 审批模块独立挂载角色权限；个人功能此前对登录人员开放，升级时保留现有角色访问。
BEGIN;
CREATE TEMP TABLE approval_permission_migration_flag ON COMMIT DROP AS
SELECT NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000000) AS first_install;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(1763200000000000000,0,'审批',70,'approval','','N','Y','M','0','0','','#',now()),
(1763200000000000001,1763200000000000000,'流程定义',10,'definitions','approval/definitions','N','Y','C','0','0','workflow:definition:list','#',now()),
(1763200000000000002,1763200000000000000,'我的待办',20,'waiting','approval/waiting','N','Y','C','0','0','approval:waiting:list','#',now()),
(1763200000000000003,1763200000000000000,'我发起的',30,'initiated','approval/initiated','N','Y','C','0','0','approval:initiated:list','#',now()),
(1763200000000000004,1763200000000000000,'我的已办',40,'finished','approval/finished','N','Y','C','0','0','approval:finished:list','#',now()),
(1763200000000000005,1763200000000000000,'审批对象配置',50,'bindings','approval/bindings','N','Y','C','0','0','approval:binding:list','#',now()),
(1763200000000000006,1763200000000000002,'审批办理',10,'#','','N','Y','F','0','0','approval:waiting:handle','#',now()),
(1763200000000000007,1763200000000000005,'配置审批对象',10,'#','','N','Y','F','0','0','approval:binding:edit','#',now()),
(1763200000000000008,1763200000000000001,'新增流程',10,'#','','N','Y','F','0','0','workflow:definition:add','#',now()),
(1763200000000000009,1763200000000000001,'发布流程',20,'#','','N','Y','F','0','0','workflow:definition:publish','#',now())
ON CONFLICT (menu_id) DO NOTHING;
-- 迁移前个人审批功能未限制角色；沿用访问权限，之后可在角色管理中调整。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE (SELECT first_install FROM approval_permission_migration_flag) AND r.del_flag='0' AND m.menu_id IN (1763200000000000000,1763200000000000002,1763200000000000003,1763200000000000004,1763200000000000006)
ON CONFLICT DO NOTHING;
-- 管理权限按已有等价权限迁移，不向无相关权限的角色额外授予配置权。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, target.menu_id FROM sys_role_menu rm JOIN sys_menu old ON old.menu_id=rm.menu_id
JOIN sys_menu target ON target.menu_id=CASE old.perms WHEN 'workflow:definition:list' THEN 1763200000000000001 WHEN 'workflow:definition:add' THEN 1763200000000000008 WHEN 'workflow:definition:publish' THEN 1763200000000000009 WHEN 'system:config:list' THEN 1763200000000000005 WHEN 'system:config:edit' THEN 1763200000000000007 END
WHERE (SELECT first_install FROM approval_permission_migration_flag)
ON CONFLICT DO NOTHING;
COMMIT;
