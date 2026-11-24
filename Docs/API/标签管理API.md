# 标签管理 API

> 状态：用户于 2026-11-20 验收通过。REST、权限、主建表、Skill 和 MCP 管理工具已接入；2026-11-20 开发服务重启后 `tools/list` 已发现标签管理 11 个工具。普通员工权限、真实读取及写入审计仍待账号级核验。分组属于具体功能命名空间 `module_key`，业务记录仍以 `module_key + record_id + tag_id` 关联标签。
> 契约迁移：2026-11-21 已补齐本文件 MCP 输入字段矩阵和写入示例；当前只完成静态代码对照，运行时权限/真实调用仍按下方状态记录。

## REST 接口

基础路径：`/system/tag`

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| `GET /modules` | 查询已登记功能及标签数量（路径沿用历史名称） | `system:tag:list` |
| `GET /groups?moduleKey=...` | 查询具体功能内分组及标签数量，默认分组排在首位 | 登录 |
| `GET /list?moduleKey=...` | 查询标签及所属分组，可按 `keyword`、`status`、`groupId` 筛选 | `system:tag:list` |
| `GET /options?moduleKey=...` | 查询业务选择用的启用标签，包含 `groupId`、`groupName` | 登录 |
| `POST /ensure` | 业务表单幂等收录标签；未指定分组时归入默认分组 | 登录 |
| `POST /` | 管理员新增标签，可传 `groupId`；不传则归入默认分组 | `system:tag:add` |
| `PUT /` | 修改标签名称、分组、常用提示或状态；功能归属不可变 | `system:tag:edit` |
| `PUT /{tagId}/disable` | 软停用标签，保留历史业务关联 | `system:tag:remove` |
| `DELETE /{tagId}` | 删除未被业务记录引用的标签 | `system:tag:remove` |
| `POST /groups` | 新增功能内自定义分组 | `system:tag:add` |
| `PUT /groups` | 重命名自定义分组 | `system:tag:edit` |
| `DELETE /groups/{groupId}` | 删除空的自定义分组 | `system:tag:remove` |

### 字段约定

- 分组记录：`groupId`、`moduleKey`（功能标识）、`groupName`、`isDefault`、`sortOrder`。
- 标签记录：`tagId`、`moduleKey`（功能标识）、`moduleName`（功能名称）、`groupId`、`groupName`、`tagName`、`isCommon`、`status`。
- 所有 bigint ID 均以十进制字符串传输，前端不得转换为 JavaScript number。
- 标签名称在同一功能、同一分组中不区分大小写唯一；不同分组可以重名。
- 层级为“导航模块 → 功能 → 分组”；`moduleKey` 是功能命名空间，不是左侧导航模块。每个功能自动拥有“默认分组”。已有未分组标签迁入默认组时保留标签 ID 和全部业务关联。未指定分组的新标签也进入默认组。
- 默认分组不可删除或重命名；自定义分组仅在没有标签时可以删除。删除前需把标签改到其他分组。
- 标签只能在同一功能内改组，不支持跨功能迁移。标签停用不影响历史关联；业务选择器不允许新选停用标签。
- 标签表、分组表、关系表及 `system:tag:list/add/edit/remove` 权限点已纳入权威初始化文件 `sql/主建表.sql`；标签表和字段均有中文数据库注释，本次无需新增 DDL。

## MCP

通过当前员工本人登录态调用，服务端再次按下列权限码检查；写操作必须先展示完整影响并取得本次确认。MCP 工具复用 REST 控制器的命名空间校验、事务和业务审计。

所有标签、分组 ID 均须使用从前序查询复制的正十进制字符串；筛选状态仅接受 `"0"` 或 `"1"`。管理请求会拒绝未知字段、缺失必填字段及非法标志值，避免错误参数被默认解释为正常维护操作。

| 工具 | 权限 | 用途 |
|---|---|---|
| `tag_module_list` | `system:tag:list` | 查询已登记功能及标签数量 |
| `tag_group_list` | 登录 | 查询指定功能内分组与数量，包括默认分组 |
| `tag_list` | `system:tag:list` | 查询标签，可按名称、状态、分组筛选 |
| `tag_options` | 登录 | 查询指定功能可供新业务选择的启用标签 |
| `tag_create` | `system:tag:add` | 新增标签；可指定组，省略组时归入默认组 |
| `tag_update` | `system:tag:edit` | 修改标签名称、组、常用标记或状态；启用通过 `status="0"` |
| `tag_disable` | `system:tag:remove` | 停用标签并保留历史关联 |
| `tag_delete` | `system:tag:remove` | 删除未被业务记录引用的标签 |
| `tag_group_create` | `system:tag:add` | 新增自定义分组 |
| `tag_group_update` | `system:tag:edit` | 重命名自定义分组 |
| `tag_group_delete` | `system:tag:remove` | 删除空的自定义分组 |

