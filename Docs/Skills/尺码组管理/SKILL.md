---
name: zhiyun-size-group-management
description: 查询和维护织云 ERP 尺码组，包括组内尺码及顺序、状态、删除和自动编码规则。仅用于用户明确提出的尺码组操作。
---

# 尺码组管理

## 接入状态

- 功能已由用户验收通过（2026-11-04）。
- API 契约：[尺码组管理 API](../../API/尺码组管理API.md)。
- MCP 工具尚未注册；未接入前不得直接调用 REST 或数据库，应引导用户在 ERP 页面操作。

## 工具与权限（待注册）

| 操作 | MCP 工具 | 权限 |
|---|---|---|
| 查询尺码组 | `size_group_list` | `system:size-group:list` |
| 业务选择 | `size_group_options` | 登录 |
| 新增 | `size_group_create` | `system:size-group:add` |
| 修改 | `size_group_update` | `system:size-group:edit` |
| 启停 | `size_group_set_status` | `system:size-group:edit` |
| 删除 | `size_group_delete` | `system:size-group:delete` |
| 查询/修改编码规则 | `size_group_get_code_rule` / `size_group_set_code_rule` | 登录 / `system:config:edit` |

工具必须使用当前员工本人身份，不得使用超管代办或通用 HTTP/SQL 工具。

## 执行规则

- 查询尺码组时展示编号、名称、状态及有序组内尺码；停用项明确标注。
- 新增或修改前先查询目标尺码组和可用尺码，保留用户未要求改变的标签、备注和排序。`sizeIds` 至少一项且不能重复；数组顺序即组内顺序。
- 停用尺码不能新加到组，但已有引用可以保留。不得为删除尺码而自动修改尺码组。
- 编号可手动指定；留空按当前规则自动生成。自动规则可设置开关、前缀及 1–10 位数字长度，只影响新建组。
- 新增、修改、启停、删除、改编码规则均先展示完整影响并等待用户确认。删除前说明会清理组内明细和标签关联。
- 写入成功后以接口响应为准；新增请求超时后先查询核实，不盲目重试。

## 当前不可执行说明

虽然功能已验收，MCP 运行时工具仍未注册。本 Skill 仅作智能体接入契约；不得绕过工具目录直接调用 REST。
