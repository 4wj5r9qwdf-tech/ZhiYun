/*
 * 文件用途：承载织云 ERP 对上游框架“IFlwCommonService”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“IFlwCommonService”能力的项目扩展或适配。
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
package org.dromara.workflow.service;

import org.dromara.common.core.enums.BusinessStatusEnum;
import org.dromara.system.api.domain.UserDTO;

import java.util.List;

/**
 * 通用 工作流服务
 *
 * @author LionLi
 */
public interface IFlwCommonService {

    /**
     * 发送消息
     *
     * @param flowName    流程定义名称
     * @param instId      实例id
     * @param messageType 消息类型
     * @param message     消息内容，为空则发送默认配置的消息内容
     */
    void sendMessage(String flowName, Long instId, List<String> messageType, String message);

    /**
     * 发送消息
     *
     * @param messageType 消息类型
     * @param message     消息内容
     * @param subject     邮件标题
     * @param userList    接收用户
     */
    void sendMessage(List<String> messageType, String message, String subject, List<UserDTO> userList);

    /**
     * 发送带跳转路径的消息。
     *
     * @param messageType 消息类型
     * @param message     消息内容
     * @param subject     邮件标题
     * @param userList    接收用户
     * @param path        前端跳转路径
     */
    void sendMessage(List<String> messageType, String message, String subject, List<UserDTO> userList, String path);

    /**
     * 发送工作流结果消息给指定用户。
     *
     * @param flowName    流程名称
     * @param status      流程状态
     * @param messageType 消息类型
     * @param userList    接收用户
     */
    void sendResultMessage(String flowName, BusinessStatusEnum status, List<String> messageType, List<UserDTO> userList, Long instanceId);

    /**
     * 申请人节点编码
     *
     * @param definitionId 流程定义id
     * @return 申请人节点编码
     */
    String applyNodeCode(Long definitionId);
}
