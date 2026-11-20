-- 文件用途：为“标签分组_20261108”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“标签分组_20261108”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 标签分组结构升级：为既有标签归入“默认分组”，保留 tag_id 和业务关系。
-- 由数据库管理员执行；应用角色按 sys_tag 所有者动态授权，不硬编码账号。
BEGIN;

CREATE SEQUENCE IF NOT EXISTS sys_tag_group_id_seq;
CREATE TABLE IF NOT EXISTS sys_tag_group (
    group_id     bigint        NOT NULL DEFAULT nextval('sys_tag_group_id_seq'),
    module_key   varchar(50)   NOT NULL,
    group_name   varchar(100)  NOT NULL,
    is_default   char(1)       NOT NULL DEFAULT '0',
    sort_order   integer       NOT NULL DEFAULT 10,
    create_by    bigint,
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    bigint,
    update_time  timestamp,
    CONSTRAINT sys_tag_group_pk PRIMARY KEY (group_id),
    CONSTRAINT sys_tag_group_module_fk FOREIGN KEY (module_key) REFERENCES sys_tag_module(module_key),
    CONSTRAINT sys_tag_group_id_module_uk UNIQUE (group_id,module_key),
    CONSTRAINT sys_tag_group_default_ck CHECK (is_default IN ('0','1'))
);
ALTER SEQUENCE sys_tag_group_id_seq OWNED BY sys_tag_group.group_id;
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_module_name ON sys_tag_group(module_key,lower(group_name));
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_one_default ON sys_tag_group(module_key) WHERE is_default='1';
CREATE INDEX IF NOT EXISTS idx_sys_tag_group_module_order ON sys_tag_group(module_key,is_default DESC,sort_order,group_id);

INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time)
SELECT module_key,'默认分组','1',0,now() FROM sys_tag_module
ON CONFLICT (module_key,lower(group_name)) DO UPDATE SET is_default='1';

ALTER TABLE sys_tag ADD COLUMN IF NOT EXISTS tag_group_id bigint;
UPDATE sys_tag t SET tag_group_id=g.group_id
FROM sys_tag_group g
WHERE g.module_key=t.module_key AND g.is_default='1' AND t.tag_group_id IS NULL;
ALTER TABLE sys_tag ALTER COLUMN tag_group_id SET NOT NULL;

ALTER TABLE sys_tag DROP CONSTRAINT IF EXISTS sys_tag_group_module_fk;
ALTER TABLE sys_tag ADD CONSTRAINT sys_tag_group_module_fk
    FOREIGN KEY(tag_group_id,module_key) REFERENCES sys_tag_group(group_id,module_key);

DROP INDEX IF EXISTS idx_sys_tag_module_name;
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tag_group_tag_name
    ON sys_tag(module_key,tag_group_id,lower(tag_name));
CREATE INDEX IF NOT EXISTS idx_sys_tag_module_status
    ON sys_tag(module_key,status,is_common);

COMMENT ON TABLE sys_tag_group IS '通用属性标签分组；分组按业务功能隔离，每个功能保留默认分组';
COMMENT ON COLUMN sys_tag_group.group_id IS '标签分组主键，由 PostgreSQL 序列生成';
COMMENT ON COLUMN sys_tag_group.module_key IS '标签分组所属业务功能标识';
COMMENT ON COLUMN sys_tag_group.group_name IS '标签分组名称；同功能内不区分大小写唯一';
COMMENT ON COLUMN sys_tag_group.is_default IS '是否为系统默认分组：0否/1是；默认分组不可删除或重命名';
COMMENT ON COLUMN sys_tag_group.sort_order IS '分组显示顺序；默认分组优先展示';
COMMENT ON COLUMN sys_tag_group.create_by IS '创建分组的操作人用户ID';
COMMENT ON COLUMN sys_tag_group.create_time IS '分组创建时间';
COMMENT ON COLUMN sys_tag_group.update_by IS '最后修改分组的操作人用户ID';
COMMENT ON COLUMN sys_tag_group.update_time IS '分组最后修改时间';
COMMENT ON COLUMN sys_tag.tag_group_id IS '标签所属分组ID，必须与标签功能归属一致';

DO $$
DECLARE app_role text;
BEGIN
    SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass;
    IF app_role IS NULL THEN RAISE EXCEPTION '无法从 public.sys_tag 识别应用数据库角色'; END IF;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON public.sys_tag_group TO %I',app_role);
    EXECUTE format('GRANT USAGE,SELECT,UPDATE ON SEQUENCE public.sys_tag_group_id_seq TO %I',app_role);
END $$;

COMMIT;
