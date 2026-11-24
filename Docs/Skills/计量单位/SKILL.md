---
name: zhiyun-unit-management
description: 查询和维护织云 ERP 全局计量单位、固定换算关系及换算规则。用于员工明确提出单位目录或换算配置操作。
---

# 计量单位

> 版本：v1
> 更新日期：2026-11-21
> 说明：2026-11-05 用户验收通过；MCP已实际发现，目录/选项及单位新增、修改、删除已实际调用验证；低权限写入拒绝已验证。

字段类型、空值、换算方向及更新完整字段以[计量单位 API 的 MCP 字段矩阵](../../API/计量单位API.md#MCP-输入字段矩阵契约迁移批次-1)为准；本 Skill 只编排调用流程。

## 适用与契约

用于全局单位查询、固定换算与单位建档维护，完整字段见 `Docs/API/计量单位API.md`。工具 `unit_list/options/convert/create/update/delete`（完整工具名分别为 `unit_list`、`unit_options`、`unit_convert`、`unit_create`、`unit_update`、`unit_delete`）。

调用参数名与 ID 规则见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

先读取单位目录，并从结果复制实际单位 ID；`unit_convert` 的 `fromId` 是待换算单位，`toId` 是库存/参照单位：

```json
{"name":"unit_list","arguments":{}}
```

```json
{"name":"unit_convert","arguments":{"fromId":"123456789012345678","toId":"123456789012345679","quantity":"125.5"}}
```

示例 ID 仅展示十进制字符串格式；调用时替换为同一有效换算关系中的真实 ID。

## 写入参数示例

```jsonl
{"name":"unit_create","arguments":{"body":{"code":"CM","name":"厘米","symbol":"cm","dimension":"length","decimalPlaces":3,"baseUnitId":"101","factor":"0.01","sortOrder":1,"status":"0","remark":"1厘米等于0.01米"}}}
{"name":"unit_update","arguments":{"id":"102","body":{"code":"CM","name":"厘米","symbol":"cm","dimension":"length","decimalPlaces":3,"baseUnitId":"101","factor":"0.01","sortOrder":1,"status":"0","remark":"1厘米等于0.01米"}}}
{"name":"unit_delete","arguments":{"id":"102"}}
```

参照 ID 必须从选项复制为字符串；系数也必须是精确十进制字符串。`unit_update` 是完整替换：先读取当前单位，再明确回填参照、系数、精度、状态和所有显示字段，尤其不要把现有换算关系省略掉。

## 执行步骤

1. 以当前员工权限读取目录，确认编码、计量维度、独立参照及精度；不猜测箱/卷/米/公斤关系。
2. 单位换算按“待换算单位数量 → 库存单位数量”解释；管理页面的库存单位仅可选启用的独立参照单位。调用 `unit_convert` 时，`fromId` 为待换算单位，`quantity` 为该单位下的数量，`toId` 为库存单位；确认共同参照和启用状态，数量传十进制字符串，并解释库存单位精度与舍入。
3. 管理页面“批量设置换算系数”选择已有单位并逐行更新；新增库存单位时可同时逐行创建换算单位。系数方向是“1个换算单位 = 系数 × 库存单位”，例如米为库存单位时厘米填 `0.01`、毫米填 `0.001`。部分成功时只修正/重试失败行，不能声称整个批次原子回滚；已有引用的单位遵守服务端修改保护。
4. 新增/修改/启停/删除前，展示完整字段、关联单位、实际变化及影响，取得本次员工确认后执行对应工具。
5. 失败按中文业务原因处理，引用保护不可绕过；超时先重新查编码/ID确认结果，不盲目重复新增。
6. 物料专属包装/重量长度换算不在公共单位中假定，交由对应业务维护。

报告数量时保留 API 原值并注明单位。数量显示位数以及数字显示参数仅用于呈现，不能将显示结果作为后续工具的写入值。
