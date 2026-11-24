# 尺码管理 API / MCP 契约

> 功能英文名：`size`
> 对应数据：`sys_size` 与 `sys_tag_relation(module_key='size')`
> 用户验收：2026-11-04 通过
> REST 状态：已实现；MCP：已在 `SizeMcpTools` 注册，实际发现与调用情况见《测试与验收清单》。
> 契约依据：后端源码与页面调用；MCP 适配直接复用 `SizeDataController` 和固定参数键对应的 `SysConfigController`。
> 尺码组管理未验收，不属于本文档范围。

## 1. 通用约定

- 以当前员工自己的 Bearer Token 调用；不可使用超管代办。请求另带项目客户端要求的 `clientid` header。
- 所有业务操作受对应 Sa-Token 权限校验；无权通常返回 `403`。业务校验失败返回 `code=500` 和中文 `msg`。
- 统一响应：`{"code":200,"msg":"操作成功","data":...}`。错误示例：`{"code":500,"msg":"部分尺码已不存在，请刷新列表后重试","data":null}`。
- ID 在接口路径及 JSON 数组中使用十进制字符串，避免 bigint 精度丢失。
- 当前尺码接口没有 `@RepeatSubmit` 防重注解；写请求超时后先核对结果再决定是否重试，尤其不得盲目重试新增。
- 接口由 `SizeDataController` 提供：`/system/size-data/{kind}`，本文只使用 `kind=size`。
- **机器审批要求**：涉及新增、修改、启停、删除、标签替换或规则修改时，智能体必须先展示待执行内容并等员工确认；查询无需确认。MCP 工具入口会显式复核当前员工权限。

## 2. MCP 工具映射（已注册）

| MCP 工具名 | REST 映射 | 权限 | 接入状态 |
|---|---|---|---|
| `size_list` | `GET /system/size-data/size/list` | `system:size:list` | 已注册 |
| `size_options` | `GET /system/size-data/size/options` | 已登录 | 已注册 |
| `size_create` | `POST /system/size-data/size` | `system:size:add` | 已注册 |
| `size_update` | `PUT /system/size-data/size/{id}` | `system:size:edit` | 已注册 |
| `size_set_status` | `PUT /system/size-data/size/{id}/status` | `system:size:edit` | 已注册 |
| `size_delete` | `DELETE /system/size-data/size/{id}` | `system:size:delete` | 已注册 |
| `size_batch_set_tags` | `POST /system/size-data/size/batch-tags` | `system:size:edit` | 已注册 |
| `size_get_code_rule` | `GET /system/config/configKey/system.code.size` | 已登录（固定参数键） | 已注册 |
| `size_set_code_rule` | `POST /system/config/size-code-rule` | `system:config:edit` | 已注册 |

## 3. 查询尺码列表

### `GET /system/size-data/size/list`

返回排序后的尺码档案，含关联类型标签。无分页参数。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `kind` | path，固定 `size` | 是 | 资源类型，当前功能固定为尺码 |

