-- 文件用途：为“人员分类授权_20261106”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
-- 文件版本：1.0.0
-- 功能目的：为“人员分类授权_20261106”相关功能创建或调整数据库结构、约束、索引、权限或字段注释。
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
-- 先记录该员工的范围模式，再保存选中的分类类型或节点；没有授权主记录时沿用既有“全部可见”兼容语义。
BEGIN;
CREATE TABLE IF NOT EXISTS sys_person_category_scope (
    user_id bigint PRIMARY KEY REFERENCES sys_user(user_id) ON DELETE CASCADE,
    scope_mode varchar(12) NOT NULL CHECK (scope_mode IN ('ALL','NONE','SELECTED'))
);
COMMENT ON TABLE sys_person_category_scope IS '人员分类授权；无记录表示全部分类可见';
COMMENT ON COLUMN sys_person_category_scope.user_id IS '员工账号稳定主键';
COMMENT ON COLUMN sys_person_category_scope.scope_mode IS '授权模式：ALL全部、NONE无权限、SELECTED指定范围';

-- SELECTED 模式下的授权明细二选一：整类授权包含后续新增子类，节点授权按 include_children 决定是否下钻。
CREATE TABLE IF NOT EXISTS sys_person_category_grant (
    user_id bigint NOT NULL REFERENCES sys_person_category_scope(user_id) ON DELETE CASCADE,
    scheme_id bigint REFERENCES base_category_scheme(scheme_id),
    category_id bigint REFERENCES base_category(category_id),
    include_children boolean NOT NULL,
    CHECK ((scheme_id IS NOT NULL AND category_id IS NULL AND include_children)
        OR (scheme_id IS NULL AND category_id IS NOT NULL)),
    UNIQUE NULLS NOT DISTINCT (user_id, scheme_id, category_id)
);
COMMENT ON TABLE sys_person_category_grant IS '人员指定分类范围，授权对象为分类类型或分类节点';
COMMENT ON COLUMN sys_person_category_grant.user_id IS '所属人员分类授权账号主键';
COMMENT ON COLUMN sys_person_category_grant.scheme_id IS '整类授权的分类类型主键';
COMMENT ON COLUMN sys_person_category_grant.category_id IS '节点授权的分类主键';
COMMENT ON COLUMN sys_person_category_grant.include_children IS '是否包含现有及未来下级节点';
COMMIT;
GRANT SELECT, INSERT, UPDATE, DELETE ON sys_person_category_scope, sys_person_category_grant TO zhizhun;
