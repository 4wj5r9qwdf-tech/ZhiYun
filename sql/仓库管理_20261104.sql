-- 仓库管理：仓库档案。开发验收前使用本模块 SQL；功能验收后再按规范并入 sql/主建表.sql。
BEGIN;

CREATE TABLE IF NOT EXISTS inv_warehouse (
    warehouse_id       bigserial PRIMARY KEY,
    warehouse_code     varchar(50) NOT NULL,
    warehouse_name     varchar(200) NOT NULL,
    building_id        bigint REFERENCES sys_building(plant_id),
    floor_name         varchar(50),
    area_name          varchar(100),
    manager_user_id    bigint REFERENCES sys_user(user_id),
    status             char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
    remark             varchar(1000),
    del_flag           char(1) NOT NULL DEFAULT '0' CHECK (del_flag IN ('0','1')),
    create_by          bigint,
    create_time        timestamp NOT NULL DEFAULT now(),
    update_by          bigint,
    update_time        timestamp
);

-- 仓库归属建筑即可；车间与厂房建筑重复，移除首版重复列及其索引。
DROP INDEX IF EXISTS idx_inv_warehouse_org;
ALTER TABLE inv_warehouse DROP COLUMN IF EXISTS workshop_org_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_warehouse_code_active
    ON inv_warehouse (lower(warehouse_code)) WHERE del_flag='0';
CREATE INDEX IF NOT EXISTS idx_inv_warehouse_building ON inv_warehouse (building_id) WHERE del_flag='0';

-- 仓库类型采用自由标签；按用户已确认的仓储规划提供六个常用初始提示，后续仍可自由新增。
INSERT INTO sys_tag_module(module_key,module_name,create_time)
VALUES('warehouse_master','仓库管理',now())
ON CONFLICT(module_key) DO UPDATE SET module_name=EXCLUDED.module_name,update_time=now();
INSERT INTO sys_tag(module_key,tag_name,is_common,status,remark)
SELECT 'warehouse_master',v.tag_name,'1','0','仓库类型常用标签（可继续添加自定义类型）'
FROM (VALUES ('成品仓'),('面料仓'),('辅料仓'),('消耗品仓'),('设备仓'),('样衣仓')) AS v(tag_name)
WHERE NOT EXISTS (SELECT 1 FROM sys_tag t WHERE t.module_key='warehouse_master' AND lower(t.tag_name)=lower(v.tag_name));

COMMENT ON TABLE inv_warehouse IS '库存模块仓库基础档案，记录仓库编码、名称、所属建筑及楼层区域、负责人和状态；仓库类型通过通用标签关联，仓库不是组织节点或建筑档案。';
COMMENT ON COLUMN inv_warehouse.warehouse_id IS '仓库档案稳定主键，供库存及后续出入库业务通过ID关联，不作为对外展示编码。';
COMMENT ON COLUMN inv_warehouse.warehouse_code IS '仓库业务编码；用于人工识别，所有未逻辑删除的仓库记录中必须唯一（不区分大小写）。';
COMMENT ON COLUMN inv_warehouse.warehouse_name IS '仓库业务名称，例如二号车间主仓或一车间样衣仓。';
COMMENT ON COLUMN inv_warehouse.building_id IS '仓库所属建筑档案ID，关联 sys_building.plant_id；建筑档案的 floor_info 提供总楼层数，本字段选择具体所属厂房或建筑。';
COMMENT ON COLUMN inv_warehouse.floor_name IS '仓库所在的具体楼层；选项根据关联建筑档案的 floor_info 楼层数生成，未设置时为空。';
COMMENT ON COLUMN inv_warehouse.area_name IS '仓库在所属建筑或楼层内的区域、房间或位置说明；未设置时为空。';
COMMENT ON COLUMN inv_warehouse.manager_user_id IS '仓库负责人用户ID，关联 sys_user.user_id；未指定负责人时为空。';
COMMENT ON COLUMN inv_warehouse.status IS '仓库使用状态：0=启用（可供新业务选择），1=停用（不得供新业务选择）。';
COMMENT ON COLUMN inv_warehouse.remark IS '仓库补充说明，记录仓库使用或管理方面的备注。';
COMMENT ON COLUMN inv_warehouse.del_flag IS '仓库逻辑删除标记：0=正常保留，1=已删除；删除记录保留用于历史追溯。';
COMMENT ON COLUMN inv_warehouse.create_by IS '创建本仓库档案的用户ID，逻辑关联 sys_user.user_id，业务来源为当前登录用户。';
COMMENT ON COLUMN inv_warehouse.create_time IS '仓库档案首次创建时间，由数据库默认记录。';
COMMENT ON COLUMN inv_warehouse.update_by IS '最后修改本仓库档案的用户ID，逻辑关联 sys_user.user_id；尚未修改时为空。';
COMMENT ON COLUMN inv_warehouse.update_time IS '仓库档案最后修改时间；尚未修改时为空。';
COMMENT ON INDEX uq_inv_warehouse_code_active IS '限制未逻辑删除的仓库档案编码不区分大小写重复。';
COMMENT ON INDEX idx_inv_warehouse_building IS '按所在建筑查询正常仓库档案。';
COMMENT ON INDEX inv_warehouse_pkey IS '仓库档案主键唯一索引，保证 warehouse_id 稳定唯一。';
COMMENT ON SEQUENCE inv_warehouse_warehouse_id_seq IS '生成仓库档案 warehouse_id 主键值的自增序列。';

-- 本地/新环境可能由数据库管理员执行DDL；将表和自增序列权限授给系统业务表所有者对应的应用角色。
DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO app_role
    FROM pg_class WHERE oid='public.sys_tag'::regclass;
    IF app_role IS NULL THEN
        RAISE EXCEPTION '无法从 public.sys_tag 识别应用数据库角色';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.inv_warehouse TO %I', app_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE public.inv_warehouse_warehouse_id_seq TO %I', app_role);
END $$;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000101,'仓库查询',5,140,'#','','N','Y','F','0','0','inventory:warehouse:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:warehouse:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000102,'仓库新增',5,141,'#','','N','Y','F','0','0','inventory:warehouse:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:warehouse:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000105,'仓库详情',5,140,'#','','N','Y','F','0','0','inventory:warehouse:query','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:warehouse:query');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000103,'仓库编辑与启停',5,142,'#','','N','Y','F','0','0','inventory:warehouse:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:warehouse:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000104,'仓库删除',5,143,'#','','N','Y','F','0','0','inventory:warehouse:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:warehouse:remove');

COMMIT;
