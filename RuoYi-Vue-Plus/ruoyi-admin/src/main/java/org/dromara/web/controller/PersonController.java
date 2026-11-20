/*
 * 文件用途：为织云 ERP 的人员档案与分类权限接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的人员档案与分类权限接口提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
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
 * 维护人员登录身份、任职信息、岗位与角色，并提供职责资格及分类数据范围管理。
 * Personnel identity, employment data, posts, roles, business-duty eligibility, and category data scope.
 *
 * 登录凭证由系统账号策略生成和验证；本类不应将默认密码或敏感凭证写入注释、日志或返回值。
 * Credentials follow the system account policy; never expose default passwords or sensitive credentials in comments, logs, or responses.
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/person")
public class PersonController {

    private final JdbcTemplate jdbcTemplate;
    private final ISysRoleService roleService;
    private final ISysUserService userService;

    /** 业务职责仅标识单据责任人资格，不等同于岗位、系统角色或功能权限。 / Business duties identify document-owner eligibility, not posts, roles, or permissions. */
    private static final Map<String, String> BUSINESS_DUTIES = Map.of(
        "SALES", "业务员", "FOLLOWER", "跟单员", "PURCHASER", "采购员",
        "PLANNER", "计划员", "WAREHOUSE", "仓管员", "QUALITY", "质检员");

    /** 返回人员维护界面可选的业务职责枚举，不授予任何单据操作权限。 / Return selectable business duties without granting document permissions. */
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
    @SaCheckPermission("system:person:categoryScope")
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
    @SaCheckPermission("system:person:categoryScope")
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

    /** 在人员数据范围内维护分类授权，并禁止非超管修改本人的授权。 / Enforce employee data scope and prevent self-granting by non-admin users. */
    private String checkCategoryScopeTarget(Long userId, boolean lock) {
        Long actor = LoginHelper.getUserId();
        if (actor == null) throw new ServiceException("请先登录");
        if (lock && actor.equals(userId) && !LoginHelper.isSuperAdmin(actor))
            throw new ServiceException("不能修改本人的分类权限，请由其他授权人员维护");
        StringBuilder where = new StringBuilder(" WHERE u.user_id=? AND u.del_flag='0'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        applyDataScope(where, args);
        var names = jdbcTemplate.queryForList(
            "SELECT u.nick_name FROM sys_user u" + where + (lock ? " FOR UPDATE OF u" : ""),
            String.class, args.toArray());
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
     * 人员信息的角色数据范围过滤：
     * 档位：1=全部数据；2=自定义组织节点(sys_role_org)；3=本组织及以下；5=仅本人。
     * 生效语义（部门与小组统一为组织树节点）：员工可见 = (其组织节点 ∈ 所选节点及下级) OR (恒含自己)
     * - admin 豁免；任一启用角色=全部则放行；多角色并集。
     * - 本组织及以下按人员当前所属组织节点展开，与部门、小组无关；二者是同一组织树模型。
     * - 自己永远可见（列表里没自己反直觉）；无任何角色 → 仅自己可见。
     *
     * Apply the combined role data scope: admin and any active all-data role bypass filtering,
     * custom scopes are unioned, descendants are included, and the current user always remains visible.
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

    /**
     * 为组织树筛选添加所选节点及全部下级；展开 ID 来自数据库 bigint，不含用户文本。
     * Add the selected organization node and all descendants to the tree filter; IDs are trusted database bigints, not user text.
     */
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

    /**
     * 判断人员是否属于指定组织节点：人员所在节点相同，或祖先链中存在该节点。
     * Check whether a person belongs to a node by matching the person's node or an exact ID in its ancestor chain.
     */
    private static boolean belongTo(Long personNodeId, long nodeId, java.util.Map<Long, String> ancMap) {
        if (personNodeId == null) {
            return false;
        }
        if (personNodeId == nodeId) {
            return true;
        }
        String anc = ancMap.get(personNodeId);
        return containsAncestorId(anc, nodeId);
    }

    /**
     * 组织节点集合展开：保留所选节点并加入祖先链中包含所选节点的所有下级。
     * Expand selected organization nodes to include every descendant whose ancestor chain contains a selected node.
     */
    private java.util.Set<Long> expandOrgWithChildren(java.util.Set<Long> roots) {
        java.util.Set<Long> out = new java.util.LinkedHashSet<>(roots);
        if (roots.isEmpty()) {
            return out;
        }
        List<Map<String, Object>> all = jdbcTemplate.queryForList("SELECT org_id, ancestors FROM sys_org");
        for (Map<String, Object> d : all) {
            String anc = String.valueOf(d.get("ancestors") == null ? "" : d.get("ancestors"));
            for (Long r : roots) {
                if (containsAncestorId(anc, r)) {
                    out.add(((Number) d.get("org_id")).longValue());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * 按逗号分隔的祖先 ID 列表做整项匹配，防止组织 ID 的数字前缀互相误命中。
     * Match a comma-separated ancestor list by complete token to prevent IDs sharing a numeric prefix from matching.
     */
    private static boolean containsAncestorId(String ancestors, long candidateId) {
        if (ancestors == null || ancestors.isBlank()) return false;
        String expected = Long.toString(candidateId);
        for (String ancestorId : ancestors.split(",")) {
            if (expected.equals(ancestorId.trim())) return true;
        }
        return false;
    }

    /**
     * 组装人员列表与导出共用的 SQL 筛选条件和参数。
     * Build the shared SQL filters and bind parameters used by personnel listing and export.
     */
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

    /**
     * 将前端排序字段映射到固定 SQL 白名单，避免将用户输入拼入 SQL 标识符。
     * Map client sort keys to a fixed SQL allowlist so user input cannot become an SQL identifier.
     */
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

    /**
     * 按 CSV 规则转义字段内容，避免逗号、引号和换行破坏导出列结构。
     * Escape a CSV field so commas, quotes and line breaks do not corrupt exported columns.
     */
    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 读取当前人员数据范围内的完整档案及岗位、角色、业务职责；移除密码散列，保留授权编辑所需的档案字段。
     * Read a full in-scope personnel profile with post, role and duty links; omit password hashes while retaining fields needed by authorized editors.
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
     * 新增人员账号和档案关系；未提供密码时写入随机散列值，必须由授权人员设置新密码后才能登录。
     * Create the account and profile relations; an omitted password becomes a random hash and requires an authorized reset before login.
     * body 包含 jobNo、name、gender、phoneNumber、orgId、可选 password、postIds、roleIds 和 ext。
     * The body contains jobNo, name, gender, phoneNumber, orgId, optional password, postIds, roleIds, and ext.
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
        // 新增人员可能并发，使用统一雪花 ID，避免毫秒时间戳主键冲突。
        // Use the shared Snowflake generator to avoid collisions during concurrent creation.
        Long userId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        // 未指定密码时生成仅供散列的随机值，账号须经授权设置密码后才能使用。
        // Hash a random value when no password is supplied; an authorized reset is required before use.
        String password = body.get("password") == null || str(body.get("password")).isBlank()
            ? java.util.UUID.randomUUID().toString() : str(body.get("password"));
        validatePassword(password);
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
        StringBuilder editWhere = new StringBuilder(" WHERE u.user_id=? AND u.del_flag='0'");
        java.util.List<Object> editArgs = new java.util.ArrayList<>();
        editArgs.add(userId);
        applyDataScope(editWhere, editArgs);
        var auditRows = jdbcTemplate.queryForList(
            "SELECT u.nick_name, e.emp_status FROM sys_user u LEFT JOIN sys_person_ext e ON e.user_id=u.user_id" + editWhere + " FOR UPDATE OF u",
            editArgs.toArray());
        if (auditRows.isEmpty()) return R.fail("人员不存在、已删除或不在当前人员数据范围内");
        Map<String,Object> before = personAuditSnapshot(userId);
        // 档案编辑与账号改密分别授权，防止通用编辑入口绕过密码权限。
        // Keep profile editing and password changes behind separate permissions.
        if (body.containsKey("password")) return R.fail("请使用专用的重置密码功能");
        jdbcTemplate.update(
            "UPDATE sys_user SET org_id=?, nick_name=?, email=?, phone_number=?, gender=?, status=?, remark=?, update_time=now() WHERE user_id=?",
            orgIdOrNull(body.get("orgId")), str(body.get("name")), str(body.get("email")),
            str(body.get("phoneNumber")), str(body.get("gender")),
            str(body.getOrDefault("status", "0")), str(body.get("remark")), userId);
        saveExt(userId, body);
        saveDingTalkAccount(userId, body);
        saveRelations(userId, body);
        recordPersonAudit(userId, "修改人员", before, personAuditSnapshot(userId), false);
        return R.ok();
    }

    /**
     * 由持有重置权限的人员设置新密码，并写入人员变更审计。
     * Set a new employee password with the dedicated permission and record the personnel audit event.
     */
    @SaCheckPermission("system:person:resetPwd")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{userId}/reset-password")
    public R<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        String password = str(body.get("password"));
        validatePassword(password);
        if (LoginHelper.isSuperAdmin(userId) && !LoginHelper.isSuperAdmin()) {
            return R.fail("只有超级管理员可以重置该账号的密码");
        }
        if (!lockPersonForAudit(userId)) return R.fail("人员不存在");
        Map<String,Object> before = personAuditSnapshot(userId);
        jdbcTemplate.update("UPDATE sys_user SET password=?, update_time=now() WHERE user_id=?",
            BCrypt.hashpw(password), userId);
        recordPersonAudit(userId, "重置密码", before, personAuditSnapshot(userId), true);
        return R.ok();
    }

    /** 校验密码长度，避免 BCrypt 的字节上限截断长密码。 / Honor BCrypt’s byte limit without truncation. */
    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ServiceException("密码至少 8 个字符，且不得超过 72 字节");
        }
    }

    /**
     * 软删除人员账号并停用登录；保留人员及历史关联以支持追溯和恢复，不在此接口物理删除。
     * Soft-delete and disable login while retaining profile links for history and recovery; this endpoint never physically deletes the account.
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
     * 恢复软删除人员；仅当原工号未被其他在职人员占用时恢复账号状态。
     * Restore a soft-deleted employee only when the original job number is not used by another active account.
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
     * 永久删除仅允许已软删除人员；清理账号关联数据，超管账号禁止删除，历史审计与登录日志保留。
     * Permanently delete only a soft-deleted employee and its account links; protect super admins and retain audit and login logs.
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

    /** 锁定人员记录并应用人员数据范围，避免越权变更及审计快照并发错位。 / Lock the in-scope employee row to prevent unauthorized writes and mismatched audit snapshots. */
    private boolean lockPersonForAudit(Long userId) {
        StringBuilder where = new StringBuilder(" WHERE u.user_id=?");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        applyDataScope(where, args);
        return !jdbcTemplate.queryForList("SELECT u.user_id FROM sys_user u" + where + " FOR UPDATE OF u",
            Long.class, args.toArray()).isEmpty();
    }

    /** 对比前后人员快照并脱敏审计；凭证正文及敏感字段原值不进入审计记录。 / Compare employee snapshots and redact sensitive fields before auditing; never store credentials or raw private values. */
    private void recordPersonAudit(Long userId,String action,Map<String,Object> before,
                                   Map<String,Object> after,boolean passwordReset) {
        var changes = new java.util.LinkedHashMap<String,Object>();
        var fields = new java.util.LinkedHashSet<String>(before.keySet());
        fields.addAll(after.keySet());
        var privateFields = java.util.Set.of("电话","邮箱","证件号码","银行卡号","卡号","住址","社保金额","社保补贴","钉钉号","出生日期");
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
        saveRoleChanges(userId, body.get("roleIds"));
        // 职责也是人员的多值关联，必须先清理旧值再重建，避免编辑未变更职责时触发联合主键冲突。
        // Business duties are multi-value person relations; clear them before rebuilding to avoid duplicate keys.
        jdbcTemplate.update("DELETE FROM sys_user_business_duty WHERE user_id=?", userId);
        if (body.get("postIds") instanceof List<?> posts) {
            for (Object p : posts) {
                jdbcTemplate.update("INSERT INTO sys_user_post (user_id, post_id) VALUES (?,?)", userId, Long.valueOf(p.toString()));
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

    /**
     * 人员档案权限只允许维护资料。角色增加或撤销复用系统用户授权权限及角色数据范围。
     * Profile permissions cover employee data. Role changes reuse user authorization and role data scope.
     */
    private void saveRoleChanges(Long userId, Object rawRoles) {
        if (rawRoles == null) return;
        if (!(rawRoles instanceof List<?> roles)) throw new ServiceException("角色列表格式无效");
        Set<Long> requested = new HashSet<>();
        for (Object role : roles) {
            if (role == null || !requested.add(Long.valueOf(role.toString()))) {
                throw new ServiceException("角色不能为空或重复");
            }
        }
        Set<Long> current = new HashSet<>(jdbcTemplate.queryForList(
            "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId));
        // 页面和 MCP 都可能回传未修改的角色，保持原样时无需额外授权。
        // Forms and MCP may echo existing roles; unchanged assignments require no extra permission.
        if (current.equals(requested)) return;
        StpUtil.checkPermission("system:user:edit");
        if (LoginHelper.isSuperAdmin(userId) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("只有超级管理员可以调整该账号的角色");
        }
        if (!LoginHelper.isSuperAdmin(userId) && requested.contains(
                org.dromara.common.core.constant.SystemConstants.SUPER_ADMIN_ROLE_ID)) {
            throw new ServiceException("普通账号不能分配超级管理员角色");
        }
        Set<Long> removed = new HashSet<>(current);
        removed.removeAll(requested);
        roleService.checkRoleDataScope(removed);
        if (requested.isEmpty()) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        } else {
            userService.insertUserAuth(userId, requested.toArray(Long[]::new));
        }
    }

    /** 列表与导出统一脱敏手机号，短号也不返回完整值。 / Mask phone numbers consistently in lists and exports, including short values. */
    private String mask(String phone) {
        if (phone == null) return null;
        int length = phone.length();
        if (length <= 2) return "*".repeat(length);
        if (length <= 7) return "*".repeat(length - 2) + phone.substring(length - 2);
        return phone.substring(0, 3) + "*".repeat(length - 7) + phone.substring(length - 4);
    }

    /**
     * 将可空请求值安全转换为字符串，不对数字 ID 做精度转换。
     * Convert a nullable request value to text without numerically coercing bigint identifiers.
     */
    private String str(Object o) { return o == null ? null : o.toString(); }

    /**
     * 解析可空的组织节点 ID；前端以字符串传输 bigint，避免 JavaScript 精度丢失。
     * Parse an optional organization-node ID sent as a string to avoid JavaScript bigint precision loss.
     */
    private Long orgIdOrNull(Object o) {
        String s = str(o);
        if (s == null || s.isBlank()) return null;
        try {
            long id = Long.parseLong(s.trim());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Convert malformed IDs into a clear business error instead of leaking a parsing exception.
        }
        throw new org.dromara.common.core.exception.ServiceException("组织机构ID格式无效");
    }

    /**
     * 将空日期值统一转换为 null，避免 PostgreSQL date 列收到空字符串。
     * Normalize blank date values to null so PostgreSQL date columns never receive an empty string.
     */
    private String dateOrNull(Object o) {
        String s = str(o);
        return (s == null || s.isBlank()) ? null : s;
    }
}
