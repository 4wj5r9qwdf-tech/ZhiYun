package org.dromara.web.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        controller = new CustomerController(jdbc);
    }

    /** 所有可选筛选均为空时查询全部客户，并继续正确分页。 / Empty optional filters return all customers with pagination. */
    @Test
    void listWithNullFiltersReturnsAllCustomers() {
        controller.list(null, null, null, null, 1, 20);

        assertEquals("SELECT count(*) FROM customer_master c WHERE 1=1", jdbc.calls.get(0).sql());
        assertEquals(List.of(), jdbc.calls.get(0).args());
        assertEquals(List.of(20, 0), jdbc.calls.get(1).args());
    }

    /** 仅按标签筛选时不应触发关键词空指针，且标签条件与分页参数顺序稳定。 / Tag-only filtering must not dereference a null keyword. */
    @Test
    void listWithOnlyTagFilterDoesNotRequireKeyword() {
        long tagId = 1793602859336L;

        controller.list(null, null, null, tagId, 1, 20);

        SqlCall count = jdbc.calls.get(0);
        assertEquals(true, count.sql().contains("r.tag_id=?"));
        assertEquals(false, count.sql().contains("customer_code ILIKE"));
        assertEquals(List.of(tagId), count.args());
        assertEquals(List.of(tagId, 20, 0), jdbc.calls.get(1).args());
    }

    /** 非空关键词仍按编码、名称、简称匹配，首尾空格会被去除。 / Non-empty keywords still search code, name and short name after trimming. */
    @Test
    void listWithKeywordTrimsAndSearchesAllNameFields() {
        controller.list("  Acme  ", null, null, null, 1, 20);

        SqlCall count = jdbc.calls.get(0);
        assertEquals(true, count.sql().contains("c.customer_code ILIKE ? OR c.customer_name ILIKE ? OR c.customer_short_name ILIKE ?"));
        assertEquals(List.of("%Acme%", "%Acme%", "%Acme%"), count.args());
    }

    /** 仅接受已知状态值；空值和未知值均不拼接状态条件。 / Ignore absent or unknown statuses and filter only recognized values. */
    @Test
    void listWithUnknownStatusDoesNotAddStatusFilter() {
        controller.list(" ", "invalid", null, null, 1, 20);

        SqlCall count = jdbc.calls.get(0);
        assertEquals("SELECT count(*) FROM customer_master c WHERE 1=1", count.sql());
        assertEquals(List.of(), count.args());
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
