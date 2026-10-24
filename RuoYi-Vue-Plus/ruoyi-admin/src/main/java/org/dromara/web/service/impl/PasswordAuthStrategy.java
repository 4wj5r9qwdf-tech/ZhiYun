package org.dromara.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.LoginType;
import org.dromara.common.core.exception.user.CaptchaException;
import org.dromara.common.core.exception.user.CaptchaExpireException;
import org.dromara.common.core.exception.user.UserException;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ValidatorUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.api.model.PasswordLoginBody;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 密码认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("password" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class PasswordAuthStrategy implements IAuthStrategy {

    private final CaptchaProperties captchaProperties;
    private final SysLoginService loginService;
    private final SysUserMapper userMapper;

    /** 超管账号（不进数据库，走配置文件 .env 的 SUPER_ADMIN_*） */
    @Value("${SUPER_ADMIN_USERNAME:}")
    private String superAdminUsername;
    @Value("${SUPER_ADMIN_PASSWORD:}")
    private String superAdminPassword;

    /**
     * 执行账号密码登录，并按客户端配置生成访问令牌。
     *
     * @param body   登录请求体
     * @param client 当前客户端配置
     * @return 登录结果
     */
    @Override
    public LoginVo login(String body, SysClientVo client) {
        PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();

        // 1) 超管账号走配置文件（不进数据库）：命中且密码对=超管
        SysUserVo superUser = authenticateSuperAdmin(username, password);
        if (superUser != null) {
            return buildLoginVo(superUser, client);
        }

        // 2) 普通账号查库：username 同时支持 工号(6位数字) 或 手机号(11位)
        SysUserVo user = loadUserByNoOrPhone(username);
        // 默认密码校验（普通账号）
        loginService.checkLogin(LoginType.PASSWORD, username,
            () -> !BCrypt.checkpw(password, user.getPassword()));

        // 3) 普通账号生成 token
        return buildLoginVo(user, client);
    }

    /**
     * 构建登录结果（超管与普通账号共用）。
     */
    private LoginVo buildLoginVo(SysUserVo user, SysClientVo client) {
        LoginUser loginUser = loginService.buildLoginUser(user);
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = IAuthStrategy.buildLoginParameter(client);
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    /**
     * 超管账号认证：配置文件比对，命中返回超管身份（全厂全模块），否则返回 null。
     * 超管不进 sys_user 表，密码明文比对（配置文件直配，简化超管初始化）。
     */
    private SysUserVo authenticateSuperAdmin(String username, String password) {
        if (StringUtils.isBlank(superAdminUsername) || !superAdminUsername.equals(username)) {
            return null;
        }
        // 配置了超管账号但密码不匹配 → 拒绝（不回退到查库，防配置错误放行）
        if (StringUtils.isBlank(superAdminPassword) || !superAdminPassword.equals(password)) {
            log.warn("超管账号密码不匹配，拒绝登录：{}", username);
            throw new UserException("user.password.not.match");
        }
        log.info("超管账号登录成功（配置文件认证）：{}", username);
        // 复用 sys_user 表的 admin 行作为超管身份载体（角色=superadmin，全厂全模块）
        SysUserVo user = userMapper.lambda()
            .eq(SysUser::getUserName, username)
            .voOne();
        if (ObjectUtil.isNull(user)) {
            throw new UserException("user.not.exists", username);
        }
        return user;
    }

    /**
     * 按 工号(6位数字) 或 手机号(11位) 查询用户。
     * username 规则：6位纯数字=工号；11位纯数字=手机号；其他=按工号查（兼容旧数据）。
     */
    private SysUserVo loadUserByNoOrPhone(String username) {
        SysUserVo user;
        if (username != null && username.matches("^\\d{11}$")) {
            // 11位纯数字 = 手机号登录
            user = userMapper.lambda()
                .eq(SysUser::getPhoneNumber, username)
                .voOne();
        } else {
            // 6位数字（工号）或其他 = 按工号登录
            user = userMapper.lambda()
                .eq(SysUser::getUserName, username)
                .voOne();
        }
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", username);
            throw new UserException("user.not.exists", username);
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", username);
            throw new UserException("user.blocked", username);
        }
        return user;
    }

    /**
     * 校验图形验证码是否有效且匹配。
     *
     * @param username 用户名
     * @param code     用户输入的验证码
     * @param uuid     验证码缓存标识
     */
    private void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        RedisUtils.deleteObject(verifyKey);
        if (captcha == null) {
            loginService.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!StringUtils.equalsIgnoreCase(code, captcha)) {
            loginService.recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

}