### MCP 调用示例

先查询功能命名空间，再查询该功能分组；不要把左侧导航模块名称当作 `moduleKey`，也不要猜 ID：

```jsonl
{"name":"tag_module_list","arguments":{}}
{"name":"tag_group_list","arguments":{"moduleKey":"customer_master"}}
```

按功能查询标签。筛选参数可以省略；如需按分组筛选，`groupId` 必须使用分组查询实际返回的十进制字符串。下面是完整的只读调用：

```json
{"name":"tag_list","arguments":{"moduleKey":"customer_master","keyword":"品牌","status":"0"}}
```

新增标签前先展示并确认功能、分组、名称和常用标记。`moduleKey`/`groupId` 要来自前述查询结果；`isCommon` 使用字符串 `"0"` 或 `"1"`。以下是字段结构示意，尖括号内容必须替换为查询结果或用户确认的值，不可原样调用：

```text
{"name":"tag_create","arguments":{"body":{"moduleKey":"<实际moduleKey>","moduleName":"<功能显示名>","groupId":"<实际groupId字符串>","tagName":"<用户确认的标签名>","isCommon":"0"}}}
```

修改时传完整的当前目标字段，不能只传希望改变的一个字段；状态 `"0"` 为启用、`"1"` 为停用。标签 ID、分组 ID 均使用十进制字符串：

```text
{"name":"tag_update","arguments":{"body":{"tagId":"<实际tagId字符串>","moduleKey":"<实际moduleKey>","groupId":"<实际groupId字符串>","tagName":"<确认后的完整标签名>","isCommon":"0","status":"0"}}}
```

分组新增字段结构示意，尖括号内容必须替换，并先取得员工确认：

```text
{"name":"tag_group_create","arguments":{"body":{"moduleKey":"<实际moduleKey>","groupName":"<用户确认的分组名>"}}}
```

`POST /ensure` 是业务表单自动收录标签的 REST 接口，不开放为通用 MCP 管理工具，避免智能体绕过明确的标签新增权限任意登记功能标签。业务功能产生的标签关联仍由对应业务保存流程维护。

> 运行时记录：2026-11-20 MCP `tools/list` 已发现 11 个标签管理工具；尚未用普通员工账号执行读取、低权限拒绝及写入审计核验。后续账号级验证要求见《测试与验收清单》。

## MCP 输入字段矩阵（契约迁移批次 1）

下表覆盖 `TagMcpTools` 的顶层参数和所有自由 `Map` 内部字段。MCP inputSchema 只约束顶层对象参数类型，不会递归约束 `body` 的键和值；工具实现通过字段白名单和控制器校验拒绝未知或非法字段。所有 ID 都是从前置查询复制的正十进制字符串。

