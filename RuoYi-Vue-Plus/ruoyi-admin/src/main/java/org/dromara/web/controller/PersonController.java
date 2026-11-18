package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.event.OnlineUserCleanEvent;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 人员信息管理（人员模块）。
 * 口径（2026-10-24 定稿）：建人员=建登录账号；工号(6位)=登录名；默认密码123456(BCrypt)；
 *   支持手机号(11位)登录；角色/岗位在人员信息里直接选；内置仅"管理员"角色。
 * 表：sys_user（登录账号+基础身份/联系方式）+ sys_person_ext（任职/合同/社保/证件/住宿登记）
 *   + 关系 sys_user_post（岗位多选）/ sys_user_role（角色多选）。
 * 住宿只登记 is_dorm，床位在宿舍模块。敏感字段（证件号/手机号/银行卡）脱敏。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/person")
public class PersonController {

    private final JdbcTemplate jdbcTemplate;

    /** 业务职责是单据责任人资格，不等同于岗位、系统角色或权限。 */
    private static final Map<String, String> BUSINESS_DUTIES = Map.of(
        "SALES", "业务员", "FOLLOWER", "跟单员", "PURCHASER", "采购员",
        "PLANNER", "计划员", "WAREHOUSE", "仓管员", "QUALITY", "质检员");

    @SaCheckPermission("system:person:list")
    @GetMapping("/business-duty-options")
    public R<List<Map<String, String>>> businessDutyOptions() {
        return R.ok(List.of(
            Map.of("code", "SALES", "name", "业务员"),
            Map.of("code", "FOLLOWER", "name", "跟单员"),
            Map.of("code", "PURCHASER", "name", "采购员"),
            Map.of("code", "PLANNER", "name", "计划员"),
            Map.of("code", "WAREHOUSE", "name", "仓管员"),
            Map.of("code", "QUALITY", "name", "质检员")));
    }

    /** 分类授权请求，全部可见为默认值。 / Represent employee category scope with full visibility by default. */
    public record CategoryScope(String mode, List<CategoryGrant> grants) {}

    /** 授权对象使用稳定主键。 / Identify a scheme or category with a stable ID. */
    public record CategoryGrant(Long schemeId, Long categoryId, boolean includeChildren) {}

    /** 返回人员当前授权及分类目录。 / Load the current employee scope and taxonomy. */
    @GetMapping("/{userId}/category-scope")
    public R<Map<String, Object>> categoryScope(@PathVariable Long userId) {
        String name = checkCategoryScopeTarget(userId, false);
        return R.ok(Map.of(
            "scope", readCategoryScope(userId, name),
            "schemes", jdbcTemplate.queryForList("""
                SELECT scheme_id::text AS id, scheme_name AS name, status
                FROM base_category_scheme WHERE del_flag='0' ORDER BY sort_order, scheme_id
                """),
            "categories", jdbcTemplate.queryForList("""
                SELECT category_id::text AS id, scheme_id::text AS "schemeId",
                       parent_id::text AS "parentId", category_name AS name, status
                FROM base_category WHERE del_flag='0' ORDER BY sort_order, category_id
                """)));
    }

    /** 在同一事务替换授权并记录真实前后值。 / Replace grants and audit the persisted change atomically. */
    @PutMapping("/{userId}/category-scope")
    @Transactional
    @Log(title = "人员分类权限", businessType = BusinessType.UPDATE)
    public R<Void> saveCategoryScope(@PathVariable Long userId, @RequestBody CategoryScope scope) {
        String name = checkCategoryScopeTarget(userId, true);
        if (scope.mode() == null || !Set.of("ALL", "NONE", "SELECTED").contains(scope.mode())
            || scope.grants() == null || "SELECTED".equals(scope.mode()) == scope.grants().isEmpty()) {
            throw new ServiceException("请选择授权模式；指定范围须至少添加一项，其他模式不提交授权项");
        }
        Set<Long> schemes = new HashSet<>();
        Set<Long> categories = new HashSet<>();
        for (CategoryGrant grant : scope.grants()) {
            if (grant == null || (grant.schemeId() == null) == (grant.categoryId() == null)) {
                throw new ServiceException("每项授权须选择一个分类类型或分类节点");
            }
            if (grant.schemeId() != null) {
                if (!grant.includeChildren() || !schemes.add(grant.schemeId()) || jdbcTemplate.queryForList(
                    "SELECT scheme_id FROM base_category_scheme WHERE scheme_id=? AND del_flag='0' FOR SHARE",
                    grant.schemeId()).isEmpty()) {
                    throw new ServiceException("分类类型无效、重复或未包含下级");
                }
            } else if (!categories.add(grant.categoryId()) || jdbcTemplate.queryForList(
                "SELECT category_id FROM base_category WHERE category_id=? AND del_flag='0' FOR SHARE",
                grant.categoryId()).isEmpty()) {
                throw new ServiceException("分类节点已删除、不存在或重复");
            }
        }
        var before = readCategoryScope(userId, name);
        jdbcTemplate.update("DELETE FROM sys_person_category_scope WHERE user_id=?", userId);
        if (!"ALL".equals(scope.mode())) {
            jdbcTemplate.update("INSERT INTO sys_person_category_scope VALUES (?,?)", userId, scope.mode());
            for (CategoryGrant grant : scope.grants()) {
                jdbcTemplate.update("INSERT INTO sys_person_category_grant VALUES (?,?,?,?)",
                    userId, grant.schemeId(), grant.categoryId(), grant.includeChildren());
            }
        }
        BusinessAudit.record("设置人员分类权限", userId, "人员",
            categoryScopeAudit(before), categoryScopeAudit(readCategoryScope(userId, name)));
        return R.ok();
    }

