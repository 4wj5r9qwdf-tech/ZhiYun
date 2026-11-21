/*
 * 文件用途：验证织云 ERP 客户列表对空筛选、人员分类范围和非法状态的查询契约。
 * 文件版本：1.0.0
 * 功能目的：防止客户列表的空筛选或分类授权拼接产生错误 SQL，并确认非法状态不会扩大查询范围。
 * 设计思路：以记录型 JdbcTemplate 隔离数据库，使用实际分类 SQL 拼接逻辑模拟“全部可见”授权。
 * 实现方法：直接调用控制器并断言 SQL、参数、分页参数和非法输入响应。
 * 关键约束：测试中的人员分类范围必须明确；未知状态应失败且不得访问数据库。
 * 依赖与引用：CustomerController；CategoryAccessService；Docs/API/API文档.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-昨日功能回归；更新客户分类范围模拟和非法状态断言
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：通过（4项测试及全量后端测试）
 * 测试范围与记录位置：客户列表空筛选、标签筛选、关键词筛选、人员分类 SQL 及非法状态拒绝；Maven Surefire 报告
 */
package org.dromara.web.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.dromara.web.service.CategoryAccessService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 客户列表直接调用路径（包括 MCP 调用）必须兼容空筛选参数。 / Verify null-safe filters for direct customer-list callers, including MCP. */
class CustomerControllerTest {

    private RecordingJdbcTemplate jdbc;
    private CustomerController controller;

    @BeforeEach
    void setUp() {
        jdbc = new RecordingJdbcTemplate();
        CategoryAccessService categories = new CategoryAccessService(jdbc) {
            @Override
            public Scope scope() {
                return new CategoryAccessService.Scope("ALL", java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
            }
        };
        controller = new CustomerController(jdbc, categories);
    }

    /** 所有可选筛选均为空时查询全部客户，并继续正确分页。 / Empty optional filters return all customers with pagination. */
    @Test
    void listWithNullFiltersReturnsAllCustomers() {
        controller.list(null, null, null, null, null, null, 1, 20);

        assertEquals("SELECT count(*) FROM customer_master c WHERE 1=1 AND TRUE", jdbc.calls.get(0).sql());
        assertEquals(List.of(), jdbc.calls.get(0).args());
        assertEquals(List.of(20, 0), jdbc.calls.get(1).args());
    }

    /** 仅按标签筛选时不应触发关键词空指针，且标签条件与分页参数顺序稳定。 / Tag-only filtering must not dereference a null keyword. */
    @Test
    void listWithOnlyTagFilterDoesNotRequireKeyword() {
        long tagId = 1793602859336L;

        controller.list(null, null, null, tagId, null, null, 1, 20);

        SqlCall count = jdbc.calls.get(0);
        assertEquals(true, count.sql().contains("r.tag_id=?"));
        assertEquals(false, count.sql().contains("customer_code ILIKE"));
        assertEquals(List.of(tagId), count.args());
        assertEquals(List.of(tagId, 20, 0), jdbc.calls.get(1).args());
    }

    /** 非空关键词仍按编码、名称、简称匹配，首尾空格会被去除。 / Non-empty keywords still search code, name and short name after trimming. */
    @Test
    void listWithKeywordTrimsAndSearchesAllNameFields() {
        controller.list("  Acme  ", null, null, null, null, null, 1, 20);

        SqlCall count = jdbc.calls.get(0);
        assertEquals(true, count.sql().contains("c.customer_code ILIKE ? OR c.customer_name ILIKE ? OR c.customer_short_name ILIKE ?"));
        assertEquals(List.of("%Acme%", "%Acme%", "%Acme%"), count.args());
    }

    /** 拒绝未知状态值且不访问数据库，避免非法筛选静默扩大结果范围。 / Reject unknown statuses without querying and widening the result set. */
    @Test
    void listWithUnknownStatusRejectsRequestWithoutQuery() {
        var response = controller.list(" ", "invalid", null, null, null, null, 1, 20);

        assertEquals("客户状态筛选只能是0（启用）或1（停用）", response.getMsg());
        assertEquals(List.of(), jdbc.calls);
    }

    /** 记录控制器实际交给 JDBC 的 SQL 与参数，隔离数据库依赖并覆盖调用参数。 / Capture JDBC SQL and parameters without requiring a database. */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> calls = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            calls.add(new SqlCall(sql, Arrays.asList(args.clone())));
            return requiredType.cast(2L);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            calls.add(new SqlCall(sql, Arrays.asList(args.clone())));
            return List.of();
        }
    }

    private record SqlCall(String sql, List<Object> args) {
    }
}
