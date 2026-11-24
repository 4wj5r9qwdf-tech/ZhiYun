/*
 * 文件用途：验证仓库 MCP 写对象的字段白名单、字符串 ID 和全量标签集合语义。
 * 文件版本：1.0.0
 * 功能目的：阻止仓库 Map 入参静默忽略错误 ID、标签成员和编码规则类型。
 * 设计思路：直接测试 MCP 适配器纯校验逻辑，不连接数据库或创建业务档案。
 * 实现方法：覆盖自动编码默认、完整更新、空标签清除及配置枚举。
 * 依赖与引用：WarehouseMcpTools.java；Docs/API/仓库管理API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-仓库MCP写入契约；校验 Map/集合/配置规则边界
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（3 项仓库 MCP 输入契约测试）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarehouseMcpInputContractTest {

    private final WarehouseMcpTools tools = new WarehouseMcpTools(null, null, null);

    @Test
    void createDefaultsAndStringIdsAreNormalizedForLegacyController() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseName", "一号仓");
        body.put("warehouseCode", null);
        body.put("buildingId", "20001");
        body.put("tagIds", List.of("30001"));
        Map<String, Object> normalized = tools.warehouseBody(body, false);
        assertEquals(20001L, normalized.get("buildingId"));
        assertEquals(List.of("30001"), normalized.get("tagIds"));
        assertEquals(2, normalized.get("inboundLocationLevel"));
        assertEquals("0", normalized.get("status"));
    }

    @Test
    void updateRequiresFullObjectAndUsesEmptyArrayToClearTags() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", "10001");
        body.put("warehouseCode", "WH0001");
        body.put("warehouseName", "一号仓");
        body.put("buildingId", null);
        body.put("floorName", null);
        body.put("areaName", "东侧");
        body.put("managerUserId", null);
        body.put("inboundLocationLevel", 2);
        body.put("status", "0");
        body.put("remark", "");
        body.put("tagIds", List.of());
        assertEquals(10001L, tools.warehouseBody(body, true).get("warehouseId"));

        body.remove("tagIds");
        assertThrows(ServiceException.class, () -> tools.warehouseBody(body, true));
    }

    @Test
    void rejectsNumericTagIdsUnknownFieldsAndMalformedCodeRules() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseName", "一号仓");
        body.put("tagIds", List.of(30001L));
        assertThrows(ServiceException.class, () -> tools.warehouseBody(body, false));

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("enabled", true);
        rule.put("prefix", "WH");
        rule.put("digits", 4);
        assertEquals(4, tools.codeRuleBody(rule).get("digits"));
        rule.put("digits", 4.0);
        assertThrows(ServiceException.class, () -> tools.codeRuleBody(rule));
    }
}
