-- 文件用途：为“人员业务职责_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“人员业务职责_20261114”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
