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

    @Override
    public void authenticate(HttpServletRequest request) {
        if (!enabled) {
            throw new IllegalArgumentException("MCP 密码认证尚未启用");
        }
        // Basic 凭证可逆，只接受 TLS 或本机连接。 / Basic credentials require TLS or loopback.
        String peer = request.getRemoteAddr();
        if (!request.isSecure() && !"127.0.0.1".equals(peer) && !"::1".equals(peer)
            && !"0:0:0:0:0:0:0:1".equals(peer)) {
            throw new IllegalArgumentException("MCP 密码认证要求 HTTPS 或本机连接");
        }
        String username = request.getHeader("X-MCP-Username");
        String password = request.getHeader("X-MCP-Password");
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
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
        if (allowedUsername.isBlank() || !allowedUsername.equals(username)) {
            throw new IllegalArgumentException("MCP 账号认证失败");
        }
        String clientId = authorization == null ? configuredClientId : request.getHeader(LoginHelper.CLIENT_KEY);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("MCP 客户端标识缺失");
        }
        SysClientVo client = clients.queryByClientId(clientId);
        if (client == null || !SystemConstants.NORMAL.equals(client.getStatus())
            || client.getGrantTypeList() == null || !client.getGrantTypeList().contains("password")) {
            throw new IllegalArgumentException("MCP 客户端无效或不支持密码认证");
        }
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
