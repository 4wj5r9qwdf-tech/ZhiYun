-- 文件用途：为“主建表”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“主建表”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 设计思路：以可重复执行 DDL 和事务控制明确迁移顺序；数据含义以实际 SQL 定义为准。
-- 实现方法：按脚本声明顺序创建/调整表字段、约束、索引、权限并写数据库 COMMENT。
-- 关键约束：不得改变已有数据含义或绕过脚本约束。
-- 依赖与引用：sql/主建表.sql；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
-- 初始开发人员：JY（织云项目自研内容）
-- 初始开发时间：待追溯（不以补录时间代替原始开发时间）
-- 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
-- 最近修改人员：Codex（智能体）
-- AI辅助工具：Codex（智能体）
-- 最近修改时间：2026-11-21
-- 修改标记：ZY-ERP-20261121-注释审查；补齐 sys_building.plant_name 字段数据库注释
-- 既有测试记录（2026-11-20）：
--   测试时间：本次整理未执行功能测试
--   测试人员：本次整理未执行
--   测试状态：待功能回归；历史测试记录待追溯
--   测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
-- 最近测试时间：2026-11-21
-- 测试人员：Codex（智能体）
-- 测试状态：主建表字段注释静态审计通过；未执行完整建表脚本
-- 测试范围与记录位置：51张表、516个字段候选；Docs/功能任务/ZY-ERP-20261121-注释与数值精度审查.md
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
-- 更新：2026-11-21（补齐 sys_building.plant_name 字段数据库注释）
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
-- 2. sys_role 历史兼容字段：组织节点 ID 集合
--    当前系统的自定义组织范围由 data_scope='2' 与 sys_role_org 关联表承载。
--    data_scope_org_ids 为历史迁移保留列，当前应用代码不读写；不要用它作为现行权限配置来源。
--    （2026-10-30 dept→org 重命名后保留线上列名 data_scope_org_ids）
-- ------------------------------------------------------------
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope_org_ids varchar(2000) DEFAULT '';
COMMENT ON COLUMN sys_role.data_scope_org_ids IS '历史兼容字段：旧版指定组织节点ID集合；当前权限配置以sys_role_org关联表为准';

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
    dingtalk_account   varchar(128),                    -- 人员手工登记的钉钉号
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
ALTER TABLE sys_person_ext ADD COLUMN IF NOT EXISTS dingtalk_account varchar(128);
COMMENT ON COLUMN sys_person_ext.dingtalk_account IS '人员登记的钉钉号；仅作账号信息，钉钉身份绑定和消息推送待接入';
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
COMMENT ON COLUMN sys_building.plant_name   IS '建筑名称（如一号厂房、行政楼或一号宿舍楼）';
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
--     不预置业务功能或标签；业务内首次输入标签时动态登记功能归属
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

COMMENT ON TABLE sys_tag_module IS '动态标签归属功能目录；仅业务功能首次添加标签时自动登记，不预置空功能';
COMMENT ON COLUMN sys_tag_module.module_key IS '业务功能提供的稳定标签命名空间标识';
COMMENT ON COLUMN sys_tag_module.module_name IS '标签管理页面显示的业务功能名称；导航模块由前端功能目录映射';
COMMENT ON COLUMN sys_tag_module.create_by IS '首次登记模块的操作人用户ID';
COMMENT ON COLUMN sys_tag_module.create_time IS '功能归属首次登记时间';
COMMENT ON COLUMN sys_tag_module.update_by IS '最后修改功能名称的操作人用户ID';
COMMENT ON COLUMN sys_tag_module.update_time IS '功能归属最后修改时间';

CREATE SEQUENCE IF NOT EXISTS sys_tag_group_id_seq;
COMMENT ON SEQUENCE sys_tag_group_id_seq IS '通用标签分组主键序列';

CREATE TABLE IF NOT EXISTS sys_tag_group (
    group_id     bigint        NOT NULL DEFAULT nextval('sys_tag_group_id_seq'),
    module_key   varchar(50)   NOT NULL,
    group_name   varchar(100)  NOT NULL,
    is_default   char(1)       NOT NULL DEFAULT '0',
    sort_order   integer       NOT NULL DEFAULT 10,
    create_by    bigint,
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    bigint,
    update_time  timestamp,
    CONSTRAINT sys_tag_group_pk PRIMARY KEY (group_id),
    CONSTRAINT sys_tag_group_module_fk FOREIGN KEY (module_key)
        REFERENCES sys_tag_module (module_key),
    CONSTRAINT sys_tag_group_id_module_uk UNIQUE (group_id, module_key),
    CONSTRAINT sys_tag_group_default_ck CHECK (is_default IN ('0','1'))
);

DO $$
DECLARE
    tag_table_owner text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO tag_table_owner
    FROM pg_class WHERE oid = 'sys_tag_group'::regclass;
    EXECUTE format('ALTER SEQUENCE sys_tag_group_id_seq OWNER TO %I', tag_table_owner);
END $$;
ALTER SEQUENCE sys_tag_group_id_seq OWNED BY sys_tag_group.group_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_module_name
    ON sys_tag_group (module_key, lower(group_name));
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_one_default
    ON sys_tag_group (module_key) WHERE is_default='1';
CREATE INDEX IF NOT EXISTS idx_sys_tag_group_module_order
    ON sys_tag_group (module_key, is_default DESC, sort_order, group_id);

COMMENT ON TABLE sys_tag_group IS '通用属性标签分组；分组按业务功能隔离，每个功能保留默认分组';
COMMENT ON COLUMN sys_tag_group.group_id IS '标签分组主键，由 PostgreSQL 序列生成';
COMMENT ON COLUMN sys_tag_group.module_key IS '标签分组所属业务功能标识';
COMMENT ON COLUMN sys_tag_group.group_name IS '标签分组名称；同功能内不区分大小写唯一';
COMMENT ON COLUMN sys_tag_group.is_default IS '是否为系统默认分组：0否/1是；默认分组不可删除或重命名';
COMMENT ON COLUMN sys_tag_group.sort_order IS '分组显示顺序；默认分组优先展示';
COMMENT ON COLUMN sys_tag_group.create_by IS '创建分组的操作人用户ID';
COMMENT ON COLUMN sys_tag_group.create_time IS '分组创建时间';
COMMENT ON COLUMN sys_tag_group.update_by IS '最后修改分组的操作人用户ID';
COMMENT ON COLUMN sys_tag_group.update_time IS '分组最后修改时间';

-- 为已有动态标签功能建立兜底分组；功能仍只在业务首次录入标签时登记。
INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time)
SELECT module_key,'默认分组','1',0,now() FROM sys_tag_module
ON CONFLICT (module_key, lower(group_name)) DO UPDATE SET is_default='1';

CREATE SEQUENCE IF NOT EXISTS sys_tag_id_seq;
COMMENT ON SEQUENCE sys_tag_id_seq IS '通用标签档案主键序列';

CREATE TABLE IF NOT EXISTS sys_tag (
    tag_id       bigint        NOT NULL DEFAULT nextval('sys_tag_id_seq'),
    module_key   varchar(50)   NOT NULL,
    tag_group_id bigint        NOT NULL,
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
        REFERENCES sys_tag_module (module_key),
    CONSTRAINT sys_tag_group_module_fk FOREIGN KEY (tag_group_id, module_key)
        REFERENCES sys_tag_group (group_id, module_key)
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

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_tag_name
    ON sys_tag (module_key, tag_group_id, lower(tag_name));
CREATE INDEX IF NOT EXISTS idx_sys_tag_module_status
    ON sys_tag (module_key, status, is_common);

COMMENT ON TABLE sys_tag IS '通用属性标签档案；按模块命名空间隔离';
COMMENT ON COLUMN sys_tag.tag_id IS '标签档案主键，由 PostgreSQL 序列生成';
COMMENT ON COLUMN sys_tag.module_key IS '标签命名空间功能标识';
COMMENT ON COLUMN sys_tag.tag_group_id IS '标签所属分组ID，必须与标签功能归属一致';
COMMENT ON COLUMN sys_tag.tag_name IS '标签名称；所属功能名称通过 module_key 关联 sys_tag_module 获取';
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

-- 标签分组表及其序列需要由应用角色访问；角色从既有标签档案所有者动态识别。
DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    IF app_role IS NULL THEN RAISE EXCEPTION '无法从 public.sys_tag 识别应用数据库角色'; END IF;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.sys_tag_group TO %I',app_role);
    EXECUTE format('GRANT USAGE,SELECT,UPDATE ON SEQUENCE public.sys_tag_group_id_seq TO %I',app_role);
END $$;

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
 category_id bigint,
 sales_representative_id bigint NOT NULL,
 status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
 remark varchar(2000),
 create_by bigint,
 create_time timestamp NOT NULL DEFAULT now(),
 update_by bigint,
 update_time timestamp
);
ALTER TABLE customer_master ADD COLUMN IF NOT EXISTS category_id bigint;
COMMENT ON TABLE customer_master IS '客户档案；供订单及样衣引用，停用不改变历史单据';
COMMENT ON COLUMN customer_master.customer_id IS '客户主键';
COMMENT ON COLUMN customer_master.customer_code IS '系统自动生成的稳定客户编号';
COMMENT ON COLUMN customer_master.customer_name IS '客户名称；允许同名，保存前提醒';
COMMENT ON COLUMN customer_master.unified_social_credit_code IS '统一社会信用代码；境外客户可空';
COMMENT ON COLUMN customer_master.customer_short_name IS '客户简称';
COMMENT ON COLUMN customer_master.country_region IS '国家地区，手动录入';
COMMENT ON COLUMN customer_master.category_id IS '客户档案归属的分类节点ID；空值表示未分类，查询和维护受人员分类权限限制';
COMMENT ON COLUMN customer_master.sales_representative_id IS '业务员，关联人员信息，不校验特定岗位';
COMMENT ON COLUMN customer_master.status IS '状态：0启用，1停用；停用仅禁止新增业务引用';
COMMENT ON COLUMN customer_master.remark IS '备注';
COMMENT ON COLUMN customer_master.create_by IS '创建人用户ID';
COMMENT ON COLUMN customer_master.create_time IS '创建时间';
COMMENT ON COLUMN customer_master.update_by IS '更新人用户ID';
COMMENT ON COLUMN customer_master.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_customer_master_name ON customer_master(lower(customer_name));
CREATE INDEX IF NOT EXISTS idx_customer_master_category_id ON customer_master(category_id);
COMMENT ON INDEX idx_customer_master_category_id IS '按客户分类筛选档案';
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
INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time)
VALUES('warehouse_master','默认分组','1',0,now())
ON CONFLICT(module_key,lower(group_name)) DO UPDATE SET is_default='1';
INSERT INTO sys_tag(module_key,tag_group_id,tag_name,is_common,status,remark)
SELECT 'warehouse_master',g.group_id,v.tag_name,'1','0','仓库类型常用标签（可继续添加自定义类型）'
FROM (VALUES ('成品仓'),('面料仓'),('辅料仓'),('消耗品仓'),('设备仓'),('样衣仓')) AS v(tag_name)
JOIN sys_tag_group g ON g.module_key='warehouse_master' AND g.is_default='1'
WHERE NOT EXISTS (SELECT 1 FROM sys_tag t WHERE t.module_key='warehouse_master' AND t.tag_group_id=g.group_id AND lower(t.tag_name)=lower(v.tag_name));

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

-- 分类管理（2026-11-06 用户验收通过）
CREATE TABLE IF NOT EXISTS base_category_scheme (
 scheme_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 scheme_code varchar(40) NOT NULL,
 scheme_name varchar(80) NOT NULL,
 max_depth integer NOT NULL DEFAULT 5 CHECK(max_depth BETWEEN 1 AND 20),
 sort_order integer NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(500) NOT NULL DEFAULT '',
 del_flag char(1) NOT NULL DEFAULT '0' CHECK(del_flag IN ('0','1')),
 create_by bigint, create_time timestamp NOT NULL DEFAULT now(), update_by bigint, update_time timestamp
);
-- 分类类型编码只要求有效类型内唯一；删除后可重建同编码类型。兼容已执行旧版约束。
ALTER TABLE base_category_scheme DROP CONSTRAINT IF EXISTS base_category_scheme_scheme_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_type_code_active ON base_category_scheme(scheme_code) WHERE del_flag='0';
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_scheme_name ON base_category_scheme(lower(scheme_name)) WHERE del_flag='0';
CREATE TABLE IF NOT EXISTS base_category (
 category_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 scheme_id bigint NOT NULL REFERENCES base_category_scheme(scheme_id),
 parent_id bigint REFERENCES base_category(category_id),
 category_code varchar(40) NOT NULL,
 category_name varchar(80) NOT NULL,
 sort_order integer NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(500) NOT NULL DEFAULT '',
 del_flag char(1) NOT NULL DEFAULT '0' CHECK(del_flag IN ('0','1')),
 create_by bigint, create_time timestamp NOT NULL DEFAULT now(), update_by bigint, update_time timestamp,
 CHECK(parent_id IS NULL OR parent_id<>category_id)
);
-- 客户档案可不分类；已分类客户必须引用真实分类节点，并阻止删除仍被客户使用的分类。
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_customer_master_category' AND conrelid='customer_master'::regclass) THEN
  ALTER TABLE customer_master ADD CONSTRAINT fk_customer_master_category
   FOREIGN KEY (category_id) REFERENCES base_category(category_id) ON DELETE RESTRICT;
 END IF;
