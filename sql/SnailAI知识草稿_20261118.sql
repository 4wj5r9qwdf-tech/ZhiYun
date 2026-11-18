-- 智能体提出的知识入库草稿；确认前不得写入共享知识库。
CREATE TABLE IF NOT EXISTS zy_snail_knowledge_draft (
    id VARCHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    rag_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    document_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP
);
COMMENT ON TABLE zy_snail_knowledge_draft IS '智能体知识入库候选与人工确认记录';
COMMENT ON COLUMN zy_snail_knowledge_draft.id IS '草稿唯一编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.owner_id IS '发起和确认的后台用户编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.agent_id IS '发起智能体编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.conversation_id IS '发起会话编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.rag_id IS '目标知识库编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.title IS '拟入库标题';
COMMENT ON COLUMN zy_snail_knowledge_draft.content IS '待确认知识正文';
COMMENT ON COLUMN zy_snail_knowledge_draft.source IS '内容来源说明';
COMMENT ON COLUMN zy_snail_knowledge_draft.status IS '状态：PENDING待确认、SUBMITTING提交中、SUBMITTED已提交处理、REJECTED已拒绝、FAILED提交失败';
COMMENT ON COLUMN zy_snail_knowledge_draft.document_id IS '提交后创建的知识文档编号';
COMMENT ON COLUMN zy_snail_knowledge_draft.created_at IS '草稿创建时间';
COMMENT ON COLUMN zy_snail_knowledge_draft.confirmed_at IS '人工确认时间';
CREATE INDEX IF NOT EXISTS idx_zy_snail_knowledge_draft_owner ON zy_snail_knowledge_draft(owner_id, agent_id, conversation_id, created_at);
