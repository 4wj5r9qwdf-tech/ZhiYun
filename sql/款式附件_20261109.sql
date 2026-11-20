-- 文件用途：为“款式附件_20261109”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“款式附件_20261109”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
