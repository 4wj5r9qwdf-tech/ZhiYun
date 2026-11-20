/*
 * 文件用途：为织云 ERP 的物料需求计划接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的物料需求计划接口提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.web.service.BusinessApprovalWorkflowService;
import org.dromara.web.service.CategoryAccessService;
import org.dromara.web.service.MaterialDemandPlanService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** 查询和维护客户订单派生的物料需求计划。 / Query and maintain material demand plans derived from customer orders. */
@RestController
@RequestMapping("/order/material-demand-plans")
@SaCheckPermission("order:material-demand-plan:list")
@RequiredArgsConstructor
public class MaterialDemandPlanController {
    private final JdbcTemplate jdbc;
    private final MaterialDemandPlanService plans;
    private final BusinessApprovalWorkflowService workflows;
    private final CategoryAccessService categoryAccess;

    /** 计划数量调整请求，使用修订号保护并发并要求提交业务原因。 / Quantity edit request guarded by the document revision and accompanied by a business reason. */
    public record QuantityRequest(Integer revision,List<MaterialDemandPlanService.QuantityChange> items,String reason) {}
    /** 需求计划状态流转请求，动作限定在控制器白名单内。 / Lifecycle request whose action is validated against the controller's allowlist. */
    public record Action(Integer revision,String action,String reason) {}

    /** 按单号、来源订单、客户、PO 和单据状态筛选计划。 / Filter plans by plan number, source order, customer, PO and document state. */
    @GetMapping("/list")
    public R<Map<String,Object>> list(@RequestParam(defaultValue="") String keyword,
                                            @RequestParam(defaultValue="") String status,
                                            @RequestParam(defaultValue="") String calculationStatus,
                                            @RequestParam(defaultValue="") String poNumber,
                                            @RequestParam(defaultValue="1") int pageNum,
                                            @RequestParam(defaultValue="20") int pageSize) {
        // HTTP 参数默认值无法覆盖内部 Java 调用传入的 null；所有可选文本筛选在入口统一归一化。
        // HTTP defaults do not protect direct Java callers from null; normalize every optional text filter here.
        String text=optionalFilter(keyword),statusFilter=optionalFilter(status),
            calculationFilter=optionalFilter(calculationStatus),po=optionalFilter(poNumber);
        String projection = """
            SELECT p.id::text AS id,p.plan_no AS "planNo",p.order_id::text AS "orderId",p.source_order_no AS "sourceOrderNo",
                   COALESCE(p.document->>'customerName','') AS "customerName",p.document->>'date' AS "orderDate",
                   p.status,p.calculation_status AS "calculationStatus",p.calculation_message AS "calculationMessage",p.revision,p.created_by::text AS "createdBy",
                   COALESCE(u.nick_name,u.user_name) AS "createdByName",p.created_at AS "createdAt",p.updated_at AS "updatedAt",
                   (SELECT string_agg(DISTINCT i.po_number,'、' ORDER BY i.po_number) FROM biz_material_demand_plan_item i WHERE i.plan_id=p.id) AS "poNumbers",
                   (SELECT count(*) FROM biz_material_demand_plan_item i WHERE i.plan_id=p.id) AS "lineCount",
                   (SELECT count(DISTINCT (i.material_id,i.material_variant_id,i.material_color_id)) FROM biz_material_demand_plan_item i WHERE i.plan_id=p.id) AS "materialCount"
            """;
        StringBuilder from = new StringBuilder("""
            FROM biz_material_demand_plan p LEFT JOIN sys_user u ON u.user_id=p.created_by
            WHERE (?='' OR p.plan_no ILIKE ? OR p.source_order_no ILIKE ? OR COALESCE(p.document->>'customerName','') ILIKE ?
                   OR p.document::text ILIKE ?)
              AND (?='' OR p.status=?) AND (?='' OR p.calculation_status=?)
              AND (?='' OR EXISTS(SELECT 1 FROM biz_material_demand_plan_item i WHERE i.plan_id=p.id AND i.po_number ILIKE ?))
            """);
        String pattern = "%" + text + "%";
        List<Object> args = new ArrayList<>(List.of(text, pattern, pattern, pattern, pattern,
            statusFilter, statusFilter, calculationFilter, calculationFilter, po, "%" + po + "%"));
        // 分页前排除含越权明细的整张计划，列表与总数使用同一范围。
        // Exclude entire restricted plans before counting or paging.
        Long userId = LoginHelper.getUserId();
        boolean superAdmin = userId != null && LoginHelper.isSuperAdmin(userId);
        from.append("""
            AND NOT EXISTS (SELECT 1 FROM biz_material_demand_plan_item i
              WHERE i.plan_id=p.id AND NOT (
                (i.category_id IS NULL AND ?) OR EXISTS (
                  SELECT 1 FROM base_category c WHERE c.category_id=i.category_id AND
            """);
        // 旧明细缺分类时沿用详情规则，仅超管可见，便于核对历史分类。
        // As in detail views, only super admins can inspect historical lines without a category.
        args.add(superAdmin);
        categoryAccess.appendScope(from, args, "c.category_id", categoryAccess.scope());
        from.append(")))");
        Long total = jdbc.queryForObject("SELECT count(*) " + from, Long.class, args.toArray());
        int size = Math.clamp(pageSize, 1, 100);
        args.add(size);
        args.add((Math.max(1L, pageNum) - 1) * size);
        var rows = jdbc.queryForList(projection + from + " ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?", args.toArray());
        return R.ok(Map.of("rows", rows, "total", total));
    }

