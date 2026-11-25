/*
 * 文件用途：承载织云 ERP 对上游框架“SaTokenFunctionTest”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SaTokenFunctionTest”能力的项目扩展或适配。
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
package org.dromara.common.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.hutool.http.HttpStatus;
import cn.hutool.extra.spring.SpringUtil;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.core.dao.PlusSaTokenDao;
import org.dromara.common.satoken.core.service.SaPermissionImpl;
import org.dromara.common.satoken.handler.SaTokenExceptionHandler;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.PermissionService;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.system.api.model.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@DisplayName("common-satoken 功能单元测试")
class SaTokenFunctionTest {

    /**
     * 验证权限校验失败统一返回 HTTP 403 业务响应。
     */
    @Test
    @DisplayName("处理权限校验异常")
    void shouldHandlePermissionException() {
        SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user");

        R<Void> result = handler.handleNotAccessException(new NotPermissionException("system:user:list"), request);

        assertEquals(HttpStatus.HTTP_FORBIDDEN, result.getCode());
        assertEquals("没有访问权限，请联系管理员授权", result.getMsg());
    }

    /**
     * 验证不同未登录类型转换为对应的用户提示。
     */
    @Test
    @DisplayName("处理登录超时和被顶下线异常")
    void shouldHandleNotLoginExceptionByType() {
        SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");

        NotLoginException timeout = NotLoginException.newInstance("login", NotLoginException.TOKEN_TIMEOUT, "timeout", "token");
        NotLoginException replaced = NotLoginException.newInstance("login", NotLoginException.BE_REPLACED, "replaced", "token");

        assertEquals("登录已过期，请重新登录", ((R<?>) handler.handleNotLoginException(timeout, request).getBody()).getMsg());
        assertEquals("当前账号已在其他设备登录，您已被强制下线", ((R<?>) handler.handleNotLoginException(replaced, request).getBody()).getMsg());
    }

    @Test
    @DisplayName("SSE 登录失效返回无正文 401")
    void shouldRejectSseWithoutJsonConversion() {
        SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resource/message");
        request.setServletPath("/resource/message");
        NotLoginException expired = NotLoginException.newInstance("login", NotLoginException.TOKEN_TIMEOUT, "timeout", "token");

        var response = handler.handleNotLoginException(expired, request);

        assertEquals(HttpStatus.HTTP_UNAUTHORIZED, response.getStatusCode().value());
        assertEquals(null, response.getBody());
    }

    /**
     * 验证 Redis 毫秒 TTL 转换为 Sa-Token 秒 TTL 时包含精度补偿，并保留特殊负值。
     */
    @Test
    @DisplayName("转换 Sa-Token 过期时间")
    void shouldConvertRedisTimeoutToSeconds() {
        PlusSaTokenDao dao = new PlusSaTokenDao();

        assertEquals(2L, ((Long) ReflectionTestUtils.invokeMethod(dao, "toTimeoutSeconds", 1000L)).longValue());
        assertEquals(-1L, ((Long) ReflectionTestUtils.invokeMethod(dao, "toTimeoutSeconds", -1L)).longValue());
        assertEquals(-2L, ((Long) ReflectionTestUtils.invokeMethod(dao, "toTimeoutSeconds", -2L)).longValue());
    }

    /**
     * 验证当前登录对象的菜单和角色权限直接来自会话快照，并对空权限集合返回可修改空列表。
     * 历史冒号格式与当前分隔符格式的登录ID均应命中会话快照。
     */
    @Test
    @DisplayName("读取当前会话权限快照")
    void shouldReadPermissionsFromCurrentLoginUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType("sys_user");
        loginUser.setUserId(7L);
        loginUser.setMenuPermission(Set.of("system:user:list", "system:user:add"));
        loginUser.setRolePermission(Set.of());

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getLoginUser).thenReturn(loginUser);
            SaPermissionImpl permission = new SaPermissionImpl();

            List<String> menus = permission.getPermissionList("sys_user:7", "login");
            List<String> roles = permission.getRoleList("sys_user:7", "login");

            assertEquals(Set.of("system:user:list", "system:user:add"), Set.copyOf(menus));
            assertEquals(List.of(), roles);
            roles.add("temporary");

            assertEquals(Set.of("system:user:list", "system:user:add"),
                Set.copyOf(permission.getPermissionList("sys_user-7", "login")));
            assertEquals(List.of(), permission.getRoleList("sys_user-7", "login"));
        }
    }

    /**
     * 验证查询其他登录对象时按登录 ID 提取用户 ID 并委托权限服务，格式错误时立即失败。
     */
    @Test
    @DisplayName("通过权限服务查询其他用户权限")
    void shouldLoadRemotePermissionsAndRejectMalformedLoginId() {
        PermissionService service = mock(PermissionService.class);
        when(service.getMenuPermission(9L)).thenReturn(Set.of("system:org:list"));

        try (var login = mockStatic(LoginHelper.class);
             var spring = mockStatic(SpringUtil.class)) {
            login.when(LoginHelper::getLoginUser).thenReturn(null);
            spring.when(() -> SpringUtil.getBean(PermissionService.class)).thenReturn(service);
            SaPermissionImpl permission = new SaPermissionImpl();

            assertEquals(List.of("system:org:list"), permission.getPermissionList("sys_user:9", "login"));
            assertEquals(List.of("system:org:list"), permission.getPermissionList("sys_user-9", "login"));
            assertThrows(ServiceException.class, () -> permission.getPermissionList("invalid", "login"));
            verify(service, times(2)).getMenuPermission(9L);
        }
    }

    /**
     * 验证系统未提供权限服务时返回明确业务错误，而不是空权限导致静默拒绝。
     */
    @Test
    @DisplayName("缺少权限服务时明确失败")
    void shouldFailClearlyWhenPermissionServiceIsMissing() {
        try (var login = mockStatic(LoginHelper.class);
             var spring = mockStatic(SpringUtil.class)) {
            login.when(LoginHelper::getLoginUser).thenReturn(null);
            spring.when(() -> SpringUtil.getBean(PermissionService.class))
                .thenThrow(new IllegalStateException("missing bean"));

            ServiceException exception = assertThrows(ServiceException.class,
                () -> new SaPermissionImpl().getRoleList("sys_user:9", "login"));

            assertEquals("PermissionService 实现类不存在", exception.getMessage());
        }
    }
}
