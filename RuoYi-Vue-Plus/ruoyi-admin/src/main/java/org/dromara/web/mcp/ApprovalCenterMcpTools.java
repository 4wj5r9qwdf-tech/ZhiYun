package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.web.controller.ApprovalBindingController;
import org.dromara.web.controller.CustomerOrderApprovalController;
import org.dromara.workflow.controller.FlwTaskController;
import org.dromara.workflow.domain.bo.BackProcessBo;
import org.dromara.workflow.domain.bo.CompleteTaskBo;
import org.dromara.workflow.domain.bo.FlowTaskBo;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审批中心 MCP 工具；所有查询和办理复用受权限保护的审批控制器及业务单据穿透校验。
 * Approval-center MCP tools reuse permission-protected workflow controllers and linked-document access checks.
 */
@Component
@RequiredArgsConstructor
public class ApprovalCenterMcpTools {

    private final FlwTaskController taskController;
    private final CustomerOrderApprovalController businessApprovalController;
    private final ApprovalBindingController bindingController;

    /** 查询当前员工的审批待办分页。 / List the current employee's pending approval tasks. */
    @McpTool(name = "approval_my_todo_list", description = "查询当前员工的审批待办分页；仅返回本人待办，需 approval:waiting:list 权限")
    public R<?> todoList(
        @McpToolParam(description = "可选流程名称关键字；省略表示不按流程名称筛选", required = false) String flowName,
        @McpToolParam(description = "页码，从1开始；省略默认为1", required = false) Integer pageNum,
        @McpToolParam(description = "每页条数，1到100；省略默认为20", required = false) Integer pageSize) {
        StpUtil.checkPermission("approval:waiting:list");
        return taskController.pageByTaskWait(taskFilter(flowName), page(pageNum, pageSize));
    }

    /** 查询当前员工已经办理的审批任务。 / List approval tasks completed by the current employee. */
    @McpTool(name = "approval_my_done_list", description = "查询当前员工已办理的审批任务分页；仅返回本人已办，需 approval:finished:list 权限")
    public R<?> doneList(
        @McpToolParam(description = "可选流程名称关键字；省略表示不按流程名称筛选", required = false) String flowName,
        @McpToolParam(description = "页码，从1开始；省略默认为1", required = false) Integer pageNum,
        @McpToolParam(description = "每页条数，1到100；省略默认为20", required = false) Integer pageSize) {
        StpUtil.checkPermission("approval:finished:list");
        return taskController.pageByTaskFinish(taskFilter(flowName), page(pageNum, pageSize));
    }

    /** 查询当前员工发起的审批实例。 / List approval instances initiated by the current employee. */
    @McpTool(name = "approval_my_initiated_list", description = "查询当前员工发起的审批及状态、当前节点和处理人；服务端仅返回本人发起的记录，需 approval:initiated:list 权限")
    public R<?> initiatedList() {
        StpUtil.checkPermission("approval:initiated:list");
        return businessApprovalController.initiated();
    }

    /** 读取本人发起审批的时间轴及抄送记录。 / Read the timeline and recorded copies for an approval initiated by the caller. */
    @McpTool(name = "approval_my_initiated_detail", description = "查询本人发起的审批时间轴、历史意见、抄送记录和业务单据摘要；只能查询本人发起的实例，需 approval:initiated:list 权限")
    public R<?> initiatedDetail(
        @McpToolParam(description = "审批实例ID，十进制字符串；从 approval_my_initiated_list 返回值复制") String instanceId) {
        StpUtil.checkPermission("approval:initiated:list");
        return businessApprovalController.initiatedDetail(id(instanceId, "审批实例ID"));
    }

    /** 从本人待办打开来源单据，单据权限和分类范围仍由业务服务端复核。 / Open the source document from the caller's task while rechecking document permissions and category scope. */
    @McpTool(name = "approval_my_todo_document", description = "查询审批待办关联的客户订单或物料需求计划详情；必须是当前有效待办，且还需对应业务单据查看权限及数据范围，需 approval:waiting:list 权限")
    public R<?> todoDocument(
        @McpToolParam(description = "待办任务ID，十进制字符串；从 approval_my_todo_list 返回值复制") String taskId) {
        StpUtil.checkPermission("approval:waiting:list");
        return businessApprovalController.detail(id(taskId, "待办任务ID"));
    }