END $$;
COMMENT ON CONSTRAINT fk_customer_master_category ON customer_master IS '客户分类引用保护；有客户使用时禁止物理删除分类节点';
-- 兼容已执行旧版增量：有效分类编码唯一；逻辑删除后可重新使用编码，历史记录仍保留原稳定ID。
ALTER TABLE base_category DROP CONSTRAINT IF EXISTS base_category_scheme_id_category_code_key;
DROP INDEX IF EXISTS uq_category_scheme_code_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_node_code_active ON base_category(scheme_id,category_code) WHERE del_flag='0';
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_sibling_name ON base_category(scheme_id,coalesce(parent_id,0),lower(category_name)) WHERE del_flag='0';
CREATE INDEX IF NOT EXISTS idx_category_parent ON base_category(parent_id);
COMMENT ON TABLE base_category_scheme IS '公共分类体系；物料和款式等按体系隔离，业务通过稳定体系编码引用';
COMMENT ON COLUMN base_category_scheme.scheme_id IS '分类体系稳定主键';
COMMENT ON COLUMN base_category_scheme.scheme_code IS '有效分类类型内唯一稳定业务编码，创建后不可修改；逻辑删除后可重新使用';
COMMENT ON COLUMN base_category_scheme.scheme_name IS '分类体系中文名称';
COMMENT ON COLUMN base_category_scheme.max_depth IS '允许的最大分类深度，根节点为第1级，范围1至20';
COMMENT ON COLUMN base_category_scheme.sort_order IS '显示排序，数字越小越靠前';
COMMENT ON COLUMN base_category_scheme.status IS '状态：0启用，1停用；停用保留历史引用';
COMMENT ON COLUMN base_category_scheme.remark IS '业务备注，不存凭据或敏感信息';
COMMENT ON COLUMN base_category_scheme.del_flag IS '旧版逻辑删除兼容标记：0正常，1历史已删除；当前空分类类型执行物理删除';
COMMENT ON COLUMN base_category_scheme.create_by IS '创建员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_category_scheme.create_time IS '创建时间';
COMMENT ON COLUMN base_category_scheme.update_by IS '最后修改员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_category_scheme.update_time IS '最后修改时间';
COMMENT ON TABLE base_category IS '公共多级分类节点，归属单个体系，业务以稳定节点ID引用';
COMMENT ON COLUMN base_category.category_id IS '分类节点稳定主键，业务引用本ID';
COMMENT ON COLUMN base_category.scheme_id IS '所属分类体系，关联base_category_scheme.scheme_id；创建后不可变更';
COMMENT ON COLUMN base_category.parent_id IS '上级分类，关联base_category.category_id；空表示根节点，禁止跨体系及循环';
COMMENT ON COLUMN base_category.category_code IS '分类类型内有效分类的唯一业务编码；逻辑删除后允许重新使用，历史通过稳定分类ID区分';
COMMENT ON COLUMN base_category.category_name IS '分类中文名称，同一父级下不允许同名';
COMMENT ON COLUMN base_category.sort_order IS '显示排序，数字越小越靠前';
COMMENT ON COLUMN base_category.status IS '状态：0启用，1停用；停用保留历史引用';
COMMENT ON COLUMN base_category.remark IS '业务备注，不存凭据或敏感信息';
COMMENT ON COLUMN base_category.del_flag IS '逻辑删除标记：0正常，1已删除';
COMMENT ON COLUMN base_category.create_by IS '创建员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_category.create_time IS '创建时间';
COMMENT ON COLUMN base_category.update_by IS '最后修改员工ID，关联sys_user.user_id的身份语义';
COMMENT ON COLUMN base_category.update_time IS '最后修改时间';
-- 人员分类权限：未登记授权记录时默认全部可见；指定范围按分类类型或分类节点保存。
CREATE TABLE IF NOT EXISTS sys_person_category_scope (
 user_id bigint PRIMARY KEY REFERENCES sys_user(user_id) ON DELETE CASCADE,
 scope_mode varchar(12) NOT NULL CHECK(scope_mode IN ('ALL','NONE','SELECTED'))
);
COMMENT ON TABLE sys_person_category_scope IS '人员分类授权；无记录或ALL表示全部分类可见，NONE表示不可见，SELECTED表示按明细授权';
COMMENT ON COLUMN sys_person_category_scope.user_id IS '员工账号稳定主键，关联sys_user.user_id';
COMMENT ON COLUMN sys_person_category_scope.scope_mode IS '授权模式：ALL全部可见、NONE全部不可见、SELECTED指定范围';
CREATE TABLE IF NOT EXISTS sys_person_category_grant (
 user_id bigint NOT NULL REFERENCES sys_person_category_scope(user_id) ON DELETE CASCADE,
 scheme_id bigint REFERENCES base_category_scheme(scheme_id),
 category_id bigint REFERENCES base_category(category_id),
 include_children boolean NOT NULL,
 CHECK((scheme_id IS NOT NULL AND category_id IS NULL AND include_children)
    OR (scheme_id IS NULL AND category_id IS NOT NULL)),
 UNIQUE NULLS NOT DISTINCT(user_id,scheme_id,category_id)
);
COMMENT ON TABLE sys_person_category_grant IS '人员指定分类范围，授权对象为分类类型或分类节点';
COMMENT ON COLUMN sys_person_category_grant.user_id IS '所属人员分类授权账号主键';
COMMENT ON COLUMN sys_person_category_grant.scheme_id IS '整类授权的分类类型主键；整类授权包含该类型现有及未来分类';
COMMENT ON COLUMN sys_person_category_grant.category_id IS '节点授权的分类主键';
COMMENT ON COLUMN sys_person_category_grant.include_children IS '是否包含该节点现有及未来下级；类型授权必须为true';
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) SELECT 1762700000000000010,'分类管理',1761400000000000001,170,'category','system/CategoryManage','N','Y','C','0','0','system:category:list','#',now() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:category:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) SELECT x.id,x.name,(SELECT menu_id FROM sys_menu WHERE perms='system:category:list'),x.sort,'#','','N','Y','F','0','0',x.perms,'#',now() FROM (VALUES (1762700000000000011::bigint,'分类管理新增',171,'system:category:add'),(1762700000000000012::bigint,'分类管理修改与启停',172,'system:category:edit'),(1762700000000000013::bigint,'分类管理删除',173,'system:category:remove')) x(id,name,sort,perms) WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=x.perms);
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='分类管理',menu_type='C',path='category',component='system/CategoryManage',visible='0',status='0' WHERE perms='system:category:list';
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='system:category:list'),menu_type='F',visible='0',status='0' WHERE perms IN ('system:category:add','system:category:edit','system:category:remove');
-- 管理员执行迁移时，复用既有业务表所有者识别应用角色，避免新表/序列无权限。
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 IF app_role IS NULL THEN RAISE EXCEPTION '无法识别应用数据库角色'; END IF;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.base_category_scheme,public.base_category,public.sys_person_category_scope,public.sys_person_category_grant TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.base_category_scheme_scheme_id_seq,public.base_category_category_id_seq TO %I',app_role);
END $$;
COMMENT ON SEQUENCE base_category_scheme_scheme_id_seq IS '生成分类体系稳定主键的序列';
COMMENT ON SEQUENCE base_category_category_id_seq IS '生成分类节点稳定主键的序列';

-- 颜色管理：全局可复用颜色基础资料
CREATE TABLE IF NOT EXISTS base_color (
 color_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 color_code varchar(40) NOT NULL,
 color_name varchar(80) NOT NULL,
 color_value varchar(7) NOT NULL DEFAULT '',
 sort_order integer NOT NULL DEFAULT 0 CHECK(sort_order BETWEEN 0 AND 999999),
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(500) NOT NULL DEFAULT '',
 create_by bigint,
 create_time timestamptz NOT NULL DEFAULT now(),
 update_by bigint,
 update_time timestamptz,
 CONSTRAINT ck_base_color_code CHECK(color_code ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$'),
 CONSTRAINT ck_base_color_name CHECK(length(trim(color_name))>0),
 CONSTRAINT ck_base_color_value CHECK(color_value='' OR color_value ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_base_color_code_ci ON base_color(lower(color_code));
CREATE UNIQUE INDEX IF NOT EXISTS uq_base_color_name_ci ON base_color(lower(color_name));
CREATE INDEX IF NOT EXISTS idx_base_color_status_sort ON base_color(status,sort_order,color_name);
COMMENT ON TABLE base_color IS '全局颜色基础资料，供物料、款式等业务复用；停用保留历史引用';
COMMENT ON COLUMN base_color.color_id IS '颜色稳定主键，跨业务引用此ID';
COMMENT ON COLUMN base_color.color_code IS '颜色稳定业务编码，新增时自动建议且可编辑，创建后不可修改';
COMMENT ON COLUMN base_color.color_name IS '颜色中文名称，忽略大小写后全局唯一';
COMMENT ON COLUMN base_color.color_value IS '可选屏幕色样值，空表示未设置；填写时格式为#RRGGBB，不替代实物色卡或供应商色号';
COMMENT ON COLUMN base_color.sort_order IS '显示排序，范围0至999999，数字越小越靠前';
COMMENT ON COLUMN base_color.status IS '状态：0启用，可供新业务选择；1停用，只供历史引用显示';
COMMENT ON COLUMN base_color.remark IS '颜色补充说明，不存供应商色号、缸号或业务批次信息';
COMMENT ON COLUMN base_color.create_by IS '创建人用户ID，关联当前登录用户身份';
COMMENT ON COLUMN base_color.create_time IS '创建时间';
COMMENT ON COLUMN base_color.update_by IS '最后修改人用户ID，关联当前登录用户身份';
COMMENT ON COLUMN base_color.update_time IS '最后修改时间';
COMMENT ON SEQUENCE base_color_color_id_seq IS '生成颜色基础资料稳定主键';
INSERT INTO sys_config(config_id,config_name,config_key,config_value,config_type,create_time,remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config),0)+1,'颜色自动编码规则','base.color.codeRule','{"enabled":true,"prefix":"CLR","digits":4}','Y',now(),'颜色编码自动生成开关、前缀及数字位数；生成值可人工编辑'
WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='base.color.codeRule');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762700000000000030,'颜色管理',1761400000000000001,180,'color','system/ColorManage','N','Y','C','0','0','system:color:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:color:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT x.id,x.name,(SELECT menu_id FROM sys_menu WHERE perms='system:color:list'),x.sort,'#','','N','Y','F','0','0',x.perms,'#',now()
FROM (VALUES (1762700000000000031::bigint,'颜色新增',1,'system:color:add'),(1762700000000000032::bigint,'颜色修改与启停',2,'system:color:edit'),(1762700000000000033::bigint,'颜色删除',3,'system:color:remove')) x(id,name,sort,perms)
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=x.perms);
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='颜色管理',menu_type='C',path='color',component='system/ColorManage',visible='0',status='0' WHERE perms='system:color:list';
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='system:color:list'),menu_type='F',visible='0',status='0' WHERE perms IN ('system:color:add','system:color:edit','system:color:remove');
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 IF app_role IS NULL THEN RAISE EXCEPTION '无法识别应用数据库角色'; END IF;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.base_color TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.base_color_color_id_seq TO %I',app_role);
END $$;

COMMIT;


