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

/** 审批 HTTP 入口不能接受客户端传入的内部越权控制参数。 */
class FlwTaskControllerSecurityTest {
    private final IFlwTaskService service = mock(IFlwTaskService.class);
    private final FlwTaskController controller = new FlwTaskController(service);

    @ParameterizedTest
    @ValueSource(strings={"ignore","ignoreDepute","ignoreCooperate","autoPass","initiator","initiatorOrgId","businessId","businessCode","submit"})
    void rejectsInternalVariables(String key) {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L);
        request.getVariables().put(key,true);
        assertEquals(HttpStatus.ERROR,controller.completeTask(request).getCode());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings={"ignore","ignoreDepute","ignoreCooperate","autoPass","initiator","initiatorOrgId","businessId","businessCode","submit"})
    void rejectsInternalVariablesWhenStarting(String key) {
        StartProcessBo request=new StartProcessBo(); request.setFlowCode("test"); request.setBusinessId("1");
        request.getVariables().put(key,true);
        assertEquals(HttpStatus.ERROR,controller.startWorkFlow(request).getCode());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsForgedInitiator() {
        StartProcessBo request=new StartProcessBo(); request.setFlowCode("test"); request.setBusinessId("1"); request.setHandler("123");
        assertEquals(HttpStatus.ERROR,controller.startWorkFlow(request).getCode());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsForgedHandler() {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L); request.setHandler("123");
        assertEquals(HttpStatus.ERROR,controller.completeTask(request).getCode());
        verifyNoInteractions(service);
    }

    @Test
    void forwardsNormalTaskToWorkflow() {
        CompleteTaskBo request=new CompleteTaskBo(); request.setTaskId(1L); request.setMessage("同意");
        when(service.completeTask(any())).thenReturn(true);
        assertEquals(HttpStatus.SUCCESS,controller.completeTask(request).getCode());
        verify(service).completeTask(request);
    }
}
