# 小组管理 API（group）

> 版本：v1
> 更新日期：2026-10-26（与后端实测一致）
> 功能分组：**小组管理 group**（《功能命名清单.md》第 50 行）
> 存储：`sys_org` 表，**`org_category='小组'` 是小组的唯一标识**（不新建表）；普通部门的 org_category 存"N 级"。
> 认证与统一响应见 [API文档.md 总索引](API文档.md)（本文不再重复登录步骤）。
> **orgId/parentId 等主键在 JSON 中一律为字符串，禁止转 number（>2^53 丢精度）。**

---

## 只读接口

### 1. 查组织树/小组列表

| 基本契约 | |
|---|---|
| 方法 + 路径 | `GET /system/org/list` |
| 一句话说明 | 返回**平铺**的部门/小组列表（非嵌套树，调用方按 parentId 自行组树）；`orgCategory` 过滤即可只拿小组 |
| 读/写类型 | **只读**（query） |
| 权限码 | `system:org:list` |

**请求参数**（query，全部可选；多条件为 AND）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| orgId | string(Long) | 否 | - | 按部门/小组 ID 精确过滤 |
| parentId | string(Long) | 否 | - | 按直接上级 ID 过滤（**查某上级下的一级子节点**） |
| orgName | string | 否 | - | 名称模糊匹配（like） |
| orgCategory | string | 否 | - | 类别模糊匹配；**查全部小组传 `orgCategory=小组`** |
| status | string | 否 | - | `0`=启用，`1`=停用 |
| belongOrgId | string(Long) | 否 | - | 部门树搜索：返回该节点**及其全部下级** |
| params[beginTime] / params[endTime] | string(日期) | 否 | - | createTime 创建时间范围 |

**示例（查全部小组）**

```bash
curl -sS "http://127.0.0.1:8080/system/org/list?orgCategory=%E5%B0%8F%E7%BB%84" \
  -H "Authorization: Bearer <access_token>" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e"
```

**成功返回示例**（实测；数组，固定按 ancestors→parentId→orderNum→orgId 排序）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "orgId": "2114531897794859009",
      "parentId": "2114510418550157313",
      "parentName": "1车间生产科",
      "ancestors": "0,2114261533571305473,2114266174136733697,2114510257610518530,2114510418550157313",
      "orgName": "1车间裁剪组",
      "orgCategory": "小组",
      "buildingId": null,
      "location": null,
      "orderNum": 0,
      "leader": null,
      "leaderName": null,
      "phone": null,
      "email": null,
      "status": "0",
      "createTime": "2026-10-26 09:37:45",
      "children": []
    }
  ]
}
```

**返回字段说明**

| 字段 | 说明 |
|---|---|
| orgId / parentId | **字符串**；parentId="0" 表示顶层 |
| ancestors | 层级链（逗号分隔，**不含自己**）。**层级深度 = 自己在链中的位置+1**；`parentName`、`leaderName` 为关联冗余名，可能为 null |
| orgCategory | 小组=`"小组"`；普通部门=`"1 级"/"2 级"/…` |
| buildingId | 所在建筑（厂房）ID；**小组通常为 null（沿祖先链继承，见下）** |
| location | 位置文本（楼/层/尾段拼接），可为 null |
| status | `"0"`=启用，`"1"`=停用 |
| children | 平铺返回时恒为 `[]`（组树由调用方按 parentId 完成） |

**错误返回**

| 场景 | 示例 |
|---|---|
| 未登录 | `{"code":401,"data":null,"msg":"登录状态异常，请重新登录"}` |
| 无权限 | `{"code":403,"data":null,"msg":"没有访问权限，请联系管理员授权"}` |

**机器契约**

| 字段 | 内容 |
|---|---|
| 幂等性 | **只读**，重复调用无副作用 |
| 副作用 | 无 |
| 前置条件 | 已登录（token 有效）；调用者具备 `system:org:list` |
| 是否需审批 | 否 |
| 权限要求 | `system:org:list`；数据范围（dataScope）按角色过滤 |

---

### 2. 查单个详情

| 基本契约 | |
|---|---|
| 方法 + 路径 | `GET /system/org/{orgId}` |
| 一句话说明 | 按 ID 查部门/小组详情（含 ancestors 层级链） |
| 读/写类型 | **只读**（query） |
| 权限码 | `system:org:query` |

**请求参数**：路径参数 `orgId`（string/Long，必填）。

**示例**

```bash
curl -sS "http://127.0.0.1:8080/system/org/2114531897794859009" \
  -H "Authorization: Bearer <access_token>" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e"
```

**成功返回示例**（实测）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "ancestors": "0,2114261533571305473,2114266174136733697,2114510257610518530,2114510418550157313",
    "buildingId": null,
    "children": [],
    "createTime": "2026-10-26 09:37:45",
    "orgCategory": "小组",
    "orgId": "2114531897794859009",
    "orgName": "1车间裁剪组",
    "email": null,
    "leader": null,
    "leaderName": null,
    "location": null,
    "orderNum": 0,
    "parentId": "2114510418550157313",
    "parentName": "1车间生产科",
    "phone": null,
    "status": "0"
  }
}
```