-- 物料档案一期：2026-11-08 用户验收通过后并入主建表。规格模板目录可按稳定字段键继续扩展。
-- 物料档案一期：仅维护可跨库存、采购、BOM复用的主数据，不在本批次记库存数量或收货流水。
BEGIN;
CREATE TABLE IF NOT EXISTS inv_material (
 material_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 material_code varchar(50) NOT NULL,
 material_name varchar(120) NOT NULL,
 category_id bigint NOT NULL REFERENCES base_category(category_id),
 specification varchar(200) NOT NULL DEFAULT '',
 model varchar(120) NOT NULL DEFAULT '',
 brand varchar(100) NOT NULL DEFAULT '',
 material_desc varchar(1000) NOT NULL DEFAULT '',
 inventory_unit_id bigint NOT NULL REFERENCES base_unit(unit_id),
 sort_order integer NOT NULL DEFAULT 0 CHECK(sort_order BETWEEN 0 AND 999999),
 status char(1) NOT NULL DEFAULT '0' CHECK(status IN ('0','1')),
 remark varchar(500) NOT NULL DEFAULT '',
 del_flag char(1) NOT NULL DEFAULT '0' CHECK(del_flag IN ('0','1')),
 create_by bigint, create_time timestamptz NOT NULL DEFAULT now(), update_by bigint, update_time timestamptz,
 CHECK(length(trim(material_code))>0), CHECK(length(trim(material_name))>0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_material_code_active ON inv_material(lower(material_code)) WHERE del_flag='0';
CREATE INDEX IF NOT EXISTS idx_inv_material_category_status ON inv_material(category_id,status) WHERE del_flag='0';
CREATE INDEX IF NOT EXISTS idx_inv_material_name ON inv_material(lower(material_name)) WHERE del_flag='0';
COMMENT ON TABLE inv_material IS '物料主档，供库存、采购、BOM及领退料共享；不保存库存数量、客户归属或供应商批号';
COMMENT ON COLUMN inv_material.material_id IS '物料稳定主键，采购、库存、BOM等业务通过此ID引用';
COMMENT ON COLUMN inv_material.material_code IS '物料业务编码；未删除物料中忽略大小写唯一，自动建议值可在保存前编辑';
COMMENT ON COLUMN inv_material.material_name IS '物料通用名称，不包含批次、供应商或单据特有信息';
COMMENT ON COLUMN inv_material.category_id IS '物料所属分类节点，关联base_category.category_id；人员分类授权按此节点校验';
COMMENT ON COLUMN inv_material.specification IS '物料规格尺寸或包装规格；历史记录可能包含改版前的综合规格描述';
COMMENT ON COLUMN inv_material.model IS '物料型号或款式标识；没有型号时为空，不替代物料编码';
COMMENT ON COLUMN inv_material.brand IS '物料品牌；无品牌或暂无信息时为空';
COMMENT ON COLUMN inv_material.material_desc IS '物料材质或关键属性补充说明，不存批次、色号、供应商和收货结果';
COMMENT ON COLUMN inv_material.inventory_unit_id IS '库存基本单位，关联base_unit.unit_id；物料数量按此单位记账，禁止用辅助单位替代';
COMMENT ON COLUMN inv_material.sort_order IS '列表显示排序，范围0至999999，数字越小越靠前';
COMMENT ON COLUMN inv_material.status IS '档案状态：0启用可供新业务选择，1停用并保留历史引用';
COMMENT ON COLUMN inv_material.remark IS '物料档案补充备注，不存业务单据处理意见';
COMMENT ON COLUMN inv_material.del_flag IS '逻辑删除标志：0正常，1已删除；有外键引用的物料拒绝删除';
COMMENT ON COLUMN inv_material.create_by IS '创建物料档案的用户ID，业务身份为员工账号';
COMMENT ON COLUMN inv_material.create_time IS '物料档案创建时间';
COMMENT ON COLUMN inv_material.update_by IS '最后修改物料档案的用户ID，业务身份为员工账号';
COMMENT ON COLUMN inv_material.update_time IS '物料档案最后修改时间';
COMMENT ON INDEX uq_inv_material_code_active IS '保护未删除物料编码大小写不敏感唯一';
COMMENT ON INDEX idx_inv_material_category_status IS '按授权分类及状态筛选物料档案';
COMMENT ON INDEX idx_inv_material_name IS '支持忽略大小写查找物料名称';
COMMENT ON SEQUENCE inv_material_material_id_seq IS '生成物料档案稳定主键';

CREATE TABLE IF NOT EXISTS inv_material_color (
 material_id bigint NOT NULL REFERENCES inv_material(material_id) ON DELETE CASCADE,
 color_id bigint NOT NULL REFERENCES base_color(color_id),
 create_by bigint, create_time timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(material_id,color_id)
);
COMMENT ON TABLE inv_material_color IS '物料可用颜色关联；一条物料可选多种颜色，具体收货色号、缸号和批次颜色另记在库存业务';
COMMENT ON COLUMN inv_material_color.material_id IS '关联物料档案ID';
COMMENT ON COLUMN inv_material_color.color_id IS '关联全局颜色基础资料ID';
COMMENT ON COLUMN inv_material_color.create_by IS '建立物料颜色关联的员工用户ID';
COMMENT ON COLUMN inv_material_color.create_time IS '建立物料颜色关联的时间';
COMMENT ON CONSTRAINT inv_material_color_pkey ON inv_material_color IS '同一物料与同一颜色只能关联一次';

-- 扩展物料规格并登记允许使用的公共辅助单位，兼容已执行过一期脚本的开发库。
ALTER TABLE inv_material
 ADD COLUMN IF NOT EXISTS fabric_weight_gsm numeric(12,4) CHECK (fabric_weight_gsm > 0),
 ADD COLUMN IF NOT EXISTS fabric_width_cm numeric(12,4) CHECK (fabric_width_cm > 0),
 ADD COLUMN IF NOT EXISTS composition varchar(300) NOT NULL DEFAULT '';
COMMENT ON COLUMN inv_material.fabric_weight_gsm IS '面料克重，单位克每平方米（g/m²）；不适用时为空';
COMMENT ON COLUMN inv_material.fabric_width_cm IS '面料幅宽，单位厘米（cm）；不适用时为空';
COMMENT ON COLUMN inv_material.composition IS '物料成分，可填写组成比例，例如棉95%、氨纶5%';

CREATE TABLE IF NOT EXISTS inv_material_aux_unit (
 material_id bigint NOT NULL REFERENCES inv_material(material_id) ON DELETE CASCADE,
 unit_id bigint NOT NULL REFERENCES base_unit(unit_id),
 PRIMARY KEY (material_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_inv_material_aux_unit_unit ON inv_material_aux_unit(unit_id);
-- 物料专属单位支持固定换算和实际数量录入；旧关联按当前公共系数迁移为物料快照。
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS conversion_mode varchar(16) NOT NULL DEFAULT 'FIXED';
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS reference_unit_id bigint REFERENCES base_unit(unit_id);
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS factor numeric(30,12);
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS inventory_factor numeric(30,12);
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS purchase_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE inv_material_aux_unit ADD COLUMN IF NOT EXISTS usage_enabled boolean NOT NULL DEFAULT true;
UPDATE inv_material_aux_unit a SET reference_unit_id=m.inventory_unit_id, factor=u.factor, inventory_factor=u.factor
FROM inv_material m, base_unit u WHERE m.material_id=a.material_id AND u.unit_id=a.unit_id
  AND a.conversion_mode='FIXED' AND a.factor IS NULL AND u.base_unit_id=m.inventory_unit_id;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='inv_material_aux_unit'::regclass AND conname='ck_material_unit_conversion') THEN
   ALTER TABLE inv_material_aux_unit ADD CONSTRAINT ck_material_unit_conversion CHECK (
     (conversion_mode='FIXED' AND reference_unit_id IS NOT NULL AND reference_unit_id<>unit_id AND factor IS NOT NULL AND factor>0 AND inventory_factor IS NOT NULL AND inventory_factor>0)
     OR (conversion_mode='VARIABLE' AND reference_unit_id IS NULL AND factor IS NULL AND inventory_factor IS NULL));
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='inv_material_aux_unit'::regclass AND conname='ck_material_unit_use') THEN
   ALTER TABLE inv_material_aux_unit ADD CONSTRAINT ck_material_unit_use CHECK (purchase_enabled OR usage_enabled);
 END IF;
END $$;
COMMENT ON TABLE inv_material_aux_unit IS '物料专属采购和辅助单位配置，独立保存换算关系';
COMMENT ON COLUMN inv_material_aux_unit.material_id IS '物料档案主键';
COMMENT ON COLUMN inv_material_aux_unit.unit_id IS '所选计量单位主键';
COMMENT ON COLUMN inv_material_aux_unit.conversion_mode IS '换算方式：FIXED 固定系数，VARIABLE 每次录入实际库存数量';
COMMENT ON COLUMN inv_material_aux_unit.reference_unit_id IS '固定换算的参照单位，可以是库存单位或本物料其他固定单位';
COMMENT ON COLUMN inv_material_aux_unit.factor IS '一个当前单位等于多少参照单位，仅属于当前物料';
COMMENT ON COLUMN inv_material_aux_unit.inventory_factor IS '一个当前单位折算成库存单位的精确系数';
COMMENT ON COLUMN inv_material_aux_unit.purchase_enabled IS '是否允许采购使用此单位';
COMMENT ON COLUMN inv_material_aux_unit.usage_enabled IS '是否允许生产及领用使用此单位';
COMMENT ON INDEX idx_inv_material_aux_unit_unit IS '按辅助单位查找物料引用';

-- 规格模板供物料独立选择，字段键在模板间共享时必须有同一业务含义。
CREATE TABLE IF NOT EXISTS inv_material_spec_template (
 template_code varchar(40) PRIMARY KEY,
 template_name varchar(80) NOT NULL,
 sort_order integer NOT NULL DEFAULT 0,
 active boolean NOT NULL DEFAULT true,
 fields jsonb NOT NULL CHECK (jsonb_typeof(fields) = 'array')
);
COMMENT ON TABLE inv_material_spec_template IS '物料规格模板目录，录入时直接选用，与分类节点独立';
COMMENT ON COLUMN inv_material_spec_template.template_code IS '稳定模板编码，档案、接口和导入通过此编码引用';
COMMENT ON COLUMN inv_material_spec_template.template_name IS '模板选择器显示名称';
COMMENT ON COLUMN inv_material_spec_template.sort_order IS '模板在选择器中的显示顺序';
COMMENT ON COLUMN inv_material_spec_template.active IS '是否允许新引用，停用后仍保留已建档物料的回显';
COMMENT ON COLUMN inv_material_spec_template.fields IS '按显示顺序保存字段定义，包含稳定键、标签、类型、单位、提示和可选值；signed_decimal 表示允许正负的百分比规格';

INSERT INTO inv_material_spec_template(template_code,template_name,sort_order,fields) VALUES
 ('fabric','面料 / 里布',10,'[{"key":"fabricStructure","label":"织法 / 布面结构","type":"text","placeholder":"例如：梭织平纹、梭织斜纹、针织罗纹"},{"key":"fabricWeightGsm","label":"克重","type":"decimal","unit":"g/m²"},{"key":"fabricWidthCm","label":"幅宽","type":"decimal","unit":"cm"},{"key":"composition","label":"成分","type":"text"},{"key":"warpShrinkagePct","label":"经向标准缩率","type":"signed_decimal","unit":"%","placeholder":"允许负值，表示尺寸增长"},{"key":"weftShrinkagePct","label":"纬向标准缩率","type":"signed_decimal","unit":"%","placeholder":"允许负值，表示尺寸增长"},{"key":"shrinkageCondition","label":"缩率测试条件","type":"text","placeholder":"例如：水洗后，注明洗涤与烘干条件"},{"key":"fabricDensity","label":"密度及口径","type":"text","placeholder":"梭织填经密/纬密；针织填横列/纵行密度，并注明每英寸或每10厘米"}]'::jsonb),
 ('interlining','衬布（含粘合衬）',20,'[{"key":"interliningType","label":"衬布类型","type":"select","options":["有纺","无纺","针织"]},{"key":"fusible","label":"是否带胶","type":"boolean"},{"key":"material","label":"材质","type":"text"},{"key":"interliningWeight","label":"克重","type":"decimal","unit":"g/m²"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"}]'::jsonb),
 ('thread','缝纫线',30,'[{"key":"composition","label":"成分","type":"text"},{"key":"threadStructure","label":"线结构","type":"text"},{"key":"threadSize","label":"线号","type":"text"},{"key":"threadSizeSystem","label":"线号体系","type":"select","options":["Tex","票号","支数","其他"]},{"key":"spoolLength","label":"每筒公称长度","type":"decimal","unit":"m"}]'::jsonb),
 ('zipper','拉链',40,'[{"key":"zipperChain","label":"牙型","type":"select","options":["尼龙","树脂","金属","隐形"]},{"key":"zipperSize","label":"号数","type":"text"},{"key":"zipperLength","label":"拉链长度","type":"decimal","unit":"mm"},{"key":"zipperEnd","label":"开闭形式","type":"select","options":["闭尾","开尾","双开尾"]},{"key":"sliderType","label":"拉头类型","type":"text"}]'::jsonb),
 ('button','钮扣与扣件',50,'[{"key":"fastenerType","label":"扣型","type":"select","options":["两孔扣","四孔扣","带脚扣","四合扣","鸡眼","铆钉","其他"]},{"key":"diameter","label":"直径","type":"decimal","unit":"mm"},{"key":"holeCount","label":"孔数","type":"integer"},{"key":"material","label":"材质","type":"text"},{"key":"surfaceFinish","label":"表面处理","type":"text"}]'::jsonb),
 ('tape','松紧带与织带',60,'[{"key":"tapeType","label":"带型","type":"select","options":["松紧带","织带","抽绳","包边带","其他"]},{"key":"tapeWidth","label":"带宽","type":"decimal","unit":"mm"},{"key":"diameter","label":"直径","type":"decimal","unit":"mm"},{"key":"material","label":"材质","type":"text"},{"key":"elongation","label":"伸长率","type":"decimal","unit":"%"}]'::jsonb),
 ('label','唛标与吊牌',70,'[{"key":"labelType","label":"类型","type":"select","options":["主唛","尺码唛","洗水唛","吊牌","其他"]},{"key":"labelProcess","label":"制作方式","type":"select","options":["织造","印刷","热转印","其他"]},{"key":"labelLength","label":"成品长度","type":"decimal","unit":"mm"},{"key":"labelWidth","label":"成品宽度","type":"decimal","unit":"mm"},{"key":"material","label":"材质","type":"text"},{"key":"foldType","label":"折法","type":"text"},{"key":"artworkVersion","label":"稿件版本","type":"text"}]'::jsonb),
 ('carton','纸箱',80,'[{"key":"insideLength","label":"内长","type":"decimal","unit":"mm"},{"key":"insideWidth","label":"内宽","type":"decimal","unit":"mm"},{"key":"insideHeight","label":"内高","type":"decimal","unit":"mm"},{"key":"plyCount","label":"纸板层数","type":"integer"},{"key":"flute","label":"楞型","type":"text"}]'::jsonb),
 ('paper_bag','纸袋',90,'[{"key":"bagWidth","label":"袋宽","type":"decimal","unit":"mm"},{"key":"bagLength","label":"袋长","type":"decimal","unit":"mm"},{"key":"gussetWidth","label":"侧褶宽","type":"decimal","unit":"mm"},{"key":"paperWeight","label":"纸张克重","type":"decimal","unit":"g/m²"},{"key":"handleType","label":"提手类型","type":"text"}]'::jsonb),
 ('plastic_bag','塑料袋',100,'[{"key":"bagWidth","label":"袋宽","type":"decimal","unit":"mm"},{"key":"bagLength","label":"袋长","type":"decimal","unit":"mm"},{"key":"filmThickness","label":"膜厚","type":"decimal","unit":"μm"},{"key":"material","label":"材质","type":"text"},{"key":"sealType","label":"封口方式","type":"select","options":["开口","自封","自粘","热封","其他"]}]'::jsonb)
ON CONFLICT (template_code) DO NOTHING;

-- 同步已安装模板的旧展示名；按稳定字段键更新文案，保留字段顺序和已录入的规格值。
WITH renamed(code, old_name, new_name, field_key, old_label, new_label) AS (
 VALUES ('fabric','面料与里料','面料 / 里布','fabricStructure','组织结构','织法 / 布面结构'),
        ('interlining','衬料','衬布（含粘合衬）','interliningType','衬料结构','衬布类型')
)
UPDATE inv_material_spec_template t
SET template_name = CASE WHEN t.template_name = r.old_name THEN r.new_name ELSE t.template_name END,
    fields = (SELECT jsonb_agg(
        CASE WHEN f.value->>'key' = r.field_key AND f.value->>'label' = r.old_label
             THEN jsonb_set(f.value, '{label}', to_jsonb(r.new_label))
             ELSE f.value END ORDER BY f.position)
        FROM jsonb_array_elements(t.fields) WITH ORDINALITY AS f(value, position))
FROM renamed r
WHERE t.template_code = r.code
  AND (t.template_name = r.old_name OR EXISTS (
    SELECT 1 FROM jsonb_array_elements(t.fields) AS f(value)
    WHERE f.value->>'key' = r.field_key AND f.value->>'label' = r.old_label
  ));

-- 面料模板补充经纬向缩率及密度口径；仅按稳定字段键追加，不覆盖既有规格值。
WITH additions AS (
 SELECT 'fabric'::varchar AS template_code,
        '[{"key":"warpShrinkagePct","label":"经向标准缩率","type":"signed_decimal","unit":"%","placeholder":"允许负值，表示尺寸增长"},{"key":"weftShrinkagePct","label":"纬向标准缩率","type":"signed_decimal","unit":"%","placeholder":"允许负值，表示尺寸增长"},{"key":"shrinkageCondition","label":"缩率测试条件","type":"text","placeholder":"例如：水洗后，注明洗涤与烘干条件"},{"key":"fabricDensity","label":"密度及口径","type":"text","placeholder":"梭织填经密/纬密；针织填横列/纵行密度，并注明每英寸或每10厘米"}]'::jsonb AS fields
)
UPDATE inv_material_spec_template t
SET fields = COALESCE((
    SELECT jsonb_agg(f.value ORDER BY f.position)
    FROM jsonb_array_elements(t.fields) WITH ORDINALITY AS f(value, position)
), '[]'::jsonb) || COALESCE((
    SELECT jsonb_agg(a.value ORDER BY a.position)
    FROM jsonb_array_elements(additions.fields) WITH ORDINALITY AS a(value, position)
    WHERE NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(t.fields) AS existing(value)
        WHERE existing.value->>'key' = a.value->>'key'
    )
), '[]'::jsonb)
FROM additions
WHERE t.template_code = additions.template_code
  AND EXISTS (
      SELECT 1 FROM jsonb_array_elements(additions.fields) AS a(value)
      WHERE NOT EXISTS (
          SELECT 1 FROM jsonb_array_elements(t.fields) AS existing(value)
          WHERE existing.value->>'key' = a.value->>'key'
      )
  );

ALTER TABLE inv_material
 ADD COLUMN IF NOT EXISTS spec_template_code varchar(40) REFERENCES inv_material_spec_template(template_code),
 ADD COLUMN IF NOT EXISTS spec_values jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(spec_values) = 'object');
COMMENT ON COLUMN inv_material.spec_template_code IS '物料主动选择的规格模板编码，为空表示使用通用规格';
COMMENT ON COLUMN inv_material.spec_values IS '以稳定字段键保存模板值与历史值；面料克重、幅宽、成分仍以原列为准';

INSERT INTO sys_config(config_id,config_name,config_key,config_value,config_type,create_time,remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config),0)+1,'物料自动编码规则','inventory.material.codeRule','{"enabled":true,"prefix":"MAT","digits":5}','Y',now(),'物料编码自动生成开关、前缀和序号位数；建议编码可以人工修改'
WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='inventory.material.codeRule');
INSERT INTO sys_config(config_id,config_name,config_key,config_value,config_type,create_time,remark)
SELECT COALESCE((SELECT MAX(config_id) FROM sys_config),0)+1,'物料默认分类类型','inventory.material.defaultCategoryScheme','','Y',now(),'物料档案页面的默认分类类型；留空时展示当前员工全部分类授权范围'
WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='inventory.material.defaultCategoryScheme');

-- 页面级权限归库存模块；角色只获得用户已有权限对应的祖先，不自动授予任何新增/编辑/删除能力。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762800000000000121,1762800000000000001,'物料档案',30,'materials','stock/MaterialManage','N','Y','C','0','0','inventory:material:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='inventory:material:list');
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT x.id,(SELECT menu_id FROM sys_menu WHERE perms='inventory:material:list'),x.name,x.sort,'#','','N','Y','F','0','0',x.perms,'#',now()
FROM (VALUES (1762800000000000122::bigint,'物料新增',1,'inventory:material:add'),(1762800000000000123::bigint,'物料编辑与启停',2,'inventory:material:edit'),(1762800000000000124::bigint,'物料删除',3,'inventory:material:remove')) x(id,name,sort,perms)
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms=x.perms);
UPDATE sys_menu SET parent_id=1762800000000000001,menu_name='物料档案',menu_type='C',path='materials',component='stock/MaterialManage',visible='0',status='0'
WHERE perms='inventory:material:list';
UPDATE sys_menu SET parent_id=(SELECT menu_id FROM sys_menu WHERE perms='inventory:material:list'),menu_type='F',visible='0',status='0'
WHERE perms IN ('inventory:material:add','inventory:material:edit','inventory:material:remove');
WITH RECURSIVE ancestors AS (
 SELECT rm.role_id,m.parent_id AS menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms IN ('inventory:material:list','inventory:material:add','inventory:material:edit','inventory:material:remove')
 UNION SELECT a.role_id,m.parent_id FROM ancestors a JOIN sys_menu m ON m.menu_id=a.menu_id WHERE a.menu_id<>0
)
INSERT INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT a.role_id,a.menu_id FROM ancestors a
WHERE a.menu_id<>0 AND NOT EXISTS(SELECT 1 FROM sys_role_menu x WHERE x.role_id=a.role_id AND x.menu_id=a.menu_id);

