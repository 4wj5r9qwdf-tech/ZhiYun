---
name: zhiyun-warehouse-layout
description: 查询织云 ERP 仓库内的库区、货架、货位及其层级关系和编码建议。用于用户查询仓库布局、位置状态或自动编码建议。
---

# 仓库布局

## 接入状态

- 功能已由用户验收通过（2026-11-05）。
- API 契约：[仓库布局 API](../../API/仓库布局API.md)。
- MCP 查询、位置 CRUD、状态和规则工具已接入，后端构建并重启成功；首次使用仍应确认 MCP 客户端能发现工具，并通过有效员工 token、`clientid` 与权限检查。
- CSV/批量导入工具未注册。不得从 Skill 直接调用 REST、数据库或任意 HTTP/SQL 工具代替。

## 可用工具

| MCP 工具 | 用途 | 权限 |
|---|---|---|
| `warehouse_layout_options` | 获取仓库和入库定位层级 | 登录 |
| `warehouse_layout_tree` | 查询库区—货架—货位树 | `inventory:location:list` |
| `warehouse_layout_list` | 按仓库/父级/层级/状态筛选位置 | `inventory:location:list` |
| `warehouse_layout_get` | 查询位置详情及父级 | `inventory:location:query` |
| `warehouse_layout_next_code` | 查看下一个层级编码建议 | `inventory:location:add` |
| `warehouse_layout_create` | 新增库区、货架或货位 | `inventory:location:add` |
| `warehouse_layout_update` | 修改可编辑位置属性 | `inventory:location:edit` |
| `warehouse_layout_set_status` | 启用/停用/维护位置 | `inventory:location:edit` |
| `warehouse_layout_delete` | 删除符合条件的位置 | `inventory:location:remove` |
| `warehouse_layout_set_code_rule` | 修改层级自动编码规则 | `system:config:edit` |

## 查询流程

1. 用户问仓库全貌或层级关系时调用 `warehouse_layout_tree`；指定某仓库时传 `warehouseId`，查询全部时不传。
2. 需要条件筛选时调用 `warehouse_layout_list`。层级值：1=库区、2=货架、3=货位；状态值：`active`、`disabled`、`maintenance`。
3. 需要单条完整信息时调用 `warehouse_layout_get`。ID 始终用十进制字符串。
4. 编码建议工具只查询建议，不预留、不新增。解释编码由仓库编码与每级编码段逐级组成，建议值可编辑。

## 写入请求处理

用户明确要求写入时，先查询并展示仓库、父级、类型、完整字段或规则及其影响，等待员工本次确认后再调用对应工具。失败后先查询核对，不能盲目重试新增。批量 CSV 导入尚无 MCP 工具，应指引用户通过页面导入。

## 安全与业务边界

- 库区直接属于仓库；货架属于库区；货位属于货架。不得虚构层级关系或跨仓移动位置。
- 位置建立后所属仓库、类型、父级和编码不可改；需要调整结构时由员工按业务流程新增或停用。
- 有子级或被单据引用的位置不可删除；应尊重后端校验，不自动级联。
- CSV 导入当前由页面解析并逐条写入，可能部分成功；MCP 批量导入工具尚未接通，不要声称具备事务型批量导入。
- AGV 字段仅表示位置能力和外部映射，本功能不创建或调度 AGV 任务。
- 所有写入工具接通后，新增、修改、状态变更、删除和参数修改均需先展示完整影响并取得员工本次确认。
