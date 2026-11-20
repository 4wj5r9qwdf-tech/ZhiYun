-- 文件用途：为“SnailAI聊天文件归属_20261118”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“SnailAI聊天文件归属_20261118”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- SnailAI 原始聊天文件归属，仅在智能体独立数据库执行。
-- 附件与员工、Agent、会话同时绑定；接口读取时必须使用这组归属条件，不能只按随机文件 ID 查询。
CREATE TABLE IF NOT EXISTS zy_snail_chat_file (
    id varchar(36) PRIMARY KEY,
    resource_id bigint NOT NULL UNIQUE,
    owner_id bigint NOT NULL,
    agent_id bigint NOT NULL,
    conversation_id varchar(128) NOT NULL,
    original_name varchar(200) NOT NULL,
    file_size bigint NOT NULL CHECK (file_size > 0),
    file_type varchar(16) NOT NULL,
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_zy_snail_chat_file_owner_conversation
    ON zy_snail_chat_file (owner_id, agent_id, conversation_id, create_time);
COMMENT ON TABLE zy_snail_chat_file IS '智能体聊天原始文件与用户、智能体及会话的归属关系';
COMMENT ON COLUMN zy_snail_chat_file.id IS '文件归属记录的随机标识';
COMMENT ON COLUMN zy_snail_chat_file.resource_id IS 'SnailAI 原生资源编号';
COMMENT ON COLUMN zy_snail_chat_file.owner_id IS '上传者在智能体后台的用户编号';
COMMENT ON COLUMN zy_snail_chat_file.agent_id IS '所属智能体编号';
COMMENT ON COLUMN zy_snail_chat_file.conversation_id IS '所属会话编号';
COMMENT ON COLUMN zy_snail_chat_file.original_name IS '上传时的原始文件名';
COMMENT ON COLUMN zy_snail_chat_file.file_size IS '原始文件字节数';
COMMENT ON COLUMN zy_snail_chat_file.file_type IS '经服务端校验的文件扩展名';
COMMENT ON COLUMN zy_snail_chat_file.create_time IS '文件上传时间';
