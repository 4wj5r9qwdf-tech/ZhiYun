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
        "customer_order","客户订单", "material_demand_plan","物料需求计划", "production_task","生产任务单");

    public record BindingRequest(String flowCode) {}

    /** 解除绑定只影响之后提交的单据，已有流程实例继续办理。 / Unbinding affects future submissions only; existing workflow instances continue unchanged. */
    @DeleteMapping("/{businessType}")
    @SaCheckPermission("approval:binding:edit")
    @Transactional
    @Log(title="解除审批对象流程绑定",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    public R<Void> remove(@PathVariable String businessType) {
        if (!Set.of("customer_order","material_demand_plan").contains(businessType)) throw new ServiceException("该单据暂未接入审批");
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
                   CASE WHEN flow_code LIKE 'order\\_%' ESCAPE '\\' THEN 'customer_order' ELSE 'material_demand_plan' END AS "businessType"
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
        String prefix=switch(businessType){case "customer_order"->"order_";case "material_demand_plan"->"mrp_";default->throw new ServiceException("该单据暂未接入审批，不能配置流程");};
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
