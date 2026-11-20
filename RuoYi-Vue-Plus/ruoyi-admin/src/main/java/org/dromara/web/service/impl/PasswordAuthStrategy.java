/*
 * 文件用途：集中处理织云 ERP 的PasswordAuthStrategy业务规则，供控制器、事件或其他模块复用。
 * 文件版本：1.0.0
 * 功能目的：集中处理织云 ERP 的PasswordAuthStrategy业务规则，供控制器、事件或其他模块复用。
 * 设计思路：在服务层统一放置状态校验、事务、数据锁定与流程协调，避免 REST/MCP 各自实现。
 * 实现方法：读取业务来源，校验状态/版本/权限，执行领域变更并记录必要审计。
 * 关键约束：单据、审批、事务和审计状态必须保持一致。
 * 依赖与引用：Docs/规划/系统与流程/生产流程规划.md；Docs/API/API文档.md；Docs/规范/开发规范.md
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

        return buildLoginVo(authenticate(username, password), client);
    }

    /** 校验账号状态和密码，供 MCP 复用同一套账号认证。 */
    public SysUserVo authenticate(String username, String password) {
        // 1) 超管账号走配置文件（不进数据库）：命中且密码对=超管
        SysUserVo superUser = authenticateSuperAdmin(username, password);
        if (superUser != null) {
            return superUser;
        }

        // 2) 普通账号查库：username 同时支持 工号(6位数字) 或 手机号(11位)
        SysUserVo user = loadUserByNoOrPhone(username);
        // 默认密码校验（普通账号）
        loginService.checkLogin(LoginType.PASSWORD, username,
            () -> !BCrypt.checkpw(password, user.getPassword()));

        // 3) 普通账号生成 token
        return user;
    }

    /** MCP 后台账号复用登录失败限制，并拒绝已停用账号。 */
    public SysUserVo authenticateMcp(String username, String password) {
        if (StringUtils.isNotBlank(superAdminUsername) && superAdminUsername.equals(username)) {
            loginService.checkLogin(LoginType.PASSWORD, username,
                () -> StringUtils.isBlank(superAdminPassword) || !superAdminPassword.equals(password));
        }
        SysUserVo user = authenticate(username, password);
        if (SystemConstants.DISABLE.equals(user.getStatus())) {
            throw new UserException("user.blocked", username);
        }
        return user;
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
