---
name: zhiyun-building-record
description: 查询和维护织云 ERP 建筑档案，包括用途、编码、楼层、面积、地址和状态。
---

# 建筑档案 Skill

字段类型、空值和完整更新规则以[建筑档案 API 的 MCP 字段矩阵](../../API/建筑档案API.md#MCP-输入字段矩阵契约迁移批次-1)为准；本 Skill 只负责调用编排。

## 适用范围

查询和维护建筑用途、编码、名称、楼层、占地/建筑面积、地址及状态。建筑档案已由用户验收通过（2026-11-05）。

## 契约与工具

- API 契约：[建筑档案API.md](../../API/建筑档案API.md)
- MCP 工具：`building_list`、`building_get`、`building_create`、`building_update`、`building_delete`。
- 工具已加入后端代码；运行时发现状态需在后端启动后用 MCP 客户端核对。未发现工具时不得假称 MCP 可调用。
- 通用 MCP 参数和调用外层示例见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

按名称/编码/地址关键词和启用状态筛选建筑；其他可选条件省略：

```json
{"name":"building_list","arguments":{"keyword":"厂房","status":"0"}}
```

## 写入参数示例

```jsonl
{"name":"building_create","arguments":{"body":{"buildingType":"厂房","plantCode":"BLD001","plantName":"一号厂房","floorInfo":"3层","landArea":1200,"buildingArea":3600,"address":"示例路1号","builtYear":2018,"status":"0","remark":"建筑示例"}}}
{"name":"building_update","arguments":{"body":{"plantId":"30001","buildingType":"厂房","plantCode":"BLD001","plantName":"一号厂房","floorInfo":"3层","landArea":1200,"buildingArea":3600,"address":"示例路1号","builtYear":2018,"status":"2","remark":"维修"}}}
{"name":"building_delete","arguments":{"plantId":"30001"}}
```

ID 只用十进制字符串；`building_update` 先读现值并提交所有字段，空值按 API 矩阵处理。建筑状态为 `0` 在用、`1` 闲置、`2` 维修；涉及删除时先检查组织关联并展示影响。

## 操作规则

- 写入前展示建筑编码、名称、用途、面积、地址与状态变化并取得本次确认。
- 删除前确认没有组织节点等关联；有关联或后端拒绝时停止，不得自动迁移组织关系。
- 建筑是物理楼宇档案；仓库是建筑内部区域，两者不得互相替代。
- 写操作超时后先查询核实，不盲目重试。