    /** 从本人已办记录打开来源单据。 / Open the linked source document from a task completed by the caller. */
    @McpTool(name = "approval_my_done_document", description = "查询本人已办记录关联的客户订单或物料需求计划详情和审批时间轴；需 approval:finished:list 及对应业务单据查看权限")
    public R<?> doneDocument(
        @McpToolParam(description = "已办历史任务ID，十进制字符串；从 approval_my_done_list 返回值复制") String taskId) {
        StpUtil.checkPermission("approval:finished:list");
        return businessApprovalController.finishedDetail(id(taskId, "已办任务ID"));
    }

    /** 从本人发起记录打开来源单据。 / Open the linked business document from an instance initiated by the caller. */
    @McpTool(name = "approval_my_initiated_document", description = "查询本人发起的审批所关联的客户订单或物料需求计划详情；服务端校验发起人身份、业务查看权限及物料分类范围，需 approval:initiated:list 权限")
    public R<?> initiatedDocument(
        @McpToolParam(description = "审批实例ID，十进制字符串；从 approval_my_initiated_list 返回值复制") String instanceId) {
        StpUtil.checkPermission("approval:initiated:list");
        return businessApprovalController.initiatedDocument(id(instanceId, "审批实例ID"));
    }

    /** 按当前节点查询可退回的前置节点。 / List valid prior nodes to which the current task can be returned. */
    @McpTool(name = "approval_task_back_nodes", description = "查询当前审批任务可退回的节点；任务必须仍有效且在调用人可读范围内；节点编码须匹配当前节点，变化后刷新待办；需 approval:waiting:list 权限")
    public R<?> backNodes(
        @McpToolParam(description = "待办任务ID，十进制字符串") String taskId,
        @McpToolParam(description = "当前节点编码，从待办任务记录的 nodeCode 复制") String currentNodeCode) {
        StpUtil.checkPermission("approval:waiting:list");
        if (currentNodeCode == null || currentNodeCode.isBlank()) throw new ServiceException("当前节点编码不能为空");
        return taskController.getBackTaskNode(id(taskId, "待办任务ID"), currentNodeCode);
    }

    /** 通过当前待办；调用前必须展示单据、节点、意见并取得本次确认。 / Approve a current task only after presenting its document, node and opinion and receiving confirmation. */
    @McpTool(name = "approval_task_approve", description = "通过当前审批待办；调用前必须先读取待办和业务单据，向员工展示单据编号、单据类型、当前节点及审批意见，并取得本次明确确认；办理人由当前登录员工确定，需 approval:waiting:handle 权限")
    public R<?> approve(
        @McpToolParam(description = "待办任务ID，十进制字符串") String taskId,
        @McpToolParam(description = "审批意见；可省略，建议填写", required = false) String opinion) {
        StpUtil.checkPermission("approval:waiting:handle");
        CompleteTaskBo request = new CompleteTaskBo();
        request.setTaskId(id(taskId, "待办任务ID"));
        request.setMessage(cleanOpinion(opinion));
        request.setMessageType(List.of("1"));
        return taskController.completeTask(request);
    }

