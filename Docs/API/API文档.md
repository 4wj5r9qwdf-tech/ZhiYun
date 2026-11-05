# API 文档（总索引）

> 版本：v1
> 更新日期：2026-11-05
> 说明：功能 API/MCP 契约总索引。先在此定位功能分组与接口，再进分组文档看完整契约。普通 REST 已实现不等于 MCP 已注册；每组需明确运行时状态。**分组文档一律放 `Docs/API/` 目录。**

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
- orgId 等 bigint 主键在 JSON 里是**字符串**（如 `"2114531897794859009"`），**禁止转 number**（>2^53 丢精度）
- 写接口带 `@RepeatSubmit`：间隔内相同参数重复提交会被拒（`不允许重复提交，请稍候再试`），重试需换时间或确认首次是否已生效

---

## 一、功能分组清单

| 功能分组 | 英文键 | 分组文档 | 状态 | 验收日期 |
|---|---|---|---|---|
| 计量单位 | `unit` | [计量单位API.md](计量单位API.md) | REST/MCP已实现；工具发现、单位读写及低权限拒绝已验证 | 用户验收通过（2026-11-05） |
| 分类管理 | `category` | [分类管理API.md](分类管理API.md) | 体系与节点REST/MCP已实现并完成发现/读写验证；待用户验收 | 未验收 |
| 尺码管理 | `size` | [尺码管理API.md](尺码管理API.md) | REST 已实现；MCP 工具未注册 | 2026-11-04 |
| 客户档案 | `customer_master` | [客户档案API.md](客户档案API.md) | REST 已实现；MCP 工具已构建并随服务重启，客户端发现待核对 | 2026-11-05 |
| 公司档案 | `company` | [公司档案API.md](公司档案API.md) | REST 已实现；MCP 工具已构建并随服务重启，客户端发现待核对 | 2026-11-05 |
| 组织架构 | `org` | [组织架构API.md](组织架构API.md) | REST 已实现；MCP 工具已构建并随服务重启，客户端发现待核对 | 2026-11-05 |
| 岗位管理 | `post` | [岗位管理API.md](岗位管理API.md) | REST 已实现；MCP 工具已构建并随服务重启，客户端发现待核对 | 2026-11-05 |
| 建筑档案 | `building` | [建筑档案API.md](建筑档案API.md) | REST 已实现；MCP 工具已构建并随服务重启，客户端发现待核对 | 2026-11-05 |
| 仓库管理 | `warehouse` | [仓库管理API.md](仓库管理API.md) | REST 已实现；MCP 查询与写工具已接入并随后端构建重启，CSV 工具未注册 | 2026-11-05 |
| 仓库布局 | `warehouse_layout` | [仓库布局API.md](仓库布局API.md) | REST 已实现；MCP 查询/写入工具已接入并随后端构建重启，批量导入工具未注册 | 2026-11-05 |

---

## 二、接口速查（每接口一行）

### 组织架构 org（`/system/org` 复用；档案类型 org_category 由用户自选，仅展示标识）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/org/list` | 查组织树/组织列表（支持 `orgCategory` 过滤、平铺返回） | 只读 |
| `GET /system/org/{orgId}` | 查单个组织节点详情（含 ancestors 层级链） | 只读 |
| `POST /system/org` | 新建组织节点（orgCategory 用户自选） | 写 |
| `PUT /system/org` | 修改组织节点 | 写 |
| `DELETE /system/org/{orgId}` | 删除组织节点（5 道前置校验） | 写 |

> 权限码：`system:org:list` / `system:org:query` / `system:org:add` / `system:org:edit` / `system:org:remove`（按发起员工角色过滤，无权限返回 403）。

