-- 文件用途：为“尺码自动编码规则_20261104”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“尺码自动编码规则_20261104”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 尺码编码规则使用系统参数存储；无表结构变更。
INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码自动编码规则', 'system.code.size',
       '{"enabled":true,"prefix":"SZ","digits":4}', 'Y', now(),
       '尺码自动编码开关、编码前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.size');

INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码组自动编码规则', 'system.code.sizeGroup',
       '{"enabled":true,"prefix":"SG","digits":4}', 'Y', now(),
       '尺码组自动编码开关、编号前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.sizeGroup');
