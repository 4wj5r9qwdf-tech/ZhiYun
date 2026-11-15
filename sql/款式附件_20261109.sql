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
COMMENT ON COLUMN rd_style_attachment.style_id IS '所属款式ID，套装和子款分别维护';
COMMENT ON COLUMN rd_style_attachment.oss_id IS '私有文件ID，同一文件只可关联一个款式';
COMMENT ON COLUMN rd_style_attachment.category IS '附件类型：款式图片、主标、尺码标、洗标、吊牌、吊牌贴纸或文档';
COMMENT ON COLUMN rd_style_attachment.sort_order IS '同款附件展示顺序';
DO $$
DECLARE app_role text;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
 EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON rd_style_attachment TO %I',app_role);
END $$;
COMMIT;
