---
name: zhiyun-company-record
description: 查询和维护织云 ERP 公司档案及登录页展示配置。适用于员工明确要求查询、新建、修改默认公司或删除档案。
---

# 公司档案 Skill

## 适用范围

维护登录页及首页展示用公司档案，包括标志、背景、主题、宣传语和默认展示公司。此功能已由用户验收通过（2026-11-05）；公司档案不代表多租户隔离。

## 契约与工具

- API 契约：[公司档案API.md](../../API/公司档案API.md)
- 字段类型、必填/省略、`null`/空白和完整替换语义，以[公司档案 MCP 输入字段矩阵](../../API/公司档案API.md#mcp-输入字段矩阵契约迁移批次-1)为准。
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

## 写入调用示例

新增档案；`companyName` 必填，文件 ID 从 OSS 上传结果复制。其他空值与默认规则见 API 字段矩阵：

```json
{"name":"company_create","arguments":{"body":{"companyName":"织云示例公司","logoOssId":"20001","loginBgOssId":null,"slogan":"专注服装制造","loginBgMode":"A","nameColor":"#FFFFFF","theme":"industrial-blue","isDefault":"0"}}}
```

修改时提交完整可编辑字段，`companyId` 从列表/详情结果原样复制；将 Logo 设为 `null` 表示清除引用：

```json
{"name":"company_update","arguments":{"body":{"companyId":"10001","companyName":"织云示例公司","logoOssId":null,"loginBgOssId":"20002","slogan":"专注服装制造","loginBgMode":"B","nameColor":"#FFFFFF","theme":"industrial-blue","status":"0","isDefault":"0"}}}
```

执行前把公司名称、文件变化、状态和默认公司影响展示给员工，并等待本次明确确认。`company_update.isDefault="0"` 不取消当前默认公司；切换默认必须使用 `company_set_default`，其目标 ID 同样必须是十进制字符串。