-- 使用公共标签表识别应用账号。仓库表可能由迁移管理员持有。
DO $$ DECLARE app_role text; BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 IF app_role IS NULL THEN RAISE EXCEPTION '未找到应用数据库角色，请先建立公共标签表'; END IF;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.inv_material,public.inv_material_color,public.inv_material_aux_unit TO %I',app_role);
 EXECUTE format('GRANT SELECT ON public.inv_material_spec_template TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.inv_material_material_id_seq TO %I',app_role);
END $$;
COMMIT;

-- 款式档案一期验收合并，按主档、套装规格、附件的依赖顺序执行。

-- 款式档案_20261108.sql
-- 研发、样衣与订单引用同一个款式ID。技术版本及样衣任务由后续功能独立建表。
BEGIN;
CREATE TABLE IF NOT EXISTS rd_style (
    style_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    internal_code varchar(60) NOT NULL CHECK (btrim(internal_code) <> ''),
    style_name varchar(120) NOT NULL CHECK (btrim(style_name) <> ''),
    customer_id bigint REFERENCES customer_master(customer_id),
    category_id bigint NOT NULL REFERENCES base_category(category_id),
    season varchar(40) NOT NULL DEFAULT '',
    description varchar(1000) NOT NULL DEFAULT '',
    cover_oss_id bigint REFERENCES sys_oss(oss_id),
    status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
    remark varchar(500) NOT NULL DEFAULT '',
    create_by bigint NOT NULL,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_by bigint,
    update_time timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rd_style_internal_code ON rd_style(lower(internal_code));
CREATE INDEX IF NOT EXISTS idx_rd_style_customer ON rd_style(customer_id);
CREATE INDEX IF NOT EXISTS idx_rd_style_category ON rd_style(category_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rd_style_cover ON rd_style(cover_oss_id) WHERE cover_oss_id IS NOT NULL;
COMMENT ON TABLE rd_style IS '款式主档，研发、样衣任务和生产订单共用';
COMMENT ON COLUMN rd_style.style_id IS '款式档案主键，订单、样衣和 BOM 通过此稳定 ID 引用';
COMMENT ON COLUMN rd_style.internal_code IS '客户款号，款式对外识别编码；有效档案中忽略大小写唯一';
COMMENT ON COLUMN rd_style.style_name IS '款式名称，用于档案、订单和生产任务展示';
COMMENT ON COLUMN rd_style.customer_id IS '所属客户主键，关联 customer_master.customer_id；自主研发且未归属客户时可为空';
COMMENT ON COLUMN rd_style.category_id IS '款式所属分类节点，关联 base_category.category_id；查询及新引用受人员分类授权控制';
COMMENT ON COLUMN rd_style.season IS '款式季节统计维度，新数据使用不含年份的季节选项，历史文本保留原值';
COMMENT ON COLUMN rd_style.description IS '款式整体说明，不替代颜色尺码、套装配套或 BOM 明细';
COMMENT ON COLUMN rd_style.cover_oss_id IS '款式主图文件 ID，关联 sys_oss.oss_id；通过私有文件接口鉴权读取';
COMMENT ON COLUMN rd_style.status IS '档案状态：0 启用，可供新业务选择；1 停用，仅保留历史引用显示';
COMMENT ON COLUMN rd_style.remark IS '款式档案补充备注，不存订单处理意见';
COMMENT ON COLUMN rd_style.create_by IS '创建款式档案的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN rd_style.create_time IS '款式档案首次创建时间';
COMMENT ON COLUMN rd_style.update_by IS '最后修改款式档案的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN rd_style.update_time IS '款式档案最后一次保存时间';
COMMENT ON INDEX rd_style_pkey IS '款式稳定主键';
COMMENT ON INDEX uq_rd_style_internal_code IS '款号唯一校验';
COMMENT ON INDEX idx_rd_style_customer IS '客户维度查询与引用检查';
COMMENT ON INDEX idx_rd_style_category IS '分类范围过滤和引用检查';
COMMENT ON INDEX uq_rd_style_cover IS '一张私有主图只绑定一个款式';
COMMENT ON SEQUENCE rd_style_style_id_seq IS '款式主键分配序列';

-- 角色管理员按需分配页面和操作权限，迁移不自动授权。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762900000000000100,0,'研发',70,'/rd',NULL,'N','Y','M','0','0','','experiment',now()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=0 AND path IN ('rd','/rd'));
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762900000000000101,menu_id,'款式档案',10,'styles','rd/StyleManage','N','Y','C','0','0','rd:style:list','#',now()
FROM sys_menu WHERE parent_id=0 AND path IN ('rd','/rd')
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='rd:style:list')
ORDER BY menu_id LIMIT 1;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.id,m.menu_id,v.name,v.seq,'#','','N','Y','F','0','0',v.perm,'#',now()
FROM sys_menu m CROSS JOIN (VALUES
    (1762900000000000102::bigint,'新增款式',1,'rd:style:add'),
    (1762900000000000103::bigint,'编辑与启停款式',2,'rd:style:edit'),
    (1762900000000000104::bigint,'删除款式',3,'rd:style:remove')
) v(id,name,seq,perm)
WHERE m.perms='rd:style:list' AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.perms=v.perm);

DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.rd_style TO %I',app_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE public.rd_style_style_id_seq TO %I',app_role);
END $$;
COMMIT;


-- 款式规格与套装_20261109.sql
BEGIN;

ALTER TABLE rd_style ADD COLUMN IF NOT EXISTS production_mode varchar(12) NOT NULL DEFAULT 'single'
    CHECK (production_mode IN ('single','combined','separate'));
ALTER TABLE rd_style ADD COLUMN IF NOT EXISTS same_hanger boolean NOT NULL DEFAULT false
    CHECK (NOT same_hanger OR production_mode='combined');
COMMENT ON COLUMN rd_style.production_mode IS '款式生产模式：single 单款，combined 合并生产套装，separate 分开生产套装';
COMMENT ON COLUMN rd_style.same_hanger IS '是否要求合并生产套装的所有组成子款共用同一衣架：true=要求，false=不要求；仅合并生产方式适用';

