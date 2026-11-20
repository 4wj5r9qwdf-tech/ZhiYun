/*
 * 文件用途：为织云 ERP 的订单审批配置接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的订单审批配置接口提供服务端 HTTP 接口。
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
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.workflow.service.IFlwDefinitionService;
import org.dromara.workflow.service.IFlwTaskAssigneeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 审批对象与 Warm-Flow 流程定义的适配入口。 / Adapter between ERP approval objects and Warm-Flow definitions. */
@RestController
@RequestMapping("/approval/customer-orders/setup")
@RequiredArgsConstructor
@SaCheckPermission({"workflow:definition:add", "workflow:definition:edit", "workflow:definition:query"})
public class OrderApprovalSetupController {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<IFlwDefinitionService> definitions;
    private final ObjectProvider<IFlwTaskAssigneeService> assignees;

    private static final String INITIATOR_ORG_LEADER = "#{@spelRuleComponent.selectOrgLeaderById(#initiatorOrgId)}";

    /** 一个审批节点的审批人规则、会签方式和抄送对象。 / One approval step's assignee rule, approval mode, and copy recipients. */
    public record Step(String name, String assignee, boolean unanimous, List<String> copyUsers) {}
    /** 可复用的流程定义输入；draft 和 modelValue 控制草稿状态及流程设计器类型。 / Reusable workflow definition input, including draft state and designer model. */
    public record Setup(String businessType, String name, List<Step> steps, Boolean draft, String modelValue) {}
    /** 返回审批对象配置可选业务类型及可绑定的流程定义。 / Provide registered business types and eligible workflow definitions for binding. */

    @GetMapping
    public R<List<Map<String, Object>>> options() {
        return R.ok(jdbc.queryForList("""
            SELECT value,label,kind,org_id,parent_id FROM (
                SELECT user_id::text AS value, nick_name AS label, '人员' AS kind, org_id::text AS org_id, NULL::text AS parent_id FROM sys_user WHERE status='0' AND del_flag='0'
                UNION ALL
                SELECT 'post:' || post_id, post_name, '岗位', NULL::text, NULL::text FROM sys_post WHERE status='0'
                UNION ALL
                SELECT 'role:' || role_id, role_name, '角色', NULL::text, NULL::text FROM sys_role WHERE status='0' AND del_flag='0'
                UNION ALL
                SELECT '#{@spelRuleComponent.selectOrgLeaderById(#initiatorOrgId)}', '发起人所在部门负责人', '负责人规则', NULL::text, NULL::text
                UNION ALL
                SELECT 'org:' || org_id, org_name, '部门', org_id::text, parent_id::text FROM sys_org WHERE status='0' AND del_flag='0'
            ) choices ORDER BY kind,label,value
            """));
    }

