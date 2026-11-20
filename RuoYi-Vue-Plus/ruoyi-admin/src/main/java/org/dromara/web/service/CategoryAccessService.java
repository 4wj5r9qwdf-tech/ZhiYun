package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 供业务档案共用的人员分类范围。 / Shared employee taxonomy scope for business catalogs. */
@Service
@RequiredArgsConstructor
public class CategoryAccessService {
    private final JdbcTemplate jdbc;

    /** 返回当前人员的可用分类和只用于定位的祖先。 / Return authorized choices with navigation ancestors. */
    public Map<String, Object> options() {
        List<Map<String,Object>> all = jdbc.queryForList("""
            SELECT s.scheme_id::text AS "schemeId",s.scheme_code AS "schemeCode",s.scheme_name AS "schemeName",s.status AS "schemeStatus",
                   c.category_id::text AS id,c.parent_id::text AS "parentId",c.category_code AS code,c.category_name AS name,c.status
            FROM base_category_scheme s JOIN base_category c ON c.scheme_id=s.scheme_id
            WHERE s.del_flag='0' AND c.del_flag='0' ORDER BY s.sort_order,s.scheme_id,c.sort_order,c.category_id
            """);
        Scope scope = scope();
        if (scope.mode.equals("NONE")) return Map.of("rows",List.of(),"restricted",true);
        Set<String> allowed = new HashSet<>();
        if (scope.mode.equals("ALL")) all.forEach(row->allowed.add(String.valueOf(row.get("id"))));
        else {
            // 类型授权覆盖该类型全部节点；节点授权本身只覆盖当前节点，递归扩展仅来自 include_children。
            // Scheme grants cover every node; category grants cover themselves and expand only when include_children is true.
            for (Map<String,Object> row : all) if (scope.schemeIds.contains(String.valueOf(row.get("schemeId"))) || scope.categoryIds.contains(String.valueOf(row.get("id")))) allowed.add(String.valueOf(row.get("id")));
            if (!scope.descendantRoots.isEmpty()) {
                Set<String> parents = new HashSet<>(scope.descendantRoots);
                boolean changed;
                do {
                    changed = false;
                    for (Map<String,Object> row : all) {
                        if (!parents.contains(String.valueOf(row.get("parentId")))) continue;
                        String childId = String.valueOf(row.get("id"));
                        allowed.add(childId);
                        // 即使子节点已被单独授权，也必须继续把它加入遍历前沿，才能完整展开更深层级。
                        // Continue traversal through already-granted children so deeper descendants are not omitted.
                        if (parents.add(childId)) changed = true;
                    }
                } while (changed);
            }
        }
        Map<String,Map<String,Object>> index=new LinkedHashMap<>(); all.forEach(row->index.put(String.valueOf(row.get("id")),row));
        // 授权节点的祖先只用于树形定位，selectable=false 防止它们被误当成已授权分类。
        // Ancestors are returned only for tree navigation and remain non-selectable unless separately granted.
        Set<String> visible=new HashSet<>(allowed);
        for(String id:new ArrayList<>(allowed)){Map<String,Object> row=index.get(id);while(row!=null&&row.get("parentId")!=null){String parent=String.valueOf(row.get("parentId"));visible.add(parent);row=index.get(parent);}}
        List<Map<String,Object>> result=all.stream().filter(row->visible.contains(String.valueOf(row.get("id"))))
            .map(row->{Map<String,Object> copy=new LinkedHashMap<>(row);boolean selectable=allowed.contains(String.valueOf(row.get("id")))&&categoryIsActive(Long.valueOf(row.get("id").toString()));copy.put("selectable",selectable);return copy;}).toList();
        return Map.of("rows",result,"restricted",!scope.mode.equals("ALL"));
    }

    /** 已解析的分类授权快照，ID以字符串保存以兼容 bigint 的接口与前端边界。 / Resolved category grants using string IDs at bigint boundaries. */
    public record Scope(String mode,Set<String> schemeIds,Set<String> categoryIds,Set<String> descendantRoots){}
    /** 未单独配置时默认全部可见；指定范围解析为类型、节点和包含下级的授权集合。 / Resolve employee category grants; an absent row means all. */
    public Scope scope() {
        Long userId = LoginHelper.getUserId();
        if (userId == null || LoginHelper.isSuperAdmin(userId)) {
            return new Scope("ALL", Set.of(), Set.of(), Set.of());
        }
        List<String> modes = jdbc.queryForList(
            "SELECT scope_mode FROM sys_person_category_scope WHERE user_id=?", String.class, userId);
        String mode = modes.isEmpty() ? "ALL" : modes.getFirst();
        if ("ALL".equals(mode) || "NONE".equals(mode)) {
            return new Scope(mode, Set.of(), Set.of(), Set.of());
        }

        Set<String> schemes = new HashSet<>();
        Set<String> nodes = new HashSet<>();
        Set<String> descendantRoots = new HashSet<>();
        var grants = jdbc.queryForList("""
            SELECT scheme_id::text AS scheme_id, category_id::text AS category_id, include_children
            FROM sys_person_category_grant WHERE user_id=?
            """, userId);
        for (var grant : grants) {
            String schemeId = (String) grant.get("scheme_id");
            String categoryId = (String) grant.get("category_id");
            if (schemeId != null) schemes.add(schemeId);
            if (categoryId != null) {
                nodes.add(categoryId);
                // 节点本身始终授权，仅勾选包含下级时才展开子树。
                // A node grant includes the node itself; expand its subtree only when requested.
                if (Boolean.TRUE.equals(grant.get("include_children"))) descendantRoots.add(categoryId);
            }
        }
        return new Scope("SELECTED", schemes, nodes, descendantRoots);
    }

