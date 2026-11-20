-- 文件用途：为“审批业务实例关联_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“审批业务实例关联_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 业务单据与审批实例的稳定关联；流程节点、待办和审批意见仍由 Warm-Flow 保存。
-- 此关联表只保存业务对象、发起修订号和流程实例编号；审批节点与意见仍由 Warm-Flow 作为唯一事实来源保存。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_approval_business_instance (
    business_key varchar(36) PRIMARY KEY,
    business_type varchar(48) NOT NULL,
    business_id bigint NOT NULL,
    business_revision integer NOT NULL,
    flow_code varchar(40) NOT NULL,
    instance_id bigint UNIQUE,
    state varchar(16) NOT NULL CHECK (state IN ('starting','pending','approved','rejected','cancelled')),
    initiator_id bigint NOT NULL,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_time timestamptz NOT NULL DEFAULT now()
);
-- 部分唯一索引只限制未结束实例，允许同一业务单据在旧流程结束后重新发起。
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_business_active ON sys_approval_business_instance(business_type,business_id) WHERE state IN ('starting','pending');
COMMENT ON TABLE sys_approval_business_instance IS '业务对象与 Warm-Flow 审批实例的关联及发起快照，不重复存储流程节点与待办';
COMMENT ON COLUMN sys_approval_business_instance.business_key IS '业务审批关联键，使用随机值避免不同单据类型的业务主键冲突及可枚举猜测';
COMMENT ON COLUMN sys_approval_business_instance.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；当前仅客户订单支持实际发起流程，其余须完成业务接入后方可启用';
COMMENT ON COLUMN sys_approval_business_instance.business_id IS '来源业务单据主键；目标表由 business_type 决定，不能作为跨表外键';
COMMENT ON COLUMN sys_approval_business_instance.business_revision IS '发起审批时锁定的业务单据修订号，用于防止审批期间以旧版本覆盖新数据';
COMMENT ON COLUMN sys_approval_business_instance.flow_code IS '发起时选定的 Warm-Flow 流程定义编码；后续配置变更不影响此历史实例';
COMMENT ON COLUMN sys_approval_business_instance.instance_id IS 'Warm-Flow 审批引擎流程实例编号；实例节点、审批意见和待办由引擎保存';
COMMENT ON COLUMN sys_approval_business_instance.state IS '关联状态：starting 启动中、pending 审批中、approved 已通过、rejected 已驳回、cancelled 已撤销';
COMMENT ON COLUMN sys_approval_business_instance.initiator_id IS '发起审批的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN sys_approval_business_instance.create_time IS '本业务单据首次关联审批实例的发起时间';
COMMENT ON COLUMN sys_approval_business_instance.update_time IS '本关联记录最近一次同步审批实例状态的时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON sys_approval_business_instance TO %I',app_role);
END $$;
COMMIT;
