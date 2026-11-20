-- 文件用途：为“审批部门负责人规则_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“审批部门负责人规则_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 将框架预置的部门负责人表达式接到织云组织架构。
UPDATE flow_spel
SET method_name='selectOrgLeaderById', method_params='initiatorOrgId',
    view_spel='#{@spelRuleComponent.selectOrgLeaderById(#initiatorOrgId)}',
    remark='发起人所在部门负责人', update_time=now()
WHERE component_name='spelRuleComponent'
  AND method_name='selectDeptLeaderById' AND method_params='initiatorDeptId';

-- 沿用框架原有按钮 ID 和角色关系，将维护权限显示在审批模块下。
UPDATE sys_menu
SET parent_id=1763200000000000001, status='0'
WHERE parent_id=1761400000000011620
  AND perms IN ('workflow:definition:query','workflow:definition:edit','workflow:definition:remove',
                'workflow:definition:export','workflow:definition:import','workflow:definition:copy','workflow:definition:active')
  AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000001);
