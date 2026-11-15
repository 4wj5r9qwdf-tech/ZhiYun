package org.dromara.web.controller;

import lombok.RequiredArgsConstructor;
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

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/approval/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderApprovalController {
    private final JdbcTemplate jdbc;
    private final ISysMessageService messages;

    /** 催审和记录在同一事务提交；相同实例限频并只通知仍有待办的人员。 */
    @PostMapping("/initiated/{instanceId}/remind")
    @Transactional(rollbackFor=Exception.class)
    public R<Void> remind(@PathVariable Long instanceId) {
        Long userId=LoginHelper.getUserId();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,"approval-remind:"+instanceId);
        var instances=jdbc.queryForList("""
            SELECT o.order_no FROM sys_approval_business_instance a JOIN biz_customer_order o ON o.id=a.business_id
            WHERE a.instance_id=? AND a.business_type='customer_order' AND a.initiator_id=? AND a.state='pending' FOR UPDATE OF a
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
                messages.storeUsers(List.of(Long.valueOf(recipient)),PushPayloadDTO.of(PushTypeEnum.MESSAGE,PushSourceEnum.WORKFLOW,
                    "您有一条客户订单审批待处理，请及时办理。",null,"/approve/waiting?taskId="+taskId));
            }
        }
        if (recipients.isEmpty()) throw new ServiceException("当前没有可催审的有效待办，请刷新后重试");
        jdbc.update("INSERT INTO sys_approval_reminder(instance_id,initiator_id,recipient_count) VALUES (?,?,?)",instanceId,userId,recipients.size());
        BusinessAudit.record("催审客户订单",instances.getFirst().get("order_no"),"订单编号",Map.of(),Map.of("催审人数",recipients.size()));
        return R.ok();
    }

    /** 发起人只可查看自己发起的审批实例，审批记录来自引擎。 */
    @GetMapping("/initiated")
    public R<List<Map<String,Object>>> initiated() {
        return R.ok(jdbc.queryForList("""
            SELECT a.instance_id::text AS "instanceId",a.business_id::text AS "businessId",a.business_type AS "businessType",o.order_no AS "orderNo",a.state,a.create_time AS "createTime",(SELECT max(r.create_time) FROM sys_approval_reminder r WHERE r.instance_id=a.instance_id) AS "lastRemindedAt",
              COALESCE((SELECT string_agg(DISTINCT t.node_name,'、') FROM flow_task t WHERE t.instance_id=a.instance_id AND t.del_flag='0'),'') AS "currentNode",
              COALESCE((SELECT string_agg(DISTINCT u.nick_name,'、') FROM flow_task t JOIN flow_user fu ON fu.associated=t.id AND fu.type IN ('1','2','3') AND fu.del_flag='0' JOIN sys_user u ON u.user_id::text=fu.processed_by WHERE t.instance_id=a.instance_id AND t.del_flag='0'),'') AS "currentAssignees"
            FROM sys_approval_business_instance a JOIN biz_customer_order o ON o.id=a.business_id
            WHERE a.business_type='customer_order' AND a.initiator_id=? ORDER BY a.create_time DESC
            """, LoginHelper.getUserId()));
    }

    @GetMapping("/initiated/{instanceId}")
    public R<Map<String,Object>> initiatedDetail(@PathVariable Long instanceId) {
        Boolean owns=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_approval_business_instance WHERE instance_id=? AND business_type='customer_order' AND initiator_id=?)",Boolean.class,instanceId,LoginHelper.getUserId());
        if (!Boolean.TRUE.equals(owns)) throw new ServiceException("审批不存在或无权查看");
        var history=jdbc.queryForList("""
            SELECT h.node_name AS "nodeName",COALESCE(u.nick_name,h.approver) AS approver,h.skip_type AS action,h.message,h.update_time AS "updateTime"
            FROM flow_his_task h LEFT JOIN sys_user u ON u.user_id::text=h.approver
            WHERE h.instance_id=? AND h.del_flag='0' ORDER BY h.update_time,h.id
            """,instanceId);
        return R.ok(Map.of("history",history));
    }

    /** 仅当前待办办理人可通过任务入口查看对应订单。 */
    @GetMapping("/tasks/{taskId}")
    public R<Map<String, Object>> detail(@PathVariable Long taskId) {
        var orders = jdbc.queryForList("""
            SELECT o.order_no AS "orderNo", o.document::text AS document, t.node_code AS "nodeCode", t.node_name AS "nodeName", i.id::text AS "instanceId",o.id::text AS "businessId"
            FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id
            JOIN sys_approval_business_instance a ON a.instance_id=i.id
            JOIN biz_customer_order o ON o.id=a.business_id
            WHERE t.id=? AND t.del_flag='0' AND i.del_flag='0' AND i.flow_status='waiting'
              AND a.business_type='customer_order' AND a.state='pending' AND o.status='submitted'
              AND EXISTS (SELECT 1 FROM flow_user u WHERE u.associated=t.id
                  AND u.processed_by=? AND u.type IN ('1','2','3') AND u.del_flag='0')
            """, taskId, LoginHelper.getUserIdStr());
        if (orders.isEmpty()) throw new ServiceException("客户订单待办不存在或已处理");
        var order = orders.getFirst();
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
        return R.ok(order);
    }

    private void populateSpecNames(Map<String,Object> line) {
        if (line.get("colorId") != null) { var names=jdbc.queryForList("SELECT color_name FROM base_color WHERE color_id::text=?",String.class,line.get("colorId").toString()); if(!names.isEmpty()) line.put("colorName",names.getFirst()); }
        if (line.get("sizeId") != null) { var names=jdbc.queryForList("SELECT name FROM sys_size WHERE id::text=?",String.class,line.get("sizeId").toString()); if(!names.isEmpty()) line.put("sizeName",names.getFirst()); }
    }
}
