BEGIN;
COMMENT ON COLUMN rd_style.season IS '款式季节统计维度，新数据使用不含年份的季节选项，历史文本保留原值';
CREATE TABLE IF NOT EXISTS rd_style_attachment (
 style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
 oss_id bigint NOT NULL UNIQUE REFERENCES sys_oss(oss_id),
 category varchar(30) NOT NULL CHECK (category IN ('style_image','main_label','size_label','wash_label','hang_tag','tag_sticker','document')),
 sort_order integer NOT NULL DEFAULT 0,
 PRIMARY KEY(style_id,oss_id)
);
COMMENT ON TABLE rd_style_attachment IS '款式与子款的图片、标识及文档附件，按款式权限控制访问';
COMMENT ON COLUMN rd_style_attachment.style_id IS '所属款式或子款主键，关联 rd_style.style_id；套装及子款各自维护附件';
COMMENT ON COLUMN rd_style_attachment.oss_id IS '私有对象存储文件主键，关联 sys_oss.oss_id；同一文件仅能关联一个款式';
COMMENT ON COLUMN rd_style_attachment.category IS '附件类型：style_image 款式图片、main_label 主标、size_label 尺码标、wash_label 洗标、hang_tag 吊牌、tag_sticker 吊牌贴纸、document 文档';
COMMENT ON COLUMN rd_style_attachment.sort_order IS '同一款式同类附件的展示顺序，数值越小越靠前';
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON rd_style_attachment TO %I',app_role);
END $$;
COMMIT;
