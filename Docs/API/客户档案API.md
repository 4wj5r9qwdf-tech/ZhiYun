# 客户档案 API / MCP 契约

> 功能键：`customer_master`；主表：`customer_master`、`customer_contact`、`customer_address`；本轮客户分类、默认分类类型/重置、联系人与地址弹出详情及原主流程于 2026-11-20 用户验收通过。
> REST 与 MCP 均使用当前员工身份。客户联系方式只向有 `system:customer:edit` 的员工返回原值，其他有查询权限的员工收到脱敏值。
> MCP 运行时记录：2026-11-20 开发服务 `tools/list` 已发现 11 个客户工具，包含 `customer_category_options`。不同人员分类范围下的实际过滤和低权限拒绝仍待账号级核验。

## 通用约定

- ID 在路径/MCP 入参使用十进制字符串；不得把 PostgreSQL bigint ID 转成可能丢精度的浮点数。
- 统一响应：`{"code":200,"msg":"操作成功","data":...}`；业务失败为 `code=500`、`data=null`、`msg` 给出原因；未登录 `401`、无权限 `403`。
- 写入以员工本人权限执行并记业务审计；写请求没有幂等键，超时后先查询确认，不盲目重试新增。
- 同名客户允许保存，重复检查只提供提示。停用客户仍可查询历史记录，但不出现在新业务引用选项中。
- 分类类型参数只控制默认显示/重置筛选，不授予人员分类权限，也不会在保存时自动给客户分配分类。
- 人员分类范围由服务端统一应用于列表、统计、重复检查、详情、新业务选项及写操作。`ALL` 可见范围还可查看未分类客户；受限范围只返回获授权分类内的客户，未分类及无权分类不返回。

## MCP 工具

| 工具 | 权限 | 类型/约束 |
|---|---|---|
| `customer_category_options` | `system:customer:list` | 只读；返回当前人员可见分类树、可选节点、受限标识和默认分类类型 |
| `customer_list` | `system:customer:list` | 只读分页；分类筛选先读取 `customer_category_options`；`categoryId` 与 `schemeId` 二选一 |
| `customer_category_counts` | `system:customer:list` | 只读统计；结果按人员分类范围计算 |
| `customer_get` | `system:customer:list` | 详情；无编辑权限时联系方式脱敏，分类越权时不返回客户 |
| `customer_check_duplicate` | `system:customer:list` | 只读同名检查，仅统计当前人员可见范围内的客户 |
| `customer_sales_options` | `system:customer:list` | 只读具有业务员职责的在职人员及组织/岗位/小组选项 |
| `customer_options` | `system:customer:list` | 只读启用客户选项；按人员分类范围过滤 |
| `customer_create` | `system:customer:add` | 写；先展示客户、分类、联系人、地址、标签并确认 |
| `customer_update` | `system:customer:edit` | 写；先展示完整变更并确认；受限人员只能维护授权分类内客户 |
| `customer_set_status` | `system:customer:edit` | 写；先确认目标状态；受限人员只能操作授权分类内客户 |
| `customer_delete` | `system:customer:remove` | 写；展示身份并确认；被业务引用或分类越权时拒绝 |

### 分类选项与列表调用示例

先调用分类选项，不要猜 ID：

```json
{"name":"customer_category_options","arguments":{}}
```

返回数据包含 `defaultSchemeId`、`restricted` 和 `rows`。分类节点字段含 `id`、`schemeId`、`name`、`parentId`、`selectable`；`selectable=false` 的祖先节点只用于树状导航，不能作为客户分类提交。`restricted=true` 表示当前人员配置了分类范围；`rows` 为空或预期节点缺失时，应提示检查人员分类权限，不得直接判断系统没有该类客户。

按关键词查询客户：未使用的可选参数直接省略，不发送 `null`：

```json
{"name":"customer_list","arguments":{"keyword":"示例客户","pageNum":1,"pageSize":20}}
```

按某分类类型查询时，使用选项结果中的 `defaultSchemeId`（如需复现页面默认筛选/重置）或某节点的 `schemeId`；按单个分类查询时使用一个 `selectable=true` 节点的 `id`。两个参数不能同时传：

```json
{"name":"customer_list","arguments":{"schemeId":"<customer_category_options返回的schemeId>","pageNum":1,"pageSize":20}}
```

```json
{"name":"customer_list","arguments":{"categoryId":"<customer_category_options返回的可选节点id>","pageNum":1,"pageSize":20}}
```

`categoryId="0"` 表示未分类，只对全部可见范围的人员开放；受限人员即使手工传入也不会读到未分类客户。没有配置默认分类类型时，页面重置回全部可见分类；有配置时，初始筛选和重置回该分类类型。该参数不会改变人员授权范围。

## REST 接口

