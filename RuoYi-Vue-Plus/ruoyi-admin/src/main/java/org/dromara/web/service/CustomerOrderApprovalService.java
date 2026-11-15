package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.workflow.api.WorkflowService;
import org.dromara.workflow.api.domain.CompleteTaskDTO;
import org.dromara.workflow.api.domain.StartProcessDTO;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderApprovalService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<WorkflowService> workflows;

    /** 订单版本、流程实例与首节点提交在同一事务内生效。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void submit(Long id, int revision, String orderNo) {
        // 与绑定切换共用事务锁，确保每张单据在提交时确定审核方式。
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "approval-binding:customer_order");
        Boolean configured = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_approval_binding WHERE business_type='customer_order')", Boolean.class);
        if (!Boolean.TRUE.equals(configured)) return;
        WorkflowService workflow = workflows.getIfAvailable();
        if (workflow == null) throw new ServiceException("审批服务未启用");
        List<String> codes = jdbc.queryForList("""
            SELECT b.flow_code FROM sys_approval_binding b
            JOIN flow_definition d ON d.flow_code=b.flow_code
            WHERE b.business_type='customer_order' AND d.is_publish=1
              AND d.activity_status=1 AND d.del_flag='0'
            FOR SHARE OF b,d
            """, String.class);
        if (codes.size() != 1) throw new ServiceException("请在审批对象配置中为客户订单绑定有效的已发布流程");
        String key = UUID.randomUUID().toString();
        String code = codes.getFirst();
        jdbc.update("""
            INSERT INTO sys_approval_business_instance
                (business_key,business_type,business_id,business_revision,flow_code,state,initiator_id)
            VALUES (?,'customer_order',?,?,?,'starting',?)
            """, key, id, revision, code, LoginHelper.getUserId());
        var request = new StartProcessDTO();
        request.setBusinessId(key);
        request.setFlowCode(code);
        request.getBizExt().setBusinessTitle("客户订单 " + orderNo);
        request.getBizExt().setBusinessCode(orderNo);
        var started = workflow.startWorkFlow(request);
        Boolean applicant = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flow_task t JOIN flow_node n
                ON n.definition_id=t.definition_id AND n.node_code=t.node_code
                WHERE t.id=? AND n.permission_flag='${initiator}' AND n.del_flag='0')
            """, Boolean.class, started.taskId());
        if (!Boolean.TRUE.equals(applicant)) throw new ServiceException("流程首个办理节点必须设置为申请人");
        jdbc.update("UPDATE sys_approval_business_instance SET instance_id=?,state='pending',update_time=now() WHERE business_key=?",
            started.processInstanceId(), key);
        var complete = new CompleteTaskDTO();
        complete.setTaskId(started.taskId());
        complete.setMessageType(List.of("1"));
        complete.setMessage("提交客户订单");
        if (!workflow.completeTask(complete)) throw new ServiceException("订单提交审批失败");
        Boolean hasReviewer = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flow_task t JOIN flow_user u ON u.associated=t.id
                JOIN sys_user employee ON employee.user_id::text=u.processed_by
                WHERE t.instance_id=? AND t.del_flag='0' AND u.del_flag='0' AND u.type IN ('1','2','3')
                  AND employee.status='0' AND employee.del_flag='0')
            """, Boolean.class, started.processInstanceId());
        if (!Boolean.TRUE.equals(hasReviewer)) throw new ServiceException("流程未产生有效的审批待办，请核对首个审批节点和办理人");

    }

    /** 回调仅接受已关联的流程实例，按发起时的单据版本执行状态更新。 */
    @EventListener
    public void onProcess(ProcessEvent event) {
        String status = switch (event.getStatus() == null ? "" : event.getStatus()) {
            case "finish" -> "approved";
            case "back", "cancel", "invalid", "termination" -> "draft";
            default -> null;
        };
        if (status == null) return;
        var links = jdbc.queryForList("""
            SELECT business_id,business_revision FROM sys_approval_business_instance
            WHERE business_key=? AND instance_id=? AND flow_code=? AND business_type='customer_order' AND state='pending'
            """, event.getBusinessId(), event.getInstanceId(), event.getFlowCode());
        if (links.isEmpty()) return;
        var link = links.getFirst();
        Object id = link.get("business_id");
        int changed = jdbc.update("""
            UPDATE biz_customer_order SET status=?,revision=revision+1,updated_at=now(),
                document=jsonb_set(document,'{reviewNote}',to_jsonb(?::text),true)
            WHERE id=? AND revision=? AND status='submitted'
            """, status, event.getParams() == null ? "" : java.util.Objects.toString(event.getParams().get("message"), ""), id, link.get("business_revision"));
        if (changed != 1) throw new ServiceException("订单审批版本不一致，请重新加载");
        String state = "approved".equals(status) ? "approved" : "back".equals(event.getStatus()) ? "rejected" : "cancelled";
        jdbc.update("UPDATE sys_approval_business_instance SET state=?,update_time=now() WHERE business_key=?", state, event.getBusinessId());
        BusinessAudit.record("客户订单审批结果", id, "订单编号", Map.of("状态","submitted"), Map.of("状态",status));
    }
}
