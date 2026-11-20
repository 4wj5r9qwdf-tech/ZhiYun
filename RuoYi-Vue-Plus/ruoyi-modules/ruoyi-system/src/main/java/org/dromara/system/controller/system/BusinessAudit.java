/*
 * 文件用途：按调用方提供的前后快照生成可审计的业务变更记录。
 * 文件版本：1.0.0
 * 功能目的：按调用方提供的前后快照生成可审计的业务变更记录。
 * 设计思路：仅比较业务明确选出的字段；对电话和邮箱不写入原始敏感值，使用受控变化提示。
 * 实现方法：合并变更前后字段集合，忽略未变化字段，再将动作、对象和差异放入请求审计上下文。
 * 关键约束：不得传入密码、密钥或不必要的敏感字段；日志必须保留可识别对象和实际变化。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/API/API文档.md；sql/操作日志扩容_20261103.sql
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.system.controller.system;

import org.dromara.common.core.utils.ServletUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** 只采集调用方明确列出的字段，并以实际保存值计算差异。 */
public final class BusinessAudit {
    private BusinessAudit() {}

    /**
     * 以调用方提供的固定查询和记录主键读取审计前快照；未找到时返回空映射。
     * sql 必须来自项目代码常量，不能拼接用户输入，以免审计读取变成动态 SQL 入口。
     */
    public static Map<String,Object> snapshot(JdbcTemplate jdbc,String sql,Object id) {
        var rows = jdbc.queryForList(sql,id);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.getFirst();
    }

    /**
     * 比较显式快照并将可追溯的业务动作、对象名称和字段差异放入当前请求上下文供审计拦截器持久化。
     * 电话和邮箱只记录是否已填写/变更，不把敏感原值复制到操作日志。
     */
    public static void record(String action,Object id,String nameField,Map<String,Object> before,Map<String,Object> after) {
        if (id == null || (before.isEmpty() && after.isEmpty())) return;
        var changes = new LinkedHashMap<String,Object>();
        var keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        // 只记录真实值变化；允许调用方通过 snapshot/after 选择审计字段，避免把整行敏感信息写入日志。
        for (String key : keys) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (Objects.equals(oldValue,newValue)) continue;
            // 联系方式审计保留变更事实但不复制原始内容，满足追溯同时降低敏感信息扩散。
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
