-- 文件用途：为“审批模块权限_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“审批模块权限_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 设计思路：以可重复执行 DDL 和事务控制明确迁移顺序；数据含义以实际 SQL 定义为准。
-- 实现方法：按脚本声明顺序创建/调整表字段、约束、索引、权限并写数据库 COMMENT。
-- 关键约束：不得改变已有数据含义或绕过脚本约束。
-- 依赖与引用：sql/主建表.sql；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
-- 初始开发人员：JY（织云项目自研内容）
-- 初始开发时间：待追溯（不以补录时间代替原始开发时间）
-- 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
-- 最近修改人员：JY
-- AI辅助工具：Codex（智能体）
-- 最近修改时间：2026-11-20
-- 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
-- 最近测试时间：本次整理未执行功能测试
-- 测试人员：本次整理未执行
-- 测试状态：待功能回归；历史测试记录待追溯
-- 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
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
