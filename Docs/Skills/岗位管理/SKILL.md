---
name: zhiyun-post-management
description: 查询和维护织云 ERP 岗位档案、岗位状态及组织筛选。适用于员工明确要求查询、新增、修改、启停或删除岗位。
---

# 岗位管理 Skill

参数类型、完整更新字段与 null 语义以[岗位管理 API 的 MCP 字段矩阵](../../API/岗位管理API.md#MCP-输入字段矩阵契约迁移批次-1)为准；本 Skill 只编排调用。

## 适用范围

查询和维护岗位档案、岗位状态、岗位选项及其组织筛选。岗位管理已由用户验收通过（2026-11-05）；角色管理不包含在此验收结论中。

## 契约与工具

- API 契约：[岗位管理API.md](../../API/岗位管理API.md)
- MCP 工具：`post_list`、`post_get`、`post_options`、`post_create`、`post_update`、`post_set_status`、`post_delete`、`post_org_tree`。
- 工具已加入后端代码；运行时发现状态需在后端启动后用 MCP 客户端核对。未发现工具时不得假称 MCP 可调用。
- 调用参数必须按工具 schema 传递；具体格式见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

按岗位名称关键词分页查询；不需要的 `query` 筛选对象可省略：

```json
{"name":"post_list","arguments":{"keyword":"业务员","pageNum":1,"pageSize":20}}
```

## 写入参数示例

```jsonl
{"name":"post_create","arguments":{"body":{"orgId":"10001","belongOrgId":"10001","postCode":"CUTTER","postName":"裁剪员","postCategory":"生产","postSort":10,"status":"0","remark":"负责裁剪工序"}}}
{"name":"post_update","arguments":{"body":{"postId":"20001","orgId":"10001","belongOrgId":null,"postCode":"CUTTER","postName":"裁剪员","postCategory":"生产","postSort":10,"status":"0","remark":"负责裁剪工序"}}}
{"name":"post_set_status","arguments":{"postId":"20001","status":"1"}}
{"name":"post_delete","arguments":{"postIds":"20001,20002"}}
```

ID 从当前员工可见岗位和组织查询复制为字符串。修改前读取完整岗位字段并确认，停用已有员工的岗位会被服务端拒绝；删除前逐项核对岗位身份。

## 操作规则

- 所有操作使用当前员工权限。新增或修改前展示编码、名称、组织归属和状态差异，并取得本次确认。
- 停用或删除前查询员工分配情况；有关联而后端拒绝时，不得转而修改人员或角色配置。
- 批量删除前逐项列出岗位编码和名称并确认完整清单。
- 本 Skill 不管理角色权限；岗位默认角色配置如会影响授权，必须单独说明影响并确认。
- 写操作超时后先查询核实，不盲目重试。
