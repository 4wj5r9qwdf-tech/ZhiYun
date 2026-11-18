package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerOrderApprovalService {
    private final JdbcTemplate jdbc;
    private final BusinessApprovalWorkflowService workflows;
    private final MaterialDemandPlanService demandPlans;

    /** 订单版本、流程实例与首节点提交在同一事务内生效。 / Commit the order revision, workflow instance and first approval task as one transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void submit(Long id, int revision, String orderNo) {
        workflows.submit("customer_order", id, revision, orderNo);
    }

    /** 回调仅接受已关联的流程实例，按发起时的单据版本执行状态更新。 / Accept callbacks only for linked instances and apply state changes against the submitted revision. */
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
        if ("approved".equals(status)) demandPlans.ensureDraftForApprovedOrder(Long.valueOf(id.toString()));
    }
}
