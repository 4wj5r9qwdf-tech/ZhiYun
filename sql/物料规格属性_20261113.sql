BEGIN;
ALTER TABLE inv_material ADD COLUMN IF NOT EXISTS variant_fields jsonb NOT NULL DEFAULT '[]';
ALTER TABLE inv_material_variant ADD COLUMN IF NOT EXISTS attributes jsonb NOT NULL DEFAULT '{}';
COMMENT ON COLUMN inv_material.variant_fields IS '物料档案中改为逐规格维护的模板稳定字段键数组；未列出的字段仍按物料主档共用';
COMMENT ON COLUMN inv_material_variant.attributes IS '本物料规格的属性值 JSON，以模板稳定字段键为键，字段值类型和单位遵循所选规格模板';
COMMIT;
