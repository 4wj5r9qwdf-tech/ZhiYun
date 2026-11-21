/*
 * 文件用途：验证 MCP Basic 认证头的认证方案与畸形输入校验。
 * 文件版本：1.0.0
 * 功能目的：防止其他 Authorization 方案或短格式认证头进入 Basic 凭据解析流程。
 * 设计思路：在 TLS 请求上下文中使用无交互依赖的模拟对象，确认无效头在调用账号、客户端服务前被拒绝。
 * 实现方法：参数化覆盖 Bearer、缺失空格的 Basic 和空 Basic 凭据，并断言返回统一格式错误。
 * 关键约束：不得访问密码认证、客户端查询或登录会话依赖。
 * 依赖与引用：RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/McpBasicAuthenticator.java；Docs/规范/开发规范.md
 * 初始开发人员：JY
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；增加认证方案边界测试
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：Maven 定向测试通过（3/3）
 * 测试范围与记录位置：Docs/功能任务/ZY-ERP-20261121-MCP认证与SSE解析缺陷修正.md
 */
package org.dromara.web.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.dromara.system.service.ISysClientService;
import org.dromara.web.service.SysLoginService;
import org.dromara.web.service.impl.PasswordAuthStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 无效认证头必须在验证员工密码、客户端或创建会话前被拒绝。 / Reject invalid schemes before checking credentials or creating a session. */
class McpBasicAuthenticatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"Bearer dXNlcjpwYXNz", "Basic", "Basic "})
    void rejectsUnsupportedSchemeAndMalformedCredentials(String authorization) {
        PasswordAuthStrategy passwords = mock(PasswordAuthStrategy.class);
        SysLoginService loginService = mock(SysLoginService.class);
        ISysClientService clients = mock(ISysClientService.class);
        McpBasicAuthenticator authenticator = new McpBasicAuthenticator(passwords, loginService, clients);
        ReflectionTestUtils.setField(authenticator, "enabled", true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isSecure()).thenReturn(true);
        when(request.getHeader("Authorization")).thenReturn(authorization);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> authenticator.authenticate(request)
        );

        assertEquals("MCP 认证信息格式错误", error.getMessage());
        verifyNoInteractions(passwords, loginService, clients);
    }
}
