package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 将反审核流程结果事务化应用到来源单据，并保留原审批实例及申请原因。 / Apply reversal workflow outcomes transactionally while preserving the original approval history and request reason. */
@Service
@RequiredArgsConstructor
public class BusinessDocumentReversalApprovalService {
    private final JdbcTemplate jdbc;

    /** 只处理本服务登记的反审核流程；退回或终止时不改变原单已审核状态。 / Handle only registered reversal instances and leave the approved source document untouched on rejection or termination. */
    @EventListener
    @Transactional(rollbackFor=Exception.class)
    public void onProcess(ProcessEvent event) {
        String eventStatus=Objects.toString(event.getStatus(),"");
        if(!List.of("finish","back","cancel","invalid","termination").contains(eventStatus))return;
        var requests=jdbc.queryForList("""
            SELECT r.id,r.business_type AS "businessType",r.business_id AS "businessId",
                   r.business_revision AS "businessRevision",r.reason,a.business_key AS "businessKey"
            FROM biz_document_reversal_request r
            JOIN sys_approval_business_instance a ON a.instance_id=r.instance_id
            WHERE a.business_key=? AND a.instance_id=? AND a.flow_code=?
              AND a.business_type IN ('customer_order_unapprove','material_demand_plan_unapprove')
              AND a.state='pending' AND r.state='pending'
            FOR UPDATE OF r,a
            """,event.getBusinessId(),event.getInstanceId(),event.getFlowCode());
        if(requests.isEmpty())return;

        var request=requests.getFirst();
        String type=Objects.toString(request.get("businessType"),"");
        Long businessId=((Number)request.get("businessId")).longValue();
        int revision=((Number)request.get("businessRevision")).intValue();
        String reason=Objects.toString(request.get("reason"),"");
        String opinion=event.getParams()==null?"":Objects.toString(event.getParams().get("message"),"");
        boolean approved="finish".equals(eventStatus);
        String requestState=approved?"approved":"back".equals(eventStatus)?"rejected":"cancelled";
        String instanceState=approved?"approved":"back".equals(eventStatus)?"rejected":"cancelled";

        if(approved){
            if("customer_order_unapprove".equals(type))reverseCustomerOrder(businessId,revision,reason,opinion);
            else if("material_demand_plan_unapprove".equals(type))reverseMaterialDemandPlan(businessId,revision,reason,opinion);
            else throw new ServiceException("不支持的反审核业务类型");
        }
        int changed=jdbc.update("""
            UPDATE biz_document_reversal_request SET state=?,result_message=?,updated_at=now()
            WHERE id=? AND state='pending'
            """,requestState,opinion,request.get("id"));
        if(changed!=1)throw new ServiceException("反审核申请状态已变化，请刷新后重试");
        jdbc.update("UPDATE sys_approval_business_instance SET state=?,update_time=now() WHERE business_key=? AND state='pending'",
            instanceState,request.get("businessKey"));
        BusinessAudit.record("反审核审批结果",businessId,"业务单据",Map.of("申请原因",reason,"流程状态","pending"),
            Map.of("申请状态",requestState,"审批意见",opinion));
    }

    /** 订单反审核通过后退回可修改草稿；重新提交会启动新的订单审批实例。 / Return a reversed order to an editable draft so its next submission starts a fresh approval instance. */
    private void reverseCustomerOrder(Long id,int revision,String reason,String opinion) {
        var orders=jdbc.queryForList("""
            SELECT order_no,status,revision,document::text AS document,demand::text AS demand
            FROM biz_customer_order WHERE id=? FOR UPDATE
            """,id);
        if(orders.isEmpty())throw new ServiceException("客户订单不存在，无法完成反审核");
        var order=orders.getFirst();
        if(!"approved".equals(order.get("status"))||((Number)order.get("revision")).intValue()!=revision)
            throw new ServiceException("客户订单状态或版本已变化，反审核审批无法生效");
        var activePlans=jdbc.queryForList("SELECT plan_no FROM biz_material_demand_plan WHERE order_id=? AND status<>'cancelled' FOR UPDATE",id);
        if(!activePlans.isEmpty())throw new ServiceException("订单仍有关联的有效物料需求计划，请先完成计划反审核或取消");
        var document=JsonUtils.parseMap(Objects.toString(order.get("document"),"{}"));
        document.put("reviewNote",opinion);
        int changed=jdbc.update("""
            UPDATE biz_customer_order SET status='draft',document=?::jsonb,demand='[]'::jsonb,
                revision=revision+1,updated_at=now()
            WHERE id=? AND status='approved' AND revision=?
            """,JsonUtils.toJsonString(document),id,revision);
        if(changed!=1)throw new ServiceException("客户订单版本已变化，反审核没有生效");
        BusinessAudit.record("客户订单反审核通过",id,"订单编号",
            Map.of("订单号",Objects.toString(order.get("order_no"),""),"状态","approved"),
            Map.of("订单号",Objects.toString(order.get("order_no"),""),"状态","draft","反审核原因",reason,"审批意见",opinion));
    }

    /** 需求计划反审核通过后退回可编辑草稿；计划人员可调整或取消，历史明细与审批记录继续保留。 / Return the plan to an editable draft after reversal approval, retaining all demand lines and workflow history. */
    private void reverseMaterialDemandPlan(Long id,int revision,String reason,String opinion) {
        var plans=jdbc.queryForList("""
            SELECT plan_no,status,revision FROM biz_material_demand_plan WHERE id=? FOR UPDATE
            """,id);
        if(plans.isEmpty())throw new ServiceException("物料需求计划不存在，无法完成反审核");
        var plan=plans.getFirst();
        if(!"approved".equals(plan.get("status"))||((Number)plan.get("revision")).intValue()!=revision)
            throw new ServiceException("物料需求计划状态或版本已变化，反审核审批无法生效");
        int changed=jdbc.update("""
            UPDATE biz_material_demand_plan SET status='draft',cancel_reason=NULL,review_note=?,
                approved_by=NULL,approved_at=NULL,revision=revision+1,updated_by=?,updated_at=now()
            WHERE id=? AND status='approved' AND revision=?
            """,opinion,org.dromara.common.satoken.utils.LoginHelper.getUserId(),id,revision);
        if(changed!=1)throw new ServiceException("物料需求计划版本已变化，反审核没有生效");
        BusinessAudit.record("物料需求计划反审核通过",id,"计划单号",
            Map.of("计划单号",Objects.toString(plan.get("plan_no"),""),"状态","approved"),
            Map.of("计划单号",Objects.toString(plan.get("plan_no"),""),"状态","draft","反审核原因",reason,"审批意见",opinion));
    }
}
