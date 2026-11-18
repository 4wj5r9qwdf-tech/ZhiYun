---
name: zhiyun-customer-record
description: 查询和维护织云 ERP 客户主档、联系人、地址、标签和业务员关联。用于员工明确要求查询、新增、修改、启停或删除客户资料。
---

# 客户档案 Skill

## 适用范围

客户主档、联系人、地址、标签、业务员关联以及新业务引用选项。此功能一期已由用户验收通过（2026-11-05）。订单与样衣业务链未因此视为已验收。

## 契约与工具

- API 契约：[客户档案API.md](../../API/客户档案API.md)
- MCP 工具：`customer_list`、`customer_category_counts`、`customer_get`、`customer_check_duplicate`、`customer_sales_options`、`customer_options`、`customer_create`、`customer_update`、`customer_set_status`、`customer_delete`。
- 工具已加入后端代码；运行时发现状态需在后端启动后用 MCP 客户端核对。未发现工具时不得假称 MCP 可调用。
- 参数类型、可选项处理和 JSON-RPC 完整示例见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

只按关键词查询启用客户；没有的筛选条件直接省略，不传 `null`：

```json
{"name":"customer_list","arguments":{"keyword":"示例客户","status":"0","pageNum":1,"pageSize":20}}
```

不筛选时可传空参数对象；服务端会使用第一页、每页20条的默认值：

```json
{"name":"customer_list","arguments":{}}
```

## 操作规则

- 先按员工本人权限查询；联系方式只在权限允许时读取原值，其他情况保持脱敏。
- 同名检查仅作提示，不得因同名自动阻止或合并客户。
- 新增、编辑、启停、删除前，展示客户身份与完整变更并取得本次确认。超时后先查询，避免重复新增。
- 删除仅在权限允许且无业务引用时执行；被引用时停止并说明原因。
- 国家/地区使用有效编码；地址地区、客户所在地与订单目的地不得混用。
- 禁止把电话、邮箱原值写入说明或日志。