    /** 创建独立流程定义；业务对象的启用流程由绑定页面单独选择。 / Create a reusable workflow definition; choose the active business binding separately in approval-object settings. */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @Log(title = "新增审批流程", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public R<Map<String,String>> setup(@RequestBody Setup setup) {
        if (!Set.of("customer_order","material_demand_plan","customer_order_unapprove","material_demand_plan_unapprove").contains(setup.businessType()))
            throw new ServiceException("该单据暂未接入审批");
        var definitionService = definitions.getIfAvailable();
        var assigneeService = assignees.getIfAvailable();
        if (definitionService == null || assigneeService == null) throw new ServiceException("审批引擎未启用");
        String name = Objects.toString(setup.name(), "").trim();
        if (name.isEmpty() || name.length() > 40 || setup.steps() == null
            || setup.steps().isEmpty() || setup.steps().size() > 12) {
            throw new ServiceException("请填写流程名称，并设置 1 至 12 个审批节点");
        }
        String model = Objects.toString(setup.modelValue(), "CLASSICS");
        if (!Set.of("CLASSICS", "MIMIC").contains(model)) throw new ServiceException("请选择有效的设计器模型");
        boolean vertical = "MIMIC".equals(model);
        String prefix=switch(setup.businessType()){
            case "customer_order"->"order_";
            case "material_demand_plan"->"mrp_";
            case "customer_order_unapprove"->"order_unapprove_";
            case "material_demand_plan_unapprove"->"mrp_unapprove_";
            default->throw new ServiceException("该单据暂未接入审批");
        };
        // UUID 去掉连字符后固定为32位；按流程编码列的40字符上限截取，避免短前缀场景请求超过UUID长度。
        // A hyphen-free UUID is 32 characters; cap the suffix at both UUID length and the 40-character column limit.
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        int suffixLength = Math.min(randomPart.length(), 40 - prefix.length());
        if (suffixLength <= 0) throw new ServiceException("流程编码前缀长度超出限制");
        String code = prefix + randomPart.substring(0, suffixLength);
        var nodes = new ArrayList<Map<String,Object>>();
        int end = setup.steps().size() + 2;
        nodes.add(node(0, end, "0", "开始", "", false, vertical));
        nodes.add(node(1, end, "1", "申请人", "${initiator}", false, vertical));
        for (int index = 0; index < setup.steps().size(); index++) {
            Step step = setup.steps().get(index);
            if (step == null) throw new ServiceException("审批节点不能为空");
            String label = Objects.toString(step.name(), "").trim();
            String assignee = Objects.toString(step.assignee(), "");
        // 动态负责人在流程运行时解析，其他候选项仍在配置时校验。
        // Resolve the initiator's department leader at runtime; validate static assignee rules during configuration.
            // Resolve the initiator's org leader at runtime and validate static assignees at setup.
            var selections = Arrays.asList(assignee.split(",", -1));
            boolean validSelections = selections.size() <= 100 && selections.stream().allMatch(selection ->
                INITIATOR_ORG_LEADER.equals(selection) || selection.matches("(?:(?:role|post|org):)?[0-9]{1,19}"));
            if (label.isEmpty() || label.length() > 40 || !validSelections) {
                throw new ServiceException("请填写节点名称并选择审批人");
            }
            for (String selection : selections) {
            if (INITIATOR_ORG_LEADER.equals(selection)) continue;
            boolean active = assigneeService.fetchUsersByStorageIds(selection).stream().anyMatch(user ->
                Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0')
                    """, Boolean.class, user.getUserId())));
            if (!active) {
                String selectionName = selection;
                if (selection.startsWith("post:")) {
                    var names = jdbc.queryForList("SELECT post_name FROM sys_post WHERE post_id::text=?", String.class, selection.substring(5));
                    selectionName = "岗位「" + (names.isEmpty() ? selection : names.getFirst()) + "」";
                } else if (selection.startsWith("role:")) {
                    var names = jdbc.queryForList("SELECT role_name FROM sys_role WHERE role_id::text=?", String.class, selection.substring(5));
                    selectionName = "角色「" + (names.isEmpty() ? selection : names.getFirst()) + "」";
                } else if (selection.startsWith("org:")) {
                    var names = jdbc.queryForList("SELECT org_name FROM sys_org WHERE org_id::text=?", String.class, selection.substring(4));
                    selectionName = "部门「" + (names.isEmpty() ? selection : names.getFirst()) + "」";
                }
                throw new ServiceException("节点「" + label + "」的" + selectionName + "没有启用的审批人员，请先在人员信息中配置后重试");
            }
            }
            List<String> copyUsers = step.copyUsers() == null ? List.of() : step.copyUsers().stream().distinct().toList();
            if (copyUsers.size() > 100 || copyUsers.stream().anyMatch(user -> user == null || !user.matches("[0-9]{1,19}"))) throw new ServiceException("抄送人员无效");
            for (String user : copyUsers) {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_user WHERE user_id::text=? AND status='0' AND del_flag='0')", Boolean.class, user))) throw new ServiceException("抄送人员不存在或已停用");
            }
            var approvalNode = node(index + 2, end, "1", label, assignee, step.unanimous(), vertical);
            if (!copyUsers.isEmpty()) approvalNode.put("ext", JsonUtils.toJsonString(List.of(Map.of("code", "CopySettingEnum", "value", String.join(",", copyUsers)))));
            nodes.add(approvalNode);
        }
        nodes.add(node(end, end, "2", "结束", "", false, vertical));
        var json = Map.of("flowCode", code, "flowName", name, "modelValue", model,
            "version", "1", "formCustom", "N", "nodeList", nodes);
        FlowEngine.defService().importDef(JsonUtils.parseObject(JsonUtils.toJsonString(json), DefJson.class));
        Long id = jdbc.queryForObject("SELECT id FROM flow_definition WHERE flow_code=? AND del_flag='0'", Long.class, code);
        if (!Boolean.TRUE.equals(setup.draft())) cn.dev33.satoken.stp.StpUtil.checkPermission("workflow:definition:publish");
        if (!Boolean.TRUE.equals(setup.draft()) && !definitionService.publish(id)) throw new ServiceException("发布审批流程失败");
        return R.ok(Map.of("id", String.valueOf(id)));
    }
    /** 解析 Warm-Flow 节点扩展信息中的审批节点配置。 / Parse editable approval-node settings from Warm-Flow node extensions. */

    private Map<String,Object> node(int index, int end, String type, String label, String assignee, boolean unanimous, boolean vertical) {
        var edges = new ArrayList<Map<String,String>>();
        if (index < end) edges.add(Map.of("skipType", "PASS", "nowNodeCode", "n" + index, "nextNodeCode", "n" + (index + 1)));
        if (index > 1 && index < end) edges.add(Map.of("skipType", "REJECT", "nowNodeCode", "n" + index, "nextNodeCode", "n1"));
        // 初始坐标随模型横向或纵向排列。 / Lay out the initial nodes horizontally or vertically for the selected model.
        String position = vertical ? "160," + (200 + index * 120) : (160 + index * 160) + ",200";
        return new HashMap<>(Map.of("nodeType", type, "nodeCode", "n" + index, "nodeName", label,
            "permissionFlag", assignee, "nodeRatio", unanimous ? "100.000" : "0.000",
            "coordinate", position + "|" + position,
            "version", "1", "formCustom", "N", "skipList", edges));
    }
}