    /** 首期仅超管维护普通人员授权，锁定人员以串行化保存。 / Restrict administration and serialize writes per employee. */
    private String checkCategoryScopeTarget(Long userId, boolean lock) {
        Long actor = LoginHelper.getUserId();
        if (actor == null || !LoginHelper.isSuperAdmin(actor)) {
            throw new ServiceException("仅超级管理员可以维护人员分类权限");
        }
        var names = jdbcTemplate.queryForList(
            "SELECT nick_name FROM sys_user WHERE user_id=? AND del_flag='0'" + (lock ? " FOR UPDATE" : ""),
            String.class, userId);
        if (names.isEmpty() || LoginHelper.isSuperAdmin(userId)) {
            throw new ServiceException("人员不存在或不适用分类授权");
        }
        return names.getFirst();
    }

    /** 回显历史授权名称，包含已停用或删除的授权目标。 / Preserve labels for stale grants during review. */
    private Map<String, Object> readCategoryScope(Long userId, String name) {
        var modes = jdbcTemplate.queryForList(
            "SELECT scope_mode FROM sys_person_category_scope WHERE user_id=?", String.class, userId);
        var grants = jdbcTemplate.queryForList("""
            SELECT g.scheme_id::text AS "schemeId", g.category_id::text AS "categoryId",
                   g.include_children AS "includeChildren", coalesce(s.scheme_name,c.category_name) AS name
            FROM sys_person_category_grant g
            LEFT JOIN base_category_scheme s ON s.scheme_id=g.scheme_id
            LEFT JOIN base_category c ON c.category_id=g.category_id
            WHERE g.user_id=? ORDER BY g.scheme_id, g.category_id
            """, userId);
        return Map.of("name", name, "mode", modes.isEmpty() ? "ALL" : modes.getFirst(), "grants", grants);
    }

    /** 转换业务审计字段。 / Label the employee scope snapshot for audit. */
    private Map<String, Object> categoryScopeAudit(Map<String, Object> scope) {
        return Map.of("人员", scope.get("name"), "授权模式", scope.get("mode"), "分类授权", scope.get("grants"));
    }

    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 人员列表（分页，join 扩展表+车间+岗位+角色；敏感字段脱敏）。
     * 筛选：keyword(工号/姓名/手机号)、empStatus、orgId、gender、posts(岗位名)、roles(角色名)。
     * 排序：orderBy=字段:asc|desc（白名单校验，防注入）。
     */
    @SaCheckPermission("system:person:list")
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String empStatus,
                                       @RequestParam(required = false) Long orgId,
                                       @RequestParam(required = false) String gender,
                                       @RequestParam(required = false) String posts,
                                       @RequestParam(required = false) String roles,
                                       @RequestParam(required = false) String businessDuty,
                                       @RequestParam(required = false) String orderBy,
                                       @RequestParam(defaultValue = "0") String delFlag,
                                       @RequestParam(defaultValue = "0") String includeSystem,
                                       @RequestParam(defaultValue = "0") String subtree,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        // delFlag：0=正常(默认) / 1=已删除(查已删可恢复) / all=全部
        String delCond = "all".equals(delFlag) ? "" : ("u.del_flag='" + ("1".equals(delFlag) ? "1" : "0") + "'");
        StringBuilder where = new StringBuilder(delCond.isEmpty() ? "WHERE 1=1" : ("WHERE " + delCond));
        // 默认隐藏内置系统账号 admin（它不是工厂员工）；includeSystem=1 才显示
        if (!"1".equals(includeSystem)) where.append(" AND u.user_name<>'admin'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        applyFilters(where, args, keyword, empStatus, "1".equals(subtree) ? null : orgId, gender, posts, roles, businessDuty);
        // 树节点筛选：orgId 按"含下级"展开（组织节点，与数据范围同款）
        if (orgId != null && "1".equals(subtree)) {
            appendSubtreeFilter(where, orgId);
        }
        applyDataScope(where, args);
        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user u LEFT JOIN sys_person_ext e ON u.user_id=e.user_id " + where, Long.class, args.toArray());
        String order = safeOrderBy(orderBy);
        int offset = (pageNum - 1) * pageSize;
        args.add(pageSize); args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + where.toString() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("phone_masked", mask(str(r.get("phone_number"))));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        return R.ok(result);
    }

