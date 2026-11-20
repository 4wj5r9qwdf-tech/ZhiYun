/*
 * 文件用途：验证内嵌智能体读取 ERP 会话文件时使用受签名保护的本地请求，且不向模型暴露授权凭据。
 * 文件版本：1.0.0
 * 功能目的：锁定文件分析工具的身份凭据只在 ERP 服务端使用，模型输入只包含业务参数与返回内容。
 * 设计思路：通过本地 HTTP 测试服务观察工具请求，检查签名头和请求体边界，不依赖外部服务。
 * 实现方法：构造临时签名密钥与本地服务，执行工具回调并断言授权头未进入模型可见参数。
 * 关键约束：测试仅覆盖工具调用契约，不代表已完成所有智能体运行时集成验收。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/ReadChatDocumentTool.java
 * 初始开发人员：JY（织云项目自研测试）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.ai.snailai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.support.ToolCallbacks;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReadChatDocumentToolTest {
    @TempDir Path dir;

    @Test
    void toolUsesSignedLocalRequestWithoutExposingAuthorizationToModel() throws Exception {
        Path key = dir.resolve("secret");
        Files.write(key, new byte[32]);
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        AtomicReference<String> signature = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/snail-ai/zhiyun/chat-document-tools/", exchange -> {
            signature.set(exchange.getRequestHeaders().getFirst("X-Chat-Document-Signature"));
            byte[] body = "{\"content\":\"订单数量 100\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/snail-ai/zhiyun/chat-document-tools/knowledge-draft", exchange -> {
            byte[] body = "{\"status\":\"PENDING\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String session = "zydoc_example";
            ReadChatDocumentTool tool = new ReadChatDocumentTool(session, "127.0.0.1", server.getAddress().getPort(), "snail-ai", key.toString());
            var callback = java.util.Arrays.stream(ToolCallbacks.from(tool)).filter(c -> c.getToolDefinition().name().equals("read_chat_document")).findFirst().orElseThrow();
            assertFalse(callback.getToolDefinition().inputSchema().contains("grant"));
            String result = callback.call("{\"fileId\":\"390bc4d9-70df-4c74-83c5-2cb0af5ae104\"}");
            assertTrue(result.contains("订单数量 100"));
            assertEquals(ChatDocumentSignature.sign(key, session), signature.get());
            assertFalse(result.contains(session));
            var draft = java.util.Arrays.stream(ToolCallbacks.from(tool)).filter(c -> c.getToolDefinition().name().equals("draft_knowledge_entry")).findFirst().orElseThrow();
            assertTrue(draft.call("{\"ragId\":3,\"title\":\"术语\",\"content\":\"内容\",\"source\":\"员工提供\"}").contains("PENDING"));
        } finally { server.stop(0); }
    }
}
