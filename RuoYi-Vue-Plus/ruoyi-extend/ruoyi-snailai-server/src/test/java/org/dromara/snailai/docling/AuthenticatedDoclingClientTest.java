package org.dromara.snailai.docling;

import cn.hutool.http.HttpRequest;
import com.aizuda.snail.ai.features.rag.docling.DoclingConvertOptions;
import com.aizuda.snail.ai.features.rag.docling.DoclingProperties;
import com.aizuda.snail.ai.features.rag.docling.DoclingResponseParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedDoclingClientTest {
    @TempDir Path temp;

    @Test
    void authenticatesOnlyScopedRequestsAndUsesUpstreamConversion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean missingAuth = new AtomicBoolean();
        AtomicBoolean unrelatedHadAuth = new AtomicBoolean();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestHeaders().getFirst("X-Api-Key");
            if (path.equals("/unrelated")) unrelatedHadAuth.set(key != null);
            else if (!"test-key".equals(key)) missingAuth.set(true);
            String body = switch (path) {
                case "/health", "/unrelated" -> "{}";
                case "/v1/convert/file/async" -> "{\"task_id\":\"task-1\"}";
                case "/v1/status/poll/task-1" -> "{\"task_status\":\"success\"}";
                case "/v1/result/task-1" -> "{\"document\":{\"md_content\":\"测试订单\"}}";
                default -> throw new IllegalStateException("Unexpected request " + path);
            };
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Path key = temp.resolve("key"); Files.writeString(key, "test-key");
            Path input = temp.resolve("input.md"); Files.writeString(input, "测试文件");
            DoclingProperties properties = new DoclingProperties();
            properties.setUrl(base); properties.setEnabled(true); properties.setPollIntervalMillis(1);
            AuthenticatedDoclingClient client = new AuthenticatedDoclingClient(properties, new DoclingResponseParser(), key);
            client.init();
            assertTrue(client.isAvailable());
            assertEquals("测试订单", client.convert(input.toFile(), DoclingConvertOptions.defaults()).getMarkdownContent());
            HttpRequest.get(base + "/unrelated").execute().close();
            assertFalse(missingAuth.get());
            assertFalse(unrelatedHadAuth.get());
        } finally { server.stop(0); }
    }
}
