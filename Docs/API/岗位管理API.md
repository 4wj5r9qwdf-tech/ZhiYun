# 岗位管理 API / MCP 契约

> 功能键：`post`；表：RuoYi `sys_post`，人员关联 `sys_user_post`，默认角色关联扩展表 `sys_post_role`。岗位管理用户验收通过（2026-11-05）；角色管理不是本次验收范围。
> 契约迁移：2026-11-21 已将岗位 MCP Map/DTO 输入转换为适配器，ID 先按十进制字符串校验；新增本文件字段矩阵与 MCP 参数示例。运行时发现和真实读写待验收。

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

## MCP 输入字段矩阵（契约迁移批次 1）

岗位与组织筛选对象在 inputSchema 中是自由 object。适配器对白名单、ID 字符串、必填字段和 JSON 整数进行检查，再转入原 `SysPostBo`/`SysOrgBo`；Map 内部字段不会由 inputSchema 自动约束。

| 字段路径 | JSON 类型 / 示例 | 必填与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、约束与副作用 |
|---|---|---|---|---|
| `post_list.query`、`post_org_tree.query` | object；`{"orgId":"10001","status":"0"}` | query 可省略或为空对象 | null等同省略；空对象不过滤；未知键拒绝 | 在当前员工权限下筛选岗位/组织；对象 ID 必须是字符串 |
| `post_list.keyword` | string；`"裁剪"` | 可选；省略不按关键词筛选 | null/空白不过滤 | 按岗位名称或编码搜索 |
| `post_list.pageNum` | integer；`1` | 可选；省略或小于1回退1 | null回退；非整数 schema 不接受 | 查询页码 |
| `post_list.pageSize` | integer；`20` | 可选；省略或小于1回退20，最大100 | null回退；非整数 schema 不接受 | 查询条数，服务端上限100 |
| `post_get.postId` | string 正十进制 ID；`"20001"` | 必填 | null/空白/数字/非正 ID 拒绝 | 从岗位列表复制 |
| `post_options.postIds` | string；`"20001,20002"` | 可选；省略不按 ID 限制 | null/空白解析为空 ID 集合；非法成员拒绝 | 多个岗位 ID 以英文逗号分隔，全部从岗位查询复制 |
| `post_options.orgId` | string 正十进制 ID；`"10001"` | 可选；省略不按组织筛选 | null/空白不过滤；数字/非法 ID 拒绝 | 从组织查询复制 |
| `post_create.body.orgId` | string 正十进制 ID；`"10001"` | 必填 | null/空白/数字/非法 ID 拒绝 | 岗位所属组织，必须是有权组织 |
| `post_create.body.postCode` | string；`"CUTTER"` | 必填；非空，最多64字符 | null/空白拒绝 | 岗位编码须唯一 |
| `post_create.body.postName` | string；`"裁剪员"` | 必填；非空，最多50字符 | null/空白拒绝 | 岗位名称须唯一 |
| `post_create.body.postSort` | integer；`10` | 必填 | null/小数/数字字符串拒绝 | 显示顺序 |
| `post_create.body.belongOrgId` | string 正十进制 ID或null；`"10001"` | 可选 | null表示未指定；空白/数字拒绝 | 组织树归属字段，与单部门 `orgId` 是不同字段 |
| `post_create.body.postCategory` | string；`"生产"` | 可选 | null可不设置；数字/对象拒绝 | 岗位类别编码，最多100字符 |
| `post_create.body.status` | string；`"0"` | 可选，框架默认正常 | null拒绝；仅字符串 `"0"`/`"1"` | 0正常、1停用；有员工分配时不能停用 |
| `post_create.body.remark` | string；`"负责裁剪工序"` | 可选 | null/空白可清空 | 岗位备注；适配器只接受字符串或null |
| `post_update.body.postId` | string 正十进制 ID；`"20001"` | 必填 | null/空白/数字拒绝 | 从 `post_list`/`post_get` 复制 |
| `post_update.body.orgId` | string 正十进制 ID；`"10001"` | 必填 | null/空白/数字拒绝 | 从组织查询复制；作为岗位主归属 |
| `post_update.body.belongOrgId` | string 正十进制 ID或null；`null` | 必须显式提交 | null在实体更新中保留原值；数字/空白拒绝 | 归属树字段；目前无专用清除语义 |
| `post_update.body.postCode` | string；`"CUTTER"` | 必填、非空 | null/空白拒绝；最多64字符 | 编码唯一；修改前回填当前值 |
| `post_update.body.postName` | string；`"裁剪员"` | 必填、非空 | null/空白拒绝；最多50字符 | 名称唯一；修改前回填当前值 |
| `post_update.body.postCategory` | string或null；`"生产"` | 必须显式提交 | null在实体更新中保留原值；空字符串用于清空 | 类别编码最多100字符 |
| `post_update.body.postSort` | integer；`10` | 必填 | null/小数/字符串拒绝 | 完整最终排序值 |
| `post_update.body.status` | string；`"0"` | 必填 | null/空白拒绝；只能字符串0/1 | 启停最终态；已有员工分配时停用会被拒绝 |
| `post_update.body.remark` | string或null；`"负责裁剪工序"` | 必须显式提交 | null在实体更新中保留原值；空白字符串可清空 | 备注最终值 |
| `post_set_status.postId`、`post_delete.postIds` | string；`"20001"` / `"20001,20002"` | 必填 | 单 ID 空值拒绝；删除参数空白解析为空数组；格式非法拒绝 | 删除支持逗号分隔批量 ID，写前逐项读取名称并确认；失败不得自动改成停用 |
| `post_set_status.status` | string；`"1"` | 必填 | null/空白/数字拒绝；仅字符串0/1 | 目标状态；停用已有员工分配岗位时拒绝 |

创建需 `system:post:add`，修改/启停需 `system:post:edit`，删除需 `system:post:remove`；列表/组织树需 `system:post:list`，详情/选择需 `system:post:query`。查询只返回当前员工可见数据。

### MCP `arguments` 示例

```jsonl
{"name":"post_list","arguments":{"query":{"orgId":"10001","status":"0"},"keyword":"裁剪","pageNum":1,"pageSize":20}}
{"name":"post_create","arguments":{"body":{"orgId":"10001","belongOrgId":"10001","postCode":"CUTTER","postName":"裁剪员","postCategory":"生产","postSort":10,"status":"0","remark":"负责裁剪工序"}}}
{"name":"post_update","arguments":{"body":{"postId":"20001","orgId":"10001","belongOrgId":null,"postCode":"CUTTER","postName":"裁剪员","postCategory":"生产","postSort":10,"status":"0","remark":"负责裁剪工序"}}}
{"name":"post_set_status","arguments":{"postId":"20001","status":"1"}}
{"name":"post_delete","arguments":{"postIds":"20001,20002"}}
```

所有示例 ID 均需替换为当前列表查询结果。`post_update` 按完整对象提交；null 的保留语义按字段矩阵处理，不要将 null 当作通用清除指令。
