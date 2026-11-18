# 物料需求计划 API

> 功能分组：物料需求计划 `material_demand_plan`
> 当前状态：REST 与审批联动代码已实现，前后端构建通过，WSL `zhizhun_erp` 开发库迁移已应用；待用户验收。MCP 工具未注册，智能体 Skill 按验收后流程补充。
> 业务边界：承接已审核客户订单的算料快照，不创建采购单、不收发库存；需求数量始终以库存单位保存。

## 一、字段和状态

### 主单字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 计划主键。数据库 bigint 在 JSON 中以字符串传输，前端不得转成 JS number。 |
| `planNo` | string | 唯一计划单号，系统生成。 |
| `orderId` / `sourceOrderNo` | string | 来源客户订单主键及单号快照。 |
| `orderRevision` | integer | 计划依据的订单修订号。 |
| `customerId` / `document` | string / object | 客户主键快照及订单、PO、交期、默认供料等只读单头快照。 |
| `status` | enum | `draft` 草稿、`submitted` 审批中、`approved` 已审核、`cancelled` 已取消。 |
| `calculationStatus` | enum | `pending` 待算料、`ready` 已有完整快照、`incomplete` 需核对、`outdated` 来源已过期。 |
| `calculationMessage` | string | 缺少 BOM、套装配套无法定位、用料缺失等不能提交的原因。 |
| `revision` | integer | 单据并发修订号；每次数量保存或单据流转递增。 |

### 需求明细

每行保留来源 PO、PO 交期、款号/套装规格/子款、款式颜色尺码与订单数量、BOM 版本、用料类型、物料分类/编码/名称、物料规格、物料颜色、供料方式、库存单位、原始需求量和计划需求量。套装子款必须按订单快照中的子款 ID、颜色 ID、尺码 ID 唯一定位；无法唯一定位的历史数据标记为需核对，不猜测配套关系。

`originalQuantity` 与 `plannedQuantity` 均使用 `inventoryUnitName` 库存单位。采购单位参考依据订单算料时保存的物料采购单位、换算模式和系数快照计算；固定换算显示建议数，按实际换算显示“待实际换算”，不向上取整成包装数。

## 二、接口

所有 bigint 主键在请求和响应中使用字符串。接口统一返回 `{ "code": 200, "msg": "操作成功", "data": ... }`。失败时使用项目统一错误结构并在 `msg` 中说明原因。

### 查询计划列表

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `GET /order/material-demand-plans/list` |
| 读/写类型 | 只读 |
| 参数 | `keyword` string 可选，匹配计划号/订单号/客户；`status` string 可选；`calculationStatus` string 可选；`poNumber` string 可选。 |
| 返回 | 计划列表，含 `id`、`planNo`、`orderId`、`sourceOrderNo`、`customerName`、`poNumbers`、`lineCount`、`materialCount`、两种状态、`calculationMessage`、制单人及时间。 |
| 权限 | `order:material-demand-plan:list`；服务端逐张计划检查所有物料分类范围，越权时整张请求报错，不静默漏行。 |
| 幂等/副作用/前置/审批 | 幂等只读；无副作用；无业务前置；不触发审批。 |

`keyword`、`status`、`calculationStatus`、`poNumber` 可省略或传空值；REST 参数缺失及内部调用传入 `null`、空白文本时均按不增加筛选条件处理。

### 查询计划详情

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `GET /order/material-demand-plans/{id}` |
| 读/写类型 | 只读 |
| 参数 | 路径 `id` string，物料需求计划主键。 |
| 返回 | 主单、来源订单/PO 快照、`items` 明细、`summary` 按物料/规格/颜色/供料方式/单位汇总、`adjustments` 数量调整历史。 |
| 权限 | `order:material-demand-plan:list`；任一明细分类不可见则整个详情拒绝，并提示调整人员分类范围。历史明细缺分类时仅超管可见以便修复。 |
| 幂等/副作用/前置/审批 | 幂等只读；无副作用；无业务前置；不触发审批。 |

### 查询可补生成计划的订单

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `GET /order/material-demand-plans/eligible-orders` |
| 读/写类型 | 只读 |
| 参数 | `keyword` string 可选，匹配已审核客户订单号或订单快照；最多返回 200 张未有有效计划的已审核订单。 |
| 返回 | `orderId`、`orderNo`、`customerId`、`customerName`、`revision`、`orderDate`、`poCount`。 |
| 权限 | 同时需要 `order:material-demand-plan:list` 与 `order:material-demand-plan:edit`。 |
| 幂等/副作用/前置/审批 | 幂等只读；无副作用；订单须已审核且无有效计划；不触发审批。 |

