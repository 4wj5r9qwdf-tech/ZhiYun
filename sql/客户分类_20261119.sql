-- 客户分类独立于标签；历史客户保留为空，由业务人员整理。
-- Customer categories are independent of tags; historical rows remain unclassified.
ALTER TABLE customer_master ADD COLUMN IF NOT EXISTS category_id bigint;
COMMENT ON COLUMN customer_master.category_id IS '客户档案归属的分类节点ID，关联基础分类；空值表示历史记录未分类';
CREATE INDEX IF NOT EXISTS idx_customer_master_category_id ON customer_master (category_id);
