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
| `person_update` | `system:person:query`、`system:person:edit` | 按字段补丁更新；省略字段保留；关联集合用 `[]` 清空，`null` 集合拒绝；标量字段按字段矩阵处理 `null`/空串 |
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

## 五、MCP 写入字段矩阵（契约迁移批次 1）

本矩阵描述 `person_create.arguments.body`、`person_update.arguments.changes`、删除/恢复参数。MCP Map 适配器先校验白名单和 JSON 类型，再由 `PersonController` 执行数据范围、关系权限和业务审计。除字段矩阵明确允许的 JSON 形态外，其他类型均拒绝。创建对象除表中列出的工作字段外，不接受密码或证件、银行卡、住址、出生、籍贯、婚姻、社保及商业保险字段；嵌套 `ext` 只接受本表列出的 12 个字段。

### 新增 `person_create.arguments.body`

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| `person_create.arguments.body` | object；`{"jobNo":"000123","name":"示例员工"}` | 必填；省略、`null` 或 `{}` 拒绝 | 空对象因缺少必填字段拒绝 | 仅允许下面列出的字段；未知字段拒绝，敏感字段不会被静默忽略 |
| `person_create.arguments.body.jobNo` | string；`"000123"` | 必填；省略拒绝 | `null`/空白拒绝 | 六位数字工号；须唯一；作为登录用户名，不可在更新工具修改 |
| `person_create.arguments.body.name` | string；`"示例员工"` | 必填；省略拒绝 | `null`/空白拒绝 | 人员姓名；写入基本档案并记业务审计 |
| `person_create.arguments.body.gender` | string 或 null；`"1"` | 可选；省略写 null | `null` 或 `""` 表示未登记；纯空格及数组/对象拒绝 | `"1"` 男、`"2"` 女；其他非空值拒绝 |
| `person_create.arguments.body.email` | string 或 null；`"employee@example.com"` | 可选；省略写 null | `null` 清空；空字符串保留为空值文本；数组/对象拒绝 | 工作邮箱；MCP 不做邮箱格式校验，数据库列长 200 |
| `person_create.arguments.body.phoneNumber` | string 或 null；`"13800001234"` | 可选；省略写 null | `null` 清空；空字符串保留为空值文本；数组/对象拒绝 | 登录手机号/联系电话；只接受字符串，列表和详情响应按权限脱敏 |
| `person_create.arguments.body.orgId` | string 或 null；`"1793602859001"` | 可选；省略不关联组织 | `null` 或精确空字符串 `""` 不关联；空格拒绝 | 从组织选项读取；正十进制字符串；组织存在性和数据范围由后端校验 |
| `person_create.arguments.body.status` | string；`"0"` | 可选；省略默认 `"0"` 正常 | `null`/空白拒绝；仅 `"0"` 或 `"1"` | 登录账号状态：0正常、1停用；不会代替人员业务在职状态 |
| `person_create.arguments.body.remark` | string 或 null；`"仓库新员工"` | 可选；省略写 null | `null` 清空；空字符串/空格原样保存；数组/对象拒绝 | 人员备注；数据库长度/约束为准 |
| `person_create.arguments.body.postIds` | string 数组；`["20001"]` | 可选；省略不分配岗位 | `null` 等同不分配；`[]` 不分配；元素必须是 ID 字符串；重复 ID 拒绝 | 岗位 ID 从岗位选项读取；全量关联集合；越权/无效岗位由后端拒绝 |
| `person_create.arguments.body.roleIds` | string 数组；`["30001"]` | 可选；省略不分配角色 | `null` 等同不分配；`[]` 不分配；元素必须是 ID 字符串；重复 ID 拒绝 | 角色 ID 从有权查询的角色选项读取；授予角色另需 `system:user:edit` 并遵守角色数据范围 |
| `person_create.arguments.body.businessDuties` | string 数组；`["SALES","FOLLOWER"]` | 可选；省略无业务职责 | `null`/`[]` 均无职责；元素须为字符串且不得重复 | 允许 `SALES`、`FOLLOWER`、`PURCHASER`、`PLANNER`、`WAREHOUSE`、`QUALITY`；职责不授予系统权限 |
| `person_create.arguments.body.ext` | object；`{"hireDate":"2026-11-20","empStatus":"试用"}` | 必填对象；省略或 `null` 拒绝 | `{}` 因缺少 `hireDate`/`empStatus` 拒绝 | 仅接收允许的任职字段；其他敏感扩展字段拒绝 |
| `person_create.arguments.body.ext.dingTalkAccount` | string 或 null；`"employee@example.com"` | 可选；省略不登记 | `null`、`""` 或纯空格清空；超长拒绝 | 手工登记钉钉号，最多 128 字符；不代表已绑定钉钉身份 |
| `person_create.arguments.body.ext.hireDate` | string；`"2026-11-20"` | 必填；省略、`null`、空白拒绝 | 必须为有效 `YYYY-MM-DD` 日期 | 入职日期；格式及有效性由 MCP 和数据库日期类型校验 |
| `person_create.arguments.body.ext.groupName` | string 或 null；`"一组"` | 可选；省略不登记名称 | `null` 清空；空白按原值保存 | 小组名称文本；最多 50 字符；若同时传有效 `groupId`，后端用组织树小组名称覆盖 |
| `person_create.arguments.body.ext.groupId` | string 或 null；`"1793602859002"` | 可选；省略不关联小组 | `null` 或 `""` 清除关联；空格拒绝 | 从组织树小组选项读取正十进制字符串；有效 ID 时小组名称由节点名称同步 |
| `person_create.arguments.body.ext.empType` | string 或 null；`"正式"` | 可选；省略不登记 | `null` 清空；空白或其他值拒绝 | 员工性质仅允许 `正式`、`临时`、`劳务` |
| `person_create.arguments.body.ext.empStatus` | string；`"试用"` | 必填；省略或空值拒绝 | `null`/空白拒绝 | 在职状态仅允许 `试用`、`在职`、`离职`、`长假`；不是登录账号启停状态 |
| `person_create.arguments.body.ext.leaveDate` | string、null；`"2026-11-20"` | 可选；省略不登记 | `null`、`""` 或纯空格写 null | 离职日期；非空值为 `YYYY-MM-DD` |
| `person_create.arguments.body.ext.signEntity` | string 或 null；`"织云服装有限公司"` | 可选；省略不登记 | `null` 清空；空白原样保存 | 合同签约主体；最多 100 字符 |
| `person_create.arguments.body.ext.contractYears` | integer 或 null；`3` | 可选；省略不登记 | `null` 清空；空白/小数/负数拒绝 | 合同年限；JSON 非负整数，最大 `2147483647` |
| `person_create.arguments.body.ext.contractStart` | string 或 null；`"2026-01-01"` | 可选；省略不登记 | `null`、`""` 或纯空格写 null | 合同开始日期；非空值为 `YYYY-MM-DD` |
| `person_create.arguments.body.ext.contractEnd` | string 或 null；`"2028-12-31"` | 可选；省略不登记 | `null`、`""` 或纯空格写 null | 合同结束日期；非空值为 `YYYY-MM-DD` |
| `person_create.arguments.body.ext.isDorm` | string；`"0"` | 可选；省略数据库默认 `"0"` | `null`/空白拒绝；仅 `"0"` 或 `"1"` | 是否住宿登记：0否、1是；不分配宿舍床位 |
| `person_create.arguments.body.password` | 不适用 | 不可传 | 不适用 | MCP 明确拒绝；创建后由有重置密码权限的人员在页面设置 |

