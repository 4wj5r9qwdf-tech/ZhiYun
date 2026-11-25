/*
 * 文件用途：承载织云 ERP 对上游框架“WorkflowGlobalListener”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“WorkflowGlobalListener”能力的项目扩展或适配。
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

package org.dromara.workflow.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.enums.BusinessStatusEnum;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.system.api.UserService;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.spi.GlobalListener;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.constant.FlowConstant;
import org.dromara.workflow.domain.bo.FlowCopyBo;
import org.dromara.workflow.domain.vo.NodeExtVo;
import org.dromara.workflow.event.WorkflowCopyEvent;
import org.dromara.workflow.event.WorkflowResultMessageEvent;
import org.dromara.workflow.event.WorkflowTaskMessageEvent;
import org.dromara.workflow.handler.FlowProcessEventHandler;
import org.dromara.workflow.service.IFlwCommonService;
import org.dromara.workflow.service.IFlwInstanceService;
import org.dromara.workflow.service.IFlwNodeExtService;
import org.dromara.workflow.service.IFlwTaskService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流全局监听器，处理任务流转中的扩展变量、消息和事件发布。
 *
 * @author may
 */
@ConditionalOnEnable
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowGlobalListener implements GlobalListener {

    private final IFlwTaskService flwTaskService;
    private final IFlwInstanceService flwInstanceService;
    private final FlowProcessEventHandler flowProcessEventHandler;
    private final IFlwCommonService flwCommonService;
    private final IFlwNodeExtService nodeExtService;
    private final UserService userService;

    /**
     * 任务创建回调，当前预留扩展。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void create(ListenerVariable listenerVariable) {

    }

    /**
     * 任务开始办理时解析节点扩展配置。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void start(ListenerVariable listenerVariable) {
        String ext = listenerVariable.getNode().getExt();
        if (StringUtils.isNotBlank(ext)) {
            Map<String, Object> variable = listenerVariable.getVariable();
            if (CollUtil.isEmpty(variable)) {
                variable = new HashMap<>();
            }
            NodeExtVo nodeExt = nodeExtService.parseNodeExt(ext, variable);
            Set<String> copySettings = nodeExt.getCopySettings();
            if (CollUtil.isNotEmpty(copySettings)) {
                List<Long> userIds = StreamUtils.toList(copySettings, Convert::toLong);
                Map<Long, String> nickNameMap = userService.selectUserNicksByIds(userIds);
                List<FlowCopyBo> copyList = StreamUtils.toList(copySettings, value -> {
                    Long userId = Convert.toLong(value);
                    FlowCopyBo copy = new FlowCopyBo();
                    copy.setUserId(userId);
                    copy.setNickName(nickNameMap.getOrDefault(userId, StringUtils.EMPTY));
                    return copy;
                });
                // 节点默认抄送人只在办理请求未明确传入名单时生效；显式空名单用于取消默认抄送。
                variable.putIfAbsent(FlowConstant.FLOW_COPY_LIST, copyList);
            }
            if (CollUtil.isNotEmpty(nodeExt.getVariables())) {
                variable.putAll(nodeExt.getVariables());
            }
        }
    }

    /**
     * 任务分派时动态调整办理权限。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void assignment(ListenerVariable listenerVariable) {
        Map<String, Object> variable = listenerVariable.getVariable();
        List<Task> nextTasks = listenerVariable.getNextTasks();
        WorkflowContext workflowContext = listenerVariable.getContext();
        Definition definition = listenerVariable.getDefinition();
        Instance instance = listenerVariable.getInstance();
        String applyNodeCode = flwCommonService.applyNodeCode(definition.getId());
        String taskStatus = workflowContext != null ? workflowContext.getTaskStatus() : null;

        for (Task flowTask : nextTasks) {
            String nodeCode = flowTask.getNodeCode();

            // 处理办理或退回时指定办理人的情况
            if (TaskStatus.PASS.getKey().equals(taskStatus)) {
                processTaskPermission(variable, flowTask, taskStatus);
            } else if (TaskStatus.BACK.getKey().equals(taskStatus)) {
                processTaskPermission(variable, flowTask, taskStatus);
            }

            // 如果是申请节点，则把启动人添加到办理人
            if (nodeCode.equals(applyNodeCode) && StringUtils.isNotBlank(instance.getCreateBy())) {
                flowTask.setPermissionList(List.of(instance.getCreateBy()));
            }
        }
    }

    /**
     * 处理任务权限设置
     *
     * @param variable   变量集合
     * @param flowTask   流程任务
     * @param taskStatus 任务状态
     */
    private void processTaskPermission(Map<String, Object> variable, Task flowTask, String taskStatus) {
        String nodeKey = taskStatus + StringUtils.COLON + flowTask.getNodeCode();

        // 检查是否存在状态相关的变量
        if (!variable.containsKey(nodeKey)) {
            return;
        }

        // 获取用户ID字符串
        Object userIdsObj = variable.get(nodeKey);
        if (userIdsObj == null) {
            return;
        }

        String userIds = userIdsObj.toString();
        if (StringUtils.isBlank(userIds)) {
            return;
        }

        // 分割用户ID并设置权限列表
        List<String> userIdList = StringUtils.str2List(userIds, StringUtils.SEPARATOR, true, true);
        if (CollUtil.isNotEmpty(userIdList)) {
            flowTask.setPermissionList(userIdList);
            if (TaskStatus.PASS.getKey().equals(taskStatus)) {
                // 办理指定人变量只消费一次；驳回指定人变量需要保留给后续重复驳回。
                variable.remove(nodeKey);
                FlowEngine.insService().removeVariables(flowTask.getInstanceId(), nodeKey);
            }
        }
    }

    /**
     * 任务完成后发布流程事件并处理抄送和通知。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void finish(ListenerVariable listenerVariable) {
        Instance instance = listenerVariable.getInstance();
        Definition definition = listenerVariable.getDefinition();
        Task task = listenerVariable.getTask();
        List<Task> nextTasks = listenerVariable.getNextTasks();
        Map<String, Object> params = new HashMap<>();
        WorkflowContext workflowContext = listenerVariable.getContext();
        Map<String, Object> variable = new HashMap<>();
        if (ObjectUtil.isNotNull(workflowContext)) {
            // 历史任务扩展(通常为附件)
            params.put("hisTaskExt", workflowContext.getExt());
            // 办理人
            params.put("handler", workflowContext.getHandler());
            // 办理意见
            params.put("message", workflowContext.getMessage());
            variable = workflowContext.getVariables();
        }
        //申请人提交事件
        Boolean submit = MapUtil.getBool(variable, FlowConstant.SUBMIT);
        if (submit != null && submit) {
            String status = determineFlowStatus(instance);
            flowProcessEventHandler.processHandler(definition.getFlowCode(), instance, status, variable, true);
        } else {
            // 判断流程状态（发布：撤销，退回，作废，终止，已完成事件）
            String status = determineFlowStatus(instance);
            if (StringUtils.isNotBlank(status)) {
                flowProcessEventHandler.processHandler(definition.getFlowCode(), instance, status, params, false);
                notifyInitiatorIfNeeded(definition, instance, status, variable);
            }
            if (!BusinessStatusEnum.initialState(instance.getFlowStatus())) {
                if (task != null && CollUtil.isNotEmpty(nextTasks) && nextTasks.size() == 1
                    && flwCommonService.applyNodeCode(definition.getId()).equals(nextTasks.getFirst().getNodeCode())) {
                    // 如果为画线指定驳回 线条指定为驳回 驳回得节点为申请人节点 则修改流程状态为退回
                    flowProcessEventHandler.processHandler(definition.getFlowCode(), instance, BusinessStatusEnum.BACK.getStatus(), params, false);
                    notifyInitiatorIfNeeded(definition, instance, BusinessStatusEnum.BACK.getStatus(), variable);
                    // 修改流程实例状态
                    instance.setFlowStatus(BusinessStatusEnum.BACK.getStatus());
                    FlowEngine.insService().updateById(instance);
                }
            }
        }
        //发布任务事件
        if (CollUtil.isNotEmpty(nextTasks)) {
            for (Task nextTask : nextTasks) {
                flowProcessEventHandler.processTaskHandler(definition.getFlowCode(), instance, nextTask, params);
            }
        }
        if (ObjectUtil.isNull(workflowContext)) {
            return;
        }
        // 只有办理或者退回的时候才执行消息通知和抄送
        if (!isPassOrBack(workflowContext.getTaskStatus())) {
            return;
        }
        if (ObjectUtil.isNull(variable)) {
            return;
        }

        if (variable.containsKey(FlowConstant.FLOW_COPY_LIST)) {
            List<FlowCopyBo> flowCopyList = MapUtil.get(variable, FlowConstant.FLOW_COPY_LIST, new TypeReference<>() {
            });
            // 添加抄送人
            SpringUtils.context().publishEvent(new WorkflowCopyEvent(task, flowCopyList));
        }
        if (variable.containsKey(FlowConstant.MESSAGE_TYPE)) {
            List<String> messageType = MapUtil.get(variable, FlowConstant.MESSAGE_TYPE, new TypeReference<>() {
            });
            String notice = MapUtil.getStr(variable, FlowConstant.MESSAGE_NOTICE);
            // 退回到申请人时只保留“已退回”结果消息，避免再追加一条“新的待办”形成重复提醒。
            if (shouldSendTaskMessage(workflowContext, definition, nextTasks)) {
                SpringUtils.context().publishEvent(new WorkflowTaskMessageEvent(definition.getFlowName(), instance.getId(), messageType, notice));
            }
        }
        FlowEngine.insService().removeVariables(instance.getId(),
            FlowConstant.FLOW_COPY_LIST,
            FlowConstant.MESSAGE_TYPE,
            FlowConstant.MESSAGE_NOTICE,
            FlowConstant.SUBMIT
        );
    }

    /**
     * 判断是否需要发送后续待办消息。
     *
     * @param workflowContext 流程执行上下文
     * @param definition 流程定义
     * @param nextTasks  后续任务列表
     * @return 是否发送待办消息
     */
    private boolean shouldSendTaskMessage(WorkflowContext workflowContext, Definition definition, List<Task> nextTasks) {
        if (workflowContext == null || !TaskStatus.BACK.getKey().equals(workflowContext.getTaskStatus())) {
            return true;
        }
        if (CollUtil.isEmpty(nextTasks) || nextTasks.size() != 1) {
            return true;
        }
        // 只有“退回到申请人”场景需要拦截待办提醒，其余退回/流转仍然保留待办消息。
        String applyNodeCode = flwCommonService.applyNodeCode(definition.getId());
        return !StringUtils.equals(applyNodeCode, nextTasks.getFirst().getNodeCode());
    }

    /**
     * 在流程完成或退回时通知发起人。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param status     业务状态
     * @param variable   流程变量
     */
    private void notifyInitiatorIfNeeded(Definition definition, Instance instance, String status, Map<String, Object> variable) {
        if (!StringUtils.equalsAny(status, BusinessStatusEnum.FINISH.getStatus(), BusinessStatusEnum.BACK.getStatus())) {
            return;
        }
        if (StringUtils.isBlank(instance.getCreateBy())) {
            return;
        }
        // 已完成、已退回这类结果消息只发给发起人，不再混入处理人待办消息。
        List<String> messageType = null;
        if (MapUtil.isNotEmpty(variable) && variable.containsKey(FlowConstant.MESSAGE_TYPE)) {
            messageType = MapUtil.get(variable, FlowConstant.MESSAGE_TYPE, new TypeReference<>() {
            });
        }
        SpringUtils.context().publishEvent(new WorkflowResultMessageEvent(definition.getFlowName(), status, instance.getCreateBy(), instance.getId(), messageType));
    }

    /**
     * 任务收口是否为「通过」或「退回」，只有这两种结果才触发消息通知与抄送。
     *
     * @param taskStatus 任务状态编码
     * @return 是否为通过或退回
     */
    private boolean isPassOrBack(String taskStatus) {
        return TaskStatus.PASS.getKey().equals(taskStatus) || TaskStatus.BACK.getKey().equals(taskStatus);
    }

    /**
     * 根据流程实例确定最终状态
     *
     * @param instance 流程实例
     * @return 流程最终状态
     */
    private String determineFlowStatus(Instance instance) {
        String flowStatus = instance.getFlowStatus();
        if (StringUtils.isNotBlank(flowStatus) && BusinessStatusEnum.initialState(flowStatus)) {
            log.info("流程实例当前状态: {}", flowStatus);
            return flowStatus;
        } else {
            Long instanceId = instance.getId();
            if (flwTaskService.isTaskEnd(instanceId)) {
                String status = BusinessStatusEnum.FINISH.getStatus();
                // 更新流程状态为已完成
                flwInstanceService.updateStatus(instanceId, status);
                log.info("流程已结束，状态更新为: {}", status);
                return status;
            }
            return null;
        }
    }

}
