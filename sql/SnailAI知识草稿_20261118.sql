-- 文件用途：为“SnailAI知识草稿_20261118”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“SnailAI知识草稿_20261118”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 智能体提出的知识入库草稿；确认前不得写入共享知识库。
-- 草稿状态从 PENDING 经人工确认进入 SUBMITTING，再记录提交结果；此表不直接替代正式知识文档表。
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