    /** 查询详情并拒绝静默隐藏人员无权查看的物料分类。 / Load details and explicitly reject plans containing materials outside the caller's category scope. */
    @GetMapping("/{id}")
    public R<Map<String,Object>> detail(@PathVariable Long id) {
        return R.ok(visibleDetail(id,false));
    }

    /** 查询可补生成计划的已审核订单，供老订单初始化与漏建单据补录。 / List approved orders eligible for one-time plan backfill. */
    @GetMapping("/eligible-orders")
    @SaCheckPermission({"order:material-demand-plan:list","order:material-demand-plan:edit"})
    public R<List<Map<String,Object>>> eligibleOrders(@RequestParam(defaultValue="") String keyword) {
        return R.ok(plans.eligibleOrders(keyword));
    }

    /** 仅从已审核客户订单幂等补生成计划，不允许凭空创建无来源需求。 / Idempotently backfill a plan from an approved order; never create demand without an order source. */
    @PostMapping("/generate/{orderId}")
    @Transactional(rollbackFor=Exception.class)
    @SaCheckPermission({"order:material-demand-plan:list","order:material-demand-plan:edit"})
    @RepeatSubmit
    @Log(title="补生成物料需求计划",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> generate(@PathVariable Long orderId) {
        Long id=plans.ensureDraftForApprovedOrder(orderId);
        return R.ok(visibleDetail(id,false));
    }

    /** 保存草稿计划量；服务层逐行校验来源、并发版本和调整原因并保留历史。 / Save draft planned quantities with server-side source, concurrency and reason checks. */
    @PutMapping("/{id}/quantities")
    @Transactional(rollbackFor=Exception.class)
    @SaCheckPermission({"order:material-demand-plan:list","order:material-demand-plan:edit"})
    @RepeatSubmit
    @Log(title="调整物料需求计划数量",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> saveQuantities(@PathVariable Long id,@RequestBody QuantityRequest request) {
        if(request.revision()==null)throw new ServiceException("缺少计划版本，请刷新后重试");
        var before=plans.detail(id,true);
        assertCategories(before);
        var saved=plans.saveQuantities(id,request.revision(),request.items(),request.reason());
        assertCategories(saved);
        return R.ok(saved);
    }

    /** 提交、撤回、审核、退回或取消计划；已绑定流程的审批结果只能由 Warm-Flow 回调更新。 / Submit, withdraw, review, return or cancel a plan; bound workflow results are changed only by engine callbacks. */
    @PostMapping("/{id}/transition")
    @Transactional(rollbackFor=Exception.class)
    @RepeatSubmit
    @Log(title="物料需求计划流转",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> transition(@PathVariable Long id,@RequestBody Action request) {
        if(request.revision()==null)throw new ServiceException("缺少计划版本，请刷新后重试");
        var before=plans.detail(id,true);
        assertCategories(before);
        if(!Objects.equals(request.revision(),before.get("revision")))throw new ServiceException("计划版本已变化，请重新加载");
        String state=Objects.toString(before.get("status"),"");
        String action=Objects.toString(request.action(),"");
        String reason=request.reason()==null?"":request.reason().trim();
        if(Set.of("return","cancel").contains(action)&&reason.isEmpty())throw new ServiceException("请填写操作原因");
        if(reason.length()>500)throw new ServiceException("操作原因最多500字");
        if("request_unapprove".equals(action))return requestReverseApproval(id,before,reason);

        String target;
        switch(action){
            case "submit" -> {
                StpUtil.checkPermission("order:material-demand-plan:edit");
                if(!"draft".equals(state))throw new ServiceException("只有草稿计划可以提交");
                if(!"ready".equals(before.get("calculationStatus"))||((List<?>)before.get("items")).isEmpty())
                    throw new ServiceException("当前计划尚无完整算料明细，请先到来源订单完成算料");
                target="submitted";
            }
            case "unsubmit" -> {
                StpUtil.checkPermission("order:material-demand-plan:edit");
                if(!"submitted".equals(state))throw new ServiceException("只有审批中的计划可以撤回");
                if(hasPendingWorkflow(id))throw new ServiceException("该计划已进入审批流程，请在审批流程中撤回");
                target="draft";
            }
            case "approve","return" -> {
                StpUtil.checkPermission("order:material-demand-plan:review");
                if(!"submitted".equals(state))throw new ServiceException("计划已不在待审核状态");
                if(hasPendingWorkflow(id))throw new ServiceException("该计划由审批流程管理，请在我的待办中办理");
                target="approve".equals(action)?"approved":"draft";
            }
            case "cancel" -> {
                StpUtil.checkPermission("order:material-demand-plan:cancel");
                if(!"draft".equals(state))throw new ServiceException("只有草稿计划可以取消");
                target="cancelled";
            }
            default -> throw new ServiceException("未知的需求计划操作");
        }

        int nextRevision=request.revision()+1;
        int changed=jdbc.update("""
            UPDATE biz_material_demand_plan SET status=?,revision=?,updated_by=?,updated_at=now(),
                submitted_by=CASE WHEN ?='submit' THEN ? ELSE submitted_by END,
                approved_by=CASE WHEN ?='approve' THEN ? WHEN ? IN ('return','unsubmit') THEN NULL ELSE approved_by END,
                approved_at=CASE WHEN ?='approve' THEN now() WHEN ?='return' OR ?='unsubmit' THEN NULL ELSE approved_at END,
                workflow_instance_id=CASE WHEN ?='submit' THEN NULL ELSE workflow_instance_id END,
                cancel_reason=CASE WHEN ?='cancel' THEN ? ELSE cancel_reason END,
                review_note=CASE WHEN ? IN ('return','approve') THEN ? ELSE review_note END
            WHERE id=? AND revision=? AND status=?
            """,target,nextRevision,LoginHelper.getUserId(),action,LoginHelper.getUserId(),action,LoginHelper.getUserId(),action,
            action,action,action,action,action,action,reason,action,reason,id,request.revision(),state);
        if(changed!=1)throw new ServiceException("计划状态已被其他人更新，请重新加载");

        if("submit".equals(action)){
            Long instance=workflows.submit("material_demand_plan",id,nextRevision,Objects.toString(before.get("planNo"),""));
            if(instance!=null)jdbc.update("UPDATE biz_material_demand_plan SET workflow_instance_id=? WHERE id=? AND revision=?",instance,id,nextRevision);
        }
        var after=plans.detail(id,false);
        BusinessAudit.record(switch(action){case "submit"->"提交物料需求计划";case "unsubmit"->"撤回物料需求计划";case "approve"->"审核通过物料需求计划";case "return"->"退回物料需求计划";default->"取消物料需求计划";},
            id,"计划单号",Map.of("状态",state,"修订号",request.revision()),Map.of("状态",target,"修订号",nextRevision,"原因",reason));
        return R.ok(after);
    }

    /** 已审核计划的反审核必须另起 Warm-Flow 流程；流程通过后计划转为已取消并保留明细。 / Route approved-plan reversals through a separate workflow and retain the cancelled plan history. */
    private R<Map<String,Object>> requestReverseApproval(Long id,Map<String,Object> before,String reason) {
        StpUtil.checkPermission("order:material-demand-plan:edit");
        if(!"approved".equals(before.get("status")))throw new ServiceException("只有已审核计划可以申请反审核");
        if(reason.isEmpty())throw new ServiceException("请填写反审核原因");
        if(reason.length()>500)throw new ServiceException("反审核原因最多500字");
        Boolean pending=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM biz_document_reversal_request
             WHERE business_type='material_demand_plan_unapprove' AND business_id=? AND state='pending')
            """,Boolean.class,id);
        if(Boolean.TRUE.equals(pending))throw new ServiceException("该计划已有反审核申请正在审批");
        Long instance=workflows.submitRequired("material_demand_plan_unapprove",id,
            ((Number)before.get("revision")).intValue(),Objects.toString(before.get("planNo"),""));
        var sourceSnapshot=Map.of("planNo",Objects.toString(before.get("planNo"),""),"status",Objects.toString(before.get("status"),""),"revision",before.get("revision"));
        jdbc.update("""
            INSERT INTO biz_document_reversal_request(business_type,business_id,business_revision,instance_id,requested_by,reason,source_snapshot)
            VALUES('material_demand_plan_unapprove',?,?,?,?,?,?::jsonb)
            """,id,before.get("revision"),instance,LoginHelper.getUserId(),reason,JsonUtils.toJsonString(sourceSnapshot));
        BusinessAudit.record("发起物料需求计划反审核审批",id,"计划单号",
            Map.of("计划单号",before.get("planNo"),"状态",before.get("status")),
            Map.of("计划单号",before.get("planNo"),"反审核原因",reason,"审批实例",instance));
        return R.ok(visibleDetail(id,false));
    }

    /** 检查此需求计划是否仍由一个审批实例办理。 / Check whether an active workflow instance currently owns the review decision. */
    private boolean hasPendingWorkflow(Long id){Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_approval_business_instance WHERE business_type='material_demand_plan' AND business_id=? AND state IN ('starting','pending'))",Boolean.class,id);return Boolean.TRUE.equals(exists);}

    /** 分类范围采用全有或全无，避免把权限不足误显示为空计划或不完整汇总。 / Apply all-or-nothing category access so restricted rows are never mistaken for an empty or incomplete plan. */
    /** 加载计划详情并在返回前执行完整的物料分类范围检查。 / Load a plan and enforce full material-category visibility before returning it. */
    private Map<String,Object> visibleDetail(Long id,boolean lock){var detail=plans.detail(id,lock);assertCategories(detail);return detail;}

    /** 全有或全无检查计划中的物料分类；缺失分类只允许超管查看以便修复历史档案。 / Enforce all-or-nothing category access; only super administrators may inspect uncategorized legacy rows for repair. */
    private void assertCategories(Map<String,Object> detail){for(Object raw:(List<?>)detail.get("items")){if(!(raw instanceof Map<?,?> item))continue;if(item.get("categoryId")==null){Long userId=LoginHelper.getUserId();if(userId==null||!LoginHelper.isSuperAdmin(userId))throw new ServiceException("需求计划存在未保存物料分类的历史明细，无法确认您的数据范围，请联系管理员核对物料档案");continue;}Long categoryId;try{categoryId=Long.valueOf(item.get("categoryId").toString());}catch(NumberFormatException e){throw new ServiceException("需求计划的物料分类快照无效，请联系管理员");}if(!categoryAccess.categoryAllowed(categoryId))throw new ServiceException("需求计划包含您无权查看的物料分类，请联系管理员调整人员分类权限");}}

    /** 将缺失或空白的可选筛选值统一为无筛选状态。 / Treat absent or blank optional filters as no filter. */
    private static String optionalFilter(String value) {
        return value == null ? "" : value.trim();
    }
}
