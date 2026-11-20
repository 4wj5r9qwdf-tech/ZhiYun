---
name: style-bom
description: 查询、核对及维护织云 ERP 款式 BOM。适用于单款及套装子款用料。
---

先确认款号和 BOM 版本，再以当前员工身份调用工具。查询需要 `rd:bom:list`，保存和发布还需要 `rd:bom:edit`。使用 `style_bom_list` 查版本，`style_bom_detail` 取完整规则和修订标识，`style_bom_coverage` 核对规格覆盖。使用 `style_bom_preview` 按有效色码或套装规格试算用量。

保存前确认单据、目标颜色尺码、用料和单位。用 `style_bom_save` 提交最终完整规则，编辑时携带最新修订标识。默认保存草稿，明确确认发布后才提交 `release=true`。发布版只读，修订冲突时重新读取并与用户核对差异。不得绕过人员分类范围或物料可见权限。

操作字段与权限详见 [款式 BOM API](../../API/款式BOMAPI.md)。

通用 MCP 请求结构、参数类型及空值处理见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

`style_bom_list` 的 `styleId` 是必填正十进制字符串，必须从 `style_list` 返回值中复制，不能传款号文本。所有必填 ID 都遵循此格式；可选规格、颜色、尺码 ID 可以省略或传空串，若传值也必须是正十进制字符串：

```json
{"name":"style_bom_list","arguments":{"styleId":"123456789012345678"}}
```

单款 BOM 试算时传 `colorId`、`sizeId`；套装试算改传 `setSpecId`。不适用的可选字段直接省略：

```json
{"name":"style_bom_preview","arguments":{"id":"123456789012345679","count":100,"colorId":"123456789012345680","sizeId":"123456789012345681"}}
```

以上 ID 仅为格式示例，实际调用使用已查询到的有效 ID。单款颜色尺码和套装规格参数不能混用。
