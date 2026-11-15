BEGIN;
-- 先建立档案内唯一索引，再移除旧的全局限制。
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_material_variant_material_code
    ON inv_material_variant(material_id, lower(code));
DROP INDEX IF EXISTS uq_inv_material_variant_code;
COMMENT ON COLUMN inv_material_variant.code IS '物料规格编码，同一物料内不区分大小写唯一';
COMMIT;
