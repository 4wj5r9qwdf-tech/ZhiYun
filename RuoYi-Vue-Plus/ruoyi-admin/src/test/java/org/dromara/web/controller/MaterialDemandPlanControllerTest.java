package org.dromara.web.controller;

import org.dromara.web.service.BusinessApprovalWorkflowService;
import org.dromara.web.service.CategoryAccessService;
import org.dromara.web.service.MaterialDemandPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/** 物料需求计划列表的可选筛选参数须兼容内部调用传入的 null。 / Verify null-safe optional filters on material demand plan lists. */
class MaterialDemandPlanControllerTest {

    /** MCP 等内部 Java 调用绕过 HTTP 默认值时，空参数仍须表示不过滤。 / Null direct-call arguments must mean no filter, just like omitted HTTP parameters. */
    @Test
    void listWithNullFiltersBuildsUnfilteredQuery() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        MaterialDemandPlanController controller = new MaterialDemandPlanController(
            jdbc, mock(MaterialDemandPlanService.class), mock(BusinessApprovalWorkflowService.class), mock(CategoryAccessService.class));

        controller.list(null, null, null, null);

        assertEquals(1, jdbc.calls.size());
        assertEquals(List.of("", "%%", "%%", "%%", "%%", "", "", "", "", "", "%%"), jdbc.calls.getFirst().args());
        assertEquals(true, jdbc.calls.getFirst().sql().contains("AND (?='' OR p.status=?)"));
        assertEquals(true, jdbc.calls.getFirst().sql().contains("AND (?='' OR p.calculation_status=?)"));
    }

    /** 记录列表查询 SQL 和绑定参数，不依赖数据库或计划测试数据。 / Capture the list query without database or plan fixtures. */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> calls = new ArrayList<>();

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            calls.add(new SqlCall(sql, Arrays.asList(args.clone())));
            return List.of();
        }
    }

    private record SqlCall(String sql, List<Object> args) {
    }
}
