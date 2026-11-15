BEGIN;
CREATE TABLE IF NOT EXISTS sys_message_receipt (
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    read_time timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
COMMENT ON TABLE sys_message_receipt IS '站内通知按接收人员分别记录已读状态';
COMMENT ON COLUMN sys_message_receipt.message_id IS '站内消息编号';
COMMENT ON COLUMN sys_message_receipt.user_id IS '已读人员账号编号';
COMMENT ON COLUMN sys_message_receipt.read_time IS '该人员首次阅读时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON sys_message_receipt TO %I',app_role);
END $$;
COMMIT;
