-- ============================================================
-- 织云 ERP 主建表脚本（唯一权威建表入口）
-- 规则：开发期可分模块零散写，功能验证通过后必须合并进本文件
--       （见 智能体交接说明.md 四、协作流程第 5 步）
-- 依赖：RuoYi-Vue-Plus 基础表已导入（script/sql/postgres/*.sql）
--       本文件只建"织云自建地基表"，不重复建 RuoYi 自带表
-- 关联：组织树数据预置见 sql/迁移_组织树服装厂.sql + sql/迁移_组织树按厂房重建.sql（厂→车间[厂房]→区域[工序]→小组，dept_category 区分；仓走库存模块不进组织树）
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
    company_id       bigint        NOT NULL,
    company_name     varchar(100)  NOT NULL,
    logo_oss_id      bigint,
    is_default       char(1)       NOT NULL DEFAULT '0',    -- 0否 1是（登录页/首页显示这家公司）
    theme            varchar(50)   DEFAULT '',              -- A.5 主题（工业蓝/深色大屏/自定义色）
    login_bg_oss_id  bigint,                                -- 登录背景图（→ sys_oss.oss_id，走MinIO）
    slogan           varchar(200)  DEFAULT '',              -- 登录页宣传语
    login_bg_mode    char(1)       NOT NULL DEFAULT 'A',    -- A=铺满左侧品牌区 / B=铺满整页
    status           char(1)       NOT NULL DEFAULT '0',    -- 0正常 1停用
    del_flag         char(1)       DEFAULT '0',
    create_dept      bigint,
    create_by        bigint,
    create_time      timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_by        bigint,
    update_time      timestamp,
    remark           varchar(500),
    CONSTRAINT sys_company_pk PRIMARY KEY (company_id),
    CONSTRAINT sys_company_logo_fk FOREIGN KEY (logo_oss_id)
        REFERENCES sys_oss (oss_id) ON DELETE SET NULL,
    CONSTRAINT sys_company_bg_fk FOREIGN KEY (login_bg_oss_id)
        REFERENCES sys_oss (oss_id) ON DELETE SET NULL
);

COMMENT ON TABLE  sys_company            IS '公司档案表（前端规范A.1：登录页/首页公司名+logo，展示层）';
COMMENT ON COLUMN sys_company.company_name IS '公司名（管理端可编辑，不写死代码）';
COMMENT ON COLUMN sys_company.logo_oss_id  IS 'logo文件（→ sys_oss.oss_id，走MinIO，暂可空）';
COMMENT ON COLUMN sys_company.is_default   IS '是否默认展示公司（是=登录页/首页显示）';
COMMENT ON COLUMN sys_company.theme        IS '主题（A.5多主题：工业蓝/深色大屏/自定义色）';
COMMENT ON COLUMN sys_company.login_bg_oss_id IS '登录背景图（→ sys_oss.oss_id，走MinIO，建议竖版1080x1920或方图1200x1200，暂可空）';
COMMENT ON COLUMN sys_company.slogan       IS '登录页宣传语（暂可空，空则用默认\"服装代工厂生产管理系统\"）';
COMMENT ON COLUMN sys_company.login_bg_mode IS '登录背景显示模式：A=铺满左侧品牌区叠加公司名(默认) / B=铺满整页登录卡片悬浮';

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

-- ------------------------------------------------------------
-- 6. 用户功能菜单配置（2026-10-24）
--    用户对每个功能卡片的个性化配置：排序/图标像素大小/置顶/加入工作台
--    支持自由拖拽像素大小（有min/max限制），安卓小组件式网格重排
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_menu_config (
    user_id     bigint       NOT NULL,                 -- 用户ID（→ sys_user.user_id）
    menu_key    varchar(50)  NOT NULL,                 -- 功能标识（前端 modules/systemCards 的 key）
    sort        int          NOT NULL DEFAULT 0,       -- 排序（拖拽后记录）
    card_w      int          NOT NULL DEFAULT 160,     -- 卡片宽度（自由拉伸，min 80 max 480）
    card_h      int          NOT NULL DEFAULT 110,     -- 卡片高度（自由拉伸，min 60 max 320，与宽度独立非等比）
    icon_size   int          NOT NULL DEFAULT 160,     -- 兼容旧列（=card_w），勿直接使用
    is_top      char(1)      NOT NULL DEFAULT '0',     -- 是否置顶 0否 1是
    in_workbench char(1)     NOT NULL DEFAULT '0',     -- 是否加入工作台 0否 1是
    create_time timestamp    DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp,
    CONSTRAINT sys_user_menu_config_pk PRIMARY KEY (user_id, menu_key)
);

COMMENT ON TABLE  sys_user_menu_config          IS '用户功能菜单配置（拖拽排序/卡片宽高自由拉伸/置顶/加入工作台）';
COMMENT ON COLUMN sys_user_menu_config.menu_key IS '功能标识（前端功能卡key）';
COMMENT ON COLUMN sys_user_menu_config.card_w   IS '卡片宽度（自由拉伸，80-480）';
COMMENT ON COLUMN sys_user_menu_config.card_h   IS '卡片高度（自由拉伸，60-320，与宽度独立）';
COMMENT ON COLUMN sys_user_menu_config.icon_size IS '兼容旧列（=card_w）';
COMMENT ON COLUMN sys_user_menu_config.is_top   IS '置顶 0否1是';
COMMENT ON COLUMN sys_user_menu_config.in_workbench IS '加入工作台 0否1是';

-- ------------------------------------------------------------
-- 7. 用户工作台（2026-10-24）
--    登录后默认首页=工作台，聚合用户加入的功能 + 待办汇总
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_workbench (
    user_id      bigint       NOT NULL,
    menu_key     varchar(50)  NOT NULL,
    sort         int          NOT NULL DEFAULT 0,
    create_time  timestamp    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sys_user_workbench_pk PRIMARY KEY (user_id, menu_key)
);

COMMENT ON TABLE sys_user_workbench IS '用户工作台（登录默认首页，聚合加入的功能）';

-- ------------------------------------------------------------
-- 8. 统一待办表（2026-10-24 占位，业务模块开发后接入真实数据）
--    红点级联：模块红点=该模块下所有功能待办数累加；功能卡红点=自己的数
--    无待办不显示红点（count=0 时前端不显示）
--    后期：审批完成/预警处理后减少对应 todo
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_todo (
    todo_id      bigint       NOT NULL,
    user_id      bigint,                              -- 归属用户（空=按模块/功能待办，任何人可见）
    module_key   varchar(50)  NOT NULL,               -- 所属模块（order/prod/.../system）
    menu_key     varchar(50)  NOT NULL,               -- 所属功能（company/...）
    title        varchar(200),                        -- 待办标题
    status       char(1)      NOT NULL DEFAULT '0',   -- 0待处理 1已完成
    biz_id       bigint,                              -- 业务单据ID（审批/预警等，后期关联）
    biz_type     varchar(50),                         -- 业务类型（approve/alert/...）
    create_time  timestamp     DEFAULT CURRENT_TIMESTAMP,
    handle_time  timestamp,
    CONSTRAINT sys_todo_pk PRIMARY KEY (todo_id)
);

COMMENT ON TABLE  sys_todo              IS '统一待办表（红点占位，业务模块开发后接入真实数据）';
COMMENT ON COLUMN sys_todo.module_key   IS '所属模块（左侧导航模块key，红点模块级累加）';
COMMENT ON COLUMN sys_todo.menu_key     IS '所属功能（功能卡key，红点功能级）';
COMMENT ON COLUMN sys_todo.status       IS '0待处理(计红点) 1已完成(不计)';

CREATE INDEX IF NOT EXISTS idx_sys_todo_module ON sys_todo (module_key, menu_key, status);

-- ------------------------------------------------------------
-- 9. 人员扩展信息表（2026-10-24 定稿，人员模块）
--    一对一挂 sys_user（user_id 主键同 sys_user）；建人员=同写 sys_user+本表+岗位角色关系
--    sys_user 存登录账号+基础身份/联系方式；本表存任职/合同薪酬/社保保险/证件/住宿
--    住宿只登记"是否住宿"(is_dorm)，床位/费用在宿舍模块管理，不进本表
--    敏感字段（证件号/手机号/银行卡）脱敏展示
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_person_ext (
    user_id            bigint        NOT NULL,          -- 主键=sys_user.user_id（员工ID，不变）
    -- 基本信息
    marital_status     varchar(10),                     -- 婚姻状况 未婚/已婚/离异
    cert_type          varchar(20),                     -- 证件类型 身份证/护照/其他
    cert_no            varchar(50),                     -- 证件号码（敏感，脱敏）
    nation             varchar(20),                     -- 民族
    education          varchar(20),                     -- 学历
    birth_date         date,                            -- 出生日期
    graduate_school    varchar(100),                    -- 毕业院校
    native_place       varchar(100),                    -- 籍贯
    address            varchar(200),                    -- 详细地址
    cert_expire_date   date,                            -- 证件到期日（进证件预警）
    -- 任职信息（车间=sys_user.dept_id；岗位/角色走 sys_user_post/sys_user_role）
    hire_date          date,                            -- 入职日期（必填）
    group_name         varchar(50),                     -- 小组名（文本冗余，展示/兼容用；权威关联看 group_id）
    group_id           bigint,                          -- 小组关联=sys_dept.dept_id(dept_category='小组'，组织树厂→车间→区域→小组)
    emp_type           varchar(20),                     -- 员工性质 正式/临时/劳务
    leave_date         date,                            -- 离职日期
    card_no            varchar(30),                     -- 员工卡（工牌号）
    emp_status         varchar(10)  NOT NULL DEFAULT '试用', -- 在职状态 试用/在职/离职/长假
    -- 合同薪酬
    bank_card_no       varchar(30),                     -- 银行卡号（敏感，脱敏）
    sign_entity        varchar(100),                    -- 签约主体
    contract_years     int,                             -- 合同年限
    contract_start     date,                            -- 合同签订日期
    contract_end       date,                            -- 合同截止日期
    -- 社保保险登记（独立块）
    has_social         char(1)       DEFAULT '0',       -- 是否缴纳社保 0否 1是（临工可0）
    social_amount      numeric(10,2),                   -- 社保缴纳金额 元/月
    social_subsidy     numeric(10,2),                   -- 社保补助金额 元/月
    has_commercial     char(1)       DEFAULT '0',       -- 是否缴纳商业险 0否 1是
    commercial_type    varchar(100),                    -- 商业险种（意外险/雇主责任险等）
    social_check_date  date,                            -- 社保核对日期（留痕）
    -- 住宿（只登记住不住，床位在宿舍模块）
    is_dorm            char(1)       DEFAULT '0',       -- 是否住宿 0否 1是
    create_time        timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_time        timestamp,
    CONSTRAINT sys_person_ext_pk PRIMARY KEY (user_id)
);

COMMENT ON TABLE  sys_person_ext              IS '人员扩展信息（一对一挂sys_user；任职/合同薪酬/社保保险/证件/住宿登记）';
COMMENT ON COLUMN sys_person_ext.user_id      IS '主键=sys_user.user_id（员工ID，不变）';
COMMENT ON COLUMN sys_person_ext.cert_no      IS '证件号码（敏感，脱敏展示）';
COMMENT ON COLUMN sys_person_ext.hire_date    IS '入职日期（必填）';
COMMENT ON COLUMN sys_person_ext.group_name   IS '小组名（文本冗余，展示/兼容用）';
COMMENT ON COLUMN sys_person_ext.group_id     IS '小组关联=sys_dept.dept_id（dept_category=小组；组织树 厂→车间→区域→小组，仓走库存模块不进组织树）';
COMMENT ON COLUMN sys_person_ext.emp_status   IS '在职状态 试用/在职/离职/长假（状态机）';
COMMENT ON COLUMN sys_person_ext.bank_card_no IS '银行卡号（敏感，脱敏展示）';
COMMENT ON COLUMN sys_person_ext.has_social   IS '是否缴纳社保 0否1是（临工可0）';
COMMENT ON COLUMN sys_person_ext.has_commercial IS '是否缴纳商业险 0否1是（保险=商业险）';
COMMENT ON COLUMN sys_person_ext.is_dorm      IS '是否住宿 0否1是（只登记，床位在宿舍模块）';

COMMIT;

-- ============================================================
-- 执行后自查：
--   1. \d sys_post_role          -- 表已建
--   2. SELECT perms FROM sys_menu WHERE perms='system:user:sensitive';  -- 1 行
--   3. SELECT column_name FROM information_schema.columns
--      WHERE table_name='sys_role' AND column_name='data_scope_dept_ids';  -- 1 行
--   4. \d sys_user_menu_config / sys_user_workbench / sys_todo / sys_person_ext  -- 4 张新表
-- ============================================================
