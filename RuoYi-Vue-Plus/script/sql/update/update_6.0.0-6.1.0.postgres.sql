-- 文件用途：将织云 ERP 的 Warm-Flow 1.8.9 PostgreSQL 存量结构迁移到 2.1.1。
-- 文件版本：1.0.0
-- 功能目的：配套 RuoYi-Vue-Plus 6.1.0，为 PostgreSQL 添加 Warm-Flow 2.1 的节点配置、状态编码、事件等待表和菜单字典。
-- 设计思路：在单个 PostgreSQL 事务内完成结构与可确定的状态映射；含义不明确的历史待办保留并在末尾列出核查。
-- 实现方法：幂等增加列/表/索引，将旧状态码映射为 Warm-Flow 2.1 编码，并更新工作流字典及事件等待菜单。
-- 关键约束：仅适用于 RuoYi 6.0.0 + Warm-Flow 1.8.9 的 PostgreSQL 存量库；生产执行前须备份、暂停审批写入并完成副本演练，先人工复核末尾列出的 waiting 待办。
-- 依赖与引用：RuoYi-Vue-Plus/script/sql/postgres/postgres_ry_workflow.sql；RuoYi-Vue-Plus/script/sql/update/update_6.0.0-6.1.0.sql；Docs/功能任务/ZY-ERP-20261124-RuoYi-Vue-Plus-6.1.0升级.md
-- 当前责任人：JY；本次整理：Codex（智能体），2026-11-24
-- 修改标记：ZY-ERP-20261124-RuoYi6.1.0；新增 PostgreSQL 方言迁移
-- 测试状态：已在临时 PostgreSQL 16 集群基于 6.0.0 工作流表结构验证迁移、状态映射及重复执行；未连接或修改项目数据库

BEGIN;

-- Warm-Flow 2.1 将节点版本统一交由流程定义管理，旧列不再读取或写入。
ALTER TABLE flow_node DROP COLUMN IF EXISTS version;

-- 2.1 把流程级、节点级配置与事件属性拆分为独立列。
ALTER TABLE flow_definition ADD COLUMN IF NOT EXISTS def_config text;
ALTER TABLE flow_node
    ADD COLUMN IF NOT EXISTS node_config text,
    ADD COLUMN IF NOT EXISTS event_type varchar(40),
    ADD COLUMN IF NOT EXISTS event_name varchar(100),
    ADD COLUMN IF NOT EXISTS attached_to_node_code varchar(100),
    ADD COLUMN IF NOT EXISTS interrupting smallint;
ALTER TABLE flow_his_task ADD COLUMN IF NOT EXISTS node_config text;

COMMENT ON COLUMN flow_definition.def_config IS '流程定义配置（自动审批等引擎配置与自定义扩展属性json）';
COMMENT ON COLUMN flow_node.node_config IS '节点配置（服务任务、事件等引擎节点配置json）';
COMMENT ON COLUMN flow_node.event_type IS '事件类型';
COMMENT ON COLUMN flow_node.event_name IS '事件名称（消息事件使用）';
COMMENT ON COLUMN flow_node.attached_to_node_code IS '边界事件附着的任务节点编码';
COMMENT ON COLUMN flow_node.interrupting IS '边界事件是否中断型';
COMMENT ON COLUMN flow_his_task.node_config IS '节点配置（服务任务、事件等引擎节点配置json）';

-- 迁移前后状态列保持字符串类型，扩大旧字段注释以记录新状态域。
ALTER TABLE flow_instance ALTER COLUMN flow_status TYPE varchar(20);
ALTER TABLE flow_task ALTER COLUMN flow_status TYPE varchar(20);
ALTER TABLE flow_his_task ALTER COLUMN flow_status TYPE varchar(20);
COMMENT ON COLUMN flow_instance.flow_status IS '实例状态（draft waiting termination invalid cancel finish back restart）';
COMMENT ON COLUMN flow_task.flow_status IS '任务状态（pending pass auto_pass back invalid take_back copy）';
COMMENT ON COLUMN flow_his_task.flow_status IS '任务状态（pending pass auto_pass back invalid take_back copy；撤回归档为 cancel）';

