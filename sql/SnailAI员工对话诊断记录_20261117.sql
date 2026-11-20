-- 文件用途：为“SnailAI员工对话诊断记录_20261117”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“SnailAI员工对话诊断记录_20261117”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 设计思路：以可重复执行 DDL 和事务控制明确迁移顺序；数据含义以实际 SQL 定义为准。
-- 实现方法：按脚本声明顺序创建/调整表字段、约束、索引、权限并写数据库 COMMENT。
-- 关键约束：不得改变已有数据含义或绕过脚本约束。
-- 依赖与引用：sql/主建表.sql；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
-- 初始开发人员：JY（织云项目自研内容）
-- 初始开发时间：待追溯（不以补录时间代替原始开发时间）
-- 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
-- 最近修改人员：JY
-- AI辅助工具：Codex（智能体）
-- 最近修改时间：2026-11-20
-- 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
-- 最近测试时间：本次整理未执行功能测试
-- 测试人员：本次整理未执行
-- 测试状态：待功能回归；历史测试记录待追溯
-- 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
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
