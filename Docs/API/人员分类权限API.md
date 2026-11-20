# 人员分类权限 API 与 MCP 契约

> 版本：v1<br>
> 更新日期：2026-11-20<br>
> 状态：用户于 2026-11-20 验收通过；REST、主建表、Skill 和 MCP 已接入。2026-11-20 开发服务 `tools/list` 已发现 2 个分类权限工具；授权权限、人员数据范围和真实读写仍待普通员工账号核验。

## 一、用途与权限边界

人员分类权限在人员信息中单独维护，限制员工可见的分类及相关基础资料，不属于角色权限并集。角色需显式分配 `system:person:categoryScope` 才能配置该项。配置操作受操作人的人员数据范围限制，并写入业务审计。

超级管理员账号不配置人员分类范围，按全部可见处理；普通人员不能修改本人的分类授权。未保存独立授权记录的普通人员默认全部可见。全部可见、指定范围、全部不可见分别对应 `ALL`、`SELECTED`、`NONE`。

所有ID在 REST 和 MCP 参数中使用十进制字符串，避免 JavaScript 数值精度损失。普通分类业务接口及 MCP 工具应使用当前员工身份执行服务端分类过滤；人员分类管理 MCP 工具仅用于具有专门配置权限的人员维护授权。

## 二、REST 接口

### 查询人员分类授权和分类目录

`GET /system/person/{userId}/category-scope`

- 权限：`system:person:categoryScope`。
- 目标人员必须是操作人数据范围内的普通人员；超级管理员账号不是可配置目标。
- 返回该人员当前模式和直接授权项，以及可供页面构造选择树的分类类型与节点。已停用节点仍可能随历史授权显示。

成功响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "scope": {
      "name": "示例员工",
      "mode": "SELECTED",
      "grants": [
        {"schemeId": "1793602859336", "categoryId": null, "includeChildren": true, "name": "物料分类"}
      ]
    },
    "schemes": [{"id": "1793602859336", "name": "物料分类", "status": "0"}],
    "categories": [{"id": "1793602859444", "schemeId": "1793602859336", "parentId": null, "name": "面料", "status": "0"}]
  }
}
```

`scope.grants` 是直接保存的授权，不是展开后的所有有效节点。类型授权的 `schemeId` 非空；节点授权的 `categoryId` 非空。类型树和节点树使用真实 ID 关联，不能按名称、编码前缀推导授权。

### 全量保存人员分类授权

`PUT /system/person/{userId}/category-scope`

- 权限：`system:person:categoryScope`。
- 非超管不能修改自己的授权；目标人员须在操作人的人员数据范围内，且不能是超级管理员账号。
- 这是全量替换：提交最终模式和完整 `grants`，不是局部追加。
- `SELECTED` 至少包含一个授权项；每一项必须且只能是一个 `schemeId` 或 `categoryId`。授权项不能重复。类型授权必须 `includeChildren=true`；节点授权可设 `true` 或 `false`。
- `ALL` 和 `NONE` 必须提交空 `grants` 数组。保存 `ALL` 会移除单独授权记录，因此回显为默认全部可见；`NONE` 会保留显式不可见模式。
- 成功后记录操作人员、目标人员和授权模式/范围前后值。

指定范围示例：

```json
{
  "mode": "SELECTED",
  "grants": [
    {"schemeId": null, "categoryId": "1793602859444", "includeChildren": false}
  ]
}
```

全部可见或全部不可见示例：

```json
{"mode": "ALL", "grants": []}
```

```json
{"mode": "NONE", "grants": []}
```

业务校验失败返回统一业务错误，例如：

```json
{"code": 500, "msg": "请选择授权模式；指定范围须至少添加一项，其他模式不提交授权项", "data": null}
```

未登录返回 `401`，未获配置权限返回 `403`。人员不存在、不在数据范围、目标为超管、非法授权模式、重复分类或无效分类ID均拒绝保存，并返回中文原因。

## 三、MCP 工具

MCP 工具沿用当前员工登录身份、`system:person:categoryScope` 权限、人员数据范围校验和 REST 控制器审计。2026-11-20 开发服务 `tools/list` 已发现 `person_category_scope_get`、`person_category_scope_set`；本次尚未使用不同授权级别的普通员工账号验证实际读写、拒绝和审计结果。

### 查询授权

工具：`person_category_scope_get`

```json
{"name":"person_category_scope_get","arguments":{"userId":"1793602859000"}}
```

### 设置授权

工具：`person_category_scope_set`

调用前先通过查询工具读取现状，向员工展示目标人员、原授权和完整新授权范围并取得本次确认。不得默默扩大或收回授权。

```json
{
  "name": "person_category_scope_set",
  "arguments": {
    "userId": "1793602859000",
    "scope": {
      "mode": "SELECTED",
      "grants": [{"schemeId": "1793602859336", "categoryId": null, "includeChildren": true}]
    }
  }
}
```

MCP 写工具与 REST 使用同一专门权限。智能体不得借用超管身份、猜测人员或分类ID，或把“无权”解释为“没有资料”。

## 四、与分类业务接口的关系

分类授权只定义人员可访问的分类范围；业务参数中的默认分类只负责页面初始定位，不授予权限，也不缩小授权范围。业务查询必须先在服务端按人员授权过滤，再执行分页、统计、导出和关键字搜索；新增、修改、启停及业务引用保存时再次校验分类范围。

客户、物料、款式、款式 BOM 等各业务的接口覆盖范围见各自 API 分组文档。采购、库存、食堂、设备及其他尚未接入的业务，必须在开发该业务时完成分类范围集成；不得因本设置已验收而宣称所有模块均已受分类权限保护。
