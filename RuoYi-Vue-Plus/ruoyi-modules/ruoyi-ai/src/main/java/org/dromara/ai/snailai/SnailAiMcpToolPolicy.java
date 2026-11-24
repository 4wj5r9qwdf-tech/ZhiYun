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
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP只读白名单收紧；仅精确登记的读取工具免确认，未知名称默认写操作
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（3 项工具分类测试）
 * 测试范围与记录位置：已知读写工具、后缀冲突及未知读式名称；SnailAiMcpToolPolicyTest 和 Maven Surefire 报告
 */
package org.dromara.ai.snailai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashSet;
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

    /** 仅把已审核的准确工具名归为只读；未知工具即使以 list/get/options 结尾也按写操作要求确认。 / Only exact reviewed names are reads; unknown tools require approval even with read-like suffixes. */
    private static final Set<String> READ_ONLY_TOOLS = Set.of(
        "approval_binding_list", "approval_my_done_list", "approval_my_initiated_detail",
        "approval_my_initiated_list", "approval_my_todo_list", "building_get", "building_list",
        "category_list", "category_options", "category_scheme_options", "category_schemes",
        "color_list", "color_next_code", "color_options", "company_file_usage", "company_get",
        "company_list", "customer_category_counts", "customer_category_options",
        "customer_check_duplicate", "customer_get", "customer_list", "customer_options",
        "customer_sales_options", "material_category_options", "material_list", "material_next_code",
        "material_options", "material_spec_templates", "org_get", "org_list", "org_manage_counts",
        "org_manages_get", "org_options", "person_business_duty_options", "person_category_scope_get",
        "person_get", "person_list", "post_get", "post_list", "post_options", "post_org_tree",
        "size_get_code_rule", "size_group_get_code_rule", "size_group_list", "size_group_options",
        "size_list", "size_options", "style_bom_coverage", "style_bom_detail", "style_bom_list",
        "style_bom_preview", "style_category_options", "style_configuration", "style_list",
        "style_spec_options", "tag_group_list", "tag_list", "tag_module_list", "tag_options",
        "unit_convert", "unit_list", "unit_options", "warehouse_form_options", "warehouse_get",
        "warehouse_layout_get", "warehouse_layout_list", "warehouse_layout_next_code",
        "warehouse_layout_options", "warehouse_layout_tree", "warehouse_list", "warehouse_next_code",
        "approval_my_todo_document", "approval_my_done_document", "approval_my_initiated_document",
        "approval_task_back_nodes", "style_tags", "read_chat_document", "read_zhiyun_skill"
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

    /** 按精确只读白名单分类；未知工具走需确认写操作路径。 / Classify exact allowlisted reads; unknown tools use the approval-required write path. */
    public static boolean isReadOnly(ToolCallback callback) {
        return isReadOnlyName(callback.getToolDefinition().name());
    }

    /** 只把经过审核的准确工具名视为只读；未登记工具一律按写操作处理。 / Treat only reviewed exact names as reads; classify all unregistered names as writes. */
    public static boolean isReadOnlyName(String toolName) {
        String name = normalizeName(toolName);
        return READ_ONLY_TOOLS.contains(name);
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
