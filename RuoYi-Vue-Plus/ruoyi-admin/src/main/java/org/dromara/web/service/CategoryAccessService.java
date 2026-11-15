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
            for (Map<String,Object> row : all) if (scope.schemeIds.contains(String.valueOf(row.get("schemeId"))) || scope.categoryIds.contains(String.valueOf(row.get("id")))) allowed.add(String.valueOf(row.get("id")));
            if (!scope.descendantRoots.isEmpty()) {
                Set<String> parents = new HashSet<>(scope.descendantRoots);
                boolean changed;
                do { changed=false; for (Map<String,Object> row:all) if (parents.contains(String.valueOf(row.get("parentId"))) && allowed.add(String.valueOf(row.get("id")))) { parents.add(String.valueOf(row.get("id"))); changed=true; } } while(changed);
            }
        }
        Map<String,Map<String,Object>> index=new LinkedHashMap<>(); all.forEach(row->index.put(String.valueOf(row.get("id")),row));
        Set<String> visible=new HashSet<>(allowed);
        for(String id:new ArrayList<>(allowed)){Map<String,Object> row=index.get(id);while(row!=null&&row.get("parentId")!=null){String parent=String.valueOf(row.get("parentId"));visible.add(parent);row=index.get(parent);}}
        List<Map<String,Object>> result=all.stream().filter(row->visible.contains(String.valueOf(row.get("id"))))
            .map(row->{Map<String,Object> copy=new LinkedHashMap<>(row);boolean selectable=allowed.contains(String.valueOf(row.get("id")))&&categoryIsActive(Long.valueOf(row.get("id").toString()));copy.put("selectable",selectable);return copy;}).toList();
        return Map.of("rows",result,"restricted",!scope.mode.equals("ALL"));
    }

    public record Scope(String mode,Set<String> schemeIds,Set<String> categoryIds,Set<String> descendantRoots){}
    /** 未单独配置时默认全部可见；指定范围解析为类型、节点和包含下级的授权集合。 / Resolve employee category grants; an absent row means all. */
    public Scope scope(){Long user=LoginHelper.getUserId();if(user==null||LoginHelper.isSuperAdmin(user))return new Scope("ALL",Set.of(),Set.of(),Set.of());List<String> modes=jdbc.queryForList("SELECT scope_mode FROM sys_person_category_scope WHERE user_id=?",String.class,user);if(modes.isEmpty()||"ALL".equals(modes.getFirst()))return new Scope("ALL",Set.of(),Set.of(),Set.of());if("NONE".equals(modes.getFirst()))return new Scope("NONE",Set.of(),Set.of(),Set.of());List<Map<String,Object>> grants=jdbc.queryForList("SELECT scheme_id::text AS scheme_id,category_id::text AS category_id,include_children FROM sys_person_category_grant WHERE user_id=?",user);Set<String> schemes=new HashSet<>(),nodes=new HashSet<>(),children=new HashSet<>();for(Map<String,Object> g:grants){if(g.get("scheme_id")!=null)schemes.add(String.valueOf(g.get("scheme_id")));if(g.get("category_id")!=null){nodes.add(String.valueOf(g.get("category_id")));if(Boolean.TRUE.equals(g.get("include_children")))children.add(String.valueOf(g.get("category_id")));}}return new Scope("SELECTED",schemes,nodes,children);}
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

    private String placeholders(Set<String> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    public boolean categoryAllowed(Long id){StringBuilder q=new StringBuilder("SELECT c.category_id FROM base_category c WHERE c.category_id=? AND ");List<Object>a=new ArrayList<>();a.add(id);appendScope(q,a,"c.category_id",scope());return !jdbc.queryForList(q.toString(),a.toArray()).isEmpty();}
    /** 新引用必须有完整启用的分类祖先链及启用体系。 / Require an enabled taxonomy and every ancestor for new references. */
    public boolean categoryIsActive(Long id){if(id==null)return false;Long count=jdbc.queryForObject("WITH RECURSIVE ancestors(category_id,parent_id,status,del_flag,scheme_id) AS (SELECT category_id,parent_id,status,del_flag,scheme_id FROM base_category WHERE category_id=? UNION ALL SELECT p.category_id,p.parent_id,p.status,p.del_flag,p.scheme_id FROM base_category p JOIN ancestors a ON p.category_id=a.parent_id) SELECT count(*) FROM ancestors a JOIN base_category_scheme s ON s.scheme_id=a.scheme_id WHERE a.status<>'0' OR a.del_flag<>'0' OR s.status<>'0' OR s.del_flag<>'0'",Long.class,id);return count!=null&&count==0;}
}
