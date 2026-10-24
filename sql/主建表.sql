-- ============================================================
-- 织云 ERP 主建表脚本（唯一权威建表入口）
-- 规则：开发期可分模块零散写，功能验证通过后必须合并进本文件
--       （见 智能体交接说明.md 四、协作流程第 5 步）
-- 依赖：RuoYi-Vue-Plus 基础表已导入（script/sql/postgres/*.sql）
--       本文件只建"织云自建地基表"，不重复建 RuoYi 自带表
-- 执行：psql -U zhizhun -d zhizhun_erp -f sql/主建表.sql
-- 更新：2026-10-24
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. 岗位 → 默认角色 关联表（权限地基 3.2）
--    RuoYi 自带 sys_post(岗位) + sys_role(角色) + sys_user_post(人岗)
--    本表补"岗位带出默认角色"：一个岗位可挂多个默认角色
--    例：生产主管 → 生产管理 + 排产操作
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_post_role (
    post_id     bigint       NOT NULL,                -- 岗位ID（→ sys_post.post_id）
    role_id     bigint       NOT NULL,                -- 角色ID（→ sys_role.role_id）
    sort        integer      NOT NULL DEFAULT 0,       -- 排序（同岗位多角色时带出顺序）
    create_dept bigint,                                -- 创建部门
    create_by   bigint,                                -- 创建者
    create_time timestamp    DEFAULT CURRENT_TIMESTAMP,
    update_by   bigint,                                -- 更新者
    update_time timestamp,                             -- 更新时间
    remark      varchar(500),                          -- 备注
    CONSTRAINT sys_post_role_pk PRIMARY KEY (post_id, role_id),
    CONSTRAINT sys_post_role_post_fk FOREIGN KEY (post_id)
        REFERENCES sys_post (post_id) ON DELETE CASCADE,
    CONSTRAINT sys_post_role_role_fk FOREIGN KEY (role_id)
        REFERENCES sys_role (role_id) ON DELETE CASCADE
);

COMMENT ON TABLE  sys_post_role                IS '岗位默认角色关联表（权限地基3.2：岗位带出默认角色）';
COMMENT ON COLUMN sys_post_role.post_id        IS '岗位ID';
COMMENT ON COLUMN sys_post_role.role_id        IS '角色ID';
COMMENT ON COLUMN sys_post_role.sort           IS '同岗位多角色带出顺序';

CREATE INDEX IF NOT EXISTS idx_sys_post_role_role_id ON sys_post_role (role_id);

-- ------------------------------------------------------------
-- 2. sys_role 扩展：数据范围支持"指定节点集合"（权限地基 2.2 第 3 种）
--    RuoYi 内置 data_scope 取值：1全数据 2本部门 3本部门及以下 4仅本人 5自定义
--    织云扩展第 6 种：6指定节点集合（跨部门协作，如总监/计划部跨车间排产）
--    data_scope=6 时，本列存逗号分隔的 sys_dept.dept_id 集合
-- ------------------------------------------------------------
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope_dept_ids varchar(2000) DEFAULT '';
COMMENT ON COLUMN sys_role.data_scope_dept_ids IS '数据范围=6(指定节点集合)时的部门ID集合，逗号分隔，→ sys_dept.dept_id';

-- ------------------------------------------------------------
-- 3. 内置敏感数据权限点（权限地基 3.4.2）
--    系统只内置这一个权限点，勾了=看明文，没勾=后端脱敏 ***
--    其他业务角色/权限点由超管在管理端运行时配置，系统不预置
--    sys_menu：RuoYi 权限码表（菜单/按钮/接口），type=M菜单 F按钮 C列权限
-- ------------------------------------------------------------
-- 3.1 归属"系统管理"一级菜单下（若不存在则建）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1762300000000000001, '敏感人事数据', 5, 99, '', NULL, '', 1, 0, 'F', '1', '0', 'system:user:sensitive', '#', 1761000000000000003, 1761100000000000001, now(), NULL, NULL, '权限地基3.4：勾选=可看身份证/手机号/银行卡/工资明文，未勾=后端脱敏(权限点不参与并集)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:user:sensitive');

-- ------------------------------------------------------------
-- 4. 超管账号初始权限（权限地基 8.1）
--    RuoYi 自带 admin 账号（user_id=1）默认绑超管角色
--    本处仅记录约定：除超管外系统不预置任何账号/角色
--    （RuoYi 默认还带 test/test1 演示账号，上线前由超管删除）
-- ------------------------------------------------------------
-- 说明：不在此处 INSERT 数据行，超管角色-权限绑定由超管在管理端首次登录后配置
--       （符合"不预置业务角色"原则，见 权限地基 3.2 / 8.1）

COMMIT;

-- ============================================================
-- 执行后自查：
--   1. \d sys_post_role          -- 表已建
--   2. SELECT perms FROM sys_menu WHERE perms='system:user:sensitive';  -- 1 行
--   3. SELECT column_name FROM information_schema.columns
--      WHERE table_name='sys_role' AND column_name='data_scope_dept_ids';  -- 1 行
-- ============================================================
