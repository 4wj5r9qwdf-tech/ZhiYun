package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** 创建、同步和查询订单派生的物料需求计划。 / Create, synchronize and read material demand plans derived from customer orders. */
@Service
@RequiredArgsConstructor
public class MaterialDemandPlanService {
    private static final TypeReference<List<Map<String,Object>>> MAP_LIST = new TypeReference<>() {};
    private final JdbcTemplate jdbc;

    /** 审批回调只更新发起时锁定修订号的计划；旧实例不能覆盖后续编辑。 / Apply workflow callbacks only to the submitted revision so stale instances cannot overwrite later edits. */
    @EventListener
    @Transactional(rollbackFor=Exception.class)
    public void onWorkflowResult(ProcessEvent event){
        String target=switch(event.getStatus()==null?"":event.getStatus()){
            case "finish"->"approved";
            case "back","cancel","invalid","termination"->"draft";
            default->null;
        };
        if(target==null)return;
        var links=jdbc.queryForList("""
            SELECT business_id,business_revision FROM sys_approval_business_instance
            WHERE business_key=? AND instance_id=? AND flow_code=? AND business_type='material_demand_plan' AND state='pending'
            """,event.getBusinessId(),event.getInstanceId(),event.getFlowCode());
        if(links.isEmpty())return;
        var link=links.getFirst();Object id=link.get("business_id");
        String opinion=event.getParams()==null?"":Objects.toString(event.getParams().get("message"),"");
        int changed=jdbc.update("""
            UPDATE biz_material_demand_plan SET status=?,revision=revision+1,review_note=?,
                approved_at=CASE WHEN ?='approved' THEN now() ELSE NULL END,updated_at=now()
            WHERE id=? AND revision=? AND status='submitted'
            """,target,opinion,target,id,link.get("business_revision"));
        if(changed!=1)throw new ServiceException("物料需求计划审批版本不一致，请重新加载");
        String linkState="approved".equals(target)?"approved":"back".equals(event.getStatus())?"rejected":"cancelled";
        jdbc.update("UPDATE sys_approval_business_instance SET state=?,update_time=now() WHERE business_key=?",linkState,event.getBusinessId());
        BusinessAudit.record("物料需求计划审批结果",id,"计划单号",Map.of("状态","submitted"),Map.of("状态",target,"审批意见",opinion));
    }

