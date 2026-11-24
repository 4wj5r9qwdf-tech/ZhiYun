/*
 * 文件用途：验证款式 BOM 自由对象入参中的 ID 字符串契约。
 * 文件版本：1.0.0
 * 功能目的：防止 style_bom_save 把 JSON 数字 ID 宽松转换为 Long，造成客户端精度损失被掩盖。
 * 设计思路：直接测试 MCP 工具调用业务控制器前的请求字段校验。
 * 实现方法：覆盖合法十进制字符串、数字型根 ID 和嵌套明细 ID 三类输入。
 * 依赖与引用：StyleBomMcpTools.java；McpInputValidator.java；Docs/API/款式BOMAPI.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-契约字段语义统一；验证BOM MCP自由对象的ID类型
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（4 项；数字 ID 拒绝、十进制字符串接受、试算默认值一致）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleBomMcpToolsTest {

    private final StyleBomMcpTools tools = new StyleBomMcpTools(null);

    @Test
    void acceptsDecimalStringIdsAndOptionalBlankValues() {
        Map<String, Object> body = Map.of(
            "styleId", "123456789012345678",
            "id", "",
            "preparerId", " ",
            "rules", List.of(Map.of(
                "childId", "123456789012345678",
                "colorId", "",
                "sizeId", " ",
                "materialId", "523456789012345678",
                "usageUnitId", ""
            ))
        );

        assertDoesNotThrow(() -> tools.validateSaveIds(body));
    }

    @Test
    void rejectsNumericRootIds() {
        assertThrows(ServiceException.class,
            () -> tools.validateSaveIds(Map.of("styleId", 123L)));
    }

    @Test
    void rejectsNumericIdsNestedInRules() {
        Map<String, Object> body = Map.of(
            "styleId", "123456789012345678",
            "rules", List.of(Map.of(
                "materialId", 523456789012345678L
            ))
        );

        assertThrows(ServiceException.class, () -> tools.validateSaveIds(body));
    }

    @Test
    void previewCountHasSameDefaultAsRest() {
        Method preview = java.util.Arrays.stream(StyleBomMcpTools.class.getDeclaredMethods())
            .filter(method -> {
                McpTool tool = method.getAnnotation(McpTool.class);
                return tool != null && "style_bom_preview".equals(tool.name());
            })
            .findFirst()
            .orElseThrow();
        Parameter count = preview.getParameters()[1];
        McpToolParam metadata = count.getAnnotation(McpToolParam.class);

        assertFalse(metadata.required());
        assertTrue(metadata.description().contains("默认 1"));
        assertEquals(Integer.class, count.getType());
    }
}
