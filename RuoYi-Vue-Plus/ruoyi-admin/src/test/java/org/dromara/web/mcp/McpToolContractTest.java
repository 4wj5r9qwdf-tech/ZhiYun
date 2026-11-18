package org.dromara.web.mcp;

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

/**
 * MCP 参数注释必须与实际空值语义一致，避免把筛选参数错误发布为必填字段。
 * Keep MCP parameter metadata aligned with null/optional semantics so clients do not send fake values.
 */
class McpToolContractTest {

    private static final List<Class<?>> TOOL_CLASSES = List.of(
        FoundationMcpTools.class,
        MasterDataMcpTools.class,
        MaterialMcpTools.class,
        SizeMcpTools.class,
        StyleBomMcpTools.class,
        StyleMcpTools.class,
        WarehouseMcpTools.class
    );
    private static final List<String> OPTIONAL_WORDS = List.of("可选", "默认", "省略", "不传", "留空");

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
                    boolean describedOptional = OPTIONAL_WORDS.stream().anyMatch(metadata.description()::contains);
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
        assertEquals(123, annotatedMethods, "Update the expected tool total when MCP registration intentionally changes");
    }
}