### 从已审核订单补生成草稿

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `POST /order/material-demand-plans/generate/{orderId}` |
| 读/写类型 | 写 |
| 参数 | 路径 `orderId` string；无请求体。 |
| 返回 | 新建或已存在的有效计划详情。 |
| 权限 | 同时需要 `order:material-demand-plan:list` 与 `order:material-demand-plan:edit`。 |
| 幂等性 | 按来源订单幂等；重复调用返回同一张未取消计划，不重复建单。 |
| 副作用 | 新建计划主单及从完整算料快照展开的明细，并记录业务审计；没有完整算料时生成“待算料/需核对”空明细主单。 |
| 前置条件 | 来源订单必须已审核；来源订单由数据库行锁保护，单个订单至多一张未取消计划。 |
| 是否需审批 | 创建本身不审批；随后提交时按物料需求计划审批绑定决定走审批流或直接审核。 |

### 保存计划数量调整

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `PUT /order/material-demand-plans/{id}/quantities` |
| 读/写类型 | 写 |
| 请求体 | `revision` integer 必填；`reason` string 必填，最多 500 字；`items` array 必填，每项含 `itemId` string 和 `plannedQuantity` decimal。数量按该明细库存单位计，范围 `0` 至非负最大 24 位精度、最多 8 位小数。 |
| 示例请求体 | `{"revision":1,"reason":"客户确认本批次损耗较低，按最新用量修正","items":[{"itemId":"2114531897794859001","plannedQuantity":120.5}]}` |
| 返回 | 保存后的计划详情及追加后的调整历史。 |
| 权限 | `order:material-demand-plan:list` + `order:material-demand-plan:edit`；服务端检查每行所属计划和分类范围。 |
| 幂等性 | 不提供幂等键；使用 `revision` 防止重复/过期覆盖。超时后先重新查询详情，不能原样盲重试。 |
| 副作用 | 更新计划需求数量和修订号；每个实际变化行追加不可变的原值/新值/原因/操作人/时间记录，并写业务审计。 |
| 前置条件 | 计划必须为 `draft` 且算料状态为 `ready`；空请求或数值未变化不写调整历史。 |
| 是否需审批 | 调整不触发审批；计划提交后审批由共享审批绑定控制。 |

### 提交、撤回、审核、退回或取消

| 项目 | 契约 |
|---|---|
| 方法 + 路径 | `POST /order/material-demand-plans/{id}/transition` |
| 读/写类型 | 写 |
| 请求体 | `revision` integer 必填；`action` enum 必填；`reason` string，可选但 `return`、`cancel` 必填，最多 500 字。 |
| 动作 | `submit` 草稿提交；`unsubmit` 无审批实例时撤回；`approve` 无绑定流程时直接审核；`return` 无绑定流程时退回草稿；`cancel` 取消草稿。 |
| 示例请求体 | `{"revision":2,"action":"submit","reason":"计划已核对"}` |
| 返回 | 操作后的计划详情。 |
| 权限 | `submit/unsubmit` 需要 `order:material-demand-plan:edit`；`approve/return` 需要 `order:material-demand-plan:review`；`cancel` 需要 `order:material-demand-plan:cancel`。 |
| 幂等性 | 不提供幂等键；`revision` 与状态前置检查拒绝重复流转。超时后先查询当前单据状态。 |
| 副作用 | 修改单据状态、修订号和审核信息并写业务审计；提交时若绑定有效审批流程，启动 Warm-Flow 并建立业务单据关联。 |
| 前置条件 | 提交要求完整算料快照且至少一行明细；审批中的有流程单据必须在我的待办办理；无绑定流程时按直接审核权限处理。 |
| 是否需审批 | 取决于 `material_demand_plan` 当前审批对象绑定：有已发布流程时由流程处理；未绑定时由有审核权限的人员处理。 |

## 三、订单与审批联动

- 客户订单审核通过时，同一事务幂等生成物料需求计划草稿；没有已保存算料快照仍生成“待算料”主单。
- 已审核订单调用 `POST /order/customer-orders/{id}/demand/calculate` 保存完整算料快照后，同一事务同步未调整的计划草稿；有人工调整或已提交/审核的计划不会被静默覆盖。
- 审批中心 `businessType` 使用 `material_demand_plan`。待办、已办、抄送、我发起的详情和铃铛通知继续使用共享审批服务；业务详情必须同时校验对应计划权限与物料分类范围。
- 当前没有物料需求计划 MCP 工具；本 REST 文档不表示智能体已可发现或调用该功能。用户验收后再补 Skill/MCP 收尾。

## 四、增量 SQL

当前开发迁移为 `sql/物料需求计划_20261117.sql`，含计划主单兼容列、需求明细、调整历史、中文表/字段注释及菜单/操作权限种子。按项目规范，用户验收前不合并入 `sql/主建表.sql`，也不清理增量脚本。
