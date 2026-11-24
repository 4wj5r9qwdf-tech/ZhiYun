---
name: zhiyun-style-management
description: 织云款式档案的查询、单款和套装维护、启停与删除，适用于研发及后续样衣、订单对款式的引用。
---

# 款式档案

使用当前员工身份，通过款式 MCP 工具或已授权 REST 接口操作；遵守人员分类范围及款式页面、增改删权限。开始前查询款式、分类、规格和关联数据，明确用户指向的款号与名称。

单款保存可用颜色、尺码和排除组合。尺码组只用于带入具体尺码；套装记录子款、子款类别和每套件数。合并生产可设置同架要求，分开生产按子款管理。套装和子款均为独立档案，保存子款不代表套装草稿已保存。

修改时先读取原始配置与标签，保留用户未要求改变的字段和关联；涉及其他子款不可见时停止维护并提示联系管理员。文件经授权接口上传和读取，关联与主图必须属于当前款式。新增、修改、启停、删除前，先向用户展示款号/名称、实际字段变化、关联增删和影响，并等待用户对本次操作明确确认后再调用写工具；一次确认不能复用于其他操作。款式 MCP 不会自行弹出确认窗口。存在业务引用时遵循后端拒绝删除的结果。单据、BOM、工艺、裁片、吊挂和套装库存属于后续业务，不视为本档案已实现。

工具名称和参数见 [款式档案 API](../../API/款式档案API.md)。当前 MCP 工具为 `style_list`、`style_category_options`、`style_spec_options`、`style_configuration`、`style_tags`、`style_create`、`style_update`、`style_set_status`、`style_delete`。工具发现与调用能力以当前环境实际结果为准。

通用 MCP 参数格式见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 写入字段契约与调用示例

字段、类型、必填条件、null/空白/空数组行为及嵌套对象约束以 [款式档案 API 写入字段矩阵](../../API/款式档案API.md#mcp-写入字段矩阵契约迁移批次-1) 为准。MCP 会拒绝数字型 ID、未列字段和不完整更新标量对象。更新前先读主档及 configuration/tags；只要修改了任一关联，就提交该关联的完整最终集合。颜色、尺码、排除组合须一起省略或一起提交。

新增单款示例：

~~~~json
{"name":"style_create","arguments":{"body":{"styleCode":"ST-DEMO-001","name":"示例衬衣","customerId":null,"categoryId":"10001","season":"春夏","description":"棉质长袖","coverOssId":null,"remark":"示例档案","tagIds":[],"productionMode":"single","sameHanger":false,"members":[],"colorIds":["20001"],"sizeIds":["30001"],"excludedPairs":[],"attachments":[],"setSpecs":[]}}}
~~~~

修改示例：更新必须提交所有标量字段。仅修改标量时省略关联集合会保留原值；按此示例传 tagIds 空数组会清除全部款式标签。所有示例 ID 均须替换为当前员工可见查询结果中的字符串 ID。

~~~~json
{"name":"style_update","arguments":{"id":"90001","body":{"styleCode":"ST-DEMO-001","name":"示例衬衣（春夏款）","customerId":"50001","categoryId":"10001","season":"春夏","description":"棉质长袖","coverOssId":"40001","remark":"已核对","productionMode":"single","sameHanger":false,"tagIds":[]}}}
~~~~

状态调用：{"name":"style_set_status","arguments":{"id":"90001","status":"0"}}。删除调用：{"name":"style_delete","arguments":{"id":"90001"}}。每次写入均先展示目标、字段/最终关联变化和影响，再取得该次明确确认。

## 查询调用示例

先查询当前员工可见款式，再用查询结果中的实际款式 ID 读取颜色、尺码及套装子款配置：

```json
{"name":"style_list","arguments":{}}
```

```json
{"name":"style_configuration","arguments":{"id":"123456789012345678"}}
```

示例 ID 仅表示十进制字符串格式，实际调用必须复制 `style_list` 返回的款式 ID。

维护套装规格时，先调用 `style_configuration` 读取套装组成和现有 `setSpecs`，再读取各子款的有效颜色尺码。修改时保留已有规格 ID，并核对最终集合中每条规格都包含全部子款；不改规格时省略该字段，避免误清空。配色名称和套装标称尺码用于识别整套，子款实际配色配码以明细为准。
