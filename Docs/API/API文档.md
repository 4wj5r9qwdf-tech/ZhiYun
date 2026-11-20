<!--
文件用途：API/MCP 接口和运行时状态总索引
文件版本：v1
功能目的：汇总 ERP REST/MCP 接口契约入口及其运行时接入、发现和可调用状态。
设计思路：按业务域描述入口、认证、参数、数据范围、响应、错误和副作用，并将静态契约与运行时可用状态分开。
实现方法：使用请求/响应示例和逐接口说明维护契约；接口变化时同步核对 API 索引、实现和对应任务记录。
依赖与引用：Docs/规范/API文档规范.md；Docs/项目记录/功能实现状态.md；Docs/AI/织云AI集成总体规划.md
初始开发人员：JY
初始开发时间：待追溯
当前责任人：JY
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-20
修改标记：规划文档目录调整及相关路径更新
最近测试时间：2026-11-20
测试人员：JY
测试状态：内部文档路径核对；未执行相关业务代码测试
-->
# API 文档（总索引）

> 版本：v1
> 更新日期：2026-11-20
> 说明：功能 API/MCP 契约总索引。先在此定位功能分组与接口，再进分组文档看完整契约。普通 REST 已实现不等于 MCP 已注册；每组需明确运行时状态。**分组文档一律放 `Docs/API/` 目录。**

> MCP 调用方请先阅读[智能体 Skill 索引](../Skills/README.md)及[通用 MCP 调用指南](../Skills/MCP调用指南.md)。每个功能 Skill 包含工具名和可复制的 `arguments` 示例；运行时 `tools/list` 的 `inputSchema` 是参数必填规则的最终依据。

---

## 〇、通用调用方法（所有接口共用）

### 1. 认证（两步）

**第一步：登录取 token**

```bash
curl -X POST http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"e5cd7e4891bf95d1d19206ce24a7b32e","grantType":"password","username":"<当前员工工号>","password":"<当前员工密码>"}'
```

- `clientId`：固定应用标识（非密钥，前端源码同款）
- 示例中的账号和密码为占位符；调用时使用当前员工自己的登录凭据，不要把真实口令写入文档、代码或 Git。
- 成功返回 `data.access_token`（有效期见后端配置，过期重新登录）
- 登录失败：`{"code":500,"data":null,"msg":"用户不存在/密码错误"}`

**第二步：后续每个请求带两个 header**

```
Authorization: Bearer <access_token>
clientid: e5cd7e4891bf95d1d19206ce24a7b32e
```

### 2. 统一响应结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | `200`=成功；`401`=未登录/登录过期；`403`=无权限；`500`=业务失败（msg 为具体原因） |
| `msg` | string | 提示信息（失败原因） |
| `data` | any | 成功时的数据；失败时 `null` |

### 3. 智能体调用红线（摘自《开发规范.md》）

- 每次写操作**以发起员工本人的 token 调用**（权限由后端 Sa-Token 按 token 校验，禁止用超管 token 代执行）
- orgId 等 bigint 主键在 JSON 里是**字符串**（如 `"2114531897794859009"`），**禁止转 number**（>2^53 丢精度）
- 写接口带 `@RepeatSubmit`：间隔内相同参数重复提交会被拒（`不允许重复提交，请稍候再试`），重试需换时间或确认首次是否已生效

---

## 一、功能分组清单

