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
        if (!sameOrigin || !allowedPath || target.getUserInfo() != null) {
            throw new IllegalStateException("Docling 请求目标不符合配置");
        }
        request.setProxy(Proxy.NO_PROXY).setFollowRedirects(false).header("X-Api-Key", active.key());
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    @Override
    public boolean isAvailable() {
        SCOPE.set(scope);
        try { return super.isAvailable(); } finally { SCOPE.remove(); }
    }

    @Override
    public DoclingParseResult convert(File file, DoclingConvertOptions options) {
        SCOPE.set(scope);
        try { return super.convert(file, options); } finally { SCOPE.remove(); }
    }

    private record Scope(URI base, String key) { }
}
