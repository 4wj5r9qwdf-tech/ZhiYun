/*
 * 文件用途：为织云 ERP 的MaterialDemandPlanControllerTest提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的MaterialDemandPlanControllerTest提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
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
import static org.mockito.Mockito.mockStatic;
import org.dromara.common.satoken.utils.LoginHelper;

/** 物料需求计划列表的可选筛选参数须兼容内部调用传入的 null。 / Verify null-safe optional filters on material demand plan lists. */
class MaterialDemandPlanControllerTest {

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