**成功返回示例**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 1001,
      "code": "SZ001",
      "name": "S",
      "name_en": "Small",
      "sort_order": 0,
      "status": "0",
      "remark": "",
      "tagIds": ["2001"],
      "tagOptions": [{"value": "2001", "label": "字母标码", "disabled": false}]
    }
  ]
}
```

**机器契约**：幂等（只读）；副作用无；前置条件为登录且有 `system:size:list`；审批不需要。

## 4. 查询尺码选项

### `GET /system/size-data/size/options`

返回全部尺码及状态，供业务选择和已有引用回显；调用方应禁止新选 `status="1"` 的尺码。

**机器契约**：幂等（只读）；副作用无；前置条件为登录；审批不需要。无单独权限码，禁止把该接口作为匿名公开查询。

## 5. 新增尺码

### `POST /system/size-data/size`

**请求体**

```json
{
  "code": "",
  "name": "S",
  "name_en": "Small",
  "sort_order": 0,
  "remark": "",
  "tagIds": ["2001"]
}
```

`code` 留空时按当前编码规则分配最小可用编码；自动编码关闭时必须填写编码。`tagIds` 可省略或为空数组。

**机器契约**：幂等性不保证（重试可能新增第二条记录）；副作用为新增尺码及标签关系；前置条件为 `system:size:add`、有效名称、编码唯一、标签属于尺码命名空间且可用；审批不需要，但智能体写入前必须取得员工确认。

**成功返回示例**（当前 ID 在 JavaScript 安全整数范围内时序列化为数字，超出范围时为字符串）

```json
{"code":200,"msg":"操作成功","data":1001}
```

## 6. 修改尺码

### `PUT /system/size-data/size/{id}`

**请求体**

```json
{
  "code": "SZ001",
  "name": "S",
  "name_en": "Small",
  "sort_order": 10,
  "remark": "成人尺码",
  "tagIds": ["2001"]
}
```

需要提交完整字段；`tagIds` 是该尺码最终标签集合，传空数组会清除关联标签。标签不可用或非尺码命名空间时拒绝保存。

**机器契约**：相同请求的档案状态幂等，但会重复触发更新时间/审计处理；副作用为更新档案和替换标签关系；前置条件为记录存在、`system:size:edit`、编码未被其他尺码占用、标签有效；审批不需要，调用前需员工确认。

成功响应：`{"code":200,"msg":"操作成功","data":1001}`（超出 JavaScript 安全整数范围的 ID 为字符串）。

## 7. 启用或停用尺码

### `PUT /system/size-data/size/{id}/status`

**请求体**

```json
{"status": "1"}
```

`0` 为启用，`1` 为停用。停用不删除历史引用；已被尺码组引用的停用尺码继续保留在原组。

**机器契约**：状态结果幂等；副作用为修改尺码状态并写审计日志；前置条件为记录存在且有 `system:size:edit`；审批不需要，调用前需员工确认。

成功响应：`{"code":200,"msg":"操作成功","data":null}`。

## 8. 删除尺码

### `DELETE /system/size-data/size/{id}`

**机器契约**：非幂等（首次成功后重复请求返回记录不存在）；副作用为删除尺码及其类型标签关联，释放业务编码；前置条件为记录存在、`system:size:delete` 且没有任何尺码组引用；审批不需要，但必须在操作卡中展示尺码名称/编码并由员工确认。

被尺码组引用时返回业务错误，例如：`该尺码仍被1个尺码组引用，请先从尺码组中移除`。

成功响应：`{"code":200,"msg":"操作成功","data":null}`。

## 9. 批量替换尺码类型标签

### `POST /system/size-data/size/batch-tags`

最多处理 500 条尺码。`tagIds` 是每条尺码最终标签集合；传空数组表示清除所选尺码全部类型标签。

**请求体示例**

```json
{
  "ids": ["1001", "1002"],
  "tagIds": ["2001"]
}
```

**清除标签示例**

```json
{
  "ids": ["1001", "1002"],
  "tagIds": []
}
```

**机器契约**：相同目标集合下业务状态幂等，但每次调用都会重新写关系及审计；副作用为先清除所选尺码旧标签，再统一写入目标标签；前置条件为 1–500 个不重复且存在的尺码 ID、不重复的可用尺码标签 ID、`system:size:edit`；审批不需要，调用前必须展示影响条数和目标标签并等待员工确认。

成功响应：`{"code":200,"msg":"操作成功","data":null}`。

## 10. 更新自动编码规则

### `GET /system/config/configKey/system.code.size`

查询当前尺码编码规则。工具映射必须将参数键固定为 `system.code.size`，禁止开放成可读取任意系统参数的通用工具。

**成功返回示例**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "{\"enabled\":true,\"prefix\":\"SZ\",\"digits\":3}"
}
```

**机器契约**：幂等（只读）；副作用无；前置条件为登录，且工具只可读取固定参数键 `system.code.size`；审批不需要。

### `POST /system/config/size-code-rule`

此端点属于系统参数维护权限，不是普通尺码操作。只在员工明确要求修改编码规则且拥有 `system:config:edit` 时调用。

**请求体**

```json
{
  "enabled": true,
  "prefix": "SZ",
  "digits": 3
}
```

**机器契约**：相同规则值幂等；副作用为更新 `system.code.size` 配置并清除参数缓存；前置条件为参数项存在、开关为布尔值、前缀非空且不超过 30 字符、数字位数为 1–10 的整数、具有 `system:config:edit`；审批不需要，但写入前必须让员工确认新规则。

## MCP 输入字段矩阵（契约迁移批次 1）

size_create.body、size_update.body 和 size_batch_set_tags.body 是 Map<String,Object>，运行时 inputSchema 不会逐字段校验对象内部结构。下表依据 SizeDataController 的保存、批量标签及规则校验；对象只使用列出的字段。

