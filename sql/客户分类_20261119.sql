-- 客户分类独立于标签；历史客户保留为空，由业务人员整理。
BEGIN;
ALTER TABLE customer_master ADD COLUMN IF NOT EXISTS category_id bigint;
COMMENT ON COLUMN customer_master.category_id IS '客户档案归属的分类节点ID；空值表示未分类，查询和维护受人员分类权限限制';
CREATE INDEX IF NOT EXISTS idx_customer_master_category_id ON customer_master (category_id);
COMMENT ON INDEX idx_customer_master_category_id IS '按客户分类筛选档案';
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_customer_master_category' AND conrelid='customer_master'::regclass) THEN
  ALTER TABLE customer_master ADD CONSTRAINT fk_customer_master_category
   FOREIGN KEY (category_id) REFERENCES base_category(category_id) ON DELETE RESTRICT;
 END IF;
END $$;
COMMENT ON CONSTRAINT fk_customer_master_category ON customer_master IS '客户分类引用保护；有客户使用时禁止物理删除分类节点';
COMMIT;
