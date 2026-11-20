/*
 * 文件用途：为织云 ERP 的CustomerControllerTest提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的CustomerControllerTest提供服务端 HTTP 接口。
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
        controller = new CustomerController(jdbc, org.mockito.Mockito.mock(org.dromara.web.service.CategoryAccessService.class));
    }

    /** 所有可选筛选均为空时查询全部客户，并继续正确分页。 / Empty optional filters return all customers with pagination. */
    @Test
    void listWithNullFiltersReturnsAllCustomers() {
        controller.list(null, null, null, null, null, null, 1, 20);

        assertEquals("SELECT count(*) FROM customer_master c WHERE 1=1", jdbc.calls.get(0).sql());
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

    /** 仅接受已知状态值；空值和未知值均不拼接状态条件。 / Ignore absent or unknown statuses and filter only recognized values. */
    @Test
    void listWithUnknownStatusDoesNotAddStatusFilter() {
        controller.list(" ", "invalid", null, null, null, null, 1, 20);

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
