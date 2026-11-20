/*
 * 文件用途：承载织云 ERP 对上游框架“LoginHelper”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“LoginHelper”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
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
package org.dromara.common.satoken.utils;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ip.AddressUtils;
import org.dromara.system.api.model.LoginUser;


/**
 * 登录鉴权助手
 * <p>
 * user_type 为 用户类型 同一个用户表 可以有多种用户类型 例如 pc,app
 * deivce 为 设备类型 同一个用户类型 可以有 多种设备类型 例如 web,ios
 * 可以组成 用户类型与设备类型多对多的 权限灵活控制
 * <p>
 * 多用户体系 针对 多种用户类型 但权限控制不一致
 * 可以组成 多用户类型表与多设备类型 分别控制权限
 *
 * @author Lion Li
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginHelper {

    public static final String LOGIN_USER_KEY = "loginUser";
    public static final String USER_KEY = "userId";
    public static final String USER_NAME_KEY = "userName";
    public static final String ORG_KEY = "orgId";
    public static final String ORG_NAME_KEY = "orgName";
    public static final String ORG_CATEGORY_KEY = "orgCategory";
    public static final String CLIENT_KEY = "clientid";
    public static final String CLIENT_ACCESS_PATH_KEY = "clientAccessPath";
    public static final String CLIENT_IP_WHITELIST_KEY = "clientIpWhitelist";

    /**
     * 登录系统 基于 设备类型
     * 针对相同用户体系不同设备
     *
     * @param loginUser 登录用户信息
     * @param model     配置参数
     */
    public static void login(LoginUser loginUser, SaLoginParameter model) {
        model = ObjectUtil.defaultIfNull(model, new SaLoginParameter());
        fillRequestContext(loginUser, model);
        StpUtil.login(loginUser.getLoginId(),
            model.setExtra(USER_KEY, loginUser.getUserId())
                .setExtra(USER_NAME_KEY, loginUser.getUsername())
                .setExtra(ORG_KEY, loginUser.getOrgId())
                .setExtra(ORG_NAME_KEY, loginUser.getOrgName())
                .setExtra(ORG_CATEGORY_KEY, loginUser.getOrgCategory())
        );
        StpUtil.getTokenSession().set(LOGIN_USER_KEY, loginUser);
    }

    /**
     * 在登录时补充当前请求上下文，避免登录态中的终端信息缺失。
     *
     * @param loginUser 登录用户
     * @param model     登录参数
     */
    private static void fillRequestContext(LoginUser loginUser, SaLoginParameter model) {
        HttpServletRequest request = ServletUtils.getRequest();
        if (ObjectUtil.isNull(request)) {
            return;
        }
        String ip = ServletUtils.getClientIP(request);
        if (StringUtils.isBlank(loginUser.getIpaddr())) {
            loginUser.setIpaddr(ip);
        }
        if (StringUtils.isBlank(loginUser.getLoginLocation()) && StringUtils.isNotBlank(ip)) {
            loginUser.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        }
        UserAgent userAgent = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (StringUtils.isBlank(loginUser.getBrowser())) {
            loginUser.setBrowser(userAgent.getBrowser().getName());
        }
        if (StringUtils.isBlank(loginUser.getOs())) {
            loginUser.setOs(userAgent.getOs().getName());
        }
        if (StringUtils.isBlank(loginUser.getDeviceType()) && StringUtils.isNotBlank(model.getDeviceType())) {
            loginUser.setDeviceType(model.getDeviceType());
        }
    }

    /**
     * 获取当前 Token 对应的登录用户信息。
     *
     * @return 登录用户
     */
    public static <T extends LoginUser> T getLoginUser() {
        try {
            return getLoginUser(StpUtil.getTokenSession());
        } catch (NotLoginException e) {
            return null;
        }
    }

    /**
     * 根据指定 Token 获取登录用户信息。
     *
     * @param token Token
     * @return 登录用户
     */
    public static <T extends LoginUser> T getLoginUser(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        SaSession session;
        try {
            session = StpUtil.getTokenSessionByToken(token);
        } catch (NotLoginException e) {
            return null;
        }
        return getLoginUser(session);
    }

    /**
     * 从会话中读取登录用户。
     *
     * @param session 登录会话
     * @return 登录用户
     */
    @SuppressWarnings("unchecked")
    private static <T extends LoginUser> T getLoginUser(SaSession session) {
        if (ObjectUtil.isNull(session)) {
            return null;
        }
        return (T) session.get(LOGIN_USER_KEY);
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        return Convert.toLong(getExtra(USER_KEY));
    }

    /**
     * 获取当前登录用户 ID 字符串。
     *
     * @return 用户 ID 字符串
     */
    public static String getUserIdStr() {
        return Convert.toStr(getExtra(USER_KEY));
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名
     */
    public static String getUsername() {
        return Convert.toStr(getExtra(USER_NAME_KEY));
    }

    /**
     * 获取当前登录用户部门 ID。
     *
     * @return 部门 ID
     */
    public static Long getOrgId() {
        return Convert.toLong(getExtra(ORG_KEY));
    }

    /**
     * 获取当前登录用户部门名称。
     *
     * @return 部门名称
     */
    public static String getOrgName() {
        return Convert.toStr(getExtra(ORG_NAME_KEY));
    }

    /**
     * 获取当前登录用户部门类别编码。
     *
     * @return 部门类别编码
     */
    public static String getOrgCategory() {
        return Convert.toStr(getExtra(ORG_CATEGORY_KEY));
    }

    /**
     * 获取当前 Token 的扩展信息
     *
     * @param key 键值
     * @return 对应的扩展数据
     */
    private static Object getExtra(String key) {
        try {
            return StpUtil.getExtra(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户类型。
     *
     * @return 用户类型
     */
    public static UserType getUserType() {
        String loginType = StpUtil.getLoginIdAsString();
        return UserType.getUserType(loginType);
    }

    /**
     * 是否为超级管理员
     *
     * @param userId 用户ID
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_USER_ID.equals(userId);
    }

    /**
     * 是否为超级管理员
     *
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin() {
        return isSuperAdmin(getUserId());
    }

    /**
     * 检查当前用户是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

}
