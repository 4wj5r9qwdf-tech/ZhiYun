-- SnailAI 原始聊天文件归属，仅在智能体独立数据库执行。
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
