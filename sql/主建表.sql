-- ============================================================
-- 织云 ERP 主建表脚本（唯一权威建表入口）
-- 规则：开发期可分模块零散写，功能验证通过后必须合并进本文件
--       （见 智能体交接说明.md 四、协作流程第 5 步）
-- 依赖：RuoYi-Vue-Plus 基础表已导入（script/sql/postgres/*.sql）
--       本文件只建"织云自建地基表"，不重复建 RuoYi 自带表
-- 新库构建顺序（2026-10-30 定版）：
--   ① 框架 SQL   RuoYi-Vue-Plus/script/sql/postgres/*.sql（含改名前的 sys_dept 等）
--   ② 改名迁移   sql/迁移_dept改org_20261027.sql（dept→org 全局改名 + 线上扩展列）
--   ③ 本文件     sql/主建表.sql（织云自建地基表，全 org 命名）
--   ④ 组织树数据 sql/迁移_组织树服装厂.sql + sql/迁移_组织树按厂房重建.sql（org_category 区分 厂→车间[厂房]→区域[工序]→小组；仓走库存模块不进组织树）
-- 执行：psql -U zhizhun -d zhizhun_erp -f sql/主建表.sql
-- 更新：2026-10-24
-- 更新：2026-10-30（与线上库对齐：create_dept→create_org；sys_role.data_scope_dept_ids→data_scope_org_ids；
--       补 sys_company.name_color、sys_user_menu_config.locked、新增 sys_org_manage 节；
--       全部列注释补全，另见 sql/补齐注释_20261030.sql）
-- 更新：2026-11-05（确认公司档案、组织架构、岗位管理、建筑档案及客户档案相关表结构和权限均已合并；客户国家/地区编码字段与外键一并保留）
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
    create_org  bigint,                                -- 创建部门（dept→org 全局改名后为 create_org）
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
COMMENT ON COLUMN sys_post_role.create_org     IS '创建部门（→ sys_org.org_id）';
COMMENT ON COLUMN sys_post_role.create_by      IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_post_role.create_time    IS '创建时间';
COMMENT ON COLUMN sys_post_role.update_by      IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_post_role.update_time    IS '更新时间';
COMMENT ON COLUMN sys_post_role.remark         IS '备注';

CREATE INDEX IF NOT EXISTS idx_sys_post_role_role_id ON sys_post_role (role_id);

-- ------------------------------------------------------------
-- 2. sys_role 扩展：数据范围支持"指定节点集合"（权限地基 2.2 第 3 种）
--    RuoYi 内置 data_scope 取值：1全数据 2本部门 3本部门及以下 4仅本人 5自定义
--    织云扩展第 6 种：6指定节点集合（跨部门协作，如总监/计划部跨车间排产）
--    data_scope=6 时，本列存逗号分隔的 sys_org.org_id 集合
--    （2026-10-30 同步：dept→org 全局改名后，线上列为 data_scope_org_ids）
-- ------------------------------------------------------------
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope_org_ids varchar(2000) DEFAULT '';
COMMENT ON COLUMN sys_role.data_scope_org_ids IS '数据范围=6(指定节点集合)时的组织节点ID集合，逗号分隔，→ sys_org.org_id';

-- ------------------------------------------------------------
-- 3. 内置敏感数据权限点（权限地基 3.4.2）
--    系统只内置这一个权限点，勾了=看明文，没勾=后端脱敏 ***
--    其他业务角色/权限点由超管在管理端运行时配置，系统不预置
--    sys_menu：RuoYi 权限码表（菜单/按钮/接口），type=M菜单 F按钮 C列权限
-- ------------------------------------------------------------
-- 3.1 归属"系统管理"一级菜单下（若不存在则建）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_org, create_by, create_time, update_by, update_time, remark)
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
    create_org       bigint,
    create_by        bigint,
    create_time      timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_by        bigint,
    update_time      timestamp,
    remark           varchar(500),
    name_color       varchar(20)   DEFAULT '#FFFFFF',       -- 公司名文本颜色（hex 色值，登录页/首页展示用）
    CONSTRAINT sys_company_pk PRIMARY KEY (company_id),
    CONSTRAINT sys_company_logo_fk FOREIGN KEY (logo_oss_id)
        REFERENCES sys_oss (oss_id) ON DELETE SET NULL,
    CONSTRAINT sys_company_bg_fk FOREIGN KEY (login_bg_oss_id)
        REFERENCES sys_oss (oss_id) ON DELETE SET NULL
);

COMMENT ON TABLE  sys_company            IS '公司档案表（前端规范A.1：登录页/首页公司名+logo，展示层）';
COMMENT ON COLUMN sys_company.company_id IS '公司ID（主键）';
COMMENT ON COLUMN sys_company.company_name IS '公司名（管理端可编辑，不写死代码）';
COMMENT ON COLUMN sys_company.logo_oss_id  IS 'logo文件（→ sys_oss.oss_id，走MinIO，暂可空）';
COMMENT ON COLUMN sys_company.is_default   IS '是否默认展示公司（是=登录页/首页显示）';
COMMENT ON COLUMN sys_company.theme        IS '主题（A.5多主题：工业蓝/深色大屏/自定义色）';
COMMENT ON COLUMN sys_company.login_bg_oss_id IS '登录背景图（→ sys_oss.oss_id，走MinIO，建议竖版1080x1920或方图1200x1200，暂可空）';
COMMENT ON COLUMN sys_company.slogan       IS '登录页宣传语（暂可空，空则用默认"服装代工厂生产管理系统"）';
COMMENT ON COLUMN sys_company.login_bg_mode IS '登录背景显示模式：A=铺满左侧品牌区叠加公司名(默认) / B=铺满整页登录卡片悬浮';
COMMENT ON COLUMN sys_company.status       IS '状态：0正常/1停用';
COMMENT ON COLUMN sys_company.del_flag     IS '删除标志：0存在/1删除';
COMMENT ON COLUMN sys_company.create_org   IS '创建部门（→ sys_org.org_id）';
COMMENT ON COLUMN sys_company.create_by    IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_company.create_time  IS '创建时间';
COMMENT ON COLUMN sys_company.update_by    IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_company.update_time  IS '更新时间';
COMMENT ON COLUMN sys_company.remark       IS '备注';
COMMENT ON COLUMN sys_company.name_color   IS '公司名文本颜色（hex 色值，如 #FFFFFF，登录页/首页展示用）';

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
    locked      char(1)      DEFAULT '1',              -- 卡片等比锁定 1锁定(正方形档位) 0解锁(可长方形)
    create_time timestamp    DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp,
    CONSTRAINT sys_user_menu_config_pk PRIMARY KEY (user_id, menu_key)
);

COMMENT ON TABLE  sys_user_menu_config          IS '用户功能菜单配置（拖拽排序/卡片宽高自由拉伸/置顶/加入工作台）';
COMMENT ON COLUMN sys_user_menu_config.user_id    IS '用户ID（→ sys_user.user_id）';
COMMENT ON COLUMN sys_user_menu_config.menu_key IS '功能标识（前端功能卡key）';
COMMENT ON COLUMN sys_user_menu_config.sort     IS '排序（拖拽后记录）';
COMMENT ON COLUMN sys_user_menu_config.card_w   IS '卡片宽度（自由拉伸，80-480）';
COMMENT ON COLUMN sys_user_menu_config.card_h   IS '卡片高度（自由拉伸，60-320，与宽度独立）';
COMMENT ON COLUMN sys_user_menu_config.icon_size IS '兼容旧列（=card_w）';
COMMENT ON COLUMN sys_user_menu_config.is_top   IS '置顶 0否1是';
COMMENT ON COLUMN sys_user_menu_config.in_workbench IS '加入工作台 0否1是';
COMMENT ON COLUMN sys_user_menu_config.locked    IS '卡片等比锁定 1锁定(正方形档位) 0解锁(可长方形)';
COMMENT ON COLUMN sys_user_menu_config.create_time  IS '创建时间';
COMMENT ON COLUMN sys_user_menu_config.update_time  IS '更新时间';

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
COMMENT ON COLUMN sys_user_workbench.user_id     IS '用户ID（→ sys_user.user_id）';
COMMENT ON COLUMN sys_user_workbench.menu_key    IS '功能标识（前端功能卡 key，→ config/modules.ts）';
COMMENT ON COLUMN sys_user_workbench.sort        IS '排序';
COMMENT ON COLUMN sys_user_workbench.create_time IS '创建时间';

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
COMMENT ON COLUMN sys_todo.todo_id      IS '待办ID（主键）';
COMMENT ON COLUMN sys_todo.user_id      IS '归属用户（→ sys_user.user_id；空=模块/功能级待办，任何人可见）';
COMMENT ON COLUMN sys_todo.module_key   IS '所属模块（左侧导航模块key，红点模块级累加）';
COMMENT ON COLUMN sys_todo.menu_key     IS '所属功能（功能卡key，红点功能级）';
COMMENT ON COLUMN sys_todo.title        IS '待办标题';
COMMENT ON COLUMN sys_todo.status       IS '0待处理(计红点) 1已完成(不计)';
COMMENT ON COLUMN sys_todo.biz_id       IS '业务单据ID（审批/预警等，后期关联）';
COMMENT ON COLUMN sys_todo.biz_type     IS '业务类型：approve审批/alert预警/...';
COMMENT ON COLUMN sys_todo.create_time  IS '创建时间';
COMMENT ON COLUMN sys_todo.handle_time  IS '处理完成时间';

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
    group_id           bigint,                          -- 小组关联=sys_org.org_id(org_category='小组'，组织树厂→车间→区域→小组)
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
COMMENT ON COLUMN sys_person_ext.group_id     IS '小组关联（→ sys_org.org_id，org_category=小组；组织树 厂→车间→区域→小组，仓走库存模块不进组织树）';
COMMENT ON COLUMN sys_person_ext.emp_status   IS '在职状态 试用/在职/离职/长假（状态机）';
COMMENT ON COLUMN sys_person_ext.bank_card_no IS '银行卡号（敏感，脱敏展示）';
COMMENT ON COLUMN sys_person_ext.has_social   IS '是否缴纳社保 0否1是（临工可0）';
COMMENT ON COLUMN sys_person_ext.has_commercial IS '是否缴纳商业险 0否1是（保险=商业险）';
COMMENT ON COLUMN sys_person_ext.is_dorm      IS '是否住宿 0否1是（只登记，床位在宿舍模块）';
COMMENT ON COLUMN sys_person_ext.marital_status IS '婚姻状况：未婚/已婚/离异';
COMMENT ON COLUMN sys_person_ext.cert_type    IS '证件类型：身份证/护照/其他';
COMMENT ON COLUMN sys_person_ext.nation       IS '民族';
COMMENT ON COLUMN sys_person_ext.education    IS '学历';
COMMENT ON COLUMN sys_person_ext.birth_date   IS '出生日期';
COMMENT ON COLUMN sys_person_ext.graduate_school IS '毕业院校';
COMMENT ON COLUMN sys_person_ext.native_place IS '籍贯';
COMMENT ON COLUMN sys_person_ext.address      IS '详细地址';
COMMENT ON COLUMN sys_person_ext.cert_expire_date IS '证件到期日（进证件预警）';
COMMENT ON COLUMN sys_person_ext.emp_type     IS '员工性质：正式/临时/劳务';
COMMENT ON COLUMN sys_person_ext.leave_date   IS '离职日期';
COMMENT ON COLUMN sys_person_ext.card_no      IS '员工卡（工牌号）';
COMMENT ON COLUMN sys_person_ext.sign_entity  IS '签约主体（合同签约公司）';
COMMENT ON COLUMN sys_person_ext.contract_years IS '合同年限（年）';
COMMENT ON COLUMN sys_person_ext.contract_start IS '合同签订日期';
COMMENT ON COLUMN sys_person_ext.contract_end  IS '合同截止日期';
COMMENT ON COLUMN sys_person_ext.social_amount IS '社保缴纳金额（元/月，DECIMAL(10,2)）';
COMMENT ON COLUMN sys_person_ext.social_subsidy IS '社保补助金额（元/月，DECIMAL(10,2)）';
COMMENT ON COLUMN sys_person_ext.commercial_type IS '商业险种（意外险/雇主责任险等）';
COMMENT ON COLUMN sys_person_ext.social_check_date IS '社保核对日期（留痕）';
COMMENT ON COLUMN sys_person_ext.create_time   IS '创建时间';
COMMENT ON COLUMN sys_person_ext.update_time   IS '更新时间';

-- ------------------------------------------------------------
-- 2. 建筑档案（物理资产/固定资产，非部门）
--    建筑=一栋楼/固定资产（面积/楼层/地址/建成年份），是"地"；
--    部门(车间/组)=管人的组织，是"人"，坐落在建筑里。
--    用途类别：厂房/行政楼/宿舍楼（**仓库是建筑内区域，不在此列**）。
--    厂房一建筑=一一车间（一号厂房=一号车间，物理/管理两种称谓）。
--    关联：sys_org 车间节点加 building_id 指向本表（只车间层关联，下面区域/组继承）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_building (
    plant_id       bigint       NOT NULL,                -- 建筑ID（列名沿用 plant_id）
    building_type  varchar(20)  DEFAULT '厂房',           -- 用途类别 厂房/行政楼/宿舍楼
    plant_code     varchar(20),                          -- 建筑编号（如 P1/P2/P3）
    plant_name     varchar(50)  NOT NULL,                -- 建筑名称（一号厂房/行政楼/一号宿舍楼…）
    floor_info     varchar(50),                          -- 楼层情况（单层/双层/三层…）
    land_area      numeric(12,2),                        -- 占地面积 ㎡
    building_area  numeric(12,2),                        -- 建筑面积 ㎡
    address        varchar(200),                         -- 地址
    built_year     int,                                  -- 建成年份
    status         char(1)       DEFAULT '0',            -- 状态 0在用 1闲置 2维修
    remark         varchar(500),                         -- 备注
    del_flag       char(1)       DEFAULT '0',            -- 删除标志 0存在 1删除
    create_by      bigint,                                -- 创建者
    create_time    timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_by      bigint,                                -- 更新者
    update_time    timestamp,                             -- 更新时间
    CONSTRAINT sys_building_pk PRIMARY KEY (plant_id)
);

COMMENT ON TABLE  sys_building            IS '建筑档案（物理资产/固定资产；厂房/行政楼/宿舍楼等独立楼宇；仓库是建筑内区域不在此列）';
COMMENT ON COLUMN sys_building.building_type IS '用途类别 厂房/行政楼/宿舍楼';
COMMENT ON COLUMN sys_building.plant_id    IS '建筑ID（主键，列名沿用 plant_id）';
COMMENT ON COLUMN sys_building.plant_code   IS '建筑编号（如 P1/P2/P3）';
COMMENT ON COLUMN sys_building.floor_info   IS '楼层情况（单层/双层/三层…）';
COMMENT ON COLUMN sys_building.land_area    IS '占地面积 ㎡';
COMMENT ON COLUMN sys_building.building_area IS '建筑面积 ㎡';
COMMENT ON COLUMN sys_building.address      IS '地址';
COMMENT ON COLUMN sys_building.built_year  IS '建成年份';
COMMENT ON COLUMN sys_building.status       IS '状态 0在用 1闲置 2维修';
COMMENT ON COLUMN sys_building.remark      IS '备注';
COMMENT ON COLUMN sys_building.del_flag    IS '删除标志：0存在/1删除';
COMMENT ON COLUMN sys_building.create_by   IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_building.create_time IS '创建时间';
COMMENT ON COLUMN sys_building.update_by   IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_building.update_time IS '更新时间';

-- ------------------------------------------------------------
-- 11. 组织架构业务管辖表（2026-10-30 并入：线上已有，文件此前遗漏）
--     管理部门与被管部门的多对多关联，表达"谁管谁"（如 生产部 管 一号车间），
--     与组织树层级（厂→车间→区域→小组）无关。
--     注：主键约束名沿用改名前 pk_sys_dept_manage（dept→org 全局改名时未重建约束名）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_org_manage (
    manage_org_id  bigint       NOT NULL,                -- 管辖方部门ID（如生产部，→ sys_org.org_id）
    managed_org_id bigint       NOT NULL,                -- 被管部门ID（如一号车间，→ sys_org.org_id）
    create_time    timestamp,                             -- 创建时间
    CONSTRAINT pk_sys_dept_manage PRIMARY KEY (manage_org_id, managed_org_id)
);

COMMENT ON TABLE  sys_org_manage              IS '组织架构业务管辖关联（管理部门与被管部门，多对多；与组织树层级无关，表达谁管谁）';
COMMENT ON COLUMN sys_org_manage.manage_org_id   IS '管辖方部门ID（如生产部，→ sys_org.org_id）';
COMMENT ON COLUMN sys_org_manage.managed_org_id  IS '被管部门ID（如一号车间，→ sys_org.org_id）';
COMMENT ON COLUMN sys_org_manage.create_time     IS '创建时间';

-- ------------------------------------------------------------
-- 15. 通用属性标签档案与业务记录关联
--     模块隔离：同名标签可在不同模块分别存在
--     不预置业务模块或标签；业务内首次输入标签时动态登记模块归属
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tag_module (
    module_key   varchar(50)   NOT NULL,
    module_name  varchar(100)  NOT NULL,
    create_by    bigint,
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    bigint,
    update_time  timestamp,
    CONSTRAINT sys_tag_module_pk PRIMARY KEY (module_key)
);

COMMENT ON TABLE sys_tag_module IS '动态标签归属模块目录；仅业务功能首次添加标签时自动登记，不预置空模块';
COMMENT ON COLUMN sys_tag_module.module_key IS '业务功能提供的稳定模块标识';
COMMENT ON COLUMN sys_tag_module.module_name IS '标签管理页面显示的业务模块名称';
COMMENT ON COLUMN sys_tag_module.create_by IS '首次登记模块的操作人用户ID';
COMMENT ON COLUMN sys_tag_module.create_time IS '模块归属首次登记时间';
COMMENT ON COLUMN sys_tag_module.update_by IS '最后修改模块名称的操作人用户ID';
COMMENT ON COLUMN sys_tag_module.update_time IS '模块归属最后修改时间';

CREATE SEQUENCE IF NOT EXISTS sys_tag_id_seq;
COMMENT ON SEQUENCE sys_tag_id_seq IS '通用标签档案主键序列';

CREATE TABLE IF NOT EXISTS sys_tag (
    tag_id       bigint        NOT NULL DEFAULT nextval('sys_tag_id_seq'),
    module_key   varchar(50)   NOT NULL,
    tag_name     varchar(100)  NOT NULL,
    is_common    char(1)       NOT NULL DEFAULT '0',
    status       char(1)       NOT NULL DEFAULT '0',
    create_by    bigint,
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    bigint,
    update_time  timestamp,
    remark       varchar(500),
    CONSTRAINT sys_tag_pk PRIMARY KEY (tag_id),
    CONSTRAINT sys_tag_module_fk FOREIGN KEY (module_key)
        REFERENCES sys_tag_module (module_key)
);
DO $$
DECLARE
    tag_table_owner text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO tag_table_owner
    FROM pg_class WHERE oid = 'sys_tag'::regclass;
    EXECUTE format('ALTER SEQUENCE sys_tag_id_seq OWNER TO %I', tag_table_owner);
END $$;
ALTER SEQUENCE sys_tag_id_seq OWNED BY sys_tag.tag_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_module_name
    ON sys_tag (module_key, lower(tag_name));
CREATE INDEX IF NOT EXISTS idx_sys_tag_module_status
    ON sys_tag (module_key, status, is_common);

COMMENT ON TABLE sys_tag IS '通用属性标签档案；按模块命名空间隔离';
COMMENT ON COLUMN sys_tag.tag_id IS '标签档案主键，由 PostgreSQL 序列生成';
COMMENT ON COLUMN sys_tag.module_key IS '标签命名空间模块标识';
COMMENT ON COLUMN sys_tag.tag_name IS '标签名称；所属模块名称通过 module_key 关联 sys_tag_module 获取';
COMMENT ON COLUMN sys_tag.is_common IS '是否作为常用提示：0否/1是';
COMMENT ON COLUMN sys_tag.status IS '状态：0正常/1停用';
COMMENT ON COLUMN sys_tag.create_by IS '创建人用户ID';
COMMENT ON COLUMN sys_tag.create_time IS '创建时间';
COMMENT ON COLUMN sys_tag.update_by IS '最后修改人用户ID';
COMMENT ON COLUMN sys_tag.update_time IS '最后修改时间';
COMMENT ON COLUMN sys_tag.remark IS '标签维护备注';

CREATE TABLE IF NOT EXISTS sys_tag_relation (
    module_key   varchar(50) NOT NULL,
    record_id    bigint      NOT NULL,
    tag_id       bigint      NOT NULL,
    create_by    bigint,
    create_time  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sys_tag_relation_pk PRIMARY KEY (module_key, record_id, tag_id),
    CONSTRAINT sys_tag_relation_tag_fk FOREIGN KEY (tag_id)
        REFERENCES sys_tag (tag_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sys_tag_relation_tag
    ON sys_tag_relation (module_key, tag_id);
COMMENT ON TABLE sys_tag_relation IS '通用属性标签与业务记录关联；业务表由module_key区分';
COMMENT ON COLUMN sys_tag_relation.record_id IS '业务记录ID';
COMMENT ON COLUMN sys_tag_relation.module_key IS '业务模块命名空间';
COMMENT ON COLUMN sys_tag_relation.tag_id IS '标签档案ID';
COMMENT ON COLUMN sys_tag_relation.create_by IS '建立关联的操作人用户ID';
COMMENT ON COLUMN sys_tag_relation.create_time IS '标签与业务记录建立关联的时间';

-- 兼容重复执行主建表脚本以及从旧版毫秒ID迁移的数据库；新标签从当前最大ID之后取值。
SELECT setval(
    'sys_tag_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(tag_id) FROM sys_tag), 0) + 1,
        (SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM sys_tag_id_seq)
    ),
    false
);

-- 标签管理权限点，挂系统管理一级菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_org, create_by, create_time, update_by, update_time, remark)
SELECT 1762400000000000101, '标签查询', 5, 100, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:tag:list', '#', 1761000000000000003, 1761100000000000001, now(), NULL, NULL, '通用标签档案查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:list');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_org, create_by, create_time, update_by, update_time, remark)
SELECT 1762400000000000102, '标签新增', 5, 101, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:tag:add', '#', 1761000000000000003, 1761100000000000001, now(), NULL, NULL, '通用标签档案新增'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:add');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_org, create_by, create_time, update_by, update_time, remark)
SELECT 1762400000000000103, '标签修改', 5, 102, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:tag:edit', '#', 1761000000000000003, 1761100000000000001, now(), NULL, NULL, '通用标签档案修改'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:edit');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_org, create_by, create_time, update_by, update_time, remark)
SELECT 1762400000000000104, '标签停用', 5, 103, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:tag:remove', '#', 1761000000000000003, 1761100000000000001, now(), NULL, NULL, '通用标签停用（软停用）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:remove');

COMMIT;

-- 公司档案删除权限：与现有公司列表及新增/修改操作同级。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1761800000000000011,'公司删除',1761800000000000007,14,'#','','N','Y','F','0','0','system:company:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:company:remove');

-- ============================================================
-- 执行后自查：
--   1. \d sys_post_role          -- 表已建
--   2. SELECT perms FROM sys_menu WHERE perms='system:user:sensitive';  -- 1 行
--   3. SELECT column_name FROM information_schema.columns
--      WHERE table_name='sys_role' AND column_name='data_scope_org_ids';  -- 1 行
--   4. \d sys_user_menu_config / sys_user_workbench / sys_todo / sys_person_ext / sys_org_manage  -- 5 张新表
-- ============================================================


-- 客户档案一期增量迁移。使用序列生成稳定客户编号，不删除历史记录。
BEGIN;
CREATE SEQUENCE IF NOT EXISTS customer_code_seq;
CREATE TABLE IF NOT EXISTS customer_master (
 customer_id bigserial PRIMARY KEY,
 customer_code varchar(50) NOT NULL UNIQUE DEFAULT ('KH' || lpad(nextval('customer_code_seq')::text,8,'0')),
 customer_name varchar(200) NOT NULL,
 unified_social_credit_code varchar(18),
 customer_short_name varchar(100),
 country_region varchar(100),
 sales_representative_id bigint NOT NULL,
 status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
 remark varchar(2000),
 create_by bigint,
 create_time timestamp NOT NULL DEFAULT now(),
 update_by bigint,
 update_time timestamp
);
COMMENT ON TABLE customer_master IS '客户档案；供订单及样衣引用，停用不改变历史单据';
COMMENT ON COLUMN customer_master.customer_id IS '客户主键';
COMMENT ON COLUMN customer_master.customer_code IS '系统自动生成的稳定客户编号';
COMMENT ON COLUMN customer_master.customer_name IS '客户名称；允许同名，保存前提醒';
COMMENT ON COLUMN customer_master.unified_social_credit_code IS '统一社会信用代码；境外客户可空';
COMMENT ON COLUMN customer_master.customer_short_name IS '客户简称';
COMMENT ON COLUMN customer_master.country_region IS '国家地区，手动录入';
COMMENT ON COLUMN customer_master.sales_representative_id IS '业务员，关联人员信息，不校验特定岗位';
COMMENT ON COLUMN customer_master.status IS '状态：0启用，1停用；停用仅禁止新增业务引用';
COMMENT ON COLUMN customer_master.remark IS '备注';
COMMENT ON COLUMN customer_master.create_by IS '创建人用户ID';
COMMENT ON COLUMN customer_master.create_time IS '创建时间';
COMMENT ON COLUMN customer_master.update_by IS '更新人用户ID';
COMMENT ON COLUMN customer_master.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_customer_master_name ON customer_master(lower(customer_name));
CREATE TABLE IF NOT EXISTS customer_contact (
 contact_id bigserial PRIMARY KEY,
 customer_id bigint NOT NULL REFERENCES customer_master(customer_id),
 contact_name varchar(100) NOT NULL,
 contact_title varchar(100),
 phone_number varchar(100),
 email varchar(200),
 contact_role varchar(100),
 is_primary boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE customer_contact IS '客户多联系人';
COMMENT ON COLUMN customer_contact.contact_id IS '联系人主键';
COMMENT ON COLUMN customer_contact.customer_id IS '所属客户ID';
COMMENT ON COLUMN customer_contact.contact_name IS '联系人姓名';
COMMENT ON COLUMN customer_contact.contact_title IS '联系人职务';
COMMENT ON COLUMN customer_contact.phone_number IS '联系电话或手机';
COMMENT ON COLUMN customer_contact.email IS '电子邮箱';
COMMENT ON COLUMN customer_contact.contact_role IS '联系人用途，手动填写';
COMMENT ON COLUMN customer_contact.is_primary IS '是否默认联系人，每客户最多一位';
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_primary_contact ON customer_contact(customer_id) WHERE is_primary;
CREATE TABLE IF NOT EXISTS customer_address (
 address_id bigserial PRIMARY KEY,
 customer_id bigint NOT NULL REFERENCES customer_master(customer_id),
 address_type varchar(100) NOT NULL,
 recipient_name varchar(100),
 recipient_phone varchar(100),
 country_region varchar(100),
 province varchar(100),
 city varchar(100),
 district varchar(100),
 address_line varchar(500),
 postal_code varchar(30),
 is_default boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE customer_address IS '客户多地址，地区和地址手工录入';
COMMENT ON COLUMN customer_address.address_id IS '地址主键';
COMMENT ON COLUMN customer_address.customer_id IS '所属客户ID';
COMMENT ON COLUMN customer_address.address_type IS '地址用途';
COMMENT ON COLUMN customer_address.recipient_name IS '收件人';
COMMENT ON COLUMN customer_address.recipient_phone IS '收件电话';
COMMENT ON COLUMN customer_address.country_region IS '国家地区';
COMMENT ON COLUMN customer_address.province IS '省或州';
COMMENT ON COLUMN customer_address.city IS '城市';
COMMENT ON COLUMN customer_address.district IS '区县';
COMMENT ON COLUMN customer_address.address_line IS '详细地址';
COMMENT ON COLUMN customer_address.postal_code IS '邮政编码';
COMMENT ON COLUMN customer_address.is_default IS '是否该用途的默认地址';
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_default_address ON customer_address(customer_id,address_type) WHERE is_default;
-- 独立权限条目供角色管理勾选；不自动向现有普通角色授予客户权限。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000201,'客户查询',5,110,'#','','N','Y','F','0','0','system:customer:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000202,'客户新增',5,111,'#','','N','Y','F','0','0','system:customer:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000203,'客户修改与启停',5,112,'#','','N','Y','F','0','0','system:customer:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:edit');
COMMIT;

-- 国家/地区基础资料及客户档案地区编码迁移。
-- 基础名称取自本机 iso-codes 的 ISO 3166-1 两位编码和中文翻译；可在系统中维护。
-- 保留客户/地址原手填文本；仅对名称或编码唯一匹配的数据填写标准编码。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_country_region (
 country_code varchar(12) PRIMARY KEY,
 name_zh varchar(100) NOT NULL,
 name_en varchar(200) NOT NULL DEFAULT '',
 status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
 create_by bigint,
 create_time timestamp NOT NULL DEFAULT now(),
 update_by bigint,
 update_time timestamp,
 CONSTRAINT ck_country_region_code CHECK (country_code ~ '^[A-Z][A-Z0-9_-]{1,11}$')
);
COMMENT ON TABLE sys_country_region IS '国家和地区基础资料：编码稳定，供客户、订单目的地和发货目的地分别引用';
COMMENT ON COLUMN sys_country_region.country_code IS '国家或地区稳定编码；初始数据使用 ISO 3166-1 两位编码，新增后不可修改';
COMMENT ON COLUMN sys_country_region.name_zh IS '国家或地区中文显示名称';
COMMENT ON COLUMN sys_country_region.name_en IS '国家或地区英文名称，用于显示与检索';
COMMENT ON COLUMN sys_country_region.status IS '状态：0启用、1停用；停用不改变历史引用';
COMMENT ON COLUMN sys_country_region.create_by IS '创建人用户ID';
COMMENT ON COLUMN sys_country_region.create_time IS '创建时间';
COMMENT ON COLUMN sys_country_region.update_by IS '最后修改人用户ID';
COMMENT ON COLUMN sys_country_region.update_time IS '最后修改时间';

INSERT INTO sys_country_region(country_code,name_zh,name_en) VALUES
  ('AD','安道尔','Andorra'),
  ('AE','阿联酋','United Arab Emirates'),
  ('AF','阿富汗','Afghanistan'),
  ('AG','安提瓜和巴布达','Antigua and Barbuda'),
  ('AI','安圭拉','Anguilla'),
  ('AL','阿尔巴尼亚','Albania'),
  ('AM','亚美尼亚','Armenia'),
  ('AO','安哥拉','Angola'),
  ('AQ','南极洲','Antarctica'),
  ('AR','阿根廷','Argentina'),
  ('AS','美属萨摩亚','American Samoa'),
  ('AT','奥地利','Austria'),
  ('AU','澳大利亚','Australia'),
  ('AW','阿鲁巴','Aruba'),
  ('AX','奥兰群岛','Åland Islands'),
  ('AZ','阿塞拜疆','Azerbaijan'),
  ('BA','波斯尼亚和黑塞哥维那','Bosnia and Herzegovina'),
  ('BB','巴巴多斯','Barbados'),
  ('BD','孟加拉','Bangladesh'),
  ('BE','比利时','Belgium'),
  ('BF','布基纳法索','Burkina Faso'),
  ('BG','保加利亚','Bulgaria'),
  ('BH','巴林','Bahrain'),
  ('BI','布隆迪','Burundi'),
  ('BJ','贝宁','Benin'),
  ('BL','圣巴泰勒米岛','Saint Barthélemy'),
  ('BM','百慕大','Bermuda'),
  ('BN','文莱','Brunei Darussalam'),
  ('BO','玻利维亚共和国','Bolivia, Plurinational State of'),
  ('BQ','博奈尔、圣尤斯特歇斯岛和萨巴','Bonaire, Sint Eustatius and Saba'),
  ('BR','巴西','Brazil'),
  ('BS','巴哈马','Bahamas'),
  ('BT','不丹','Bhutan'),
  ('BV','布维群岛','Bouvet Island'),
  ('BW','博兹瓦那','Botswana'),
  ('BY','白俄罗斯','Belarus'),
  ('BZ','伯利兹','Belize'),
  ('CA','加拿大','Canada'),
  ('CC','科科斯群岛','Cocos (Keeling) Islands'),
  ('CD','刚果民主共和国','Congo, The Democratic Republic of the'),
  ('CF','中非','Central African Republic'),
  ('CG','刚果','Congo'),
  ('CH','瑞士','Switzerland'),
  ('CI','科特迪瓦','Côte d''Ivoire'),
  ('CK','库克群岛','Cook Islands'),
  ('CL','智利','Chile'),
  ('CM','喀麦隆','Cameroon'),
  ('CN','中国','China'),
  ('CO','哥伦比亚','Colombia'),
  ('CR','哥斯达黎加','Costa Rica'),
  ('CU','古巴','Cuba'),
  ('CV','佛得角','Cabo Verde'),
  ('CW','库拉索','Curaçao'),
  ('CX','圣诞岛','Christmas Island'),
  ('CY','塞浦路斯','Cyprus'),
  ('CZ','捷克','Czechia'),
  ('DE','德国','Germany'),
  ('DJ','吉布提','Djibouti'),
  ('DK','丹麦','Denmark'),
  ('DM','多米尼克','Dominica'),
  ('DO','多米尼加共和国','Dominican Republic'),
  ('DZ','阿尔及利亚','Algeria'),
  ('EC','厄瓜多尔','Ecuador'),
  ('EE','爱沙尼亚','Estonia'),
  ('EG','埃及','Egypt'),
  ('EH','西撒哈拉','Western Sahara'),
  ('ER','厄立特里亚','Eritrea'),
  ('ES','西班牙','Spain'),
  ('ET','埃塞俄比亚','Ethiopia'),
  ('FI','芬兰','Finland'),
  ('FJ','斐济','Fiji'),
  ('FK','福克兰群岛(马尔维纳斯)','Falkland Islands (Malvinas)'),
  ('FM','密克罗尼西亚','Micronesia, Federated States of'),
  ('FO','法罗群岛','Faroe Islands'),
  ('FR','法国','France'),
  ('GA','加蓬','Gabon'),
  ('GB','英国','United Kingdom'),
  ('GD','格林纳达','Grenada'),
  ('GE','格鲁吉亚','Georgia'),
  ('GF','法属圭亚那','French Guiana'),
  ('GG','根西岛','Guernsey'),
  ('GH','加纳','Ghana'),
  ('GI','直布罗陀','Gibraltar'),
  ('GL','格陵兰','Greenland'),
  ('GM','冈比亚','Gambia'),
  ('GN','几内亚','Guinea'),
  ('GP','瓜德罗普','Guadeloupe'),
  ('GQ','赤道几内亚','Equatorial Guinea'),
  ('GR','希腊','Greece'),
  ('GS','南乔治亚岛和南桑德韦奇岛','South Georgia and the South Sandwich Islands'),
  ('GT','瓜地马拉','Guatemala'),
  ('GU','关岛','Guam'),
  ('GW','几内亚比绍','Guinea-Bissau'),
  ('GY','圭亚那','Guyana'),
  ('HK','香港','Hong Kong'),
  ('HM','赫德岛与麦克唐纳群岛','Heard Island and McDonald Islands'),
  ('HN','洪都拉斯','Honduras'),
  ('HR','克罗地亚','Croatia'),
  ('HT','海地','Haiti'),
  ('HU','匈牙利','Hungary'),
  ('ID','印度尼西亚','Indonesia'),
  ('IE','爱尔兰','Ireland'),
  ('IL','以色列','Israel'),
  ('IM','曼岛','Isle of Man'),
  ('IN','印度','India'),
  ('IO','英属印度洋领地','British Indian Ocean Territory'),
  ('IQ','伊拉克','Iraq'),
  ('IR','伊朗伊斯兰共和国','Iran, Islamic Republic of'),
  ('IS','冰岛','Iceland'),
  ('IT','意大利','Italy'),
  ('JE','泽西岛','Jersey'),
  ('JM','牙买加','Jamaica'),
  ('JO','约旦','Jordan'),
  ('JP','日本','Japan'),
  ('KE','肯尼亚','Kenya'),
  ('KG','吉尔吉斯坦','Kyrgyzstan'),
  ('KH','柬埔塞','Cambodia'),
  ('KI','基里巴斯','Kiribati'),
  ('KM','科摩罗','Comoros'),
  ('KN','圣基茨和尼维斯','Saint Kitts and Nevis'),
  ('KP','朝鲜民主主义人民共和国','Korea, Democratic People''s Republic of'),
  ('KR','大韩民国','Korea, Republic of'),
  ('KW','科威特','Kuwait'),
  ('KY','开曼群岛','Cayman Islands'),
  ('KZ','哈萨克斯坦','Kazakhstan'),
  ('LA','老挝人民民主共和国','Lao People''s Democratic Republic'),
  ('LB','黎巴嫩','Lebanon'),
  ('LC','圣路西亚','Saint Lucia'),
  ('LI','列支敦士登','Liechtenstein'),
  ('LK','斯里兰卡','Sri Lanka'),
  ('LR','利比里亚','Liberia'),
  ('LS','莱索托','Lesotho'),
  ('LT','立陶宛','Lithuania'),
  ('LU','卢森堡','Luxembourg'),
  ('LV','拉脱维亚','Latvia'),
  ('LY','利比亚','Libya'),
  ('MA','摩洛哥','Morocco'),
  ('MC','摩纳哥','Monaco'),
  ('MD','摩尔多瓦共和国','Moldova, Republic of'),
  ('ME','黑山','Montenegro'),
  ('MF','法属圣马丁','Saint Martin (French part)'),
  ('MG','马达加斯加','Madagascar'),
  ('MH','马绍尔群岛','Marshall Islands'),
  ('MK','北马其顿','North Macedonia'),
  ('ML','马里','Mali'),
  ('MM','缅甸','Myanmar'),
  ('MN','蒙古','Mongolia'),
  ('MO','澳门','Macao'),
  ('MP','北马里亚纳群岛','Northern Mariana Islands'),
  ('MQ','马提尼克','Martinique'),
  ('MR','毛里塔尼亚','Mauritania'),
  ('MS','蒙塞拉特岛','Montserrat'),
  ('MT','马尔他','Malta'),
  ('MU','毛里求斯','Mauritius'),
  ('MV','马尔代夫','Maldives'),
  ('MW','马拉维','Malawi'),
  ('MX','墨西哥','Mexico'),
  ('MY','马来西亚','Malaysia'),
  ('MZ','莫桑比克','Mozambique'),
  ('NA','纳米比亚','Namibia'),
  ('NC','新喀里多尼亚','New Caledonia'),
  ('NE','尼日尔','Niger'),
  ('NF','诺福克岛','Norfolk Island'),
  ('NG','尼日利亚','Nigeria'),
  ('NI','尼加拉瓜','Nicaragua'),
  ('NL','荷兰','Netherlands'),
  ('NO','挪威','Norway'),
  ('NP','尼泊尔','Nepal'),
  ('NR','瑙鲁','Nauru'),
  ('NU','纽埃','Niue'),
  ('NZ','新西兰','New Zealand'),
  ('OM','阿曼','Oman'),
  ('PA','巴拿马','Panama'),
  ('PE','秘鲁','Peru'),
  ('PF','法属玻利尼西亚','French Polynesia'),
  ('PG','巴布亚新几内亚','Papua New Guinea'),
  ('PH','菲律宾','Philippines'),
  ('PK','巴基斯坦','Pakistan'),
  ('PL','波兰','Poland'),
  ('PM','圣皮埃尔和密克隆','Saint Pierre and Miquelon'),
  ('PN','皮特克恩','Pitcairn'),
  ('PR','波多黎各','Puerto Rico'),
  ('PS','巴勒斯坦','Palestine, State of'),
  ('PT','葡萄牙','Portugal'),
  ('PW','帕劳','Palau'),
  ('PY','巴拉圭','Paraguay'),
  ('QA','卡塔尔','Qatar'),
  ('RE','留尼汪','Réunion'),
  ('RO','罗马尼亚','Romania'),
  ('RS','塞尔维亚','Serbia'),
  ('RU','俄罗斯','Russian Federation'),
  ('RW','卢旺达','Rwanda'),
  ('SA','沙特阿拉伯','Saudi Arabia'),
  ('SB','所罗门群岛','Solomon Islands'),
  ('SC','塞舌尔','Seychelles'),
  ('SD','苏丹','Sudan'),
  ('SE','瑞典','Sweden'),
  ('SG','新加坡','Singapore'),
  ('SH','圣赫勒拿-阿森松-特里斯坦达库尼亚','Saint Helena, Ascension and Tristan da Cunha'),
  ('SI','斯洛文尼亚','Slovenia'),
  ('SJ','斯瓦尔巴特和扬马延岛','Svalbard and Jan Mayen'),
  ('SK','斯洛伐克','Slovakia'),
  ('SL','塞拉利昂','Sierra Leone'),
  ('SM','圣马力诺市','San Marino'),
  ('SN','塞内加尔','Senegal'),
  ('SO','索马里','Somalia'),
  ('SR','苏里南','Suriname'),
  ('SS','南苏丹','South Sudan'),
  ('ST','圣多美和普林西比','Sao Tome and Principe'),
  ('SV','萨尔瓦多','El Salvador'),
  ('SX','荷属圣马丁','Sint Maarten (Dutch part)'),
  ('SY','阿拉伯叙利亚共和国','Syrian Arab Republic'),
  ('SZ','斯威士兰','Eswatini'),
  ('TC','特克斯和凯科斯群岛','Turks and Caicos Islands'),
  ('TD','乍得','Chad'),
  ('TF','法属南半球领地','French Southern Territories'),
  ('TG','多哥','Togo'),
  ('TH','泰国','Thailand'),
  ('TJ','塔吉克斯坦','Tajikistan'),
  ('TK','托克劳','Tokelau'),
  ('TL','东帝汶','Timor-Leste'),
  ('TM','土库曼斯坦','Turkmenistan'),
  ('TN','突尼斯','Tunisia'),
  ('TO','汤加','Tonga'),
  ('TR','土耳其','Türkiye'),
  ('TT','特里尼达和多巴哥','Trinidad and Tobago'),
  ('TV','图瓦卢','Tuvalu'),
  ('TW','中国台湾省','Taiwan, Province of China'),
  ('TZ','坦桑尼亚','Tanzania, United Republic of'),
  ('UA','乌克兰','Ukraine'),
  ('UG','乌干达','Uganda'),
  ('UM','美国本土外小岛屿','United States Minor Outlying Islands'),
  ('US','美国','United States'),
  ('UY','乌拉圭','Uruguay'),
  ('UZ','乌兹别克斯坦','Uzbekistan'),
  ('VA','梵地冈','Holy See (Vatican City State)'),
  ('VC','圣文森特和格林纳丁斯','Saint Vincent and the Grenadines'),
  ('VE','委内瑞拉玻利瓦尔共和国','Venezuela, Bolivarian Republic of'),
  ('VG','英属维尔京群岛','Virgin Islands, British'),
  ('VI','美属维尔京群岛','Virgin Islands, U.S.'),
  ('VN','越南','Viet Nam'),
  ('VU','瓦努阿图','Vanuatu'),
  ('WF','瓦利斯和富图纳','Wallis and Futuna'),
  ('WS','萨摩亚','Samoa'),
  ('YE','也门','Yemen'),
  ('YT','马约特','Mayotte'),
  ('ZA','南非','South Africa'),
  ('ZM','赞比亚','Zambia'),
  ('ZW','津巴布韦','Zimbabwe')
ON CONFLICT (country_code) DO NOTHING;

ALTER TABLE customer_master ADD COLUMN IF NOT EXISTS country_region_code varchar(12);
ALTER TABLE customer_address ADD COLUMN IF NOT EXISTS country_region_code varchar(12);
COMMENT ON COLUMN customer_master.country_region_code IS '客户所在国家/地区编码，关联 sys_country_region；与订单目的地分开';
COMMENT ON COLUMN customer_address.country_region_code IS '该地址的国家/地区编码，关联 sys_country_region；与订单目的地分开';
COMMENT ON COLUMN customer_master.country_region IS '客户所在国家/地区名称快照及历史手填值；无法唯一匹配时保留原文';
COMMENT ON COLUMN customer_address.country_region IS '地址国家/地区名称快照及历史手填值；无法唯一匹配时保留原文';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='customer_master'::regclass AND conname='fk_customer_country_region') THEN
   ALTER TABLE customer_master ADD CONSTRAINT fk_customer_country_region FOREIGN KEY (country_region_code) REFERENCES sys_country_region(country_code);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='customer_address'::regclass AND conname='fk_customer_address_country_region') THEN
   ALTER TABLE customer_address ADD CONSTRAINT fk_customer_address_country_region FOREIGN KEY (country_region_code) REFERENCES sys_country_region(country_code);
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_customer_country_region ON customer_master(country_region_code);
CREATE INDEX IF NOT EXISTS idx_customer_address_country_region ON customer_address(country_region_code);

-- 只匹配唯一的中文名、英文名或编码；不确定的旧值继续保留，交由用户在编辑时选择。
WITH matches AS (
 SELECT c.customer_id, min(r.country_code) AS code FROM customer_master c
 JOIN sys_country_region r ON lower(trim(c.country_region)) IN (lower(r.name_zh),lower(r.name_en),lower(r.country_code))
 WHERE c.country_region_code IS NULL AND nullif(trim(c.country_region),'') IS NOT NULL
 GROUP BY c.customer_id HAVING count(*)=1
) UPDATE customer_master c SET country_region_code=m.code FROM matches m WHERE c.customer_id=m.customer_id;
WITH matches AS (
 SELECT a.address_id, min(r.country_code) AS code FROM customer_address a
 JOIN sys_country_region r ON lower(trim(a.country_region)) IN (lower(r.name_zh),lower(r.name_en),lower(r.country_code))
 WHERE a.country_region_code IS NULL AND nullif(trim(a.country_region),'') IS NOT NULL
 GROUP BY a.address_id HAVING count(*)=1
) UPDATE customer_address a SET country_region_code=m.code FROM matches m WHERE a.address_id=m.address_id;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000301,'国家地区查询',5,120,'#','','N','Y','F','0','0','system:country:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:country:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000302,'国家地区新增',5,121,'#','','N','Y','F','0','0','system:country:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:country:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000303,'国家地区修改与启停',5,122,'#','','N','Y','F','0','0','system:country:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:country:edit');
COMMIT;

-- 客户删除权限，单独授予角色。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000204,'客户删除',5,113,'#','','N','Y','F','0','0','system:customer:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:remove');

CREATE TABLE IF NOT EXISTS sys_size (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 code varchar(40) NOT NULL UNIQUE DEFAULT ('SZ' || replace(gen_random_uuid()::text,'-','')),
 name varchar(100) NOT NULL CHECK(length(trim(name))>0),
 name_en varchar(200) NOT NULL DEFAULT '',
 sort_order integer NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(2000) NOT NULL DEFAULT '',
 create_by bigint, create_time timestamptz NOT NULL DEFAULT now(),
 update_by bigint, update_time timestamptz
);
COMMENT ON TABLE sys_size IS '尺码基础档案，停用保留历史引用';
COMMENT ON COLUMN sys_size.id IS '稳定主键';
COMMENT ON COLUMN sys_size.code IS '尺码业务编码，自动生成时分配当前最小可用序号';
COMMENT ON COLUMN sys_size.name IS '尺码名称';
COMMENT ON COLUMN sys_size.name_en IS '英文名称';
COMMENT ON COLUMN sys_size.sort_order IS '显示排序';
COMMENT ON COLUMN sys_size.status IS '状态：0启用，1停用';
COMMENT ON COLUMN sys_size.remark IS '备注';
COMMENT ON COLUMN sys_size.create_by IS '创建人用户ID';
COMMENT ON COLUMN sys_size.create_time IS '创建时间';
COMMENT ON COLUMN sys_size.update_by IS '更新人用户ID';
COMMENT ON COLUMN sys_size.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS sys_size_group (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 code varchar(40) NOT NULL UNIQUE DEFAULT ('SG' || replace(gen_random_uuid()::text,'-','')),
 name varchar(100) NOT NULL CHECK(length(trim(name))>0),
 name_en varchar(200) NOT NULL DEFAULT '',
 sort_order integer NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(2000) NOT NULL DEFAULT '',
 create_by bigint, create_time timestamptz NOT NULL DEFAULT now(),
 update_by bigint, update_time timestamptz
);
COMMENT ON TABLE sys_size_group IS '尺码组基础档案，停用保留历史引用';
COMMENT ON COLUMN sys_size_group.id IS '稳定主键';
COMMENT ON COLUMN sys_size_group.code IS '系统生成的稳定编号';
COMMENT ON COLUMN sys_size_group.name IS '尺码组名称';
COMMENT ON COLUMN sys_size_group.name_en IS '英文名称';
COMMENT ON COLUMN sys_size_group.sort_order IS '显示排序';
COMMENT ON COLUMN sys_size_group.status IS '状态：0启用，1停用';
COMMENT ON COLUMN sys_size_group.remark IS '备注';
COMMENT ON COLUMN sys_size_group.create_by IS '创建人用户ID';
COMMENT ON COLUMN sys_size_group.create_time IS '创建时间';
COMMENT ON COLUMN sys_size_group.update_by IS '更新人用户ID';
COMMENT ON COLUMN sys_size_group.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS sys_size_group_item (
 group_id bigint NOT NULL REFERENCES sys_size_group(id),
 size_id bigint NOT NULL REFERENCES sys_size(id),
 sort_order integer NOT NULL,
 PRIMARY KEY(group_id,size_id),
 UNIQUE(group_id,sort_order)
);
COMMENT ON TABLE sys_size_group_item IS '尺码组有序尺码明细，同组禁止重复尺码';
COMMENT ON COLUMN sys_size_group_item.group_id IS '所属尺码组ID';
COMMENT ON COLUMN sys_size_group_item.size_id IS '尺码档案ID';
COMMENT ON COLUMN sys_size_group_item.sort_order IS '组内排序，与尺码档案默认排序独立';
-- 尺码及尺码组自动编码默认规则；重复执行不覆盖管理员已配置值。
INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码自动编码规则', 'system.code.size',
       '{"enabled":true,"prefix":"SZ","digits":4}', 'Y', now(),
       '尺码自动编码开关、编码前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.size');
INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_time, remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config), 0) + 1,
       '尺码组自动编码规则', 'system.code.sizeGroup',
       '{"enabled":true,"prefix":"SG","digits":4}', 'Y', now(),
       '尺码组自动编码开关、编号前缀及数字位数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.code.sizeGroup');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000401,'尺码查询',5,130,'#','','N','Y','F','0','0','system:size:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000402,'尺码新增',5,131,'#','','N','Y','F','0','0','system:size:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000403,'尺码修改与启停',5,132,'#','','N','Y','F','0','0','system:size:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000404,'尺码删除',5,133,'#','','N','Y','F','0','0','system:size:delete','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size:delete');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000411,'尺码组查询',5,140,'#','','N','Y','F','0','0','system:size-group:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size-group:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000412,'尺码组新增',5,141,'#','','N','Y','F','0','0','system:size-group:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size-group:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000413,'尺码组修改与启停',5,142,'#','','N','Y','F','0','0','system:size-group:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size-group:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000414,'尺码组删除',5,143,'#','','N','Y','F','0','0','system:size-group:delete','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:size-group:delete');

-- 已有尺码编辑权限的角色同步获得删除权限。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT edit_rm.role_id,delete_menu.menu_id
FROM sys_role_menu edit_rm
JOIN sys_menu edit_menu ON edit_menu.menu_id=edit_rm.menu_id
JOIN sys_menu delete_menu ON delete_menu.perms=CASE edit_menu.perms
 WHEN 'system:size:edit' THEN 'system:size:delete'
 WHEN 'system:size-group:edit' THEN 'system:size-group:delete'
END
WHERE edit_menu.perms IN ('system:size:edit','system:size-group:edit')
ON CONFLICT DO NOTHING;

-- 权限树整合：保留既有ID与角色关联，停用未接入的框架菜单，不删除记录。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000001,0,'订单',10,'order','','N','Y','M','0','0','','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000002,1761600000000000002,'系统维护权限（底座接口）',10,'maintenance','','N','Y','M','0','0','','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000010,1761600000000000002,'标签管理',10,'tag','','N','Y','C','0','0','system:tag:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000011,1761600000000000002,'国家/地区',10,'country-region','','N','Y','C','0','0','system:country:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000012,1761600000000000002,'尺码管理',10,'size','','N','Y','C','0','0','system:size:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000013,1761600000000000002,'尺码组管理',10,'size-group','','N','Y','C','0','0','system:size-group:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762500000000000014,1762500000000000001,'客户档案',10,'customer','','N','Y','C','0','0','system:customer:list','#',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,status='0';
UPDATE sys_menu SET parent_id=1762500000000000010 WHERE perms LIKE 'system:tag:%' AND menu_id<>1762500000000000010;
UPDATE sys_menu SET parent_id=1762500000000000011 WHERE perms LIKE 'system:country:%' AND menu_id<>1762500000000000011;
UPDATE sys_menu SET parent_id=1762500000000000012 WHERE perms LIKE 'system:size:%' AND menu_id<>1762500000000000012;
UPDATE sys_menu SET parent_id=1762500000000000013 WHERE perms LIKE 'system:size-group:%' AND menu_id<>1762500000000000013;
UPDATE sys_menu SET parent_id=1762500000000000014 WHERE perms LIKE 'system:customer:%' AND menu_id<>1762500000000000014;
UPDATE sys_menu SET parent_id=1762500000000000002 WHERE menu_id IN (1761400000000000102,1761400000000000105,1761400000000000106,1761400000000000108,1761400000000000118,1761400000000000133,1761400000000000123,1761400000000000109,1761400000000000113);
-- 重复的文件配置入口合并到文件配置管理节点，保留操作权限ID。
UPDATE sys_menu SET parent_id=1761400000000000133 WHERE parent_id=1761400000000000118 AND perms LIKE 'system:ossConfig:%';
UPDATE sys_menu SET menu_name='人员信息' WHERE perms='system:person:list';
UPDATE sys_menu SET menu_name='建筑档案' WHERE perms='system:building:list';
WITH RECURSIVE retired AS (SELECT menu_id FROM sys_menu WHERE menu_id IN (1761400000000000001,1761400000000000002,1761400000000000003,1761400000000000004,1761400000000000005,1761400000000000006,1761400000000011616,1761400000000011618) UNION ALL SELECT m.menu_id FROM sys_menu m JOIN retired p ON m.parent_id=p.menu_id) UPDATE sys_menu SET status='1',visible='1' WHERE menu_id IN (SELECT menu_id FROM retired);
-- 已有授权仍可到达合并后的父目录；不授予额外按钮权限。
WITH RECURSIVE ancestors AS (
 SELECT rm.role_id,m.parent_id AS menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.status='0' AND m.parent_id<>0
 UNION
 SELECT a.role_id,m.parent_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE m.status='0' AND m.parent_id<>0
)
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT a.role_id,a.menu_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id AND m.status='0'
WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu x WHERE x.role_id=a.role_id AND x.menu_id=a.menu_id);

UPDATE sys_menu SET status='0',visible='0' WHERE menu_id=1761400000000000001; UPDATE sys_menu SET parent_id=1761400000000000001 WHERE parent_id=1761600000000000002; INSERT INTO sys_role_menu(role_id,menu_id) SELECT role_id,1761400000000000001 FROM sys_role_menu WHERE menu_id=1761600000000000002 ON CONFLICT DO NOTHING; UPDATE sys_menu SET status='1',visible='1' WHERE menu_id=1761600000000000002;

-- 系统管理直接承载启用的维护功能。
UPDATE sys_menu SET parent_id=1761400000000000001 WHERE parent_id=1762500000000000002;
UPDATE sys_menu SET status='1',visible='1' WHERE menu_id=1762500000000000002;


-- 仓库管理及仓库布局：验收后归并（2026-11-05），包含应用角色授权及完整权限树。

-- 已验收功能原增量：仓库管理_20261104.sql
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


-- 已验收功能原增量：库位管理_20261105.sql
-- 仓库布局：库区、货架、货位三级位置档案及仓库入库定位深度设置。
BEGIN;
ALTER TABLE inv_warehouse ADD COLUMN IF NOT EXISTS inbound_location_level smallint NOT NULL DEFAULT 2 CHECK (inbound_location_level BETWEEN 0 AND 3);
COMMENT ON COLUMN inv_warehouse.inbound_location_level IS '入库定位最小深度：0=仓库，1=库区，2=货架，3=货位；默认货架级。';
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
COMMENT ON TABLE inv_warehouse_location IS '仓库内库区、货架、货位三级位置档案；AGV调度暂不在本表实现。';
COMMENT ON COLUMN inv_warehouse_location.location_id IS '仓储位置稳定主键，供库存单据引用。';
COMMENT ON COLUMN inv_warehouse_location.warehouse_id IS '所属仓库ID。';
COMMENT ON COLUMN inv_warehouse_location.parent_location_id IS '直接父级位置ID；库区为空，货架挂库区，货位挂货架。';
COMMENT ON COLUMN inv_warehouse_location.location_code IS '仓库内唯一位置编码，作为稳定人工识别码。';
COMMENT ON COLUMN inv_warehouse_location.location_name IS '位置名称。';
COMMENT ON COLUMN inv_warehouse_location.location_level IS '位置层级：1=库区，2=货架，3=货位。';
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
-- 管理员建表时从既有业务表识别应用角色，不能取管理员所有的仓库表角色或写死角色名。
DO $$ DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 IF app_role IS NULL THEN RAISE EXCEPTION '无法识别应用数据库角色'; END IF;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.inv_warehouse_location TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.inv_warehouse_location_location_id_seq TO %I',app_role);
END $$;
-- 应用连接用户在 application-dev.yml 中配置为 zhizhun；表所有者可能不同，需单独授权。

-- 仓库布局接口权限；前端功能入口复用库存模块的本地功能卡片目录，不额外创建左侧菜单节点。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000111,'仓库布局查询',5,150,'#','','N','Y','F','0','0','inventory:location:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000112,'仓库布局详情',5,151,'#','','N','Y','F','0','0','inventory:location:query','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:query');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000113,'仓库布局新增',5,152,'#','','N','Y','F','0','0','inventory:location:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000114,'仓库布局编辑与状态',5,153,'#','','N','Y','F','0','0','inventory:location:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762600000000000115,'仓库布局删除',5,154,'#','','N','Y','F','0','0','inventory:location:remove','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:location:remove');

-- 更新已有权限的中文名称，不改变权限码和角色授权。
UPDATE sys_menu SET menu_name=CASE perms
 WHEN 'inventory:location:list' THEN '仓库布局查询'
 WHEN 'inventory:location:query' THEN '仓库布局详情'
 WHEN 'inventory:location:add' THEN '仓库布局新增'
 WHEN 'inventory:location:edit' THEN '仓库布局编辑与状态'
 WHEN 'inventory:location:remove' THEN '仓库布局删除' END
WHERE perms IN ('inventory:location:list','inventory:location:query','inventory:location:add','inventory:location:edit','inventory:location:remove');
COMMIT;


-- 仓储权限目录修复：复用既有权限ID与权限码，不给现有角色新增操作授权。
-- Inventory menu repair: preserve existing permission identities and role assignments.
BEGIN;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1762800000000000001,0,'库存',30,'stock','','N','Y','M','0','0','','DatabaseOutlined',now())
ON CONFLICT(menu_id) DO UPDATE SET parent_id=0,menu_name='库存',path='stock',menu_type='M',visible='0',status='0';

-- list权限本身作为页面节点，原有勾选ID不变，其余操作挂回各自页面。
UPDATE sys_menu SET parent_id=1762800000000000001,menu_name='仓库管理',menu_type='C',path='warehouse',component='stock/WarehouseManage',icon='DatabaseOutlined',order_num=10,visible='0',status='0'
WHERE perms='inventory:warehouse:list';
UPDATE sys_menu SET parent_id=1762800000000000001,menu_name='仓库布局',menu_type='C',path='layout',component='stock/WarehouseLayoutManage',icon='EnvironmentOutlined',order_num=20,visible='0',status='0'
WHERE perms='inventory:location:list';
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='inventory:warehouse:list'),menu_type='F',visible='0',status='0'
WHERE perms IN ('inventory:warehouse:query','inventory:warehouse:add','inventory:warehouse:edit','inventory:warehouse:remove');
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='inventory:location:list'),menu_type='F',visible='0',status='0'
WHERE perms IN ('inventory:location:query','inventory:location:add','inventory:location:edit','inventory:location:remove');

-- 为已持有仓储页面/操作的角色补齐可达祖先，不新增任何写权限。
WITH RECURSIVE ancestors AS (
 SELECT rm.role_id,m.parent_id AS menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
 WHERE m.perms IN ('inventory:warehouse:list','inventory:warehouse:query','inventory:warehouse:add','inventory:warehouse:edit','inventory:warehouse:remove','inventory:location:list','inventory:location:query','inventory:location:add','inventory:location:edit','inventory:location:remove')
 UNION
 SELECT a.role_id,m.parent_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE m.parent_id<>0
)
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT a.role_id,a.menu_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id
WHERE a.menu_id<>0 AND NOT EXISTS(SELECT 1 FROM sys_role_menu r WHERE r.role_id=a.role_id AND r.menu_id=a.menu_id);
COMMIT;

-- 计量单位初始档案：按业务编码关联，重复执行不覆盖已有数据。
-- 时间单位以秒为参照，1天为86400秒；时长业务以整数秒计算。
BEGIN;
CREATE TABLE IF NOT EXISTS base_unit (
 unit_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 unit_code varchar(40) NOT NULL UNIQUE,
 unit_name varchar(80) NOT NULL,
 symbol varchar(20) NOT NULL DEFAULT '',
 dimension varchar(20) NOT NULL CHECK(dimension IN ('length','mass','area','volume','time','count','other')),
 decimal_places integer NOT NULL DEFAULT 3 CHECK(decimal_places BETWEEN 0 AND 8),
 base_unit_id bigint REFERENCES base_unit(unit_id),
 factor numeric(30,12) NOT NULL DEFAULT 1 CHECK(factor>0),
 sort_order integer NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(500) NOT NULL DEFAULT '',
 del_flag char(1) NOT NULL DEFAULT '0' CHECK(del_flag IN ('0','1')),
 create_by bigint, create_time timestamp NOT NULL DEFAULT now(), update_by bigint, update_time timestamp,
 CHECK(base_unit_id IS NULL AND factor=1 OR base_unit_id IS NOT NULL AND base_unit_id<>unit_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_base_unit_name ON base_unit(lower(unit_name)) WHERE del_flag='0';
COMMENT ON TABLE base_unit IS '计量单位公共档案；固定换算仅限同维度同参照单位，物料专属包装或重量长度换算另行维护';
COMMENT ON COLUMN base_unit.unit_id IS '单位稳定主键，业务外键使用本ID';
COMMENT ON COLUMN base_unit.unit_code IS '单位唯一业务编码，删除后不复用';
COMMENT ON COLUMN base_unit.unit_name IS '计量单位中文名称';
COMMENT ON COLUMN base_unit.symbol IS '单位符号，如m、kg，仅供显示';
COMMENT ON COLUMN base_unit.dimension IS '计量维度：length长度/mass质量/area面积/volume体积/time时间/count计数/other其他';
COMMENT ON COLUMN base_unit.decimal_places IS '数量展示及换算结果小数位数，0至8';
COMMENT ON COLUMN base_unit.base_unit_id IS '固定换算参照单位，关联base_unit.unit_id；空表示独立参照单位';
COMMENT ON COLUMN base_unit.factor IS '1本单位折合多少参照单位，精度numeric(30,12)，必须大于0；独立单位固定为1';
COMMENT ON COLUMN base_unit.sort_order IS '显示排序，数字越小越靠前';
COMMENT ON COLUMN base_unit.status IS '状态：0启用，1停用；停用保留历史引用';
COMMENT ON COLUMN base_unit.remark IS '业务备注，不存凭据或敏感信息';
COMMENT ON COLUMN base_unit.del_flag IS '逻辑删除标记：0正常，1已删除';
COMMENT ON COLUMN base_unit.create_by IS '创建员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_unit.create_time IS '创建时间';
COMMENT ON COLUMN base_unit.update_by IS '最后修改员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_unit.update_time IS '最后修改时间';

COMMENT ON SEQUENCE base_unit_unit_id_seq IS '生成计量单位稳定主键的序列';
INSERT INTO base_unit(unit_code,unit_name,symbol,dimension,decimal_places,base_unit_id,factor,sort_order,status,remark)
VALUES ('01','米','m','length',3,NULL,1,0,'0',''),('04','秒','s','time',0,NULL,1,0,'0','')
ON CONFLICT DO NOTHING;
-- 已有同编码档案必须与预期参照一致，否则停止初始化，避免静默缺失或错误关联。
DO $$
BEGIN
 IF (SELECT count(*) FROM base_unit WHERE (unit_code,unit_name,dimension) IN (('01','米','length'),('04','秒','time')) AND base_unit_id IS NULL AND status='0' AND del_flag='0') <> 2 THEN
  RAISE EXCEPTION '计量单位初始参照米或秒与已有档案冲突，请先核对';
 END IF;
END $$;
-- 辅助单位只关联编码、名称和维度均匹配的启用参照单位，避免误绑定。
INSERT INTO base_unit(unit_code,unit_name,symbol,dimension,decimal_places,base_unit_id,factor,sort_order,status,remark)
SELECT v.code,v.name,v.symbol,v.dimension,v.decimals,r.unit_id,v.factor,0,'0',''
FROM (VALUES
 ('02','厘米','cm','length',1,'01','米',0.01::numeric),
 ('03','毫米','mm','length',0,'01','米',0.001::numeric),
 ('05','分钟','min','time',0,'04','秒',60::numeric),
 ('06','小时','h','time',0,'04','秒',3600::numeric),
 ('07','天','d','time',0,'04','秒',86400::numeric)
) AS v(code,name,symbol,dimension,decimals,root_code,root_name,factor)
JOIN base_unit r ON r.unit_code=v.root_code AND r.unit_name=v.root_name AND r.dimension=v.dimension AND r.base_unit_id IS NULL AND r.status='0' AND r.del_flag='0'
ON CONFLICT DO NOTHING;
COMMIT;
-- 开发库已有权限表的所有者作为应用角色；避免以初始化管理员身份建表后应用无权访问。
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(c.relowner) INTO app_role FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname='sys_tag';
 IF app_role IS NULL THEN RAISE EXCEPTION '未找到应用角色，请先创建sys_tag'; END IF;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.base_unit TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.base_unit_unit_id_seq TO %I',app_role);
END $$;

-- 计量单位权限：保留已有权限ID，并补齐角色权限树祖先。
BEGIN;
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762700000000000000,'计量单位',1761400000000000001,160,'unit','system/UnitManage','N','Y','C','0','0','system:unit:list','#',now() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:unit:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT x.id,x.name,(SELECT menu_id FROM sys_menu WHERE perms='system:unit:list'),x.sort,'#','','N','Y','F','0','0',x.perms,'#',now() FROM (VALUES
(1762700000000000001::bigint,'计量单位新增',161,'system:unit:add'),(1762700000000000002::bigint,'计量单位修改与启停',162,'system:unit:edit'),(1762700000000000003::bigint,'计量单位删除',163,'system:unit:remove')) AS x(id,name,sort,perms) WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms=x.perms);
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='计量单位',menu_type='C',path='unit',component='system/UnitManage',visible='0',status='0' WHERE perms='system:unit:list';
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='system:unit:list'),menu_type='F',visible='0',status='0' WHERE perms IN ('system:unit:add','system:unit:edit','system:unit:remove');
WITH RECURSIVE ancestors AS (
 SELECT rm.role_id,m.parent_id AS menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms IN ('system:unit:list','system:unit:add','system:unit:edit','system:unit:remove')
 UNION SELECT a.role_id,m.parent_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE m.parent_id<>0
)
INSERT INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT a.role_id,a.menu_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE a.menu_id<>0 AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=a.role_id AND rm.menu_id=a.menu_id);
COMMIT;
