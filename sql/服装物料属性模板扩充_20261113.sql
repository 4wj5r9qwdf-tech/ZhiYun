-- 文件用途：为“服装物料属性模板扩充_20261113”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“服装物料属性模板扩充_20261113”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
BEGIN;
-- 补充常用辅料和包材；保留已有模板编码、字段及档案值。
INSERT INTO inv_material_spec_template(template_code,template_name,sort_order,fields) VALUES
 ('hook_loop','粘扣带',65,'[{"key":"material","label":"材质","type":"text"},{"key":"surfaceType","label":"钩面或毛面","type":"text"},{"key":"width","label":"带宽","type":"decimal","unit":"mm"},{"key":"length","label":"裁切长度","type":"decimal","unit":"mm"},{"key":"backing","label":"背胶或背衬","type":"text"}]'::jsonb),
 ('cord_hardware','绳扣、绳头与环扣',66,'[{"key":"hardwareType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"openingWidth","label":"内口宽","type":"decimal","unit":"mm"},{"key":"cordDiameter","label":"适配绳径","type":"decimal","unit":"mm"},{"key":"surfaceFinish","label":"表面处理","type":"text"}]'::jsonb),
 ('padding','垫肩、胸垫与定型辅料',67,'[{"key":"paddingType","label":"辅料类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"shape","label":"形状","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"}]'::jsonb),
 ('filling','填充料与保暖棉',68,'[{"key":"fillingType","label":"填充类型","type":"text"},{"key":"composition","label":"成分","type":"text"},{"key":"form","label":"形态","type":"text"},{"key":"weightGsm","label":"克重","type":"decimal","unit":"g/m²"},{"key":"width","label":"幅宽","type":"decimal","unit":"cm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"},{"key":"fillPower","label":"蓬松度","type":"decimal","unit":"cu in/oz"}]'::jsonb),
 ('decoration','章标、贴花与装饰辅料',71,'[{"key":"decorationType","label":"装饰类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"attachment","label":"固定方式","type":"text"},{"key":"artworkVersion","label":"图案版本","type":"text"}]'::jsonb),
 ('heat_transfer','烫画、热转印与压胶材料',72,'[{"key":"transferType","label":"材料类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"artworkVersion","label":"图案版本","type":"text"},{"key":"applicationReference","label":"供应商工艺资料编号","type":"text"}]'::jsonb),
 ('sticker','贴纸、条码标与不干胶',73,'[{"key":"stickerType","label":"标签类型","type":"text"},{"key":"faceMaterial","label":"面材","type":"text"},{"key":"adhesive","label":"胶型","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"printVersion","label":"印刷版号","type":"text"}]'::jsonb),
 ('tag_fastener','吊绳、胶针与吊牌扣',74,'[{"key":"fastenerType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"cordDiameter","label":"绳径","type":"decimal","unit":"mm"},{"key":"closure","label":"扣合方式","type":"text"}]'::jsonb),
 ('hanger','衣架、裤架与包装夹',101,'[{"key":"hangerType","label":"配件类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"clipSpacing","label":"夹距","type":"decimal","unit":"mm"},{"key":"hookType","label":"挂钩形式","type":"text"}]'::jsonb),
 ('paper_insert','拷贝纸、衬纸与纸板',102,'[{"key":"paperType","label":"纸品类型","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"paperWeight","label":"克重","type":"decimal","unit":"g/m²"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"}]'::jsonb),
 ('packing_supply','封箱胶带、捆扎带与缠绕膜',103,'[{"key":"supplyType","label":"包材类型","type":"text"},{"key":"material","label":"材质","type":"text"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"μm"},{"key":"rollLength","label":"公称卷长","type":"decimal","unit":"m"}]'::jsonb),
 ('desiccant','干燥剂与防护小包',104,'[{"key":"agentType","label":"内容物类型","type":"text"},{"key":"netWeight","label":"单包净重","type":"decimal","unit":"g"},{"key":"packMaterial","label":"外袋材质","type":"text"},{"key":"length","label":"包长","type":"decimal","unit":"mm"},{"key":"width","label":"包宽","type":"decimal","unit":"mm"}]'::jsonb),
 ('production_supply','裁剪、缝制与印花耗材',105,'[{"key":"supplyType","label":"耗材类型","type":"text"},{"key":"material","label":"材质或成分","type":"text"},{"key":"equipmentModel","label":"适用设备或型号","type":"text"},{"key":"productGrade","label":"牌号或系列","type":"text"},{"key":"technicalReference","label":"技术资料编号","type":"text"}]'::jsonb),
 ('general','通用物料',110,'[{"key":"material","label":"材质","type":"text"},{"key":"length","label":"长度","type":"decimal","unit":"mm"},{"key":"width","label":"宽度","type":"decimal","unit":"mm"},{"key":"height","label":"高度","type":"decimal","unit":"mm"},{"key":"thickness","label":"厚度","type":"decimal","unit":"mm"},{"key":"diameter","label":"直径","type":"decimal","unit":"mm"},{"key":"technicalReference","label":"技术资料编号","type":"text"}]'::jsonb)
ON CONFLICT (template_code) DO NOTHING;

WITH additions(code, fields) AS (VALUES
 ('fabric','[{"key":"fabricFinish","label":"后整理","type":"text"},{"key":"fabricConstruction","label":"纱支与织物规格","type":"text"}]'::jsonb),
 ('interlining','[{"key":"adhesiveType","label":"胶点类型","type":"text"},{"key":"applicationReference","label":"供应商压烫资料编号","type":"text"}]'::jsonb),
 ('zipper','[{"key":"sliderLock","label":"拉头锁定形式","type":"text"},{"key":"pullerType","label":"拉片款式","type":"text"},{"key":"tapeMaterial","label":"布带材质","type":"text"}]'::jsonb),
 ('label','[{"key":"foldType","label":"折法","type":"text"},{"key":"printVersion","label":"印刷或织造版号","type":"text"},{"key":"language","label":"标识语言","type":"text"}]'::jsonb),
 ('carton','[{"key":"boxStyle","label":"箱型","type":"text"},{"key":"boardGrade","label":"纸板等级","type":"text"},{"key":"printVersion","label":"印刷版号","type":"text"}]'::jsonb)
)
UPDATE inv_material_spec_template t
SET fields = t.fields || COALESCE((
  SELECT jsonb_agg(f.value ORDER BY f.position)
  FROM jsonb_array_elements(a.fields) WITH ORDINALITY f(value, position)
  WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(t.fields) old WHERE old->>'key'=f.value->>'key')
), '[]'::jsonb)
FROM additions a WHERE t.template_code=a.code;
COMMIT;
