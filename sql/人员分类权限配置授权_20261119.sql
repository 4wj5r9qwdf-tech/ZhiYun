-- 人员分类权限设置作为独立按钮权限；仅登记权限，不自动授予普通角色。
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:person:list' AND menu_type='C') THEN
        RAISE EXCEPTION '未找到人员信息菜单，无法挂载分类权限设置';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1769900000000000087 AND perms IS DISTINCT FROM 'system:person:categoryScope') THEN
        RAISE EXCEPTION '人员分类权限菜单编号已被占用';
    END IF;
END $$;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1769900000000000087,parent.menu_id,'分类权限设置',90,'#','','N','Y','F','0','0','system:person:categoryScope','#',now()
FROM sys_menu parent WHERE parent.perms='system:person:list' AND parent.menu_type='C'
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:person:categoryScope')
LIMIT 1;
COMMIT;
