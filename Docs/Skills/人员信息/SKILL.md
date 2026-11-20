---
name: zhiyun-personnel-record
description: 查询和维护织云 ERP 人员档案、业务职责、钉钉号登记及人员分类权限；按权限区分人员档案工具和分类授权工具。
---

# 人员信息 Skill

## 适用范围

人员档案及钉钉号登记；另含人员分类权限管理契约。人员身份、数据范围和操作权限按系统实际授权执行。

## 契约与工具

- 人员档案契约：[人员信息 API](../../API/人员信息API.md)；分类授权契约：[人员分类权限 API](../../API/人员分类权限API.md)。
- 人员档案 MCP 工具：`person_list`、`person_get`、`person_business_duty_options`、`person_create`、`person_update`、`person_delete`、`person_restore`。2026-11-20 后端重启后认证 `tools/list` 已发现 7/7 个；`person_business_duty_options` 已做实际读取，人员档案读写和普通账号权限尚未验收。工具目录总计 158 个，人员分类授权另有独立 2 个工具，详见 API 文档及验收清单。
- 人员分类权限另有独立工具 `person_category_scope_get` 和 `person_category_scope_set`，只供具备 `system:person:categoryScope` 且在人员数据范围内的员工使用。档案查询/维护权限不自动包含分类授权权限。
- 人员档案工具沿用当前员工身份及系统权限、人员数据范围。列表/详情手机号脱敏，短号也不返回完整值；MCP 不返回证件号码、银行卡号、家庭住址和社保金额。不得将空结果或无权说明为人员不存在。
- MCP 的参数严格按 `tools/list` 的 schema 传入：列表筛选字段直接放在 `arguments` 顶层，新增放在 `arguments.body`，修改使用 `arguments.userId` + `arguments.changes`。不要把 REST URL、`filter` 包装或 `null` 填入 MCP 参数。
- 查询人员分类权限示例：

  ```json
  {"name":"person_category_scope_get","arguments":{"userId":"1793602859000"}}
  ```

- 设置指定范围示例：

  ```json
  {"name":"person_category_scope_set","arguments":{"userId":"1793602859000","scope":{"mode":"SELECTED","grants":[{"schemeId":"1793602859336","categoryId":null,"includeChildren":true}]}}}
  ```

- `ALL`、`NONE` 和 `SELECTED` 必须明确指定；`ALL`/`NONE` 的 `grants` 为空数组，`SELECTED` 至少一项。所有主键使用十进制字符串；无权、空结果和不存在不可混为一谈。运行时工具发现状态以 MCP 客户端 `tools/list` 为准。通用调用规则见[调用指南](../MCP调用指南.md)。

## 钉钉号

- 人员列表和详情字段为 `dingtalk_account`；人员新增/修改请求使用 `ext.dingTalkAccount`。字段选填，最长 128 字符，提交空字符串用于清空，省略字段保留原值。
- 修改前按员工身份核实目标，并说明具体变更；不得因同名推断人员身份。写入需要人员编辑权限，沿用人员业务审计。
- 钉钉号是手工登记信息，不证明已绑定钉钉企业人员，也不代表钉钉消息推送已经启用。不要将钉钉号、员工工号和钉钉企业用户 ID 假定为同一值。
- 涉及人员账号时只提供完成任务所需的信息，不输出无关人员敏感资料。

## 人员档案 MCP 流程

1. 先用 `person_list` 按工号或姓名查找，并核实返回的员工身份和数据范围；列表手机号已脱敏。
2. 需要查看工作资料时用 `person_get`；不返回证件、银行卡、家庭住址或社保金额。
3. 新增、修改、删除和恢复前，向用户展示工号、姓名及操作影响并取得本次确认。`person_update` 只传实际修改字段，省略字段会由服务端保留；不得把 `null` 当成“没改”。MCP 仅接受白名单工作字段和部分任职信息，不提交证件、银行卡、住址、出生信息或社保金额。
4. MCP 不接收密码、不重置密码、不做彻底删除或全量导出。MCP 新增账号后，由具备重置密码权限的人员在人员管理页面设置密码，员工才能登录。
5. 岗位/角色/组织 ID 必须来自 ERP 已授权资料，全部用十进制字符串，不猜 ID；业务职责代码先读取 `person_business_duty_options`。职责不是角色或系统权限。

## 调用示例

查询姓名或工号关键字；可选项未使用时直接省略：

```json
{"name":"person_list","arguments":{"keyword":"张三","pageNum":1,"pageSize":20}}
```

查询一名员工（`userId` 必须替换为列表返回的 `personId`）：

```json
{"name":"person_get","arguments":{"userId":"<personId>"}}
```

读取业务职责选项：

```json
{"name":"person_business_duty_options","arguments":{}}
```

新增人员后，由具备重置密码权限的人员在人员页面设置登录密码。新增人员只传确认过的字段。工号必须为六位数字字符串，禁止传 `password`；组织、岗位、角色 ID 只能使用已授权查询结果：

```json
{"name":"person_create","arguments":{"body":{"jobNo":"000123","name":"示例员工","businessDuties":["SALES"],"ext":{"dingTalkAccount":"employee@example.com"}}}}
```

仅修改钉钉号时使用补丁对象；不要重发整份人员档案，也不要传证件、银行卡、住址或社保等私人字段：

```json
{"name":"person_update","arguments":{"userId":"<personId>","changes":{"ext":{"dingTalkAccount":"employee@example.com"}}}}
```

软删除和恢复均需在操作前确认目标员工，并用当前查询结果中的 ID：

```json
{"name":"person_delete","arguments":{"userId":"<personId>"}}
```

```json
{"name":"person_restore","arguments":{"userId":"<personId>"}}
```

新增或修改人员时，只有实际增加或撤销角色才额外要求 `system:user:edit`，并检查角色数据范围。普通账号无法分配超级管理员角色。修改普通档案时可省略 `roleIds`，或传回原有角色列表。
