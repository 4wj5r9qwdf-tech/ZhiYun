-- 文件用途：为“人员分类权限配置授权_20261119”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“人员分类权限配置授权_20261119”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 人员分类权限设置作为独立按钮权限；仅登记权限，不自动授予普通角色。
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:person:list' AND menu_type='C') THEN
        RAISE EXCEPTION '未找到人员信息菜单，无法挂载分类权限设置';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1769900000000000087 AND perms IS DISTINCT FROM 'system:person:categoryScope') THEN
        RAISE EXCEPTION '人员分类权限菜单编号已被占用';
    END IF;
END $$;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1769900000000000087,parent.menu_id,'分类权限设置',90,'#','','N','Y','F','0','0','system:person:categoryScope','#',now()
FROM sys_menu parent WHERE parent.perms='system:person:list' AND parent.menu_type='C'
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:person:categoryScope')
LIMIT 1;
COMMIT;