**错误返回**：`401` 未登录；`403` 无权限或超出数据范围；ID 不存在时 `{"code":500,"msg":"..."}` 类业务失败。

**机器契约**

| 字段 | 内容 |
|---|---|
| 幂等性 | **只读**，重复调用无副作用 |
| 副作用 | 无 |
| 前置条件 | 已登录；调用者具备 `system:org:query` 且该节点在角色数据范围内 |
| 是否需审批 | 否 |
| 权限要求 | `system:org:query` |

---

## 写接口

### 3. 新建部门/小组

| 基本契约 | |
|---|---|
| 方法 + 路径 | `POST /system/org` |
| 一句话说明 | 新建节点；**`orgCategory` 传 `"小组"` 即为新建小组**，`parentId` 为挂靠上级（部门或已建小组均可 → 支持二级小组） |
| 读/写类型 | **写**（mutation） |
| 权限码 | `system:org:add` |

**请求参数**（JSON body，`SysOrgBo`）

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| parentId | string(Long) | 否 | `0` | 挂靠上级 ID；`"0"`=顶层；可填部门或小组 ID |
| orgName | string | **是** | - | 名称，≤30 字符；**同级下不可重名** |
| orgCategory | string | 否 | - | 小组传 **`"小组"`**；普通部门传"N 级"标识，≤100 字符 |
| orderNum | integer | **是** | - | 显示顺序（数字越小越靠前），≥0 |
| buildingId | string(Long) | 否 | null | 所在建筑 ID；**小组一般不填**（前端沿祖先链带出） |
| location | string | 否 | null | 位置文本，≤200 字符 |
| leader | string(Long) | 否 | null | 负责人 **user_id**（系统人员 ID，非姓名文本） |
| phone | string | 否 | null | 联系电话，≤11 位 |
| email | string | 否 | null | 邮箱（需合法格式） |
| status | string | 否 | `"0"` | `0`=启用，`1`=停用 |
| orgId | string(Long) | 否 | - | 新建不传 |

**示例请求体（新建小组，可直接运行）**

```bash
curl -X POST http://127.0.0.1:8080/system/org \
  -H "Authorization: Bearer <access_token>" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e" \
  -H "Content-Type: application/json" \
  -d '{
    "parentId": "2114510418550157313",
    "orgName": "1车间裁剪2组",
    "orgCategory": "小组",
    "orderNum": 10,
    "status": "0"
  }'
```

**成功返回**（实测）

```json
{"code": 200, "data": null, "msg": "操作成功"}
```

> 新节点无自增回包——用 `GET /system/org/list?orgName=1车间裁剪2组` 回读取 orgId（见 Skill《新建小组》步骤③）。

**错误返回**（实测/代码）

| 场景 | 返回示例 |
|---|---|
| orgName 缺失 | `{"code":500,"data":null,"msg":"部门名称不能为空"}` |
| orderNum 缺失 | `{"code":500,"data":null,"msg":"显示顺序不能为空"}` |
| 重名（同级） | `{"code":500,"data":null,"msg":"新增部门'X'失败，部门名称已存在"}` |
| 间隔内重复提交 | `{"code":500,"data":null,"msg":"不允许重复提交，请稍候再试"}` |
| 未登录 / 无权限 | `401` / `403`（同总索引） |

**机器契约**

| 字段 | 内容 |
|---|---|
| 幂等性 | **不支持天然幂等**（每次成功都新建一行）；带 `@RepeatSubmit` 防间隔内相同参数误双击。**重试前必须先查是否已建**（用 list+orgName 回读），否则会重名失败（重名失败本身是安全的兜底） |
| 副作用 | `sys_org` 插入一行；新节点进入组织树（ancestors 由后端按 parentId 自动维护，**调用方不要传 ancestors**） |
| 前置条件 | `parentId` 指向的节点存在；同级无重名；调用者具备 `system:org:add` |
| 是否需审批 | 否 |
| 权限要求 | `system:org:add` |

---

### 4. 修改部门/小组

| 基本契约 | |
|---|---|
| 方法 + 路径 | `PUT /system/org` |
| 一句话说明 | 修改节点（改名/换挂靠上级/改负责人/改状态等）；**换 parentId 会触发后端重算该节点及其全部下级的 ancestors 层级链** |
| 读/写类型 | **写**（mutation） |
| 权限码 | `system:org:edit` |

**请求参数**：与新建相同的 body，但 **`orgId` 必填**（要改谁）。

**示例请求体**

