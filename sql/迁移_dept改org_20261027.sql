-- =====================================================================
-- 织云 ERP 迁移：全局 dept→org 改名（2026-10-27 线上 ad-hoc 执行的改名，补成可重跑脚本）
-- 背景：用户拍板全改，当时直接改线上库（backups/*.dump 可回退），仓库一直缺这份迁移。
--       不补的话，按 框架SQL→主建表.sql 建的【新库】没有 org 命名，而代码只认 org，新库起不来。
-- 内容：表改名 sys_dept→sys_org / sys_role_dept→sys_role_org / sys_dept_manage→sys_org_manage
--       列改名 dept_*/create_dept → org_*/create_org（22 张表 create_dept→create_org（21 张框架表 + sys_org 随表改名））
--       补线上 ad-hoc 扩展列：sys_org.building_id / sys_org.location / sj_namespace.unique_id
-- 幂等：每步带存在性判断，改前/改后执行都安全（已改名的步骤自动跳过）
-- 顺序：框架 SQL 之后、主建表.sql 与 组织树数据迁移 之前
-- 双场景：守卫用 current_schema()，生产重跑（public）与临时 schema 新库验证都适用
-- 注意：索引/约束名未改（线上保留旧名 idx_sys_dept_parent_id 等，功能无影响，不改名）
-- =====================================================================

BEGIN;

-- 表改名 sys_dept → sys_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_dept')
     AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_org') THEN
    ALTER TABLE sys_dept RENAME TO sys_org;
  END IF;
END $$;

-- 表改名 sys_role_dept → sys_role_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_role_dept')
     AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_role_org') THEN
    ALTER TABLE sys_role_dept RENAME TO sys_role_org;
  END IF;
END $$;

-- 表改名 sys_dept_manage → sys_org_manage（线上改名前由 ad-hoc 建的管辖表；新库无此表，no-op，之后由 主建表.sql 第11节创建）
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_dept_manage')
     AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_org_manage') THEN
    ALTER TABLE sys_dept_manage RENAME TO sys_org_manage;
  END IF;
END $$;

-- ===== 列改名（每个带独立判断） =====
-- sys_org: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='org_id') THEN
    ALTER TABLE sys_org RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- sys_org: dept_name → org_name
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='dept_name')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='org_name') THEN
    ALTER TABLE sys_org RENAME COLUMN dept_name TO org_name;
  END IF;
END $$;

-- sys_org: dept_category → org_category
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='dept_category')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='org_category') THEN
    ALTER TABLE sys_org RENAME COLUMN dept_category TO org_category;
  END IF;
END $$;

-- sys_org: create_dept → create_org（sys_dept 改名而来，审计列随表）
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_org' AND c.column_name='create_org') THEN
    ALTER TABLE sys_org RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_role_org: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role_org' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role_org' AND c.column_name='org_id') THEN
    ALTER TABLE sys_role_org RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- sys_role: dept_check_strictly → org_check_strictly
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='dept_check_strictly')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='org_check_strictly') THEN
    ALTER TABLE sys_role RENAME COLUMN dept_check_strictly TO org_check_strictly;
  END IF;
END $$;

-- sys_role: data_scope_dept_ids → data_scope_org_ids
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='data_scope_dept_ids')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='data_scope_org_ids') THEN
    ALTER TABLE sys_role RENAME COLUMN data_scope_dept_ids TO data_scope_org_ids;
  END IF;
END $$;

-- sys_oper_log: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oper_log' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oper_log' AND c.column_name='org_id') THEN
    ALTER TABLE sys_oper_log RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- sys_oper_log: dept_name → org_name
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oper_log' AND c.column_name='dept_name')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oper_log' AND c.column_name='org_name') THEN
    ALTER TABLE sys_oper_log RENAME COLUMN dept_name TO org_name;
  END IF;
END $$;

-- sys_post: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_post' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_post' AND c.column_name='org_id') THEN
    ALTER TABLE sys_post RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- sys_user: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_user' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_user' AND c.column_name='org_id') THEN
    ALTER TABLE sys_user RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- test_demo: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_demo' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_demo' AND c.column_name='org_id') THEN
    ALTER TABLE test_demo RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- test_tree: dept_id → org_id
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_tree' AND c.column_name='dept_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_tree' AND c.column_name='org_id') THEN
    ALTER TABLE test_tree RENAME COLUMN dept_id TO org_id;
  END IF;