### 尺码管理 size（`/system/size-data/size`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/size-data/size/list` | 查询尺码档案（含类型标签） | 只读 |
| `GET /system/size-data/size/options` | 查询业务选择用尺码（含状态） | 只读 |
| `GET /system/config/configKey/system.code.size` | 查询尺码自动编码规则（固定参数键） | 只读 |
| `POST /system/size-data/size` | 新增尺码 | 写 |
| `PUT /system/size-data/size/{id}` | 修改尺码档案 | 写 |
| `PUT /system/size-data/size/{id}/status` | 启用或停用尺码 | 写 |
| `DELETE /system/size-data/size/{id}` | 删除未被尺码组引用的尺码 | 写 |
| `POST /system/size-data/size/batch-tags` | 批量替换所选尺码的类型标签；空数组清空标签 | 写 |
| `POST /system/config/size-code-rule` | 更新自动编码开关、前缀和数字位数 | 写 |

> 权限码：尺码档案 `system:size:list/add/edit/delete`；编码规则 `system:config:edit`。REST 路由已实现；当前未注册到 MCP 动作目录，智能体不可直接调用，见[完整 API 契约](尺码管理API.md)。

### 仓库管理 warehouse（`/system/warehouse`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/warehouse/list` | 按关键字、状态、仓库类型查询仓库 | 只读 |
| `GET /system/warehouse/options` | 查询建筑和负责人选项 | 只读 |
| `GET /system/warehouse/next-code` | 查询仓库编码建议 | 只读 |
| `GET /system/warehouse/{id}` | 查询仓库详情和类型标签 | 只读 |
| `POST /system/warehouse` | 新增仓库 | 写 |
| `PUT /system/warehouse` | 修改仓库 | 写 |
| `PUT /system/warehouse/{id}/status` | 启用或停用仓库 | 写 |
| `DELETE /system/warehouse/{id}` | 逻辑删除无布局仓库 | 写 |
| `POST /system/config/warehouse-code-rule` | 设置仓库自动编码规则 | 写 |

> 权限码：`inventory:warehouse:list/query/add/edit/remove`；规则设置为 `system:config:edit`。MCP 查询与写工具均已接入代码，写调用必须先获得员工确认；见[完整仓库管理契约](仓库管理API.md)。

### 仓库布局 warehouse_layout（`/system/warehouse-location`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/warehouse-location/options` | 查询仓库及入库定位深度 | 只读 |
| `GET /system/warehouse-location/tree` | 查询库区—货架—货位层级树 | 只读 |
| `GET /system/warehouse-location/list` | 按仓库、父级、层级、关键词、状态查询 | 只读 |
| `GET /system/warehouse-location/{id}` | 查询位置详情 | 只读 |
| `GET /system/warehouse-location/next-code` | 查询位置编码建议 | 只读 |
| `POST /system/warehouse-location` | 新增位置；页面导入也逐条调用此接口 | 写 |
| `PUT /system/warehouse-location` | 修改位置属性 | 写 |
| `PUT /system/warehouse-location/{id}/status` | 启用、停用或维护位置 | 写 |
| `DELETE /system/warehouse-location/{id}` | 逻辑删除未被引用的末级位置 | 写 |
| `GET /system/config/configKey/inventory.warehouseLayout.codeRule` | 读取固定的布局编码规则 | 只读 |
| `POST /system/config/warehouse-layout-code-rule` | 设置库区/货架/货位编码规则 | 写 |

> 权限码：位置查询 `inventory:location:list/query`，维护 `inventory:location:add/edit/remove`；规则设置 `system:config:edit`。MCP 查询/写入工具均已接入代码；CSV 无专用批量 API，也未提供 MCP 批量工具；见[完整仓库布局契约](仓库布局API.md)。

### 客户档案 customer_master（`/system/customer`）

| 方法 + 路径 | 说明 | 读/写 |
|---|---|---|
| `GET /system/customer/list` | 客户档案分页查询 | 只读 |
| `GET /system/customer/category-counts` | 客户分类数量 | 只读 |
| `GET /system/customer/{id}` | 客户主档、联系人、地址和标签 | 只读 |
| `GET /system/customer/duplicates`、`/sales-options`、`/options` | 同名提示、业务员筛选、启用客户选项 | 只读 |
| `POST /system/customer`、`PUT /system/customer/{id}` | 新增、修改 | 写 |
| `PUT /system/customer/{id}/status`、`DELETE /system/customer/{id}` | 启停、删除未被引用档案 | 写 |