### 修改 `person_update.arguments.changes`

更新是补丁：顶层字段或 `ext` 子字段**省略=保留现值**；`changes` 必须至少包含一个已知字段。`ext:{}` 是无变更空补丁；`changes:{}` 拒绝。字符串和枚举值约束与新增相同，以下列出更新时不同的空值/集合语义。

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| `person_update.arguments.userId` | string；`"1793602859000"` | 必填；省略拒绝 | `null`/空白/数字拒绝 | 从查询结果复制正十进制字符串；目标会重新按当前数据范围读取并锁定 |
| `person_update.arguments.changes` | object；`{"name":"新姓名"}` | 必填；至少一个受支持字段 | `null`/`{}` 拒绝 | 补丁对象；不接受 `jobNo`、`password` 或未知/敏感字段 |
| `person_update.arguments.changes.name` | string；`"新姓名"` | 可选；省略保留 | `null`/空白拒绝 | 修改人员姓名；操作前展示原值与目标值 |
| `person_update.arguments.changes.gender` | string 或 null；`"2"` | 可选；省略保留 | `null` 或 `""` 表示未登记；纯空格拒绝；其他非空值只允许 `"1"`/`"2"` | 修改性别代码 |
| `person_update.arguments.changes.email` | string 或 null；`"new@example.com"` | 可选；省略保留 | `null` 清空；空字符串按原值保存 | 修改工作邮箱；数据库列长 200 |
| `person_update.arguments.changes.phoneNumber` | string 或 null；`"13800001234"` | 可选；省略保留 | `null` 清空；空字符串按原值保存 | 修改登录手机号/联系电话；响应继续脱敏 |
| `person_update.arguments.changes.orgId` | string 或 null；`"1793602859001"` | 可选；省略保留 | `null` 或 `""` 清除组织；空格拒绝 | 组织 ID 从有权选项读取；后端复核数据范围 |
| `person_update.arguments.changes.status` | string；`"1"` | 可选；省略保留 | `null`/空白拒绝 | 登录账号状态 0正常、1停用；员工在职状态需改 `ext.empStatus` |
| `person_update.arguments.changes.remark` | string 或 null；`"备注"` | 可选；省略保留 | `null` 清空；空字符串/空格原样保存 | 修改备注 |
| `person_update.arguments.changes.postIds` | string 数组；`["20001"]` | 可选；省略保留所有岗位 | `null` 拒绝；`[]` 清空全部岗位；数组按最终集合替换；重复 ID 拒绝 | 岗位 ID 必须是十进制字符串；后端校验岗位权限与有效性 |
| `person_update.arguments.changes.roleIds` | string 数组；`["30001"]` | 可选；省略保留所有角色 | `null` 拒绝；`[]` 撤销全部角色；数组按最终集合替换；重复 ID 拒绝 | 角色变更另需 `system:user:edit`；不得借此授予超管角色 |
| `person_update.arguments.changes.businessDuties` | string 数组；`["SALES"]` | 可选；省略保留职责 | `null` 拒绝；`[]` 清除全部职责；重复或未知代码拒绝 | 整体替换业务职责集合；允许代码同新增字段表 |
| `person_update.arguments.changes.ext` | object；`{"dingTalkAccount":null}` | 可选；省略保留整个扩展档案 | `null` 拒绝；`{}` 不改变任何扩展字段 | 仅允许新增表所列 12 个字段；不支持敏感扩展字段 |
| `person_update.arguments.changes.ext.dingTalkAccount` | string 或 null；`"new-account"` | 可选；省略保留 | `null`/空串/纯空格清空；最多 128 字符 | 更新手工登记钉钉号，不触发身份绑定 |
| `person_update.arguments.changes.ext.hireDate` | string 或 null；`"2026-11-20"` | 可选；省略保留 | `null`/空串/纯空格清空；其他值须为有效 `YYYY-MM-DD` | 更新入职日期；API 当前允许清除，页面表单仍要求填写 |
| `person_update.arguments.changes.ext.groupName` | string 或 null；`"二组"` | 可选；省略保留 | `null` 清空；空白按原值保存 | 最多 50 字符；同次若提供有效 `groupId`，以组织树小组名称为准 |
| `person_update.arguments.changes.ext.groupId` | string 或 null；`"1793602859002"` | 可选；省略保留关联 | `null` 或 `""` 清除 ID 关联；空格/数字拒绝 | 正十进制字符串；清除 ID 不自动清除冗余 `groupName`，需要同时传 `groupName:null` |
| `person_update.arguments.changes.ext.empType` | string 或 null；`"劳务"` | 可选；省略保留 | `null` 清空；空白/未知值拒绝 | 仅允许 `正式`、`临时`、`劳务` |
| `person_update.arguments.changes.ext.empStatus` | string；`"在职"` | 可选；省略保留 | `null`/空白拒绝 | 仅允许 `试用`、`在职`、`离职`、`长假` |
| `person_update.arguments.changes.ext.leaveDate` | string 或 null；`"2026-11-20"` | 可选；省略保留 | `null`/空串/纯空格清除；非空值须为有效日期 | 离职日期，格式 `YYYY-MM-DD` |
| `person_update.arguments.changes.ext.signEntity` | string 或 null；`"织云服装有限公司"` | 可选；省略保留 | `null` 清空；空白原样保存；最多 100 字符 | 修改签约主体 |
| `person_update.arguments.changes.ext.contractYears` | integer 或 null；`3` | 可选；省略保留 | `null` 清空；空白/小数/负数拒绝 | 非负 JSON 整数，最大 `2147483647` |
| `person_update.arguments.changes.ext.contractStart` | string 或 null；`"2026-01-01"` | 可选；省略保留 | `null`/空串/纯空格清除；非空值须为有效日期 | 合同开始日期，格式 `YYYY-MM-DD` |
| `person_update.arguments.changes.ext.contractEnd` | string 或 null；`"2028-12-31"` | 可选；省略保留 | `null`/空串/纯空格清除；非空值须为有效日期 | 合同结束日期，格式 `YYYY-MM-DD` |
| `person_update.arguments.changes.ext.isDorm` | string；`"1"` | 可选；省略保留 | `null`/空白拒绝；仅 `"0"` 或 `"1"` | 是否住宿登记；不调整宿舍床位 |
| `person_update.arguments.changes.jobNo` | 不适用 | 不可传 | 不适用 | 工号不可经通用补丁修改 |
| `person_update.arguments.changes.password` | 不适用 | 不可传 | 不适用 | 密码只能由独立授权页面流程处理 |
| `person_delete.arguments.userId` | string；`"1793602859000"` | 必填；省略拒绝 | `null`/空白/数字拒绝 | 需先查详情；停用登录并软删除，保留历史关系；禁止内置 `admin` 和超管 |
| `person_restore.arguments.userId` | string；`"1793602859000"` | 必填；省略拒绝 | `null`/空白/数字拒绝 | 需先确认目标处于软删除状态；恢复前检查工号冲突和数据范围 |

### 可复制的写入 `arguments`

```json
{"body":{"jobNo":"000123","name":"示例员工","gender":"1","email":"employee@example.com","phoneNumber":"13800001234","orgId":"1793602859001","status":"0","remark":"一线员工","postIds":["20001"],"roleIds":[],"businessDuties":["SALES"],"ext":{"hireDate":"2026-11-20","empStatus":"试用","empType":"正式","groupId":"1793602859002","groupName":"一组","dingTalkAccount":"employee-example"}}}
```

修改为全新岗位/角色/职责集合，或传 `[]` 清空集合；省略其他字段表示保留：

```json
{"userId":"1793602859000","changes":{"postIds":[],"roleIds":["30001"],"businessDuties":["FOLLOWER"],"ext":{"dingTalkAccount":null}}}
```

以上示例为工具的实际 `arguments` 对象内容；外层 JSON-RPC/MCP 包装见调用指南。写入前先读详情、展示目标和字段影响并取得本次明确确认。
