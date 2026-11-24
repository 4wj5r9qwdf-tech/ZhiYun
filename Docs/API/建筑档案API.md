# 建筑档案 API / MCP 契约

> 功能键：`building`；表：`sys_building`；作为物业资产中的建筑实体，不是组织节点。用户验收通过（2026-11-05）。
> 契约迁移：2026-11-21 已补 MCP 字段矩阵；MCP 内部以字符串接收建筑 ID，再适配旧控制器的 Number 参数；建筑状态筛选补齐 0/1/2 三种档案值。

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

### 列表筛选约束

`keyword`、`buildingType`、`status` 可省略；`status` 仅允许字符串 `"0"`（在用）、`"1"`（闲置）或 `"2"`（维修）。其他非空状态值会返回明确的参数错误，避免筛选拼错后误读为空结果。

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

新增时省略 `plantId`，`plantName` 必填。建筑用途按现有资料使用厂房、行政楼、宿舍楼等类别；仓库属于建筑内仓储地点，不作为建筑用途。面积单位按当前档案字段录入（平方米）。建筑状态为 `0` 在用、`1` 闲置、`2` 维修。

删除前置条件：建筑没有未删除的组织节点关联；被车间/部门关联时后端拒绝删除，应先由员工处理组织关系。写入返回 `{"code":200,"msg":"操作成功","data":null}`，字段错误以服务端中文 `msg` 为准。

## MCP 输入字段矩阵（契约迁移批次 1）

`building_create.body` 与 `building_update.body` 在 inputSchema 中为自由 object，适配器会按白名单、字段类型和完整更新要求校验；`plantId` 在进入旧控制器前由正十进制字符串转换为 Long。下面的 `arguments` 是 MCP 结构。

| 字段路径 | JSON 类型 / 示例 | 必填与省略结果 | `null` / 空白 / 空集合结果 | 用途、来源、约束与副作用 |
|---|---|---|---|---|
| `building_list.keyword` | string；`"一号厂房"` | 可选；省略不筛选 | null/空白不筛选 | 模糊匹配编码、名称、地址 |
| `building_list.buildingType` | string；`"厂房"` | 可选；省略查全部用途 | null/空白不筛选 | 按建筑用途筛选；建议值取现有建筑目录 |
| `building_list.status` | string；`"2"` | 可选；省略查全部状态 | null/空白不筛选；仅 `"0"`/`"1"`/`"2"` 有效，数字类型拒绝 | 0在用、1闲置、2维修；状态过滤在后端执行 |
| `building_get.plantId`、`building_delete.plantId` | string 正十进制 ID；`"30001"` | 必填；从 `building_list` 复制 | null/空白/数字/非正 ID 拒绝 | 所有 ID 保留字符串，删除为软删除且有组织关联时拒绝 |
| `building_create.body` | object；示例见下文 | 必须含 `plantName`；其他业务字段可省略 | null/空对象拒绝；未知键与 `plantId` 拒绝 | 新增建筑且无幂等键；超时先查询核实，不盲目重复创建 |
| `building_update.plantId` | string 正十进制 ID；`"30001"` | 必填 | null/空白/JSON 数字/非法 ID 拒绝 | 从建筑查询复制；MCP 适配器转换为当前 REST 控制器要求的数值 ID |
| `building_update.body` | object；包含 `plantId` 与其余10个字段 | 全部11个键必须出现；先读取现值并回填 | null/空对象/缺键/未知键拒绝 | 完整替换建筑档案；省略字段会拒绝，避免控制器用 null 覆盖现值 |
| `body.buildingType` | string；`"厂房"` | 新增省略默认 `"厂房"`；更新必传 | null 可清空；空白可存为空文本 | 用途类别，不是仓库类型 |
| `body.plantCode` | string；`"BLD001"` | 可选；更新须显式提交 | null/空白清除；当前无唯一性校验 | 建筑业务编码 |
| `body.plantName` | string；`"一号厂房"` | 必填；省略/null/空白拒绝 | trim 检查非空；适配器保留原字符串 | 建筑名称 |
| `body.floorInfo` | string；`"3层"` | 新增可省略；更新必传 | null/空白清除 | 总楼层/楼层说明；仓库楼层选项依赖此值 |
| `body.landArea` | number；`1200` | 新增可省略；更新必传 | null 表示未维护/清空；字符串拒绝 | 占地面积，平方米；数据库 `numeric(12,2)` |
| `body.buildingArea` | number；`3600` | 新增可省略；更新必传 | null 表示未维护/清空；字符串拒绝 | 建筑面积，平方米；数据库 `numeric(12,2)` |
| `body.address` | string；`"示例路1号"` | 新增可省略；更新必传 | null/空白清除 | 地址文本 |
| `body.builtYear` | integer或null；`2018` | 新增可省略；更新必传 | null 清空；小数/字符串拒绝 | 建成年份，作为整数保存 |
| `body.status` | string；`"0"` | 新增可省略并按控制器默认0；更新必填 | null拒绝；仅字符串 `"0"`、`"1"`、`"2"` | 0在用、1闲置、2维修；闲置/维修记录不作为仓库建筑选项 |
| `body.remark` | string；`"主厂房"` | 新增可省略；更新必传 | null/空白清除 | 补充备注 |

写权限分别是 `system:building:add/edit/remove`；列表/详情需对应 `system:building:list/query`。建筑创建和更新均记录业务审计，更新前应展示全部字段差异；删除有组织关联时服务端拒绝，不自动调整组织。

### MCP `arguments` 示例

```jsonl
{"name":"building_list","arguments":{"keyword":"厂房","status":"2"}}
{"name":"building_create","arguments":{"body":{"buildingType":"厂房","plantCode":"BLD001","plantName":"一号厂房","floorInfo":"3层","landArea":1200,"buildingArea":3600,"address":"示例路1号","builtYear":2018,"status":"0","remark":"建筑示例"}}}
{"name":"building_update","arguments":{"body":{"plantId":"30001","buildingType":"厂房","plantCode":"BLD001","plantName":"一号厂房","floorInfo":"3层","landArea":1200,"buildingArea":3600,"address":"示例路1号","builtYear":2018,"status":"2","remark":"维修"}}}
{"name":"building_delete","arguments":{"plantId":"30001"}}
```

所有 ID 示例都必须替换成 `building_list`/详情的实际 ID。更新示例中的 null 字段表示明确清空；调用前必须先读当前值并向员工展示完整变化。
