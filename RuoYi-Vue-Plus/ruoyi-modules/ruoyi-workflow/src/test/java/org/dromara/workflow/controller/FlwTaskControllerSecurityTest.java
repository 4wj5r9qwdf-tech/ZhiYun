/*
 * 文件用途：承载织云 ERP 对上游框架“FlwTaskControllerSecurityTest”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlwTaskControllerSecurityTest”能力的项目扩展或适配。
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
package org.dromara.workflow.controller;

import org.dromara.common.core.constant.HttpStatus;
import org.dromara.workflow.domain.bo.CompleteTaskBo;
import org.dromara.workflow.domain.bo.StartProcessBo;
import org.dromara.workflow.service.IFlwTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 审批 HTTP 入口不能接受客户端传入的内部越权控制参数。 / Verify that HTTP callers cannot forge internal workflow authorization variables. */
class FlwTaskControllerSecurityTest {
    private final IFlwTaskService service = mock(IFlwTaskService.class);
    private final FlwTaskController controller = new FlwTaskController(service);

    /** 完成任务时逐项拒绝客户端伪造的引擎内部变量，并确认服务层未执行。 / Reject forged engine variables on task completion and verify that the service is untouched. */
    @ParameterizedTest
    @ValueSource(strings={"ignore","ignoreDepute","ignoreCooperate","autoPass","initiator","initiatorOrgId","businessId","businessCode","submit"})
    void rejectsInternalVariables(String key) {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L);
        request.getVariables().put(key,true);
        assertEquals(HttpStatus.ERROR,controller.completeTask(request).getCode());
        verifyNoInteractions(service);
    }

    /** 发起流程时拒绝客户端注入审批身份、业务主键和自动通过标记。 / Reject forged identity, business-key and auto-pass variables when a process starts. */
    @ParameterizedTest
    @ValueSource(strings={"ignore","ignoreDepute","ignoreCooperate","autoPass","initiator","initiatorOrgId","businessId","businessCode","submit"})
    void rejectsInternalVariablesWhenStarting(String key) {
        StartProcessBo request=new StartProcessBo(); request.setFlowCode("test"); request.setBusinessId("1");
        request.getVariables().put(key,true);
        assertEquals(HttpStatus.ERROR,controller.startWorkFlow(request).getCode());
        verifyNoInteractions(service);
    }

    /** 发起人身份只能由服务端登录上下文确定。 / Ensure the initiator is derived from the authenticated server context. */
    @Test
    void rejectsForgedInitiator() {
        StartProcessBo request=new StartProcessBo(); request.setFlowCode("test"); request.setBusinessId("1"); request.setHandler("123");
        assertEquals(HttpStatus.ERROR,controller.startWorkFlow(request).getCode());
        verifyNoInteractions(service);
    }

    /** 办理人只能由当前待办和服务端上下文确定。 / Ensure a task handler cannot be impersonated by request data. */
    @Test
    void rejectsForgedHandler() {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L); request.setHandler("123");
        assertEquals(HttpStatus.ERROR,controller.completeTask(request).getCode());
        verifyNoInteractions(service);
    }

    /** 普通审批请求仍正常委托给工作流服务。 / Preserve normal task completion through the workflow service. */
    @Test
    void forwardsNormalTaskToWorkflow() {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L); request.setMessage("同意");
        when(service.completeTask(any())).thenReturn(true);
        assertEquals(HttpStatus.SUCCESS,controller.completeTask(request).getCode());
        verify(service).completeTask(request);
    }
}
