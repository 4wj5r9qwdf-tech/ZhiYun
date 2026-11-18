package org.dromara.workflow.liteflow.operation;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.enums.TaskOperationEnum;
import org.dromara.workflow.domain.bo.TaskOperationBo;
import org.dromara.workflow.domain.context.TaskOperationContext;

import java.util.Collections;

/**
 * 准备任务操作参数。
 *
 * @author may
 */
@ConditionalOnEnable
@Slf4j
@LiteflowComponent("taskOpPrepare")
public class TaskOpPrepareComponent extends NodeComponent {

    /** 校验操作所需的人员编号并构造引擎参数。 / Validate the operation-specific recipients before building engine parameters. */
    @Override
    public void process() {
        TaskOperationContext context = getContextBean(TaskOperationContext.class);
        TaskOperationEnum op = TaskOperationEnum.getByCode(context.getTaskOperation());
        if (op == null) {
            log.error("Invalid operation type:{} ", context.getTaskOperation());
            throw new ServiceException("Invalid operation type " + context.getTaskOperation());
        }
        context.setOperation(op);

        TaskOperationBo bo = context.getTaskOperationBo();
        switch (op) {
            case DELEGATE_TASK, TRANSFER_TASK -> {
                if (bo.getUserId() == null || !bo.getUserId().matches("[0-9]{1,19}")) {
                    throw new ServiceException("请选择有效的委派或转办人员");
                }
            }
            case ADD_SIGNATURE, REDUCTION_SIGNATURE -> {
                if (bo.getUserIds() == null || bo.getUserIds().isEmpty() || bo.getUserIds().size() > 100
                    || bo.getUserIds().stream().anyMatch(id -> id == null || !id.matches("[0-9]{1,19}"))) {
                    throw new ServiceException("请选择有效的加签或减签人员");
                }
            }
        }

        FlowParams flowParams = FlowParams.build().message(bo.getMessage());
        if (LoginHelper.isSuperAdmin()) {
            flowParams.ignore(true);
        }
        switch (op) {
            case DELEGATE_TASK, TRANSFER_TASK -> flowParams.addHandlers(Collections.singletonList(bo.getUserId()));
            case ADD_SIGNATURE -> flowParams.addHandlers(bo.getUserIds());
            case REDUCTION_SIGNATURE -> flowParams.reductionHandlers(bo.getUserIds());
        }
        context.setFlowParams(flowParams);
    }

}
