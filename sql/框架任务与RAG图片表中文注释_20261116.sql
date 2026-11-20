-- 文件用途：为“框架任务与RAG图片表中文注释_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“框架任务与RAG图片表中文注释_20261116”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 将调度框架统计表及 RAG 文档图片索引表的表、字段注释修正为中文业务说明。
-- 仅更新 PostgreSQL 元数据注释，不修改表结构或业务数据；可在已有数据库上重复执行。
BEGIN;

COMMENT ON TABLE sj_job_summary IS '定时任务与工作流任务执行结果汇总，供调度监控看板统计展示';
COMMENT ON COLUMN sj_job_summary.id IS '调度任务统计记录主键';
COMMENT ON COLUMN sj_job_summary.namespace_id IS '调度框架命名空间标识，用于隔离不同部署空间';
COMMENT ON COLUMN sj_job_summary.group_name IS '任务所属分组名称';
COMMENT ON COLUMN sj_job_summary.business_id IS '业务任务 ID；根据 system_task_type 对应任务定义 ID 或工作流定义 ID';
COMMENT ON COLUMN sj_job_summary.system_task_type IS '任务类型：3 表示定时任务（JOB），4 表示工作流任务（WORKFLOW）';
COMMENT ON COLUMN sj_job_summary.trigger_at IS '本统计记录对应的任务触发时间';
COMMENT ON COLUMN sj_job_summary.success_num IS '任务执行成功的日志条数';
COMMENT ON COLUMN sj_job_summary.fail_num IS '任务执行失败的日志条数';
COMMENT ON COLUMN sj_job_summary.fail_reason IS '任务执行失败原因的汇总信息';
COMMENT ON COLUMN sj_job_summary.stop_num IS '任务被停止的日志条数';
COMMENT ON COLUMN sj_job_summary.stop_reason IS '任务被停止原因的汇总信息';
COMMENT ON COLUMN sj_job_summary.cancel_num IS '任务被取消的日志条数';
COMMENT ON COLUMN sj_job_summary.cancel_reason IS '任务被取消原因的汇总信息';
COMMENT ON COLUMN sj_job_summary.create_dt IS '统计记录创建时间';
COMMENT ON COLUMN sj_job_summary.update_dt IS '统计记录最后更新时间';

COMMENT ON TABLE sj_retry_summary IS '异步重试任务状态汇总，供调度监控看板统计展示';
COMMENT ON COLUMN sj_retry_summary.id IS '重试任务统计记录主键';
COMMENT ON COLUMN sj_retry_summary.namespace_id IS '调度框架命名空间标识，用于隔离不同部署空间';
COMMENT ON COLUMN sj_retry_summary.group_name IS '重试任务所属分组名称';
COMMENT ON COLUMN sj_retry_summary.scene_name IS '重试任务场景名称';
COMMENT ON COLUMN sj_retry_summary.trigger_at IS '本统计记录对应的触发时间';
COMMENT ON COLUMN sj_retry_summary.running_num IS '当前仍在重试中的日志条数';
COMMENT ON COLUMN sj_retry_summary.finish_num IS '已完成重试流程的日志条数';
COMMENT ON COLUMN sj_retry_summary.max_count_num IS '已达到最大重试次数的日志条数';
COMMENT ON COLUMN sj_retry_summary.suspend_num IS '已暂停重试的日志条数';
COMMENT ON COLUMN sj_retry_summary.create_dt IS '统计记录创建时间';
COMMENT ON COLUMN sj_retry_summary.update_dt IS '统计记录最后更新时间';

COMMENT ON TABLE sai_rag_document_image IS 'RAG 文档解析图片索引，保存图片位置、识别文字和图注，供知识库检索与问答引用';
COMMENT ON COLUMN sai_rag_document_image.id IS '文档解析图片记录主键';
COMMENT ON COLUMN sai_rag_document_image.rag_id IS '所属知识库 ID，对应 sai_rag.id；本表通过业务 ID 关联';
COMMENT ON COLUMN sai_rag_document_image.document_id IS '来源文档 ID，对应 sai_rag_document.id；本表通过业务 ID 关联';
COMMENT ON COLUMN sai_rag_document_image.chunk_id IS '关联的文本或图片分块 ID，对应 sai_rag_chunk.id；未生成分块时为空';
COMMENT ON COLUMN sai_rag_document_image.resource_id IS '图片资源 ID，对应 sai_resource.id；未登记资源文件时为空';
COMMENT ON COLUMN sai_rag_document_image.image_index IS '图片在来源文档中的顺序号';
COMMENT ON COLUMN sai_rag_document_image.image_url IS '解析后图片的存储地址或访问地址';
COMMENT ON COLUMN sai_rag_document_image.caption IS '图片内容说明或解析生成的图注';
COMMENT ON COLUMN sai_rag_document_image.figure_no IS '来源文档中的图号，例如图 1 或 Figure 1';
COMMENT ON COLUMN sai_rag_document_image.figure_title IS '来源文档中图片对应的标题';
COMMENT ON COLUMN sai_rag_document_image.section_title IS '图片所在的文档章节标题';
COMMENT ON COLUMN sai_rag_document_image.source_page IS '图片在来源文档中的页码';
COMMENT ON COLUMN sai_rag_document_image.document_name IS '解析时记录的来源文档名称快照';
COMMENT ON COLUMN sai_rag_document_image.ocr_text IS '从图片中识别出的文字内容';
COMMENT ON COLUMN sai_rag_document_image.create_dt IS '图片解析索引记录创建时间';
COMMENT ON COLUMN sai_rag_document_image.update_dt IS '图片解析索引记录最后更新时间';

COMMIT;
