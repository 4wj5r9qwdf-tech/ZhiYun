-- 业务单据与审批实例的稳定关联；流程节点、待办和审批意见仍由 Warm-Flow 保存。
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
