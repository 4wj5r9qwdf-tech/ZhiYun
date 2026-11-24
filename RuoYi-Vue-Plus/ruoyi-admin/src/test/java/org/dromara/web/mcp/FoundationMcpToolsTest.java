/*
 * 文件用途：验证计量单位 MCP Map 入参的类型边界。
 * 文件版本：1.0.0
 * 功能目的：防止 ID、系数或未知字段被自由 Map 宽松接受并造成契约漂移。
 * 设计思路：直接验证适配层进入 REST 控制器前的规范化结果。
 * 实现方法：覆盖单位ID/系数、颜色编码字段/类型及编码规则完整性。
 * 依赖与引用：FoundationMcpTools.java；McpInputValidator.java；Docs/API/计量单位API.md；Docs/API/颜色管理API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-基础资料MCP Map契约收紧；验证ID、精确数值和完整字段
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（8 项基础资料 MCP 输入契约测试）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundationMcpToolsTest {

    private final FoundationMcpTools tools = new FoundationMcpTools(null, null, null);

    @Test
    void preservesLargeDecimalStringIdsAndConversionFactors() {
        Map<String, Object> body = unitBody();
        body.put("baseUnitId", "123456789012345678");
        body.put("factor", "0.001");

        Map<String, Object> normalized = tools.validateUnitBody(body, false);

        assertEquals("123456789012345678", normalized.get("baseUnitId"));
        assertEquals("0.001", normalized.get("factor"));
    }

    @Test
    void rejectsNumericReferenceIdsAndFactors() {
        Map<String, Object> numericId = unitBody();
        numericId.put("baseUnitId", 123L);
        assertThrows(ServiceException.class, () -> tools.validateUnitBody(numericId, false));

        Map<String, Object> numericFactor = unitBody();
        numericFactor.put("factor", 0.001d);
        assertThrows(ServiceException.class, () -> tools.validateUnitBody(numericFactor, false));
    }

    @Test
    void acceptsNullReferenceForAnIndependentRootAndRejectsUnknownKeys() {
        Map<String, Object> root = unitBody();
        root.put("baseUnitId", null);
        assertEquals(null, tools.validateUnitBody(root, false).get("baseUnitId"));

        Map<String, Object> unknown = unitBody();
        unknown.put("unitId", "101");
        assertThrows(ServiceException.class, () -> tools.validateUnitBody(unknown, false));
    }

    @Test
    void unitUpdateRequiresTheCompleteReplacementObject() {
        assertThrows(ServiceException.class, () -> tools.validateUnitBody(unitBody(), true));

        Map<String, Object> complete = unitBody();
        complete.put("symbol", "m");
        complete.put("decimalPlaces", 3);
        complete.put("baseUnitId", null);
        complete.put("sortOrder", 0);
        complete.put("status", "0");
        complete.put("remark", "长度基准单位");
        tools.validateUnitBody(complete, true);
    }

    @Test
    void colorEditRejectsIgnoredCodeFieldsAndNumericValues() {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("name", "海军蓝");
        edit.put("color_value", "#1F2A44");
        edit.put("sort_order", 10);
        edit.put("remark", "示例");
        tools.validateColorBody(edit, false);

        edit.put("code", "NEW-CODE");
        assertThrows(ServiceException.class, () -> tools.validateColorBody(edit, false));

        Map<String, Object> numericColor = new LinkedHashMap<>();
        numericColor.put("name", "海军蓝");
        numericColor.put("color_value", 123456);
        assertThrows(ServiceException.class, () -> tools.validateColorBody(numericColor, false));
    }

    @Test
    void colorCodeRuleRequiresAllTypedFields() {
        Map<String, Object> rule = Map.of("enabled", true, "prefix", "CLR", "digits", 4);
        tools.validateColorCodeRuleBody(rule);

        assertThrows(ServiceException.class,
            () -> tools.validateColorCodeRuleBody(Map.of("enabled", true, "prefix", "CLR")));
        assertThrows(ServiceException.class,
            () -> tools.validateColorCodeRuleBody(Map.of("enabled", true, "prefix", "CLR", "digits", "4")));
    }

    @Test
    void categoryNodeRejectsNumericIdsAndRequiresFullUpdate() {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("schemeId", "101");
        create.put("parentId", null);
        create.put("name", "面料");
        tools.validateCategoryBody(create, false);

        create.put("schemeId", 101L);
        assertThrows(ServiceException.class, () -> tools.validateCategoryBody(create, false));

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("schemeId", "101");
        update.put("parentId", null);
        update.put("code", "FABRIC");
        update.put("name", "面料");
        update.put("sortOrder", 0);
        update.put("status", "0");
        update.put("remark", "");
        tools.validateCategoryBody(update, true);
        update.remove("parentId");
        assertThrows(ServiceException.class, () -> tools.validateCategoryBody(update, true));
    }

    @Test
    void categorySchemeUpdateRejectsIncompleteReplacement() {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("code", "material");
        create.put("name", "物料分类");
        tools.validateCategorySchemeBody(create, false);

        assertThrows(ServiceException.class, () -> tools.validateCategorySchemeBody(create, true));
    }

    private Map<String, Object> unitBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "M");
        body.put("name", "米");
        body.put("dimension", "length");
        body.put("factor", "1");
        return body;
    }
}
