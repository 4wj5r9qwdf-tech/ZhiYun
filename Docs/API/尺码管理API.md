# 尺码管理 API / MCP 契约

> 功能英文名：`size`
> 对应数据：`sys_size` 与 `sys_tag_relation(module_key='size')`
> 用户验收：2026-11-04 通过
> REST 状态：已实现；MCP 运行时：未注册，以下 MCP 工具名为契约映射，不可直接调用。
> 契约依据：核对后端源码与页面调用；本次文档整理未另行执行接口烟测。
> 尺码组管理未验收，不属于本文档范围。

## 1. 通用约定

- 以当前员工自己的 Bearer Token 调用；不可使用超管代办。请求另带项目客户端要求的 `clientid` header。
- 所有业务操作受对应 Sa-Token 权限校验；无权通常返回 `403`。业务校验失败返回 `code=500` 和中文 `msg`。
- 统一响应：`{"code":200,"msg":"操作成功","data":...}`。错误示例：`{"code":500,"msg":"部分尺码已不存在，请刷新列表后重试","data":null}`。
- ID 在接口路径及 JSON 数组中使用十进制字符串，避免 bigint 精度丢失。
- 当前尺码接口没有 `@RepeatSubmit` 防重注解；写请求超时后先核对结果再决定是否重试，尤其不得盲目重试新增。
- 接口由 `SizeDataController` 提供：`/system/size-data/{kind}`，本文只使用 `kind=size`。
- **机器审批要求**：涉及新增、修改、启停、删除、标签替换或规则修改时，智能体必须先展示待执行内容并等员工确认；查询无需确认。系统当前未提供 MCP 工具，以下契约不是可运行的 agent tool。

## 2. MCP 工具映射（待注册）

| MCP 工具名 | REST 映射 | 权限 | 接入状态 |
|---|---|---|---|
| `size_list` | `GET /system/size-data/size/list` | `system:size:list` | 未注册 |
| `size_options` | `GET /system/size-data/size/options` | 已登录 | 未注册 |
| `size_create` | `POST /system/size-data/size` | `system:size:add` | 未注册 |
| `size_update` | `PUT /system/size-data/size/{id}` | `system:size:edit` | 未注册 |
| `size_set_status` | `PUT /system/size-data/size/{id}/status` | `system:size:edit` | 未注册 |
| `size_delete` | `DELETE /system/size-data/size/{id}` | `system:size:delete` | 未注册 |
| `size_batch_set_tags` | `POST /system/size-data/size/batch-tags` | `system:size:edit` | 未注册 |
| `size_get_code_rule` | `GET /system/config/configKey/system.code.size` | 已登录（固定参数键） | 未注册 |
| `size_set_code_rule` | `POST /system/config/size-code-rule` | `system:config:edit` | 未注册 |

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

## 11. 错误及安全边界

| 返回码/消息 | 条件 |
|---|---|
| `401` | Token 无效或会话过期 |
| `403` | 当前员工缺少对应权限 |
| `500` + 业务消息 | 名称/编码无效或重复、标签不可用、尺码不存在或仍被尺码组引用等 |

所有变更以当前员工本人身份执行并留审计。Skill 和 MCP 工具未注册前，智能体不得绕过工具目录直接调用这些 REST 路由。
