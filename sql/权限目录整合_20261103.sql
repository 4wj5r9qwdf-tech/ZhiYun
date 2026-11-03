-- 权限树整合：保留既有ID与角色关联，停用未接入的框架菜单，不删除记录。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000001,0,'订单',10,'order','','N','Y','M','0','0','','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000002,1761600000000000002,'系统维护权限（底座接口）',10,'maintenance','','N','Y','M','0','0','','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000010,1761600000000000002,'标签管理',10,'tag','','N','Y','C','0','0','system:tag:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000011,1761600000000000002,'国家/地区',10,'country-region','','N','Y','C','0','0','system:country:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000012,1761600000000000002,'尺码管理',10,'size','','N','Y','C','0','0','system:size:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000013,1761600000000000002,'尺码组管理',10,'size-group','','N','Y','C','0','0','system:size-group:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000014,1762500000000000001,'客户档案',10,'customer','','N','Y','C','0','0','system:customer:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
UPDATE sys_menu SET parent_id=1762500000000000010 WHERE perms LIKE 'system:tag:%' AND menu_id<>1762500000000000010;
UPDATE sys_menu SET parent_id=1762500000000000011 WHERE perms LIKE 'system:country:%' AND menu_id<>1762500000000000011;
UPDATE sys_menu SET parent_id=1762500000000000012 WHERE perms LIKE 'system:size:%' AND menu_id<>1762500000000000012;
UPDATE sys_menu SET parent_id=1762500000000000013 WHERE perms LIKE 'system:size-group:%' AND menu_id<>1762500000000000013;
UPDATE sys_menu SET parent_id=1762500000000000014 WHERE perms LIKE 'system:customer:%' AND menu_id<>1762500000000000014;
UPDATE sys_menu SET parent_id=1762500000000000002 WHERE menu_id IN (1761400000000000102,1761400000000000105,1761400000000000106,1761400000000000108,1761400000000000118,1761400000000000133,1761400000000000123,1761400000000000109,1761400000000000113);
-- 重复的文件配置入口合并到文件配置管理节点，保留操作权限ID。
UPDATE sys_menu SET parent_id=1761400000000000133 WHERE parent_id=1761400000000000118 AND perms LIKE 'system:ossConfig:%';
UPDATE sys_menu SET menu_name='人员信息' WHERE perms='system:person:list';
UPDATE sys_menu SET menu_name='建筑档案' WHERE perms='system:building:list';
WITH RECURSIVE retired AS (SELECT menu_id FROM sys_menu WHERE menu_id IN (1761400000000000001,1761400000000000002,1761400000000000003,1761400000000000004,1761400000000000005,1761400000000000006,1761400000000011616,1761400000000011618) UNION ALL SELECT m.menu_id FROM sys_menu m JOIN retired p ON m.parent_id=p.menu_id) UPDATE sys_menu SET status='1',visible='1' WHERE menu_id IN (SELECT menu_id FROM retired);
-- 已有授权仍可到达合并后的父目录；不授予额外按钮权限。
WITH RECURSIVE ancestors AS (
 SELECT rm.role_id,m.parent_id AS menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.status='0' AND m.parent_id<>0
 UNION
 SELECT a.role_id,m.parent_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE m.status='0' AND m.parent_id<>0
)
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT a.role_id,a.menu_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id AND m.status='0'
WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu x WHERE x.role_id=a.role_id AND x.menu_id=a.menu_id);

UPDATE sys_menu SET status='0',visible='0' WHERE menu_id=1761400000000000001; UPDATE sys_menu SET parent_id=1761400000000000001 WHERE parent_id=1761600000000000002; INSERT INTO sys_role_menu(role_id,menu_id) SELECT role_id,1761400000000000001 FROM sys_role_menu WHERE menu_id=1761600000000000002 ON CONFLICT DO NOTHING; UPDATE sys_menu SET status='1',visible='1' WHERE menu_id=1761600000000000002;

-- 系统管理直接承载启用的维护功能。
UPDATE sys_menu SET parent_id=1761400000000000001 WHERE parent_id=1762500000000000002;
UPDATE sys_menu SET status='1',visible='1' WHERE menu_id=1762500000000000002;