-- 流程实例、任务和历史记录分别使用各自的状态域；仅转换已知旧数字码。
UPDATE flow_instance SET flow_status = CASE flow_status
    WHEN '0' THEN 'draft' WHEN '1' THEN 'waiting' WHEN '4' THEN 'termination'
    WHEN '5' THEN 'invalid' WHEN '6' THEN 'cancel' WHEN '7' THEN 'waiting'
    WHEN '8' THEN 'finish' WHEN '9' THEN 'back' WHEN '12' THEN 'restart'
    WHEN '13' THEN 'draft' END
WHERE flow_status IN ('0', '1', '4', '5', '6', '7', '8', '9', '12', '13');

UPDATE flow_task SET flow_status = CASE flow_status
    WHEN '1' THEN 'pending' WHEN '2' THEN 'pass' WHEN '8' THEN 'pass'
    WHEN '9' THEN 'back' WHEN '10' THEN 'invalid' WHEN '11' THEN 'take_back' END
WHERE flow_status IN ('1', '2', '8', '9', '10', '11');

UPDATE flow_his_task SET flow_status = CASE flow_status
    WHEN '1' THEN 'pending' WHEN '2' THEN 'pass' WHEN '8' THEN 'pass'
    WHEN '9' THEN 'back' WHEN '10' THEN 'invalid' WHEN '11' THEN 'take_back' END
WHERE flow_status IN ('1', '2', '8', '9', '10', '11');

-- 历史协作码改为字符串。会签/票签等不是协作动作，统一记为 none；审批结果仍由 flow_status 保留。
ALTER TABLE flow_his_task ALTER COLUMN cooperate_type TYPE varchar(20)
USING CASE cooperate_type::text
    WHEN '0' THEN 'none' WHEN '1' THEN 'none' WHEN '2' THEN 'transfer'
    WHEN '3' THEN 'delegate' WHEN '4' THEN 'none' WHEN '5' THEN 'none'
    WHEN '6' THEN 'add_signer' WHEN '7' THEN 'remove_signer'
    ELSE cooperate_type::text END;
ALTER TABLE flow_his_task ALTER COLUMN cooperate_type SET DEFAULT 'none';
COMMENT ON COLUMN flow_his_task.cooperate_type IS '协作方式（none transfer delegate add_signer remove_signer）';
COMMENT ON COLUMN flow_user."type" IS '人员类型（1审批人 2转办人 3委托人 4抄送人）';
COMMENT ON COLUMN flow_user.associated IS '关联id（待办任务id；抄送人记录为 flow_his_task.id）';

