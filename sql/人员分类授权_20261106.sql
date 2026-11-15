BEGIN;
CREATE TABLE IF NOT EXISTS sys_person_category_scope (
    user_id bigint PRIMARY KEY REFERENCES sys_user(user_id) ON DELETE CASCADE,
    scope_mode varchar(12) NOT NULL CHECK (scope_mode IN ('ALL','NONE','SELECTED'))
);
COMMENT ON TABLE sys_person_category_scope IS '人员分类授权；无记录表示全部分类可见';
COMMENT ON COLUMN sys_person_category_scope.user_id IS '员工账号稳定主键';
COMMENT ON COLUMN sys_person_category_scope.scope_mode IS '授权模式：ALL全部、NONE无权限、SELECTED指定范围';

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