CREATE TABLE IF NOT EXISTS rd_style_member (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    child_id bigint NOT NULL REFERENCES rd_style(style_id),
    role_name varchar(60) NOT NULL CHECK (btrim(role_name)<>''),
    quantity integer NOT NULL CHECK (quantity>0),
    sort_order integer NOT NULL,
    PRIMARY KEY (style_id,child_id),
    CHECK (style_id<>child_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_member_child ON rd_style_member(child_id);
COMMENT ON TABLE rd_style_member IS '套装与独立子款的组成关系，移除关系不删除子款';
COMMENT ON COLUMN rd_style_member.style_id IS '所属套装款式主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_member.child_id IS '组成套装的独立单款主键，关联 rd_style.style_id；移除组成不删除子款档案';
COMMENT ON COLUMN rd_style_member.role_name IS '子款在此套装中的业务名称，例如上衣、下装或帽子';
COMMENT ON COLUMN rd_style_member.quantity IS '每一套包含的本子款件数，必须为正整数';
COMMENT ON COLUMN rd_style_member.sort_order IS '套装子款显示和处理顺序，数值越小越靠前';

CREATE TABLE IF NOT EXISTS rd_style_color (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    color_id bigint NOT NULL REFERENCES base_color(color_id),
    PRIMARY KEY (style_id,color_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_color_color ON rd_style_color(color_id);
COMMENT ON TABLE rd_style_color IS '单款可用颜色';
COMMENT ON COLUMN rd_style_color.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_color.color_id IS '单款可用颜色主键，关联 base_color.color_id';

CREATE TABLE IF NOT EXISTS rd_style_size (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    size_id bigint NOT NULL REFERENCES sys_size(id),
    PRIMARY KEY (style_id,size_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_size_size ON rd_style_size(size_id);
COMMENT ON TABLE rd_style_size IS '单款可用尺码，保存具体尺码而非尺码组';
COMMENT ON COLUMN rd_style_size.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_size.size_id IS '单款可用尺码主键，关联 sys_size.id；此处保存实际尺码而非尺码组';

CREATE TABLE IF NOT EXISTS rd_style_spec_exclusion (
    style_id bigint NOT NULL,
    color_id bigint NOT NULL,
    size_id bigint NOT NULL,
    PRIMARY KEY (style_id,color_id,size_id),
    FOREIGN KEY (style_id,color_id) REFERENCES rd_style_color(style_id,color_id) ON DELETE CASCADE,
    FOREIGN KEY (style_id,size_id) REFERENCES rd_style_size(style_id,size_id) ON DELETE CASCADE
);
COMMENT ON TABLE rd_style_spec_exclusion IS '单款明确排除的颜色尺码组合，其余组合由所选颜色和尺码自动生成';
COMMENT ON COLUMN rd_style_spec_exclusion.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_spec_exclusion.color_id IS '被排除组合的颜色主键，关联 base_color.color_id';
COMMENT ON COLUMN rd_style_spec_exclusion.size_id IS '被排除组合的尺码主键，关联 sys_size.id';

-- 让款式分组可在录入首个标签之前维护。
INSERT INTO sys_tag_module(module_key,module_name) VALUES ('rd_style','款式档案')
ON CONFLICT (module_key) DO NOTHING;
INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order)
VALUES ('rd_style','默认分组','1',0) ON CONFLICT DO NOTHING;

DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON rd_style_member,rd_style_color,rd_style_size,rd_style_spec_exclusion TO %I',app_role);
END $$;
COMMIT;


-- 款式附件_20261109.sql
BEGIN;
COMMENT ON COLUMN rd_style.season IS '款式季节统计维度，新数据使用不含年份的季节选项，历史文本保留原值';
CREATE TABLE IF NOT EXISTS rd_style_attachment (
 style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
 oss_id bigint NOT NULL UNIQUE REFERENCES sys_oss(oss_id),
 category varchar(30) NOT NULL CHECK (category IN ('style_image','main_label','size_label','wash_label','hang_tag','tag_sticker','document')),
 sort_order integer NOT NULL DEFAULT 0,
 PRIMARY KEY(style_id,oss_id)
);
COMMENT ON TABLE rd_style_attachment IS '款式与子款的图片、标识及文档附件，按款式权限控制访问';
COMMENT ON COLUMN rd_style_attachment.style_id IS '所属款式或子款主键，关联 rd_style.style_id；套装及子款各自维护附件';
COMMENT ON COLUMN rd_style_attachment.oss_id IS '私有对象存储文件主键，关联 sys_oss.oss_id；同一文件仅能关联一个款式';
COMMENT ON COLUMN rd_style_attachment.category IS '附件类型：style_image 款式图片、main_label 主标、size_label 尺码标、wash_label 洗标、hang_tag 吊牌、tag_sticker 吊牌贴纸、document 文档';
COMMENT ON COLUMN rd_style_attachment.sort_order IS '同一款式同类附件的展示顺序，数值越小越靠前';
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON rd_style_attachment TO %I',app_role);
END $$;
COMMIT;

BEGIN;
-- 套装规格保存常用配色配码；订单确认后应另存本单快照。
CREATE TABLE IF NOT EXISTS rd_style_set_spec (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    style_id BIGINT NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    color_name VARCHAR(120) NOT NULL,
    size_name VARCHAR(120) NOT NULL,
    remark VARCHAR(500) NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT rd_style_set_spec_name_unique UNIQUE (style_id, color_name, size_name) DEFERRABLE INITIALLY IMMEDIATE
);
COMMENT ON TABLE rd_style_set_spec IS '套装常用配色配码规格';
COMMENT ON COLUMN rd_style_set_spec.id IS '套装规格记录主键，供本表子款配套明细关联';
COMMENT ON COLUMN rd_style_set_spec.style_id IS '所属套装款式，关联 rd_style.style_id；删除套装档案时级联删除其规格';
COMMENT ON COLUMN rd_style_set_spec.color_name IS '套装配色方案名称，用于业务员识别整套颜色搭配';
COMMENT ON COLUMN rd_style_set_spec.size_name IS '套装尺码方案名称；可与各子款实际尺码不同';
COMMENT ON COLUMN rd_style_set_spec.remark IS '该套装配色配码方案的补充说明';
COMMENT ON COLUMN rd_style_set_spec.sort_order IS '规格方案显示顺序，数值越小越靠前';
CREATE TABLE IF NOT EXISTS rd_style_set_spec_item (
    spec_id BIGINT NOT NULL REFERENCES rd_style_set_spec(id) ON DELETE CASCADE,
    child_id BIGINT NOT NULL REFERENCES rd_style(style_id),
    color_id BIGINT NOT NULL REFERENCES base_color(color_id),
    size_id BIGINT NOT NULL REFERENCES sys_size(id),
    PRIMARY KEY (spec_id, child_id)
);
COMMENT ON TABLE rd_style_set_spec_item IS '套装规格对应的子款颜色尺码';
COMMENT ON COLUMN rd_style_set_spec_item.spec_id IS '所属套装规格，关联 rd_style_set_spec.id；删除规格时级联删除配套明细';
COMMENT ON COLUMN rd_style_set_spec_item.child_id IS '本配套中的单款子款，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_set_spec_item.color_id IS '该子款实际使用的颜色，关联 base_color.color_id';
COMMENT ON COLUMN rd_style_set_spec_item.size_id IS '该子款实际使用的尺码，关联 sys_size.id；允许同套装内子款异码';
CREATE INDEX IF NOT EXISTS idx_rd_style_set_spec_item_child ON rd_style_set_spec_item(child_id);

-- 沿用款式档案的应用数据库角色。
DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON rd_style_set_spec,rd_style_set_spec_item TO %I',app_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE rd_style_set_spec_id_seq TO %I',app_role);
END $$;
COMMIT;

BEGIN;
ALTER TABLE inv_material ADD COLUMN IF NOT EXISTS multi_spec boolean NOT NULL DEFAULT false;
CREATE TABLE IF NOT EXISTS inv_material_variant (
    variant_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    material_id bigint NOT NULL REFERENCES inv_material(material_id),
    code varchar(64) NOT NULL,
    name varchar(120) NOT NULL,
    status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_material_variant_material_code ON inv_material_variant(material_id, lower(code));
DROP INDEX IF EXISTS uq_inv_material_variant_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_material_variant_name ON inv_material_variant(material_id,lower(name));
COMMENT ON TABLE inv_material_variant IS '物料可独立引用的库存规格，停用保留历史标识';
COMMENT ON COLUMN inv_material.multi_spec IS '是否启用多规格：true=业务行必须指定一个物料规格，false=直接引用物料主档';
COMMENT ON COLUMN inv_material_variant.variant_id IS '物料规格稳定主键，BOM、采购和库存明细使用此 ID 区分规格库存';
COMMENT ON COLUMN inv_material_variant.material_id IS '所属物料主键，关联 inv_material.material_id；删除物料时须先处理其规格';
COMMENT ON COLUMN inv_material_variant.code IS '物料规格编码；同一物料内忽略大小写唯一，不同物料允许复用编码';
COMMENT ON COLUMN inv_material_variant.name IS '物料规格名称；同一物料内忽略大小写唯一';
COMMENT ON COLUMN inv_material_variant.status IS '规格状态：0 启用，可用于新业务；1 停用，仅保留历史引用';
DO $$ DECLARE app_role text; BEGIN
 SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE ON public.inv_material_variant TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.inv_material_variant_variant_id_seq TO %I',app_role);
END $$;
COMMIT;

BEGIN;
ALTER TABLE inv_material ADD COLUMN IF NOT EXISTS variant_fields jsonb NOT NULL DEFAULT '[]';
ALTER TABLE inv_material_variant ADD COLUMN IF NOT EXISTS attributes jsonb NOT NULL DEFAULT '{}';
COMMENT ON COLUMN inv_material.variant_fields IS '物料档案中改为逐规格维护的模板稳定字段键数组；未列出的字段仍按物料主档共用';
COMMENT ON COLUMN inv_material_variant.attributes IS '本物料规格的属性值 JSON，以模板稳定字段键为键，字段值类型和单位遵循所选规格模板';
COMMIT;

BEGIN;
-- 补充常用辅料和包材；保留已有模板编码、字段及档案值。
INSERT INTO inv_material_spec_template(template_code,template_name,sort_order,fields) VALUES
 ('hook_loop','粘扣带',65,'[{"key":"material","label":"材质","type":"text"},{"key":"surfaceType","label":"钩面或毛面","type":"text"},{"key":"width","label":"带宽","type":"decimal","unit":"mm"},{"key":"length","label":"裁切长度","type":"decimal","unit":"mm"},{"key":"backing","label":"背胶或背衬","type":"text"}]'::jsonb),
 ('cord_hardware','绳扣、绳头与环扣',66,'[{"key":"hardwareType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"openingWidth","label":"内口宽","type":"decimal","unit":"mm"},{"key":"cordDiameter","label":"适配绳径","type":"decimal","unit":"mm"},{"key":"surfaceFinish","label":"表面处理","type":"text"}]'::jsonb),
 ('padding','垫肩、胸垫与定型辅料',67,'[{"key":"paddingType","label":"辅料类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"shape","label":"形状","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"}]'::jsonb),
 ('filling','填充料与保暖棉',68,'[{"key":"fillingType","label":"填充类型","type":"text"},{"key":"composition","label":"成分","type":"text"},{"key":"form","label":"形态","type":"text"},{"key":"weightGsm","label":"克重","type":"decimal","unit":"g/m²"},{"key":"width","label":"幅宽","type":"decimal","unit":"cm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"},{"key":"fillPower","label":"蓬松度","type":"decimal","unit":"cu in/oz"}]'::jsonb),
 ('decoration','章标、贴花与装饰辅料',71,'[{"key":"decorationType","label":"装饰类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"attachment","label":"固定方式","type":"text"},{"key":"artworkVersion","label":"图案版本","type":"text"}]'::jsonb),
 ('heat_transfer','烫画、热转印与压胶材料',72,'[{"key":"transferType","label":"材料类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"artworkVersion","label":"图案版本","type":"text"},{"key":"applicationReference","label":"供应商工艺资料编号","type":"text"}]'::jsonb),
 ('sticker','贴纸、条码标与不干胶',73,'[{"key":"stickerType","label":"标签类型","type":"text"},{"key":"faceMaterial","label":"面材","type":"text"},{"key":"adhesive","label":"胶型","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"printVersion","label":"印刷版号","type":"text"}]'::jsonb),
 ('tag_fastener','吊绳、胶针与吊牌扣',74,'[{"key":"fastenerType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"cordDiameter","label":"绳径","type":"decimal","unit":"mm"},{"key":"closure","label":"扣合方式","type":"text"}]'::jsonb),
 ('hanger','衣架、裤架与包装夹',101,'[{"key":"hangerType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"clipSpacing","label":"夹距","type":"decimal","unit":"mm"},{"key":"hookType","label":"挂钩形式","type":"text"}]'::jsonb),
 ('paper_insert','拷贝纸、衬纸与纸板',102,'[{"key":"paperType","label":"纸品类型","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"paperWeight","label":"克重","type":"decimal","unit":"g/m²"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"}]'::jsonb),
 ('packing_supply','封箱胶带、捆扎带与缠绕膜',103,'[{"key":"supplyType","label":"包材类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"μm"},{"key":"rollLength","label":"公称卷长","type":"decimal","unit":"m"}]'::jsonb),
 ('desiccant','干燥剂与防护小包',104,'[{"key":"agentType","label":"内容物类型","type":"text"},{"key":"netWeight","label":"单包净重","type":"decimal","unit":"g"},{"key":"packMaterial","label":"外袋材质","type":"text"},{"key":"length","label":"包长","type":"decimal","unit":"mm"},{"key":"width","label":"包宽","type":"decimal","unit":"mm"}]'::jsonb),
 ('production_supply','裁剪、缝制与印花耗材',105,'[{"key":"supplyType","label":"耗材类型","type":"text"},{"key":"material","label":"材质或成分","type":"text"},{"key":"equipmentModel","label":"适用设备或型号","type":"text"},{"key":"productGrade","label":"牌号或系列","type":"text"},{"key":"technicalReference","label":"技术资料编号","type":"text"}]'::jsonb),
 ('general','通用物料',110,'[{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"height","label":"高度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"},{"key":"diameter","label":"直径","type":"decimal","unit":"mm"},{"key":"technicalReference","label":"技术资料编号","type":"text"}]'::jsonb)
ON CONFLICT (template_code) DO NOTHING;

WITH additions(code, fields) AS (VALUES
 ('fabric','[{"key":"fabricFinish","label":"后整理","type":"text"},{"key":"fabricConstruction","label":"纱支与织物规格","type":"text"}]'::jsonb),
 ('interlining','[{"key":"adhesiveType","label":"胶点类型","type":"text"},{"key":"applicationReference","label":"供应商压烫资料编号","type":"text"}]'::jsonb),
 ('zipper','[{"key":"sliderLock","label":"拉头锁定形式","type":"text"},{"key":"pullerType","label":"拉片款式","type":"text"},{"key":"tapeMaterial","label":"布带材质","type":"text"}]'::jsonb),
 ('label','[{"key":"foldType","label":"折法","type":"text"},{"key":"printVersion","label":"印刷或织造版号","type":"text"},{"key":"language","label":"标识语言","type":"text"}]'::jsonb),
 ('carton','[{"key":"boxStyle","label":"箱型","type":"text"},{"key":"boardGrade","label":"纸板等级","type":"text"},{"key":"printVersion","label":"印刷版号","type":"text"}]'::jsonb)
)
UPDATE inv_material_spec_template t
SET fields = t.fields || COALESCE((
  SELECT jsonb_agg(f.value ORDER BY f.position)
  FROM jsonb_array_elements(a.fields) WITH ORDINALITY f(value, position)
  WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(t.fields) old WHERE old->>'key'=f.value->>'key')
), '[]'::jsonb)
FROM additions a WHERE t.template_code=a.code;
COMMIT;

-- 款式 BOM 一期：单据、版本、制单及发布信息与菜单权限。
BEGIN;
CREATE TABLE IF NOT EXISTS rd_style_bom (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    bom_code varchar(60) NOT NULL,
    preparer_id bigint,
    style_id bigint NOT NULL REFERENCES rd_style(style_id),
    version integer NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','released')),
    remark varchar(500) NOT NULL DEFAULT '',
    rules jsonb NOT NULL,
    update_time timestamptz NOT NULL DEFAULT now(),
    UNIQUE(style_id,version)
);
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS create_by bigint;
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS create_time timestamptz;
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS update_by bigint;
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS release_by bigint;
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS release_time timestamptz;
COMMENT ON COLUMN rd_style_bom.create_by IS '创建本 BOM 版本的登录人员，关联 sys_user.user_id；旧版本可能为空';
COMMENT ON COLUMN rd_style_bom.create_time IS '本 BOM 版本首次保存时间；迁移前历史版本可能为空';
COMMENT ON COLUMN rd_style_bom.update_by IS '最近一次保存本 BOM 版本的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN rd_style_bom.release_by IS '发布本 BOM 版本的登录人员，关联 sys_user.user_id；草稿或历史版本可能为空';
COMMENT ON COLUMN rd_style_bom.release_time IS '本 BOM 版本正式发布的时间；未发布版本为空';
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS bom_code varchar(60);
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS preparer_id bigint;
-- 历史记录使用专用前缀保留稳定标识，避免与新单日期流水冲突。
UPDATE rd_style_bom SET bom_code = 'BOM-HIST-' || id WHERE bom_code IS NULL OR btrim(bom_code) = '';
UPDATE rd_style_bom SET preparer_id = create_by WHERE preparer_id IS NULL;
ALTER TABLE rd_style_bom ALTER COLUMN bom_code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rd_style_bom_code ON rd_style_bom (lower(bom_code));
COMMENT ON COLUMN rd_style_bom.bom_code IS 'BOM 单号，全局唯一，可编辑；系统默认按日期和流水号生成';
COMMENT ON COLUMN rd_style_bom.preparer_id IS '业务制单人，关联 sys_user.user_id；可在制单时选择，与记录创建人和实际操作审计人分开';

COMMENT ON TABLE rd_style_bom IS '款式用料清单版本，发布版保留用料与单位快照';
COMMENT ON COLUMN rd_style_bom.id IS '款式 BOM 版本主键，供订单算料和审批关联使用';
COMMENT ON COLUMN rd_style_bom.bom_code IS 'BOM 单号，全局唯一，可编辑；系统默认按日期和流水号生成';
COMMENT ON COLUMN rd_style_bom.preparer_id IS '业务制单人，关联 sys_user.user_id；可在制单时选择，与记录创建人和实际操作审计人分开';
COMMENT ON COLUMN rd_style_bom.style_id IS '所属款式，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_bom.version IS '同一款式内唯一的正整数版本号；新建时建议递增，草稿可编辑';
COMMENT ON COLUMN rd_style_bom.status IS '版本状态：draft 草稿，可修改；released 已发布，供订单算料使用且不可覆盖';
COMMENT ON COLUMN rd_style_bom.remark IS '本 BOM 版本的变更原因或业务说明';
COMMENT ON COLUMN rd_style_bom.rules IS '结构化用料 JSON，按子款及适用颜色尺码保存物料、物料规格、颜色、单位、换算系数和用量快照';
COMMENT ON COLUMN rd_style_bom.update_time IS '本 BOM 版本最近保存或发布的时间';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762900000000000130,parent_id,'款式 BOM',20,'style-boms','rd/StyleBomManage','N','Y','C','0','0','rd:bom:list','#',now()
FROM sys_menu WHERE perms='rd:style:list'
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='rd:bom:list') LIMIT 1;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762900000000000131,menu_id,'编辑与发布 BOM',1,'#','','N','Y','F','0','0','rd:bom:edit','#',now()
FROM sys_menu WHERE perms='rd:bom:list'
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='rd:bom:edit');
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE ON rd_style_bom TO %I',app_role);
 EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE rd_style_bom_id_seq TO %I',app_role);
END $$;
COMMIT;

-- ------------------------------------------------------------
-- 人员业务职责（多选）：责任人选择资格与岗位/系统权限分离。
-- ------------------------------------------------------------
BEGIN;
CREATE TABLE IF NOT EXISTS sys_user_business_duty (
    user_id bigint NOT NULL REFERENCES sys_user(user_id) ON DELETE CASCADE,
    duty_code varchar(24) NOT NULL CHECK (duty_code IN ('SALES','FOLLOWER','PURCHASER','PLANNER','WAREHOUSE','QUALITY')),
    create_time timestamp NOT NULL DEFAULT now(),
    CONSTRAINT sys_user_business_duty_pk PRIMARY KEY(user_id,duty_code)
);
COMMENT ON TABLE sys_user_business_duty IS '人员业务职责，多选标识人员可担任的业务单据责任身份，不等同于岗位、系统角色或操作权限';
COMMENT ON COLUMN sys_user_business_duty.user_id IS '人员ID，关联 sys_user.user_id';
COMMENT ON COLUMN sys_user_business_duty.duty_code IS '业务职责代码：SALES业务员、FOLLOWER跟单员、PURCHASER采购员、PLANNER计划员、WAREHOUSE仓管员、QUALITY质检员';
COMMENT ON COLUMN sys_user_business_duty.create_time IS '职责关联建立时间';
CREATE INDEX IF NOT EXISTS ix_sys_user_business_duty_code ON sys_user_business_duty(duty_code,user_id);
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON sys_user_business_duty TO %I',app_role);
END $$;
COMMIT;
-- 审批模块独立挂载角色权限；个人功能此前对登录人员开放，升级时保留现有角色访问。
BEGIN;
CREATE TEMP TABLE approval_permission_migration_flag ON COMMIT DROP AS
SELECT NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000000) AS first_install;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(1763200000000000000,0,'审批',70,'approval','','N','Y','M','0','0','','#',now()),
(1763200000000000001,1763200000000000000,'流程定义',10,'definitions','approval/definitions','N','Y','C','0','0','workflow:definition:list','#',now()),
(1763200000000000002,1763200000000000000,'我的待办',20,'waiting','approval/waiting','N','Y','C','0','0','approval:waiting:list','#',now()),
(1763200000000000003,1763200000000000000,'我发起的',30,'initiated','approval/initiated','N','Y','C','0','0','approval:initiated:list','#',now()),
(1763200000000000004,1763200000000000000,'我的已办',40,'finished','approval/finished','N','Y','C','0','0','approval:finished:list','#',now()),
(1763200000000000005,1763200000000000000,'审批对象配置',50,'bindings','approval/bindings','N','Y','C','0','0','approval:binding:list','#',now()),
(1763200000000000006,1763200000000000002,'审批办理',10,'#','','N','Y','F','0','0','approval:waiting:handle','#',now()),
(1763200000000000007,1763200000000000005,'配置审批对象',10,'#','','N','Y','F','0','0','approval:binding:edit','#',now()),
(1763200000000000008,1763200000000000001,'新增流程',10,'#','','N','Y','F','0','0','workflow:definition:add','#',now()),
(1763200000000000009,1763200000000000001,'发布流程',20,'#','','N','Y','F','0','0','workflow:definition:publish','#',now())
ON CONFLICT (menu_id) DO NOTHING;
-- 迁移前个人审批功能未限制角色；沿用访问权限，之后可在角色管理中调整。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE (SELECT first_install FROM approval_permission_migration_flag) AND r.del_flag='0' AND m.menu_id IN (1763200000000000000,1763200000000000002,1763200000000000003,1763200000000000004,1763200000000000006)
ON CONFLICT DO NOTHING;
-- 管理权限按已有等价权限迁移，不向无相关权限的角色额外授予配置权。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, target.menu_id FROM sys_role_menu rm JOIN sys_menu old ON old.menu_id=rm.menu_id
JOIN sys_menu target ON target.menu_id=CASE old.perms WHEN 'workflow:definition:list' THEN 1763200000000000001 WHEN 'workflow:definition:add' THEN 1763200000000000008 WHEN 'workflow:definition:publish' THEN 1763200000000000009 WHEN 'system:config:list' THEN 1763200000000000005 WHEN 'system:config:edit' THEN 1763200000000000007 END
WHERE (SELECT first_install FROM approval_permission_migration_flag)
ON CONFLICT DO NOTHING;
COMMIT;
-- 将框架预置的部门负责人表达式接到织云组织架构。
UPDATE flow_spel
SET method_name='selectOrgLeaderById', method_params='initiatorOrgId',
    view_spel='#{@spelRuleComponent.selectOrgLeaderById(#initiatorOrgId)}',
    remark='发起人所在部门负责人', update_time=now()
WHERE component_name='spelRuleComponent'
  AND method_name='selectDeptLeaderById' AND method_params='initiatorDeptId';

-- 沿用框架原有按钮 ID 和角色关系，将维护权限显示在审批模块下。
UPDATE sys_menu
SET parent_id=1763200000000000001, status='0'
WHERE parent_id=1761400000000011620
  AND perms IN ('workflow:definition:query','workflow:definition:edit','workflow:definition:remove',
                'workflow:definition:export','workflow:definition:import','workflow:definition:copy','workflow:definition:active')
  AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000001);
