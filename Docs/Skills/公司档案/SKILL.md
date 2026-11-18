---
name: zhiyun-company-record
description: 查询和维护织云 ERP 公司档案及登录页展示配置。适用于员工明确要求查询、新建、修改默认公司或删除档案。
---

# 公司档案 Skill

## 适用范围

维护登录页及首页展示用公司档案，包括标志、背景、主题、宣传语和默认展示公司。此功能已由用户验收通过（2026-11-05）；公司档案不代表多租户隔离。

## 契约与工具

- API 契约：[公司档案API.md](../../API/公司档案API.md)
- MCP 工具：`company_list`、`company_get`、`company_file_usage`、`company_create`、`company_update`、`company_set_default`、`company_delete`。
- 工具已加入后端代码；运行时发现状态需在后端启动后用 MCP 客户端核对。未发现工具时不得假称 MCP 可调用。
- 通用参数类型及 JSON-RPC 示例见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

调用 `company_list` 查询公司分页列表，分页参数省略时使用服务端默认值：

```json
{"name":"company_list","arguments":{}}
```

查询详情时，`companyId` 必须复制自列表响应的真实十进制字符串 ID：

```json
{"name":"company_get","arguments":{"companyId":"123456789012345678"}}
```

示例 ID 仅展示字符串格式，实际调用不得照抄该 ID。

## 操作规则

- 使用当前员工权限；写操作前展示公司名称及所有受影响字段并取得本次确认。
- 设置默认公司会取消原默认标记并影响登录页/首页。先查询旧默认项，明确展示新旧公司后再确认执行。
- 删除前查询 OSS 文件引用；不得因删除公司而自动删除仍被引用的文件。
- 写调用超时先查询当前结果，再决定是否重试。
