/*
 * 文件用途：承载织云 ERP 对上游框架“CompleteExecuteComponent”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“CompleteExecuteComponent”能力的项目扩展或适配。
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
package org.dromara.workflow.liteflow.complete;

import cn.hutool.core.convert.Convert;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.enums.BusinessStatusEnum;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.enums.TaskStatusEnum;
import org.dromara.workflow.domain.bo.CompleteTaskBo;
import org.dromara.workflow.domain.context.CompleteTaskContext;

import java.util.Map;

import static org.dromara.workflow.common.constant.FlowConstant.*;

/**
 * 构建办理参数并执行当前任务跳转。
 *
 * @author may
 */
@ConditionalOnEnable
@RequiredArgsConstructor
@LiteflowComponent("completeExecute")
public class CompleteExecuteComponent extends NodeComponent {

    private final TaskService taskService;

    @Override
    public void process() {
        CompleteTaskContext context = getContextBean(CompleteTaskContext.class);
        CompleteTaskBo completeTaskBo = context.getCompleteTaskBo();
        Map<String, Object> variables = completeTaskBo.getVariables();
        FlowParams flowParams = FlowParams.build()
            .handler(completeTaskBo.getHandler())
            .variable(variables)
            // 超管可介入他人的待办，办理人仍由当前登录账号记录。
            // Allow a super administrator to intervene while retaining the real operator.
            .ignore(org.dromara.common.satoken.utils.LoginHelper.isSuperAdmin() || Convert.toBool(variables.getOrDefault(VAR_IGNORE, false)))
            .ignoreDepute(Convert.toBool(variables.getOrDefault(VAR_IGNORE_DEPUTE, false)))
            .ignoreCooperate(Convert.toBool(variables.getOrDefault(VAR_IGNORE_COOPERATE, false)))
            .skipType(SkipType.PASS.getKey())
            .message(completeTaskBo.getMessage())
            .flowStatus(BusinessStatusEnum.WAITING.getStatus())
            .hisStatus(TaskStatusEnum.PASS.getStatus())
            .hisTaskExt(completeTaskBo.getFileId());

        context.setFlowParams(flowParams);
        context.setAutoPass(Convert.toBool(context.getInstance().getVariableMap().getOrDefault(AUTO_PASS, false)));
        taskService.skip(context.getFlowTask().getId(), flowParams);
    }

}
