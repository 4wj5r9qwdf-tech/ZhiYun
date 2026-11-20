/*
 * 文件用途：实现织云内嵌智能体的受认证保护的 Docling 文档转换客户端服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的受认证保护的 Docling 文档转换客户端服务能力。
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

import cn.hutool.http.GlobalInterceptor;
import cn.hutool.http.HttpRequest;
import com.aizuda.snail.ai.features.rag.docling.DoclingClient;
import com.aizuda.snail.ai.features.rag.docling.DoclingConvertOptions;
import com.aizuda.snail.ai.features.rag.docling.DoclingParseResult;
import com.aizuda.snail.ai.features.rag.docling.DoclingProperties;
import com.aizuda.snail.ai.features.rag.docling.DoclingResponseParser;

import java.io.File;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** 为框架原生 Docling 客户端补充请求认证，不改变其解析流程。 */
public final class AuthenticatedDoclingClient extends DoclingClient {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private final Scope scope;

    public AuthenticatedDoclingClient(DoclingProperties properties, DoclingResponseParser parser, Path keyFile) {
        super(properties, parser);
        try {
            String key = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            URI base = URI.create(properties.getUrl());
            if (key.isEmpty() || base.getHost() == null || base.getUserInfo() != null
                || !("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))) {
                throw new IllegalArgumentException("Docling 服务认证配置无效");
            }
            scope = new Scope(base, key);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取 Docling 服务密钥文件", exception);
        }
        if (REGISTERED.compareAndSet(false, true)) {
            // 拦截器只在本客户端同步调用期间生效，避免影响其他 HTTP 请求。
            // Restrict the global hook to this client's synchronous call scope.
            GlobalInterceptor.INSTANCE.addRequestInterceptor(AuthenticatedDoclingClient::authenticate);
        }
    }

    private static void authenticate(HttpRequest request) {
        // 普通请求不设置此线程局部上下文，因此全局拦截器对其他 HTTP 调用保持无操作。
        Scope active = SCOPE.get();
        if (active == null) return;
        URI target = URI.create(request.getUrl());
        URI base = active.base();
        String basePath = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        String path = target.getPath();
        boolean sameOrigin = base.getScheme().equalsIgnoreCase(target.getScheme())
            && base.getHost().equalsIgnoreCase(target.getHost()) && port(base) == port(target);
        boolean allowedPath = path != null && (path.equals(basePath + "/health")
            || path.equals(basePath + "/v1/convert/file/async")
            || path.matches(java.util.regex.Pattern.quote(basePath) + "/v1/(status/poll|result)/[A-Za-z0-9_-]+"));
        // 只允许配置源站及有限 API 路径，避免上游客户端重定向或异常 URL 将服务密钥带到其他主机。
        if (!sameOrigin || !allowedPath || target.getUserInfo() != null) {
            throw new IllegalStateException("Docling 请求目标不符合配置");
        }
        // 禁止系统代理和 HTTP 重定向，确保密钥只随已校验的原始目标请求发送。
        request.setProxy(Proxy.NO_PROXY).setFollowRedirects(false).header("X-Api-Key", active.key());
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    /** 服务健康检查也使用已配置的受限请求上下文，且只允许访问同源 health 路径。 */
    @Override
    public boolean isAvailable() {
        SCOPE.set(scope);
        try { return super.isAvailable(); } finally { SCOPE.remove(); }
    }

    /**
     * 在当前线程范围内调用上游文档转换实现；拦截器只会给配置源站的允许路径添加服务密钥。
     * finally 清除线程局部状态，防止线程池复用时把密钥带到后续无关请求。
     */
    @Override
    public DoclingParseResult convert(File file, DoclingConvertOptions options) {
        SCOPE.set(scope);
        try { return super.convert(file, options); } finally { SCOPE.remove(); }
    }

    private record Scope(URI base, String key) { }
}