| 方法 + 路径 | 用途 / 参数 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/customer/category-options` | 当前人员可见分类树、`restricted`、`defaultSchemeId` | `system:customer:list` | 只读幂等；分类项按人员授权过滤 |
| `GET /system/customer/list` | `keyword`、`status`、`salesId`、`tagId`、`categoryId`、`schemeId`、`pageNum`、`pageSize` | `system:customer:list` | 只读幂等；人员分类范围在服务端强制过滤；分类与分类类型不可同时传 |
| `GET /system/customer/category-counts` | 客户总数及标签统计 | `system:customer:list` | 只读幂等；统计按人员分类范围计算 |
| `GET /system/customer/{id}` | 客户主档、联系人、地址和标签 | `system:customer:list` | 只读幂等；按人员分类范围检查并按 edit 权限脱敏联系方式 |
| `GET /system/customer/duplicates?name=...&id=0` | 同名数量；`id` 排除当前记录 | `system:customer:list` | 只读幂等；只统计当前人员可见客户 |
| `GET /system/customer/sales-options` | 具有业务员职责的在职人员、组织/岗位/小组筛选数据 | `system:customer:list` | 只读幂等；返回非敏感人员信息 |
| `GET /system/customer/options` | 启用客户选项 | `system:customer:list` | 只读幂等；按人员分类范围过滤 |
| `POST /system/customer` | 新增客户及联系人/地址/标签 | `system:customer:add` | 非幂等；受限人员必须选择可用分类；需员工确认 |
| `PUT /system/customer/{id}` | 更新主档并整体替换联系人、地址、标签集合 | `system:customer:edit` | 目标状态幂等、仍写审计；分类越权拒绝；需员工确认 |
| `PUT /system/customer/{id}/status` | `{ "status":"0" }` 启用或 `1` 停用 | `system:customer:edit` | 状态幂等；保留历史；分类越权拒绝；需员工确认 |
| `DELETE /system/customer/{id}` | 删除客户及其联系人/地址 | `system:customer:remove` | 非幂等；仅无业务外键引用时删除；分类越权拒绝；需员工确认 |

### 筛选参数和默认分类行为

- `keyword`、`status`、`salesId`、`tagId`、`categoryId`、`schemeId` 均可省略；字符串筛选为空白时不增加对应条件。`status` 只接受 `0`（启用）和 `1`（停用），其他非空文本会返回参数错误，避免错误值被静默忽略而扩大查询结果。
- `categoryId` 为十进制分类节点 ID；`0` 表示未分类，正数节点查询该节点及下级分类。MCP 和 REST 均支持 `categoryId="0"`。`schemeId` 为正数分类类型 ID，查询该类型下客户。两者只能选一个。
- 分类类型默认参数键为 `sales.customer.defaultCategoryScheme`。`category-options` 返回配置值；该值用于页面初始筛选及“重置”，不是权限授予或客户自动分类规则。
- 页面重置其他查询条件时，保留已配置的默认分类类型；清除分类树选择时也回到默认类型。未配置时重置到全部当前可见分类。
- 人员分类范围优先于页面筛选：范围为指定分类时，列表、统计、选项、详情及维护只覆盖获授权节点；分类类型筛选不得扩大授权。未分类客户仅在全部可见范围可见。
- 受限人员看到空列表，可能是该人员无目标分类权限，也可能该分类下暂无客户。先读取 `customer_category_options` 检查 `restricted` 和可选节点，再向管理员核对人员分类授权；不要把权限过滤结果说明成客户档案不存在。
- 分页参数默认 `pageNum=1`、`pageSize=20`，每页最大100条。

### 新增/修改请求体

```json
{
  "customer_name":"示例客户",
  "customer_short_name":"示例",
  "unified_social_credit_code":"91310000XXXXXXXXXX",
  "country_region":"中国",
  "country_region_code":"CN",
  "categoryId":"<可选的customer_category_options返回的可选节点id>",
  "sales_representative_id":"<业务员用户ID>",
  "remark":"",
  "tagIds":["<客户标签ID>"],
  "contacts":[{"contact_name":"王女士","contact_title":"采购","phone_number":"13800000000","email":"buyer@example.com","contact_role":"采购联系人","is_primary":true}],
  "addresses":[{"address_type":"样衣收件","recipient_name":"王女士","recipient_phone":"13800000000","country_region":"中国","country_region_code":"CN","province":"上海市","city":"上海市","district":"浦东新区","address_line":"示例路1号","postal_code":"200000","is_default":true}]
}
```

- 新增时 `categoryId` 可省略或为空，表示未分类；受限人员必须选择其有权限的有效分类。修改时省略 `categoryId` 保留原分类，传 `""` 清空分类；受限人员不能清空到未分类，也不能转入未授权分类。
- `contacts` 和 `addresses` 可省略或为空；每类最多100条。联系人姓名/电话必填；每客户最多一个默认联系人。地址用途及详细地址必填；用途包含“样衣”时收件人和电话必填；同一用途最多一个默认地址。
- 地区编码必须有效；原有停用地区或无法匹配的历史手填值可保留原样，不能把停用编码新增给其他客户/地址。业务员必须是在职且具备“业务员”职责的人员。
- 成功响应示例：`{"code":200,"msg":"操作成功","data":"<customerId>"}`。常见错误包括分类无效/无权、受限人员未选分类、业务员职责或状态无效、无效标签、地区编码无效、重复默认项、客户被业务引用。

### 写操作机器契约

- 新增：非幂等；写主档、子项、标签关系和审计记录；前置为 `system:customer:add`、名称/业务员有效、分类范围符合当前人员及子项约束通过；无需审批，执行前确认完整请求。
- 修改：整体替换联系人、地址及标签集合；前置为记录存在、当前员工可见客户并具备 `system:customer:edit`；受限范围内的分类变更必须使用当前员工可选节点；执行前展示完整变更。
- 启停：保留历史引用；前置为 `system:customer:edit` 且客户属于当前员工可见分类；执行前确认目标状态。
- 删除：删除客户及档案子项、标签关系；任何已声明业务外键引用时拒绝；前置为 `system:customer:remove` 且客户属于当前员工可见分类；执行前确认客户编码和名称。
- 审计不得记录原始联系方式；日志对电话、邮箱脱敏。