> 权限及联系人脱敏规则见[客户档案 API 契约](客户档案API.md)。MCP 工具已加入代码，待运行时发现核对。

### 公司档案 company（`/system/company`）

列表、详情、OSS 文件引用查询、新增、修改、设置默认公司、软删除接口和权限见[公司档案 API 契约](公司档案API.md)。切换默认公司会影响登录页及首页展示，智能体须在写入前展示影响并取得确认。MCP 工具已加入代码，待运行时发现核对。

### 组织架构 org（`/system/org`）

组织树、详情、组织选项、业务管辖查询与全量替换、新增、修改、删除接口及数据范围约束见[组织架构 API 契约](组织架构API.md)。MCP 工具已加入代码，待运行时发现核对。

### 岗位管理 post（`/system/post`）

岗位分页、详情、选项、组织树、新增、修改、启停、批量删除接口及权限见[岗位管理 API 契约](岗位管理API.md)。MCP 工具已加入代码，待运行时发现核对。

### 建筑档案 building（`/system/building`）

建筑列表、详情、新增、修改和删除接口及权限见[建筑档案 API 契约](建筑档案API.md)。MCP 工具已加入代码，待运行时发现核对。

---

## 三、维护约定

1. 每个用户验收通过的功能都补充分组 API/MCP 契约文档、Skill 并更新本索引。准确标明 REST 实现和 MCP 运行时接入状态；未注册的工具不可标成可用。
2. 分组文档必须含**机器契约五字段**（幂等性/副作用/前置条件/是否需审批/权限要求），缺一不算达标。
3. 示例必须是**可直接运行的 curl/JSON**，与后端实测一致（禁止凭记忆写）。
4. 功能验收后在 `Docs/Skills/<功能名>/SKILL.md` 建立 Skill；MCP 工具接入时按《内嵌智能体规范》逐组登记。Skill 必须声明所依赖工具是否已注册，不以文档存在暗示运行时已接通。


## 客户档案（2026-11-02）

- GET /system/customer/list：分页，keyword/status/salesId/tagId；返回 rows/total。
- GET /system/customer/{id}：主档、contacts、addresses、tagIds、tagOptions（含停用名称）；只读权限联系方式脱敏。
- GET /system/customer/duplicates：name/id，同名计数仅提醒，不强制拒绝。
- GET /system/customer/sales-options：业务员及岗位、组织、小组筛选所需非敏感信息。
- GET /system/customer/options：仅启用客户，供新业务引用。
- POST /system/customer：自动编号，事务保存主档和子项。
- PUT /system/customer/{id}：编辑，不能覆盖编号。
- PUT /system/customer/{id}/status：状态0启用/1停用，不改变历史业务。
- 权限：查询list、新增add、修改与启停edit，前缀system:customer；不提供物理删除。
- 联系方式只有客户维护权限可读原值；操作日志不保存客户请求与响应正文。
- 停用标签原关联可保留且显示名称，新增关联服务端拒绝。

## 国家/地区基础资料（2026-11-03）

- GET `/system/country-region/options`：登录后获取编码、中英文名称及启停状态；包含停用项以显示已有引用，前端禁止新增选择停用项。
- GET `/system/country-region/list`：目录管理查询，需 `system:country:list`。
- POST `/system/country-region`：新增编码和名称，需 `system:country:add`；编码创建后不可更改。
- PUT `/system/country-region/{code}`：修改名称；PUT `/system/country-region/{code}/status`：启停；均需 `system:country:edit`。不提供物理删除。
- 客户主档与地址分别提交 `country_region_code`；名称 `country_region` 由服务端按编码保存快照。已停用编码仅允许原记录保留；未能唯一匹配的旧手填值可原样保留或清除，修改为其他地区时必须选择有效编码。
- 客户列表 `country_display` 优先显示标准资料的当前中文名；详情中的 `country_region` 是保存时名称或未匹配旧值。订单目的地和实际发货目的地不读取客户所在地区作为事实。
