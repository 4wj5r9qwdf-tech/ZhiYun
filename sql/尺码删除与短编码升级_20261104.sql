-- 尺码与尺码组：启用删除权限。历史编码保持不变；尺码自动编码按当前占用情况选择最小可用序号。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000404,'尺码删除',1762500000000000012,4,'#','','N','Y','F','0','0','system:size:delete','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size:delete');

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000414,'尺码组删除',1762500000000000013,4,'#','','N','Y','F','0','0','system:size-group:delete','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size-group:delete');

-- 现有具备编辑权限的角色同步获得对应删除权限，保持尺码基础资料的维护权限一致。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT edit_rm.role_id,delete_menu.menu_id
FROM sys_role_menu edit_rm
JOIN sys_menu edit_menu ON edit_menu.menu_id=edit_rm.menu_id
JOIN sys_menu delete_menu ON delete_menu.perms=CASE edit_menu.perms
 WHEN 'system:size:edit' THEN 'system:size:delete'
 WHEN 'system:size-group:edit' THEN 'system:size-group:delete'
END
WHERE edit_menu.perms IN ('system:size:edit','system:size-group:edit')
ON CONFLICT DO NOTHING;
