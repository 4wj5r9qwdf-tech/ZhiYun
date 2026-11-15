-- 人员业务职责：仅声明可担任哪些业务单据责任人，不授予菜单或数据权限。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_user_business_duty (
    user_id bigint NOT NULL REFERENCES sys_user(user_id) ON DELETE CASCADE,
    duty_code varchar(24) NOT NULL CHECK (duty_code IN ('SALES','FOLLOWER','PURCHASER','PLANNER','WAREHOUSE','QUALITY')),
    create_time timestamp NOT NULL DEFAULT now(),
    CONSTRAINT sys_user_business_duty_pk PRIMARY KEY(user_id,duty_code)
);
COMMENT ON TABLE sys_user_business_duty IS '人员业务职责，多选标识人员可担任的业务单据责任身份，不等同于岗位、系统角色或操作权限';
COMMENT ON COLUMN sys_user_business_duty.user_id IS '人员ID，关联 sys_user.user_id';
COMMENT ON COLUMN sys_user_business_duty.duty_code IS '业务职责代码：SALES业务员、FOLLOWER跟单员、PURCHASER采购员、PLANNER计划员、WAREHOUSE仓管员、QUALITY质检员';
COMMENT ON COLUMN sys_user_business_duty.create_time IS '职责关联建立时间';
CREATE INDEX IF NOT EXISTS ix_sys_user_business_duty_code ON sys_user_business_duty(duty_code,user_id);

-- 客户和订单已有责任人按既有业务关联迁移职责，避免升级后历史档案/草稿无法继续使用。
DO $$ BEGIN
    IF to_regclass('public.customer_master') IS NOT NULL THEN
        INSERT INTO sys_user_business_duty(user_id,duty_code)
        SELECT DISTINCT sales_representative_id,'SALES' FROM customer_master WHERE sales_representative_id IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
    IF to_regclass('public.biz_customer_order') IS NOT NULL THEN
        INSERT INTO sys_user_business_duty(user_id,duty_code)
        SELECT DISTINCT sales_id,'SALES' FROM biz_customer_order WHERE sales_id IS NOT NULL
        ON CONFLICT DO NOTHING;
        INSERT INTO sys_user_business_duty(user_id,duty_code)
        SELECT DISTINCT follower_id,'FOLLOWER' FROM biz_customer_order WHERE follower_id IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON sys_user_business_duty TO %I',app_role);
END $$;
COMMIT;
