# API 文档（总索引）

> 版本：v1
> 更新日期：2026-10-26
> 说明：面向智能体的 API 总索引。格式遵守《Docs/API文档规范.md》：先在此定位功能分组与接口，再进分组文档看完整契约。**分组文档一律放 `Docs/API/` 目录。**

---

## 〇、通用调用方法（所有接口共用）

### 1. 认证（两步）

**第一步：登录取 token**

```bash
curl -X POST http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"e5cd7e4891bf95d1d19206ce24a7b32e","grantType":"password","username":"admin","password":"ABCabc123"}'
```

- `clientId`：固定应用标识（非密钥，前端源码同款）
- 成功返回 `data.access_token`（有效期见后端配置，过期重新登录）
- 登录失败：`{"code":500,"data":null,"msg":"用户不存在/密码错误"}`

**第二步：后续每个请求带两个 header**

```
Authorization: Bearer <access_token>
clientid: e5cd7e4891bf95d1d19206ce24a7b32e
```

### 2. 统一响应结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | `200`=成功；`401`=未登录/登录过期；`403`=无权限；`500`=业务失败（msg 为具体原因） |
| `msg` | string | 提示信息（失败原因） |
| `data` | any | 成功时的数据；失败时 `null` |

### 3. 智能体调用红线（摘自《开发规范.md》）

- 每次写操作**以发起员工本人的 token 调用**（权限由后端 Sa-Token 按 token 校验，禁止用超管 token 代执行）
- deptId 等 bigint 主键在 JSON 里是**字符串**（如 `"2114531897794859009"`），**禁止转 number**（>2^53 丢精度）
- 写接口带 `@RepeatSubmit`：间隔内相同参数重复提交会被拒（`不允许重复提交，请稍候再试`），重试需换时间或确认首次是否已生效

---

## 一、功能分组清单

| 功能分组 | 英文键 | 分组文档 | 状态 | 验收日期 |
|---|---|---|---|---|
| 小组管理 | group | [Docs/API/小组管理API.md](小组管理API.md) | ✅ 已达标 | 2026-10-26 |
| （后续功能验收后逐组追加） | | | | |

---

## 二、接口速查（每接口一行）

### 小组管理 group（复用 `/system/dept`，小组 = `dept_category='小组'`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/dept/list` | 查组织树/小组列表（支持 `deptCategory` 过滤、平铺返回） | 只读 |
| `GET /system/dept/{deptId}` | 查单个部门/小组详情（含 ancestors 层级链） | 只读 |
| `POST /system/dept` | 新建部门/小组（`deptCategory='小组'` 即建小组） | 写 |
| `PUT /system/dept` | 修改部门/小组 | 写 |
| `DELETE /system/dept/{deptId}` | 删除部门/小组（5 道前置校验） | 写 |

> 权限码：`system:dept:list` / `system:dept:query` / `system:dept:add` / `system:dept:edit` / `system:dept:remove`（按发起员工角色过滤，无权限返回 403）。

---

## 三、维护约定

1. 每个功能**验收通过后**当批次补一份分组文档进 `Docs/API/`，并更新本索引（分组清单 + 速查行）。
2. 分组文档必须含**机器契约五字段**（幂等性/副作用/前置条件/是否需审批/权限要求），缺一不算达标。
3. 示例必须是**可直接运行的 curl/JSON**，与后端实测一致（禁止凭记忆写）。
4. MCP 工具（`mcp-server/`）与 Skill（`Docs/Skills/`）以本索引为能力清单来源。
