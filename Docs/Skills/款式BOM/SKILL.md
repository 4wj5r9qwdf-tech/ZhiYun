---
name: style-bom
description: 查询、核对及维护织云 ERP 款式 BOM。适用于单款及套装子款用料。
---

先确认款号和 BOM 版本，再以当前员工身份调用工具。查询需要 `rd:bom:list`，保存和发布还需要 `rd:bom:edit`。使用 `style_bom_list` 查版本，`style_bom_detail` 取完整规则和修订标识，`style_bom_coverage` 核对规格覆盖。使用 `style_bom_preview` 按有效色码或套装规格试算用量。

保存前确认单据、目标颜色尺码、用料和单位。用 `style_bom_save` 提交最终完整规则，编辑时携带最新修订标识。默认保存草稿，明确确认发布后才提交 `release=true`。发布版只读，修订冲突时重新读取并与用户核对差异。不得绕过人员分类范围或物料可见权限。

操作字段与权限详见 [款式 BOM API](../../API/款式BOMAPI.md)。
