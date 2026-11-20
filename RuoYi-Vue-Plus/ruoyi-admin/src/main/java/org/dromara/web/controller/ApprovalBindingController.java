/*
 * 文件用途：为织云 ERP 的业务对象审批绑定接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的业务对象审批绑定接口提供服务端 HTTP 接口。
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
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 业务审批对象与已发布 Warm-Flow 流程的绑定；不重复维护流程节点和待办。 / Bind business objects to published Warm-Flow definitions without duplicating workflow nodes or tasks. */
@RestController
@RequestMapping("/system/approval-bindings")
@RequiredArgsConstructor
public class ApprovalBindingController {
    private final JdbcTemplate jdbc;
    private static final Map<String,String> TYPES=Map.of(
        "customer_order","客户订单", "material_demand_plan","物料需求计划",
        "customer_order_unapprove","客户订单反审核", "material_demand_plan_unapprove","物料需求计划反审核",
        "production_task","生产任务单");

    public record BindingRequest(String flowCode) {}

    /** 解除绑定只影响之后提交的单据，已有流程实例继续办理。 / Unbinding affects future submissions only; existing workflow instances continue unchanged. */
    @DeleteMapping("/{businessType}")
    @SaCheckPermission("approval:binding:edit")
    @Transactional
    @Log(title="解除审批对象流程绑定",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    public R<Void> remove(@PathVariable String businessType) {
        if (Set.of("customer_order_unapprove","material_demand_plan_unapprove").contains(businessType))
            throw new ServiceException("反审核审批流程必须保持绑定；请更换已发布流程，不要解除绑定");
        if (!Set.of("customer_order","material_demand_plan").contains(businessType))
            throw new ServiceException("该审批对象不支持解除绑定");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,"approval-binding:"+businessType);
        List<String> old=jdbc.queryForList("SELECT flow_code FROM sys_approval_binding WHERE business_type=? FOR UPDATE",String.class,businessType);
        if (!old.isEmpty()) {
            jdbc.update("DELETE FROM sys_approval_binding WHERE business_type=?",businessType);
            BusinessAudit.record("解除审批对象流程绑定",businessType,"审批对象",Map.of("业务类型",TYPES.get(businessType),"流程编码",old.getFirst()),Map.of("审核方式","按单据审核权限"));
        }
        return R.ok();
    }

    /** 仅管理员配置权限可查看业务类型绑定与可选的已发布流程。 / Only users with configuration permission can view bindings and eligible published workflows. */
    @GetMapping
    @SaCheckPermission("approval:binding:list")
    public R<Map<String,Object>> list() {
        var bindings=jdbc.queryForList("SELECT business_type AS \"businessType\",flow_code AS \"flowCode\",update_time AS \"updateTime\" FROM sys_approval_binding ORDER BY business_type");
        // 按业务前缀隔离流程候选，避免将客户订单流程误绑定到需求计划。
        var flows=jdbc.queryForList("""
            SELECT flow_code AS "flowCode",flow_name AS "flowName",version,
                   CASE
                     WHEN flow_code LIKE 'order\\_unapprove\\_%' ESCAPE '\\' THEN 'customer_order_unapprove'
                     WHEN flow_code LIKE 'mrp\\_unapprove\\_%' ESCAPE '\\' THEN 'material_demand_plan_unapprove'
                     WHEN flow_code LIKE 'order\\_%' ESCAPE '\\' THEN 'customer_order'
                     WHEN flow_code LIKE 'mrp\\_%' ESCAPE '\\' THEN 'material_demand_plan'
                   END AS "businessType"
            FROM flow_definition
            WHERE is_publish=1 AND activity_status=1 AND del_flag='0'
              AND (flow_code LIKE 'order\\_%' ESCAPE '\\' OR flow_code LIKE 'mrp\\_%' ESCAPE '\\')
            ORDER BY flow_name,flow_code
            """);
        return R.ok(Map.of("types",TYPES,"bindings",bindings,"flows",flows));
    }

    /** 更新绑定只影响后续发起的流程，既有审批实例应继续使用原定义。 / Binding changes apply to future submissions; existing instances keep their original workflow definition. */
    @PutMapping("/{businessType}")
    @SaCheckPermission("approval:binding:edit")
    @Transactional
    @Log(title="审批对象流程绑定",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    public R<Void> save(@PathVariable String businessType,@RequestBody BindingRequest request) {
        if (!TYPES.containsKey(businessType)) throw new ServiceException("不支持的审批业务类型");
        String code=Objects.toString(request.flowCode(),"").trim();
        if (code.isEmpty() || code.length()>40) throw new ServiceException("请选择已发布的审批流程");
        String prefix=switch(businessType){
            case "customer_order"->"order_";
            case "material_demand_plan"->"mrp_";
            case "customer_order_unapprove"->"order_unapprove_";
            case "material_demand_plan_unapprove"->"mrp_unapprove_";
            default->throw new ServiceException("该单据暂未接入审批，不能配置流程");
        };
        if (!code.startsWith(prefix)) throw new ServiceException("请选择与该业务单据匹配的审批流程");
        var flows=jdbc.queryForList("SELECT flow_name FROM flow_definition WHERE flow_code=? AND is_publish=1 AND activity_status=1 AND del_flag='0' FOR SHARE",String.class,code);
        if (flows.size()!=1) throw new ServiceException("流程未发布、已停用或存在重复，请检查流程定义");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,"approval-binding:"+businessType);
        List<String> old=jdbc.queryForList("SELECT flow_code FROM sys_approval_binding WHERE business_type=? FOR UPDATE",String.class,businessType);
        if (!old.isEmpty() && old.getFirst().equals(code)) return R.ok();
        jdbc.update("INSERT INTO sys_approval_binding(business_type,flow_code,update_by,update_time) VALUES(?,?,?,now()) ON CONFLICT (business_type) DO UPDATE SET flow_code=EXCLUDED.flow_code,update_by=EXCLUDED.update_by,update_time=now()",businessType,code,LoginHelper.getUserId());
        BusinessAudit.record("设置审批对象流程",businessType,"审批对象",Map.of("业务类型",TYPES.get(businessType),"流程编码",old.isEmpty()?"未设置":old.getFirst()),Map.of("业务类型",TYPES.get(businessType),"流程编码",code,"流程名称",flows.getFirst()));
        return R.ok();
    }
}
