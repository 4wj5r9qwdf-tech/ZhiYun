# 标签管理 API

> 状态：已按“导航模块 → 功能 → 分组”修正展示与筛选，开发库迁移、前后端构建和后端重启均完成，待用户验收。分组属于具体功能命名空间 `module_key`，业务记录仍以 `module_key + record_id + tag_id` 关联标签。

## REST 接口

基础路径：`/system/tag`

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| `GET /modules` | 查询已登记功能及标签数量（路径沿用历史名称） | `system:tag:list` |
| `GET /groups?moduleKey=...` | 查询具体功能内分组及标签数量，默认分组排在首位 | 登录 |
| `GET /list?moduleKey=...` | 查询标签及所属分组，可按 `keyword`、`status`、`groupId` 筛选 | `system:tag:list` |
| `GET /options?moduleKey=...` | 查询业务选择用的启用标签，包含 `groupId`、`groupName` | 登录 |
| `POST /ensure` | 业务表单幂等收录标签；未指定分组时归入默认分组 | 登录 |
| `POST /system/tag` | 管理员新增标签，可传 `groupId`；不传则归入默认分组 | `system:tag:add` |
| `PUT /system/tag` | 修改标签名称、分组、常用提示或状态；功能归属不可变 | `system:tag:edit` |
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

## MCP

标签管理目前没有独立 MCP 管理工具。业务 MCP 若返回或维护标签 ID，仍遵循上述功能隔离与默认分组规则；不得据此宣称标签分组已有运行时 MCP 工具。
