/*
 * 文件用途：验证工作流办理人规则仍支持织云的组织标识。
 * 文件版本：1.0.0
 * 功能目的：防止 Warm-Flow 2.1 升级把 org: 存储标识退化为上游 dept:。
 * 设计思路：mock 系统服务，断言组织 ID 被展开为用户 ID 或用户候选。
 * 实现方法：验证 convertPermissions 与 ERP 配置候选解析的组织分支。
 * 依赖与引用：FlwTaskAssigneeServiceImpl；IFlwTaskAssigneeService；Docs/功能任务/ZY-ERP-20261124-RuoYi-Vue-Plus-6.1.0升级.md
 * 当前责任人：JY；本次修改：Codex（智能体），2026-11-24
 * 修改标记：ZY-ERP-20261124-RuoYi6.1.0；新增组织办理人回归
 */
package org.dromara.workflow.service.impl;

import org.dromara.system.api.OrgService;
import org.dromara.system.api.PostService;
import org.dromara.system.api.RoleService;
import org.dromara.system.api.TaskAssigneeService;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.workflow.service.IFlwSpelService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlwTaskAssigneeServiceImplTest {

    @Test
    void expandsOrganizationPermissionToUserIds() {
        UserService userService = mock(UserService.class);
        FlwTaskAssigneeServiceImpl service = new FlwTaskAssigneeServiceImpl(
            mock(TaskAssigneeService.class), userService, mock(OrgService.class),
            mock(RoleService.class), mock(PostService.class), mock(IFlwSpelService.class));
        when(userService.selectUserIdsByOrgIds(List.of(42L))).thenReturn(List.of(7001L, 7002L));

        assertEquals(List.of("7001", "7002"), service.convertPermissions(List.of("org:42")));
        verify(userService).selectUserIdsByOrgIds(List.of(42L));
    }

    @Test
    void resolvesOrganizationStorageIdForApprovalSetup() {
        UserService userService = mock(UserService.class);
        FlwTaskAssigneeServiceImpl service = new FlwTaskAssigneeServiceImpl(
            mock(TaskAssigneeService.class), userService, mock(OrgService.class),
            mock(RoleService.class), mock(PostService.class), mock(IFlwSpelService.class));
        UserDTO user = new UserDTO();
        user.setUserId(7001L);
        when(userService.selectUsersByOrgIds(List.of(42L))).thenReturn(List.of(user));

        assertEquals(List.of(user), service.fetchUsersByStorageIds("org:42"));
        verify(userService).selectUsersByOrgIds(List.of(42L));
    }
}
