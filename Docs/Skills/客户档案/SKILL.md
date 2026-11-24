---
name: zhiyun-customer-record
description: 查询和维护织云 ERP 客户主档、客户分类、联系人、地址、标签和业务员关联。用于员工明确要求查询、新增、修改、启停或删除客户资料。
---

# 客户档案 Skill

## 适用范围

客户主档、分类、联系人、地址、标签、业务员关联以及新业务引用选项。客户档案主流程和 2026-11-20 验收的客户分类默认类型/重置、联系人/地址弹出详情均已验收。客户订单与样衣业务链不因客户档案验收而自动视为通过。

## 契约与工具

- API 契约：[客户档案API.md](../../API/客户档案API.md)
- MCP 工具：`customer_category_options`、`customer_list`、`customer_category_counts`、`customer_get`、`customer_check_duplicate`、`customer_sales_options`、`customer_options`、`customer_create`、`customer_update`、`customer_set_status`、`customer_delete`。
- 2026-11-20 开发服务 MCP `tools/list` 已发现上述 11 个工具，包含 `customer_category_options` 和分类筛选参数。普通员工分类范围下的实际结果尚未账号级核验；工具可发现不代表已完成权限验收。
- 参数类型、空值处理和 JSON-RPC 调用结构见[织云 ERP MCP 调用指南](../MCP调用指南.md)。
- 所有字段空值、嵌套联系人/地址、完整集合替换和 ID 来源以[客户档案 API 字段矩阵](../../API/客户档案API.md#MCP-输入字段矩阵契约迁移批次-1)为准；本 Skill 只编排调用。

## 推荐查询顺序

第一步，读取当前人员可访问的客户分类。不要自行猜测分类 ID：

```json
{"name":"customer_category_options","arguments":{}}
```

检查 `data.restricted`、`data.defaultSchemeId` 与 `data.rows`：

- 只从 `selectable=true` 的节点取 `id` 作为 `categoryId`；只用于树导航的父级节点不可提交。
- 页面初始查询和重置使用 `defaultSchemeId`；若为空，表示不按分类类型默认过滤。
- `restricted=true` 表示当前人员的分类范围有限。查不到分类或客户时，先检查 `rows` 和人员分类授权；不要断言系统没有数据。

第二步，按关键词查客户；没有的可选条件直接省略，不传 `null`：

```json
{"name":"customer_list","arguments":{"keyword":"示例客户","pageNum":1,"pageSize":20}}
```

要按默认分类类型查询，将第一步返回的实际 `defaultSchemeId` 作为字符串填入 `schemeId`。按某个分类节点查询则改用实际可选节点 `id` 作为字符串 `categoryId`；两者只能选一个：

```json
{"name":"customer_list","arguments":{"schemeId":"<第一步返回的实际defaultSchemeId>","pageNum":1,"pageSize":20}}
```

`categoryId="0"` 代表未分类，仅全部可见范围的人员可以查询未分类客户。不要把 `categoryId` 和 `schemeId` 同时传入，也不要把界面显示名称传给 ID 参数。

## 分类与可见范围

- 默认分类类型是页面筛选和重置的兜底；它不会授予人员分类权限，也不会替新增客户自动选择具体分类。
- 人员分类授权由服务端强制执行，覆盖客户列表、详情、统计、同名检查、新业务选项、修改、启停和删除。前端隐藏分类不能替代服务端权限。
- 全部可见人员可查看未分类客户。指定范围人员只能读取授权分类内客户，未分类客户及无权分类的客户不会返回，也不能新增到或转入无权分类。
- 空结果不等于客户不存在。对受限人员先核对分类选项、默认分类类型和授权范围，再说明查询结果。

## 操作规则

- 先按员工本人权限查询；联系方式只在权限允许时读取原值，其他情况保持脱敏。
- 新增、编辑、启停、删除前，展示客户身份与完整变更并取得本次确认。超时后先查询，避免重复新增。
- 新增/修改分类只能使用 `customer_category_options` 中可选择且有效的节点；修改省略 `categoryId` 表示保留当前分类，传空字符串表示清空（受限人员不能清空到未分类）。
- 联系人、地址、标签等集合按完整数组提交；省略表示最终清空。提供集合时必须使用 JSON 数组，错误类型会被拒绝，不能用对象、逗号字符串或 `null` 代替。
- 同名检查仅作提示，不得因同名自动阻止或合并客户。
- 删除仅在权限允许且无业务引用时执行；被引用时停止并说明原因。
- 国家/地区使用有效编码；地址地区、客户所在地与订单目的地不得混用。
- 禁止把电话、邮箱原值写入说明或日志。

### 写入 arguments 示例

新增前先执行分类选项、业务员选项和标签查询；向员工展示全部主档与关联集合并确认后再调用。下例 ID 均须替换为本次查询结果：

~~~json
{"name":"customer_create","arguments":{"body":{"customer_name":"示例客户","customer_short_name":"示例","unified_social_credit_code":"","country_region":"中国","country_region_code":"CN","categoryId":"<可选分类ID>","sales_representative_id":"<在职业务员ID>","remark":"示例说明","tagIds":["<客户标签ID>"],"contacts":[{"contact_name":"王女士","contact_title":"采购","phone_number":"021-55550000","email":"buyer@example.com","contact_role":"采购联系人","is_primary":true}],"addresses":[{"address_type":"样衣收件","recipient_name":"王女士","recipient_phone":"021-55550000","country_region":"中国","country_region_code":"CN","province":"上海市","city":"上海市","district":"浦东新区","address_line":"示例路1号","postal_code":"200000","is_default":true}]}}}
~~~

修改前调用 customer_get 并构造完整最终集合。若用户没有要求删除联系人、地址或标签，保留当前项；不要省略任一集合：

~~~json
{"name":"customer_update","arguments":{"customerId":"<从customer_list复制的客户ID>","body":{"customer_name":"示例客户","customer_short_name":"示例","unified_social_credit_code":"","country_region":"中国","country_region_code":"CN","categoryId":"<当前分类ID>","sales_representative_id":"<当前业务员ID>","remark":"示例说明","tagIds":["<保留的标签ID>"],"contacts":[{"contact_name":"王女士","contact_title":"采购","phone_number":"021-55550000","email":"buyer@example.com","contact_role":"采购联系人","is_primary":true}],"addresses":[{"address_id":"<该客户已有地址ID>","address_type":"样衣收件","recipient_name":"王女士","recipient_phone":"021-55550000","country_region":"中国","country_region_code":"CN","province":"上海市","city":"上海市","district":"浦东新区","address_line":"示例路1号","postal_code":"200000","is_default":true}]}}}
~~~

尖括号内容只标示值的来源，调用前必须替换成真实查询结果；不需要分类时按 API 字段矩阵传省略或空值语义，不要发送占位文字。
