# 客户档案 API / MCP 契约

> 功能键：`customer_master`；表：`customer_master`、`customer_contact`、`customer_address`；一期用户验收通过（2026-11-05）。
> REST 与 MCP 均使用当前员工身份。客户联系方式只向有 `system:customer:edit` 的员工返回原值，其他有查询权限的员工收到脱敏值。

## 通用约定

- ID 在路径/MCP 入参使用十进制字符串；JSON bigint 安全范围内序列化为数字，超出安全范围序列化为字符串。
- 统一响应：`{"code":200,"msg":"操作成功","data":...}`；业务失败为 `code=500`、`data=null`、`msg` 给出原因；未登录 `401`、无权限 `403`。
- 写入以员工本人权限执行并记业务审计；写请求没有幂等键，超时后先查询确认，不盲目重试新增。
- 同名客户允许保存，重复检查只提供提示。停用客户仍可查询历史记录，但不出现在新业务引用选项中。

## MCP 工具

| 工具 | 权限 | 类型/约束 |
|---|---|---|
| `customer_list` | `system:customer:list` | 只读分页；联系方式脱敏策略按员工权限 |
| `customer_category_counts` | `system:customer:list` | 只读统计 |
| `customer_get` | `system:customer:list` | 详情；无编辑权限时联系方式脱敏 |
| `customer_check_duplicate` | `system:customer:list` | 只读同名检查，仅提醒 |
| `customer_sales_options` | `system:customer:list` | 只读业务员及组织/岗位/小组选项 |
| `customer_options` | `system:customer:list` | 只读启用客户选项 |
| `customer_create` | `system:customer:add` | 写；先展示客户、联系人、地址、标签并确认 |
| `customer_update` | `system:customer:edit` | 写；先展示完整变更并确认 |
| `customer_set_status` | `system:customer:edit` | 写；先确认目标状态 |
| `customer_delete` | `system:customer:remove` | 写；展示身份并确认；被业务引用时拒绝 |

## REST 接口

| 方法 + 路径 | 用途 / 参数 | 权限 | 机器契约 |
|---|---|---|---|
| `GET /system/customer/list` | keyword、status、salesId、tagId、pageNum、pageSize | `system:customer:list` | 只读幂等；无副作用；无需审批 |
| `GET /system/customer/category-counts` | 无 | `system:customer:list` | 只读幂等；无副作用；无需审批 |
| `GET /system/customer/{id}` | 客户详情、联系人、地址、标签 | `system:customer:list` | 只读幂等；按 edit 权限脱敏；无需审批 |
| `GET /system/customer/duplicates?name=...&id=0` | 同名数量；`id` 排除当前记录 | `system:customer:list` | 只读幂等；无副作用；无需审批 |
| `GET /system/customer/sales-options` | 业务员、组织、岗位、小组筛选数据 | `system:customer:list` | 只读幂等；返回非敏感人员信息；无需审批 |
| `GET /system/customer/options` | 启用客户选项 | `system:customer:list` | 只读幂等；无副作用；无需审批 |
| `POST /system/customer` | 新增客户及联系人/地址/标签 | `system:customer:add` | 非幂等；写主子表及审计；需员工确认 |
| `PUT /system/customer/{id}` | 更新主档并整体替换联系人、地址、标签集合 | `system:customer:edit` | 目标状态幂等、仍写审计；需员工确认 |
| `PUT /system/customer/{id}/status` | `{ "status":"0" }` 启用或 `1` 停用 | `system:customer:edit` | 状态幂等；保留历史；需员工确认 |
| `DELETE /system/customer/{id}` | 删除客户及其联系人/地址 | `system:customer:remove` | 非幂等；仅无业务外键引用时删除；需员工确认 |

### 新增/修改请求体

```json
{
  "customer_name":"示例客户",
  "customer_short_name":"示例",
  "unified_social_credit_code":"91310000XXXXXXXXXX",
  "country_region":"中国",
  "country_region_code":"CN",
  "sales_representative_id":"10001",
  "remark":"",
  "tagIds":["20001"],
  "contacts":[{"contact_name":"王女士","contact_title":"采购","phone_number":"13800000000","email":"buyer@example.com","contact_role":"采购联系人","is_primary":true}],
  "addresses":[{"address_type":"样衣收件","recipient_name":"王女士","recipient_phone":"13800000000","country_region":"中国","country_region_code":"CN","province":"上海市","city":"上海市","district":"浦东新区","address_line":"示例路1号","postal_code":"200000","is_default":true}]
}
```

`contacts` 和 `addresses` 可省略或为空；每类最多100条。联系人姓名/电话必填；每客户最多一个默认联系人。地址用途及详细地址必填；用途包含“样衣”时收件人和电话必填；同一用途最多一个默认地址。地区编码必须有效；原有停用地区或无法匹配的历史手填值可保留原样，不能把停用编码新增给其他客户/地址。

成功响应示例：`{"code":200,"msg":"操作成功","data":31001}`。错误包括必填/格式错误、无效业务员或标签、地区编码停用或不存在、重复默认项、客户被业务引用。

### 写操作机器契约

- 新增：非幂等；新增客户、联系人、地址、标签关系和审计记录；前置为 `system:customer:add`、名称/业务员有效、子项与地区约束通过；无需审批，执行前确认完整请求。
- 修改：重复提交同一目标状态不重复产生业务变化，但重写时间/审计；替换联系人、地址及标签集合；前置为记录存在和 `system:customer:edit`；无需审批，执行前展示各字段及子项变化。
- 删除：非幂等；删除客户及档案子项、标签关系；任何已声明业务外键引用时拒绝；前置为 `system:customer:remove`；无需审批，执行前确认客户编码和名称。
- 审计不得记录原始联系方式；日志对电话、邮箱脱敏。
