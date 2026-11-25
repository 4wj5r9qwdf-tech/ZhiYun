/*
 * 文件用途：为 ERP 审批配置校验提供人员规则展开服务契约。
 * 文件版本：1.1.0
 * 功能目的：把人员、角色、组织和岗位标识展开为人员候选，供审批流程配置校验。
 * 设计思路：与 Warm-Flow 的 HandlerSelectService 分离，保留织云业务配置接口。
 * 实现方法：由 FlwTaskAssigneeServiceImpl 通过系统 UserService 解析；SpEL 只在流程运行时解析。
 * 依赖与引用：FlwTaskAssigneeServiceImpl；OrderApprovalSetupController；Docs/功能任务/ZY-ERP-20261124-RuoYi-Vue-Plus-6.1.0升级.md
 * 当前责任人：JY；本次修改：Codex（智能体），2026-11-24
 * 修改标记：ZY-ERP-20261124-RuoYi6.1.0；保留审批设置的人员候选校验接口
 */
package org.dromara.workflow.service;

import org.dromara.system.api.domain.UserDTO;

import java.util.List;

/** ERP 审批配置使用的办理人员解析服务。 */
public interface IFlwTaskAssigneeService {

    /**
     * 解析逗号分隔的存储标识。用户 ID 不带前缀，角色、组织和岗位分别使用 role:、org:、post:。
     * 动态 SpEL 在配置阶段不执行，应由流程运行时处理。
     *
     * @param storageIds 人员规则标识
     * @return 去重后的人员列表
     */
    List<UserDTO> fetchUsersByStorageIds(String storageIds);
}
