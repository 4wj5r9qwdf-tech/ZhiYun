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
COMMENT ON COLUMN sys_approval_business_instance.business_key IS '不可预测的流程业务键，避免不同类型单据编号冲突';
COMMENT ON COLUMN sys_approval_business_instance.business_type IS '注册的业务对象类型';
COMMENT ON COLUMN sys_approval_business_instance.business_id IS '业务单据内部编号';
COMMENT ON COLUMN sys_approval_business_instance.business_revision IS '发起审批时的单据版本';
COMMENT ON COLUMN sys_approval_business_instance.flow_code IS '发起时确定的流程定义编码，配置变更不改历史实例';
COMMENT ON COLUMN sys_approval_business_instance.instance_id IS 'Warm-Flow 流程实例编号';
COMMENT ON COLUMN sys_approval_business_instance.state IS '关联状态：启动中、审批中、已通过、已驳回或已撤销';
COMMENT ON COLUMN sys_approval_business_instance.initiator_id IS '发起审批的登录人员';
COMMENT ON COLUMN sys_approval_business_instance.create_time IS '发起时间';
COMMENT ON COLUMN sys_approval_business_instance.update_time IS '关联状态更新时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON sys_approval_business_instance TO %I',app_role);
END $$;
COMMIT;
