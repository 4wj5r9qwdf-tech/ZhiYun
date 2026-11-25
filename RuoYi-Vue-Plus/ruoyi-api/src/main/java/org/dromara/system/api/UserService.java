/*
 * 文件用途：承载织云 ERP 对上游框架“UserService”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“UserService”能力的项目扩展或适配。
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
package org.dromara.system.api;

import org.dromara.system.api.domain.UserDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 通用 用户服务
 *
 * @author Lion Li
 */
public interface UserService {

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectUserNameById(Long userId);

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    String selectNicknameById(Long userId);

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userIds 用户ID 多个用逗号隔开
     * @return 用户昵称
     */
    String selectNicknameByIds(String userIds);

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    String selectPhonenumberById(Long userId);

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    String selectEmailById(Long userId);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户id
     * @return 用户列表
     */
    UserDTO selectById(Long userId);

    /**
     * 通过用户ID查询用户列表
     *
     * @param userIds 用户ids
     * @return 用户列表
     */
    List<UserDTO> selectListByIds(Collection<Long> userIds);

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds);

    /**
     * 通过角色ID查询用户
     *
     * @param roleIds 角色ids
     * @return 用户
     */
    List<UserDTO> selectUsersByRoleIds(Collection<Long> roleIds);

    /**
     * 通过部门ID查询用户
     *
     * @param orgIds 部门ids
     * @return 用户
     */
    List<UserDTO> selectUsersByOrgIds(Collection<Long> orgIds);

    /**
     * 通过组织 ID 查询正常状态的用户 ID。
     *
     * @param orgIds 组织 ID 集合
     * @return 用户 ID 集合
     */
    List<Long> selectUserIdsByOrgIds(Collection<Long> orgIds);

    /**
     * 通过岗位ID查询用户
     *
     * @param postIds 岗位ids
     * @return 用户
     */
    List<UserDTO> selectUsersByPostIds(Collection<Long> postIds);

    /**
     * 通过岗位ID查询用户ID
     *
     * @param postIds 岗位ids
     * @return 用户ids
     */
    List<Long> selectUserIdsByPostIds(Collection<Long> postIds);

    /**
     * 根据用户 ID 列表查询用户昵称映射关系
     *
     * @param userIds 用户 ID 列表
     * @return Map，其中 key 为用户 ID，value 为对应的用户昵称
     */
    Map<Long, String> selectUserNicksByIds(Collection<Long> userIds);

}
