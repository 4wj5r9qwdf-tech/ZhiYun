BEGIN;
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS bom_code varchar(60);
ALTER TABLE rd_style_bom ADD COLUMN IF NOT EXISTS preparer_id bigint;
-- 历史记录使用专用前缀保留稳定标识，避免与新单日期流水冲突。
UPDATE rd_style_bom SET bom_code = 'BOM-HIST-' || id WHERE bom_code IS NULL OR btrim(bom_code) = '';
UPDATE rd_style_bom SET preparer_id = create_by WHERE preparer_id IS NULL;
ALTER TABLE rd_style_bom ALTER COLUMN bom_code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rd_style_bom_code ON rd_style_bom (lower(bom_code));
COMMENT ON COLUMN rd_style_bom.bom_code IS 'BOM 单号，全局唯一，可编辑；系统默认按日期和流水号生成';
COMMENT ON COLUMN rd_style_bom.preparer_id IS '业务制单人，关联 sys_user.user_id；可在制单时选择，与记录创建人和实际操作审计人分开';
COMMIT;
