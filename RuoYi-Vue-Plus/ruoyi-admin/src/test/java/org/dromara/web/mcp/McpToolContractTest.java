/*
 * 文件用途：验证织云 ERP MCP 工具名称、参数元数据、写操作确认和注册目录契约。
 * 文件版本：1.0.0
 * 功能目的：避免工具 schema、调用说明和智能体权限/确认策略偏离实际工具实现。
 * 设计思路：反射读取 MCP 注解，针对名称唯一性、可选参数、写入确认、特殊前置与注册状态建立可重复断言。
 * 实现方法：遍历已注册工具类的注解并逐项核对工具名、描述与参数元数据。
 * 关键约束：该测试只验证静态契约，不等同于运行服务中的 tools/list、权限隔离或真实业务调用验收。
 * 依赖与引用：RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/；Docs/Skills/MCP调用指南.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP调用前置契约；校验全部写工具展示确认及关键业务差异
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（9 项 MCP 工具契约测试）
 * 测试范围与记录位置：全部写工具确认描述、款式/审批/分类契约和工具注册；Maven Surefire 报告
 */
package org.dromara.web.mcp;

import org.dromara.demo.mcp.McpDemoServerTool;
import org.dromara.ai.snailai.SnailAiMcpToolPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 参数注释必须与实际空值语义一致，避免把筛选参数错误发布为必填字段。
 * Keep MCP parameter metadata aligned with null/optional semantics so clients do not send fake values.
 */
class McpToolContractTest {

    private static final List<Class<?>> TOOL_CLASSES = List.of(
        FoundationMcpTools.class,
        MasterDataMcpTools.class,
        MaterialMcpTools.class,
        ApprovalCenterMcpTools.class,
        PersonCategoryScopeMcpTools.class,
        PersonMcpTools.class,
        TagMcpTools.class,
        SizeMcpTools.class,
        StyleBomMcpTools.class,
        StyleMcpTools.class,
        WarehouseMcpTools.class,
        McpDemoServerTool.class
    );
    private static final List<String> OPTIONAL_WORDS = List.of("可选", "默认", "省略", "不传", "留空");
    private static final List<String> WRITE_TOOL_SUFFIXES = List.of(
        "_create", "_update", "_delete", "_set_status", "_save", "_return", "_approve", "_set",
        "_remove", "_restore", "_batch_create", "_batch_delete", "_batch_set", "_batch_set_tags",
        "_submit", "_cancel", "_revoke", "_enable", "_disable", "_publish", "_reset", "_set_code_rule",
        "_set_default"
    );
    private static final List<String> PRE_CALL_WORDS = List.of(
        "调用前", "执行前", "写操作前", "写入前", "操作前", "新增前", "修改前", "删除前", "保存前", "退回前", "恢复前", "设置前"
    );

