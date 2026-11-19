package org.dromara.common.security.config;

import jakarta.servlet.http.HttpServletRequest;

/** MCP 密码认证扩展，由应用提供用户认证及会话创建。 */
public interface McpPasswordAuthenticator {
    void authenticate(HttpServletRequest request);
}
