-- 客户删除权限，单独授予角色。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000204,'客户删除',5,113,'#','','N','Y','F','0','0','system:customer:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:remove');
