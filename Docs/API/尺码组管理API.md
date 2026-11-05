# 尺码组管理 API / MCP 契约

> 用户验收：2026-11-04 通过。REST 已实现；MCP 运行时尚未注册，以下工具名为契约映射。
> 接口依据：`SizeDataController`、`SysConfigController` 与尺码组管理页面实现。

## 通用约定

- 使用当前员工本人 Bearer Token 和项目要求的 `clientid`；按权限检查执行，禁止超管代办。
- 统一响应 `{"code":200,"msg":"操作成功","data":...}`；业务错误返回中文 `msg`。
- 写操作由智能体发起时，先展示变更内容并取得员工确认。MCP 未注册前不得从 Skill 直接调用 REST 或数据库。
- 路由前缀 `/system/size-data/size-group`；尺码组 ID 使用十进制字符串传递。

## 工具映射（待注册）

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

## 安全与状态

尺码组功能已由用户验收；REST 已实现，MCP 工具尚未注册。因此 Skill 当前是契约文档，不可直接执行。
