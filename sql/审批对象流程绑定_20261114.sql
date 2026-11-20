-- 文件用途：为“审批对象流程绑定_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“审批对象流程绑定_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 业务对象与已发布的 Warm-Flow 流程定义绑定；流程结构和待办仍由引擎维护。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_approval_binding (
    business_type varchar(48) PRIMARY KEY,
    flow_code varchar(40) NOT NULL,
    update_by bigint NOT NULL,
    update_time timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_approval_binding IS '业务审批对象与 Warm-Flow 流程编码的配置绑定，不保存流程节点或待办';
COMMENT ON COLUMN sys_approval_binding.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；当前仅客户订单支持实际发起流程，其余须完成业务接入后方可启用';
COMMENT ON COLUMN sys_approval_binding.flow_code IS '绑定的流程定义编码，关联 flow_definition.flow_code；仅允许已发布且启用的流程，节点由 Warm-Flow 维护';
COMMENT ON COLUMN sys_approval_binding.update_by IS '最后修改绑定的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN sys_approval_binding.update_time IS '最后一次新增或修改审批对象流程绑定的时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON sys_approval_binding TO %I',app_role);
END $$;
COMMIT;
