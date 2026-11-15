-- 业务对象与已发布的 Warm-Flow 流程定义绑定；流程结构和待办仍由引擎维护。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_approval_binding (
    business_type varchar(48) PRIMARY KEY,
    flow_code varchar(40) NOT NULL,
    update_by bigint NOT NULL,
    update_time timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_approval_binding IS '业务审批对象与 Warm-Flow 流程编码的配置绑定，不保存流程节点或待办';
COMMENT ON COLUMN sys_approval_binding.business_type IS '业务对象类型，例如客户订单、物料需求计划、生产任务';
COMMENT ON COLUMN sys_approval_binding.flow_code IS '已发布且启用的 Warm-Flow 流程定义编码';
COMMENT ON COLUMN sys_approval_binding.update_by IS '最后修改绑定的人员';
COMMENT ON COLUMN sys_approval_binding.update_time IS '最后修改绑定的时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON sys_approval_binding TO %I',app_role);
END $$;
COMMIT;