```bash
curl -X PUT http://127.0.0.1:8080/system/org \
  -H "Authorization: Bearer <access_token>" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e" \
  -H "Content-Type: application/json" \
  -d '{
    "orgId": "2114531897794859009",
    "parentId": "2114510418550157313",
    "orgName": "1车间裁剪一组",
    "orgCategory": "小组",
    "orderNum": 10,
    "status": "0",
    "leader": "1761100000000000001"
  }'
```

**成功返回**

```json
{"code": 200, "data": null, "msg": "操作成功"}
```

**错误返回**（实测/代码）

| 场景 | 返回示例 |
|---|---|
| 上级填自己 | `{"code":500,"data":null,"msg":"修改部门'X'失败，上级部门不能是自己"}` |
| 改重名 | `{"code":500,"data":null,"msg":"修改部门'X'失败，部门名称已存在"}` |
| 停用但有启用子节点 | `{"code":500,"data":null,"msg":"该部门包含未停用的子部门!"}` |
| 停用但下面有人员 | `{"code":500,"data":null,"msg":"该部门下存在已分配用户，不能禁用!"}` |
| 间隔内重复提交 | `{"code":500,"data":null,"msg":"不允许重复提交，请稍候再试"}` |

**机器契约**

| 字段 | 内容 |
|---|---|
| 幂等性 | **不支持天然幂等**（重复提交相同 body = 重复执行同一修改，数据结果相同但 update_time 变化）；`@RepeatSubmit` 拦间隔内双击。同 body 重放语义上安全（结果收敛） |
| 副作用 | 更新 `sys_org` 本行；**若改了 parentId，级联重算该节点+全部下级的 ancestors**；停用（status=1）会校验子节点与人员占用 |
| 前置条件 | orgId 存在且在数据范围内；上级不能是自己；停用需无启用子节点且节点下无人 |
| 是否需审批 | 否 |
| 权限要求 | `system:org:edit` |

---

### 5. 删除部门/小组

| 基本契约 | |
|---|---|
| 方法 + 路径 | `DELETE /system/org/{orgId}` |
| 一句话说明 | 删除节点（软删，del_flag=1）；**5 道前置校验全过才删** |
| 读/写类型 | **写**（mutation） |
| 权限码 | `system:org:remove` |

**请求参数**：路径参数 `orgId`（string/Long，必填）。

**示例**

```bash
curl -X DELETE http://127.0.0.1:8080/system/org/2114531897794859009 \
  -H "Authorization: Bearer <access_token>" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e"
```

**成功返回**

```json
{"code": 200, "data": null, "msg": "操作成功"}
```

**错误返回**（5 道校验，任一不过即拒，均为 `code:500`）

| 顺序 | 校验 | 返回示例 |
|---|---|---|
| 1 | 系统默认部门 | `{"code":500,"data":null,"msg":"默认部门,不允许删除"}` |
| 2 | 有下级 | `{"code":500,"data":null,"msg":"存在下级部门,不允许删除"}` |
| 3 | 节点下有人员 | `{"code":500,"data":null,"msg":"部门存在用户,不允许删除"}` |
| 4 | 节点挂了岗位 | `{"code":500,"data":null,"msg":"部门存在岗位,不允许删除"}` |
| 5 | 被其他部门业务管辖 | `{"code":500,"data":null,"msg":"该部门被其他部门管辖,请先在管辖部门中移除"}` |

> 顺序即执行顺序：**要删小组，先确保它没有下级（二级小组先删子组）、组里没人、没被管辖引用。**

**机器契约**

| 字段 | 内容 |
|---|---|
| 幂等性 | **幂等**（第二次删已删节点：软删标记重复置位/或因 del_flag 已 1 走不到，结果收敛不报错扩散）；失败均是校验型拒绝，无部分写入 |
| 副作用 | `sys_org.del_flag=1`（软删）；该节点从组织树消失，**其下级（若有）不会被删** |
| 前置条件 | 无下级、无人员、无岗位、未被管辖引用、非默认部门；调用者具备 `system:org:remove` |
| 是否需审批 | 否 |
| 权限要求 | `system:org:remove` |

---

## 附：小组相关业务规则（智能体必读）

1. **小组判定**：`orgCategory == "小组"`；别用层级深度判定（普通部门也有同深度的）。
2. **二级小组**：`parentId` 可以是另一个小组（如 缝纫1组 挂 缝纫组 下），系统允许；`ancestors` 会如实反映。
3. **层级路径**：由 `ancestors` 拼接展示（每个 id 经 `GET /system/org/list` 映射为名称，`>` 连接），深度 = 链长 + 1。
4. **所属厂房**：小组的 `buildingId` 通常为 null——**沿祖先链向上找最近非 null 的 buildingId** 即为该组所在厂房（前端 `workshopOfOrg` 同款逻辑）。
5. **负责人**：`leader` 存 user_id（字符串），显示名经人员接口映射；传姓名文本会导致后端 Long 反序列化失败（500）。
