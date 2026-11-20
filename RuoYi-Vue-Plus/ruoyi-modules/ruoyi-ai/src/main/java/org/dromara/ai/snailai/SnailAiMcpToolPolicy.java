/*
 * 文件用途：规范化并判定 ERP 智能体 MCP 工具的可见性、只读属性和确认要求。
 * 文件版本：1.0.0
 * 功能目的：规范化并判定 ERP 智能体 MCP 工具的可见性、只读属性和确认要求。
 * 设计思路：将工具名称和元数据映射到经权限系统批准的工具类别；未知或歧义工具默认按写操作处理。
 * 实现方法：对工具名称去前缀/归一化，按权限列表过滤，并判断是否需要员工确认。
 * 关键约束：过滤规则需与服务端 MCP 权限一致；未知工具不能被默认视为安全读取。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md；RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/
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
package org.dromara.ai.snailai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 对 MCP 工具执行失败关闭的权限筛选，并区分员工登录型查询与需确认的写操作。 / Apply fail-closed MCP permission filtering and classify employee-only reads. */
public final class SnailAiMcpToolPolicy {

    private static final Pattern PERMISSION_CODE =
        Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_-]*:[A-Za-z0-9_*:-]+)");

    private static final Map<String, Set<String>> PERMISSION_OVERRIDES = Map.ofEntries(
        Map.entry("unit_list", Set.of("system:unit:list")),
        Map.entry("unit_options", Set.of()),
        Map.entry("unit_convert", Set.of()),
        Map.entry("unit_create", Set.of("system:unit:add")),
        Map.entry("unit_update", Set.of("system:unit:edit")),
        Map.entry("unit_delete", Set.of("system:unit:remove")),
        Map.entry("tag_group_list", Set.of()),
        Map.entry("tag_options", Set.of()),
        Map.entry("category_schemes", Set.of("system:category:list")),
        Map.entry("category_scheme_options", Set.of()),
        Map.entry("category_list", Set.of("system:category:list")),
        Map.entry("category_options", Set.of()),
        Map.entry("category_create", Set.of("system:category:add")),
        Map.entry("category_update", Set.of("system:category:edit")),
        Map.entry("category_delete", Set.of("system:category:remove")),
        Map.entry("category_scheme_create", Set.of("system:category:add")),
        Map.entry("category_scheme_update", Set.of("system:category:edit")),
        Map.entry("category_scheme_delete", Set.of("system:category:remove")),
        Map.entry("style_bom_list", Set.of("rd:bom:list")),
        Map.entry("style_bom_detail", Set.of("rd:bom:list")),
        Map.entry("style_bom_coverage", Set.of("rd:bom:list")),
        Map.entry("style_bom_preview", Set.of("rd:bom:list")),
        Map.entry("style_bom_save", Set.of("rd:bom:list", "rd:bom:edit")),
        Map.entry("material_category_options", Set.of()),
        Map.entry("material_options", Set.of()),
        Map.entry("material_spec_templates", Set.of()),
        Map.entry("color_options", Set.of()),
        Map.entry("warehouse_form_options", Set.of()),
        Map.entry("warehouse_layout_options", Set.of()),
        Map.entry("size_options", Set.of()),
        Map.entry("size_get_code_rule", Set.of()),
        Map.entry("size_group_options", Set.of()),
        Map.entry("size_group_get_code_rule", Set.of())
    );

    private static final Set<String> EMPLOYEE_LOGIN_ONLY = Set.of(
        "unit_options", "unit_convert", "tag_group_list", "tag_options", "category_scheme_options", "category_options",
        "material_category_options", "material_options", "material_spec_templates",
        "color_options", "warehouse_form_options", "warehouse_layout_options",
        "size_options", "size_get_code_rule", "size_group_options", "size_group_get_code_rule"
    );

    private static final List<String> READ_ONLY_SUFFIXES = List.of(
        "_list", "_get", "_detail", "_options", "_tree", "_counts", "_usage",
        "_check_duplicate", "_sales_options", "_org_tree", "_configuration", "_spec_options",
        "_spec_templates", "_tags", "_preview", "_coverage", "_convert", "_next_code",
        "_get_code_rule", "_schemes", "_manages_get"
    );

    /** 语义不适合用后缀判断的只读工具，采用完整名称白名单。 / Exact names for read tools whose names do not use a safe read-only suffix. */
    private static final Set<String> READ_ONLY_TOOLS = Set.of(
        "approval_my_todo_document", "approval_my_done_document", "approval_my_initiated_document",
        "approval_task_back_nodes"
    );

    /** 静态策略类不允许实例化。 / Prevent instances of this static policy utility. */
    private SnailAiMcpToolPolicy() {
    }

    /** 判断当前员工是否满足工具描述或显式覆盖表声明的全部权限；未知工具一律拒绝。 / Require every declared permission and reject tools with no known policy. */
    public static boolean isAllowed(ToolCallback callback, Set<String> permissions) {
        ToolDefinition definition = callback.getToolDefinition();
        String name = normalizeName(definition.name());
        Set<String> required = requiredPermissions(name, definition.description());
        if (required == null) {
            return false;
        }
        if (required.isEmpty()) {
            return EMPLOYEE_LOGIN_ONLY.contains(name);
        }
        return permissions.contains("*:*:*") || permissions.containsAll(required);
    }

    /** 按明确白名单或约定的只读后缀分类；需要确认的写操作不能使用泛化详情后缀。 / Classify read tools by allowlist or read suffix; write actions must not rely on generic detail suffixes. */
    public static boolean isReadOnly(ToolCallback callback) {
        String name = normalizeName(callback.getToolDefinition().name());
        return READ_ONLY_TOOLS.contains(name) || READ_ONLY_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    /** 去除 MCP 传输层可能附加的命名空间前缀，得到本地工具目录名。 / Normalize transport-qualified tool names to their local identifiers. */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String result = name;
        int slash = result.lastIndexOf('/');
        if (slash >= 0) {
            result = result.substring(slash + 1);
        }
        int separator = result.lastIndexOf("__");
        if (separator >= 0) {
            result = result.substring(separator + 2);
        }
        if (result.startsWith("erp_")) {
            result = result.substring("erp_".length());
        } else if (result.startsWith("zhiyun_erp_")) {
            result = result.substring("zhiyun_erp_".length());
        }
        return result;
    }

    /** 从工具描述提取权限码，并在描述缺失时读取显式覆盖；返回 null 表示未知且必须拒绝。 / Resolve required permissions from metadata or explicit overrides, failing closed when unknown. */
    private static Set<String> requiredPermissions(String name, String description) {
        Matcher matcher = PERMISSION_CODE.matcher(description == null ? "" : description);
        Set<String> parsed = new LinkedHashSet<>();
        while (matcher.find()) {
            parsed.add(matcher.group(1));
        }
        if (!parsed.isEmpty()) {
            return parsed;
        }
        if (PERMISSION_OVERRIDES.containsKey(name)) {
            return PERMISSION_OVERRIDES.get(name);
        }
        return null;
    }
}
