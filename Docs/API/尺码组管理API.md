# 尺码组管理 API / MCP 契约

> 用户验收：2026-11-04 通过。REST 已实现；MCP 工具已在 `SizeMcpTools` 注册，实际发现与调用情况见《测试与验收清单》。
> 接口依据：`SizeDataController`、`SysConfigController` 与尺码组管理页面实现。

## 通用约定

- 使用当前员工本人 Bearer Token 和项目要求的 `clientid`；按权限检查执行，禁止超管代办。
- 统一响应 `{"code":200,"msg":"操作成功","data":...}`；业务错误返回中文 `msg`。
- 写操作由智能体发起时，先展示变更内容并取得员工确认。MCP 入口显式检查当前员工权限，委托已有控制器完成校验、事务和审计。
- 路由前缀 `/system/size-data/size-group`；尺码组 ID 使用十进制字符串传递。

## 工具映射（已注册）

| MCP 工具 | REST | 权限 |
|---|---|---|
| `size_group_list` | `GET /system/size-data/size-group/list` | `system:size-group:list` |
| `size_group_options` | `GET /system/size-data/size-group/options` | 登录 |
| `size_group_create` | `POST /system/size-data/size-group` | `system:size-group:add` |
| `size_group_update` | `PUT /system/size-data/size-group/{id}` | `system:size-group:edit` |
| `size_group_set_status` | `PUT /system/size-data/size-group/{id}/status` | `system:size-group:edit` |
| `size_group_delete` | `DELETE /system/size-data/size-group/{id}` | `system:size-group:delete` |
| `size_group_get_code_rule` | `GET /system/config/configKey/system.code.sizeGroup` | 登录，固定键 |
| `size_group_set_code_rule` | `POST /system/config/size-group-code-rule` | `system:config:edit` |

## 查询

`GET /system/size-data/size-group/list` 返回尺码组、标签和有序 `items`；`GET .../options` 为登录后的业务选择接口，含停用组供历史回显。

## 新增与修改

`POST /system/size-data/size-group` 与 `PUT /system/size-data/size-group/{id}` 使用完整字段：

```json
{"code":"","name":"字母尺码组","name_en":"Alpha","sort_order":0,"remark":"","tagIds":[],"sizeIds":["1001","1002"]}
```

名称必填，`sizeIds` 至少一个且不可重复；同组不允许包含同名尺码。`code` 留空时按参数 `system.code.sizeGroup` 生成当前最小可用号（默认 `SG` + 4 位）；关闭自动生成时必须手填。编号可编辑且不可重复。`sizeIds` 数组顺序就是组内顺序；停用尺码不能新加入，原引用可以保留。

创建非幂等；修改会替换组内成员、顺序及标签关系。失败时不盲目重试创建请求。

## 启停与删除

- `PUT /{id}/status` 请求 `{"status":"0"}` 启用，`{"status":"1"}` 停用；停用保留引用。
- `DELETE /{id}` 删除尺码组，并在同一事务清理组内明细和标签关系。删除不可恢复，操作前展示名称、编号并确认。

## 编码规则

查询固定参数键 `GET /system/config/configKey/system.code.sizeGroup`；写入 `POST /system/config/size-group-code-rule`：

```json
{"enabled":true,"prefix":"SG","digits":4}
```

`enabled` 必须为布尔值，前缀非空且最多 30 字符，`digits` 为 1–10 整数。规则只影响新建组。规则记录缺失时，保存会创建默认参数记录。修改前展示旧值和新值并取得确认。

## MCP 输入字段矩阵（契约迁移批次 1）

size_group_create.body、size_group_update.body 和 size_group_set_code_rule.body 是自由 Map；运行时 schema 不校验内部键。下表依据 SizeDataController 的保存和 SysConfigController 的规则校验。

| 字段路径 | JSON 类型 / 示例 | 必填与省略结果 | null / 空白 / 空集合结果 | 用途、来源、约束与副作用 |
|---|---|---|---|---|
| size_group_update.id、size_group_set_status.id、size_group_delete.id | string 正十进制 ID；"1001" | 必填；省略拒绝 | null/空白/非数字拒绝；数组/对象不适用 | 从 size_group_list 复制；删除会清理该组成员和标签关系 |
| size_group_create.body.code | string；"SG0001" | 可选；省略/空白按自动编码规则生成，关闭规则则拒绝 | null/空白与省略相同；最多40字符且不得重复 | 创建后可按完整更新契约修改编码；建议值不预留 |
| size_group_update.body.code | string；"SG0001" | 必填；省略或空白拒绝 | null/空白拒绝；最多40字符且不得重复 | 完整提交最终编码 |
| body.name | string；"字母尺码组" | 新增/修改必填；省略、null、空白拒绝 | trim 后不能为空；最多100字符 | 尺码组显示名称 |
| body.name_en | string；"Alpha" | 可选；省略/null/空白写为空字符串 | 空白清空字段 | 英文名称，最多200字符 |
| body.sort_order | integer；10 | 可选；省略/null/空白按0处理 | 非整数拒绝；无额外数值边界 | 显示排序 |
| body.remark | string；"成人字母尺码" | 可选；省略/null/空白写为空字符串 | 空白清空备注 | 最长2000字符 |
| body.tagIds | array<string>；["2001"] | 新增可省略；修改省略会被解释为空集合并清空全部标签，故修改须显式提交最终集合 | null/省略/[]均清除全部组标签；非数组代码会按空集合处理 | 每个标签须属于 size_group 命名空间且有效或已有关联；使用完整最终集合 |
| body.sizeIds | array<string>；["1001","1002"] | 新增/修改必填，至少一个；重复尺码拒绝 | null/省略/[]拒绝；空白 ID 无效 | 按此数组顺序写入组内尺码顺序；尺码须存在，不可新加入停用尺码，同组不可出现同名尺码 |
| size_group_set_status.status | string 枚举；"1" | 必填；只能 "0" 或 "1" | null/空白/其他值拒绝 | "0"启用、"1"停用；状态变更不删除成员 |
| size_group_set_code_rule.body.enabled | boolean；true | 必填 | null/省略拒绝 | 是否启用自动编码 |
| size_group_set_code_rule.body.prefix | string；"SG" | 必填，非空且最多30字符 | null/空白拒绝 | 新建尺码组的编码前缀 |
| size_group_set_code_rule.body.digits | integer；4 | 必填，1–10整数 | null/空白/小数/越界拒绝 | 序号补零位数，只影响后续新建 |

### MCP arguments 示例

新建组时顺序由 sizeIds 数组决定；修改时必须先读取当前成员和标签，再提交完整最终成员、标签和字段：

~~~json
{"name":"size_group_create","arguments":{"body":{"code":"","name":"字母尺码组","name_en":"Alpha","sort_order":0,"remark":"","tagIds":[],"sizeIds":["1001","1002"]}}}
~~~

~~~json
{"name":"size_group_update","arguments":{"id":"3001","body":{"code":"SG0001","name":"字母尺码组","name_en":"Alpha","sort_order":0,"remark":"","tagIds":["2001"],"sizeIds":["1001","1002"]}}}
~~~

## 安全与状态

尺码组功能已由用户验收；REST 与 MCP 工具已实现。服务构建、重启、工具发现和实际调用状态以《测试与验收清单》记录为准。
