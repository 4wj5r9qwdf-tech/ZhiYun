-- 仅用于标签归属表早期由管理员建立、应用角色尚无权限的已有库。
-- 由 PostgreSQL 管理员运行；应用角色从 sys_tag 表的所有者获取，不硬编码账号。
BEGIN;
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT, INSERT, UPDATE ON public.sys_tag_module TO %I', app_role);
END $$;
COMMIT;
