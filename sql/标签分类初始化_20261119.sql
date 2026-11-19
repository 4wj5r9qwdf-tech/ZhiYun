-- 已有功能的标签分类与常用标签初始化，重复执行不覆盖已有名称、状态和业务关联。
-- Initialize feature-scoped groups and suggestions without changing existing records.
INSERT INTO sys_tag_module(module_key,module_name)
VALUES ('person_info','人员信息'),('inventory_material','物料档案'),
       ('rd_style','款式档案'),('customer_master','客户档案'),
       ('warehouse_master','仓库管理'),('size','尺码管理'),('size_group','尺码组管理')
ON CONFLICT (module_key) DO NOTHING;

INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order)
SELECT m.module_key,'默认分组','1',0 FROM sys_tag_module m
WHERE m.module_key IN ('person_info','inventory_material','rd_style','customer_master','warehouse_master','size','size_group')
  AND NOT EXISTS (SELECT 1 FROM sys_tag_group g WHERE g.module_key=m.module_key AND g.is_default='1')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order)
SELECT v.module_key,v.group_name,'0',v.sort_order FROM (VALUES
 ('person_info','擅长工序工艺',10),('person_info','工序熟练度',20),
 ('person_info','服装类型经验',30),('person_info','工作能力',40),
 ('person_info','设备操作能力',50),('person_info','工作时间偏好',60),
 ('person_info','请假及出勤状态',70),('person_info','家庭情况自主申报',80),
 ('person_info','培训与认证',90),
 ('inventory_material','材料特征',10),('inventory_material','适用工艺',20),
 ('inventory_material','采购属性',30),('inventory_material','储存要求',40),
 ('rd_style','产品类型',10),('rd_style','工艺特点',20),('rd_style','生产要求',30),('rd_style','质量风险',40),
 ('customer_master','合作特点',10),('customer_master','质量要求',20),('customer_master','交付要求',30),
 ('warehouse_master','仓储条件',10),('warehouse_master','适用物料',20),('warehouse_master','特殊储存要求',30),
 ('size','尺码类型',10),('size_group','尺码组类型',10)
) AS v(module_key,group_name,sort_order)
ON CONFLICT DO NOTHING;

-- 同一功能中已有同名标签时保留现有记录与分组，不重复生成或迁移关联。
INSERT INTO sys_tag(module_key,tag_group_id,tag_name,is_common,status,remark)
SELECT v.module_key,g.group_id,v.tag_name,'1','0','项目初始化常用提示，可按业务维护'
FROM (VALUES
 ('person_info','擅长工序工艺','平车'),('person_info','擅长工序工艺','包边'),
 ('person_info','擅长工序工艺','锁眼'),('person_info','擅长工序工艺','钉扣'),
 ('person_info','服装类型经验','衬衫'),('person_info','服装类型经验','裤装'),
 ('person_info','服装类型经验','羽绒服'),
 ('person_info','工作时间偏好','周末可出勤'),('person_info','工作时间偏好','周末需提前协商'),
 ('rd_style','产品类型','衬衫'),('rd_style','产品类型','裤装'),('rd_style','产品类型','羽绒服')
) AS v(module_key,group_name,tag_name)
JOIN sys_tag_group g ON g.module_key=v.module_key AND lower(g.group_name)=lower(v.group_name)
WHERE NOT EXISTS (SELECT 1 FROM sys_tag t WHERE t.module_key=v.module_key AND lower(t.tag_name)=lower(v.tag_name))
ON CONFLICT DO NOTHING;
