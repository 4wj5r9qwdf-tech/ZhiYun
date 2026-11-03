-- 客户档案一期增量迁移。使用序列生成稳定客户编号，不删除历史记录。
BEGIN;
CREATE SEQUENCE IF NOT EXISTS customer_code_seq;
CREATE TABLE IF NOT EXISTS customer_master (
 customer_id bigserial PRIMARY KEY,
 customer_code varchar(50) NOT NULL UNIQUE DEFAULT ('KH' || lpad(nextval('customer_code_seq')::text,8,'0')),
 customer_name varchar(200) NOT NULL,
 unified_social_credit_code varchar(18),
 customer_short_name varchar(100),
 country_region varchar(100),
 sales_representative_id bigint NOT NULL,
 status char(1) NOT NULL DEFAULT '0' CHECK (status IN ('0','1')),
 remark varchar(2000),
 create_by bigint,
 create_time timestamp NOT NULL DEFAULT now(),
 update_by bigint,
 update_time timestamp
);
COMMENT ON TABLE customer_master IS '客户档案；供订单及样衣引用，停用不改变历史单据';
COMMENT ON COLUMN customer_master.customer_id IS '客户主键';
COMMENT ON COLUMN customer_master.customer_code IS '系统自动生成的稳定客户编号';
COMMENT ON COLUMN customer_master.customer_name IS '客户名称；允许同名，保存前提醒';
COMMENT ON COLUMN customer_master.unified_social_credit_code IS '统一社会信用代码；境外客户可空';
COMMENT ON COLUMN customer_master.customer_short_name IS '客户简称';
COMMENT ON COLUMN customer_master.country_region IS '国家地区，手动录入';
COMMENT ON COLUMN customer_master.sales_representative_id IS '业务员，关联人员信息，不校验特定岗位';
COMMENT ON COLUMN customer_master.status IS '状态：0启用，1停用；停用仅禁止新增业务引用';
COMMENT ON COLUMN customer_master.remark IS '备注';
COMMENT ON COLUMN customer_master.create_by IS '创建人用户ID';
COMMENT ON COLUMN customer_master.create_time IS '创建时间';
COMMENT ON COLUMN customer_master.update_by IS '更新人用户ID';
COMMENT ON COLUMN customer_master.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_customer_master_name ON customer_master(lower(customer_name));
CREATE TABLE IF NOT EXISTS customer_contact (
 contact_id bigserial PRIMARY KEY,
 customer_id bigint NOT NULL REFERENCES customer_master(customer_id),
 contact_name varchar(100) NOT NULL,
 contact_title varchar(100),
 phone_number varchar(100),
 email varchar(200),
 contact_role varchar(100),
 is_primary boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE customer_contact IS '客户多联系人';
COMMENT ON COLUMN customer_contact.contact_id IS '联系人主键';
COMMENT ON COLUMN customer_contact.customer_id IS '所属客户ID';
COMMENT ON COLUMN customer_contact.contact_name IS '联系人姓名';
COMMENT ON COLUMN customer_contact.contact_title IS '联系人职务';
COMMENT ON COLUMN customer_contact.phone_number IS '联系电话或手机';
COMMENT ON COLUMN customer_contact.email IS '电子邮箱';
COMMENT ON COLUMN customer_contact.contact_role IS '联系人用途，手动填写';
COMMENT ON COLUMN customer_contact.is_primary IS '是否默认联系人，每客户最多一位';
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_primary_contact ON customer_contact(customer_id) WHERE is_primary;
CREATE TABLE IF NOT EXISTS customer_address (
 address_id bigserial PRIMARY KEY,
 customer_id bigint NOT NULL REFERENCES customer_master(customer_id),
 address_type varchar(100) NOT NULL,
 recipient_name varchar(100),
 recipient_phone varchar(100),
 country_region varchar(100),
 province varchar(100),
 city varchar(100),
 district varchar(100),
 address_line varchar(500),
 postal_code varchar(30),
 is_default boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE customer_address IS '客户多地址，地区和地址手工录入';
COMMENT ON COLUMN customer_address.address_id IS '地址主键';
COMMENT ON COLUMN customer_address.customer_id IS '所属客户ID';
COMMENT ON COLUMN customer_address.address_type IS '地址用途';
COMMENT ON COLUMN customer_address.recipient_name IS '收件人';
COMMENT ON COLUMN customer_address.recipient_phone IS '收件电话';
COMMENT ON COLUMN customer_address.country_region IS '国家地区';
COMMENT ON COLUMN customer_address.province IS '省或州';
COMMENT ON COLUMN customer_address.city IS '城市';
COMMENT ON COLUMN customer_address.district IS '区县';
COMMENT ON COLUMN customer_address.address_line IS '详细地址';
COMMENT ON COLUMN customer_address.postal_code IS '邮政编码';
COMMENT ON COLUMN customer_address.is_default IS '是否该用途的默认地址';
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_default_address ON customer_address(customer_id,address_type) WHERE is_default;
-- 独立权限条目供角色管理勾选；不自动向现有普通角色授予客户权限。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000201,'客户查询',5,110,'#','','N','Y','F','0','0','system:customer:list','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000202,'客户新增',5,111,'#','','N','Y','F','0','0','system:customer:add','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1762400000000000203,'客户修改与启停',5,112,'#','','N','Y','F','0','0','system:customer:edit','#',now()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:customer:edit');
COMMIT;
