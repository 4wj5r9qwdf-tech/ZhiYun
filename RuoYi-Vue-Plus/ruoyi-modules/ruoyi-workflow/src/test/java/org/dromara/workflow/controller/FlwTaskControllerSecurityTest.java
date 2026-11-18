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
