BEGIN;
ALTER TABLE inv_material ADD COLUMN IF NOT EXISTS variant_fields jsonb NOT NULL DEFAULT '[]';
ALTER TABLE inv_material_variant ADD COLUMN IF NOT EXISTS attributes jsonb NOT NULL DEFAULT '{}';
COMMENT ON COLUMN inv_material.variant_fields IS '当前属性模板中按独立规格填写的字段键数组';
COMMENT ON COLUMN inv_material_variant.attributes IS '按模板字段键保存该物料规格的属性值，单位由模板定义';
COMMIT;