    /** 客户订单审核通过后幂等生成草稿；尚无完整算料时保留待算料计划头。 / Idempotently create a draft after order approval and keep a pending header when no complete demand snapshot exists. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long ensureDraftForApprovedOrder(Long orderId) {
        var order = order(orderId, true);
        if (!"approved".equals(order.get("status"))) throw new ServiceException("只有已审核订单可以生成物料需求计划");
        assertOrderNotReversing(orderId);
        var existing = jdbc.queryForList("SELECT id FROM biz_material_demand_plan WHERE order_id=? AND status<>'cancelled' FOR UPDATE", Long.class, orderId);
        if (!existing.isEmpty()) return existing.getFirst();
        return create(order);
    }

    /** 订单审核后保存新算料快照时同步计划；有人工调整或已流转的计划不允许静默覆盖。 / Synchronize a newly saved order demand snapshot unless the plan was adjusted or progressed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long synchronizeApprovedOrder(Long orderId) {
        var order = order(orderId, true);
        if (!"approved".equals(order.get("status"))) throw new ServiceException("只有已审核订单可以同步物料需求计划");
        assertOrderNotReversing(orderId);
        var active = jdbc.queryForList("SELECT id,status FROM biz_material_demand_plan WHERE order_id=? AND status<>'cancelled' FOR UPDATE", orderId);
        if (active.isEmpty()) return create(order);
        var plan = active.getFirst();
        Long planId = ((Number)plan.get("id")).longValue();
        if (!"draft".equals(plan.get("status"))) throw new ServiceException("需求计划已提交或审核，不能被订单重新算料覆盖");
        Boolean adjusted = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM biz_material_demand_plan_adjustment WHERE plan_id=?)", Boolean.class, planId);
        if (Boolean.TRUE.equals(adjusted)) throw new ServiceException("需求计划已有人工作量调整；请先取消旧计划，再重新算料并生成新计划，避免覆盖调整记录");

        var document = snapshot(order);
        var source = demands(order.get("demand"));
        String calculationStatus = calculationStatus(source);
        String calculationMessage = calculationMessage(source);
        List<Map<String,Object>> items = List.of();
        int previousLineCount=activeItemsCount(planId);
        if ("ready".equals(calculationStatus)) {
            try {
                items = buildItems(source, document);
                calculationMessage = "";
            } catch (ServiceException error) {
                items = List.of();
                calculationStatus = "incomplete";
                calculationMessage = error.getMessage();
            }
        }
        replaceItems(planId, items);
        jdbc.update("""
            UPDATE biz_material_demand_plan SET order_revision=?,source_order_no=?,customer_id=?,document=?::jsonb,
                calculation_status=?,calculation_message=?,revision=revision+1,updated_by=?,updated_at=now()
            WHERE id=? AND status='draft'
            """, order.get("revision"), order.get("order_no"), order.get("customer_id"),
            JsonUtils.toJsonString(document), calculationStatus, calculationMessage, LoginHelper.getUserId(), planId);
        BusinessAudit.record("同步物料需求计划算料", planId, "计划单号",
            Map.of("来源订单",Objects.toString(order.get("order_no"),""),"需求行数",previousLineCount),
            Map.of("来源订单",Objects.toString(order.get("order_no"),""),"需求行数",items.size(),"算料状态",calculationStatus));
        return planId;
    }

    /** 保存草稿明细的计划量并追加前后值调整历史。 / Save draft planned quantities and append immutable before-and-after adjustment history. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String,Object> saveQuantities(Long planId, int revision, List<QuantityChange> changes, String reason) {
        var before = detail(planId, true);
        if (!"draft".equals(before.get("status"))) throw new ServiceException("只有草稿需求计划可以调整数量");
        if (!"ready".equals(before.get("calculationStatus"))) throw new ServiceException("请先在来源订单完成完整算料，再调整需求计划");
        if (!Objects.equals(revision, before.get("revision"))) throw new ServiceException("计划版本已变化，请重新加载后再保存");
        if (changes == null || changes.isEmpty()) return before;
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 500) throw new ServiceException("请填写计划数量调整原因（最多500字）");

        var existing = new LinkedHashMap<Long,Map<String,Object>>();
        for (Map<String,Object> row : listOfMaps(before.get("items"))) {
            existing.put(Long.valueOf(row.get("itemId").toString()),row);
        }
        var seen = new HashSet<Long>();
        var changed = new ArrayList<Map<String,Object>>();
        for (QuantityChange change : changes) {
            if (change == null || change.itemId() == null || change.plannedQuantity() == null || !seen.add(change.itemId()))
                throw new ServiceException("需求计划明细参数重复或不完整，请刷新后重试");
            var row = existing.get(change.itemId());
            if (row == null) throw new ServiceException("需求明细已变化，请重新加载");
            // numeric(24,8) 仅保留 16 位整数；去掉末尾零后校验数值能否无损存储。
            // numeric(24,8) allows 16 integer digits; ignore trailing zeros before checking exact storage.
            BigDecimal next = change.plannedQuantity().stripTrailingZeros();
            long integerDigits = (long) next.precision() - next.scale();
            if (next.signum() < 0 || next.scale() > 8 || integerDigits > 16) {
                throw new ServiceException("计划数量必须为0或正数，整数最多16位，小数最多8位");
            }
            BigDecimal previous = new BigDecimal(row.get("plannedQuantity").toString());
            if (previous.compareTo(next) == 0) continue;
            Long itemId = change.itemId();
            jdbc.update("""
                UPDATE biz_material_demand_plan_item SET planned_quantity=?,adjustment_reason=?,updated_at=now()
                WHERE item_id=? AND plan_id=?
                """, next, cleanReason, itemId, planId);
            jdbc.update("""
                INSERT INTO biz_material_demand_plan_adjustment(plan_id,item_id,previous_quantity,planned_quantity,reason,changed_by)
                VALUES (?,?,?,?,?,?)
                """, planId, itemId, previous, next, cleanReason, LoginHelper.getUserId());
            changed.add(Map.of("明细", itemLabel(row), "原计划量", previous, "新计划量", next, "原因", cleanReason));
        }
        if (changed.isEmpty()) return before;
        int updated = jdbc.update("""
            UPDATE biz_material_demand_plan SET revision=revision+1,updated_by=?,updated_at=now()
            WHERE id=? AND revision=? AND status='draft'
            """, LoginHelper.getUserId(), planId, revision);
        if (updated != 1) throw new ServiceException("计划版本已变化，请重新加载后再保存");
        var after = detail(planId, false);
        BusinessAudit.record("调整物料需求计划数量", planId, "计划单号", Map.of("调整明细",changed),
            Map.of("计划单号",after.get("planNo"),"调整明细",changed,"原因",cleanReason));
        return after;
    }

    /** 查询需求计划和其结构化明细，统一回传汇总及调整历史。 / Load a plan with structured lines, grouped totals and quantity adjustment history. */
    public Map<String,Object> detail(Long planId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE" : "";
        var plans = jdbc.queryForList("""
            SELECT p.id::text AS id,p.plan_no AS "planNo",p.order_id::text AS "orderId",p.order_revision AS "orderRevision",
                   p.source_order_no AS "sourceOrderNo",p.customer_id::text AS "customerId",p.status,
                   p.calculation_status AS "calculationStatus",p.calculation_message AS "calculationMessage",p.revision,p.workflow_instance_id::text AS "workflowInstanceId",
                   p.created_by::text AS "createdBy",COALESCE(creator.nick_name,creator.user_name) AS "createdByName",
                   p.updated_by::text AS "updatedBy",COALESCE(editor.nick_name,editor.user_name) AS "updatedByName",p.submitted_by::text AS "submittedBy",
                   p.approved_by::text AS "approvedBy",p.approved_at AS "approvedAt",p.cancel_reason AS "cancelReason",
                   p.review_note AS "reviewNote",p.created_at AS "createdAt",p.updated_at AS "updatedAt",p.document::text AS document
            FROM biz_material_demand_plan p
            LEFT JOIN sys_user creator ON creator.user_id=p.created_by
            LEFT JOIN sys_user editor ON editor.user_id=p.updated_by
            WHERE p.id=?
            """ + lockClause, planId);
        if (plans.isEmpty()) throw new ServiceException("物料需求计划不存在");
        var plan = new LinkedHashMap<String,Object>(plans.getFirst());
        plan.put("document", JsonUtils.parseMap(Objects.toString(plan.get("document"),"{}")));
        var itemRows = jdbc.queryForList("""
            SELECT item_id::text AS "itemId",plan_id::text AS "planId",line_no AS "lineNo",po_number AS "poNumber",po_due_date AS "poDueDate",
                   supply_mode AS "supplyMode",source_line_key AS "sourceLineKey",source_style_id::text AS "sourceStyleId",
                   source_style_code AS "sourceStyleCode",source_style_name AS "sourceStyleName",set_spec_id::text AS "setSpecId",
                   set_spec_name AS "setSpecName",child_style_id::text AS "childStyleId",child_style_code AS "childStyleCode",
                   child_style_name AS "childStyleName",color_id::text AS "colorId",color_name AS "colorName",
                   size_id::text AS "sizeId",size_name AS "sizeName",order_quantity AS "orderQuantity",bom_id::text AS "bomId",
                   bom_version AS "bomVersion",usage_type AS "usageType",part_name AS "partName",material_id::text AS "materialId",
                   material_code AS "materialCode",material_name AS "materialName",category_id::text AS "categoryId",category_name AS "categoryName",
                   material_variant_id::text AS "materialVariantId",material_spec AS "materialSpec",material_color_id::text AS "materialColorId",
                   material_color_name AS "materialColorName",inventory_unit_id::text AS "inventoryUnitId",inventory_unit_name AS "inventoryUnitName",
                   per_unit_usage AS "perUnitUsage",original_quantity AS "originalQuantity",planned_quantity AS "plannedQuantity",
                   trim_scale(per_unit_usage)::text AS "usageRaw",
                   trim_scale(original_quantity)::text AS "originalRaw",trim_scale(planned_quantity)::text AS "plannedRaw",
                   (SELECT decimal_places FROM base_unit WHERE unit_id=inventory_unit_id) AS "unitPlaces",
                   purchase_units::text AS "purchaseUnitsJson",adjustment_reason AS "adjustmentReason",sort_order AS "sortOrder"
            FROM biz_material_demand_plan_item WHERE plan_id=? ORDER BY sort_order,item_id
            """, planId);
        var items = new ArrayList<Map<String,Object>>();
        for (var row : itemRows) {
            var item = new LinkedHashMap<String,Object>(row);
            item.put("purchaseUnits", purchaseUnits(Objects.toString(item.remove("purchaseUnitsJson"),"[]"), new BigDecimal(item.get("plannedQuantity").toString())));
            items.add(item);
        }
        plan.put("items",items);
        plan.put("summary",summarize(items));
        var reversalHistory=jdbc.queryForList("""
            SELECT r.instance_id::text AS "instanceId",r.reason,r.state,r.result_message AS "resultMessage",
                   r.requested_by::text AS "requestedBy",COALESCE(u.nick_name,u.user_name) AS "requestedByName",
                   r.created_at AS "requestedAt",r.updated_at AS "updatedAt"
            FROM biz_document_reversal_request r LEFT JOIN sys_user u ON u.user_id=r.requested_by
            WHERE r.business_type='material_demand_plan_unapprove' AND r.business_id=?
            ORDER BY r.created_at DESC,r.id DESC
            """,planId);
        plan.put("reversalHistory",reversalHistory);
        var activeReversal=reversalHistory.stream().filter(row->"pending".equals(row.get("state"))).findFirst().orElse(null);
        plan.put("reverseApprovalPending",activeReversal!=null);
        plan.put("reverseApproval",activeReversal);
        plan.put("adjustments",jdbc.queryForList("""
            SELECT a.adjustment_id::text AS "adjustmentId",a.item_id::text AS "itemId",i.material_code AS "materialCode",
                   i.material_name AS "materialName",i.material_spec AS "materialSpec",i.po_number AS "poNumber",
                   i.color_name AS "colorName",i.size_name AS "sizeName",i.inventory_unit_name AS "inventoryUnitName",
                   a.previous_quantity AS "previousQuantity",a.planned_quantity AS "plannedQuantity",a.reason,
                   a.changed_by::text AS "changedBy",COALESCE(u.nick_name,u.user_name) AS "changedByName",a.changed_at AS "changedAt"
            FROM biz_material_demand_plan_adjustment a JOIN biz_material_demand_plan_item i ON i.item_id=a.item_id
            LEFT JOIN sys_user u ON u.user_id=a.changed_by WHERE a.plan_id=? ORDER BY a.changed_at DESC,a.adjustment_id DESC
            """,planId));
        return plan;
    }