    /** 将分类授权安全拼入受控SQL；节点包含下级通过递归CTE，不信任前端隐藏。 / Enforce grants in SQL, including descendant inheritance. */
    public void appendScope(StringBuilder sql, List<Object> args, String column, Scope scope) {
        if ("ALL".equals(scope.mode())) {
            sql.append("TRUE");
            return;
        }
        if ("NONE".equals(scope.mode())) {
            sql.append("FALSE");
            return;
        }

        // PostgreSQL 的分类主键是 bigint，JDBC 绑定 Long；字符串只用于接口传输。
        // Bind taxonomy IDs as Long for PostgreSQL; use strings at the API boundary.
        List<String> clauses = new ArrayList<>();
        if (!scope.schemeIds().isEmpty()) {
            clauses.add(column + " IN (SELECT category_id FROM base_category WHERE scheme_id IN ("
                + placeholders(scope.schemeIds()) + "))");
            scope.schemeIds().stream().map(Long::valueOf).forEach(args::add);
        }
        if (!scope.categoryIds().isEmpty()) {
            clauses.add(column + " IN (" + placeholders(scope.categoryIds()) + ")");
            scope.categoryIds().stream().map(Long::valueOf).forEach(args::add);
        }
        if (!scope.descendantRoots().isEmpty()) {
            clauses.add(column + " IN (WITH RECURSIVE allowed_category(category_id) AS ("
                + "SELECT category_id FROM base_category WHERE category_id IN (" + placeholders(scope.descendantRoots()) + ")"
                + " UNION SELECT c.category_id FROM base_category c JOIN allowed_category a"
                + " ON c.parent_id=a.category_id WHERE c.del_flag='0') SELECT category_id FROM allowed_category)");
            scope.descendantRoots().stream().map(Long::valueOf).forEach(args::add);
        }
        sql.append(clauses.isEmpty() ? "FALSE" : "(" + String.join(" OR ", clauses) + ")");
    }
    /** 生成参数化分类查询占位符，避免拼接人员授权范围到 SQL。 / Build parameterized category-scope placeholders instead of injecting scope into SQL text. */

    private String placeholders(Set<String> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }
    /** 判断分类是否处于当前人员明确授权的可见范围内。 / Check whether a category is explicitly included in the current user's authorized scope. */

    public boolean categoryAllowed(Long id) {
        if (id == null) return false;
        StringBuilder sql = new StringBuilder("SELECT c.category_id FROM base_category c WHERE c.category_id=? AND ");
        List<Object> args = new ArrayList<>();
        args.add(id);
        appendScope(sql, args, "c.category_id", scope());
        return !jdbc.queryForList(sql.toString(), args.toArray()).isEmpty();
    }

    /**
     * 新引用要求分类存在，且整条祖先链和所属分类类型均启用。
     * New references require an existing category with enabled ancestors and category types.
     */
    public boolean categoryIsActive(Long id) {
        if (id == null) return false;
        // 不存在的分类没有祖先记录，必须明确返回 false。
        // An empty ancestor set means the category is unknown and must be rejected.
        Boolean active = jdbc.queryForObject("""
            WITH RECURSIVE ancestors(category_id, parent_id, status, del_flag, scheme_id) AS (
                SELECT category_id, parent_id, status, del_flag, scheme_id FROM base_category WHERE category_id=?
                UNION
                SELECT p.category_id, p.parent_id, p.status, p.del_flag, p.scheme_id
                FROM base_category p JOIN ancestors a ON p.category_id=a.parent_id
            )
            SELECT EXISTS (SELECT 1 FROM ancestors WHERE parent_id IS NULL OR parent_id=0)
                AND NOT EXISTS (
                    SELECT 1 FROM ancestors a
                    LEFT JOIN base_category_scheme s ON s.scheme_id=a.scheme_id
                    WHERE a.status IS DISTINCT FROM '0' OR a.del_flag IS DISTINCT FROM '0'
                       OR s.status IS DISTINCT FROM '0' OR s.del_flag IS DISTINCT FROM '0'
                )
            """, Boolean.class, id);
        return Boolean.TRUE.equals(active);
    }
}
