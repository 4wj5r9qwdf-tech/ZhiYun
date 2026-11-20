# 标签管理 API

> 状态：用户于 2026-11-20 验收通过。REST、权限、主建表、Skill 和 MCP 管理工具已接入；2026-11-20 开发服务重启后 `tools/list` 已发现标签管理 11 个工具。普通员工权限、真实读取及写入审计仍待账号级核验。分组属于具体功能命名空间 `module_key`，业务记录仍以 `module_key + record_id + tag_id` 关联标签。

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

```json
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
