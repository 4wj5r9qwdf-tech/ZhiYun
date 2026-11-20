/*
 * 文件用途：将客户订单审核/反审核接入流程审批，并维护订单状态和流程实例关联。
 * 文件版本：1.0.0
 * 功能目的：将客户订单审核/反审核接入流程审批，并维护订单状态和流程实例关联。
 * 设计思路：审批配置、业务单据状态和流程实例引用分层记录，回调须校验来源对象及当前状态。
 * 实现方法：校验订单与审批绑定后创建流程实例；在事务回调中按已知状态推进订单并写审计。
 * 关键约束：不允许过期审批实例覆盖更新后的订单；反审核须遵守下游计划和生产任务约束。
 * 依赖与引用：Docs/规划/系统与流程/生产流程规划.md；sql/审批业务实例关联_20261114.sql；sql/业务单据反审核审批_20261119.sql
 * 初始开发人员：JY（织云项目自研内容）
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
