-- 12. 内嵌智能体（2026-10-30，方案A：ERP 内置最小智能体，不引外部框架）
--     定位（内嵌智能体规范 §4 权限红线）：智能体=员工权限放大器。
--     工具列表按【当前登录人】权限码实时过滤；工具调用=回环调 ERP 现成接口（本人 token，
--     @SaCheckPermission/数据权限原样生效）；留痕记员工工号+渠道 agent，不记 AI。
--     sys_agent_action = 动作目录（配置表：加动作=插一行，接口均为 ERP 现成 REST）
--     sys_agent_log    = 三点留痕（问了什么/调了哪些动作/结果是什么）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_agent_action (
    action_id     bigint        NOT NULL,
    module_key    varchar(50)   NOT NULL,
    action_key    varchar(50)   NOT NULL,
    action_name   varchar(50)   NOT NULL,
    description   varchar(500),
    endpoint      varchar(200)  NOT NULL,
    http_method   varchar(10)   NOT NULL DEFAULT 'GET',
    op_type       char(1)       NOT NULL DEFAULT '0',
    params_schema varchar(1000) DEFAULT '{}',
    perm          varchar(100),
    enabled       char(1)       NOT NULL DEFAULT '0',
    del_flag      char(1)       DEFAULT '0',
    create_org    bigint,
    create_by     bigint,
    create_time   timestamp     DEFAULT CURRENT_TIMESTAMP,
    update_by     bigint,
    update_time   timestamp,
    CONSTRAINT sys_agent_action_pk PRIMARY KEY (action_id)
);

COMMENT ON TABLE  sys_agent_action             IS '智能体动作目录（LLM 可调用的 ERP 动作配置：加动作=插一行；一期只登记只读 GET）';
COMMENT ON COLUMN sys_agent_action.action_id   IS '主键ID';
COMMENT ON COLUMN sys_agent_action.module_key  IS '所属模块（左侧导航模块 key：person/property/system 等）';
COMMENT ON COLUMN sys_agent_action.action_key  IS '动作标识（唯一；LLM 调用时的工具名）';
COMMENT ON COLUMN sys_agent_action.action_name IS '动作名（中文，展示用）';
COMMENT ON COLUMN sys_agent_action.description IS '给 LLM 的说明（何时用、参数含义，写清业务口径）';
COMMENT ON COLUMN sys_agent_action.endpoint    IS 'ERP 动作接口（相对路径；回环调用，带发起员工本人 token，权限/数据范围原样生效）';
COMMENT ON COLUMN sys_agent_action.http_method IS '请求方法（GET/POST；一期只登记只读 GET，写操作二期评估）';
COMMENT ON COLUMN sys_agent_action.op_type     IS '操作类型：0=读(直接执行回复) 1=写(二期走确认卡，AI 只生成操作卡片，用户确认后才执行)';
COMMENT ON COLUMN sys_agent_action.params_schema IS 'LLM 参数 JSON schema（{}=无参；字段与说明给 LLM 填参用）';
COMMENT ON COLUMN sys_agent_action.perm        IS '可见权限码（空=人人可见；有值=仅持有该码的员工在对话里看得到此动作）';
COMMENT ON COLUMN sys_agent_action.enabled     IS '0启用/1停用';
COMMENT ON COLUMN sys_agent_action.del_flag    IS '删除标志：0存在/1删除';
COMMENT ON COLUMN sys_agent_action.create_org  IS '创建部门（→ sys_org.org_id）';
COMMENT ON COLUMN sys_agent_action.create_by   IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_agent_action.create_time IS '创建时间';
COMMENT ON COLUMN sys_agent_action.update_by   IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_agent_action.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS sys_agent_log (
    log_id      bigint       NOT NULL,
    user_id     bigint,
    user_name   varchar(30),
    question    text,
    tool_trace  text,
    answer      text,
    duration_ms integer,
    create_time  timestamp   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sys_agent_log_pk PRIMARY KEY (log_id)
);

