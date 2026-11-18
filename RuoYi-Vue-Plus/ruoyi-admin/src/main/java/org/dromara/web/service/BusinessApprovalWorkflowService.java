package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.workflow.api.WorkflowService;
import org.dromara.workflow.api.domain.CompleteTaskDTO;
import org.dromara.workflow.api.domain.StartProcessDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 通过共享审批绑定启动已发布流程并建立业务关联。 / Start a published workflow from the shared approval binding and link it to a business document. */
@Service
@RequiredArgsConstructor
public class BusinessApprovalWorkflowService {
    private static final Map<String,String> BUSINESS_TYPES = Map.of(
        "customer_order", "客户订单", "material_demand_plan", "物料需求计划");

    private final JdbcTemplate jdbc;
    private final ObjectProvider<WorkflowService> workflows;

    /** 绑定流程时启动 Warm-Flow 并完成申请人节点；未绑定时返回 false，由业务单据按审核权限处理。 / Start Warm-Flow and complete its initiator node when a binding exists; otherwise let the document use its direct-review permission. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long submit(String businessType, Long businessId, int revision, String documentNo) {
        String typeName = BUSINESS_TYPES.get(businessType);
        if (typeName == null) throw new ServiceException("该业务单据尚未接入审批流程");

        // 绑定修改和单据提交使用同一事务锁，避免提交瞬间使用未确认的流程配置。
        // Serialize binding changes with submission so a document never starts against an indeterminate workflow.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "approval-binding:" + businessType);
        List<String> codes = jdbc.queryForList("""
            SELECT flow_code FROM sys_approval_binding
            WHERE business_type=? FOR SHARE
            """, String.class, businessType);
        if (codes.isEmpty()) return null;
        if (codes.size() != 1) throw new ServiceException("审批对象流程绑定异常，请联系管理员");

        WorkflowService workflow = workflows.getIfAvailable();
        if (workflow == null) throw new ServiceException("审批服务未启用");
        String flowCode = codes.getFirst();
        Boolean active = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flow_definition
                WHERE flow_code=? AND is_publish=1 AND activity_status=1 AND del_flag='0')
            """, Boolean.class, flowCode);
        if (!Boolean.TRUE.equals(active)) throw new ServiceException("绑定的审批流程未发布或已停用，请重新配置");

        String businessKey = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO sys_approval_business_instance
                (business_key,business_type,business_id,business_revision,flow_code,state,initiator_id)
            VALUES (?,?,?,?,?,'starting',?)
            """, businessKey, businessType, businessId, revision, flowCode, LoginHelper.getUserId());

        var start = new StartProcessDTO();
        start.setBusinessId(businessKey);
        start.setFlowCode(flowCode);
        start.getBizExt().setBusinessTitle(typeName + " " + documentNo);
        start.getBizExt().setBusinessCode(documentNo);
        var started = workflow.startWorkFlow(start);

        Boolean applicantNode = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flow_task t JOIN flow_node n
                ON n.definition_id=t.definition_id AND n.node_code=t.node_code
                WHERE t.id=? AND n.permission_flag='${initiator}' AND n.del_flag='0')
            """, Boolean.class, started.taskId());
        if (!Boolean.TRUE.equals(applicantNode)) throw new ServiceException("流程首个办理节点必须设置为申请人");

        jdbc.update("""
            UPDATE sys_approval_business_instance
            SET instance_id=?,state='pending',update_time=now() WHERE business_key=?
            """, started.processInstanceId(), businessKey);
        var complete = new CompleteTaskDTO();
        complete.setTaskId(started.taskId());
        complete.setMessageType(List.of("1"));
        complete.setMessage("提交" + typeName);
        if (!workflow.completeTask(complete)) throw new ServiceException(typeName + "提交审批失败");

        Boolean hasReviewer = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flow_task t JOIN flow_user u ON u.associated=t.id
                JOIN sys_user employee ON employee.user_id::text=u.processed_by
                WHERE t.instance_id=? AND t.del_flag='0' AND u.del_flag='0' AND u.type IN ('1','2','3')
                  AND employee.status='0' AND employee.del_flag='0')
            """, Boolean.class, started.processInstanceId());
        if (!Boolean.TRUE.equals(hasReviewer)) throw new ServiceException("流程未产生有效的审批待办，请核对审批节点和办理人");
        return started.processInstanceId();
    }
}
