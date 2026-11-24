/*
 * 文件用途：验证物料需求计划控制器对嵌套请求字段和 ID 类型的处理。
 * 文件版本：1.0.0
 * 功能目的：确保请求中的业务 ID 保持十进制字符串，且空集合准确对应无变更。
 * 设计思路：直接测试请求转换与参数校验，不连接数据库或修改业务记录。
 * 实现方法：覆盖大整数字符串、数值型和非法 ID 拒绝、null 与空列表行为。
 * 关键约束：Jackson 不得先把大整数转为 Long/Double 后再校验来源类型。
 * 依赖与引用：MaterialDemandPlanController.java；Docs/API/物料需求计划API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-物料需求计划请求契约；覆盖 ID 原始 JSON 类型与空集合语义
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（3 项控制器契约测试）
 * 测试范围与记录位置：Maven Surefire 报告
 */
package org.dromara.web.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.web.service.BusinessApprovalWorkflowService;
import org.dromara.web.service.CategoryAccessService;
import org.dromara.web.service.MaterialDemandPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import org.dromara.common.satoken.utils.LoginHelper;

/** 物料需求计划列表的可选筛选参数须兼容内部调用传入的 null。 / Verify null-safe optional filters on material demand plan lists. */
class MaterialDemandPlanControllerTest {

    /** 计划明细 ID 在 JSON 中必须是十进制字符串，数值节点和非法字符串拒绝。 / Require decimal-string item IDs and reject numeric JSON values. */
    @Test
    void quantityChangesRequireStringIds() {
        var stringId = new MaterialDemandPlanController.QuantityItemRequest(
            JsonNodeFactory.instance.textNode("2114531897794859001"), new BigDecimal("120.50000000"));
        assertEquals(List.of(new MaterialDemandPlanService.QuantityChange(2114531897794859001L, new BigDecimal("120.50000000"))),
            MaterialDemandPlanController.quantityChanges(List.of(stringId)));

        var numericId = new MaterialDemandPlanController.QuantityItemRequest(
            JsonNodeFactory.instance.numberNode(12), new BigDecimal("1"));
        assertThrows(ServiceException.class, () -> MaterialDemandPlanController.quantityChanges(List.of(numericId)));
        var invalidId = new MaterialDemandPlanController.QuantityItemRequest(
            JsonNodeFactory.instance.textNode("+12"), new BigDecimal("1"));
        assertThrows(ServiceException.class, () -> MaterialDemandPlanController.quantityChanges(List.of(invalidId)));
        assertThrows(ServiceException.class, () -> MaterialDemandPlanController.quantityChanges(List.of(
            new MaterialDemandPlanController.QuantityItemRequest(JsonNodeFactory.instance.textNode("0"), BigDecimal.ONE))));
    }

    /** 空明细列表按服务层既有行为表示无变更，不要求数量调整原因。 / Preserve the service's no-op behavior for absent or empty changes. */
    @Test
    void absentQuantityChangesRemainNoOp() {
        assertNull(MaterialDemandPlanController.quantityChanges(null));
        assertEquals(List.of(), MaterialDemandPlanController.quantityChanges(List.of()));
    }

    /** MCP 等内部 Java 调用绕过 HTTP 默认值时，空参数仍须表示不过滤。 / Null direct-call arguments must mean no filter, just like omitted HTTP parameters. */
    @Test
    void listWithNullFiltersBuildsUnfilteredQuery() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        MaterialDemandPlanController controller = new MaterialDemandPlanController(
            jdbc, mock(MaterialDemandPlanService.class), mock(BusinessApprovalWorkflowService.class), mock(CategoryAccessService.class));

        try (var session = mockStatic(LoginHelper.class)) {
            controller.list(null, null, null, null, 1, 20);
        }

        assertEquals(1, jdbc.calls.size());
        assertEquals(List.of("", "%%", "%%", "%%", "%%", "", "", "", "", "", "%%", false, 20, 0L), jdbc.calls.getFirst().args());
        assertEquals(true, jdbc.calls.getFirst().sql().contains("AND (?='' OR p.status=?)"));
        assertEquals(true, jdbc.calls.getFirst().sql().contains("AND (?='' OR p.calculation_status=?)"));
    }

    /** 记录列表查询 SQL 和绑定参数，不依赖数据库或计划测试数据。 / Capture the list query without database or plan fixtures. */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> calls = new ArrayList<>();

        /** 返回固定总数，便于断言控制器查询参数而不启动数据库。 / Return a fixed count so controller bindings can be asserted without a database. */
        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            return type.cast(0L);
        }

        /** 记录分页 SQL 与参数，模拟空列表结果。 / Capture paged SQL and parameters while returning an empty result set. */
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            calls.add(new SqlCall(sql, Arrays.asList(args.clone())));
            return List.of();
        }
    }

    private record SqlCall(String sql, List<Object> args) {
    }
}