| 字段路径 | JSON 类型 / 示例 | 必填与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、约束与副作用 |
|---|---|---|---|---|
| `tag_group_list.moduleKey`、`tag_options.moduleKey` | string；`"customer_master"` | 必填；须先从 `tag_module_list` 读取 | null/空白不适用；无效命名空间由服务端拒绝 | 业务功能稳定命名空间；不是侧边导航模块名 |
| `tag_list.moduleKey` | string；`"customer_master"` | 必填；从 `tag_module_list` 读取 | null/空白不适用；无效命名空间拒绝 | 标签查询范围固定为一个业务功能 |
| `tag_list.keyword` | string；`"品牌"` | 可选；省略不按名称筛选 | null/纯空白不筛选；数组/对象拒绝 | 标签名称模糊筛选 |
| `tag_list.status` | string；`"0"` | 可选；省略查全部状态 | null/空白不筛选；仅字符串 `"0"`/`"1"` 可筛选 | 0 启用、1 停用；数字 0/1 拒绝 |
| `tag_list.groupId` | string 正十进制 ID；`"3001"` | 可选；省略查该功能全部分组 | null/空白不筛选；非字符串、非正 ID 拒绝 | 从同一 `moduleKey` 的 `tag_group_list` 读取；不得混用其他功能的分组 ID |
| `tag_create.body` | object；见下方 `arguments` | 必须提供对象，且必须含 `moduleKey`、`moduleName`、`tagName` | null/空对象拒绝；未知字段拒绝 | 新增单个标签；无幂等键，超时后先用 `tag_list` 查证，避免重复创建 |
| `tag_create.body.moduleKey`、`moduleName`、`tagName` | string；`"customer_master"` / `"客户档案"` / `"重点客户"` | 三者必填；不得省略、null 或空白 | 空白拒绝；数字/对象拒绝 | `moduleKey` 必须已登记；显示名与功能标识一致；标签名同功能同分组内不区分大小写唯一，最多长度按 REST 校验 |
| `tag_create.body.groupId` | string 正十进制 ID；`"3001"` | 可选；省略进入该功能默认分组 | null/空白按未提供处理并进入默认分组；非字符串或非法 ID 拒绝 | 只能用同一功能 `tag_group_list` 返回的自定义组或默认组 |
| `tag_create.body.isCommon` | string；`"0"` | 可选；省略默认 `"0"` | 若键显式出现且为 null/空白则拒绝；只能传字符串 `"0"`/`"1"` | 仅作为常用提示，不改变权限或业务状态 |
| `tag_update.body` | object；见下方 `arguments` | 必须含全部六个字段：`tagId`、`moduleKey`、`groupId`、`tagName`、`isCommon`、`status` | null/空对象拒绝；未知/缺少字段拒绝 | 完整提交可编辑最终态；无增量补丁语义 |
| `tag_update.body.tagId`、`groupId` | string 正十进制 ID；`"4001"` / `"3001"` | 必填；省略、null、空白均拒绝 | 非字符串或非法 ID 拒绝 | 标签 ID 来自 `tag_list`；目标分组来自同功能 `tag_group_list`；跨功能改组拒绝 |
| `tag_update.body.moduleKey`、`tagName` | string；`"customer_master"` / `"重点客户"` | 必填；省略拒绝 | null/空白拒绝 | 功能归属不可变；标签名需与目标同组的其他标签不重复 |
| `tag_update.body.isCommon`、`status` | string；`"1"` / `"0"` | 必填；省略拒绝 | null/空白/数字均拒绝；只能是字符串 `"0"` 或 `"1"` | `isCommon` 为常用提示；`status` 为生命周期（0启用、1停用），停用保留历史业务关系 |
| `tag_disable.tagId`、`tag_delete.tagId` | string 正十进制 ID；`"4001"` | 必填；ID 来自 `tag_list` | null/空白/数字/非正 ID 拒绝 | disable 软停用并保留关联；delete 永久删除且只允许无业务引用记录，不能把删除失败改为解除关联 |
| `tag_group_create.body` | object；`{"moduleKey":"customer_master","groupName":"客户分层"}` | 必须含 `moduleKey`、`groupName` | null/空对象拒绝；未知字段拒绝 | 创建自定义分组；无幂等键，重试前先查询 |
| `tag_group_create.body.moduleKey`、`groupName` | string；`"customer_master"` / `"客户分层"` | 必填；均不可省略 | null/空白拒绝 | 功能键先从 `tag_module_list` 读取；同一功能内分组名不区分大小写唯一 |
| `tag_group_update.body` | object；`{"groupId":"3001","moduleKey":"customer_master","groupName":"重要客户"}` | 必须含三个字段 | null/空对象拒绝；未知字段拒绝 | 只能重命名同功能的自定义分组，默认组由服务端拒绝 |
| `tag_group_update.body.groupId` | string 正十进制 ID；`"3001"` | 必填；从 `tag_group_list` 复制 | null/空白/数字/非法 ID 拒绝 | 不能指向其他功能分组或默认分组 |
| `tag_group_update.body.moduleKey`、`groupName` | string；`"customer_master"` / `"重要客户"` | 必填；均不可省略 | null/空白拒绝 | 分组功能归属不能变；名称须非空且同功能不重复 |
| `tag_group_delete.groupId` | string 正十进制 ID；`"3001"` | 必填；从 `tag_group_list` 复制 | null/空白/数字/非法 ID 拒绝 | 仅删除空自定义分组；默认组及含标签分组拒绝 |

写入权限分别为 `system:tag:add`（新增）、`system:tag:edit`（修改）和 `system:tag:remove`（停用/删除）；查询标签需 `system:tag:list`，分组/业务选项需员工登录。所有写工具都复用员工当前身份、服务端权限、命名空间检查及审计。

### 写操作 `arguments` 示例

以下均为结构示例；先读实际功能、分组和标签，再将示例 ID/名称替换成当前查询结果及员工确认内容。尖括号占位符不是可直接写入的业务值。

```jsonl
{"name":"tag_update","arguments":{"body":{"tagId":"4001","moduleKey":"customer_master","groupId":"3001","tagName":"重点客户","isCommon":"1","status":"0"}}}
{"name":"tag_disable","arguments":{"tagId":"4001"}}
{"name":"tag_delete","arguments":{"tagId":"4001"}}
{"name":"tag_group_create","arguments":{"body":{"moduleKey":"customer_master","groupName":"客户分层"}}}
{"name":"tag_group_update","arguments":{"body":{"groupId":"3001","moduleKey":"customer_master","groupName":"重要客户"}}}
{"name":"tag_group_delete","arguments":{"groupId":"3001"}}
```

`tag_update` 用完整字段，不能只传发生变化的字段。`tag_disable` 保留历史关联；`tag_delete` 不可恢复。分组删除前需查询确认 `isDefault=false` 且 `tagCount=0`。以上语义只在 API 字段表维护，Skill 应引用本节，不另行定义空值规则。
