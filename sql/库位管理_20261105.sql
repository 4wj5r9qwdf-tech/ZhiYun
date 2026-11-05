-- 库位管理：区域、货架、货位三级位置档案及仓库入库定位深度设置。
BEGIN;
ALTER TABLE inv_warehouse ADD COLUMN IF NOT EXISTS inbound_location_level smallint NOT NULL DEFAULT 2 CHECK (inbound_location_level BETWEEN 0 AND 3);
COMMENT ON COLUMN inv_warehouse.inbound_location_level IS '入库定位最小深度：0=仓库，1=区域，2=货架，3=货位；默认货架级。';
CREATE TABLE IF NOT EXISTS inv_warehouse_location (
 location_id bigserial PRIMARY KEY,
 warehouse_id bigint NOT NULL REFERENCES inv_warehouse(warehouse_id),
 parent_location_id bigint REFERENCES inv_warehouse_location(location_id),
 location_code varchar(80) NOT NULL,
 location_name varchar(200) NOT NULL,
 location_level smallint NOT NULL CHECK (location_level BETWEEN 1 AND 3),
 agv_reachable boolean NOT NULL DEFAULT true,
 capacity_limit numeric(18,4),
 capacity_unit varchar(30),
 location_status varchar(20) NOT NULL DEFAULT 'active' CHECK (location_status IN ('active','disabled','maintenance')),
 external_location_code varchar(120),
 remark varchar(1000),
 del_flag char(1) NOT NULL DEFAULT '0' CHECK (del_flag IN ('0','1')),
 create_by bigint, create_time timestamp NOT NULL DEFAULT now(), update_by bigint, update_time timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_warehouse_location_code ON inv_warehouse_location(warehouse_id,lower(location_code)) WHERE del_flag='0';
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_warehouse_location_external ON inv_warehouse_location(warehouse_id,external_location_code) WHERE del_flag='0' AND external_location_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inv_warehouse_location_parent ON inv_warehouse_location(warehouse_id,parent_location_id,location_level) WHERE del_flag='0';
COMMENT ON TABLE inv_warehouse_location IS '仓库内区域、货架、货位三级位置档案；AGV调度暂不在本表实现。';
COMMENT ON COLUMN inv_warehouse_location.location_id IS '库位稳定主键，供库存单据引用。';
COMMENT ON COLUMN inv_warehouse_location.warehouse_id IS '所属仓库ID。';
COMMENT ON COLUMN inv_warehouse_location.parent_location_id IS '直接父级位置ID；区域为空，货架挂区域，货位挂货架。';
COMMENT ON COLUMN inv_warehouse_location.location_code IS '仓库内唯一位置编码，作为稳定人工识别码。';
COMMENT ON COLUMN inv_warehouse_location.location_name IS '位置名称。';
COMMENT ON COLUMN inv_warehouse_location.location_level IS '位置层级：1=区域，2=货架，3=货位。';
COMMENT ON COLUMN inv_warehouse_location.agv_reachable IS '位置是否允许AGV到达的预留标记。';
COMMENT ON COLUMN inv_warehouse_location.capacity_limit IS '位置容量上限预留值；本期不计算库存占用。';
COMMENT ON COLUMN inv_warehouse_location.capacity_unit IS '位置容量单位预留值。';
COMMENT ON COLUMN inv_warehouse_location.location_status IS '位置档案状态：active=可用，disabled=停用，maintenance=维护中。';
COMMENT ON COLUMN inv_warehouse_location.external_location_code IS '外部仓储设备或AGV系统的位置映射码预留值。';
COMMENT ON COLUMN inv_warehouse_location.remark IS '位置补充说明。';
COMMENT ON COLUMN inv_warehouse_location.del_flag IS '逻辑删除标记：0=正常，1=已删除。';
COMMENT ON COLUMN inv_warehouse_location.create_by IS '创建人用户ID。';
COMMENT ON COLUMN inv_warehouse_location.create_time IS '创建时间。';
COMMENT ON COLUMN inv_warehouse_location.update_by IS '最后修改人用户ID。';
COMMENT ON COLUMN inv_warehouse_location.update_time IS '最后修改时间。';
DO $$ DECLARE r text; BEGIN SELECT pg_get_userbyid(relowner) INTO r FROM pg_class WHERE oid='public.inv_warehouse'::regclass; EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON inv_warehouse_location TO %I',r); EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE inv_warehouse_location_location_id_seq TO %I',r); END $$;

-- 库位管理接口权限；前端功能入口复用库存模块的本地功能卡片目录，不额外创建左侧菜单节点。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000111,'库位查询',5,150,'#','','N','Y','F','0','0','inventory:location:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000112,'库位详情',5,151,'#','','N','Y','F','0','0','inventory:location:query','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:query');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000113,'库位新增',5,152,'#','','N','Y','F','0','0','inventory:location:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000114,'库位编辑与状态',5,153,'#','','N','Y','F','0','0','inventory:location:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000115,'库位删除',5,154,'#','','N','Y','F','0','0','inventory:location:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:remove');

COMMIT;