    /** 列出计划草稿可补生成的已审核订单，供部署前历史订单补建计划。 / List approved orders without a plan so existing records can be backfilled after rollout. */
    public List<Map<String,Object>> eligibleOrders(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        return jdbc.queryForList("""
            SELECT o.id::text AS "orderId",o.order_no AS "orderNo",o.customer_id::text AS "customerId",
                   COALESCE(o.document->>'customerName','') AS "customerName",o.revision,
                   o.document->>'date' AS "orderDate",
                   (SELECT count(*) FROM jsonb_array_elements(COALESCE(o.document->'pos','[]'::jsonb))) AS "poCount"
            FROM biz_customer_order o
            WHERE o.status='approved'
              AND (?='' OR o.order_no ILIKE ? OR o.document::text ILIKE ?)
              AND NOT EXISTS(SELECT 1 FROM biz_material_demand_plan p WHERE p.order_id=o.id AND p.status<>'cancelled')
              AND NOT EXISTS(SELECT 1 FROM biz_document_reversal_request r WHERE r.business_type='customer_order_unapprove'
                  AND r.business_id=o.id AND r.state='pending')
            ORDER BY o.updated_at DESC,o.id DESC LIMIT 200
            """,query,"%"+query+"%","%"+query+"%");
    }

    /** 从已保存订单快照构造主单，只有完整算料快照会展开为需求明细。 / Build the plan header from the saved order and materialize lines only from a complete saved demand snapshot. */
    private Long create(Map<String,Object> order) {
        var document = snapshot(order);
        var source = demands(order.get("demand"));
        String status = calculationStatus(source);
        String message = calculationMessage(source);
        List<Map<String,Object>> items=List.of();
        if("ready".equals(status)){
            try{items=buildItems(source,document);message="";}
            catch(ServiceException error){
                // 历史订单快照可能未保存套装子款色码；订单审核不得因派生计划无法还原而失败。
                // Older order snapshots may lack set-component color-size data; this derived-plan gap must not fail order approval.
                // Legacy order snapshots may not preserve set-child color-size pairings; keep order approval successful and flag the plan for recalculation.
                status="incomplete";
                message=error.getMessage();
            }
        }
        String number = nextNumber();
        Long planId = jdbc.queryForObject("""
            INSERT INTO biz_material_demand_plan
                (order_id,order_revision,plan_no,status,calculation_status,calculation_message,source_order_no,customer_id,document,created_by,updated_by)
            VALUES (?,?,?,'draft',?,?,?,?,?::jsonb,?,?) RETURNING id
            """,Long.class,order.get("id"),order.get("revision"),number,status,message,order.get("order_no"),order.get("customer_id"),
            JsonUtils.toJsonString(document),LoginHelper.getUserId(),LoginHelper.getUserId());
        replaceItems(planId,items);
        BusinessAudit.record("生成物料需求计划",planId,"计划单号",Map.of(),Map.of("计划单号",number,
            "来源订单",Objects.toString(order.get("order_no"),""),"算料状态",status,"需求行数",items.size()));
        return planId;
    }

