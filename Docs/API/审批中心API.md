# 审批中心 API 与 MCP 契约

> 更新日期：2026-11-20<br>
> 验收范围：我的待办、我发起的、我的已办、审批对象配置，以及审批列表/进度中的关联业务单据穿透。流程定义设计、管理端监控、抄送收件箱、催审和管理员代办不在本次验收范围。

## 权限与数据范围

| 能力 | 权限码 | 服务端约束 |
|---|---|---|
| 我的待办列表、详情与来源单据 | `approval:waiting:list` | 普通员工只查当前办理人的待办；超管按框架逻辑可查看待办。详情还校验来源单据权限和数据范围 |
| 待办审批、退回 | `approval:waiting:handle` | 办理人由当前登录员工确定；审批引擎校验任务状态、办理资格及节点操作配置 |
| 我的已办列表、详情及来源单据 | `approval:finished:list` | 普通员工只能查看本人实际办理的历史任务；业务详情再次检查业务权限和分类范围 |
| 我发起的列表、进度及来源单据 | `approval:initiated:list` | 只能查询当前登录员工发起的审批；来源业务详情再次检查对应业务权限和分类范围 |
| 审批对象配置读取 | `approval:binding:list` | 仅返回已接入业务类型、现有绑定和已发布的匹配流程 |
| 审批对象配置修改 | `approval:binding:edit` | 只允许绑定与业务类型匹配的已发布流程；解除绑定仅支持常规客户订单和物料需求计划 |

主键在 MCP JSON 参数中一律用十进制字符串，避免 bigint 被转成浮点数后失真。MCP 每次使用当前员工自己的会话；禁止使用超管 token 代办。

## REST 接口

### 我的待办与我的已办

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| `GET /workflow/task/pageByTaskWait` | 当前员工待办分页；常用参数 `pageNum`、`pageSize`、`flowName` | `approval:waiting:list` |
| `GET /workflow/task/pageByTaskFinish` | 当前员工已办分页；常用参数同待办 | `approval:finished:list` |
| `GET /workflow/task/getBackTaskNode/{taskId}/{nowNodeCode}` | 查询当前任务允许退回的节点 | `approval:waiting:list` |
| `POST /workflow/task/completeTask` | 通过待办，示例：`{"taskId":123,"message":"同意","messageType":["1"]}` | `approval:waiting:handle` |
| `POST /workflow/task/backProcess` | 退回待办；`taskId`、由退回节点查询得到的 `nodeCode`、`message` 必填 | `approval:waiting:handle` |
| `GET /approval/customer-orders/tasks/{taskId}` | 待办来源单据详情、流程节点和审批记录 | `approval:waiting:list`，并检查业务权限 |
| `GET /approval/customer-orders/finished/{taskId}` | 本人已办来源单据详情和时间轴 | `approval:finished:list`，并检查业务权限 |

`completeTask` 的办理人由服务器会话确定，客户端不能传 `handler` 或审批内部变量。退回目标必须先从 `getBackTaskNode` 的结果中选择；不能自行猜节点编码。节点按钮配置会限制抄送、退回、委派等操作。本期 MCP 暴露通过与退回，不开放改派、加签、终止等管理操作。

### 我发起的

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| `GET /approval/customer-orders/initiated` | 查询当前员工发起的审批及状态、当前节点、处理人、上次催审时间 | `approval:initiated:list` |
| `GET /approval/customer-orders/initiated/{instanceId}` | 查询本人审批的时间轴、审批意见、抄送记录和业务单据摘要 | `approval:initiated:list` |
| `GET /approval/customer-orders/initiated/{instanceId}/document` | 穿透到本人审批关联的客户订单或物料需求计划；再次校验业务权限 | `approval:initiated:list`，并检查业务权限 |

抄送时间轴只说明已生成的抄送记录，不表示接收人已阅读。来源单据目前支持客户订单、物料需求计划及其反审核审批实例；其他业务类型返回不支持提示。

### 审批对象配置

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| `GET /system/approval-bindings` | 返回业务类型、当前绑定和可选已发布流程 | `approval:binding:list` |
| `PUT /system/approval-bindings/{businessType}` | 设置流程；请求体 `{"flowCode":"order_..."}` | `approval:binding:edit` |
| `DELETE /system/approval-bindings/{businessType}` | 解除常规业务流程绑定，仅支持 `customer_order`、`material_demand_plan` | `approval:binding:edit` |

业务类型：`customer_order`、`material_demand_plan`、`customer_order_unapprove`、`material_demand_plan_unapprove`。反审核类型必须持续绑定流程，服务器拒绝解除。设置绑定只影响之后提交的单据，既有流程实例继续使用原流程定义。可选流程必须已发布、启用且与业务类型前缀匹配。