-- 将框架原有流程分类菜单挂入审批模块，保留原有权限编号和角色授权。
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程类别',path='categories',component='approval/categories',status='0',visible='0'
WHERE menu_id=1761400000000011622 AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000000);
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT role_id,1763200000000000000 FROM sys_role_menu WHERE menu_id=1761400000000011622
ON CONFLICT DO NOTHING;
-- 在审批模块注册个人抄送入口，保留此前可查看抄送的角色访问。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
VALUES(1763200000000000010,1763200000000000000,'我的抄送',45,'copies','approval/copies','N','Y','C','0','0','approval:copy:list','#',now())
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT role_id,1763200000000000010 FROM sys_role_menu WHERE menu_id=1761400000000011628
ON CONFLICT DO NOTHING;
-- 框架前端能力接入：恢复原有菜单入口并补充新控制台权限。
-- 保留已存在菜单ID和角色授权；新入口仅登记为可分配权限，不默认授予任何角色。
BEGIN;

-- 在应用的审批和系统管理目录下补齐框架页面入口。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.menu_id,v.parent_id,v.menu_name,v.order_num,v.path,v.component,'N','Y','C','0','0',v.perms,'#',now()
FROM (VALUES
 (1763300000000000001::bigint,1763200000000000000::bigint,'流程实例',60,'instances','approval/instances','workflow:instance:list'),
 (1763300000000000002::bigint,1763200000000000000::bigint,'任务监控',70,'task-monitor','approval/task-monitor','workflow:task:list'),
 (1763300000000000003::bigint,1763200000000000000::bigint,'流程表达式',80,'expressions','approval/expressions','workflow:spel:list'),
 (1763300000000000004::bigint,1763200000000000000::bigint,'请假流程示例',90,'leave-demo','approval/leave-demo','workflow:leave:list'),
 (1763300000000000011::bigint,1761400000000000001::bigint,'通知公告',110,'notice','system/notice/index','system:notice:list'),
 (1763300000000000012::bigint,1761400000000000001::bigint,'登录日志',120,'loginInfo','monitor/loginInfo/index','monitor:logininfo:list'),
 (1763300000000000013::bigint,1761400000000000001::bigint,'代码生成',130,'generator','tool/gen/index','tool:gen:list'),
 (1763300000000000014::bigint,1761400000000000001::bigint,'Admin 监控',140,'admin-monitor','monitor/admin/index','monitor:admin:list'),
 (1763300000000000015::bigint,1761400000000000001::bigint,'SnailJob 任务调度',150,'snailjob','system/snailjob/index','system:snailjob:list'),
 (1763300000000000016::bigint,1761400000000000001::bigint,'SnailAI 智能体',160,'snailai','system/snailai/index','system:snailai:list'),
 (1763300000000000017::bigint,1761400000000000001::bigint,'接口文档',170,'api-docs','system/api-docs/index','system:api-docs:list')
) AS v(menu_id,parent_id,menu_name,order_num,path,component,perms)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=v.perms)
ON CONFLICT(menu_id) DO NOTHING;

-- 复用旧框架菜单记录和角色关系，迁移到当前系统目录并启用入口。
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程实例',order_num=60,path='instances',component='approval/instances',menu_type='C',visible='0',status='0'
WHERE perms='workflow:instance:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='任务监控',order_num=70,path='task-monitor',component='approval/task-monitor',menu_type='C',visible='0',status='0'
WHERE perms='workflow:task:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='流程表达式',order_num=80,path='expressions',component='approval/expressions',menu_type='C',visible='0',status='0'
WHERE perms='workflow:spel:list';
UPDATE sys_menu SET parent_id=1763200000000000000,menu_name='请假流程示例',order_num=90,path='leave-demo',component='approval/leave-demo',menu_type='C',visible='0',status='0'
WHERE perms='workflow:leave:list';

UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='通知公告',order_num=110,path='notice',component='system/notice/index',menu_type='C',visible='0',status='0'
WHERE perms='system:notice:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='登录日志',order_num=120,path='loginInfo',component='monitor/loginInfo/index',menu_type='C',visible='0',status='0'
WHERE perms='monitor:logininfo:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='代码生成',order_num=130,path='generator',component='tool/gen/index',menu_type='C',visible='0',status='0'
WHERE perms='tool:gen:list';
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='Admin 监控',order_num=140,path='admin-monitor',component='monitor/admin/index',menu_type='C',visible='0',status='0'
WHERE perms='monitor:admin:list';

-- SnailJob、SnailAI 与接口文档入口；升级时按固定菜单 ID 对齐，不复制已有权限标识。
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='SnailJob 任务调度',order_num=150,path='snailjob',component='system/snailjob/index',menu_type='C',visible='0',status='0',perms='system:snailjob:list'
WHERE menu_id=1763300000000000015;
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='SnailAI 智能体',order_num=160,path='snailai',component='system/snailai/index',menu_type='C',visible='0',status='0',perms='system:snailai:list'
WHERE menu_id=1763300000000000016;
UPDATE sys_menu SET parent_id=1761400000000000001,menu_name='接口文档',order_num=170,path='api-docs',component='system/api-docs/index',menu_type='C',visible='0',status='0',perms='system:api-docs:list'
WHERE menu_id=1763300000000000017;

-- 确保框架端点所需的可分配操作权限存在；不自动写入 sys_role_menu。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.menu_id,parent.menu_id,v.menu_name,v.order_num,'#','','N','Y','F','0','0',v.perms,'#',now()
FROM (VALUES
 (1763300000000000101::bigint,'实例查询','workflow:instance:query','workflow:instance:list',1),
 (1763300000000000102::bigint,'实例变量查看','workflow:instance:variableQuery','workflow:instance:list',2),
 (1763300000000000103::bigint,'实例变量修改','workflow:instance:variable','workflow:instance:list',3),
 (1763300000000000104::bigint,'实例暂停恢复','workflow:instance:active','workflow:instance:list',4),
 (1763300000000000105::bigint,'实例删除','workflow:instance:remove','workflow:instance:list',5),
 (1763300000000000106::bigint,'实例作废','workflow:instance:invalid','workflow:instance:list',6),
 (1763300000000000107::bigint,'实例撤销','workflow:instance:cancel','workflow:instance:list',7),
 (1763300000000000108::bigint,'任务改派','workflow:task:edit','workflow:task:list',1),
 (1763300000000000109::bigint,'表达式查看','workflow:spel:query','workflow:spel:list',1),
 (1763300000000000110::bigint,'表达式新增','workflow:spel:add','workflow:spel:list',2),
 (1763300000000000111::bigint,'表达式修改','workflow:spel:edit','workflow:spel:list',3),
 (1763300000000000112::bigint,'表达式删除','workflow:spel:remove','workflow:spel:list',4),
 (1763300000000000113::bigint,'请假示例查看','workflow:leave:query','workflow:leave:list',1),
 (1763300000000000114::bigint,'请假示例新增','workflow:leave:add','workflow:leave:list',2),
 (1763300000000000115::bigint,'请假示例修改','workflow:leave:edit','workflow:leave:list',3),
 (1763300000000000116::bigint,'请假示例删除','workflow:leave:remove','workflow:leave:list',4),
 (1763300000000000117::bigint,'通知公告详情','system:notice:query','system:notice:list',1),
 (1763300000000000118::bigint,'通知公告新增','system:notice:add','system:notice:list',2),
 (1763300000000000119::bigint,'通知公告修改','system:notice:edit','system:notice:list',3),
 (1763300000000000120::bigint,'通知公告删除','system:notice:remove','system:notice:list',4),
 (1763300000000000121::bigint,'代码生成查看','tool:gen:query','tool:gen:list',1),
 (1763300000000000122::bigint,'代码生成配置','tool:gen:edit','tool:gen:list',2),
 (1763300000000000123::bigint,'代码生成删除','tool:gen:remove','tool:gen:list',3),
 (1763300000000000124::bigint,'代码生成导入','tool:gen:import','tool:gen:list',4),
 (1763300000000000125::bigint,'代码生成预览','tool:gen:preview','tool:gen:list',5),
 (1763300000000000126::bigint,'代码下载','tool:gen:code','tool:gen:list',6),
 (1763300000000000127::bigint,'登录日志删除','monitor:logininfo:remove','monitor:logininfo:list',1),
 (1763300000000000128::bigint,'登录日志导出','monitor:logininfo:export','monitor:logininfo:list',2),
 (1763300000000000129::bigint,'登录账户解锁','monitor:logininfo:unlock','monitor:logininfo:list',3)
) AS v(menu_id,menu_name,perms,parent_perm,order_num)
JOIN sys_menu parent ON parent.perms=v.parent_perm AND parent.menu_type='C'
WHERE NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms=v.perms)
ON CONFLICT(menu_id) DO NOTHING;

-- 将已存在的按钮授权节点挂到对应功能页面下，保留其 menu_id 和角色授权关系。
UPDATE sys_menu child SET parent_id=parent.menu_id,status='0'
FROM sys_menu parent
WHERE child.menu_type='F'
  AND ((child.perms LIKE 'workflow:instance:%' AND parent.perms='workflow:instance:list')
    OR (child.perms LIKE 'workflow:task:%' AND parent.perms='workflow:task:list')
    OR (child.perms LIKE 'workflow:spel:%' AND parent.perms='workflow:spel:list')
    OR (child.perms LIKE 'workflow:leave:%' AND parent.perms='workflow:leave:list')
    OR (child.perms LIKE 'system:notice:%' AND parent.perms='system:notice:list')
    OR (child.perms LIKE 'tool:gen:%' AND parent.perms='tool:gen:list')
    OR (child.perms LIKE 'monitor:logininfo:%' AND parent.perms='monitor:logininfo:list'));

COMMIT;

-- SnailAI ERP 员工对话和 MCP 调用诊断记录。
-- 管理页与 API 使用 system:snailai:trace:list；脚本不默认授权任何角色。
BEGIN;

CREATE TABLE IF NOT EXISTS sys_snail_ai_chat_trace (
    trace_id          varchar(36)  NOT NULL,
    user_id           bigint       NOT NULL,
    nickname          varchar(100) NOT NULL DEFAULT '',
    agent_id          bigint,
    conversation_id   varchar(128) NOT NULL DEFAULT '',
    question          text         NOT NULL DEFAULT '',
    answer            text         NOT NULL DEFAULT '',
    attachment_summary text        NOT NULL DEFAULT '[]',
    visible_tools     text         NOT NULL DEFAULT '[]',
    status            varchar(24)  NOT NULL DEFAULT 'RUNNING',
    error_message     text         NOT NULL DEFAULT '',
    duration_ms       bigint       NOT NULL DEFAULT 0,
    started_at        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       timestamp,
    CONSTRAINT sys_snail_ai_chat_trace_pk PRIMARY KEY (trace_id)
);

COMMENT ON TABLE sys_snail_ai_chat_trace IS 'SnailAI ERP员工对话诊断记录，含提问、回复、可见工具和运行状态；限授权管理员查看';
COMMENT ON COLUMN sys_snail_ai_chat_trace.trace_id IS 'ERP 本地随机诊断编号，不是 SnailAI 对话编号';
COMMENT ON COLUMN sys_snail_ai_chat_trace.user_id IS '发起本轮对话的 ERP 员工ID';
COMMENT ON COLUMN sys_snail_ai_chat_trace.nickname IS '发起时的员工昵称快照';
COMMENT ON COLUMN sys_snail_ai_chat_trace.agent_id IS '本轮使用的 SnailAI 智能体ID';
COMMENT ON COLUMN sys_snail_ai_chat_trace.conversation_id IS 'SnailAI 对话编号；空白表示上游未提供，只能按本轮诊断编号查看，不与其他轮次合并';
COMMENT ON COLUMN sys_snail_ai_chat_trace.question IS '员工本轮提问，长度受应用限制';
COMMENT ON COLUMN sys_snail_ai_chat_trace.answer IS '本轮模型流式文本回复';
COMMENT ON COLUMN sys_snail_ai_chat_trace.attachment_summary IS '附件名称、类型、大小和解析字符数，不保存原始文件或音频';
COMMENT ON COLUMN sys_snail_ai_chat_trace.visible_tools IS '按发起员工权限过滤后交给本轮模型的工具清单';
COMMENT ON COLUMN sys_snail_ai_chat_trace.status IS '本轮状态：RUNNING、SUCCEEDED、FAILED 或 CANCELLED';
COMMENT ON COLUMN sys_snail_ai_chat_trace.error_message IS '失败或取消原因，服务端已限制长度并脱敏';
COMMENT ON COLUMN sys_snail_ai_chat_trace.duration_ms IS '本轮处理耗时，单位毫秒';
COMMENT ON COLUMN sys_snail_ai_chat_trace.started_at IS '本轮开始时间';
COMMENT ON COLUMN sys_snail_ai_chat_trace.finished_at IS '本轮结束时间';

