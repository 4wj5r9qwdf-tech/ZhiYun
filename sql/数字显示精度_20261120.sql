-- 显示位数供只读视图使用；不改变单据原值和结算金额。
INSERT INTO sys_config(config_id,config_name,config_key,config_value,config_type,create_time,remark)
SELECT COALESCE(MAX(config_id),0)+1,'数字显示精度','system.display.decimals',
       '{"bomUsage":4,"purchasePrice":4,"costPrice":6,"percentage":2}','Y',now(),'BOM单耗、采购单价、成本单价和百分比显示位数，各为0至8'
FROM sys_config HAVING NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.display.decimals');