END $$;

-- flow_category: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_category' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_category' AND c.column_name='create_org') THEN
    ALTER TABLE flow_category RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- flow_instance_biz_ext: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_instance_biz_ext' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_instance_biz_ext' AND c.column_name='create_org') THEN
    ALTER TABLE flow_instance_biz_ext RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- flow_spel: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_spel' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='flow_spel' AND c.column_name='create_org') THEN
    ALTER TABLE flow_spel RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- gen_table: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='gen_table' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='gen_table' AND c.column_name='create_org') THEN
    ALTER TABLE gen_table RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- gen_table_column: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='gen_table_column' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='gen_table_column' AND c.column_name='create_org') THEN
    ALTER TABLE gen_table_column RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_client: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_client' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_client' AND c.column_name='create_org') THEN
    ALTER TABLE sys_client RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_config: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_config' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_config' AND c.column_name='create_org') THEN
    ALTER TABLE sys_config RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_dict_data: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_dict_data' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_dict_data' AND c.column_name='create_org') THEN
    ALTER TABLE sys_dict_data RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_dict_type: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_dict_type' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_dict_type' AND c.column_name='create_org') THEN
    ALTER TABLE sys_dict_type RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_menu: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_menu' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_menu' AND c.column_name='create_org') THEN
    ALTER TABLE sys_menu RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_message: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_message' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_message' AND c.column_name='create_org') THEN
    ALTER TABLE sys_message RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_notice: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_notice' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_notice' AND c.column_name='create_org') THEN
    ALTER TABLE sys_notice RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_oss: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oss' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oss' AND c.column_name='create_org') THEN
    ALTER TABLE sys_oss RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_oss_config: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oss_config' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_oss_config' AND c.column_name='create_org') THEN
    ALTER TABLE sys_oss_config RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_post: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_post' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_post' AND c.column_name='create_org') THEN
    ALTER TABLE sys_post RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_role: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_role' AND c.column_name='create_org') THEN
    ALTER TABLE sys_role RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_social: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_social' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_social' AND c.column_name='create_org') THEN
    ALTER TABLE sys_social RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- sys_user: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_user' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='sys_user' AND c.column_name='create_org') THEN
    ALTER TABLE sys_user RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- test_demo: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_demo' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_demo' AND c.column_name='create_org') THEN
    ALTER TABLE test_demo RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- test_leave: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_leave' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_leave' AND c.column_name='create_org') THEN
    ALTER TABLE test_leave RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- test_tree: create_dept → create_org
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_tree' AND c.column_name='create_dept')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='test_tree' AND c.column_name='create_org') THEN
    ALTER TABLE test_tree RENAME COLUMN create_dept TO create_org;
  END IF;
END $$;

-- ===== 线上 ad-hoc 扩展列（新库补齐；已存在则跳过） =====
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_org') THEN
    ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS building_id bigint;
    COMMENT ON COLUMN public.sys_org.building_id IS '所在建筑(物理资产)=sys_building主键plant_id；只车间节点填,下面区域/组继承';
  END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sys_org') THEN
    ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS location varchar(200);
    COMMENT ON COLUMN public.sys_org.location IS '位置路径(文本): 楼/楼层/车间/房间 逐级拼接, 选到哪层填到哪层(如 一号厂房/裁剪车间 或 办公楼/3层/302室)';
  END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace=current_schema()::regnamespace AND c.relname='sj_namespace') THEN
    ALTER TABLE sj_namespace ADD COLUMN IF NOT EXISTS unique_id varchar(64);
    COMMENT ON COLUMN public.sj_namespace.unique_id IS '唯一id';
  END IF;
END $$;

COMMIT;

-- 自查：
--   \d sys_org / sys_role_org / sys_org_manage   -- 新表名就位
--   SELECT count(*) FROM information_schema.columns WHERE column_name LIKE '%%dept%%';  -- 应 0
