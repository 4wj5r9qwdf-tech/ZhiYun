---
name: zhiyun-tag-management
description: 查询和维护织云 ERP 通用标签目录，按导航模块、业务功能、功能内分组定位标签。
---

# 通用标签管理

> 版本：v1
> 更新日期：2026-11-20
> 说明：用户验收通过。分组属于具体业务功能；MCP 管理工具已实现，运行时发现/调用状态以《标签管理 API》及《测试与验收清单》记录为准。

## 适用范围与工具

用于维护全系统共用的标签目录结构和标签项，包括查询功能、查看功能内分组、筛选标签、创建/修改/停用/删除标签及维护自定义分组。业务记录上的标签关联由客户、物料、款式等对应业务功能维护，不由标签管理工具代替。

调用前按需阅读 `Docs/API/标签管理API.md` 和[通用 MCP 调用指南](../MCP调用指南.md)。工具名如下：

- 查询功能：`tag_module_list`
- 查询分组：`tag_group_list`
- 查询标签：`tag_list`、`tag_options`
- 标签维护：`tag_create`、`tag_update`、`tag_disable`、`tag_delete`
- 分组维护：`tag_group_create`、`tag_group_update`、`tag_group_delete`

## 必须遵循的定位和 ID 规则

- 层级是“导航模块 → 业务功能 → 功能内分组”。`moduleKey` 是业务功能的稳定命名空间，例如 `customer_master`，不是“客户管理”这类导航模块名称。
- 先用 `tag_module_list` 读取已登记功能，再用 `tag_group_list` 获取该功能的分组与实际 `groupId`。不得猜测或自行编造 `moduleKey`、`groupId`、`tagId`。
- 所有 bigint ID 在工具参数中使用十进制字符串，禁止转为 JavaScript 数字。
- 标签 ID 在不同功能/分组之间不可互换。同一功能内可重名的分组也不能只按名称判断，必须同时使用其实际 ID。
- 每个已登记功能有不可删除、不可重命名的“默认分组”。未传 `groupId` 的新标签进入默认分组。
- 标签只能在所属功能内部改组，不能跨功能迁移。默认分组不可删除或改名；自定义分组仅在没有标签时可删除。

## 查询流程与示例

1. 需要查看标签管理入口中有哪些已登记功能时，调用 `tag_module_list`：

```json
{"name":"tag_module_list","arguments":{}}
```

2. 选定实际返回的 `moduleKey` 后，读取其分组：

```json
{"name":"tag_group_list","arguments":{"moduleKey":"customer_master"}}
```

3. 查询功能内全部标签或按名称、状态和分组筛选。可选字段省略即可，不要传 `null`：

```json
{"name":"tag_list","arguments":{"moduleKey":"customer_master","keyword":"品牌","status":"0"}}
```

4. 业务表单需要新选择的启用标签时使用 `tag_options`。停用项不在新选项中；若要查看历史停用记录，改用 `tag_list` 并查询 `status="1"`。

## 写入流程

每次写操作都必须先展示完整目标和影响，取得用户本次明确确认后再调用工具。ERP 会再按当前员工身份校验对应权限；权限不足时停止，不换用超管身份、不绕过 MCP。

### 新增标签

调用 `tag_create`，至少提交 `moduleKey`、`moduleName`、`tagName`。`groupId` 可选；指定分组必须来自该功能的实际分组查询。`isCommon` 使用字符串 `"0"`（普通）或 `"1"`（作为常用提示）：

```text
{"name":"tag_create","arguments":{"body":{"moduleKey":"<实际moduleKey>","moduleName":"<功能显示名>","groupId":"<实际groupId字符串>","tagName":"<用户确认的标签名>","isCommon":"1"}}}
```

尖括号只用于说明参数来源，调用前必须替换为实际查询值；禁止将占位文字原样发送。省略 `groupId` 时服务端会使用默认分组。

管理端只能维护有明确功能归属的标签。若目标功能未登记，先检查该业务功能是否已使用标签；不要猜一个命名空间来创建空目录。

### 修改、启用和停用

- `tag_update` 要提交完整字段：`tagId`、`moduleKey`、`groupId`、`tagName`、`isCommon`、`status`。不要只发送变化字段。
- `status="0"` 为启用，`status="1"` 为停用；启用由 `tag_update` 完成。
- `tag_disable` 用于软停用，保留历史关联并阻止业务新选。停用后不得尝试删除来替代停用。
- 修改分组时，先确认标签所属功能与新分组相同；禁止跨功能移动。

### 删除标签或分组

- `tag_delete` 仅可删除没有任何业务记录关联的标签。被引用时服务端会拒绝；停止重试，改为解释并建议停用。
- `tag_group_delete` 仅可删除空的自定义分组。先通过 `tag_group_list` 查看 `isDefault` 与 `tagCount`；默认分组或非空分组不能删除。
- 删除不可恢复。调用前说明准确名称、所属功能和分组，并取得用户本次确认。

### 分组维护

- `tag_group_create`：提交 `moduleKey` 与 `groupName`。先查询分组，避免重复名称。
- `tag_group_update`：提交完整定位 `groupId`、`moduleKey` 与新 `groupName`；默认分组不能重命名。
- 分组名称在同一功能内不区分大小写唯一，不同功能可以重名。

## 权限、审计与边界

- 查询标签目录需要 `system:tag:list`；分组目录和业务选项要求员工登录。
- 新增标签/分组需要 `system:tag:add`；修改需要 `system:tag:edit`；停用或删除及删除分组需要 `system:tag:remove`。
- MCP 通过员工当前 token 执行，并复用 REST 校验和业务审计；不得使用超管 token 代员工操作。
- `POST /system/tag/ensure` 是业务表单自动收录入口，不是通用标签管理 MCP 工具。不要用它绕过管理权限或创建任意功能目录。
- 本工具维护标签目录和分组；业务对象与标签的关联仍由相应业务功能完成。新增标签目录项不代表客户、物料或款式已关联该标签。
- 请求超时或结果不明时，先用 `tag_list` 或 `tag_group_list` 查证，再决定是否继续；不要盲目重复创建或删除。
