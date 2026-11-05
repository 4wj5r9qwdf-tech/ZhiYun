# 分类管理

> 版本：v1
> 更新日期：2026-11-05
> 说明：功能已实现、待用户验收；MCP已发现，体系和节点的查询/新增/修改/删除已实际调用验证。

## 适用与契约

用于独立业务分类体系及节点管理，字段见 `Docs/API/分类管理API.md`。工具 `category_schemes`、`category_scheme_options`、`category_scheme_create/update/delete`、`category_list`、`category_options`、`category_create/update/delete`；斜杠表示各自独立工具，如 `category_create`、`category_update`、`category_delete`。

## 执行步骤

1. 查询体系，明确本次业务范围；未指定体系时先定位，不把物料/款式分类混为一棵树。
2. 查询完整节点，确认父级路径、层级限制和状态；历史可显示不等于可新增引用。
3. 写入前展示体系、编码、目标路径和变化。停用体系需说明整棵分类树不能再被新业务选择；确认后按员工本人权限执行。
4. 不能跨体系移动，不能成环或超深；删除必须是无业务引用叶节点，批量删除/停用子级优先，启用父级优先。
5. 超时先核实结果，部分成功逐项报告，不绕过服务端引用/权限保护。体系编码创建后不改。
