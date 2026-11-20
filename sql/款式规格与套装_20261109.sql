-- 文件用途：为“款式规格与套装_20261109”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“款式规格与套装_20261109”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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

ALTER TABLE rd_style ADD COLUMN IF NOT EXISTS production_mode varchar(12) NOT NULL DEFAULT 'single'
    CHECK (production_mode IN ('single','combined','separate'));
ALTER TABLE rd_style ADD COLUMN IF NOT EXISTS same_hanger boolean NOT NULL DEFAULT false
    CHECK (NOT same_hanger OR production_mode='combined');
COMMENT ON COLUMN rd_style.production_mode IS '款式生产模式：single 单款，combined 合并生产套装，separate 分开生产套装';
COMMENT ON COLUMN rd_style.same_hanger IS '是否要求合并生产套装的所有组成子款共用同一衣架：true=要求，false=不要求；仅合并生产方式适用';

CREATE TABLE IF NOT EXISTS rd_style_member (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    child_id bigint NOT NULL REFERENCES rd_style(style_id),
    role_name varchar(60) NOT NULL CHECK (btrim(role_name)<>''),
    quantity integer NOT NULL CHECK (quantity>0),
    sort_order integer NOT NULL,
    PRIMARY KEY (style_id,child_id),
    CHECK (style_id<>child_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_member_child ON rd_style_member(child_id);
COMMENT ON TABLE rd_style_member IS '套装与独立子款的组成关系，移除关系不删除子款';
COMMENT ON COLUMN rd_style_member.style_id IS '所属套装款式主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_member.child_id IS '组成套装的独立单款主键，关联 rd_style.style_id；移除组成不删除子款档案';
COMMENT ON COLUMN rd_style_member.role_name IS '子款在此套装中的业务名称，例如上衣、下装或帽子';
COMMENT ON COLUMN rd_style_member.quantity IS '每一套包含的本子款件数，必须为正整数';
COMMENT ON COLUMN rd_style_member.sort_order IS '套装子款显示和处理顺序，数值越小越靠前';

CREATE TABLE IF NOT EXISTS rd_style_color (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    color_id bigint NOT NULL REFERENCES base_color(color_id),
    PRIMARY KEY (style_id,color_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_color_color ON rd_style_color(color_id);
COMMENT ON TABLE rd_style_color IS '单款可用颜色';
COMMENT ON COLUMN rd_style_color.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_color.color_id IS '单款可用颜色主键，关联 base_color.color_id';

CREATE TABLE IF NOT EXISTS rd_style_size (
    style_id bigint NOT NULL REFERENCES rd_style(style_id) ON DELETE CASCADE,
    size_id bigint NOT NULL REFERENCES sys_size(id),
    PRIMARY KEY (style_id,size_id)
);
CREATE INDEX IF NOT EXISTS idx_rd_style_size_size ON rd_style_size(size_id);
COMMENT ON TABLE rd_style_size IS '单款可用尺码，保存具体尺码而非尺码组';
COMMENT ON COLUMN rd_style_size.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_size.size_id IS '单款可用尺码主键，关联 sys_size.id；此处保存实际尺码而非尺码组';

CREATE TABLE IF NOT EXISTS rd_style_spec_exclusion (
    style_id bigint NOT NULL,
    color_id bigint NOT NULL,
    size_id bigint NOT NULL,
    PRIMARY KEY (style_id,color_id,size_id),
    FOREIGN KEY (style_id,color_id) REFERENCES rd_style_color(style_id,color_id) ON DELETE CASCADE,
    FOREIGN KEY (style_id,size_id) REFERENCES rd_style_size(style_id,size_id) ON DELETE CASCADE
);
COMMENT ON TABLE rd_style_spec_exclusion IS '单款明确排除的颜色尺码组合，其余组合由所选颜色和尺码自动生成';
COMMENT ON COLUMN rd_style_spec_exclusion.style_id IS '单款档案主键，关联 rd_style.style_id';
COMMENT ON COLUMN rd_style_spec_exclusion.color_id IS '被排除组合的颜色主键，关联 base_color.color_id';
COMMENT ON COLUMN rd_style_spec_exclusion.size_id IS '被排除组合的尺码主键，关联 sys_size.id';

-- 让款式分组可在录入首个标签之前维护。
INSERT INTO sys_tag_module(module_key,module_name) VALUES ('rd_style','款式档案')
ON CONFLICT (module_key) DO NOTHING;
INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order)
VALUES ('rd_style','默认分组','1',0) ON CONFLICT DO NOTHING;

DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO STRICT app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON rd_style_member,rd_style_color,rd_style_size,rd_style_spec_exclusion TO %I',app_role);
END $$;
COMMIT;
