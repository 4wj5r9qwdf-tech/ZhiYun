package org.dromara.system.controller.system;

import org.dromara.common.core.utils.ServletUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** 只采集调用方明确列出的字段，并以实际保存值计算差异。 */
public final class BusinessAudit {
    private BusinessAudit() {}

    public static Map<String,Object> snapshot(JdbcTemplate jdbc,String sql,Object id) {
        var rows = jdbc.queryForList(sql,id);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.getFirst();
    }

    public static void record(String action,Object id,String nameField,Map<String,Object> before,Map<String,Object> after) {
        if (id == null || (before.isEmpty() && after.isEmpty())) return;
        var changes = new LinkedHashMap<String,Object>();
        var keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (Objects.equals(oldValue,newValue)) continue;
            if (Set.of("电话","邮箱").contains(key)) {
                changes.put(key,Arrays.asList(oldValue == null ? null : "已填写",newValue == null ? null : "已变更"));
            } else {
                changes.put(key,Arrays.asList(oldValue,newValue));
            }
        }
        String name = Objects.toString(after.getOrDefault(nameField,before.get(nameField)),"");
        ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
            "action",action,"id",id.toString(),"name",name,"changes",changes));
    }
}