-- 任务状态字典完整替换为 Warm-Flow 2.1 编码；终止归档是 2.1.1 新增的历史状态。
DELETE FROM sys_dict_data WHERE dict_type = 'wf_task_status';
INSERT INTO sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
     create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
    (1761600000000000060, 1,  '待审批', 'pending',       'wf_task_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '待审批'),
    (1761600000000000049, 2,  '通过',   'pass',          'wf_task_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '通过'),
    (1761600000000000061, 3,  '自动审批', 'auto_pass',   'wf_task_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '自动审批'),
    (1761600000000000056, 4,  '抄送',   'copy',          'wf_task_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '抄送'),
    (1761600000000000052, 5,  '退回',   'back',          'wf_task_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '退回'),
    (1761600000000000062, 6,  '拿回',   'take_back',     'wf_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '拿回'),
    (1761600000000000051, 7,  '失效',   'invalid',       'wf_task_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '失效'),
    (1761600000000000048, 8,  '撤销',   'cancel',        'wf_task_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '撤销'),
    (1761600000000000064, 9,  '转办',   'transfer',      'wf_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '转办'),
    (1761600000000000065, 10, '委派',   'delegate',      'wf_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '委派'),
    (1761600000000000066, 11, '加签',   'add_signer',    'wf_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '加签'),
    (1761600000000000067, 12, '减签',   'remove_signer', 'wf_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '减签'),
    (1761600000000000068, 13, '已终止', 'termination',   'wf_task_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '终止归档留痕');

-- 实例状态字典补充引擎已有的 restart 编码，不覆盖现有相同类型和值的行。
INSERT INTO sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
     create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761600000000000063, 8, '重启', 'restart', 'wf_business_status', '', 'info', 'N',
       1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '重启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'wf_business_status' AND dict_value = 'restart'
);

-- 定时、消息等等待型事件共用此表；ID 由应用的 ID 生成器写入。
CREATE TABLE IF NOT EXISTS flow_event_wait
(
    id                bigint       NOT NULL,
    tenant_id         varchar(40),
    del_flag          char(1)      DEFAULT '0',
    definition_id     bigint,
    instance_id       bigint,
    node_code         varchar(100),
    event_type        varchar(40)  NOT NULL,
    event_name        varchar(100),
    attached_task_id  bigint,
    trigger_type      varchar(40),
    trigger_interval  varchar(100),
    next_trigger_at   timestamp,
    timezone          varchar(40),
    repeat_count      integer,
    max_repeat_count  integer,
    status            varchar(20)  NOT NULL,
    retry_count       integer,
    max_retry_count   integer,
    retry_at          timestamp,
    lock_owner        varchar(64),
    lock_until        timestamp,
    version           integer,
    payload           text,
    last_error        varchar(500),
    create_time       timestamp,
    update_time       timestamp,
    CONSTRAINT flow_event_wait_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_wait_status_next ON flow_event_wait (status, next_trigger_at, id);
CREATE INDEX IF NOT EXISTS event_wait_status_retry ON flow_event_wait (status, retry_at, id);
CREATE INDEX IF NOT EXISTS event_wait_tenant_status_next ON flow_event_wait (tenant_id, status, next_trigger_at, id);
CREATE INDEX IF NOT EXISTS event_wait_tenant_status_retry ON flow_event_wait (tenant_id, status, retry_at, id);
CREATE INDEX IF NOT EXISTS event_wait_instance_task ON flow_event_wait (instance_id, attached_task_id);
CREATE INDEX IF NOT EXISTS event_wait_name_instance_status ON flow_event_wait (event_name, instance_id, status);
CREATE INDEX IF NOT EXISTS event_wait_def_node_status ON flow_event_wait (definition_id, node_code, status);
COMMENT ON TABLE flow_event_wait IS '事件等待表';
COMMENT ON COLUMN flow_event_wait.definition_id IS '流程定义id（定时启动事件必须有）';
COMMENT ON COLUMN flow_event_wait.instance_id IS '流程实例id（实例等待事件必须有）';
COMMENT ON COLUMN flow_event_wait.node_code IS '事件节点编码（定位沿用 definition_id + node_code）';
COMMENT ON COLUMN flow_event_wait.event_type IS '事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）';
COMMENT ON COLUMN flow_event_wait.status IS '等待状态（WAITING RUNNING CANCELLED RETRY DEAD）';

-- 新增定时任务菜单与按钮。按 ID 更新同一菜单项，保留已有角色菜单关联。
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
     visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
    (1761400000000011901, '定时任务', 1761400000000011630, 3, 'timer', 'workflow/timer/index', '', 'N', 'N', 'C', '0', '0', 'workflow:eventWait:list', 'time', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '定时任务与事件等待监控菜单'),
    (1761400000000011902, '定时任务查询', 1761400000000011901, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'workflow:eventWait:query', '#', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, ''),
    (1761400000000011903, '定时任务手动补发', 1761400000000011901, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'workflow:eventWait:trigger', '#', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    query_param = EXCLUDED.query_param,
    is_frame = EXCLUDED.is_frame,
    is_cache = EXCLUDED.is_cache,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    perms = EXCLUDED.perms,
    icon = EXCLUDED.icon,
    active_menu = EXCLUDED.active_menu,
    ext = EXCLUDED.ext,
    remark = EXCLUDED.remark;

-- 2.1.0 迁移可能把实例的 waiting 写入待办状态；不要自动猜测这些记录的真实状态。
-- 结果非空时先结合对应实例、历史任务确认；确认是待审批后再单独更新为 pending。
SELECT id, instance_id, node_code, flow_status FROM flow_task WHERE flow_status = 'waiting';

COMMIT;
