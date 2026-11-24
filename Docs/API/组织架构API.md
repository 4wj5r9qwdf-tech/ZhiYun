# 组织架构 API / MCP 契约

> 功能键：`org`；主表：`sys_org`，业务管辖关联表：`sys_org_manage`。当前基础组织架构用户验收通过（2026-11-05）；节点种类、层级扩展等后续范围另行确认。
> 契约迁移：2026-11-21 将组织 MCP 从 Long 型 DTO 参数改为 Map 适配器；代码先校验字符串 ID 和完整更新字段，再转入原业务 DTO。运行时 inputSchema/权限调用仍需验收。

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
| `org_manages_set` | `system:org:edit` | 全量替换关系；必须显式传入数组，空数组清空，缺失/null 拒绝；必须确认 |
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
  "email":"department@example.com",
  "status":"0"
}
```

新增时省略 `orgId`。必填为 `orgName`、`orderNum`；`parentId` 按目标层级提供。`orgCategory` 为业务类别，建筑是独立物理档案；业务管辖关系与树层级相互独立。

页面中的“部门电话”和“部门邮箱”分别对应现有字段 `phone` 和 `email`，用于登记部门联系方式；`leader` 是负责人用户 ID。部门邮箱目前不自动接收审批通知，选择部门审批时匹配部门人员，不等同于只选择部门负责人。

成功响应示例：`{"code":200,"msg":"操作成功","data":null}`。MCP `org_manages_set` 必须显式传入最终 ID 数组，例如 `["10002","10003"]`；空数组代表清空全部，缺失或 `null` 会被拒绝，避免调用方漏传参数时误清关系。所有 ID 必须是有效的正十进制字符串。

### 错误与边界

组织名称重复、父级无效、不能自为父级、禁用节点仍有子级/人员、默认组织/非叶节点删除、人员/岗位引用、数据范围越权均由后端拒绝。不得为了完成用户请求自动改动人员归属或其他组织关系。

## MCP 输入字段矩阵（契约迁移批次 1）

`org_create`、`org_update`、`org_list` 和 `post_org_tree` 使用自由 object；MCP 适配器先拒绝未知键和数字型 ID，再将字符串 ID 转成框架 `SysOrgBo`。`org_manages_set.managedOrgIds` 是最终完整列表，不是追加列表。工具 inputSchema 不展开 Map 内部字段，因此必须遵守本表的逐字段约束。

| 工具.字段路径 | JSON 类型 / 格式 / 示例 | 必填与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、校验与副作用 |
|---|---|---|---|---|
| `org_list.query` | object；`{"orgName":"车间","status":"0"}` | 可选；省略查询当前员工可见组织 | `null` 等同省略；`{}` 不筛选；字符串空白不筛选；数组不适用 | 筛选对象，不会扩大员工组织数据范围；未知字段不要发送 |
| `org_create.body.orgName`、`org_update.body.orgName` | string；`"一车间"` | 必填；省略、null 或全空白由 `@NotBlank` 拒绝 | 空白拒绝；数组/对象不适用 | 组织名称，同父级下需唯一，最长 30 个字符 |
| `org_create.body.orderNum`、`org_update.body.orderNum` | integer；`10` | 必填；省略/null 拒绝 | 空白不适用；数组/对象不适用 | 树内显示顺序；当前 DTO 未声明数值上下界 |
| `org_create.body.orgId` | 不接受该字段 | 新增由服务端生成 | 传入该键即拒绝 | 创建对象白名单不含 `orgId`，不允许客户端指定主键 |
| `org_update.body.orgId` | string 正十进制 ID；`"10001"` | 必填；省略无法定位目标 | `null`/空白拒绝；数组/对象不适用 | 从 `org_list`/`org_get` 复制；须在当前员工数据范围内 |
| `org_create.body.parentId`、`org_update.body.parentId` | string 正十进制 ID 或 null；`"10000"` / `null` | 新增可省略表示根；更新必填此键 | null 或字符串 `"0"` 表示根；更新 null 明确移动到根，不能表示“未修改”；空白和数字拒绝 | 从组织树复制。移动节点会联动更新后代祖先链；适配器将合法值转换为框架 Long |
| `org_create.body.orgCategory`、`org_update.body.orgCategory` | string；`"workshop"` | 可选；省略保留/不设置 | null 未作为写入语义保证；空字符串可被 DTO 接收但业务含义未定义；数组/对象不适用 | 类别编码最长 100；组织类别与建筑实体是不同字段 |
| `org_create.body.buildingId`、`org_update.body.buildingId` | string 正十进制 ID；`"30001"` | 可选；省略时不设置 | null 在更新实体转换后可能按 ORM 非空策略保留旧值；不能据此清除建筑关系；空白拒绝；数组/对象不适用 | 只为车间节点选择有效建筑；来源于建筑查询。清除关系的明确 MCP 操作尚未提供 |
| `org_create.body.location`、`org_update.body.location` | string；`"一号厂房/1层"` | 可选 | null 的更新效果未承诺；空白可通过长度校验，但作为空路径的业务行为未专门定义 | 位置路径文本，最长 200；提交前核对建筑与路径 |
| `org_create.body.leader`、`org_update.body.leader` | string 正十进制用户 ID；`"20001"` | 可选 | 更新时 null 可能按 ORM 非空策略保留旧负责人；不能据此清除；空白拒绝；数组/对象不适用 | 负责人必须来自员工查询；清除负责人的专用语义尚未定义 |
| `org_create.body.phone`、`org_update.body.phone` | string；`"021-55550000"` | 可选 | null 的更新效果未承诺；空字符串可通过长度约束并可能写入空值 | 部门电话，最长 11 个字符；仅作联系资料，不是负责人个人号码 |
| `org_create.body.email`、`org_update.body.email` | string；`"workshop@example.com"` | 可选 | null/空字符串的更新效果未专门定义；空白字符串不得当作可靠清除方式 | 部门邮箱，需满足 Email 格式且最长 50 个字符；不自动接收审批通知 |
| `org_create.body.status` | string 枚举；`"0"` | 可选；省略使用框架默认值 | null/空白/数字拒绝；若提供仅允许字符串 `"0"`/`"1"` | `0` 正常、`1` 停用；停用有下级或人员的节点会拒绝 |
| `org_update.body.status` | string 枚举；`"0"` | 必填；更新完整对象的一部分 | null/空白/数字拒绝；仅允许字符串 `"0"`/`"1"` | `0` 正常、`1` 停用。更新为 `0` 会自动启用祖先节点；停用有下级或人员的节点会拒绝 |
| `org_manages_set.orgId` | string 正十进制 ID；`"10001"` | 必填；省略拒绝 | `null`/空白拒绝；数组/对象不适用 | 管辖方组织 ID，须从可见组织查询复制 |
| `org_manages_set.managedOrgIds` | array<string>；`["10002","10003"]` | 必填；缺失或 null 拒绝 | `[]` 合法，清空全部现有管辖关系；空白字符串不是数组 | 最终被管辖组织集合，每个 ID 为可访问组织的十进制字符串；全量替换并记录审计 |

**更新注意：** 组织更新不是补丁。MCP 必须提交完整字段；其中 `parentId:null` 或 `"0"` 明确移动到根，控制器对此 null 做了空安全处理。`buildingId`、`leader` 等字段的 null 由 ORM 按非空更新策略保留旧值，不能据此清除；清除关系的专用操作尚未提供。

**运行时待验：** 代码现以 Map 接收对象，内部 ID 按字符串验证后转换，解决了原 `SysOrgBo` Long 字段与字符串 ID 契约的冲突。inputSchema 对 Map 子字段不会递归约束；普通员工权限、运行时 `tools/list` 以及真实读取仍需在业务验收后核对。