| 功能分组 | 英文键 | 分组文档 | 状态 | 验收日期 |
|---|---|---|---|---|
| 标签管理 | `tag` | [标签管理API.md](标签管理API.md) | 2026-11-20 用户验收通过；REST、权限、主建表、Skill 已接入；开发服务 `tools/list` 已发现 11 个工具，普通员工权限和读写审计待核 | 2026-11-20 |
| 计量单位 | `unit` | [计量单位API.md](计量单位API.md) | REST/MCP已实现；工具发现、单位读写及低权限拒绝已验证 | 用户验收通过（2026-11-05） |
| 分类管理 | `category` | [分类管理API.md](分类管理API.md) | 体系与节点 REST/MCP 已实现，已有发现及读写验证记录 | 2026-11-06 用户验收通过 |
| 颜色管理 | `color` | [颜色管理API.md](颜色管理API.md) | 用户验收通过（2026-11-07）；8个MCP工具发现，目录/选项/编码建议及颜色CRUD/启停真实调用通过 | 2026-11-07 |
| 物料档案 | `material` | [物料档案API.md](物料档案API.md) | 一期、二期用户验收通过；运行时发现 9/9 MCP 工具；临时档案新增、列表、修改、启停、删除均已实调；普通账号权限与 SnailAI 确认卡待核 | 2026-11-20 |
| 物料需求计划 | `material_demand_plan` | [物料需求计划API.md](物料需求计划API.md) | REST 与审批业务联动已实现；前后端构建通过、开发库迁移已应用；待用户验收；MCP/Skill 尚未接入 | — |
| 尺码管理 | `size` | [尺码管理API.md](尺码管理API.md) | REST/MCP 已实现；本轮补齐 9 个工具，运行时验证见测试清单 | 2026-11-04 |
| 尺码组管理 | `size_group` | [尺码组管理API.md](尺码组管理API.md) | REST/MCP 已实现；本轮补齐 8 个工具，运行时验证见测试清单 | 2026-11-04 |
| 客户档案 | `customer_master` | [客户档案API.md](客户档案API.md) | 本轮客户分类/默认类型/重置、联系人与地址详情及原主流程用户验收通过；REST/Skill/MCP 契约已更新；开发服务 `tools/list` 已发现 11 个客户工具（含分类选项），账号级权限过滤待核 | 2026-11-20 |
| 人员信息 | `person` | [人员信息API.md](人员信息API.md) | 人员档案 MCP 7/7 已在开发服务发现；列表、详情、职责选项、新增、补丁修改、软删除、恢复；敏感扩展字段拒绝；普通账号读写权限待核，模块整体待验收 | 2026-11-20 |
| 人员分类权限设置 | `person_category_scope` | [人员分类权限API.md](人员分类权限API.md) | 用户验收通过（2026-11-20）；REST、主建表、Skill 已补，开发服务 `tools/list` 已发现 2 个 MCP 工具；不同账号的授权和数据范围待核 | 2026-11-20 |
| 公司档案 | `company` | [公司档案API.md](公司档案API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；详见分组契约 | 2026-11-05 |
| 组织架构 | `org` | [组织架构API.md](组织架构API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；详见分组契约 | 2026-11-05 |
| 岗位管理 | `post` | [岗位管理API.md](岗位管理API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；详见分组契约 | 2026-11-05 |
| 建筑档案 | `building` | [建筑档案API.md](建筑档案API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；详见分组契约 | 2026-11-05 |
| 仓库管理 | `warehouse` | [仓库管理API.md](仓库管理API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；CSV 导入没有独立批量 API | 2026-11-05 |
| 仓库布局 | `warehouse_layout` | [仓库布局API.md](仓库布局API.md) | REST/MCP 已实现；本轮 `tools/list` 已发现；MCP 批量新增按行提交并返回部分成功结果 | 2026-11-05 |
| 框架原生能力接入 | `framework_native` | [框架原生能力接入API.md](框架原生能力接入API.md) | 系统管理、审批监控、代码生成、独立控制台和 Swagger 已接入；角色权限记录已核对；MCP 不开放高权限系统维护操作 | 2026-11-17 |
| 审批中心 | `approval_center` | [审批中心API.md](审批中心API.md) | 用户验收上述页面；REST/MCP/Skill 已补齐，开发服务发现 13/13 审批工具并可读取 Skill；员工业务调用与普通账号权限边界待实调 | 2026-11-20 |
| SnailAI 员工侧入口 | snail_ai_employee | [../织云AI集成总体规划.md](../AI/织云AI集成总体规划.md) | ERP 右上角入口、员工 OpenAPI 身份、短时会话关联、MCP 权限过滤、写操作确认与本地 Skill 读取代码已完成；实时工具发现与员工验收待验证 | — |

---

## 二、接口速查（每接口一行）

### 颜色管理 color（`/system/color`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/color/list` | 查询颜色管理目录 | 只读 |
| `GET /system/color/options` | 查询业务颜色选项，停用项带 `selectable=false` | 只读 |
| `GET /system/color/next-code` | 获取下一颜色编码可编辑建议 | 只读 |
| `POST /system/color` | 新增颜色 | 写 |
| `PUT /system/color/{id}` | 修改颜色名称、色值、排序和备注 | 写 |
| `PUT /system/color/{id}/status` | 启用或停用颜色 | 写 |
| `DELETE /system/color/{id}` | 删除未被外键引用的颜色 | 写 |
| `POST /system/color/code-rule` | 修改颜色编码自动生成参数 | 写 |

> 权限码：`system:color:list/add/edit/remove`；编码规则复用 `system:config:edit`。MCP 工具：`color_list/options/next_code/create/update/set_status/delete/set_code_rule`；见[完整颜色管理契约](颜色管理API.md)。

### 物料档案 material（`/inventory/material`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /inventory/material/list` | 按员工分类权限查询物料目录 | 只读 |
| `GET /inventory/material/category-options` | 查询当前员工可见分类及树路径节点 | 只读 |
| `GET /inventory/material/default-category` | 查询当前员工可见范围内的页面默认分类类型 | 只读 |
| `GET /inventory/material/options` | 查询授权范围内启用物料 | 只读 |
| `GET /inventory/material/spec-templates` | 查询规格模板目录及字段定义 | 只读 |
| `GET /inventory/material/next-code` | 获取可编辑物料编码建议 | 只读 |
| `POST /inventory/material` | 新增物料档案及颜色关联 | 写 |
| `PUT /inventory/material/{id}` | 修改物料档案及颜色集合 | 写 |
| `PUT /inventory/material/{id}/status` | 启用或停用物料 | 写 |
| `DELETE /inventory/material/{id}` | 删除无业务引用物料 | 写 |
| `POST /system/config/material-code-rule` | 保存物料编码规则 | 写 |

> 权限码：`inventory:material:list/add/edit/remove`；编码及默认分类参数复用 `system:config:edit`。MCP 工具：`material_list/material_category_options/material_spec_templates/material_options/material_next_code/material_create/material_update/material_set_status/material_delete`；运行时已发现 9/9 工具，分类数据范围由服务端执行。物料档案一期、二期均已通过用户验收；详见[物料档案 API](物料档案API.md)。

### 组织架构 org（`/system/org` 复用；档案类型 org_category 由用户自选，仅展示标识）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/org/list` | 查组织树/组织列表（支持 `orgCategory` 过滤、平铺返回） | 只读 |
| `GET /system/org/{orgId}` | 查单个组织节点详情（含 ancestors 层级链） | 只读 |
| `POST /system/org` | 新建组织节点（orgCategory 用户自选） | 写 |
| `PUT /system/org` | 修改组织节点 | 写 |
| `DELETE /system/org/{orgId}` | 删除组织节点（5 道前置校验） | 写 |

> 权限码：`system:org:list` / `system:org:query` / `system:org:add` / `system:org:edit` / `system:org:remove`（按发起员工角色过滤，无权限返回 403）。

### 尺码管理 size（`/system/size-data/size`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/size-data/size/list` | 查询尺码档案（含类型标签） | 只读 |
| `GET /system/size-data/size/options` | 查询业务选择用尺码（含状态） | 只读 |
| `GET /system/config/configKey/system.code.size` | 查询尺码自动编码规则（固定参数键） | 只读 |
| `POST /system/size-data/size` | 新增尺码 | 写 |
| `PUT /system/size-data/size/{id}` | 修改尺码档案 | 写 |
| `PUT /system/size-data/size/{id}/status` | 启用或停用尺码 | 写 |
| `DELETE /system/size-data/size/{id}` | 删除未被尺码组引用的尺码 | 写 |
| `POST /system/size-data/size/batch-tags` | 批量替换所选尺码的类型标签；空数组清空标签 | 写 |
| `POST /system/config/size-code-rule` | 更新自动编码开关、前缀和数字位数 | 写 |

> 权限码：尺码档案 `system:size:list/add/edit/delete`；编码规则 `system:config:edit`。9 个 MCP 工具已在 `SizeMcpTools` 注册，见[完整 API 契约](尺码管理API.md)。

### 尺码组管理 size_group（`/system/size-data/size-group`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/size-data/size-group/list` | 查询尺码组、成员顺序及标签 | 只读 |
| `GET /system/size-data/size-group/options` | 查询业务选择项及历史成员 | 只读 |
| `GET /system/config/configKey/system.code.sizeGroup` | 读取固定尺码组编码规则 | 只读 |
| `POST /system/size-data/size-group` | 新增尺码组 | 写 |
| `PUT /system/size-data/size-group/{id}` | 修改档案及成员顺序 | 写 |
| `PUT /system/size-data/size-group/{id}/status` | 启用或停用尺码组 | 写 |
| `DELETE /system/size-data/size-group/{id}` | 删除尺码组 | 写 |
| `POST /system/config/size-group-code-rule` | 修改自动编码规则 | 写 |

> 权限码：`system:size-group:list/add/edit/delete`；规则修改使用 `system:config:edit`。8 个 MCP 工具已在 `SizeMcpTools` 注册，见[完整尺码组契约](尺码组管理API.md)。

### 仓库管理 warehouse（`/system/warehouse`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/warehouse/list` | 按关键字、状态、仓库类型查询仓库 | 只读 |
| `GET /system/warehouse/options` | 查询建筑和负责人选项 | 只读 |
| `GET /system/warehouse/next-code` | 查询仓库编码建议 | 只读 |
| `GET /system/warehouse/{id}` | 查询仓库详情和类型标签 | 只读 |
| `POST /system/warehouse` | 新增仓库 | 写 |
| `PUT /system/warehouse` | 修改仓库 | 写 |
| `PUT /system/warehouse/{id}/status` | 启用或停用仓库 | 写 |
| `DELETE /system/warehouse/{id}` | 逻辑删除无布局仓库 | 写 |
| `POST /system/config/warehouse-code-rule` | 设置仓库自动编码规则 | 写 |

> 权限码：`inventory:warehouse:list/query/add/edit/remove`；规则设置为 `system:config:edit`。MCP 查询与写工具均已接入代码，写调用必须先获得员工确认；见[完整仓库管理契约](仓库管理API.md)。

### 仓库布局 warehouse_layout（`/system/warehouse-location`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/warehouse-location/options` | 查询仓库及入库定位深度 | 只读 |
| `GET /system/warehouse-location/tree` | 查询库区—货架—货位层级树 | 只读 |
| `GET /system/warehouse-location/list` | 按仓库、父级、层级、关键词、状态查询 | 只读 |
| `GET /system/warehouse-location/{id}` | 查询位置详情 | 只读 |
| `GET /system/warehouse-location/next-code` | 查询位置编码建议 | 只读 |
| `POST /system/warehouse-location` | 新增位置；页面导入也逐条调用此接口 | 写 |
| `PUT /system/warehouse-location` | 修改位置属性 | 写 |
| `PUT /system/warehouse-location/{id}/status` | 启用、停用或维护位置 | 写 |
| `DELETE /system/warehouse-location/{id}` | 逻辑删除未被引用的末级位置 | 写 |
| `GET /system/config/configKey/inventory.warehouseLayout.codeRule` | 读取固定的布局编码规则 | 只读 |
| `POST /system/config/warehouse-layout-code-rule` | 设置库区/货架/货位编码规则 | 写 |

> 权限码：位置查询 `inventory:location:list/query`，维护 `inventory:location:add/edit/remove`；规则设置 `system:config:edit`。MCP 支持单条及逐行部分成功的批量新增；见[完整仓库布局契约](仓库布局API.md)。

> 2026-11-17 认证 MCP 会话曾实际发现 125 个工具，其中 123 个属于织云业务工具，另有 2 个 `ruoyi-demo` 框架示例工具。2026-11-20 人员档案 MCP 加入并重启后，认证 `tools/list` 最新共发现 158 个工具：人员档案 7/7、人员分类权限 2/2、物料档案 9/9、审批中心 13/13。普通账号业务调用和权限边界仍需单独实调；物料写工具尚无实际调用记录。REST/OpenAPI 与 MCP 并非逐接口一一映射；MCP 不提供通用 HTTP、SQL 或高权限系统维护写入能力。

### 客户档案 customer_master（`/system/customer`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/customer/category-options` | 当前人员可见分类树、默认分类类型和受限标识 | 只读 |
| `GET /system/customer/list` | 客户档案分页查询；支持分类节点和分类类型筛选 | 只读 |
| `GET /system/customer/category-counts` | 当前人员可见客户总数及标签统计 | 只读 |
| `GET /system/customer/{id}` | 当前人员可见的客户主档、联系人、地址和标签 | 只读 |
| `GET /system/customer/duplicates`、`/sales-options`、`/options` | 同名提示、业务员筛选、启用客户选项 | 只读 |
| `POST /system/customer`、`PUT /system/customer/{id}` | 新增、修改 | 写 |
| `PUT /system/customer/{id}/status`、`DELETE /system/customer/{id}` | 启停、删除未被引用档案 | 写 |

> 分类筛选、默认类型/重置、人员分类范围及联系人脱敏规则见[客户档案 API 契约](客户档案API.md)。2026-11-20 开发服务 `tools/list` 已发现 11 个客户工具，包含 `customer_category_options` 和分类筛选参数；不同员工分类范围下的实际过滤仍须账号级核验，写操作按员工权限及确认要求执行。

### 人员信息与业务职责（`/system/person`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/person/business-duty-options` | 查询可多选业务职责代码与名称 | 只读；`system:person:list` |
| `GET /system/person/{userId}` | 人员详情增加 `businessDuties` 字符串数组 | 只读；沿用人员数据范围 |
| `POST /system/person`、`PUT /system/person` | 新增/修改请求支持 `businessDuties` 多选数组 | 写；沿用 `system:person:add/edit`，业务审计记录职责前后值 |

一期职责代码：`SALES` 业务员、`FOLLOWER` 跟单员、`PURCHASER` 采购员、`PLANNER` 计划员、`WAREHOUSE` 仓管员、`QUALITY` 质检员。职责仅用于单据责任人候选过滤和服务端引用校验，不授予菜单/操作/数据权限。客户订单业务员、跟单员候选分别按 `SALES`、`FOLLOWER` 过滤；采购单实现时按 `PURCHASER` 过滤，采购操作权限仍单独校验。

人员档案 MCP 工具及字段隐私、补丁更新、软删除和恢复契约见[人员信息 API 与 MCP 契约](人员信息API.md)。2026-11-20 新增并在开发服务发现 7 个工具；列表/详情仅输出工作必需字段，新增/修改拒绝证件、银行卡、家庭住址、出生信息、保险及社保金额等敏感扩展字段；密码重置、彻底删除和全量导出不开放 MCP。

人员钉钉号：列表和详情返回 `dingtalk_account`；新增及修改通过 `ext.dingTalkAccount` 传入，选填，去除首尾空白，最长 128 字符，空字符串清空；未提供该字段时保留旧值。详情需要 `system:person:query` 并受人员数据范围约束，新增和修改分别需要 `system:person:add` 和 `system:person:edit`，变更纳入人员业务审计。该字段仅登记钉钉号，尚未完成钉钉企业身份校验、绑定或消息推送。

客户订单 PO 支持 `offlineDate` 默认下线日期；`GET /order/customer-orders/options` 返回 `offlineLeadDays`，由系统参数 `order.customer.offlineLeadDays` 设置，未设置时为 0 个自然日。前端按 PO 默认交期计算建议日期，允许手动修改，保存后作为订单快照。`POST /system/config/order-offline-lead-days` 接收整数 `days`（0–3650），需要系统参数编辑权限。款式规格行支持 `unitPrice` 加工单价（可空、非负、最多两位小数），服务端以精确十进制计算 `processingAmount`；空单价代表待填写，不等同零。单款单价按件，套装单价按套。订单总额按各规格金额汇总，缺失单价时标记金额未完整。

客户订单 `POST /order/customer-orders/{id}/transition` 的 `action=unsubmit` 可将待审核订单退回草稿，需要制单权限，清除需求快照，并检查当前修订号。已审核订单的反审核改走 `POST /order/customer-orders/{id}/reverse-approval`，请求体为 `{"revision":4,"reason":"反审核原因"}`，需要订单编辑权限和已绑定的 `customer_order_unapprove` 审批流程；流程未绑定时拒绝申请，不回退为直接审核权限。发起后原订单仍保持已审核，审批退回/终止时也不改变原单；审批通过后订单退回草稿并清除需求快照，再次提交时重新启动客户订单普通审批。申请时和审批通过回调时均锁定订单并检查版本及未取消的物料需求计划；关联计划必须先反审核并取消。其他下游单据接入后仍须补充引用检查。

客户订单提交和审核不以 BOM 完成作为前置条件；草稿中的用料试算是临时预览。已审核订单可调用 `POST /order/customer-orders/{id}/demand/calculate`，提交当前 `revision`；已有快照时还须明确传入 `recalculate=true`。接口要求订单列表及制单权限和订单数据访问范围。关联的需求计划若仍是未调整草稿，会在同一事务内同步新的完整算料快照；计划存在人工调整、已提交或已审核状态时，重新算料会被拒绝，避免覆盖计划工作。BOM 缺失或用料不完整时返回 `saved=false` 和带缺失原因的 `plans`，不写入正式快照；全部完整时返回 `saved=true`、`plans` 和最新 `order`，事务内归档旧用料快照、保存新快照并记录业务审计。订单列表 `demandState` 区分未算料、已算料、BOM 更新、BOM 不可用和待核对。历史表增量见 `sql/订单算料快照历史_20261114.sql`。独立物料需求计划接口、字段、审批、权限和迁移状态见[物料需求计划 API 契约](物料需求计划API.md)；前后端构建与 WSL 开发库迁移已通过，待用户验收；MCP/Skill 尚未接入。

### 物料需求计划 material_demand_plan（`/order/material-demand-plans`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /order/customer-orders/list` | 按关键词、状态及可见范围分页查询，接受 `pageNum`、`pageSize`，返回 `{rows,total}` | 只读 |
| `GET /order/material-demand-plans/list` | 按计划号、来源订单、客户、PO、单据状态和算料状态分页查询，接受 `pageNum`、`pageSize`，返回 `{rows,total}` | 只读 |
| `GET /order/material-demand-plans/{id}` | 查询计划主单、来源明细、物料汇总及数量调整历史 | 只读 |
| `GET /order/material-demand-plans/eligible-orders` | 查询可补生成计划的已审核订单 | 只读 |
| `POST /order/material-demand-plans/generate/{orderId}` | 从已审核订单幂等补生成需求计划草稿 | 写 |
| `PUT /order/material-demand-plans/{id}/quantities` | 按修订号保存计划数量并记录调整原因 | 写 |
| `POST /order/material-demand-plans/{id}/transition` | 提交、撤回、直接审核/退回或取消计划 | 写 |

> 独立接口契约、参数、权限、幂等、副作用及审批前置见[物料需求计划 API 契约](物料需求计划API.md)。全部写入口使用 `@RepeatSubmit`，业务层另以来源订单幂等、修订号和状态校验防止重复生成/过期覆盖。MCP 工具未注册，不能通过智能体运行时调用。

### 公司档案 company（`/system/company`）

列表、详情、OSS 文件引用查询、新增、修改、设置默认公司、软删除接口和权限见[公司档案 API 契约](公司档案API.md)。切换默认公司会影响登录页及首页展示，智能体须在写入前展示影响并取得确认。本轮 MCP `tools/list` 已发现公司档案工具。

### 组织架构 org（`/system/org`）

组织树、详情、组织选项、业务管辖查询与全量替换、新增、修改、删除接口及数据范围约束见[组织架构 API 契约](组织架构API.md)。本轮 MCP `tools/list` 已发现组织架构工具。

### 岗位管理 post（`/system/post`）

岗位分页、详情、选项、组织树、新增、修改、启停、批量删除接口及权限见[岗位管理 API 契约](岗位管理API.md)。本轮 MCP `tools/list` 已发现岗位管理工具。

### 建筑档案 building（`/system/building`）

建筑列表、详情、新增、修改和删除接口及权限见[建筑档案 API 契约](建筑档案API.md)。本轮 MCP `tools/list` 已发现建筑档案工具。

---

## 三、维护约定

1. 每个用户验收通过的功能都补充分组 API/MCP 契约文档、Skill 并更新本索引。准确标明 REST 实现和 MCP 运行时接入状态；未注册的工具不可标成可用。
2. 分组文档必须含**机器契约五字段**（幂等性/副作用/前置条件/是否需审批/权限要求），缺一不算达标。
3. 示例必须是**可直接运行的 curl/JSON**，与后端实测一致（禁止凭记忆写）。
4. 功能验收后在 `Docs/Skills/<功能名>/SKILL.md` 建立 Skill；MCP 工具接入时按《内嵌智能体规范》逐组登记。Skill 必须声明所依赖工具是否已注册，不以文档存在暗示运行时已接通。


## 客户档案（2026-11-02）

- GET /system/customer/list：分页，keyword/status/salesId/tagId；返回 rows/total。
- GET /system/customer/{id}：主档、contacts、addresses、tagIds、tagOptions（含停用名称）；只读权限联系方式脱敏。
- GET /system/customer/duplicates：name/id，同名计数仅提醒，不强制拒绝。
- GET /system/customer/sales-options：业务员及岗位、组织、小组筛选所需非敏感信息。
- GET /system/customer/options：仅启用客户，供新业务引用。
- POST /system/customer：自动编号，事务保存主档和子项。
- PUT /system/customer/{id}：编辑，不能覆盖编号。
- PUT /system/customer/{id}/status：状态0启用/1停用，不改变历史业务。
- 权限：查询list、新增add、修改与启停edit，前缀system:customer；不提供物理删除。
- 联系方式只有客户维护权限可读原值；操作日志不保存客户请求与响应正文。
- 停用标签原关联可保留且显示名称，新增关联服务端拒绝。

## 国家/地区基础资料（2026-11-03）

- GET `/system/country-region/options`：登录后获取编码、中英文名称及启停状态；包含停用项以显示已有引用，前端禁止新增选择停用项。
- GET `/system/country-region/list`：目录管理查询，需 `system:country:list`。
- POST `/system/country-region`：新增编码和名称，需 `system:country:add`；编码创建后不可更改。
- PUT `/system/country-region/{code}`：修改名称；PUT `/system/country-region/{code}/status`：启停；均需 `system:country:edit`。不提供物理删除。
- 客户主档与地址分别提交 `country_region_code`；名称 `country_region` 由服务端按编码保存快照。已停用编码仅允许原记录保留；未能唯一匹配的旧手填值可原样保留或清除，修改为其他地区时必须选择有效编码。
- 客户列表 `country_display` 优先显示标准资料的当前中文名；详情中的 `country_region` 是保存时名称或未匹配旧值。订单目的地和实际发货目的地不读取客户所在地区作为事实。

- [款式档案 API](款式档案API.md)：单款、套装、规格、附件及 MCP 契约。
- [款式 BOM API](款式BOMAPI.md)：BOM 版本、用料规则、覆盖核对、试算及 MCP。

人员分类权限设置的 REST、MCP 和授权结构详见[人员分类权限 API](人员分类权限API.md)。该设置需单独授予 `system:person:categoryScope`，不能通过普通角色并集隐式获得。

### 智能体对话诊断

`GET /snail-ai/admin/traces/conversations` 和 `GET /snail-ai/admin/traces/conversations/{traceId}` 需要 `system:snailai:trace:list`。有上游对话编号时按员工、智能体和对话编号归组；未取得编号时独立显示本轮。`GET /snail-ai/admin/traces/{traceId}/images` 使用同一权限，缺少可确认图片归属的上游标识时返回空列表。这些诊断接口供 ERP 管理页面使用。

### 数字显示精度

登录后通过 `GET /system/config/configKey/system.display.decimals` 读取 JSON 字符串。保存使用 `POST /system/config/display-decimals`，需要 `system:config:edit`，示例请求为 `{"bomUsage":4,"purchasePrice":4,"costPrice":6,"percentage":2}`。四项必填，均为 0～8 的整数。数量优先读取所用单位的显示位数。显示参数供只读展示使用，单据计算及保存使用原始数值。