COMMENT ON TABLE  sys_agent_log          IS '智能体对话留痕（三点：员工问了什么/解析调了哪些动作/结果是什么；操作人记员工不记AI，渠道=agent）';
COMMENT ON COLUMN sys_agent_log.log_id   IS '主键ID';
COMMENT ON COLUMN sys_agent_log.user_id  IS '发起员工（→ sys_user.user_id，追责主体）';
COMMENT ON COLUMN sys_agent_log.user_name IS '工号快照（→ sys_user.user_name，人员后续变动不影响留痕）';
COMMENT ON COLUMN sys_agent_log.question IS '员工提问原文（留痕点1）';
COMMENT ON COLUMN sys_agent_log.tool_trace IS '动作调用链 JSON 数组（留痕点2：[{action_key,endpoint,status,ms}]）';
COMMENT ON COLUMN sys_agent_log.answer   IS '智能体最终回答（留痕点3）';
COMMENT ON COLUMN sys_agent_log.duration_ms IS '本轮总耗时（毫秒）';
COMMENT ON COLUMN sys_agent_log.create_time IS '创建时间';

-- 动作目录一期种子（只读动作，接口均为 ERP 现成；系统功能配置，非业务示例数据）
INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000001, 'person', 'search_person', '人员查询',
  '按姓名/工号/手机号查员工名册（如：张三是谁、裁剪组有哪些人、在职多少人）。数据按发起员工的数据权限过滤，看不到的不会出现。',
  '/system/person/list', 'GET', '{"keyword":"姓名/工号/手机号，留空=全量名册"}', 'system:person:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000001);

INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000002, 'person', 'list_orgs', '组织树',
  '查全厂组织架构（厂→车间→区域→小组，含各节点类型与负责人）。',
  '/system/org/list', 'GET', '{}', 'system:org:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000002);

INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000003, 'person', 'list_posts', '岗位列表',
  '查岗位档案（裁剪/缝纫/后整等职务）。',
  '/system/post/list', 'GET', '{}', 'system:post:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000003);

INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000004, 'system', 'list_roles', '角色列表',
  '查角色名册及数据范围（全部/本部门及以下/仅本人等）。',
  '/system/role/list', 'GET', '{}', 'system:role:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000004);

INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000005, 'system', 'list_companies', '公司档案',
  '查登记的公司（河北佰信达/依时华：名称、默认展示、主题）。',
  '/system/company/list', 'GET', '{}', 'system:company:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000005);

INSERT INTO sys_agent_action (action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm, enabled, del_flag, create_by, create_time)
SELECT 1763100000000000006, 'property', 'list_buildings', '建筑档案',
  '查建筑清单（厂房/行政楼/宿舍楼：名称、楼层、占地/建筑面积、地址、状态）。',
  '/system/building/list', 'GET', '{}', 'system:building:list', '0', '0', 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_agent_action WHERE action_id=1763100000000000006);

-- 菜单权限点：系统目录挂"智能体"页（C 行；分配权限树自动出现，闭包逻辑已修复）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_org, create_by, create_time)
SELECT 1763100000000000007, '智能体', 1761600000000000002, 98, 'agent', 'agent', '', 'N', 'Y', 'C', '0', '0', 'system:agent:chat', 'robot', '', '', 1761000000000000103, 1761100000000000001, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763100000000000007);

-- 管理员角色补新菜单行（"全选"语义跟随；超管免检不受影响）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2114638088423723010, 1763100000000000007
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=2114638088423723010 AND menu_id=1763100000000000007);

COMMIT;

-- ============================================================
-- 执行后自查：
--   1. \d sys_post_role          -- 表已建
--   2. SELECT perms FROM sys_menu WHERE perms='system:user:sensitive';  -- 1 行
--   3. SELECT column_name FROM information_schema.columns
--      WHERE table_name='sys_role' AND column_name='data_scope_org_ids';  -- 1 行
--   4. \d sys_user_menu_config / sys_user_workbench / sys_todo / sys_person_ext / sys_org_manage  -- 5 张新表
--   5. SELECT count(*) FROM sys_agent_action;  -- 一期 6 条只读动作
--   6. SELECT perms FROM sys_menu WHERE menu_id=1763100000000000007;  -- 智能体页 1 行
-- ============================================================
