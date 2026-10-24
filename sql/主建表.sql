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

-- ------------------------------------------------------------
-- 5. 公司档案表（前端规范 A.1：登录页/首页显示公司名+logo）
--    两个公司实体，名称可编辑、各配 logo；可增（加第三公司不改表）
--    logo_oss_id → sys_oss.oss_id（走 MinIO），暂可空（logo 后续上传）
--    is_default=是 的公司显示在登录页/首页（A.1 展示层）
--    超管账号不进数据库（走配置文件，见 .env 的 SUPER_ADMIN_*）
--    普通账号进 sys_user（工号6位数字/手机号+密码，BCrypt）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_company (
    company_id    bigint        NOT NULL,
    company_name  varchar(100)  NOT NULL,
    logo_oss_id   bigint,
    is_default    char(1)       NOT NULL DEFAULT '0',    -- 0否 1是（登录页/首页显示这家公司）
    theme         varchar(50)   DEFAULT '',              -- A.5 主题（工业蓝/深色大屏/自定义色）
    status        char(1)       NOT NULL DEFAULT '0',    -- 0正常 1停用
    del_flag      char(1)       DEFAULT '0',
    create_dept   bigint,
    create_by     bigint,
    create_time   timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_by     bigint,
    update_time   timestamp,
    remark        varchar(500),
    CONSTRAINT sys_company_pk PRIMARY KEY (company_id),
    CONSTRAINT sys_company_logo_fk FOREIGN KEY (logo_oss_id)
        REFERENCES sys_oss (oss_id) ON DELETE SET NULL
);

COMMENT ON TABLE  sys_company            IS '公司档案表（前端规范A.1：登录页/首页公司名+logo，展示层）';
COMMENT ON COLUMN sys_company.company_name IS '公司名（管理端可编辑，不写死代码）';
COMMENT ON COLUMN sys_company.logo_oss_id  IS 'logo文件（→ sys_oss.oss_id，走MinIO，暂可空）';
COMMENT ON COLUMN sys_company.is_default   IS '是否默认展示公司（是=登录页/首页显示）';
COMMENT ON COLUMN sys_company.theme        IS '主题（A.5多主题：工业蓝/深色大屏/自定义色）';

-- 默认只允许一家默认展示公司（部分唯一索引，status=0 且 del_flag=0 生效）
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_company_default
    ON sys_company (is_default) WHERE is_default = '1' AND status = '0' AND del_flag = '0';

-- 预置两家公司（河北佰信达=默认展示；依时华备用，logo 后续上传）
INSERT INTO sys_company (company_id, company_name, is_default, status, del_flag, create_time)
SELECT 1762400000000000001, '河北佰信达实业有限公司', '1', '0', '0', now()
WHERE NOT EXISTS (SELECT 1 FROM sys_company WHERE company_id = 1762400000000000001);

INSERT INTO sys_company (company_id, company_name, is_default, status, del_flag, create_time)
SELECT 1762400000000000002, '河北依时华服饰有限公司', '0', '0', '0', now()
WHERE NOT EXISTS (SELECT 1 FROM sys_company WHERE company_id = 1762400000000000002);

COMMIT;

-- ============================================================
-- 执行后自查：
--   1. \d sys_post_role          -- 表已建
--   2. SELECT perms FROM sys_menu WHERE perms='system:user:sensitive';  -- 1 行
--   3. SELECT column_name FROM information_schema.columns
--      WHERE table_name='sys_role' AND column_name='data_scope_dept_ids';  -- 1 行
-- ============================================================
