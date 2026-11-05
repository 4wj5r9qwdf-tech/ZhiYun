# 建筑档案 API / MCP 契约

> 功能键：`building`；表：`sys_building`；作为物业资产中的建筑实体，不是组织节点。用户验收通过（2026-11-05）。

## MCP 工具

| 工具 | 权限 | 类型 |
|---|---|---|
| `building_list` | `system:building:list` | 只读筛选 |
| `building_get` | `system:building:query` | 只读详情 |
| `building_create` | `system:building:add` | 写；展示完整建筑字段并确认 |
| `building_update` | `system:building:edit` | 写；展示变化并确认 |
| `building_delete` | `system:building:remove` | 软删除；展示身份并确认 |

## REST 接口

| 方法 + 路径 | 用途 / 参数 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/building/list` | keyword、buildingType、status 筛选，并返回关联车间数 | `system:building:list` | 只读幂等；无需审批 |
| `GET /system/building/{plantId}` | 建筑详情 | `system:building:query` | 只读幂等；无需审批 |
| `POST /system/building` | 新增建筑档案 | `system:building:add` | 非幂等；写建筑与审计；需员工确认 |
| `PUT /system/building` | 修改建筑档案 | `system:building:edit` | 更新档案与审计；需员工确认 |
| `DELETE /system/building/{plantId}` | 软删除建筑 | `system:building:remove` | 非幂等；有关联组织时拒绝；需员工确认 |

### 新增/修改请求体

```json
{
  "plantId":"30001",
  "buildingType":"厂房",
  "plantCode":"BLD001",
  "plantName":"一号厂房",
  "floorInfo":"3层",
  "landArea":1200,
  "buildingArea":3600,
  "address":"示例路1号",
  "builtYear":2018,
  "status":"0",
  "remark":""
}
```

新增时省略 `plantId`，`plantName` 必填。建筑用途按现有资料使用厂房、行政楼、宿舍楼等类别；仓库属于建筑内仓储地点，不作为建筑用途。面积单位按当前档案字段录入（平方米）。

删除前置条件：建筑没有未删除的组织节点关联；被车间/部门关联时后端拒绝删除，应先由员工处理组织关系。写入返回 `{"code":200,"msg":"操作成功","data":null}`，字段错误以服务端中文 `msg` 为准。
