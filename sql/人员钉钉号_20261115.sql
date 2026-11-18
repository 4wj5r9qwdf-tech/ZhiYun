-- 人员档案登记钉钉号，已有人员默认留空。
ALTER TABLE sys_person_ext ADD COLUMN IF NOT EXISTS dingtalk_account varchar(128);
COMMENT ON COLUMN sys_person_ext.dingtalk_account IS '人员登记的钉钉号；仅作账号信息，钉钉身份绑定和消息推送待接入';
