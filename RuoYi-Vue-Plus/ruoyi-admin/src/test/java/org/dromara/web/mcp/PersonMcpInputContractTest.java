/*
 * 文件用途：验证人员 MCP 输入字段类型、空集合替换及嵌套扩展字段白名单。
 * 文件版本：1.0.0
 * 功能目的：防止人员 Map 入参静默接受错误类型或把null数组误解为保留。
 * 设计思路：直接覆盖 MCP 入口的纯校验逻辑，不连接数据库或修改人员数据。
 * 实现方法：检查人员创建必填字段、更新集合替换和日期/枚举/ID 类型。
 * 依赖与引用：PersonMcpTools.java；Docs/API/人员信息API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-人员MCP字段契约；覆盖敏感白名单与集合空值语义
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（3 项人员 MCP 输入契约测试）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonMcpInputContractTest {

    private final PersonMcpTools tools = new PersonMcpTools(null, null);

    @Test
    void createRequiresJobAndWorkFieldsAndRejectsSensitiveFields() {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("jobNo", "000123");
        create.put("name", "示例员工");
        create.put("ext", Map.of("hireDate", "2026-11-20", "empStatus", "试用"));
        assertDoesNotThrow(() -> tools.validateInput(create, true));

        create.put("password", "secret");
        assertThrows(ServiceException.class, () -> tools.requireBody(create, java.util.Set.of("jobNo", "name", "gender", "email", "phoneNumber", "orgId", "status", "remark", "postIds", "roleIds", "businessDuties", "ext")));
        create.remove("password");
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("hireDate", "2026-11-20");
        ext.put("empStatus", "试用");
        ext.put("address", "敏感住址");
        create.put("ext", ext);
        assertThrows(ServiceException.class, () -> tools.validateInput(create, true));
    }

    @Test
    void updateRequiresExplicitEmptyArraysToClearAssociations() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("postIds", List.of());
        update.put("roleIds", List.of());
        update.put("businessDuties", List.of());
        assertDoesNotThrow(() -> tools.validateInput(update, false));

        update.put("postIds", null);
        assertThrows(ServiceException.class, () -> tools.validateInput(update, false));
    }

    @Test
    void extensionDatesAndStringIdsAreValidatedBeforeControllerCall() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("hireDate", null);
        ext.put("groupId", "10001");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("ext", ext);
        assertDoesNotThrow(() -> tools.validateInput(update, false));
        assertDoesNotThrow(() -> tools.validateIds(update));

        ext.put("hireDate", "20-11-2026");
        assertThrows(ServiceException.class, () -> tools.validateInput(update, false));
        ext.put("hireDate", null);
        ext.put("groupId", 10001L);
        assertThrows(ServiceException.class, () -> tools.validateIds(update));
    }
}
