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
