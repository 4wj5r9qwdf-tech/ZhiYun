# 组织架构 API / MCP 契约

> 功能键：`org`；主表：`sys_org`，业务管辖关联表：`sys_org_manage`。当前基础组织架构用户验收通过（2026-11-05）；节点种类、层级扩展等后续范围另行确认。

## 通用约定

- 请求以当前员工身份执行，并遵守角色权限和组织数据范围。ID 在 MCP/URL 中传十进制字符串；统一响应 `code/msg/data`。
- 新增/编辑/管辖关系写入有审计及重复提交保护。写操作先查询并向员工展示节点路径、类别、建筑及影响，再取得本次确认。
- 不能删除默认组织、存在子级/人员/岗位或被管辖引用的组织；停用有下级或人员时拒绝。

## MCP 工具

| 工具 | 权限 | 类型 |
|---|---|---|
| `org_list` | `system:org:list` | 只读条件查询 |
| `org_get` | `system:org:query` | 只读详情，并应用数据范围 |
| `org_options` | `system:org:query` | 只读选择项 |
| `org_create` | `system:org:add` | 写；员工确认后新增 |
| `org_update` | `system:org:edit` | 写；员工确认后修改 |
| `org_delete` | `system:org:remove` | 写；员工确认后删除合格叶节点 |
| `org_manages_get` | `system:org:query` | 只读查询管辖关系 |
| `org_manages_set` | `system:org:edit` | 全量替换关系；空列表会清空，必须确认 |
| `org_manage_counts` | `system:org:query` | 只读管辖统计 |

## REST 接口

| 方法 + 路径 | 用途 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/org/list` | 按 SysOrgBo 条件查询组织树/列表 | `system:org:list` | 只读幂等；受当前数据范围影响；无需审批 |
| `GET /system/org/list/exclude/{orgId}` | 查询排除指定节点及其后代的可选组织 | `system:org:list` | 只读幂等；无需审批 |
| `GET /system/org/{orgId}` | 组织详情 | `system:org:query` | 只读幂等；数据范围校验；无需审批 |
| `GET /system/org/optionselect?orgIds=...` | 组织选择项 | `system:org:query` | 只读幂等；无需审批 |
| `GET /system/org/{orgId}/manages` | 查询该组织管辖的组织ID | `system:org:query` | 只读幂等；无需审批 |
| `GET /system/org/manage-counts` | 查询组织管辖数量/名称 | `system:org:query` | 只读幂等；无需审批 |
| `POST /system/org` | 新增组织节点 | `system:org:add` | 非幂等；写组织表与审计；需确认；重复短时间提交会被拦截 |
| `PUT /system/org` | 修改组织节点/状态 | `system:org:edit` | 更新组织与审计；需确认；父级、唯一名、状态受校验 |
| `DELETE /system/org/{orgId}` | 删除组织节点 | `system:org:remove` | 非幂等；只允许无引用节点；需确认 |
| `PUT /system/org/{orgId}/manages` | 全量替换被管辖组织ID列表 | `system:org:edit` | 写管辖关系和审计；空数组清空；需明确确认 |

### 新增/修改请求体

```json
{
  "orgId":"10001",
  "parentId":"10000",
  "orgName":"一车间",
  "orgCategory":"车间",
  "buildingId":"30001",
  "location":"一号厂房/1层",
  "orderNum":10,
  "leader":"20001",
  "phone":"13800000000",
  "email":"leader@example.com",
  "status":"0"
}
```

新增时省略 `orgId`。必填为 `orgName`、`orderNum`；`parentId` 按目标层级提供。`orgCategory` 为业务类别，建筑是独立物理档案；业务管辖关系与树层级相互独立。

成功响应示例：`{"code":200,"msg":"操作成功","data":null}`。管辖关系请求体是最终 ID 数组，例如 `["10002","10003"]`；空数组代表清空全部。

### 错误与边界

组织名称重复、父级无效、不能自为父级、禁用节点仍有子级/人员、默认组织/非叶节点删除、人员/岗位引用、数据范围越权均由后端拒绝。不得为了完成用户请求自动改动人员归属或其他组织关系。
