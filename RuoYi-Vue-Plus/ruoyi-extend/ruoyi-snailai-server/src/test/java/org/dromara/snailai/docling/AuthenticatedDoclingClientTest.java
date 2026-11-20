/*
 * 文件用途：实现织云内嵌智能体的AuthenticatedDoclingClientTest服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的AuthenticatedDoclingClientTest服务能力。
 * 设计思路：模型和文件服务只处理经 ERP 身份验证的请求；模型输出不能直接成为正式业务数据。
 * 实现方法：校验会话/员工/文件/配置后调用分析、存储或提议逻辑并返回结构化结果。
 * 关键约束：文件访问、敏感数据和知识发布遵守服务端权限与人工确认边界。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md
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
