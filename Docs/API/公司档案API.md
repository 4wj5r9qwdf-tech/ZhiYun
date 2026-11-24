# 公司档案 API / MCP 契约

> 功能键：`company`；数据表：`sys_company`；用户验收通过（2026-11-05）。公司档案供登录页和首页展示，不代表多租户隔离。

## 通用约定

- 使用当前员工 token 与 `clientid`；ID 以十进制字符串传入。统一响应为 `code/msg/data`；无权限返回 `403`，业务错误在 `msg` 中。
- 文件本体经 MinIO/OSS 上传接口取得 `oss_id`，本组接口只读写文件 ID，不接收文件二进制。
- 写入有业务审计，无幂等键。重复新增可能创建重复档案；超时后先查询。设为默认公司会在同一事务中清除旧默认标记。

## MCP 工具

| 工具 | 权限 | 机器契约 |
|---|---|---|
| `company_list` | `system:company:list` | 只读分页 |
| `company_get` | `system:company:query` | 只读详情 |
| `company_file_usage` | `system:company:list` | 只读文件引用查询，最多100个ID |
| `company_create` | `system:company:add` | 写；展示完整档案并确认 |
| `company_update` | `system:company:edit` | 写；展示变化并确认 |
| `company_set_default` | `system:company:edit` | 写；展示新旧默认项并确认 |
| `company_delete` | `system:company:remove` | 软删除；展示身份并确认 |

## REST 接口