    /** 可选或默认参数必须在生成的 MCP input schema 中标为非必填。 / Optional and defaulted values must not be required in the MCP input schema. */
    @Test
    void optionalDescriptionsMustBeMarkedNotRequired() {
        List<String> violations = new ArrayList<>();
        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getDeclaredMethods()) {
                if (method.getAnnotation(McpTool.class) == null) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    McpToolParam metadata = parameter.getAnnotation(McpToolParam.class);
                    assertNotNull(metadata, toolClass.getSimpleName() + "." + method.getName() + " parameter " + parameter.getName());
                    String description = metadata.description().stripLeading();
                    boolean describedOptional = OPTIONAL_WORDS.stream().anyMatch(description::startsWith);
                    if (describedOptional && metadata.required()) {
                        violations.add(toolClass.getSimpleName() + "." + method.getName() + "." + parameter.getName());
                    }
                    if (!metadata.required() && !describedOptional) {
                        violations.add(toolClass.getSimpleName() + "." + method.getName() + "." + parameter.getName() + " lacks an explicit optional/default description");
                    }
                }
            }
        }
        assertEquals(List.of(), violations, "MCP optional-parameter metadata is inconsistent");
    }

    /** MCP 工具名必须唯一，避免 Skill 指向含义不确定的工具。 / MCP names must be unique so Skills resolve to one tool. */
    @Test
    void toolNamesMustBeUnique() {
        Set<String> names = new HashSet<>();
        int annotatedMethods = 0;
        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getDeclaredMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool == null) {
                    continue;
                }
                annotatedMethods++;
                assertFalse(tool.name().isBlank(), toolClass.getSimpleName() + "." + method.getName() + " must have an explicit MCP name");
                assertEquals(true, names.add(tool.name()), "Duplicate MCP tool name: " + tool.name());
            }
        }
        assertEquals(159, annotatedMethods, "Update the expected tool total when MCP registration intentionally changes");
    }

    /** 款式写工具须声明调用前展示实际目标和影响并取得本次确认。 / Style write tools must require a pre-call review and one-time employee confirmation. */
    @Test
    void styleWriteToolsRequirePreCallConfirmation() {
        for (String name : List.of("style_create", "style_update", "style_set_status", "style_delete")) {
            McpTool tool = findTool(name);
            assertNotNull(tool, "Missing MCP tool " + name);
            assertTrue(tool.description().contains("调用前"), name + " must require review before execution");
            assertTrue(tool.description().contains("展示"), name + " must describe information shown to the employee");
            assertTrue(tool.description().contains("本次明确确认"), name + " must require fresh confirmation for this action");
        }
    }

    /** 所有按命名约定识别的写工具都须要求先展示具体对象并取得确认。 / Every named write tool must require a pre-call display and employee confirmation. */
    @Test
    void everyNamedWriteToolRequiresPreCallDisplayAndConfirmation() {
        List<String> violations = new ArrayList<>();
        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getDeclaredMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool == null || WRITE_TOOL_SUFFIXES.stream().noneMatch(tool.name()::endsWith)) {
                    continue;
                }
                assertFalse(SnailAiMcpToolPolicy.isReadOnlyName(tool.name()),
                    tool.name() + " is a write tool and must never be classified as read-only");
                String description = tool.description();
                if (!description.contains("展示")) {
                    violations.add(tool.name() + " must specify information shown before writing");
                }
                if (!description.contains("确认")) {
                    violations.add(tool.name() + " must require employee confirmation");
                }
                if (PRE_CALL_WORDS.stream().noneMatch(description::contains)) {
                    violations.add(tool.name() + " must make confirmation timing explicit");
                }
            }
        }
        assertEquals(List.of(), violations, "MCP write-tool confirmation descriptions are inconsistent");
    }

    /** BOM 草稿保存也需要单次确认，发布必须额外明确确认并设置 release=true。 / BOM draft saves need confirmation, and release needs a separate explicit intent. */
    @Test
    void styleBomSaveRequiresConfirmationForDraftAndRelease() {
        McpTool tool = findTool("style_bom_save");
        assertNotNull(tool);
        assertTrue(tool.description().contains("调用前"));
        assertTrue(tool.description().contains("完整规格、用料规则和版本变化"));
        assertTrue(tool.description().contains("保存草稿"));
        assertTrue(tool.description().contains("发布必须另行明确确认并设置 release=true"));
    }

    /** 设置绑定必须从列表选择 flowCode；解除绑定只依赖业务类型且禁止解除反审核流程。 / Set and remove bindings have distinct inputs and prerequisites. */
    @Test
    void approvalBindingSetAndRemoveHaveDistinctPrerequisites() {
        McpTool set = findTool("approval_binding_set");
        McpTool remove = findTool("approval_binding_remove");
        assertNotNull(set);
        assertNotNull(remove);
        assertTrue(set.description().contains("approval_binding_list"));
        assertTrue(set.description().contains("flowCode"));
        assertTrue(remove.description().contains("approval_binding_list"));
        assertTrue(remove.description().contains("不需要 flowCode"));
        assertTrue(remove.description().contains("反审核流程不允许解绑"));
    }

    /** 确认代码目录中已实现的物料、人员及通用分类写工具，避免把权限过滤误报为功能缺失。 / Keep backend registration distinct from a permission-filtered runtime catalog. */
    @Test
    void materialPersonnelAndCategoryWriteToolsAreRegistered() {
        for (String name : List.of("material_create", "material_update", "material_set_status", "material_delete",
            "person_create", "person_update", "person_delete", "person_restore",
            "category_create", "category_update", "category_delete")) {
            assertNotNull(findTool(name), "Expected backend MCP registration for " + name);
        }
    }

    /** 客户分类查询工具须指出通用分类树维护入口及独立权限边界。 / Explain that customer taxonomy maintenance uses the generic category tools and separate permissions. */
    @Test
    void customerCategoryOptionsPointsToGenericCategoryMaintenance() {
        McpTool tool = findTool("customer_category_options");
        assertNotNull(tool);
        assertTrue(tool.description().contains("只查询"));
        assertTrue(tool.description().contains("category_create"));
        assertTrue(tool.description().contains("category_scheme_*"));
        assertTrue(tool.description().contains("system:category:*"));
    }

    /** 演示工具默认不得混入 ERP 数据工具目录；只有显式激活演示 profile 才注册。 / Demo tools must stay out of the ERP catalog unless their profile is explicitly enabled. */
    @Test
    void demoToolsMustBeExplicitlyProfileGated() {
        org.springframework.context.annotation.Profile profile = McpDemoServerTool.class
            .getAnnotation(org.springframework.context.annotation.Profile.class);
        assertNotNull(profile);
        assertEquals(List.of("mcp-demo"), List.of(profile.value()));
    }

    /** 按工具公开名称查找注解，供调用契约断言复用。 / Find one MCP tool by its public name for contract assertions. */
    private static McpTool findTool(String name) {
        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getDeclaredMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool != null && tool.name().equals(name)) return tool;
            }
        }
        return null;
    }
}
