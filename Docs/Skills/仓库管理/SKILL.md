---
name: zhiyun-warehouse-management
description: 查询和维护织云 ERP 仓库档案、仓库类型及仓库自动编码规则。适用于用户明确要求查询、新建、修改、启停或删除仓库，以及调整仓库编码规则。
---

# 仓库管理

## 接入状态

- 功能已由用户验收通过（2026-11-05）。
- API 契约：[仓库管理 API](../../API/仓库管理API.md)。
- MCP 查询与写入工具已接入，后端构建并重启成功；首次使用仍应确认 MCP 客户端能发现工具，并通过有效员工 token、`clientid` 与权限检查。
- CSV 导入工具未注册。不得从 Skill 直接调用 REST、数据库或任意 HTTP/SQL 工具代替未注册的批量导入工具。

## 可用工具

| MCP 工具 | 用途 | 权限 |
|---|---|---|
| `warehouse_list` | 按关键词/状态/类型查询仓库 | `inventory:warehouse:list` |
| `warehouse_get` | 查询仓库详情与类型标签 | `inventory:warehouse:query` |
| `warehouse_form_options` | 查询建筑和负责人选项 | 登录 |
| `warehouse_next_code` | 查看下一个仓库编码建议 | `inventory:warehouse:add` |
| `warehouse_create` | 新增仓库 | `inventory:warehouse:add` |
| `warehouse_update` | 修改仓库 | `inventory:warehouse:edit` |
| `warehouse_set_status` | 启用/停用仓库 | `inventory:warehouse:edit` |
| `warehouse_delete` | 逻辑删除仓库 | `inventory:warehouse:remove` |
| `warehouse_set_code_rule` | 修改仓库编码规则 | `system:config:edit` |

## 查询流程

1. 查询清单优先调用 `warehouse_list`；需要完整字段或标签 ID 时按结果仓库 ID 调用 `warehouse_get`。
2. ID 始终作为十进制字符串传递。按结果原样说明仓库编码、名称、所属建筑/楼层/区域、负责人、入库定位层级、状态和仓库类型。
3. `warehouse_next_code` 只返回建议，不会预留编码或创建仓库；用户可自行编辑建议值。

## 写入请求处理

用户明确要求写入时，先查询目标记录及当前规则，展示完整字段、变更和影响，再等待员工本次确认；确认后调用对应 MCP 工具。工具写入以响应为准，并在失败后先查询核对，不盲目重试新增。没有批量导入 MCP 工具，CSV 请用户在页面导入。

## 安全与边界

- 工具必须以发起员工本人身份执行。权限不足时停止并说明缺少相应权限，不切换管理员身份。
- 仓库类型为标签关联；停用仓库保留档案和历史引用。
- 仓库下存在库区/货架/货位时不能删除，仓库编码也不能修改；不要自动处理布局来绕开限制。
- 调整编码规则会影响以后新增仓库，不改已有编码；先读取并展示旧规则和完整新规则，再等待员工确认。
- 不猜测建筑、负责人或仓库类型；重复编码或引用校验失败后按后端原因停止。