## MCP 工具

工具在 `ApprovalCenterMcpTools` 注册；权限由 MCP 工具过滤和后端控制器共同校验。2026-11-20 审批中心接入后曾发现 151 个工具，人员档案工具加入并重启服务后最新共发现 158 个，其中审批中心工具仍为 13/13；审批工具参数 schema 已逐项检查，并通过 `read_zhiyun_skill` 实际读取审批 Skill。待办/已办等工具的实际员工业务调用和普通账号权限边界尚未实调。

| 工具名 | 用途 | 权限 |
|---|---|---|
| `approval_my_todo_list` | 当前员工待办分页 | `approval:waiting:list` |
| `approval_my_done_list` | 当前员工已办分页 | `approval:finished:list` |
| `approval_my_initiated_list` | 当前员工发起的审批 | `approval:initiated:list` |
| `approval_my_initiated_detail` | 本人发起审批的时间轴、审批意见和抄送记录 | `approval:initiated:list` |
| `approval_my_todo_document` | 待办关联来源单据 | `approval:waiting:list` 及业务权限 |
| `approval_my_done_document` | 已办关联来源单据 | `approval:finished:list` 及业务权限 |
| `approval_my_initiated_document` | 我发起的审批关联来源单据 | `approval:initiated:list` 及业务权限 |
| `approval_task_back_nodes` | 当前待办可退回节点 | `approval:waiting:list` |
| `approval_task_approve` | 通过本人当前待办 | `approval:waiting:handle` |
| `approval_task_return` | 将本人当前待办退回到有效节点 | `approval:waiting:handle` |
| `approval_binding_list` | 查询审批对象与已发布流程 | `approval:binding:list` |
| `approval_binding_set` | 设置业务流程绑定 | `approval:binding:edit` |
| `approval_binding_remove` | 解除支持的常规流程绑定 | `approval:binding:edit` |

### MCP 参数示例

查询待办第一页：

```json
{
  "name": "approval_my_todo_list",
  "arguments": { "pageNum": 1, "pageSize": 20 }
}
```

打开列表中任务对应的业务单据（把真实任务 ID 按字符串复制）：

```json
{
  "name": "approval_my_todo_document",
  "arguments": { "taskId": "123456789012345678" }
}
```

查询我发起的审批进度和来源单据：

```json
{
  "name": "approval_my_initiated_detail",
  "arguments": { "instanceId": "123456789012345678" }
}
```

```json
{
  "name": "approval_my_initiated_document",
  "arguments": { "instanceId": "123456789012345678" }
}
```

按有效节点退回：先调用 `approval_task_back_nodes`，让员工选择其返回的 `nodeCode`，然后展示单据类型/编号、当前节点、目标节点和退回意见，取得本次明确确认后调用：

```json
{
  "name": "approval_task_return",
  "arguments": {
    "taskId": "123456789012345678",
    "nodeCode": "user_selected_node_code",
    "opinion": "请补充交期依据后重新提交"
  }
}
```

配置流程时先查询并从 `flows` 结果复制 `flowCode`，向员工展示旧绑定、新绑定及生效范围，取得确认后再调用：

```json
{
  "name": "approval_binding_set",
  "arguments": {
    "businessType": "customer_order",
    "flowCode": "order_example"
  }
}
```

`approval_task_approve`、`approval_task_return`、`approval_binding_set` 和 `approval_binding_remove` 都是写操作。智能体必须先读取所需详情、展示明确变更并取得员工对本次动作的确认；不得将一次确认复用到其他单据或配置。`approval_my_initiated_list` 当前返回本人全部匹配记录，未分页；若调用数据量较大，应分批按业务流程改造后再使用，不得将未提供的分页参数塞进工具调用。

## 状态边界

- 用户已验收上述页面及页面内单据穿透；此项不代表客户订单/需求计划完整业务审批联动验收。
- 本次已补 REST 穿透接口、MCP 工具代码与 Skill，后端 Maven 打包通过；开发服务的 `tools/list` 发现和 Skill 读取已验证，实际员工业务调用及普通账号权限边界尚未验证，分别记录、不互相代替。
- 当前员工权限不足返回 403；不得改用超管身份继续。审批详情即使从合法待办进入，也必须拥有对应业务单据查看权限和分类数据范围。

退回节点查询沿用任务详情的数据范围。`currentNodeCode`（REST 路径参数 `nowNodeCode`）必须与服务端当前节点一致。节点已变化时，请刷新待办后重新查询。