| 字段路径 | JSON 类型 / 示例 | 必填与省略结果 | null / 空白 / 空集合结果 | 用途、来源、约束与副作用 |
|---|---|---|---|---|
| size_update.id、size_set_status.id、size_delete.id | string 正十进制 ID；"1001" | 必填；省略拒绝 | null/空白/非数字拒绝；数组/对象不适用 | 从 size_list 或 size_options 原样复制；size_delete 仅无尺码组引用可执行 |
| size_create.body.code | string；"SZ001" | 可选；省略或空白按编码规则生成，规则关闭时拒绝 | null/空白与省略相同；最多40字符，重复拒绝 | 可编辑的建议编号；编码查询不预留号码 |
| size_update.body.code | string；"SZ001" | 必填；省略或空白拒绝 | null/空白拒绝；最多40字符，重复拒绝 | 修改时完整提交最终编码；不因字段名相同而复用创建时的留空自动生成语义 |
| body.name | string；"S" | 新增/修改必填；省略、null、空白拒绝 | trim 后不能为空；最多100字符 | 尺码中文/主显示名称 |
| body.name_en | string；"Small" | 可选；省略按空字符串写入 | null/空白写为空字符串 | 英文名称，最多200字符 |
| body.sort_order | integer；10 | 可选；省略/null/空白回退为0；需可解析为整数 | 空白按0；小数格式或非数字拒绝 | 显示排序；当前控制器不限制正负范围 |
| body.remark | string；"成人尺码" | 可选；省略/null/空白写为空字符串 | 空白清空备注 | 最长2000字符 |
| body.tagIds | array<string>；["2001"] | 新增可省略；修改也可省略但会解释为空集合并清除全部标签，因此修改必须显式提交完整最终集合 | null/非数组按空集合处理并清除；[]明确清除全部标签 | 每个标签须属于 size 命名空间且可用或已在目标记录上保留；不得用其他功能标签 |
| size_set_status.status | string 枚举；"1" | 必填；只能是 "0" 或 "1" | null、空白、其他文本拒绝 | "0"启用、"1"停用；停用不移除尺码组历史关联 |
| size_batch_set_tags.body.ids | array<string>；["1001","1002"] | 必填，1–500 个且不得重复 | null/缺失/[]拒绝；空白 ID 解析失败 | 从尺码列表复制；全部尺码须存在，否则整批不变 |
| size_batch_set_tags.body.tagIds | array<string>；["2001"] | 可选；省略按空数组处理并清除所选尺码全部标签 | null/省略/[]均表示清除全部类型标签；非数组拒绝 | 这是给每个所选尺码设置的同一最终集合；全部校验通过后在一个事务内替换并审计 |
| size_set_code_rule.body.enabled | boolean；true | 必填；省略/null拒绝 | 空白不适用 | 是否生成建议编码；规则只影响以后新建 |
| size_set_code_rule.body.prefix | string；"SZ" | 必填；非空且最多30字符 | null/空白拒绝 | 自动编码前缀；规则格式按系统参数校验 |
| size_set_code_rule.body.digits | integer；3 | 必填，1–10整数 | null/空白/小数/越界拒绝 | 序号补零位数 |

### MCP arguments 示例

更新尺码时，请从最新详情回填完整字段，并将当前标签以外的移除项从最终 tagIds 中去掉。空数组是有意清除标签，须单独展示并确认：

~~~json
{"name":"size_update","arguments":{"id":"1001","body":{"code":"SZ001","name":"S","name_en":"Small","sort_order":10,"remark":"成人尺码","tagIds":["2001"]}}}
~~~

~~~json
{"name":"size_batch_set_tags","arguments":{"body":{"ids":["1001","1002"],"tagIds":[]}}}
~~~

## 11. 错误及安全边界

| 返回码/消息 | 条件 |
|---|---|
| `401` | Token 无效或会话过期 |
| `403` | 当前员工缺少对应权限 |
| `500` + 业务消息 | 名称/编码无效或重复、标签不可用、尺码不存在或仍被尺码组引用等 |

所有变更以当前员工本人身份执行并留审计。MCP 工具仅委托已存在控制器，不提供通用 HTTP 或 SQL 能力；调用仍须遵守对应权限及员工确认要求。
