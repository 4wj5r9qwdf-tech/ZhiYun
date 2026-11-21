/*
 * 文件用途：为 MCP 后台账号密码认证建立受控的 ERP 员工登录会话。
 * 文件版本：1.0.1
 * 功能目的：让被允许的 MCP 客户端使用专用员工账号认证，并沿用 ERP 现有身份和权限上下文。
 * 设计思路：认证默认关闭；开启后仅接受 HTTPS 或本机请求，并同时校验专用用户名、员工密码及启用的 password 客户端。
 * 实现方法：读取自定义 MCP 凭据头或标准 Basic 认证头，验证配置和客户端后创建 120 秒的 Sa-Token 会话。
 * 关键约束：Authorization 头必须明确使用 Basic 方案；不得记录密码或绕过 ERP 员工权限；认证失败必须拒绝请求。
 * 依赖与引用：Docs/Skills/MCP调用指南.md；Docs/AI/织云AI集成总体规划.md；Docs/规范/开发规范.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；校验Basic方案并拒绝畸形认证头
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：McpBasicAuthenticatorTest 定向测试通过（3 个畸形/非 Basic 输入场景）
 * 测试范围与记录位置：Docs/功能任务/ZY-ERP-20261121-MCP认证与SSE解析缺陷修正.md
 */
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.security.config.McpPasswordAuthenticator;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.dromara.web.service.impl.PasswordAuthStrategy;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** MCP 后台使用账号密码时，为本次请求建立独立短时身份。 */
@Component
@RequiredArgsConstructor
public class McpBasicAuthenticator implements McpPasswordAuthenticator {
    private final PasswordAuthStrategy passwords;
    private final SysLoginService loginService;
    private final ISysClientService clients;
    @Value("${zhiyun.mcp.basic-auth.enabled:false}")
    private boolean enabled;
    @Value("${zhiyun.mcp.basic-auth.username:}")
    private String allowedUsername;
    @Value("${zhiyun.mcp.basic-auth.client-id:}")
    private String configuredClientId;

    /**
     * 用 MCP 专用用户名/密码建立短时 ERP 登录会话，使后续 MCP 调用沿用现有员工身份和权限体系。
     * 仅在显式开启且请求通过 HTTPS 或来自本机时运行；客户端、员工密码和登录上下文都必须验证成功。
     */
    @Override
    public void authenticate(HttpServletRequest request) {
        // Basic 密码认证默认关闭；未由运维显式启用时不尝试任何降级认证。
        if (!enabled) {
            throw new IllegalArgumentException("MCP 密码认证尚未启用");
        }
        // Basic 凭证可逆，只接受 TLS 或本机连接。 / Basic credentials require TLS or loopback.
        String peer = request.getRemoteAddr();
        if (!request.isSecure() && !"127.0.0.1".equals(peer) && !"::1".equals(peer)
            && !"0:0:0:0:0:0:0:1".equals(peer)) {
            throw new IllegalArgumentException("MCP 密码认证要求 HTTPS 或本机连接");
        }
        // 同时兼容受控的自定义头和标准 Authorization: Basic；后者必须是可解析的 Base64 用户名:密码。
        String username = request.getHeader("X-MCP-Username");
        String password = request.getHeader("X-MCP-Password");
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            // Authorization scheme 按 HTTP 约定不区分大小写；不允许把 Bearer 等其他方案误当成 Basic 凭据切片。
            if (!authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
                throw new IllegalArgumentException("MCP 认证信息格式错误");
            }
            String encodedCredentials = authorization.substring(6).trim();
            if (encodedCredentials.isEmpty()) {
                throw new IllegalArgumentException("MCP 认证信息格式错误");
            }
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("MCP 认证信息格式错误");
            }
            int separator = decoded.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("MCP 认证信息格式错误");
            }
            username = decoded.substring(0, separator);
            password = decoded.substring(separator + 1);
        }
        if (username == null || username.isBlank() || password == null || password.isBlank() || password.length() > 1024) {
            throw new IllegalArgumentException("MCP 认证信息格式错误");
        }
        // MCP 专用用户名采用配置白名单，避免此入口接受任意 ERP 用户名进行密码探测。
        if (allowedUsername.isBlank() || !allowedUsername.equals(username)) {
            throw new IllegalArgumentException("MCP 账号认证失败");
        }
        String clientId = authorization == null ? configuredClientId : request.getHeader(LoginHelper.CLIENT_KEY);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("MCP 客户端标识缺失");
        }
        // 认证客户端必须存在、启用且明确允许 password 授权类型，不能只依赖客户端 ID 字符串。
        SysClientVo client = clients.queryByClientId(clientId);
        if (client == null || !SystemConstants.NORMAL.equals(client.getStatus())
            || client.getGrantTypeList() == null || !client.getGrantTypeList().contains("password")) {
            throw new IllegalArgumentException("MCP 客户端无效或不支持密码认证");
        }
        // 密码校验交给既有登录策略，成功后在会话中写入账号、组织及客户端上下文供数据权限复用。
        SysUserVo user = passwords.authenticateMcp(username, password);
        LoginUser loginUser = loginService.buildLoginUser(user);
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType("mcp-basic");
        SaLoginParameter model = IAuthStrategy.buildLoginParameter(client);
        model.setDeviceType("mcp-basic");
        model.setTimeout(120);
        model.setActiveTimeout(-1);
        model.setExtra(LoginHelper.USER_KEY, loginUser.getUserId());
        model.setExtra(LoginHelper.USER_NAME_KEY, loginUser.getUsername());
        model.setExtra(LoginHelper.ORG_KEY, loginUser.getOrgId());
        model.setExtra(LoginHelper.ORG_NAME_KEY, loginUser.getOrgName());
        model.setExtra(LoginHelper.ORG_CATEGORY_KEY, loginUser.getOrgCategory());
        String token = StpUtil.createLoginSession(loginUser.getLoginId(), model);
        StpUtil.getTokenSessionByToken(token).set(LoginHelper.LOGIN_USER_KEY, loginUser);
        StpUtil.setTokenValueToStorage(token);
        request.setAttribute(McpPasswordAuthenticator.class.getName(), clientId);
    }
}
