# 人员信息 API 与 MCP 契约

> 状态：人员档案 MCP 已接入并在开发服务认证 `tools/list` 中发现 7/7 个工具；人员档案整体 7 个工具，人员分类权限另有独立 2 个工具。`person_business_duty_options` 已实际读取成功；未读取人员列表/详情或执行人员新增、修改、删除、恢复。REST 路由复用人员权限、数据范围、业务校验和审计。列表/详情只返回工作所需的白名单字段；MCP 新增/修改只接受限定的工作字段，不接受证件、银行卡、家庭住址及社保金额等敏感个人字段。普通账号读写权限待核；人员信息模块整体仍待用户验收。

## 一、权限与数据边界

| 操作 | 权限 | 数据范围与行为 |
|---|---|---|
| 人员列表 | `system:person:list` | 只查在册员工；隐藏内置 `admin`，按当前用户人员数据范围过滤 |
| 人员详情 | `system:person:query` | 详情同样检查当前用户人员数据范围；MCP手机号脱敏，不返回证件号码、银行卡号、住址和社保金额 |
| 新增 | `system:person:add` | 创建人员登录账号，记录人员业务审计；MCP 不接收或返回密码 |
| 修改 | `system:person:query` + `system:person:edit` | MCP 使用补丁语义；服务端先取当前档案并合并，只改提交字段。工号不可通过此工具修改 |
| 软删除 | `system:person:query` + `system:person:remove` | 停用登录并软删除，保留历史关联；禁止操作超级管理员及内置 `admin` |
| 恢复 | `system:person:query` + `system:person:edit` | 恢复已软删档案；服务端检查数据范围及工号冲突 |
| 业务职责选项 | `system:person:list` | 返回 `SALES`、`FOLLOWER`、`PURCHASER`、`PLANNER`、`WAREHOUSE`、`QUALITY`；职责不等于岗位或权限 |

所有人员、组织、岗位及角色 ID 在 MCP 中以十进制字符串传递，不能把 bigint 转成浮点数。MCP 不开放密码重置、导出和彻底删除；人员删除仅为可恢复软删除。创建/修改只允许指定的基本工作字段和少量入职、任职信息；证件、银行卡、家庭地址、出生信息及社保金额等扩展字段会被拒绝。创建人员沿用当前系统的初始密码处理规则，密码必须通过人员页面或既有安全流程维护，不能要求模型生成、复述或返回密码。

`person_create` / `person_update` 的 `ext` 只接受 `dingTalkAccount`、`hireDate`、`groupName`、`groupId`、`empType`、`empStatus`、`leaveDate`、`signEntity`、`contractYears`、`contractStart`、`contractEnd`、`isDorm`；证件信息、银行卡、住址、出生/籍贯/婚姻信息、社保/商业保险明细等字段会被明确拒绝，不会静默忽略。

`ext` 示例：

```json
{"dingTalkAccount":"employee@example.com","empType":"正式","empStatus":"在职"}
```

## 二、REST 路由

| 方法 + 路径 | 用途 | 权限 |
|---|---|---|
| `GET /system/person/list` | 分页查询，可按工号/姓名/手机号、状态、组织、岗位、角色和业务职责筛选 | `system:person:list` |
| `GET /system/person/business-duty-options` | 读取业务职责字典 | `system:person:list` |
| `GET /system/person/{userId}` | 查询人员详情 | `system:person:query` |
| `POST /system/person` | 新增人员及关联岗位、角色、业务职责 | `system:person:add` |
| `PUT /system/person` | 修改人员主档及扩展信息 | `system:person:edit` |
| `DELETE /system/person/{userId}` | 软删除并停用账号 | `system:person:remove` |
| `PUT /system/person/{userId}/restore` | 恢复软删除人员 | `system:person:edit` |

编辑、恢复、删除、密码重置和彻底删除的目标检查均在锁定目标记录时应用人员数据范围，防止只靠 MCP 预查后产生越权或并发绕过。所有写操作仍由后端写人员业务审计。

## 三、MCP 工具

| 工具 | 权限 | 说明 |
|---|---|---|
| `person_list` | `system:person:list` | 分页查询在册员工；列表手机号脱敏（短号也不返回完整值），排除系统账号及已删除人员 |
| `person_get` | `system:person:query` | 按十进制字符串 ID 查询安全投影；不返回证件、银行、家庭地址和社保金额 |
| `person_business_duty_options` | `system:person:list` | 读取业务职责代码与名称 |
| `person_create` | `system:person:add` | 新增人员；不接收密码参数，成功时返回人员 ID 和工号 |
| `person_update` | `system:person:query`、`system:person:edit` | 按字段补丁更新；省略字段由服务端保留，显式传 `null`/空值表示清空相应字段 |
| `person_delete` | `system:person:query`、`system:person:remove` | 可恢复软删除，停用登录，不做物理删除 |
| `person_restore` | `system:person:query`、`system:person:edit` | 恢复已软删人员，先检查工号冲突 |

新增、修改、删除和恢复是写操作，需在展示目标员工、字段变化及结果后取得本次明确确认。不得因无权或空结果断言员工不存在；不得借用超管身份扩大工具权限。

### 查询列表

```json
{"name":"person_list","arguments":{"keyword":"张三","pageNum":1,"pageSize":20}}
```

按业务职责筛选：

```json
{"name":"person_list","arguments":{"businessDuty":"SALES","pageNum":1,"pageSize":20}}
```

### 查询详情

```json
{"name":"person_get","arguments":{"userId":"1793602859000"}}
```

### 新增人员

```json
{"name":"person_create","arguments":{"body":{"jobNo":"000123","name":"张三","gender":"1","orgId":"1793602859001","postIds":[],"roleIds":[],"businessDuties":["SALES"],"ext":{"empStatus":"试用","empType":"正式","hireDate":"2026-11-20","dingTalkAccount":"zhangsan"}}}}
```

`jobNo` 必须为 6 位数字字符串；新建成功返回新人员 ID。不要传 `password`，MCP 新建后需由具有重置密码权限的人员在人员页面设置密码，工具不会接收或回传口令。

### 修改人员

```json
{"name":"person_update","arguments":{"userId":"1793602859000","changes":{"name":"张三","businessDuties":["SALES","FOLLOWER"],"ext":{"dingTalkAccount":"zhangsan"}}}}
```

`changes` 是补丁对象，只传要变更的字段。比如不传 `postIds`、`roleIds`、`ext.address` 时，服务端会从当前档案补齐并保留它们；要撤销全部角色时传 `roleIds: []`，并满足用户授权权限。不能修改工号或密码。

## 四、MCP 明确不开放的操作

- 密码重置及密码传输：不在 MCP 参数、返回值或审计摘要中处理口令。
- 彻底删除：删除会清理该人员的角色、岗位、待办等多张关联表，必须走受控管理页面。
- 全量导出：人员导出包含更多个人信息，只通过现有受权页面执行。
- 分类权限：使用独立的 `person_category_scope_get` / `person_category_scope_set`，权限为 `system:person:categoryScope`，不能与人员档案 CRUD 权限混为一谈。

人员编辑接口拒绝提交密码。设置新密码使用 `PUT /system/person/{userId}/reset-password`，请求体为 `{"password":"<新密码>"}`，需具备 `system:person:resetPwd` 权限。密码至少 8 个字符且最多 72 字节。

新增或修改人员时，只有实际增加或撤销角色才额外要求 `system:user:edit`，并检查角色数据范围。普通账号无法分配超级管理员角色。修改普通档案时可省略 `roleIds`，或传回原有角色列表。
