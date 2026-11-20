package org.dromara.web.controller;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.transaction.annotation.Transactional;
import org.dromara.system.service.ISysMessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.web.service.CategoryAccessService;
import org.dromara.web.service.MaterialDemandPlanService;
import cn.dev33.satoken.stp.StpUtil;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/** 客户订单与物料需求计划共用的审批详情、催审及单据穿透接口。 / Shared approval detail, reminder, and business-document resolution endpoints. */
@RestController
@RequestMapping("/approval/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderApprovalController {
    private static final String BUSINESS_DOCUMENTS = """
        SELECT 'customer_order'::text AS business_type,id,order_no AS document_no,document::text AS document,status
        FROM biz_customer_order
        UNION ALL
        SELECT 'material_demand_plan'::text AS business_type,id,plan_no AS document_no,document::text AS document,status
        FROM biz_material_demand_plan
        UNION ALL
        SELECT 'customer_order_unapprove'::text AS business_type,id,order_no AS document_no,document::text AS document,status
        FROM biz_customer_order
        UNION ALL
        SELECT 'material_demand_plan_unapprove'::text AS business_type,id,plan_no AS document_no,document::text AS document,status
        FROM biz_material_demand_plan
        """;
    private final JdbcTemplate jdbc;
    private final ISysMessageService messages;
    private final MaterialDemandPlanService plans;
    private final CategoryAccessService categoryAccess;

    /** 提供审批业务类型到单据快照的统一查询片段。 / Build the shared SQL CTE that resolves supported approval business types to document snapshots. */
    private String businessDocumentsCte(){return "WITH business_documents AS ("+BUSINESS_DOCUMENTS+") ";}

    /** 返回审批业务类型的中文显示名，用于提醒标题及日志动作。 / Resolve a Chinese business label for reminders and audit action names. */
    private String businessName(String type){return switch(type){case "material_demand_plan"->"物料需求计划";case "customer_order_unapprove"->"客户订单反审核";case "material_demand_plan_unapprove"->"物料需求计划反审核";default->"客户订单";};}

    /** 催审和记录在同一事务提交；相同实例限频并只通知仍有待办的人员。 / Commit the reminder and its audit record together; rate-limit each instance and notify only current pending assignees. */
    @SaCheckPermission("approval:initiated:list")
    @PostMapping("/initiated/{instanceId}/remind")
    @Transactional(rollbackFor=Exception.class)
    public R<Void> remind(@PathVariable Long instanceId) {
        Long userId=LoginHelper.getUserId();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,"approval-remind:"+instanceId);
        var instances=jdbc.queryForList(businessDocumentsCte()+"""
            SELECT a.business_type AS "businessType",d.document_no AS "documentNo"
            FROM sys_approval_business_instance a JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE a.instance_id=? AND a.business_type IN ('customer_order','material_demand_plan','customer_order_unapprove','material_demand_plan_unapprove') AND a.initiator_id=? AND a.state='pending' FOR UPDATE OF a
            """,instanceId,userId);
        if (instances.isEmpty()) throw new ServiceException("审批不存在、已结束或您不是发起人");
        Boolean recent=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_approval_reminder WHERE instance_id=? AND create_time > now()-interval '30 minutes')",Boolean.class,instanceId);
        if (Boolean.TRUE.equals(recent)) throw new ServiceException("30分钟内已催审，请稍后再试");
        var tasks=jdbc.queryForList("SELECT id::text AS id FROM flow_task WHERE instance_id=? AND del_flag='0' ORDER BY id FOR UPDATE",instanceId);
        var recipients=new java.util.LinkedHashSet<String>();
        for (var task:tasks) {
            String taskId=task.get("id").toString();
            var users=jdbc.queryForList("""
                SELECT DISTINCT u.user_id::text FROM flow_user f JOIN sys_user u ON u.user_id::text=f.processed_by
                WHERE f.associated::text=? AND f.type IN ('1','2','3') AND f.del_flag='0' AND u.status='0' AND u.del_flag='0'
                """,String.class,taskId);
            for (String recipient:users) if (recipients.add(recipient)) {
                String name=businessName(Objects.toString(instances.getFirst().get("businessType"),""));
                messages.storeUsers(List.of(Long.valueOf(recipient)),PushPayloadDTO.of(PushTypeEnum.MESSAGE,PushSourceEnum.WORKFLOW,
                    "您有一条"+name+"审批待处理，请及时办理。",null,"/approve/waiting?taskId="+taskId));
            }
        }
        if (recipients.isEmpty()) throw new ServiceException("当前没有可催审的有效待办，请刷新后重试");
        jdbc.update("INSERT INTO sys_approval_reminder(instance_id,initiator_id,recipient_count) VALUES (?,?,?)",instanceId,userId,recipients.size());
        BusinessAudit.record("催审"+businessName(Objects.toString(instances.getFirst().get("businessType"),"")),instances.getFirst().get("documentNo"),"单据编号",Map.of(),Map.of("催审人数",recipients.size()));
        return R.ok();
    }

    /** 发起人只可查看自己发起的审批实例，审批记录来自引擎。 / Limit initiated-approval history to the caller and read its timeline from the workflow engine. */
    @SaCheckPermission("approval:initiated:list")
    @GetMapping("/initiated")
    public R<List<Map<String,Object>>> initiated() {
        return R.ok(jdbc.queryForList(businessDocumentsCte()+"""
            SELECT a.instance_id::text AS "instanceId",a.business_id::text AS "businessId",a.business_type AS "businessType",d.document_no AS "orderNo",d.document_no AS "businessNo",a.state,a.create_time AS "createTime",(SELECT max(r.create_time) FROM sys_approval_reminder r WHERE r.instance_id=a.instance_id) AS "lastRemindedAt",
              COALESCE((SELECT string_agg(DISTINCT t.node_name,'、') FROM flow_task t WHERE t.instance_id=a.instance_id AND t.del_flag='0'),'') AS "currentNode",
              COALESCE((SELECT string_agg(DISTINCT u.nick_name,'、') FROM flow_task t JOIN flow_user fu ON fu.associated=t.id AND fu.type IN ('1','2','3') AND fu.del_flag='0' JOIN sys_user u ON u.user_id::text=fu.processed_by WHERE t.instance_id=a.instance_id AND t.del_flag='0'),'') AS "currentAssignees",
              COALESCE((SELECT r.reason FROM biz_document_reversal_request r WHERE r.instance_id=a.instance_id LIMIT 1),'') AS "reversalReason"
            FROM sys_approval_business_instance a JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE a.business_type IN ('customer_order','material_demand_plan','customer_order_unapprove','material_demand_plan_unapprove') AND a.initiator_id=? ORDER BY a.create_time DESC
            """, LoginHelper.getUserId()));
    }
    /** 读取发起人有权查看的审批进度和时间轴，并只返回已关联的业务单据摘要。 / Return the initiator-visible workflow timeline and linked business summary. */

    @SaCheckPermission("approval:initiated:list")
    @GetMapping("/initiated/{instanceId}")
    public R<Map<String,Object>> initiatedDetail(@PathVariable Long instanceId) {
        Boolean owns=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_approval_business_instance WHERE instance_id=? AND business_type IN ('customer_order','material_demand_plan','customer_order_unapprove','material_demand_plan_unapprove') AND initiator_id=?)",Boolean.class,instanceId,LoginHelper.getUserId());
        if (!Boolean.TRUE.equals(owns)) throw new ServiceException("审批不存在或无权查看");
        var history=jdbc.queryForList("""
            SELECT h.node_name AS "nodeName",COALESCE(u.nick_name,h.approver) AS approver,h.skip_type AS action,h.message,h.update_time AS "updateTime"
            FROM flow_his_task h LEFT JOIN sys_user u ON u.user_id::text=h.approver
            WHERE h.instance_id=? AND h.del_flag='0' AND h.flow_status <> 'copy' ORDER BY h.update_time,h.id
            """,instanceId);
        // 抄送关联指向历史任务的 task_id；仅展示已生成的记录，不推断接收人是否阅读。
        // Copy associations reference the historic task ID; a recorded copy does not prove that it was read.
        var copies=jdbc.queryForList("""
            SELECT h.id::text AS id, h.node_name AS "nodeName", h.update_time AS "sentAt",
                   f.processed_by AS "userId", COALESCE(u.nick_name, f.processed_by) AS "userName"
            FROM flow_his_task h JOIN flow_user f ON f.associated=h.task_id AND f.type='4' AND f.del_flag='0'
            LEFT JOIN sys_user u ON u.user_id::text=f.processed_by
            WHERE h.instance_id=? AND h.del_flag='0' AND h.flow_status='copy'
            ORDER BY h.update_time,h.id,f.processed_by
            """,instanceId);
        var business=jdbc.queryForMap(businessDocumentsCte()+"""
            SELECT a.business_type AS "businessType",a.business_id::text AS "businessId",d.document_no AS "businessNo"
            FROM sys_approval_business_instance a JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE a.instance_id=?
            """,instanceId);
        var reversal=jdbc.queryForList("""
            SELECT r.business_type AS "businessType",r.reason,r.state,r.result_message AS "resultMessage",
                   r.requested_by::text AS "requestedBy",COALESCE(u.nick_name,u.user_name) AS "requestedByName",
                   r.created_at AS "requestedAt",r.updated_at AS "updatedAt"
            FROM biz_document_reversal_request r LEFT JOIN sys_user u ON u.user_id=r.requested_by
            WHERE r.instance_id=?
            """,instanceId);
        if(!reversal.isEmpty())business.put("reversal",reversal.getFirst());
        return R.ok(Map.of("history",history,"copies",copies,"business",business));
    }

    /** 仅允许发起人穿透查看本人审批关联的业务单据，并复用单据自身的数据权限。 / Allow initiators to open only their linked business document and reuse its own data-scope checks. */
    @SaCheckPermission("approval:initiated:list")
    @GetMapping("/initiated/{instanceId}/document")
    public R<Map<String,Object>> initiatedDocument(@PathVariable Long instanceId) {
        var rows=jdbc.queryForList(businessDocumentsCte()+"""
            SELECT a.business_type AS "businessType",a.business_id::text AS "businessId",
                   d.document_no AS "orderNo",d.document,i.id::text AS "instanceId",
                   COALESCE((SELECT t.node_code FROM flow_task t WHERE t.instance_id=i.id AND t.del_flag='0' ORDER BY t.id LIMIT 1),'') AS "nodeCode",
                   COALESCE((SELECT t.node_name FROM flow_task t WHERE t.instance_id=i.id AND t.del_flag='0' ORDER BY t.id LIMIT 1),'') AS "nodeName"
            FROM sys_approval_business_instance a
            JOIN flow_instance i ON i.id=a.instance_id
            JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE a.instance_id=? AND a.initiator_id=?
              AND a.business_type IN ('customer_order','material_demand_plan','customer_order_unapprove','material_demand_plan_unapprove')
            """,instanceId,LoginHelper.getUserId());
        if(rows.isEmpty())throw new ServiceException("审批不存在或您不是发起人");
        return R.ok(enrichBusinessDocument(rows.getFirst()));
    }

    /** 仅当前待办办理人可通过任务入口查看对应订单。 / Allow only the current task assignee to open its linked order through the task endpoint. */
    @SaCheckPermission("approval:waiting:list")
    @GetMapping("/tasks/{taskId}")
    public R<Map<String, Object>> detail(@PathVariable Long taskId) {
        var orders = jdbc.queryForList(businessDocumentsCte()+"""
            SELECT d.document_no AS "orderNo",d.business_type AS "businessType",d.document,t.node_code AS "nodeCode",t.node_name AS "nodeName",i.id::text AS "instanceId",d.id::text AS "businessId"
            FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id
            JOIN sys_approval_business_instance a ON a.instance_id=i.id
            JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE t.id=? AND t.del_flag='0' AND i.del_flag='0' AND i.flow_status='waiting'
              AND a.business_type IN ('customer_order','material_demand_plan','customer_order_unapprove','material_demand_plan_unapprove') AND a.state='pending'
              AND ((a.business_type IN ('customer_order','material_demand_plan') AND d.status='submitted')
                OR (a.business_type IN ('customer_order_unapprove','material_demand_plan_unapprove') AND d.status='approved'))
              AND (? OR EXISTS (SELECT 1 FROM flow_user u WHERE u.associated=t.id
                  AND u.processed_by=? AND u.type IN ('1','2','3') AND u.del_flag='0'))
            """, taskId, LoginHelper.isSuperAdmin(), LoginHelper.getUserIdStr());
        if (orders.isEmpty()) throw new ServiceException("审批待办不存在或已处理");
        return R.ok(enrichBusinessDocument(orders.getFirst()));
    }

    /** 仅向当前办理人提供审批候选人员的编号和名称。 / Expose only candidate IDs and names to the current approver. */
    @SaCheckPermission("approval:waiting:handle")
    @GetMapping("/tasks/{taskId}/people")
    public R<List<Map<String,Object>>> taskPeople(@PathVariable Long taskId) {
        detail(taskId);
        return R.ok(jdbc.queryForList("""
            SELECT user_id::text AS value, nick_name || ' · ' || user_name AS label
            FROM sys_user WHERE status='0' AND del_flag='0' ORDER BY nick_name,user_id
            """));
    }

    /** 仅允许实际办理过该节点的人员查看已办订单与审批记录。 / Allow only the actual historic approver to inspect the associated order and timeline. */
    @SaCheckPermission("approval:finished:list")
    @GetMapping("/finished/{taskId}")
    public R<Map<String, Object>> finishedDetail(@PathVariable Long taskId) {
        var orders = jdbc.queryForList(businessDocumentsCte()+"""
            SELECT d.document_no AS "orderNo",d.business_type AS "businessType",d.document,h.node_code AS "nodeCode",h.node_name AS "nodeName",i.id::text AS "instanceId",d.id::text AS "businessId"
            FROM flow_his_task h JOIN flow_instance i ON i.id=h.instance_id
            JOIN sys_approval_business_instance a ON a.instance_id=i.id
            JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE h.id=? AND h.del_flag='0' AND h.node_type=1 AND h.flow_status <> 'copy' AND (? OR h.approver=?)
            """, taskId, LoginHelper.isSuperAdmin(), LoginHelper.getUserIdStr());
        if (orders.isEmpty()) throw new ServiceException("已办记录不存在或无权查看");
        return R.ok(enrichBusinessDocument(orders.getFirst()));
    }

    /** 仅向抄送记录的实际接收人展示订单及审批历史。 / Restrict copied order details to the recorded recipient. */
    @cn.dev33.satoken.annotation.SaCheckPermission("approval:copy:list")
    @GetMapping("/copies/{taskId}")
    public R<Map<String,Object>> copiedDetail(@PathVariable Long taskId) {
        var orders=jdbc.queryForList(businessDocumentsCte()+"""
            SELECT d.document_no AS "orderNo",d.business_type AS "businessType",d.document,h.node_name AS "nodeName",h.instance_id::text AS "instanceId",d.id::text AS "businessId"
            FROM flow_his_task h JOIN flow_user f ON f.associated=h.task_id AND f.type='4' AND f.del_flag='0'
            JOIN sys_approval_business_instance a ON a.instance_id=h.instance_id
            JOIN business_documents d ON d.id=a.business_id AND d.business_type=a.business_type
            WHERE h.task_id=? AND h.flow_status='copy' AND h.del_flag='0' AND f.processed_by=?
            """,taskId,LoginHelper.getUserIdStr());
        if (orders.isEmpty()) throw new ServiceException("抄送记录不存在或无权查看");
        return R.ok(enrichBusinessDocument(orders.getFirst()));
    }

    /** 根据历史记录定位本人收到的抄送。 / Resolve a copy history row only for its actual recipient. */
    @cn.dev33.satoken.annotation.SaCheckPermission("approval:copy:list")
    @GetMapping("/copies/by-history/{historyId}")
    public R<Map<String,Object>> copiedHistory(@PathVariable Long historyId) {
        var tasks=jdbc.queryForList("""
            SELECT h.task_id FROM flow_his_task h JOIN flow_user f ON f.associated=h.task_id
            WHERE h.id=? AND h.flow_status='copy' AND h.del_flag='0'
              AND f.type='4' AND f.del_flag='0' AND f.processed_by=?
            """,Long.class,historyId,LoginHelper.getUserIdStr());
        if (tasks.isEmpty()) throw new ServiceException("抄送记录不存在或无权查看");
        return copiedDetail(tasks.getFirst());
    }

    /** 按审批实例类型返回关联业务详情，并再次校验对应业务菜单和物料分类权限。 / Resolve the linked document by type and recheck its business and material-category permissions. */
    private Map<String,Object> enrichBusinessDocument(Map<String,Object> row) {
        String type=Objects.toString(row.get("businessType"),"");
        boolean reversal=type.endsWith("_unapprove");
        String documentType=reversal?type.substring(0,type.length()-"_unapprove".length()):type;
        if("customer_order".equals(documentType)) {
            StpUtil.checkPermission("order:customer-order:list");
            Long orderId=Long.valueOf(Objects.toString(row.get("businessId"),""));
            Long userId=LoginHelper.getUserId();
            Boolean accessible=jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM biz_customer_order
                 WHERE id=? AND (created_by=? OR sales_id=? OR follower_id=? OR ?))
                """,Boolean.class,orderId,userId,userId,userId,StpUtil.hasPermission("order:customer-order:review"));
            if(!Boolean.TRUE.equals(accessible))throw new ServiceException("客户订单不存在或超出访问范围");
            var order=enrichOrder(row);
            order.put("businessType",type);
            if(reversal)order.put("reversal",reversalInfo(Objects.toString(row.get("instanceId"),"")));
            return order;
        }
        if(!"material_demand_plan".equals(documentType))throw new ServiceException("该审批单据类型暂不支持查看");
        StpUtil.checkPermission("order:material-demand-plan:list");
        Long id=Long.valueOf(row.get("businessId").toString());
        var plan=plans.detail(id,false);
        assertPlanCategories(plan);
        plan.put("businessType",type);plan.put("businessId",row.get("businessId"));plan.put("orderNo",row.get("orderNo"));
        plan.put("instanceId",row.get("instanceId"));plan.put("nodeCode",row.get("nodeCode"));plan.put("nodeName",row.get("nodeName"));
        if(reversal)plan.put("reversal",reversalInfo(Objects.toString(row.get("instanceId"),"")));
        plan.put("history",jdbc.queryForList("""
            SELECT h.node_name AS "nodeName",COALESCE(u.nick_name,h.approver) AS approver,h.skip_type AS action,h.message,h.update_time AS "updateTime"
            FROM flow_his_task h LEFT JOIN sys_user u ON u.user_id::text=h.approver
            WHERE h.instance_id::text=? AND h.del_flag='0' ORDER BY h.update_time,h.id
            """,Objects.toString(row.get("instanceId"),"")));
        return plan;
    }

    /** 读取本次反审核申请原因和处理结果，供审批人及发起人查看。 / Load the current reversal request reason and result for approvers and initiators. */
    private Map<String,Object> reversalInfo(String instanceId) {
        var requests=jdbc.queryForList("""
            SELECT r.reason,r.state,r.result_message AS "resultMessage",r.requested_by::text AS "requestedBy",
                   COALESCE(u.nick_name,u.user_name) AS "requestedByName",r.created_at AS "requestedAt"
            FROM biz_document_reversal_request r LEFT JOIN sys_user u ON u.user_id=r.requested_by
            WHERE r.instance_id=?
            """,Long.valueOf(instanceId));
        if(requests.isEmpty())throw new ServiceException("反审核申请信息不存在或已失效");
        return requests.getFirst();
    }

    /** 审批详情沿用计划页的全单分类权限规则，避免待办/抄送穿透绕过人员数据范围。 / Apply the same all-or-nothing category scope used by plan pages so approval links cannot bypass employee data access. */
    private void assertPlanCategories(Map<String,Object> plan) {
        for(Object raw:(List<?>)plan.get("items")) {
            if(!(raw instanceof Map<?,?> item)) continue;
            if(item.get("categoryId")==null) {
                Long userId=LoginHelper.getUserId();
                if(userId==null||!LoginHelper.isSuperAdmin(userId)) throw new ServiceException("需求计划存在未保存物料分类的历史明细，无法确认您的数据范围，请联系管理员核对物料档案");
                continue;
            }
            Long category;
            try { category=Long.valueOf(item.get("categoryId").toString()); }
            catch(NumberFormatException error) { throw new ServiceException("需求计划的物料分类快照无效，请联系管理员"); }
            if(!categoryAccess.categoryAllowed(category)) throw new ServiceException("需求计划包含您无权查看的物料分类，请联系管理员调整人员分类权限");
        }
    }

    /** 补充订单明细和审批历史，供授权后的待办及已办详情复用。 / Enrich authorized order details with readable specifications and approval history. */
    private Map<String,Object> enrichOrder(Map<String,Object> order) {
        var document=JsonUtils.parseMap(order.get("document").toString());
        // 仅解析单据实际引用的色码，展示历史停用项时仍可辨认。
        if (document.get("pos") instanceof List<?> pos) for (Object po:pos) {
            if (!(po instanceof Map<?,?> p) || !(p.get("lines") instanceof List<?> lines)) continue;
            for (Object item:lines) if (item instanceof Map<?,?> raw) {
                @SuppressWarnings("unchecked") Map<String,Object> line=(Map<String,Object>) raw;
                populateSpecNames(line);
                if (line.get("configuration") instanceof List<?> configs) for (Object c:configs) if (c instanceof Map<?,?> child) {
                    @SuppressWarnings("unchecked") Map<String,Object> part=(Map<String,Object>) child;
                    populateSpecNames(part);
                    if (part.get("childId") != null) { var names=jdbc.queryForList("SELECT internal_code || ' · ' || style_name FROM rd_style WHERE style_id::text=?",String.class,part.get("childId").toString()); if(!names.isEmpty()) part.put("childLabel",names.getFirst()); }
                }
            }
        }
        order.put("document", document);
        order.put("history",jdbc.queryForList("""
            SELECT h.node_name AS "nodeName",COALESCE(u.nick_name,h.approver) AS approver,h.skip_type AS action,h.message,h.update_time AS "updateTime"
            FROM flow_his_task h LEFT JOIN sys_user u ON u.user_id::text=h.approver
            WHERE h.instance_id::text=? AND h.del_flag='0' ORDER BY h.update_time,h.id
            """,order.get("instanceId").toString()));
        return order;
    }

    /** 按当前可见档案补齐审批详情中的款式、颜色和尺码名称；无法解析时保留快照文本。 / Hydrate style, color and size labels in approval details while retaining saved text when references are unavailable. */
    private void populateSpecNames(Map<String,Object> line) {
        if (line.get("colorId") != null) { var names=jdbc.queryForList("SELECT color_name FROM base_color WHERE color_id::text=?",String.class,line.get("colorId").toString()); if(!names.isEmpty()) line.put("colorName",names.getFirst()); }
        if (line.get("sizeId") != null) { var names=jdbc.queryForList("SELECT name FROM sys_size WHERE id::text=?",String.class,line.get("sizeId").toString()); if(!names.isEmpty()) line.put("sizeName",names.getFirst()); }
    }
}
