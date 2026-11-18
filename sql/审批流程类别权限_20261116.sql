-- 将框架原有流程分类菜单挂入审批模块，保留原有权限编号和角色授权。
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程类别',path='categories',component='approval/categories',status='0',visible='0'
WHERE menu_id=1761400000000011622 AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000000);
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT role_id,1763200000000000000 FROM sys_role_menu WHERE menu_id=1761400000000011622
ON CONFLICT DO NOTHING;
