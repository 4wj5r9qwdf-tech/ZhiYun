-- 在审批模块注册个人抄送入口，保留此前可查看抄送的角色访问。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1763200000000000010,1763200000000000000,'我的抄送',45,'copies','approval/copies','N','Y','C','0','0','approval:copy:list','#',now())
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT role_id,1763200000000000010 FROM sys_role_menu WHERE menu_id=1761400000000011628
ON CONFLICT DO NOTHING;
