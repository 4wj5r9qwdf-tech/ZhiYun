-- 文件用途：移除已退役的 Spring AI Alibaba Agent Studio ERP 管理菜单及角色授权。
-- 文件版本：1.0.0
-- 功能目的：清理由 sql/SpringAIAlibaba管理入口_20261119.sql 登记的专用菜单权限。
-- 设计思路：按稳定权限标识定位菜单，先删角色关联再删菜单，使脚本可重复执行。
-- 实现方法：在事务内删除 system:spring-ai-alibaba:list 对应的 sys_role_menu 与 sys_menu 记录。
-- 关键约束：只触及 Spring AI Alibaba 专用菜单和授权，不删除业务数据、角色或其他菜单。
-- 依赖与引用：sql/SpringAIAlibaba管理入口_20261119.sql；Docs/功能任务/ZY-ERP-20261124-SAA测试平台退役.md
-- 初始开发人员：JY（织云项目自研内容）
-- 初始开发时间：2026-11-24
-- 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
-- 最近修改人员：Codex（智能体）
-- AI辅助工具：Codex（智能体）
-- 最近修改时间：2026-11-24
-- 修改标记：ZY-ERP-20261124-SAA测试平台退役；新增可重复执行的菜单/授权清理迁移
-- 最近测试时间：2026-11-24
-- 测试人员：Codex（智能体）
-- 测试状态：开发库执行及幂等复跑通过；菜单与角色关联均为 0，独立测试数据库仍存在。
-- 测试范围与记录位置：见 Docs/功能任务/ZY-ERP-20261124-SAA测试平台退役.md
BEGIN;

DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:spring-ai-alibaba:list'
);

DELETE FROM sys_menu
WHERE perms = 'system:spring-ai-alibaba:list';

COMMIT;
