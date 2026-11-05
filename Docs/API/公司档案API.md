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

新增时省略 `companyId`；`loginBgMode` 为 `A` 左侧品牌区或 `B` 全屏背景；`status` 为 `0` 启用/`1` 停用。设置默认后其他公司将不再是默认项。成功统一返回：`{"code":200,"msg":"操作成功","data":null}`。

### 错误与权限边界

- 无权操作返回 `403`；公司不存在/已删除返回业务失败；OSS 文件引用查询最多100个 ID。
- 删除为软删除；不自动删 OSS 文件。调用前必须检查 `company_file_usage`，如有文件被引用，不得自动清理文件。
- 设置默认公司影响全局登录页/首页展示；智能体必须明确展示旧默认公司与目标公司，并取得本次确认。
