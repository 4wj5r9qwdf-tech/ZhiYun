# 审批中心 Skill

## 适用场景

用户要求查询本人审批待办、查看本人已办、查看本人发起的审批进度和抄送记录、打开审批关联单据，或维护业务单据与已发布审批流程的绑定时使用。

不用于流程定义图形设计、管理员全量任务监控、代替他人审批、钉钉消息、抄送收件箱查询、催审、委派/转办/加签或终止流程。客户订单和物料需求计划的完整业务审批联动仍需单独验收。

## 使用前

1. 先阅读[通用 MCP 调用指南](../MCP调用指南.md)，连接 MCP 后以 `tools/list` 核对工具名和 `inputSchema`。
2. 始终使用当前员工本人的 ERP 会话；不索取或保存密码/Token，不切换超管代办。
3. 所有任务、流程实例及业务对象 ID 都按十进制字符串传入。ID 必须来自本次员工权限下的工具结果，不能根据编号或猜测生成。
4. MCP 参数以运行时 `inputSchema` 为准。未填写的可选筛选参数应省略，不传 `null` 或额外的 `body`/`filter` 包装。

## 工具及工作流

### 我的待办

- `approval_my_todo_list`：查询本人待办分页，可选 `flowName`、`pageNum`、`pageSize`。页码默认 1，每页默认 20，最大 100。
- `approval_my_todo_document`：打开待办关联的客户订单或物料需求计划。需要 `taskId`，同时需要来源业务的查看权限和数据范围。
- `approval_task_back_nodes`：查询当前任务允许退回的节点；需要 `taskId` 和当前待办的 `nodeCode`。
- `approval_task_approve`：通过当前待办；参数 `taskId` 必填，`opinion` 可选。
- `approval_task_return`：退回当前待办；需先取得可退回节点列表，员工选择节点后传入 `taskId`、返回结果中的 `nodeCode` 和非空 `opinion`。

办理前先读待办和来源单据，核实单据类型、单据编号和当前审批节点。通过或退回前，把动作、目标节点和意见完整展示给员工，并取得针对这张单据、这一次动作的明确确认。退回节点必须来自 `approval_task_back_nodes`，不能自造节点编码。后端继续校验当前办理资格、任务状态和节点按钮配置。

通过示例：

```json
{
  "name": "approval_task_approve",
  "arguments": {
    "taskId": "123456789012345678",
    "opinion": "数量和交期已核对，同意。"
  }
}
```

### 我的已办

- `approval_my_done_list`：查询本人已办理的任务分页，可选 `flowName`、`pageNum`、`pageSize`。
- `approval_my_done_document`：按历史任务 ID 查看来源单据和审批时间轴；只允许实际办理过该节点的员工查看。

已办记录不等于当前仍可操作。不要对已完成任务重复发起办理动作。

### 我发起的

- `approval_my_initiated_list`：查询本人发起的审批、当前状态、节点、处理人及上次催审时间。当前返回本人全部匹配记录，没有分页参数。
- `approval_my_initiated_detail`：查看本人实例的审批时间轴、历史意见、抄送记录和业务摘要。
- `approval_my_initiated_document`：穿透查看这条审批对应的客户订单或物料需求计划。

列表或详情为空时，只能说明当前员工权限范围内没有返回记录；不要据此判断系统中不存在该审批。抄送记录表示已产生抄送，不代表接收人已阅读。

查询并查看来源单据示例：

```json
{
  "name": "approval_my_initiated_list",
  "arguments": {}
}
```

```json
{
  "name": "approval_my_initiated_document",
  "arguments": { "instanceId": "123456789012345678" }
}
```

### 审批对象配置

- `approval_binding_list`：查询已接入业务对象、当前流程绑定和可选的已发布流程。
- `approval_binding_set`：设置绑定。只从 `approval_binding_list` 返回的 `flows` 中选择匹配的已发布流程。
- `approval_binding_remove`：仅可解除 `customer_order`、`material_demand_plan` 的常规审批绑定。反审核流程不可解绑。

配置流程前，先读取当前绑定，确认目标业务类型、旧流程、新流程及生效范围。绑定变化只影响之后发起的单据，已经运行的实例继续按原流程办理。设置或解除前必须把变化展示给员工并取得本次明确确认。

设置客户订单流程示例：

```json
{
  "name": "approval_binding_set",
  "arguments": {
    "businessType": "customer_order",
    "flowCode": "order_example"
  }
}
```

可用业务类型：

| 代码 | 业务对象 |
|---|---|
| `customer_order` | 客户订单 |
| `material_demand_plan` | 物料需求计划 |
| `customer_order_unapprove` | 客户订单反审核 |
| `material_demand_plan_unapprove` | 物料需求计划反审核 |

## 来源单据和权限

当前支持的来源单据包括客户订单、物料需求计划及其反审核审批实例。打开来源单据时，服务端会再次核对相应的业务读取权限；物料需求计划还会核对其中物料分类的人员范围。审批权限不能替代业务单据权限，也不能扩大分类数据范围。

权限不足收到 403 时，说明该员工当前没有所需操作权限；停止操作并告知用户，不切换其他账号。实例归属不符、待办失效或单据不可见时，按服务端返回的提示处理，不尝试其他接口、数据库或管理员身份绕过。

## MCP 工具索引

| 工具名 | 读写 | 关键参数 |
|---|---|---|
| `approval_my_todo_list` | 读 | 可选 `flowName`、`pageNum`、`pageSize` |
| `approval_my_done_list` | 读 | 可选 `flowName`、`pageNum`、`pageSize` |
| `approval_my_initiated_list` | 读 | 无 |
| `approval_my_initiated_detail` | 读 | `instanceId` 字符串 |
| `approval_my_todo_document` | 读 | `taskId` 字符串 |
| `approval_my_done_document` | 读 | `taskId` 字符串 |
| `approval_my_initiated_document` | 读 | `instanceId` 字符串 |
| `approval_task_back_nodes` | 读 | `taskId`、`currentNodeCode` |
| `approval_task_approve` | 写 | `taskId`、可选 `opinion` |
| `approval_task_return` | 写 | `taskId`、有效 `nodeCode`、非空 `opinion` |
| `approval_binding_list` | 读 | 无 |
| `approval_binding_set` | 写 | `businessType`、已发布 `flowCode` |
| `approval_binding_remove` | 写 | `businessType` |

写工具每次调用都需重新确认。2026-11-20 开发服务已通过 `tools/list` 发现全部 13 个审批工具，并通过 `read_zhiyun_skill` 实际读取本 Skill；普通员工业务调用和权限边界仍待核验。

## API 参考

详细方法、参数、权限、来源单据和返回边界见[审批中心 API 契约](../../API/审批中心API.md)。

退回节点查询沿用任务详情的数据范围。`currentNodeCode`（REST 路径参数 `nowNodeCode`）必须与服务端当前节点一致。节点已变化时，请刷新待办后重新查询。