| 方法 + 路径 | 说明 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/company/list` | 分页列表，pageNum/pageSize | `system:company:list` | 只读幂等；无副作用；无需审批 |
| `GET /system/company/{companyId}` | 公司详情及logo/背景URL | `system:company:query` | 只读幂等；无需审批 |
| `GET /system/company/file-usage/{ids}` | 查询最多100个OSS文件被哪些公司logo/背景引用 | `system:company:list` | 只读幂等；无需审批 |
| `POST /system/company` | 新增档案 | `system:company:add` | 非幂等；新增公司与审计；无需审批，智能体需员工确认 |
| `PUT /system/company` | 编辑档案 | `system:company:edit` | 更新档案与审计；需员工确认 |
| `PUT /system/company/default/{companyId}` | 设置唯一默认展示公司 | `system:company:edit` | 修改默认关系并记录前后值；需员工确认 |
| `DELETE /system/company/{companyId}` | 软删除档案 | `system:company:remove` | 非幂等；标记删除并记录审计；需员工确认 |

### 新增/修改请求体

```json
{
  "companyId":"10001",
  "companyName":"织云服装",
  "logoOssId":"20001",
  "loginBgOssId":"20002",
  "slogan":"专注服装制造",
  "loginBgMode":"A",
  "nameColor":"#FFFFFF",
  "theme":"industrial-blue",
  "status":"0",
  "isDefault":"1"
}
```

新增时省略 `companyId`，新增状态固定为启用 `0`；修改时 `status` 为 `0` 启用/`1` 停用。`loginBgMode` 为 `A` 左侧品牌区或 `B` 全屏背景。设置默认后其他公司将不再是默认项；`company_update` 中 `isDefault="0"` 不取消当前默认，默认公司改用单独的 `company_set_default` 工具切换。成功统一返回：`{"code":200,"msg":"操作成功","data":null}`。

### 错误与权限边界

- 无权操作返回 `403`；公司不存在/已删除返回业务失败；OSS 文件引用查询最多100个 ID。
- 删除为软删除；不自动删 OSS 文件。调用前必须检查 `company_file_usage`，如有文件被引用，不得自动清理文件。
- 设置默认公司影响全局登录页/首页展示；智能体必须明确展示旧默认公司与目标公司，并取得本次确认。

## MCP 输入字段矩阵（契约迁移批次 1）

以下为智能体可调用 MCP 的严格输入契约。对象入参由 MCP Map 适配器校验字段白名单、必填项、枚举、字符串 ID 和长度；REST 页面仍复用原有请求体，为兼容旧页面可接受数值 JSON ID。智能体一律传十进制字符串。除字段矩阵明确允许的 object、数组或 `null` 外，其余字段均拒绝数组、对象和数字 JSON 值。`company_list` 的可见范围为当前员工权限范围；空结果只表示当前权限和筛选下未查到。

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| `company_list.arguments.pageNum` | integer；`1` | 可选；省略或小于 1 时按 1 | `null` 按省略处理；空白/数组/对象拒绝 | 页码；最大值 1000000；只读分页 |
| `company_list.arguments.pageSize` | integer；`20` | 可选；省略或小于 1 时按 20 | `null` 按省略处理；空白/数组/对象拒绝 | 每页条数；大于 100 时限制为 100；只读分页 |
| `company_get.arguments.companyId` | string；`"10001"` | 必填；省略拒绝 | `null`、空白、数组、对象拒绝 | 从 `company_list` 的 `company_id` 复制；正十进制 bigint 字符串；按权限查询未删除档案 |
| `company_file_usage.arguments.ids` | string；`"20001,20002"` | 必填；省略拒绝 | `null`/空白/空集合拒绝；ID 间空项拒绝 | 从 OSS 上传结果复制文件 ID；逗号分隔，最多 100 个；去重后查仍被未删除公司引用的 Logo/背景 |
| `company_create.arguments.body.companyName` | string；`"织云示例公司"` | 必填；省略拒绝 | `null`/空白拒绝；数组/对象拒绝 | 公司名称；1–100 字符；写入公司档案并记录审计 |
| `company_create.arguments.body.logoOssId` | string 或 null；`"20001"` | 可选；省略写入 null | `null` 清除/不设置文件；空白拒绝；数组/对象拒绝 | 上传公开图片后取 OSS ID；正十进制字符串；不接收文件本体 |
| `company_create.arguments.body.loginBgOssId` | string 或 null；`"20002"` | 可选；省略写入 null | `null` 清除/不设置文件；空白拒绝；数组/对象拒绝 | 登录背景 OSS 文件 ID；正十进制字符串；不接收文件本体 |
| `company_create.arguments.body.slogan` | string 或 null；`"专注服装制造"` | 可选；省略写入 null | `null` 写入 null；空字符串/空格按原值保存；数组/对象拒绝 | 登录页宣传语；最多 200 字符；不会自动 trim |
| `company_create.arguments.body.loginBgMode` | string；`"A"` | 可选；省略默认 `A` | `null`/空白拒绝；仅 `A` 或 `B` | `A`=左侧品牌区，`B`=全屏背景；决定登录页背景布局 |
| `company_create.arguments.body.nameColor` | string 或 null；`"#FFFFFF"` | 可选；省略默认 `#FFFFFF` | `null` 写入 null；空字符串/空格原样保存；数组/对象拒绝 | 公司名颜色；最多 20 字符；建议 `#RRGGBB`，服务端不验证颜色格式；页面空值显示默认白色 |
| `company_create.arguments.body.theme` | string 或 null；`"industrial-blue"` | 可选；省略写入 null | `null` 写入 null；空字符串/空格原样保存；数组/对象拒绝 | 展示主题；最多 50 字符；主题名称由页面约定，MCP 当前不校验枚举 |
| `company_create.arguments.body.isDefault` | string；`"1"` | 可选；省略等同 `"0"` | `null`/空白拒绝；数组/对象拒绝 | `"0"`=不设默认，`"1"`=设为默认并在同一事务清除其他默认标记；触发前必须展示新旧默认项 |
| `company_create.arguments.body.status` | 不适用 | 不可传；新增固定为启用 `"0"` | 不适用 | 当前 MCP 新增工具不开放停用新档案；未知/不支持字段拒绝 |
| `company_update.arguments.body.companyId` | string；`"10001"` | 必填；省略拒绝 | `null`/空白拒绝；数字 JSON 值拒绝 | 从详情响应复制；正十进制字符串；标识被替换档案 |
| `company_update.arguments.body.companyName` | string；`"织云示例公司"` | 必填；省略拒绝 | `null`/空白拒绝；数组/对象拒绝 | 公司名称；1–100 字符；按完整档案替换提交 |
| `company_update.arguments.body.logoOssId` | string 或 null；`"20001"` | 必填字段；省略拒绝 | `null` 清除 Logo；空白拒绝；数组/对象拒绝 | 当前 Logo OSS ID；须按最终状态提交，文件引用变化写审计 |
| `company_update.arguments.body.loginBgOssId` | string 或 null；`"20002"` | 必填字段；省略拒绝 | `null` 清除背景；空白拒绝；数组/对象拒绝 | 当前背景 OSS ID；须按最终状态提交 |
| `company_update.arguments.body.slogan` | string 或 null；`"专注服装制造"` | 必填字段；省略拒绝 | `null` 写入 null；空白字符串原样保存；数组/对象拒绝 | 最终宣传语；最多 200 字符；不会自动 trim |
| `company_update.arguments.body.loginBgMode` | string；`"A"` | 必填；省略拒绝 | `null`/空白拒绝；仅 `A` 或 `B` | 最终背景模式：`A` 左侧品牌区、`B` 全屏背景 |
| `company_update.arguments.body.nameColor` | string 或 null；`"#FFFFFF"` | 必填字段；省略拒绝 | `null` 写入 null；空白原样保存；数组/对象拒绝 | 最终公司名称颜色；最多 20 字符，不验证颜色格式；页面空值显示默认白色 |
| `company_update.arguments.body.theme` | string 或 null；`"industrial-blue"` | 必填字段；省略拒绝 | `null` 写入 null；空白原样保存；数组/对象拒绝 | 最终展示主题；最多 50 字符；不校验主题枚举 |
| `company_update.arguments.body.status` | string；`"0"` | 必填；省略拒绝 | `null`/空白拒绝；仅 `"0"` 启用、`"1"` 停用 | 最终启停状态；停用公司不删除档案 |
| `company_update.arguments.body.isDefault` | string；`"1"` | 可选；省略不改变默认公司 | `null`/空白拒绝；仅 `"0"` 或 `"1"` | `"1"` 切换默认公司并清除旧默认；`"0"` 不取消当前默认。需要撤销当前默认时应改设另一家公司 |
| `company_set_default.arguments.companyId` | string；`"10001"` | 必填；省略拒绝 | `null`/空白/非字符串拒绝 | 从详情复制目标 ID；事务内设为唯一默认并更新审计；影响全局登录页/首页 |
| `company_delete.arguments.companyId` | string；`"10001"` | 必填；省略拒绝 | `null`/空白/非字符串拒绝 | 从详情复制；软删除档案并写审计，不删除 OSS 文件；操作前展示公司名称和当前默认状态 |

### MCP `arguments` 示例

新增最小档案；省略的可选字段按上表处理：

```json
{"body":{"companyName":"织云示例公司","logoOssId":"20001","loginBgOssId":null,"slogan":"专注服装制造","loginBgMode":"A","nameColor":"#FFFFFF","theme":"industrial-blue","isDefault":"0"}}
```

完整修改示例；先从查询结果替换真实 `companyId` 与 OSS 文件 ID：

```json
{"body":{"companyId":"10001","companyName":"织云示例公司","logoOssId":null,"loginBgOssId":"20002","slogan":"专注服装制造","loginBgMode":"B","nameColor":"#FFFFFF","theme":"industrial-blue","status":"0","isDefault":"0"}}
```

设置默认、删除和文件引用检查分别调用 `company_set_default`、`company_delete`、`company_file_usage`；上述写工具必须先展示影响并取得员工本次确认。MCP 查询返回的 `company_id`、`logo_oss_id`、`login_bg_oss_id` 均为十进制字符串；完整响应对象字段表留待只读返回契约批次补齐。