CREATE INDEX IF NOT EXISTS idx_snail_ai_trace_started ON sys_snail_ai_chat_trace (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_snail_ai_trace_user_started ON sys_snail_ai_chat_trace (user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_snail_ai_trace_status_started ON sys_snail_ai_chat_trace (status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_snail_ai_trace_conversation ON sys_snail_ai_chat_trace (conversation_id);

CREATE TABLE IF NOT EXISTS sys_snail_ai_tool_trace (
    event_id           varchar(36)  NOT NULL,
    trace_id           varchar(36)  NOT NULL,
    tool_name          varchar(160) NOT NULL,
    tool_description   varchar(1000) NOT NULL DEFAULT '',
    arguments_preview  text         NOT NULL DEFAULT '',
    result_preview     text         NOT NULL DEFAULT '',
    status             varchar(24)  NOT NULL DEFAULT 'RUNNING',
    error_message      text         NOT NULL DEFAULT '',
    duration_ms        bigint       NOT NULL DEFAULT 0,
    started_at         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at        timestamp,
    CONSTRAINT sys_snail_ai_tool_trace_pk PRIMARY KEY (event_id),
    CONSTRAINT sys_snail_ai_tool_trace_chat_fk FOREIGN KEY (trace_id)
        REFERENCES sys_snail_ai_chat_trace (trace_id) ON DELETE CASCADE
);

COMMENT ON TABLE sys_snail_ai_tool_trace IS 'SnailAI 本轮 MCP/Skill 工具调用诊断事件';
COMMENT ON COLUMN sys_snail_ai_tool_trace.event_id IS '工具调用事件随机编号';
COMMENT ON COLUMN sys_snail_ai_tool_trace.trace_id IS '所属 ERP 员工对话诊断编号';
COMMENT ON COLUMN sys_snail_ai_tool_trace.tool_name IS '实际调用的 MCP 或本地 Skill 工具名';
COMMENT ON COLUMN sys_snail_ai_tool_trace.tool_description IS '工具说明快照';
COMMENT ON COLUMN sys_snail_ai_tool_trace.arguments_preview IS '历史参数预览字段；当前写入未记录提示，不保存工具参数正文';
COMMENT ON COLUMN sys_snail_ai_tool_trace.result_preview IS '历史结果预览字段；当前写入未记录提示，不保存工具结果正文';
COMMENT ON COLUMN sys_snail_ai_tool_trace.status IS '工具调用状态：RUNNING、SUCCEEDED、FAILED 或 NOT_APPROVED';
COMMENT ON COLUMN sys_snail_ai_tool_trace.error_message IS '工具失败或未获确认原因';
COMMENT ON COLUMN sys_snail_ai_tool_trace.duration_ms IS '工具调用耗时，单位毫秒';
COMMENT ON COLUMN sys_snail_ai_tool_trace.started_at IS '工具调用开始时间';
COMMENT ON COLUMN sys_snail_ai_tool_trace.finished_at IS '工具调用结束时间';

CREATE INDEX IF NOT EXISTS idx_snail_ai_tool_trace_parent_time ON sys_snail_ai_tool_trace (trace_id, started_at);

INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1763300000000000018,1761400000000000001,'智能体对话记录',180,'snailai-traces','system/snailai/traces','N','Y','C','0','0','system:snailai:trace:list','#',now()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:snailai:trace:list')
ON CONFLICT(menu_id) DO NOTHING;

UPDATE sys_menu
   SET parent_id=1761400000000000001, menu_name='智能体对话记录', order_num=180,
       path='snailai-traces', component='system/snailai/traces', menu_type='C', visible='0', status='0',
       perms='system:snailai:trace:list'
 WHERE menu_id=1763300000000000018;

COMMIT;

-- 订单和审批基础表：先建立订单及流程关联，再建立需求计划和反审核记录。

-- 同步开发迁移：客户订单_20261114.sql
BEGIN;
CREATE TABLE IF NOT EXISTS biz_customer_order (
 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 order_no varchar(60) NOT NULL,
 customer_id bigint NOT NULL REFERENCES customer_master(customer_id),
 company_id bigint NOT NULL REFERENCES sys_company(company_id),
 sales_id bigint NOT NULL REFERENCES sys_user(user_id),
 follower_id bigint REFERENCES sys_user(user_id),
 created_by bigint NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'draft' CHECK(status IN ('draft','submitted','approved','cancelled')),
 document jsonb NOT NULL,
 demand jsonb NOT NULL DEFAULT '[]',
 revision integer NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_biz_customer_order_no ON biz_customer_order(lower(order_no));
CREATE INDEX IF NOT EXISTS ix_biz_customer_order_customer ON biz_customer_order(customer_id);
COMMENT ON TABLE biz_customer_order IS '客户订单主单，按 PO 保存订购、配套和交付分配，审核后冻结需求快照';
COMMENT ON COLUMN biz_customer_order.id IS '客户订单内部主键，供订单明细及派生单据关联';
COMMENT ON COLUMN biz_customer_order.order_no IS '系统订单号，唯一索引按忽略大小写方式校验，供用户检索和单据引用';
COMMENT ON COLUMN biz_customer_order.customer_id IS '客户，关联 customer_master.customer_id';
COMMENT ON COLUMN biz_customer_order.company_id IS '归属公司，关联 sys_company.company_id，首次保存后锁定';
COMMENT ON COLUMN biz_customer_order.sales_id IS '业务员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_customer_order.follower_id IS '跟单员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_customer_order.created_by IS '实际制单员工，关联 sys_user.user_id；与业务员、跟单员字段分开记录';
COMMENT ON COLUMN biz_customer_order.status IS '单据状态：draft 草稿、submitted 已提交待审核、approved 已审核、cancelled 已取消';
COMMENT ON COLUMN biz_customer_order.document IS '订单业务快照 JSON：订单日期、客户与公司展示信息、PO、款式/颜色/尺码、交期、供料安排、单价和交付分配；金额按元保留两位小数';
COMMENT ON COLUMN biz_customer_order.demand IS '订单算料快照 JSON：按 PO、款式规格、物料颜色与规格保存需求；含库存单位数量、采购单位数量、换算系数及 BOM 版本，空数组表示未算料';
COMMENT ON COLUMN biz_customer_order.revision IS '订单并发修订号，创建时为1，每次成功保存递增，用于拒绝基于旧版本的覆盖';
COMMENT ON COLUMN biz_customer_order.created_at IS '订单记录首次创建时间';
COMMENT ON COLUMN biz_customer_order.updated_at IS '订单记录最后一次成功保存时间';
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1763100000000000100,parent_id,'客户订单',5,'manage','','N','Y','C','0','0','order:customer-order:list','#',now()
FROM sys_menu WHERE perms='system:customer:list' AND menu_type='C'
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.id,m.menu_id,v.name,v.n,'#','','N','Y','F','0','0',v.perm,'#',now()
FROM sys_menu m CROSS JOIN (VALUES
 (1763100000000000101,'制单与提交',1,'order:customer-order:edit'),
 (1763100000000000102,'订单审核',2,'order:customer-order:review'),
 (1763100000000000103,'取消订单',3,'order:customer-order:cancel')
) v(id,name,n,perm) WHERE m.perms='order:customer-order:list'
ON CONFLICT(menu_id) DO NOTHING;
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT, INSERT, UPDATE ON biz_customer_order TO %I',app_role);
 EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE biz_customer_order_id_seq TO %I',app_role);
END $$;
COMMIT;

-- 同步开发迁移：审批对象流程绑定_20261114.sql
-- 业务对象与已发布的 Warm-Flow 流程定义绑定；流程结构和待办仍由引擎维护。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_approval_binding (
    business_type varchar(48) PRIMARY KEY,
    flow_code varchar(40) NOT NULL,
    update_by bigint NOT NULL,
    update_time timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_approval_binding IS '业务审批对象与 Warm-Flow 流程编码的配置绑定，不保存流程节点或待办';
COMMENT ON COLUMN sys_approval_binding.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；当前仅客户订单支持实际发起流程，其余须完成业务接入后方可启用';
COMMENT ON COLUMN sys_approval_binding.flow_code IS '绑定的流程定义编码，关联 flow_definition.flow_code；仅允许已发布且启用的流程，节点由 Warm-Flow 维护';
COMMENT ON COLUMN sys_approval_binding.update_by IS '最后修改绑定的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN sys_approval_binding.update_time IS '最后一次新增或修改审批对象流程绑定的时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON sys_approval_binding TO %I',app_role);
END $$;
COMMIT;

-- 同步开发迁移：审批业务实例关联_20261114.sql
-- 业务单据与审批实例的稳定关联；流程节点、待办和审批意见仍由 Warm-Flow 保存。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_approval_business_instance (
    business_key varchar(36) PRIMARY KEY,
    business_type varchar(48) NOT NULL,
    business_id bigint NOT NULL,
    business_revision integer NOT NULL,
    flow_code varchar(40) NOT NULL,
    instance_id bigint UNIQUE,
    state varchar(16) NOT NULL CHECK (state IN ('starting','pending','approved','rejected','cancelled')),
    initiator_id bigint NOT NULL,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_time timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_business_active ON sys_approval_business_instance(business_type,business_id) WHERE state IN ('starting','pending');
COMMENT ON TABLE sys_approval_business_instance IS '业务对象与 Warm-Flow 审批实例的关联及发起快照，不重复存储流程节点与待办';
COMMENT ON COLUMN sys_approval_business_instance.business_key IS '业务审批关联键，使用随机值避免不同单据类型的业务主键冲突及可枚举猜测';
COMMENT ON COLUMN sys_approval_business_instance.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；当前仅客户订单支持实际发起流程，其余须完成业务接入后方可启用';
COMMENT ON COLUMN sys_approval_business_instance.business_id IS '来源业务单据主键；目标表由 business_type 决定，不能作为跨表外键';
COMMENT ON COLUMN sys_approval_business_instance.business_revision IS '发起审批时锁定的业务单据修订号，用于防止审批期间以旧版本覆盖新数据';
COMMENT ON COLUMN sys_approval_business_instance.flow_code IS '发起时选定的 Warm-Flow 流程定义编码；后续配置变更不影响此历史实例';
COMMENT ON COLUMN sys_approval_business_instance.instance_id IS 'Warm-Flow 审批引擎流程实例编号；实例节点、审批意见和待办由引擎保存';
COMMENT ON COLUMN sys_approval_business_instance.state IS '关联状态：starting 启动中、pending 审批中、approved 已通过、rejected 已驳回、cancelled 已撤销';
COMMENT ON COLUMN sys_approval_business_instance.initiator_id IS '发起审批的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN sys_approval_business_instance.create_time IS '本业务单据首次关联审批实例的发起时间';
COMMENT ON COLUMN sys_approval_business_instance.update_time IS '本关联记录最近一次同步审批实例状态的时间';
DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON sys_approval_business_instance TO %I',app_role);
END $$;
COMMIT;

-- 同步开发迁移：物料需求计划_20261117.sql
-- 物料需求计划单、来源明细快照与计划调整历史。
BEGIN;

-- 兼容既有订单派生计划表，并补齐单据查询、算料状态和业务流转字段。
CREATE TABLE IF NOT EXISTS biz_material_demand_plan (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL REFERENCES biz_customer_order(id),
    order_revision integer NOT NULL,
    plan_no varchar(64) NOT NULL UNIQUE,
    status varchar(16) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','submitted','approved','cancelled')),
    calculation_status varchar(16) NOT NULL DEFAULT 'pending' CHECK (calculation_status IN ('pending','ready','incomplete','outdated')),
    calculation_message text NOT NULL DEFAULT '',
    revision integer NOT NULL DEFAULT 1,
    source_order_no varchar(60) NOT NULL DEFAULT '',
    customer_id bigint,
    document jsonb NOT NULL DEFAULT '{}'::jsonb,
    workflow_instance_id bigint,
    created_by bigint NOT NULL,
    updated_by bigint,
    submitted_by bigint,
    approved_by bigint,
    approved_at timestamptz,
    cancel_reason varchar(500),
    review_note varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS calculation_status varchar(16) NOT NULL DEFAULT 'pending';
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS calculation_message text NOT NULL DEFAULT '';
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS source_order_no varchar(60) NOT NULL DEFAULT '';
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS customer_id bigint;
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS updated_by bigint;
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS submitted_by bigint;
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS approved_by bigint;
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS approved_at timestamptz;
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS cancel_reason varchar(500);
ALTER TABLE biz_material_demand_plan ADD COLUMN IF NOT EXISTS review_note varchar(500);
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_demand_plan_active_order
    ON biz_material_demand_plan(order_id) WHERE status <> 'cancelled';
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_status_updated
    ON biz_material_demand_plan(status,updated_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_source_order
    ON biz_material_demand_plan(source_order_no);

COMMENT ON TABLE biz_material_demand_plan IS '客户订单审核后生成的物料需求计划主单，保留订单修订、算料状态、审批状态及订单快照；取消单保留供追溯';
COMMENT ON COLUMN biz_material_demand_plan.id IS '需求计划内部主键，供需求明细、审批业务关联和下游采购计划引用';
COMMENT ON COLUMN biz_material_demand_plan.order_id IS '来源客户订单主键，关联 biz_customer_order.id；每张订单最多有一张未取消的有效计划';
COMMENT ON COLUMN biz_material_demand_plan.order_revision IS '生成或同步本计划时的客户订单修订号，用于判断算料依据是否变化';
COMMENT ON COLUMN biz_material_demand_plan.plan_no IS '物料需求计划单号，全表唯一，供列表检索、审批标题和单据穿透';
COMMENT ON COLUMN biz_material_demand_plan.status IS '单据状态：draft 草稿、submitted 审批中、approved 已审核、cancelled 已取消';
COMMENT ON COLUMN biz_material_demand_plan.calculation_status IS '算料状态：pending 待算料、ready 已有完整快照、incomplete 用料不完整、outdated 来源快照已过期';
COMMENT ON COLUMN biz_material_demand_plan.calculation_message IS '算料待处理原因快照；说明缺少 BOM、套装配套无法定位或订单用料不完整等提交阻断原因';
COMMENT ON COLUMN biz_material_demand_plan.revision IS '单据并发修订号，创建时为1，每次成功保存或状态流转递增';
COMMENT ON COLUMN biz_material_demand_plan.source_order_no IS '来源客户订单编号快照，复制 biz_customer_order.order_no 便于计划列表检索';
COMMENT ON COLUMN biz_material_demand_plan.customer_id IS '来源客户主键快照，关联 customer_master.customer_id；名称快照另存于 document';
COMMENT ON COLUMN biz_material_demand_plan.document IS '订单单头及 PO 快照，记录客户、公司、订单日期、供料安排、PO/交期、款式配套及最近审核说明，不代替结构化需求明细';
COMMENT ON COLUMN biz_material_demand_plan.workflow_instance_id IS 'Warm-Flow 审批实例编号；未绑定流程或未提交时为空，节点和意见由审批引擎保存';
COMMENT ON COLUMN biz_material_demand_plan.created_by IS '创建或自动派生本计划的登录人员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_material_demand_plan.updated_by IS '最后一次修改需求计划的人员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_material_demand_plan.submitted_by IS '最近一次提交审批的人员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_material_demand_plan.approved_by IS '未绑定流程时直接审核计划的人员，关联 sys_user.user_id；流程审批人由引擎记录';
COMMENT ON COLUMN biz_material_demand_plan.approved_at IS '计划最近一次审核通过时间；流程办理时间以 Warm-Flow 审批记录为准';
COMMENT ON COLUMN biz_material_demand_plan.cancel_reason IS '取消草稿计划的业务原因，最长500字';
COMMENT ON COLUMN biz_material_demand_plan.review_note IS '最近一次直接审核或退回说明；绑定流程后的节点意见保存在 Warm-Flow';
COMMENT ON COLUMN biz_material_demand_plan.created_at IS '需求计划首次创建时间';
COMMENT ON COLUMN biz_material_demand_plan.updated_at IS '需求计划最近一次保存或状态更新的时间';

-- 每行绑定来源 PO/色码和 BOM 物料行；引用 ID 仅作来源，不用外键阻止历史快照留存。
CREATE TABLE IF NOT EXISTS biz_material_demand_plan_item (
    item_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    plan_id bigint NOT NULL REFERENCES biz_material_demand_plan(id),
    line_no integer NOT NULL,
    po_number varchar(100) NOT NULL DEFAULT '',
    po_due_date date,
    supply_mode varchar(16) NOT NULL DEFAULT 'customer' CHECK (supply_mode IN ('customer','purchase','mixed')),
    source_line_key varchar(64) NOT NULL DEFAULT '',
    source_style_id bigint,
    source_style_code varchar(100) NOT NULL DEFAULT '',
    source_style_name varchar(200) NOT NULL DEFAULT '',
    set_spec_id bigint,
    set_spec_name varchar(200) NOT NULL DEFAULT '',
    child_style_id bigint,
    child_style_code varchar(100) NOT NULL DEFAULT '',
    child_style_name varchar(200) NOT NULL DEFAULT '',
    color_id bigint,
    color_name varchar(100) NOT NULL DEFAULT '',
    size_id bigint,
    size_name varchar(100) NOT NULL DEFAULT '',
    order_quantity numeric(20,8) NOT NULL DEFAULT 0,
    bom_id bigint,
    bom_version varchar(40) NOT NULL DEFAULT '',
    usage_type varchar(40) NOT NULL DEFAULT '',
    part_name varchar(200) NOT NULL DEFAULT '',
    material_id bigint NOT NULL,
    material_code varchar(100) NOT NULL DEFAULT '',
    material_name varchar(200) NOT NULL DEFAULT '',
    category_id bigint,
    category_name varchar(200) NOT NULL DEFAULT '',
    material_variant_id bigint,
    material_spec varchar(200) NOT NULL DEFAULT '',
    material_color_id bigint,
    material_color_name varchar(100) NOT NULL DEFAULT '',
    inventory_unit_id bigint,
    inventory_unit_name varchar(50) NOT NULL DEFAULT '',
    per_unit_usage numeric(24,8),
    original_quantity numeric(24,8) NOT NULL DEFAULT 0 CHECK (original_quantity >= 0),
    planned_quantity numeric(24,8) NOT NULL DEFAULT 0 CHECK (planned_quantity >= 0),
    purchase_units jsonb NOT NULL DEFAULT '[]'::jsonb,
    adjustment_reason varchar(500),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_material_demand_plan_item_line UNIQUE(plan_id,line_no)
);
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_item_plan ON biz_material_demand_plan_item(plan_id,sort_order,item_id);
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_item_material ON biz_material_demand_plan_item(material_id,material_variant_id,material_color_id);
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_item_po ON biz_material_demand_plan_item(plan_id,po_number);

COMMENT ON TABLE biz_material_demand_plan_item IS '物料需求计划的来源需求明细；保留 PO、订单色码、BOM、物料分类/规格/颜色、库存单位和采购单位换算快照';
COMMENT ON COLUMN biz_material_demand_plan_item.item_id IS '需求明细主键，供计划调整历史和采购需求分摊引用';
COMMENT ON COLUMN biz_material_demand_plan_item.plan_id IS '所属需求计划主键，关联 biz_material_demand_plan.id';
COMMENT ON COLUMN biz_material_demand_plan_item.line_no IS '本计划内的稳定明细行号，用于排序和并发保存校验';
COMMENT ON COLUMN biz_material_demand_plan_item.po_number IS '来源客户 PO 编号快照；不同 PO 的交期和供料方式不能在明细中丢失';
COMMENT ON COLUMN biz_material_demand_plan_item.po_due_date IS '来源 PO 默认交期快照，作为计划到料时间参考，不等同于采购承诺交期';
COMMENT ON COLUMN biz_material_demand_plan_item.supply_mode IS '供料方式：customer 客供、purchase 自采、mixed 客供＋自采；mixed 表示收货来源可选，不预拆数量';
COMMENT ON COLUMN biz_material_demand_plan_item.source_line_key IS '来源客户订单款式规格行键，关联 document 中的订单规格快照';
COMMENT ON COLUMN biz_material_demand_plan_item.source_style_id IS '来源订单款式主键快照，关联 rd_style.style_id；名称和款号单独快照保存';
COMMENT ON COLUMN biz_material_demand_plan_item.source_style_code IS '来源客户款号快照，来自 rd_style.internal_code';
COMMENT ON COLUMN biz_material_demand_plan_item.source_style_name IS '来源款式名称快照，保留订单制单时的显示名称';
COMMENT ON COLUMN biz_material_demand_plan_item.set_spec_id IS '来源套装规格主键快照，关联 rd_style_set_spec.id；单款为空';
COMMENT ON COLUMN biz_material_demand_plan_item.set_spec_name IS '套装规格名称快照；单款为空';
COMMENT ON COLUMN biz_material_demand_plan_item.child_style_id IS '套装当前需求对应子款主键快照，关联 rd_style.style_id；套装共用用料为空';
COMMENT ON COLUMN biz_material_demand_plan_item.child_style_code IS '套装子款客户款号快照；无子款归属时为空';
COMMENT ON COLUMN biz_material_demand_plan_item.child_style_name IS '套装子款名称快照；无子款归属时为空';
COMMENT ON COLUMN biz_material_demand_plan_item.color_id IS '订单款式或套装子款颜色主键快照，关联 base_color.color_id';
COMMENT ON COLUMN biz_material_demand_plan_item.color_name IS '订单款式或套装子款颜色名称快照';
COMMENT ON COLUMN biz_material_demand_plan_item.size_id IS '订单款式或套装子款尺码主键快照，关联 sys_size.id';
COMMENT ON COLUMN biz_material_demand_plan_item.size_name IS '订单款式或套装子款尺码名称快照';
COMMENT ON COLUMN biz_material_demand_plan_item.order_quantity IS '来源 PO 中该款式规格的订单数量；套装子款需求另按每套配套件数展开';
COMMENT ON COLUMN biz_material_demand_plan_item.bom_id IS '计算本行需求的款式 BOM 主键快照，关联 rd_style_bom.id';
COMMENT ON COLUMN biz_material_demand_plan_item.bom_version IS '计算本行需求时的 BOM 版本号快照';
COMMENT ON COLUMN biz_material_demand_plan_item.usage_type IS 'BOM 用料类型快照，取值沿用款式 BOM 中已登记的类型';
COMMENT ON COLUMN biz_material_demand_plan_item.part_name IS 'BOM 用料位置或套装子款名称快照，用于说明该物料用于何处';
COMMENT ON COLUMN biz_material_demand_plan_item.material_id IS '物料档案主键快照，关联 inv_material.material_id；停用或改名不影响本计划文字快照';
COMMENT ON COLUMN biz_material_demand_plan_item.material_code IS '物料编码快照，来自 inv_material.material_code';
COMMENT ON COLUMN biz_material_demand_plan_item.material_name IS '物料名称快照，来自 inv_material.material_name';
COMMENT ON COLUMN biz_material_demand_plan_item.category_id IS '物料所属分类主键快照，关联 base_category.category_id，用于权限复核和分类汇总';
COMMENT ON COLUMN biz_material_demand_plan_item.category_name IS '物料分类名称快照';
COMMENT ON COLUMN biz_material_demand_plan_item.material_variant_id IS '物料规格主键快照，关联 inv_material_variant.variant_id；单规格物料为空';
COMMENT ON COLUMN biz_material_demand_plan_item.material_spec IS '具体物料规格编码和名称快照，不能与物料档案的规格参数模板混淆';
COMMENT ON COLUMN biz_material_demand_plan_item.material_color_id IS 'BOM 指定的物料颜色主键快照，关联 base_color.color_id';
COMMENT ON COLUMN biz_material_demand_plan_item.material_color_name IS 'BOM 指定的物料颜色名称快照；无指定颜色时为空';
COMMENT ON COLUMN biz_material_demand_plan_item.inventory_unit_id IS '需求数量使用的库存单位主键快照，关联 base_unit.unit_id';
COMMENT ON COLUMN biz_material_demand_plan_item.inventory_unit_name IS '需求数量使用的库存单位名称快照';
COMMENT ON COLUMN biz_material_demand_plan_item.per_unit_usage IS '每件订单成衣对应的物料用量；单位为 inventory_unit_name，套装子款按展开件数计算';
COMMENT ON COLUMN biz_material_demand_plan_item.original_quantity IS '订单算料产生的原始需求数量，按库存单位计；保存后不因计划调整而改变';
COMMENT ON COLUMN biz_material_demand_plan_item.planned_quantity IS '计划人员确认的需求数量，按库存单位计；公式基数为原始需求量，允许调整但不得小于0，整数最多16位、小数最多8位';
COMMENT ON COLUMN biz_material_demand_plan_item.purchase_units IS '采购单位换算参考快照，保存单位、固定/实际换算模式、系数和建议数量；实际换算模式不虚构数量';
COMMENT ON COLUMN biz_material_demand_plan_item.adjustment_reason IS '本行最近一次计划数量调整原因；每次前后值另记 biz_material_demand_plan_adjustment';
COMMENT ON COLUMN biz_material_demand_plan_item.sort_order IS '计划内显示顺序，按来源 PO、订单款式色码、BOM 明细顺序生成';
COMMENT ON COLUMN biz_material_demand_plan_item.created_at IS '需求明细首次生成时间';
COMMENT ON COLUMN biz_material_demand_plan_item.updated_at IS '需求明细最近一次调整时间';

-- 每次数量调整独立留存，不覆盖历史前后值。
CREATE TABLE IF NOT EXISTS biz_material_demand_plan_adjustment (
    adjustment_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    plan_id bigint NOT NULL REFERENCES biz_material_demand_plan(id),
    item_id bigint NOT NULL REFERENCES biz_material_demand_plan_item(item_id),
    previous_quantity numeric(24,8) NOT NULL CHECK (previous_quantity >= 0),
    planned_quantity numeric(24,8) NOT NULL CHECK (planned_quantity >= 0),
    reason varchar(500) NOT NULL,
    changed_by bigint NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_material_demand_plan_adjustment_plan ON biz_material_demand_plan_adjustment(plan_id,changed_at DESC,adjustment_id DESC);
COMMENT ON TABLE biz_material_demand_plan_adjustment IS '物料需求计划人工调整历史，逐次保存库存单位计划量的前后值、调整原因和操作人';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.adjustment_id IS '需求计划调整历史主键';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.plan_id IS '被调整的需求计划主键，关联 biz_material_demand_plan.id';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.item_id IS '被调整的需求明细主键，关联 biz_material_demand_plan_item.item_id';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.previous_quantity IS '本次调整前的计划需求数量，单位与对应明细库存单位相同';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.planned_quantity IS '本次调整后的计划需求数量，单位与对应明细库存单位相同';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.reason IS '计划人员调整需求数量的业务原因，必填且最长500字';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.changed_by IS '执行本次计划调整的人员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_material_demand_plan_adjustment.changed_at IS '本次计划数量调整时间';

-- 审批配置与审批中心共用注册的业务类型和实例关联，不重复建流程数据。
COMMENT ON COLUMN sys_approval_business_instance.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；流程节点、意见与待办由 Warm-Flow 保存';
COMMENT ON COLUMN sys_approval_binding.business_type IS '业务对象注册编码：customer_order 客户订单、material_demand_plan 物料需求计划、production_task 生产任务单；流程绑定只影响后续提交';

-- 将物料需求计划入口挂入订单模块；操作权限由角色按计划岗位分配。
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1763100000000000300,parent_id,'物料需求计划',20,'demand-plans','order/MaterialDemandPlanManage','N','Y','C','0','0','order:material-demand-plan:list','FileSearchOutlined',now()
FROM sys_menu WHERE perms='order:customer-order:list' AND menu_type='C'
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,path=EXCLUDED.path,
    component=EXCLUDED.component,menu_type='C',visible='0',status='0',perms=EXCLUDED.perms,icon=EXCLUDED.icon;
INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT v.id,m.menu_id,v.name,v.n,'#','','N','Y','F','0','0',v.perm,'#',now()
FROM sys_menu m CROSS JOIN (VALUES
    (1763100000000000301,'计划编辑与补生成',1,'order:material-demand-plan:edit'),
    (1763100000000000302,'计划审核',2,'order:material-demand-plan:review'),
    (1763100000000000303,'取消计划',3,'order:material-demand-plan:cancel')
) v(id,name,n,perm)
WHERE m.perms='order:material-demand-plan:list'
ON CONFLICT(menu_id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',visible='0';

DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON biz_material_demand_plan, biz_material_demand_plan_adjustment TO %I',app_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON biz_material_demand_plan_item TO %I',app_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE biz_material_demand_plan_id_seq, biz_material_demand_plan_item_item_id_seq, biz_material_demand_plan_adjustment_adjustment_id_seq TO %I',app_role);
END $$;

COMMIT;

-- 同步开发迁移：业务单据反审核审批_20261119.sql
-- 客户订单与物料需求计划的反审核申请及审批结果快照。
BEGIN;

CREATE TABLE IF NOT EXISTS biz_document_reversal_request (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_type varchar(64) NOT NULL CHECK (business_type IN ('customer_order_unapprove','material_demand_plan_unapprove')),
    business_id bigint NOT NULL,
    business_revision integer NOT NULL,
    instance_id bigint NOT NULL UNIQUE,
    requested_by bigint NOT NULL,
    reason varchar(500) NOT NULL,
    source_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    state varchar(16) NOT NULL DEFAULT 'pending' CHECK (state IN ('pending','approved','rejected','cancelled')),
    result_message varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_reversal_pending
    ON biz_document_reversal_request(business_type,business_id) WHERE state='pending';
CREATE INDEX IF NOT EXISTS ix_document_reversal_requester
    ON biz_document_reversal_request(requested_by,created_at DESC,id DESC);

COMMENT ON TABLE biz_document_reversal_request IS '业务单据反审核申请记录，关联独立 Warm-Flow 审批实例并保留原因、来源快照及审批结果；不替代原单和原审核历史';
COMMENT ON COLUMN biz_document_reversal_request.id IS '反审核申请记录主键';
COMMENT ON COLUMN biz_document_reversal_request.business_type IS '反审核业务类型：customer_order_unapprove 客户订单反审核、material_demand_plan_unapprove 物料需求计划反审核';
COMMENT ON COLUMN biz_document_reversal_request.business_id IS '被申请反审核的业务单据主键；目标业务表由 business_type 决定，不能作为跨表外键';
COMMENT ON COLUMN biz_document_reversal_request.business_revision IS '发起申请时锁定的原单修订号；审批通过时必须仍与原单版本一致';
COMMENT ON COLUMN biz_document_reversal_request.instance_id IS '本次反审核专用 Warm-Flow 流程实例编号；与原审核实例分开保存';
COMMENT ON COLUMN biz_document_reversal_request.requested_by IS '发起反审核申请的人员，关联 sys_user.user_id';
COMMENT ON COLUMN biz_document_reversal_request.reason IS '申请反审核的业务原因，必填且最多500字，供审批人和发起人查看';
COMMENT ON COLUMN biz_document_reversal_request.source_snapshot IS '申请时的原单关键状态快照；用于追溯，审批期间不得覆盖';
COMMENT ON COLUMN biz_document_reversal_request.state IS '反审核申请状态：pending 审批中、approved 审批通过、rejected 被退回、cancelled 流程取消或终止';
COMMENT ON COLUMN biz_document_reversal_request.result_message IS '审批结束时记录的审批意见或处理结果说明';
COMMENT ON COLUMN biz_document_reversal_request.created_at IS '反审核申请发起时间';
COMMENT ON COLUMN biz_document_reversal_request.updated_at IS '反审核申请最近一次处理时间';

COMMENT ON COLUMN sys_approval_business_instance.business_type IS '业务对象注册编码：customer_order 客户订单、customer_order_unapprove 客户订单反审核、material_demand_plan 物料需求计划、material_demand_plan_unapprove 物料需求计划反审核、production_task 生产任务单；流程节点、意见与待办由 Warm-Flow 保存';
COMMENT ON COLUMN sys_approval_binding.business_type IS '业务对象注册编码：customer_order 客户订单、customer_order_unapprove 客户订单反审核、material_demand_plan 物料需求计划、material_demand_plan_unapprove 物料需求计划反审核、production_task 生产任务单；普通单据与反审核事项分别绑定流程';

DO $$ DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON biz_document_reversal_request TO %I',app_role);
    EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE biz_document_reversal_request_id_seq TO %I',app_role);
END $$;

COMMIT;

-- 显示位数供只读视图使用；不改变单据原值和结算金额。
INSERT INTO sys_config(config_id,config_name,config_key,config_value,config_type,create_time,remark)
SELECT COALESCE(MAX(config_id),0)+1,'数字显示精度','system.display.decimals',
       '{"bomUsage":4,"purchasePrice":4,"costPrice":6,"percentage":2}','Y',now(),'BOM单耗、采购单价、成本单价和百分比显示位数，各为0至8'
FROM sys_config HAVING NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system.display.decimals');
