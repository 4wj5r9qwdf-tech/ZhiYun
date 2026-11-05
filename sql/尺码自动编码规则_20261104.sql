-- 尺码编码规则使用系统参数存储；无表结构变更。
INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码自动编码规则', 'system.code.size',
       '{"enabled":true,"prefix":"SZ","digits":4}', 'Y', now(),
       '尺码自动编码开关、编码前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.size');

INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码组自动编码规则', 'system.code.sizeGroup',
       '{"enabled":true,"prefix":"SG","digits":4}', 'Y', now(),
       '尺码组自动编码开关、编号前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.sizeGroup');
