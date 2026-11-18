BEGIN;
CREATE TABLE IF NOT EXISTS sys_message_receipt (
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    read_time timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
COMMENT ON TABLE sys_message_receipt IS '站内通知按接收人员分别记录已读状态';
COMMENT ON COLUMN sys_message_receipt.message_id IS '站内消息主键，关联 sys_message.message_id；用于区分同一消息对不同人员的阅读状态';
COMMENT ON COLUMN sys_message_receipt.user_id IS '接收并已读消息的人员账号，关联 sys_user.user_id';
COMMENT ON COLUMN sys_message_receipt.read_time IS '该人员首次将消息标记为已读的时间，重复标记不覆盖首次时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON sys_message_receipt TO %I',app_role);
END $$;
COMMIT;
