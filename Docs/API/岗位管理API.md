# 岗位管理 API / MCP 契约

> 功能键：`post`；表：RuoYi `sys_post`，人员关联 `sys_user_post`，默认角色关联扩展表 `sys_post_role`。岗位管理用户验收通过（2026-11-05）；角色管理不是本次验收范围。

## MCP 工具

| 工具 | 权限 | 类型 |
|---|---|---|
| `post_list` | `system:post:list` | 只读分页、关键词搜索 |
| `post_get` | `system:post:query` | 只读详情 |
| `post_options` | `system:post:query` | 只读岗位选择项 |
| `post_create` | `system:post:add` | 写；展示字段并确认 |
| `post_update` | `system:post:edit` | 写；展示变更并确认 |
| `post_set_status` | `system:post:edit` | 写；确认启停 |
| `post_delete` | `system:post:remove` | 批量写；确认完整岗位清单 |
| `post_org_tree` | `system:post:list` | 只读岗位组织筛选树 |

## REST 接口

| 方法 + 路径 | 用途 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/post/list` | 岗位分页；keyword 可搜名称/编码 | `system:post:list` | 只读幂等；无副作用；无需审批 |
| `GET /system/post/{postId}` | 岗位详情 | `system:post:query` | 只读幂等；无需审批 |
| `GET /system/post/optionselect` | 按 orgId 或 postIds 查询岗位选项 | `system:post:query` | 只读幂等；无需审批 |
| `GET /system/post/orgTree` | 岗位筛选用组织树 | `system:post:list` | 只读幂等；无需审批 |
| `POST /system/post` | 新增岗位 | `system:post:add` | 非幂等；岗位表写入及审计；需确认；短时间重复请求拦截 |
| `PUT /system/post` | 修改岗位档案 | `system:post:edit` | 更新档案与审计；需确认；岗位名/编码需唯一 |
| `PUT /system/post/{postId}/status` | 启用或停用岗位 | `system:post:edit` | 状态幂等；已有员工分配时禁止停用；需确认 |
| `DELETE /system/post/{postIds}` | 批量删除岗位（逗号分隔ID） | `system:post:remove` | 非幂等；写删除及审计；需确认每条岗位 |

### 新增/修改请求体

```json
{
  "postId":"10001",
  "orgId":"10000",
  "belongOrgId":"10000",
  "postCode":"SEWING_SUPERVISOR",
  "postName":"缝制主管",
  "postCategory":"生产管理",
  "postSort":10,
  "status":"0",
  "remark":""
}
```

新增时省略 `postId`。`orgId`、`postCode`、`postName`、`postSort` 必填；岗位编码和名称必须唯一。当前岗位按全厂通用范围管理，组织树仅用于岗位筛选；已分配员工的岗位不可停用。

成功响应：`{"code":200,"msg":"操作成功","data":null}`。写请求由当前员工权限执行并记录岗位编码、名称、归属、状态等变化；不自动改员工主岗或角色。
