/*
 * 文件用途：验证 ERP 内嵌 SnailAI 的写工具确认包装器。
 * 文件版本：1.0.0
 * 功能目的：保证写工具在员工确认前不会执行 MCP delegate，拒绝后也不执行。
 * 设计思路：运行实际 ToolCallback 包装器并通过会话审批事件模拟页面确认/拒绝。
 * 实现方法：核对待确认卡携带工具名和参数、验证员工身份绑定，并记录 delegate 调用次数。
 * 关键约束：超时、拒绝、错误员工和关闭会话均不得放行写工具。
 * 依赖与引用：ErpSnailAiToolRuntime.java；SnailAiChatSessionStore.java
 * 初始开发人员：Codex（智能体）
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-写工具确认执行护栏测试；验证确认前阻断与拒绝不执行
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（2 项实际确认包装器测试）
 * 测试范围与记录位置：ErpSnailAiToolRuntimeTest；Maven Surefire 报告
 */
package org.dromara.ai.snailai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ErpSnailAiToolRuntimeTest {

    @Test
    void writeDelegateWaitsForTheSameEmployeesOneTimeDecision() throws Exception {
        SnailAiChatSessionStore sessions = new SnailAiChatSessionStore(mock(SnailAiTraceService.class));
        SnailAiChatSession session = sessions.open(42L, "JY", "test-token", "client", Set.of(), "trace");
        AtomicInteger calls = new AtomicInteger();
        ToolCallback delegate = delegate(calls);
        var callback = new ErpSnailAiToolRuntime.ObservedToolCallback(delegate, session, sessions, true);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var invocation = executor.submit(() -> callback.call("{\"customerId\":\"90001\",\"name\":\"测试客户\"}"));
            Map<String, Object> card = session.approvals().next().block(Duration.ofSeconds(3));

            assertNotNull(card);
            assertEquals("customer_update", card.get("toolName"));
            assertEquals("{\"customerId\":\"90001\",\"name\":\"测试客户\"}", card.get("arguments"));
            assertEquals(0, calls.get(), "MCP delegate must remain untouched until approval");
            String approvalId = card.get("approvalId").toString();
            assertFalse(sessions.resolveApproval(approvalId, 99L, true), "another employee cannot approve this action");
            assertTrue(sessions.resolveApproval(approvalId, 42L, true));
            assertFalse(sessions.resolveApproval(approvalId, 42L, true), "approval is one-time");

            assertEquals("executed", invocation.get(3, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            sessions.close(session.getSid());
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedWriteReturnsWithoutCallingMcpDelegate() throws Exception {
        SnailAiChatSessionStore sessions = new SnailAiChatSessionStore(mock(SnailAiTraceService.class));
        SnailAiChatSession session = sessions.open(42L, "JY", "test-token", "client", Set.of(), "trace");
        AtomicInteger calls = new AtomicInteger();
        var callback = new ErpSnailAiToolRuntime.ObservedToolCallback(delegate(calls), session, sessions, true);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var invocation = executor.submit(() -> callback.call("{}"));
            Map<String, Object> card = session.approvals().next().block(Duration.ofSeconds(3));
            assertNotNull(card);
            assertTrue(sessions.resolveApproval(card.get("approvalId").toString(), 42L, false));

            assertTrue(invocation.get(3, TimeUnit.SECONDS).contains("操作未执行"));
            assertEquals(0, calls.get());
        } finally {
            sessions.close(session.getSid());
            executor.shutdownNow();
        }
    }

    private static ToolCallback delegate(AtomicInteger calls) {
        ToolDefinition definition = ToolDefinition.builder()
            .name("customer_update")
            .description("修改客户资料；调用前取得确认；需要 system:customer:edit")
            .inputSchema("{\"type\":\"object\"}")
            .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                calls.incrementAndGet();
                return "executed";
            }
        };
    }
}
