/*
 * 文件用途：验证款式 MCP DTO 前的递归输入契约。
 * 文件版本：1.0.0
 * 功能目的：拒绝数值 ID、未知嵌套字段和不完整的款式标量替换。
 * 设计思路：直接测试纯 Map 校验，不连接数据库或保存款式。
 * 实现方法：覆盖标量 ID、全量字段、成员、规格配对、附件和空集合形态。
 * 依赖与引用：StyleMcpTools.java；Docs/API/款式档案API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-款式 MCP 生产模式字段契约；覆盖嵌套对象字符串 ID 与适用范围
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（5 项输入契约测试）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StyleMcpInputContractTest {

    private final StyleMcpTools tools = new StyleMcpTools(null);

    @Test
    void createAcceptsStringIdsAndCompleteSpecificationArrays() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("styleCode", "ST001");
        body.put("name", "示例衬衣");
        body.put("categoryId", "10001");
        body.put("productionMode", "single");
        body.put("colorIds", List.of("20001"));
        body.put("sizeIds", List.of("30001"));
        body.put("excludedPairs", List.of());
        body.put("attachments", List.of(Map.of("ossId", "40001", "category", "style_image")));
        assertDoesNotThrow(() -> tools.styleBody(body, true));

        body.put("categoryId", 10001L);
        assertThrows(ServiceException.class, () -> tools.styleBody(body, true));
    }

    @Test
    void updateRequiresCompleteScalarSnapshotAndAllowsAssociationOmission() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("styleCode", "ST001");
        body.put("name", "示例衬衣");
        body.put("customerId", null);
        body.put("categoryId", "10001");
        body.put("season", null);
        body.put("description", null);
        body.put("coverOssId", null);
        body.put("remark", null);
        body.put("productionMode", "single");
        body.put("sameHanger", false);
        assertDoesNotThrow(() -> tools.styleBody(body, false));
        body.remove("remark");
        assertThrows(ServiceException.class, () -> tools.styleBody(body, false));
    }

    @Test
    void rejectsNumericNestedIdsAndUnknownSensitiveFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("styleCode", "ST001");
        body.put("name", "示例套装");
        body.put("categoryId", "10001");
        body.put("productionMode", "combined");
        body.put("members", List.of(Map.of("childId", 20001L, "roleName", "上衣", "quantity", 1)));
        assertThrows(ServiceException.class, () -> tools.styleBody(body, true));

        body.put("members", List.of(Map.of("childId", "20001", "roleName", "上衣", "quantity", 1)));
        body.put("certNo", "private");
        assertThrows(ServiceException.class, () -> tools.styleBody(body, true));
    }

    @Test
    void rejectsPartialStyleSpecificationCollections() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("styleCode", "ST001");
        body.put("name", "示例衬衣");
        body.put("categoryId", "10001");
        body.put("colorIds", List.of());
        assertThrows(ServiceException.class, () -> tools.styleBody(body, true));
    }

    @Test
    void rejectsSingleStyleSpecificationsForSetProductionModes() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("styleCode", "ST001");
        body.put("name", "示例套装");
        body.put("categoryId", "10001");
        body.put("productionMode", "combined");
        body.put("colorIds", List.of());
        body.put("sizeIds", List.of());
        body.put("excludedPairs", List.of());
        assertThrows(ServiceException.class, () -> tools.styleBody(body, true));
    }
}
