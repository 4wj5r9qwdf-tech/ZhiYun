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

@RestController
@RequestMapping("/approval/customer-orders/setup")
@RequiredArgsConstructor
@SaCheckPermission({"workflow:definition:add", "workflow:definition:publish", "system:config:edit"})
public class OrderApprovalSetupController {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<IFlwDefinitionService> definitions;
    private final ObjectProvider<IFlwTaskAssigneeService> assignees;

    public record Step(String name, String assignee, boolean unanimous, List<String> copyUsers) {}
    public record Setup(String businessType, String name, List<Step> steps) {}

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
                SELECT 'org:' || org_id, org_name, '部门', org_id::text, parent_id::text FROM sys_org WHERE status='0' AND del_flag='0'
            ) choices ORDER BY kind,label,value
            """));
    }

    /** 创建独立流程定义；业务对象的启用流程由绑定页面单独选择。 */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @Log(title = "新增审批流程", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public R<Void> setup(@RequestBody Setup setup) {
        if (!"customer_order".equals(setup.businessType())) throw new ServiceException("该单据暂未接入审批");
        var definitionService = definitions.getIfAvailable();
        var assigneeService = assignees.getIfAvailable();
        if (definitionService == null || assigneeService == null) throw new ServiceException("审批引擎未启用");
        String name = Objects.toString(setup.name(), "").trim();
        if (name.isEmpty() || name.length() > 40 || setup.steps() == null
            || setup.steps().isEmpty() || setup.steps().size() > 12) {
            throw new ServiceException("请填写流程名称，并设置 1 至 12 个审批节点");
        }
        String code = "order_" + UUID.randomUUID().toString().replace("-", "");
        var nodes = new ArrayList<Map<String,Object>>();
        int end = setup.steps().size() + 2;
        nodes.add(node(0, end, "0", "开始", "", false));
        nodes.add(node(1, end, "1", "申请人", "${initiator}", false));
        for (int index = 0; index < setup.steps().size(); index++) {
            Step step = setup.steps().get(index);
            if (step == null) throw new ServiceException("审批节点不能为空");
            String label = Objects.toString(step.name(), "").trim();
            String assignee = Objects.toString(step.assignee(), "");
            if (label.isEmpty() || label.length() > 40 || !assignee.matches("(?:(?:(?:role|post|org):)?[0-9]{1,19})(?:,(?:(?:role|post|org):)?[0-9]{1,19}){0,99}")) {
                throw new ServiceException("请填写节点名称并选择审批人");
            }
            for (String selection : assignee.split(",")) {
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
            var approvalNode = node(index + 2, end, "1", label, assignee, step.unanimous());
            if (!copyUsers.isEmpty()) approvalNode.put("ext", JsonUtils.toJsonString(List.of(Map.of("code", "CopySettingEnum", "value", String.join(",", copyUsers)))));
            nodes.add(approvalNode);
        }
        nodes.add(node(end, end, "2", "结束", "", false));
        var json = Map.of("flowCode", code, "flowName", name, "modelValue", "CLASSICS",
            "version", "1", "formCustom", "N", "nodeList", nodes);
        FlowEngine.defService().importDef(JsonUtils.parseObject(JsonUtils.toJsonString(json), DefJson.class));
        Long id = jdbc.queryForObject("SELECT id FROM flow_definition WHERE flow_code=? AND del_flag='0'", Long.class, code);
        if (!definitionService.publish(id)) throw new ServiceException("发布审批流程失败");
        return R.ok();
    }

    private Map<String,Object> node(int index, int end, String type, String label, String assignee, boolean unanimous) {
        var edges = new ArrayList<Map<String,String>>();
        if (index < end) edges.add(Map.of("skipType", "PASS", "nowNodeCode", "n" + index, "nextNodeCode", "n" + (index + 1)));
        if (index > 1 && index < end) edges.add(Map.of("skipType", "REJECT", "nowNodeCode", "n" + index, "nextNodeCode", "n1"));
        return new HashMap<>(Map.of("nodeType", type, "nodeCode", "n" + index, "nodeName", label,
            "permissionFlag", assignee, "nodeRatio", unanimous ? "100.000" : "0.000",
            "coordinate", (160 + index * 160) + ",200|" + (160 + index * 160) + ",200",
            "version", "1", "formCustom", "N", "skipList", edges));
    }
}