    /** 退回当前待办；目标必须来自可退回节点查询。 / Return the current task to a node returned by the valid-back-node query. */
    @McpTool(name = "approval_task_return", description = "退回当前审批待办；先调用 approval_task_back_nodes 并让员工选择目标节点，再展示单据、节点、目标节点及退回意见取得本次明确确认；需 approval:waiting:handle 权限")
    public R<?> returnTask(
        @McpToolParam(description = "待办任务ID，十进制字符串") String taskId,
        @McpToolParam(description = "退回目标节点编码，必须来自 approval_task_back_nodes 返回值") String nodeCode,
        @McpToolParam(description = "退回意见，必填") String opinion) {
        StpUtil.checkPermission("approval:waiting:handle");
        if (nodeCode == null || nodeCode.isBlank()) throw new ServiceException("请选择有效的退回节点");
        String message = cleanOpinion(opinion);
        if (message == null) throw new ServiceException("退回意见不能为空");
        BackProcessBo request = new BackProcessBo();
        request.setTaskId(id(taskId, "待办任务ID"));
        request.setNodeCode(nodeCode);
        request.setMessage(message);
        request.setMessageType(List.of("1"));
        return taskController.backProcess(request);
    }

    /** 查询审批对象到已发布流程的绑定关系。 / List business-object bindings to published workflow definitions. */
    @McpTool(name = "approval_binding_list", description = "查询已接入审批对象的流程绑定和可选已发布流程；需 approval:binding:list 权限")
    public R<?> bindingList() {
        StpUtil.checkPermission("approval:binding:list");
        return bindingController.list();
    }

    /** 设置业务审批对象流程；只接受与业务类型匹配的已发布流程。 / Bind a business approval object to a matching published workflow. */
    @McpTool(name = "approval_binding_set", description = "设置业务单据的审批流程绑定；调用前展示业务类型、原流程、新流程及对之后提交单据的影响并取得本次明确确认；只允许绑定匹配的已发布流程，需 approval:binding:edit 权限")
    public R<?> bindingSet(
        @McpToolParam(description = "审批业务类型：customer_order、material_demand_plan、customer_order_unapprove、material_demand_plan_unapprove") String businessType,
        @McpToolParam(description = "已发布流程编码；必须从 approval_binding_list 的 flows 中复制") String flowCode) {
        StpUtil.checkPermission("approval:binding:edit");
        return bindingController.save(businessType, new ApprovalBindingController.BindingRequest(flowCode));
    }

    /** 解除允许解绑的常规流程绑定；反审核流程不能解绑。 / Remove a supported normal approval binding; reversal workflows are mandatory and cannot be unbound. */
    @McpTool(name = "approval_binding_remove", description = "解除客户订单或物料需求计划的常规审批流程绑定；执行前展示业务类型、当前流程和之后提交单据的审核方式变化并取得本次明确确认；反审核流程不允许解绑，需 approval:binding:edit 权限")
    public R<?> bindingRemove(
        @McpToolParam(description = "仅支持 customer_order 或 material_demand_plan") String businessType) {
        StpUtil.checkPermission("approval:binding:edit");
        return bindingController.remove(businessType);
    }

    /** 创建仅使用当前员工权限范围的流程任务查询条件。 / Build a caller-scoped workflow query filter. */
    private FlowTaskBo taskFilter(String flowName) {
        FlowTaskBo filter = new FlowTaskBo();
        if (flowName != null && !flowName.isBlank()) filter.setFlowName(flowName.trim());
        return filter;
    }

    /** 应用有界分页，防止智能体一次读取整个任务历史。 / Apply bounded pagination to prevent reading an unbounded task history. */
    private PageQuery page(Integer pageNum, Integer pageSize) {
        int number = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : pageSize;
        if (number < 1 || size < 1 || size > 100) throw new ServiceException("分页参数无效：页码至少为1，每页条数须为1到100");
        return new PageQuery(size, number);
    }

    /** 只接受十进制字符串主键，避免 MCP JSON 数字丢失 bigint 精度。 / Accept decimal-string IDs to preserve bigint precision in JSON. */
    private Long id(String value, String label) {
        return McpInputValidator.requirePositiveId(value, label);
    }

    /** 清理空意见并限制意见长度。 / Normalize optional opinions and enforce a safe length. */
    private String cleanOpinion(String opinion) {
        if (opinion == null || opinion.isBlank()) return null;
        String value = opinion.trim();
        if (value.length() > 2000) throw new ServiceException("审批意见不能超过2000个字符");
        return value;
    }
}