    /**
     * 导出人员（CSV 带 BOM，Excel 可开；全量按筛选；敏感字段脱敏）。
     */
    @SaCheckPermission("system:person:export")
    @GetMapping("/export")
    public R<Map<String, Object>> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String empStatus,
                                         @RequestParam(required = false) Long orgId,
                                         @RequestParam(required = false) String gender,
                                         @RequestParam(required = false) String posts,
                                         @RequestParam(required = false) String roles,
                                         @RequestParam(required = false) String businessDuty) {
        StringBuilder where = new StringBuilder("WHERE u.del_flag='0'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        applyFilters(where, args, keyword, empStatus, orgId, gender, posts, roles, businessDuty);
        if (orgId != null) {
            appendSubtreeFilter(where, orgId);
        }
        applyDataScope(where, args);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + where.toString() + " ORDER BY u.user_id DESC", args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("phone_masked", mask(str(r.get("phone_number"))));
        }
        StringBuilder sb = new StringBuilder("﻿工号,姓名,性别,手机号,部门,岗位,角色,业务职责,状态,员工卡\r\n");
        for (Map<String, Object> r : rows) {
            sb.append(csv(str(r.get("job_no")))).append(',')
              .append(csv(str(r.get("name")))).append(',')
              .append(csv("1".equals(str(r.get("gender"))) ? "男" : "2".equals(str(r.get("gender"))) ? "女" : "")).append(',')
              .append(csv(str(r.get("phone_masked")))).append(',')
              .append(csv(str(r.get("org_name")))).append(',')
              .append(csv(str(r.get("posts")))).append(',')
              .append(csv(str(r.get("roles")))).append(',')
              .append(csv(str(r.get("business_duties")))).append(',')
              .append(csv(str(r.get("emp_status")))).append(',')
              .append(csv(str(r.get("card_no")))).append("\r\n");
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("fileName", "人员信息.csv");
        data.put("content", base64);
        data.put("count", rows.size());
        return R.ok(data);
    }

    /** 列表/导出共用 SELECT。 */
    private String selectSql() {
        return "SELECT u.user_id, u.user_id::text AS user_id_text, u.user_name AS job_no, u.nick_name AS name, u.gender, " +
            "  u.status AS account_status, u.phone_number, u.org_id, d.org_name, e.emp_status, e.emp_type, " +
            "  e.hire_date, e.card_no, e.is_dorm, e.dingtalk_account, " +
            "  (SELECT string_agg(p.post_name, '、') FROM sys_user_post up JOIN sys_post p ON up.post_id=p.post_id WHERE up.user_id=u.user_id) AS posts, " +
            "  (SELECT string_agg(r.role_name, '、') FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=u.user_id) AS roles, " +
            "  (SELECT string_agg(CASE d.duty_code WHEN 'SALES' THEN '业务员' WHEN 'FOLLOWER' THEN '跟单员' WHEN 'PURCHASER' THEN '采购员' WHEN 'PLANNER' THEN '计划员' WHEN 'WAREHOUSE' THEN '仓管员' WHEN 'QUALITY' THEN '质检员' END, '、' ORDER BY d.duty_code) FROM sys_user_business_duty d WHERE d.user_id=u.user_id) AS business_duties " +
            "FROM sys_user u " +
            "LEFT JOIN sys_person_ext e ON u.user_id=e.user_id " +
            "LEFT JOIN sys_org d ON u.org_id=d.org_id ";
    }

    /**
     * 数据范围过滤（统一双线模型，2026-10-26 定版）：
     * 档位：1=全部数据；2=自定义节点(sys_role_org 勾选部门/小组)；3=本部门及以下；5=仅本人；6=本小组及下级。
     * 生效语义（部门与小组统一为组织树节点）：
     *   员工可见 = (其部门 ∈ 勾选节点及下级) OR (其小组 ∈ 勾选节点及下级) OR (恒含自己)
     * - admin 豁免；任一启用角色=全部则放行；多角色并集。
     * - 本部门及以下=自定义{本人部门}；本小组及下级=自定义{本人小组}——同一套展开逻辑。
     * - 自己永远可见（列表里没自己反直觉）；无任何角色 → 仅自己可见。
     */
    private void applyDataScope(StringBuilder where, java.util.List<Object> args) {
        Long userId = LoginHelper.getUserId();
        if (userId == null || LoginHelper.isSuperAdmin(userId)) {
            return;
        }
        java.util.List<Long> roleIds = jdbcTemplate.queryForList(
            "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId);
        if (roleIds.isEmpty()) {
            where.append(" AND u.user_id=?");
            args.add(userId);
            return;
        }
        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < roleIds.size(); i++) { if (i > 0) ph.append(','); ph.append('?'); }
        java.util.List<String> scopes = jdbcTemplate.queryForList(
            "SELECT DISTINCT data_scope FROM sys_role WHERE status='0' AND role_id IN (" + ph + ")",
            String.class, roleIds.toArray());
        if (scopes.contains("1")) {
            return;
        }
        java.util.Set<Long> nodes = new java.util.LinkedHashSet<>();
        for (Long roleId : roleIds) {
            String scope = jdbcTemplate.query(
                "SELECT data_scope FROM sys_role WHERE role_id=? AND status='0'", rs -> rs.next() ? rs.getString(1) : null, roleId);
            if (scope == null) continue;
            switch (scope) {
                case "2" -> jdbcTemplate.queryForList(                       // 自定义组织节点
                    "SELECT org_id FROM sys_role_org WHERE role_id=?", Long.class, roleId).forEach(nodes::add);
                case "3" -> {                                                  // 本组织及以下
                    Long myOrg = jdbcTemplate.queryForObject(
                        "SELECT org_id FROM sys_user WHERE user_id=?", Long.class, userId);
                    if (myOrg != null) nodes.add(myOrg);
                }
                case "5" -> { }                                                // 仅本人：不产生节点，恒自己兜底
                default -> { }
            }
        }
        // 节点统一展开"自身+全部下级"（部门与小组同为 sys_org 节点，一套 ancestors 逻辑）
        java.util.Set<Long> expanded = expandOrgWithChildren(nodes);
        StringBuilder cond = new StringBuilder();
        if (!expanded.isEmpty()) {
            cond.append("u.org_id IN (");
            for (int i = 0; i < expanded.size(); i++) { if (i > 0) cond.append(','); cond.append('?'); }
            cond.append(')');
            args.addAll(expanded);
        }
        cond.append(cond.length() > 0 ? " OR u.user_id=?" : "u.user_id=?");
        args.add(userId);
        where.append(" AND (").append(cond).append(')');
    }

    /** 树节点筛选：所选组织节点及全部下级。展开 id 为库内 bigint，字面量拼接无注入。 */
    private void appendSubtreeFilter(StringBuilder where, Long orgId) {
        java.util.Set<Long> expanded = expandOrgWithChildren(java.util.Set.of(orgId));
        String ids = expanded.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(String.valueOf(orgId));
        where.append(" AND u.org_id IN (").append(ids).append(')');
    }

    /**
     * 组织树人数角标：每个节点统计本节点及下级的在册人员，口径与点选组织节点后的列表筛选一致。
     * Organization-tree badge counts include active employees in the node and descendants, matching the list filter.
     */
    @SaCheckPermission("system:person:query")
    @GetMapping("/org-counts")
    public R<Map<String, Long>> orgCounts() {
        // 人数角标与人员列表使用同一数据范围和系统账号过滤。
        StringBuilder countWhere = new StringBuilder(" WHERE u.del_flag='0' AND u.user_name<>'admin'");
        java.util.List<Object> countArgs = new java.util.ArrayList<>();
        applyDataScope(countWhere, countArgs);
        List<Map<String, Object>> people = jdbcTemplate.queryForList(
            "SELECT u.org_id FROM sys_user u" + countWhere, countArgs.toArray());
        List<Map<String, Object>> orgs = jdbcTemplate.queryForList(
            "SELECT org_id, ancestors FROM sys_org");
        java.util.Map<Long, String> ancMap = new java.util.HashMap<>();
        for (Map<String, Object> d : orgs) {
            ancMap.put(((Number) d.get("org_id")).longValue(),
                String.valueOf(d.get("ancestors") == null ? "" : d.get("ancestors")));
        }
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> d : orgs) {
            long nodeId = ((Number) d.get("org_id")).longValue();
            long cnt = 0;
            for (Map<String, Object> p : people) {
                Long pd = p.get("org_id") == null ? null : ((Number) p.get("org_id")).longValue();
                if (belongTo(pd, nodeId, ancMap)) {
                    cnt++;
                }
            }
            out.put(String.valueOf(nodeId), cnt);
        }
        return R.ok(out);
    }

    /** 人员归属节点：自身即节点，或其所在链（ancestors）包含节点。 */
    private boolean belongTo(Long personNodeId, long nodeId, java.util.Map<Long, String> ancMap) {
        if (personNodeId == null) {
            return false;
        }
        if (personNodeId == nodeId) {
            return true;
        }
        String anc = ancMap.get(personNodeId);
        return anc != null && anc.contains(String.valueOf(nodeId));
    }

    /** 部门集合展开：所选部门 + 祖先链包含任一所选部门的所有下级。 */
    private java.util.Set<Long> expandOrgWithChildren(java.util.Set<Long> roots) {
        java.util.Set<Long> out = new java.util.LinkedHashSet<>(roots);
        if (roots.isEmpty()) {
            return out;
        }
        List<Map<String, Object>> all = jdbcTemplate.queryForList("SELECT org_id, ancestors FROM sys_org");
        for (Map<String, Object> d : all) {
            String anc = String.valueOf(d.get("ancestors") == null ? "" : d.get("ancestors"));
            for (Long r : roots) {
                if (anc.contains(String.valueOf(r))) {
                    out.add(((Number) d.get("org_id")).longValue());
                    break;
                }
            }
        }
        return out;
    }

    /** 组装筛选条件（列表/导出共用）。 */
    private void applyFilters(StringBuilder where, java.util.List<Object> args,
                              String keyword, String empStatus, Long orgId, String gender, String posts, String roles, String businessDuty) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.user_name LIKE ? OR u.nick_name LIKE ? OR u.phone_number LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw); args.add(kw); args.add(kw);
        }
        if (empStatus != null && !empStatus.isBlank()) { where.append(" AND e.emp_status=?"); args.add(empStatus); }
        if (orgId != null) { where.append(" AND u.org_id=?"); args.add(orgId); }
        if (gender != null && !gender.isBlank()) { where.append(" AND u.gender=?"); args.add(gender); }
        if (posts != null && !posts.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_post up JOIN sys_post p ON up.post_id=p.post_id WHERE up.user_id=u.user_id AND p.post_name LIKE ?)");
            args.add("%" + posts.trim() + "%");
        }
        if (roles != null && !roles.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=u.user_id AND r.role_name LIKE ?)");
            args.add("%" + roles.trim() + "%");
        }
        if (businessDuty != null && !businessDuty.isBlank() && BUSINESS_DUTIES.containsKey(businessDuty)) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_business_duty bd WHERE bd.user_id=u.user_id AND bd.duty_code=?)");
            args.add(businessDuty);
        }
    }

    /** 排序白名单（防注入）。 */
    private String safeOrderBy(String orderBy) {
        java.util.Map<String, String> allow = java.util.Map.of(
            "job_no", "u.user_name", "name", "u.nick_name", "org", "d.org_name",
            "status", "e.emp_status", "card", "e.card_no", "hire", "e.hire_date");
        if (orderBy == null || orderBy.isBlank()) return "u.user_id DESC";
        String[] p = orderBy.split(":");
        String col = allow.get(p[0].trim());
        if (col == null) return "u.user_id DESC";
        String dir = (p.length > 1 && "asc".equalsIgnoreCase(p[1].trim())) ? "ASC" : "DESC";
        return col + " " + dir;
    }

    /** CSV 字段转义。 */
    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 人员详情（含扩展表全字段 + 岗位/角色ID列表，敏感字段给全值供编辑）。
     */
    @SaCheckPermission("system:person:query")
    @GetMapping("/{userId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long userId) {
        // 详情不能绕过列表的数据范围，密码散列也不应返回给前端。
        StringBuilder detailWhere = new StringBuilder(" WHERE u.user_id=?");
        java.util.List<Object> detailArgs = new java.util.ArrayList<>();
        detailArgs.add(userId);
        applyDataScope(detailWhere, detailArgs);
        List<Map<String, Object>> matches = jdbcTemplate.queryForList(
            "SELECT u.*, e.* FROM sys_user u LEFT JOIN sys_person_ext e ON u.user_id=e.user_id" + detailWhere, detailArgs.toArray());
        if (matches.isEmpty()) return R.fail("人员不存在或无权查看");
        Map<String, Object> row = matches.get(0);
        row.remove("password");
        List<Long> postIds = jdbcTemplate.queryForList(
            "SELECT post_id FROM sys_user_post WHERE user_id=?", Long.class, userId);
        List<Long> roleIds = jdbcTemplate.queryForList(
            "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId);
        row.put("postIds", postIds);
        row.put("roleIds", roleIds);
        row.put("businessDuties", jdbcTemplate.queryForList(
            "SELECT duty_code FROM sys_user_business_duty WHERE user_id=? ORDER BY duty_code", String.class, userId));
        return R.ok(row);
    }

    /**
     * 新增人员（建人员=建登录账号）：写 sys_user + sys_person_ext + 岗位/角色关系。
     * body: jobNo(工号6位)、name、gender、phoneNumber、orgId、password(可空=默认123456)、
     *   postIds[]、roleIds[]、ext{...}。
     */
    @SaCheckPermission("system:person:add")
    @Log(title = "人员信息", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String jobNo = str(body.get("jobNo"));
        if (jobNo == null || jobNo.length() != 6 || !jobNo.matches("\\d{6}")) {
            return R.fail("工号必须是6位数字");
        }
        // 工号唯一：只在"未删除"里查重（软删的不占工号，可复用给新人）
        Long exists = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user WHERE user_name=? AND del_flag='0'", Long.class, jobNo);
        if (exists != null && exists > 0) {
            return R.fail("工号已存在：" + jobNo);
        }
        Long userId = System.currentTimeMillis();
        String password = body.get("password") == null || str(body.get("password")).isBlank()
            ? DEFAULT_PASSWORD : str(body.get("password"));
        jdbcTemplate.update(
            "INSERT INTO sys_user (user_id, org_id, user_name, nick_name, user_type, email, phone_number, gender, password, status, del_flag, create_time, remark) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,'0',now(),?)",
            userId, orgIdOrNull(body.get("orgId")), jobNo, str(body.get("name")), "00",
            str(body.get("email")), str(body.get("phoneNumber")), str(body.get("gender")),
            BCrypt.hashpw(password), str(body.getOrDefault("status", "0")), str(body.get("remark")));
        saveExt(userId, body);
        saveDingTalkAccount(userId, body);
        saveRelations(userId, body);
        recordPersonAudit(userId, "新增人员", java.util.Map.of(), personAuditSnapshot(userId), false);
        return R.ok();
    }

    /**
     * 修改人员及其档案关系；工号和密码不在此接口修改，工号变更使用专用接口。
     * Update an employee and related profile links; job number and password changes use dedicated endpoints.
     */
    @SaCheckPermission("system:person:edit")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(str(body.get("userId")));
        var auditRows = jdbcTemplate.queryForList(
            "SELECT u.nick_name, e.emp_status FROM sys_user u LEFT JOIN sys_person_ext e ON e.user_id=u.user_id WHERE u.user_id=? FOR UPDATE OF u", userId);
        if (auditRows.isEmpty()) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        String pwd = str(body.get("password"));
        // 密码：填了就重置（BCrypt），不填不动
        if (pwd != null && !pwd.isBlank()) {
            jdbcTemplate.update(
                "UPDATE sys_user SET org_id=?, nick_name=?, email=?, phone_number=?, gender=?, status=?, remark=?, password=?, update_time=now() WHERE user_id=?",
                orgIdOrNull(body.get("orgId")), str(body.get("name")), str(body.get("email")),
                str(body.get("phoneNumber")), str(body.get("gender")),
                str(body.getOrDefault("status", "0")), str(body.get("remark")),
                BCrypt.hashpw(pwd), userId);
        } else {
            jdbcTemplate.update(
                "UPDATE sys_user SET org_id=?, nick_name=?, email=?, phone_number=?, gender=?, status=?, remark=?, update_time=now() WHERE user_id=?",
                orgIdOrNull(body.get("orgId")), str(body.get("name")), str(body.get("email")),
                str(body.get("phoneNumber")), str(body.get("gender")),
                str(body.getOrDefault("status", "0")), str(body.get("remark")), userId);
        }
        saveExt(userId, body);
        saveDingTalkAccount(userId, body);
        saveRelations(userId, body);
        recordPersonAudit(userId, "修改人员", before, personAuditSnapshot(userId), pwd != null && !pwd.isBlank());
        return R.ok();
    }

    /**
     * 将人员登录密码重置为系统默认初始密码，并写入人员变更审计。
     * Reset the employee login password to the default initial password and record the personnel audit event.
     */
    @SaCheckPermission("system:person:resetPwd")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{userId}/reset-password")
    public R<Void> resetPassword(@PathVariable Long userId) {
        if (!lockPersonForAudit(userId)) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        jdbcTemplate.update("UPDATE sys_user SET password=?, update_time=now() WHERE user_id=?",
            BCrypt.hashpw(DEFAULT_PASSWORD), userId);
        recordPersonAudit(userId, "重置密码", before, personAuditSnapshot(userId), true);
        return R.ok();
    }

    /**
     * 删除人员（软删）：置 del_flag='1' + status='1'停用（不能再登录）。
     * 人员是登录账号，软删保留历史关联、可恢复，不做物理删。
     */
    @SaCheckPermission("system:person:remove")
    @Log(title = "人员信息", businessType = BusinessType.DELETE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable Long userId) {
        // 不允许删超管（user_id 由 .env 配置的超管身份载体，删了登录会出问题）
        if (LoginHelper.isSuperAdmin(userId)) {
            return R.fail("超级管理员账号不允许删除");
        }
        if (!lockPersonForAudit(userId)) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        jdbcTemplate.update(
            "UPDATE sys_user SET del_flag='1', status='1', update_time=now() WHERE user_id=?", userId);
        recordPersonAudit(userId, "删除人员", before, personAuditSnapshot(userId), false);
        return R.ok();
    }

    /**
     * 恢复已删除人员：del_flag 置回 '0'（status 恢复为在职，可重新登录/使用）。
     * 已删人员工号不占用，恢复前需确认该工号未被新人占用。
     */
    @SaCheckPermission("system:person:edit")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{userId}/restore")
    public R<Void> restore(@PathVariable Long userId) {
        if (!lockPersonForAudit(userId)) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT user_name, del_flag FROM sys_user WHERE user_id=?", userId);
        if (!"1".equals(str(row.get("del_flag")))) {
            return R.fail("该人员未被删除，无需恢复");
        }
        String jobNo = str(row.get("user_name"));
        // 工号若已被新人占用（未删除的同工号），不能恢复
        Long conflict = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user WHERE user_name=? AND del_flag='0' AND user_id<>?",
            Long.class, jobNo, userId);
        if (conflict != null && conflict > 0) {
            return R.fail("工号 " + jobNo + " 已被其他在职人员占用，无法恢复");
        }
        jdbcTemplate.update(
            "UPDATE sys_user SET del_flag='0', status='0', update_time=now() WHERE user_id=?", userId);
        recordPersonAudit(userId, "恢复人员", before, personAuditSnapshot(userId), false);
        return R.ok();
    }

    /**
     * 彻底删除人员（物理删除）：仅限已软删（del_flag='1'）的人员。
     * 清掉账号与全部关联（角色/岗位/工作台配置/菜单配置/待办/社会登录/扩展表），
     * 释放"角色已分配"占用——人员彻底删除后，其占用的角色即可在角色管理里删除。
     * 超管账号禁止；审计留痕（各表 create_by）与登录日志不清理。
     */
    @SaCheckPermission("system:person:remove")
    @Log(title = "人员信息", businessType = BusinessType.DELETE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{userId}/permanent")
    public R<Void> removePermanent(@PathVariable Long userId) {
        if (LoginHelper.isSuperAdmin(userId)) {
            return R.fail("超级管理员账号不允许彻底删除");
        }
        if (!lockPersonForAudit(userId)) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT user_name, del_flag FROM sys_user WHERE user_id=?", userId);
        } catch (Exception e) {
            return R.fail("人员不存在");
        }
        if (!"1".equals(str(row.get("del_flag")))) {
            return R.fail("该人员尚未删除，请先删除（软删）再彻底删除");
        }
        // 先清关联行（含角色占用），再删本体
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_business_duty WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_menu_config WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_workbench WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_todo WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_social WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_person_ext WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE user_id=?", userId);
        // 踢掉该用户仍在线的会话（权限/身份已失效）
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(java.util.List.of(userId)));
        recordPersonAudit(userId, "永久删除人员", before, java.util.Map.of(), false);
        return R.ok();
    }

    // ===== 内部 =====

    /** 保存扩展表（upsert）。 */
    private boolean lockPersonForAudit(Long userId) {
        return !jdbcTemplate.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE",Long.class,userId).isEmpty();
    }

    private void recordPersonAudit(Long userId,String action,Map<String,Object> before,
                                   Map<String,Object> after,boolean passwordReset) {
        var changes = new java.util.LinkedHashMap<String,Object>();
        var fields = new java.util.LinkedHashSet<String>(before.keySet());
        fields.addAll(after.keySet());
        var privateFields = java.util.Set.of("电话","邮箱","证件号码","银行卡号","卡号","住址","社保金额","社保补贴");
        for (String field : fields) {
            Object oldValue = before.get(field);
            Object newValue = after.get(field);
            if (java.util.Objects.equals(oldValue,newValue)) continue;
            changes.put(field,privateFields.contains(field)
                ? java.util.Arrays.asList(oldValue == null ? null : "已填写",newValue == null ? null : "已变更")
                : java.util.Arrays.asList(oldValue,newValue));
        }
        if (passwordReset) changes.put("登录密码",java.util.Arrays.asList(null,"已重置"));
        Object name = after.isEmpty() ? before.get("姓名") : after.get("姓名");
        org.dromara.common.core.utils.ServletUtils.getRequest().setAttribute("zhiyun.audit",
            java.util.Map.of("id",userId.toString(),"name",java.util.Objects.toString(name,""),"action",action,"changes",changes));
    }

    /** 读取人员可审计字段快照，并以安全占位值记录敏感字段变化。 / Capture auditable personnel fields while representing sensitive changes without exposing their values. */
    private Map<String,Object> personAuditSnapshot(Long userId) {
        var snapshot = jdbcTemplate.queryForMap("""
            SELECT u.nick_name AS "姓名",u.org_id::text AS "组织 ID",u.email AS "邮箱",
                   u.user_name AS "工号",u.del_flag AS "删除标记",u.phone_number AS "电话",u.gender AS "性别",u.status AS "账号状态",u.remark AS "备注",
                   e.dingtalk_account AS "钉钉号",e.marital_status AS "婚姻状况",e.cert_type AS "证件类型",e.cert_no AS "证件号码",
                   e.nation AS "民族",e.education AS "学历",e.birth_date::text AS "出生日期",
                   e.graduate_school AS "毕业学校",e.native_place AS "籍贯",e.address AS "住址",
                   e.cert_expire_date::text AS "证件有效期",e.hire_date::text AS "入职日期",
                   e.group_name AS "小组名称",e.group_id::text AS "小组 ID",e.emp_type AS "用工类型",
                   e.leave_date::text AS "离职日期",e.card_no AS "卡号",e.emp_status AS "在职状态",
                   e.bank_card_no AS "银行卡号",e.sign_entity AS "签约主体",e.contract_years AS "合同年限",
                   e.contract_start::text AS "合同开始日期",e.contract_end::text AS "合同结束日期",
                   e.has_social AS "是否参保",e.social_amount AS "社保金额",e.social_subsidy AS "社保补贴",
                   e.has_commercial AS "是否有商业保险",e.commercial_type AS "商业保险类型",
                   e.social_check_date::text AS "社保核查日期",e.is_dorm AS "是否住宿"
            FROM sys_user u LEFT JOIN sys_person_ext e ON e.user_id=u.user_id WHERE u.user_id=?
            """,userId);
        snapshot.put("岗位 ID",jdbcTemplate.queryForList(
            "SELECT post_id::text FROM sys_user_post WHERE user_id=? ORDER BY post_id",String.class,userId));
        snapshot.put("角色 ID",jdbcTemplate.queryForList(
            "SELECT role_id::text FROM sys_user_role WHERE user_id=? ORDER BY role_id",String.class,userId));
        snapshot.put("业务职责",jdbcTemplate.queryForList(
            "SELECT CASE duty_code WHEN 'SALES' THEN '业务员' WHEN 'FOLLOWER' THEN '跟单员' WHEN 'PURCHASER' THEN '采购员' WHEN 'PLANNER' THEN '计划员' WHEN 'WAREHOUSE' THEN '仓管员' WHEN 'QUALITY' THEN '质检员' END FROM sys_user_business_duty WHERE user_id=? ORDER BY duty_code",String.class,userId));
        return snapshot;
    }

    /** 保存人员扩展档案，并将小组 ID 与组织节点名称保持一致。 / Persist employee extensions and keep the group ID aligned with its organization-node name. */
    private void saveExt(Long userId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = body.get("ext") instanceof Map ? (Map<String, Object>) body.get("ext") : java.util.Map.of();
        // 小组：优先 group_id 关联（组织树 org_category=小组）；带 group_id 时同步冗余 group_name（取小组节点名，找不到则用传入文本）
        Object gidRaw = ext.get("groupId");
        // 前端 groupId 以字符串传输（防 bigint 精度丢失），Number 判断会漏——统一走 orgIdOrNull
        Long groupId = orgIdOrNull(gidRaw);
        String groupName = str(ext.get("groupName"));
        if (groupId != null) {
            String node = jdbcTemplate.query(
                "SELECT org_name FROM sys_org WHERE org_id=? AND org_category='小组'", rs -> rs.next() ? rs.getString(1) : null, groupId);
            if (node != null) groupName = node;
        }
        jdbcTemplate.update(
            "INSERT INTO sys_person_ext (user_id, marital_status, cert_type, cert_no, nation, education, birth_date, " +
            "  graduate_school, native_place, address, cert_expire_date, hire_date, group_name, group_id, emp_type, leave_date, " +
            "  card_no, emp_status, bank_card_no, sign_entity, contract_years, contract_start, contract_end, " +
            "  has_social, social_amount, social_subsidy, has_commercial, commercial_type, social_check_date, is_dorm, update_time) " +
            "VALUES (?,?,?,?,?,?,?::date,?,?,?,?::date,?::date,?,?,?,?::date,?,?,?,?,?::numeric,?::date,?::date,?,?::numeric,?::numeric,?,?,?::date,?,now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET " +
            "  marital_status=EXCLUDED.marital_status, cert_type=EXCLUDED.cert_type, cert_no=EXCLUDED.cert_no, " +
            "  nation=EXCLUDED.nation, education=EXCLUDED.education, birth_date=EXCLUDED.birth_date, " +
            "  graduate_school=EXCLUDED.graduate_school, native_place=EXCLUDED.native_place, address=EXCLUDED.address, " +
            "  cert_expire_date=EXCLUDED.cert_expire_date, hire_date=EXCLUDED.hire_date, group_name=EXCLUDED.group_name, " +
            "  group_id=EXCLUDED.group_id, " +
            "  emp_type=EXCLUDED.emp_type, leave_date=EXCLUDED.leave_date, card_no=EXCLUDED.card_no, " +
            "  emp_status=EXCLUDED.emp_status, bank_card_no=EXCLUDED.bank_card_no, sign_entity=EXCLUDED.sign_entity, " +
            "  contract_years=EXCLUDED.contract_years, contract_start=EXCLUDED.contract_start, contract_end=EXCLUDED.contract_end, " +
            "  has_social=EXCLUDED.has_social, social_amount=EXCLUDED.social_amount, social_subsidy=EXCLUDED.social_subsidy, " +
            "  has_commercial=EXCLUDED.has_commercial, commercial_type=EXCLUDED.commercial_type, " +
            "  social_check_date=EXCLUDED.social_check_date, is_dorm=EXCLUDED.is_dorm, update_time=now()",
            userId, ext.get("maritalStatus"), ext.get("certType"), ext.get("certNo"), ext.get("nation"),
            ext.get("education"), dateOrNull(ext.get("birthDate")), ext.get("graduateSchool"), ext.get("nativePlace"),
            ext.get("address"), dateOrNull(ext.get("certExpireDate")), dateOrNull(ext.get("hireDate")), groupName, groupId,
            ext.get("empType"), dateOrNull(ext.get("leaveDate")), ext.get("cardNo"),
            str(ext.getOrDefault("empStatus", "试用")), ext.get("bankCardNo"), ext.get("signEntity"),
            ext.get("contractYears"), dateOrNull(ext.get("contractStart")), dateOrNull(ext.get("contractEnd")),
            ext.getOrDefault("hasSocial", "0"), ext.get("socialAmount"), ext.get("socialSubsidy"),
            ext.getOrDefault("hasCommercial", "0"), ext.get("commercialType"), dateOrNull(ext.get("socialCheckDate")),
            ext.getOrDefault("isDorm", "0"));
    }

    /** 保存用户手工登记的钉钉号；旧客户端未传此字段时保留已有值。 / Persist a manually entered DingTalk account while preserving it when older clients omit the field. */
    private void saveDingTalkAccount(Long userId, Map<String, Object> body) {
        if (!(body.get("ext") instanceof Map<?, ?> ext) || !ext.containsKey("dingTalkAccount")) return;
        Object raw = ext.get("dingTalkAccount");
        String value = raw == null ? null : raw.toString().trim();
        if (value != null && value.length() > 128) throw new ServiceException("钉钉号不能超过128个字符");
        jdbcTemplate.update("UPDATE sys_person_ext SET dingtalk_account=? WHERE user_id=?", value == null || value.isEmpty() ? null : value, userId);
    }

    /** 保存岗位、角色和业务职责关联；先清理旧集合，再整体写入防止重复关系。 / Replace post, role and business-duty links as complete sets to prevent stale or duplicate relations. */
    private void saveRelations(Long userId, Map<String, Object> body) {
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        // 职责也是人员的多值关联，必须先清理旧值再重建，避免编辑未变更职责时触发联合主键冲突。
        // Business duties are multi-value person relations; clear them before rebuilding to avoid duplicate keys.
        jdbcTemplate.update("DELETE FROM sys_user_business_duty WHERE user_id=?", userId);
        if (body.get("postIds") instanceof List<?> posts) {
            for (Object p : posts) {
                jdbcTemplate.update("INSERT INTO sys_user_post (user_id, post_id) VALUES (?,?)", userId, Long.valueOf(p.toString()));
            }
        }
        if (body.get("roleIds") instanceof List<?> roles) {
            for (Object r : roles) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", userId, Long.valueOf(r.toString()));
            }
        }
        Object rawDuties = body.get("businessDuties");
        if (rawDuties != null && !(rawDuties instanceof List<?>)) throw new ServiceException("业务职责格式无效");
        if (rawDuties instanceof List<?> duties) {
            Set<String> unique = new HashSet<>();
            for (Object duty : duties) {
                String code = duty == null ? "" : duty.toString();
                if (!BUSINESS_DUTIES.containsKey(code) || !unique.add(code)) throw new ServiceException("业务职责无效或重复");
                jdbcTemplate.update("INSERT INTO sys_user_business_duty(user_id,duty_code) VALUES (?,?)", userId, code);
            }
        }
    }

    /** 手机号脱敏：138****8000。 */
    private String mask(String phone) {
        // 供 SQL mask() 用不到，占位（列表 SQL 里直接调用本方法不可行，故 SQL 侧不调用）
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    /** 部门ID：前端 bigint 以字符串传输（防 JS 精度丢失），落库前转 Long；空→null。 */
    private Long orgIdOrNull(Object o) {
        String s = str(o);
        return (s == null || s.isBlank()) ? null : Long.valueOf(s);
    }

    /** 日期字段：空串/null 一律转 null（PG date 不接受 ''），非空转字符串。 */
    private String dateOrNull(Object o) {
        String s = str(o);
        return (s == null || s.isBlank()) ? null : s;
    }
}