    /** 生成具有业务含义且可读的单号，并在事务锁内避免并发重复。 / Generate a readable plan number while serializing same-day allocations. */
    private String nextNumber() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,"material-demand-plan-number:"+LocalDate.now(ZoneId.of("Asia/Shanghai")));
        String prefix="MRP"+LocalDate.now(ZoneId.of("Asia/Shanghai")).toString().replace("-","");
        var used=new HashSet<>(jdbc.queryForList("SELECT upper(plan_no) FROM biz_material_demand_plan WHERE upper(plan_no) LIKE ?",String.class,prefix+"%"));
        for(int n=1;n<Integer.MAX_VALUE;n++){String candidate=prefix+String.format(Locale.ROOT,"%04d",n);if(!used.contains(candidate))return candidate;}
        throw new ServiceException("无可用的物料需求计划单号");
    }

    /** 锁定来源订单并解析 JSON 快照，防止审核回调与订单编辑并发改写来源。 / Lock and decode the source order to prevent concurrent changes during generation. */
    private Map<String,Object> order(Long orderId, boolean lock) {
        var rows=jdbc.queryForList("""
            SELECT id,order_no,customer_id,status,revision,created_by,sales_id,follower_id,
                   document::text AS document,demand::text AS demand FROM biz_customer_order WHERE id=?
            """+(lock?" FOR UPDATE":""),orderId);
        if(rows.isEmpty())throw new ServiceException("来源客户订单不存在");
        var row=new LinkedHashMap<String,Object>(rows.getFirst());
        row.put("document",JsonUtils.parseMap(Objects.toString(row.get("document"),"{}")));
        row.put("demand",demands(row.get("demand")));
        return row;
    }

    /** 反审核审批期间禁止补建或重新同步需求计划，避免上游撤销请求与下游计划并发。 / Block plan creation or synchronization while its source order reversal is pending. */
    private void assertOrderNotReversing(Long orderId) {
        Boolean pending=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM biz_document_reversal_request
             WHERE business_type='customer_order_unapprove' AND business_id=? AND state='pending')
            """,Boolean.class,orderId);
        if(Boolean.TRUE.equals(pending))throw new ServiceException("来源订单正在反审核审批，暂不能生成或同步物料需求计划");
    }

    /** 将订单抬头信息保留为只读快照，供列表、审批和历史计划回查。 / Preserve order header and PO data as a read-only snapshot for list, approval and historical review. */
    private Map<String,Object> snapshot(Map<String,Object> order) {
        // 来源查询将 JSONB 显式转为文本，统一通过项目 JSON 工具解析，避免未经校验的泛型强转。
        // The source query selects JSONB as text; parse it with the shared JSON utility instead of an unchecked cast.
        var source = JsonUtils.parseMap(Objects.toString(order.get("document"), "{}"));
        var result=new LinkedHashMap<String,Object>(source);
        result.put("orderId",Objects.toString(order.get("id"),""));
        result.put("orderNo",Objects.toString(order.get("order_no"),""));
        result.put("customerId",Objects.toString(order.get("customer_id"),""));
        result.put("orderRevision",order.get("revision"));
        result.put("sourceOrderStatus",Objects.toString(order.get("status"),""));
        return result;
    }

    /** 解析客户订单已经校验并保存的完整需求快照。 / Parse the complete material demand snapshot already validated and stored on the customer order. */
    private List<Map<String,Object>> demands(Object value) {
        String json=Objects.toString(value,"[]");
        if(json.isBlank()||"null".equals(json))return List.of();
        return JsonUtils.parseObject(json,MAP_LIST);
    }

    /** 只有没有缺项、问题或套装缺料的订单快照才允许生成可提交计划。 / Only a snapshot without issues, missing inputs or set-style gaps can produce a submit-ready plan. */
    private String calculationStatus(List<Map<String,Object>> source) {
        if (source == null || source.isEmpty()) return "pending";
        boolean incomplete = source.stream().anyMatch(row -> row.get("issue")!=null&&!row.get("issue").toString().isBlank()
            || row.get("gaps") instanceof Collection<?> gaps && !gaps.isEmpty()
            || !(row.get("details") instanceof Collection<?> details) || details.isEmpty());
        return incomplete ? "incomplete" : "ready";
    }

    /** 将订单算料的不完整原因压成主单提示，避免计划人员面对只有状态但没有原因的空明细。 / Preserve actionable calculation blockers on the plan header for users who cannot infer why no rows were created. */
    private String calculationMessage(List<Map<String,Object>> source) {
        if (source == null || source.isEmpty()) return "来源订单尚未保存算料结果";
        var messages = new LinkedHashSet<String>();
        for (var row : source) {
            Object issue = row.get("issue");
            if (issue != null && !issue.toString().isBlank()) messages.add(issue.toString().trim());
            if (row.get("gaps") instanceof Collection<?> gaps) for (Object gap : gaps)
                if (gap != null && !gap.toString().isBlank()) messages.add(gap.toString().trim());
            if (!(row.get("details") instanceof Collection<?> details) || details.isEmpty())
                messages.add("有订单规格尚未形成物料需求明细");
        }
        if (messages.isEmpty()) return "";
        String result = String.join("；", messages);
        return result.substring(0, Math.min(1000, result.length()));
    }

    /** 按原订单 PO、款式色码及 BOM 明细构造需求行，并补齐生成时的档案文本快照。 / Expand saved demand by PO, style specification and BOM line, preserving the labels available at generation time. */
    private List<Map<String,Object>> buildItems(List<Map<String,Object>> demands,Map<String,Object> orderDocument) {
        var result=new ArrayList<Map<String,Object>>();
        var pos=listOfMaps(orderDocument.get("pos"));
        Map<String,Map<String,Object>> lineByKey=new HashMap<>();
        Map<String,Map<String,Object>> poByNumber=new HashMap<>();
        for(var po:pos){String poNo=Objects.toString(po.get("number"),"");poByNumber.put(poNo,po);for(var line:listOfMaps(po.get("lines")))lineByKey.put(poNo+"|"+Objects.toString(line.get("key"),""),line);}
        var materialCache=new HashMap<String,Map<String,Object>>();
        var colorCache=new HashMap<String,String>();
        var sizeCache=new HashMap<String,String>();
        var childStyleCache=new HashMap<String,Map<String,String>>();
        var setSpecCache=new HashMap<String,String>();
        var bomVersionCache=new HashMap<String,String>();
        int lineNo=0;
        for(var source:demandListSafe(demands)){
            String poNo=Objects.toString(source.get("po"),"");
            String lineKey=Objects.toString(source.get("lineKey"),"");
            var po=poByNumber.getOrDefault(poNo,Map.of());
            var orderLine=lineByKey.getOrDefault(poNo+"|"+lineKey,Map.of());
            var details=listOfMaps(source.get("details"));
            for(var detail:details){
                var row=new LinkedHashMap<String,Object>();
                String styleId=Objects.toString(orderLine.get("styleId"),"");
                String styleLabel=Objects.toString(orderLine.get("styleName"),"");
                String[] sourceStyle=splitStyleLabel(styleLabel);
                row.put("lineNo",++lineNo);row.put("poNumber",poNo);row.put("poDueDate",dateValue(po.get("due")));
                row.put("supplyMode",supply(detail.get("supply"),po,orderDocument));
                row.put("sourceLineKey",lineKey);row.put("sourceStyleId",longValue(styleId));row.put("sourceStyleCode",sourceStyle[0]);row.put("sourceStyleName",sourceStyle[1]);
                row.put("setSpecId",longValue(orderLine.get("setSpecId")));
                row.put("setSpecName",lookupName(setSpecCache,"set:"+Objects.toString(orderLine.get("setSpecId"),""),"SELECT spec_name FROM rd_style_set_spec WHERE id=?",orderLine.get("setSpecId")));
                row.put("childStyleId",null);row.put("childStyleCode","");row.put("childStyleName","");
                row.put("colorId",longValue(orderLine.get("colorId")));row.put("colorName",lookupName(colorCache,"color:"+Objects.toString(orderLine.get("colorId"),""),"SELECT color_name FROM base_color WHERE color_id=?",orderLine.get("colorId")));
                row.put("sizeId",longValue(orderLine.get("sizeId")));row.put("sizeName",lookupName(sizeCache,"size:"+Objects.toString(orderLine.get("sizeId"),""),"SELECT name FROM sys_size WHERE id=?",orderLine.get("sizeId")));
                BigDecimal ordered=decimal(orderLine.get("quantity"),BigDecimal.ZERO);
                String part=Objects.toString(detail.get("part"),"");
                if(orderLine.get("configuration") instanceof Collection<?> config){
                    boolean shared="整套共用".equals(part);
                    if(!shared){
                        String sourceChildId=Objects.toString(detail.get("sourceChildStyleId"),"");
                        Object sourceColorId=detail.get("sourceColorId"),sourceSizeId=detail.get("sourceSizeId");
                        var matches=new ArrayList<Map<String,Object>>();
                        for(var child:listOfMaps(config)){
                            String childId=Objects.toString(child.get("childId"),"");
                            var childStyle=childStyleCache.computeIfAbsent(childId,this::styleSnapshot);
                            boolean sameChild=sourceChildId.isBlank()?Objects.equals(Objects.toString(childStyle.get("label"),""),part):Objects.equals(childId,sourceChildId);
                            boolean sameColor=sourceColorId==null||Objects.equals(Objects.toString(child.get("colorId"),""),sourceColorId.toString());
                            boolean sameSize=sourceSizeId==null||Objects.equals(Objects.toString(child.get("sizeId"),""),sourceSizeId.toString());
                            if(sameChild&&sameColor&&sameSize)matches.add(child);
                        }
                        if(matches.size()!=1)throw new ServiceException("套装算料快照无法唯一定位子款配套色码，请从来源订单重新算料");
                        var child=matches.getFirst();
                        String childId=Objects.toString(child.get("childId"),"");
                        var childStyle=childStyleCache.computeIfAbsent(childId,this::styleSnapshot);
                        row.put("childStyleId",longValue(childId));row.put("childStyleCode",Objects.toString(childStyle.get("code"),""));row.put("childStyleName",Objects.toString(childStyle.get("name"),""));
                        Object colorId=sourceColorId==null?child.get("colorId"):sourceColorId;
                        Object sizeId=sourceSizeId==null?child.get("sizeId"):sourceSizeId;
                        row.put("colorId",longValue(colorId));row.put("colorName",lookupName(colorCache,"color:"+Objects.toString(colorId,""),"SELECT color_name FROM base_color WHERE color_id=?",colorId));
                        row.put("sizeId",longValue(sizeId));row.put("sizeName",lookupName(sizeCache,"size:"+Objects.toString(sizeId,""),"SELECT name FROM sys_size WHERE id=?",sizeId));
                        Object childCount=detail.get("sourceChildCount");
                        ordered=ordered.multiply(decimal(childCount==null?child.get("quantity"):childCount,BigDecimal.ONE));
                    }
                }
                row.put("orderQuantity",ordered);
                String bomId=Objects.toString(source.get("bomId"),"");
                row.put("bomId",longValue(bomId));
                row.put("bomVersion",lookupName(bomVersionCache,"bom:"+bomId,"SELECT version::text FROM rd_style_bom WHERE id=?",bomId));
                row.put("usageType",Objects.toString(detail.get("usageType"),""));row.put("partName",part);
                String materialId=Objects.toString(detail.get("materialId"),"");
                var material=materialCache.computeIfAbsent(materialId,this::materialSnapshot);
                if(material.isEmpty())throw new ServiceException("需求计划引用的物料档案不存在，请重新算料");
                row.put("materialId",longValue(materialId));row.put("materialCode",Objects.toString(material.get("materialCode"),""));row.put("materialName",Objects.toString(detail.get("material"),Objects.toString(material.get("materialName"),"")));
                row.put("categoryId",material.get("categoryId"));row.put("categoryName",Objects.toString(detail.get("materialCategory"),Objects.toString(material.get("categoryName"),"")));
                row.put("materialVariantId",longValue(detail.get("materialVariantId")));row.put("materialSpec",Objects.toString(detail.get("materialSpec"),""));
                row.put("materialColorId",longValue(detail.get("materialColorId")));row.put("materialColorName",Objects.toString(detail.get("materialColor"),""));
                row.put("inventoryUnitId",longValue(detail.get("unitId")));row.put("inventoryUnitName",Objects.toString(detail.get("unit"),""));
                BigDecimal original=decimal(detail.get("quantity"),BigDecimal.ZERO);
                row.put("perUnitUsage",ordered.signum()==0?null:original.divide(ordered,8,RoundingMode.HALF_UP));
                row.put("originalQuantity",original);row.put("plannedQuantity",original);
                row.put("purchaseUnits",detail.get("purchaseUnits") instanceof Collection<?> units?units:List.of());
                row.put("sortOrder",lineNo);
                result.add(row);
            }
        }
        return result;
    }

    /** 将可能为空的需求列表归一化为空集合，避免历史快照空值导致遍历失败。 / Normalize an absent demand collection so legacy snapshots remain safe to iterate. */
    private List<Map<String,Object>> demandListSafe(List<Map<String,Object>> source) { return source == null ? List.of() : source; }

    /** 从 JSON 数组中提取对象行并复制为可写 Map，忽略非对象值。 / Copy JSON object entries into mutable maps and ignore unexpected scalar entries. */
    private List<Map<String,Object>> listOfMaps(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        var result = new ArrayList<Map<String,Object>>();
        for (Object item : collection) if (item instanceof Map<?,?> raw) {
            var copy = new LinkedHashMap<String,Object>();
            raw.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
            result.add(copy);
        }
        return result;
    }

    /** 安全解析十进制快照值，非法值回退到调用方给定的业务默认值。 / Parse a decimal snapshot safely and use the supplied business fallback for invalid values. */
    private BigDecimal decimal(Object value, BigDecimal fallback) { try { return value == null ? fallback : new BigDecimal(value.toString()); } catch (NumberFormatException error) { return fallback; } }

    /** 把接口和 JSON 中的 bigint 字符串转换为数据库查询参数。 / Convert a JSON bigint string into a database query parameter without routing it through floating point. */
    private Long longValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return Long.valueOf(value.toString()); } catch (NumberFormatException error) { return null; } }

    /** 解析 PO 日期快照；无效历史日期保留为空而不伪造交期。 / Parse a PO date snapshot and leave invalid legacy dates empty rather than inventing a due date. */
    private java.sql.Date dateValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return java.sql.Date.valueOf(value.toString()); } catch (IllegalArgumentException error) { return null; } }

    /** 将款号和名称的组合文本拆成两列，格式异常时完整保留原文本。 / Split a combined style label when it follows the saved delimiter, otherwise preserve it as the code. */
    private String[] splitStyleLabel(String label) { String[] values = label.split(" · ", 2); return values.length == 2 ? values : new String[]{label, ""}; }

    /** 查询款式子件的编码/名称快照，供套装需求明细追溯子款。 / Resolve a set component style snapshot for child-specific demand lines. */
    private Map<String,String> styleSnapshot(String childId){if(childId==null||childId.isBlank())return Map.of();var rows=jdbc.queryForList("SELECT internal_code,style_name FROM rd_style WHERE style_id=?",Long.valueOf(childId));if(rows.isEmpty())return Map.of();String code=Objects.toString(rows.getFirst().get("internal_code"),"");String name=Objects.toString(rows.getFirst().get("style_name"),"");return Map.of("code",code,"name",name,"label",code+" · "+name);}

    /** 查询物料编码及分类快照；需求计划仍保留物料分类人员范围检查所需的分类主键。 / Resolve material code and category snapshots for traceability and server-side category-scope checks. */
    private Map<String,Object> materialSnapshot(String materialId){if(materialId==null||materialId.isBlank())return Map.of();var rows=jdbc.queryForList("SELECT m.material_code AS \"materialCode\",m.material_name AS \"materialName\",m.category_id::text AS \"categoryId\",c.category_name AS \"categoryName\" FROM inv_material m LEFT JOIN base_category c ON c.category_id=m.category_id WHERE m.material_id=? AND m.del_flag='0'",Long.valueOf(materialId));return rows.isEmpty()?Map.of():rows.getFirst();}

    /** 从缓存或数据库解析只读名称快照，缓存键按业务 ID 隔离。 / Resolve a display snapshot through an ID-keyed cache to avoid repeated reference queries. */
    private <T> String lookupName(Map<String,String> cache,String key,String query,Object id){if(id==null||id.toString().isBlank())return "";return cache.computeIfAbsent(key,k->{Object parameter=id instanceof Number?id:Long.valueOf(id.toString());var values=jdbc.queryForList(query,String.class,parameter);return values.isEmpty()?"":values.getFirst();});}

    /** 按明细供料、PO 覆盖、订单默认的优先级确定供料快照。 / Resolve supply mode in detail, PO override, then order-default precedence. */
    private String supply(Object value,Map<String,Object> po,Map<String,Object> orderDocument){String mode=Objects.toString(value,"");if(Set.of("customer","purchase","mixed").contains(mode))return mode;mode=Objects.toString(po.get("supply"),"");if(Set.of("customer","purchase","mixed").contains(mode))return mode;mode=Objects.toString(orderDocument.get("supply"),"customer");return Set.of("customer","purchase","mixed").contains(mode)?mode:"customer";}

    /** 写入来源快照行；仅对尚无人工调整的草稿执行整体替换。 / Persist snapshot rows and replace them only on an unadjusted draft. */
    private void replaceItems(Long planId,List<Map<String,Object>> items){jdbc.update("DELETE FROM biz_material_demand_plan_item WHERE plan_id=?",planId);for(var row:items)jdbc.update("""
        INSERT INTO biz_material_demand_plan_item(plan_id,line_no,po_number,po_due_date,supply_mode,source_line_key,
            source_style_id,source_style_code,source_style_name,set_spec_id,set_spec_name,child_style_id,child_style_code,child_style_name,
            color_id,color_name,size_id,size_name,order_quantity,bom_id,bom_version,usage_type,part_name,material_id,material_code,
            material_name,category_id,category_name,material_variant_id,material_spec,material_color_id,material_color_name,
            inventory_unit_id,inventory_unit_name,per_unit_usage,original_quantity,planned_quantity,purchase_units,sort_order)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
        """,planId,row.get("lineNo"),row.get("poNumber"),row.get("poDueDate"),row.get("supplyMode"),row.get("sourceLineKey"),
        row.get("sourceStyleId"),row.get("sourceStyleCode"),row.get("sourceStyleName"),row.get("setSpecId"),row.get("setSpecName"),
        row.get("childStyleId"),row.get("childStyleCode"),row.get("childStyleName"),row.get("colorId"),row.get("colorName"),
        row.get("sizeId"),row.get("sizeName"),row.get("orderQuantity"),row.get("bomId"),row.get("bomVersion"),row.get("usageType"),
        row.get("partName"),row.get("materialId"),row.get("materialCode"),row.get("materialName"),row.get("categoryId"),
        row.get("categoryName"),row.get("materialVariantId"),row.get("materialSpec"),row.get("materialColorId"),row.get("materialColorName"),
        row.get("inventoryUnitId"),row.get("inventoryUnitName"),row.get("perUnitUsage"),row.get("originalQuantity"),row.get("plannedQuantity"),
        JsonUtils.toJsonString(row.get("purchaseUnits")),row.get("sortOrder"));}

    /** 读取同步前的明细行数，用于审计计划重算前后的变化规模。 / Count existing lines before synchronization so the audit records the before-and-after scope. */
    private int activeItemsCount(Long planId){Integer count=jdbc.queryForObject("SELECT count(*) FROM biz_material_demand_plan_item WHERE plan_id=?",Integer.class,planId);return count==null?0:count;}

    /** 按物料档案/规格/颜色/供料方式汇总，同时保留订单 PO 色码来源子行。 / Aggregate by material variant, color, supply mode and unit while retaining each PO and style-color-size source. */
    private List<Map<String,Object>> summarize(List<Map<String,Object>> items){
        var groups = new LinkedHashMap<String,Map<String,Object>>();
        var sourcesByGroup = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (var item : items) {
            // 供料方式、单位、规格和物料颜色都影响可汇总库存口径；PO 与成衣色码留在来源子行中。
            // Supply mode, unit, variant, and material color define a stock bucket; PO and garment pairs remain source details.
            List<String> dimensions = List.of(
                Objects.toString(item.get("materialId"), ""),
                Objects.toString(item.get("materialVariantId"), ""),
                Objects.toString(item.get("materialSpec"), ""),
                Objects.toString(item.get("materialColorId"), ""),
                Objects.toString(item.get("materialColorName"), ""),
                Objects.toString(item.get("supplyMode"), ""),
                Objects.toString(item.get("inventoryUnitId"), ""));
            String key = compositeKey(dimensions);
            var group = groups.computeIfAbsent(key, ignored -> {
                var value = new LinkedHashMap<String,Object>();
                value.put("key", key);
                value.put("materialId", item.get("materialId"));
                value.put("materialCode", item.get("materialCode"));
                value.put("materialName", item.get("materialName"));
                value.put("categoryName", item.get("categoryName"));
                value.put("materialSpec", item.get("materialSpec"));
                value.put("materialColorName", item.get("materialColorName"));
                value.put("supplyMode", item.get("supplyMode"));
                value.put("inventoryUnitName", item.get("inventoryUnitName"));
                value.put("originalQuantity", BigDecimal.ZERO);
                value.put("plannedQuantity", BigDecimal.ZERO);
                value.put("purchaseUnits", item.get("purchaseUnits"));
                return value;
            });
            group.put("originalQuantity", ((BigDecimal) group.get("originalQuantity"))
                .add(new BigDecimal(item.get("originalQuantity").toString())));
            group.put("plannedQuantity", ((BigDecimal) group.get("plannedQuantity"))
                .add(new BigDecimal(item.get("plannedQuantity").toString())));
            sourcesByGroup.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        groups.forEach((key, group) -> group.put("sources", sourcesByGroup.getOrDefault(key, List.of())));
        return new ArrayList<>(groups.values());
    }

    /** 生成无分隔符碰撞的汇总维度键；该键仅供结果展开状态使用，不是业务主键。 / Build a collision-safe grouping key used only for UI expansion, never as a business ID. */
    private String compositeKey(List<String> dimensions) {
        var key = new StringBuilder();
        for (String dimension : dimensions) key.append(dimension.length()).append(':').append(dimension);
        return key.toString();
    }

    /** 按已保存的物料换算规则展示采购单位参考数量；实际换算不推测采购包装数量。 / Calculate fixed-conversion purchase references while leaving variable conversions explicitly uncalculated. */
    private List<Map<String,Object>> purchaseUnits(String json,BigDecimal plannedQuantity) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        List<Map<String,Object>> stored = JsonUtils.parseObject(json, MAP_LIST);
        if (stored == null || stored.isEmpty()) return List.of();
        var result = new ArrayList<Map<String,Object>>();
        for (var unit : stored) {
            var item = new LinkedHashMap<String,Object>(unit);
            String mode = Objects.toString(unit.get("mode"), "");
            BigDecimal factor = decimal(unit.get("inventoryFactor"), BigDecimal.ZERO);
            if ("FIXED".equalsIgnoreCase(mode) && factor.signum() > 0) {
                BigDecimal converted = plannedQuantity.divide(factor, 8, RoundingMode.HALF_UP);
                item.put("quantity", converted.stripTrailingZeros().toPlainString());
                item.put("approximate", converted.multiply(factor).compareTo(plannedQuantity) != 0);
            } else {
                item.put("quantity", null);
                item.put("approximate", false);
            }
            result.add(item);
        }
        return result;
    }

    /** 生成数量调整历史中的可读业务行标识，不暴露内部数据库 ID。 / Build a human-readable adjustment label without exposing internal database IDs. */
    private String itemLabel(Map<String,Object> row){return String.join(" / ",Objects.toString(row.get("poNumber"),""),Objects.toString(row.get("sourceStyleCode"),""),Objects.toString(row.get("colorName"),""),Objects.toString(row.get("sizeName"),""),Objects.toString(row.get("materialCode"),""),Objects.toString(row.get("materialSpec"),""));}

    /** 单行计划数量修改值；数量单位继承该需求明细的库存单位。 / One planned-quantity change whose unit is inherited from the demand line's inventory unit. */
    public record QuantityChange(Long itemId, BigDecimal plannedQuantity) {}
}
