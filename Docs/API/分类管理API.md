# 分类管理 API 与 MCP 契约

> 版本：v1
> 更新日期：2026-11-05
> 说明：实现完成、待用户验收。表为 `base_category_scheme`、`base_category`；功能键 `category`。各业务独立体系，共享管理能力。

## 一、公共契约

认证与响应沿用API总索引；员工本人token与clientid，ID均传十进制字符串。成功 `code=200`，数据在 `data`；权限403、未登录401、业务约束500并返回中文原因。管理目录全量返回，页面完整筛选分页，不存在只筛当前页。

| 方法/路径 | 用途 | 权限 | 机器契约 |
|---|---|---|---|
| GET `/system/category/schemes` | 全部体系 | `system:category:list` | 幂等、无副作用、已登录且有权限、无需审批 |
| GET `/system/category/scheme-options` | 公共体系选项 | 已登录 | 幂等、无副作用、包含停用项供历史显示、无需审批 |
| GET `/system/category/list?schemeId=...` | 单体系完整平铺树节点 | `system:category:list` | 幂等、无副作用、schemeId必填、无需审批 |
| GET `/system/category/options?schemeId=...` | 单体系业务选项 | 已登录 | 幂等、无副作用、schemeId必填、按整条父链及体系标记selectable、无需审批 |
| POST `/system/category/schemes` | 新增体系 | `system:category:add` | 非幂等、写体系与审计、编码/名称唯一、无审批但智能体须确认 |
| PUT `/system/category/schemes/{id}` | 完整更新/启停体系 | `system:category:edit` | 目标值幂等但写审计/时间、编码固定且深度不破坏现有树、须确认 |
| DELETE `/system/category/schemes/{id}` | 删除空体系 | `system:category:remove` | 非幂等、逻辑删除及审计、没有未删除节点或外部引用、须确认 |
| POST `/system/category` | 新增分类 | `system:category:add` | 非幂等、写分类与审计、体系/父级有效且唯一和深度通过、须确认 |
| PUT `/system/category/{id}` | 完整更新/移动/启停分类 | `system:category:edit` | 目标值幂等但写审计/时间、不能跨体系/成环/超深、须确认 |
| DELETE `/system/category/{id}` | 删除无引用叶节点 | `system:category:remove` | 非幂等、逻辑删除及审计、无未删除下级及外部引用、须确认 |

## 二、体系参数

| 字段 | 类型/必填/默认 | 中文 / English |
|---|---|---|
| code | string，必填，最多40 | 稳定体系编码，字母数字开头及`_.-`，创建后不可修改 / Stable scheme code |
| name | string，必填，最多80 | 体系名称 / Scheme name |
| maxDepth | integer，可选，默认5，范围1–20 | 最大层级，根节点计1级 / Maximum tree depth |
| sortOrder | integer，可选，默认0，范围0–999999 | 显示排序 / Display order |
| status | string，可选，默认0 | 0启用、1停用；停用体系让全树不可新引用 / Availability |
| remark | string，可选，默认空，最多500 | 备注 / Business notes |

```json
{"code":"material","name":"物料分类","maxDepth":5,"sortOrder":0,"status":"0","remark":"供物料档案使用"}
```

## 三、分类参数

| 字段 | 类型/必填/默认 | 中文 / English |
|---|---|---|
| schemeId | string，必填 | 体系ID，创建后不可迁移 / Stable scheme ID |
| parentId | string或null，可选 | 同体系父级ID，null表示根 / Optional parent ID |
| code | string，必填，最多40 | 体系内唯一分类编码 / Unique node code within scheme |
| name | string，必填，最多80 | 同父级名称唯一 / Sibling-unique category name |
| sortOrder | integer，可选，默认0，范围0–999999 | 显示排序 / Display order |
| status | string，可选，默认0 | 0启用、1停用 / Lifecycle status |
| remark | string，可选，默认空，最多500 | 备注 / Business notes |

```json
{"schemeId":"101","parentId":null,"code":"FABRIC","name":"面料","sortOrder":0,"status":"0","remark":""}
```

上例ID应替换为体系创建真实返回值。新增和更新都返回 `{"code":200,"msg":"操作成功","data":"102"}`。删除成功 `data:null`。

体系查询 `data` 为包含 `id` 和所有体系参数的数组；节点列表 `data` 包含 `id/schemeId/parentId/code/name/sortOrder/status/remark`。选项另外返回 `selectable:boolean`，停用体系/任意停用祖先/节点都使其false，历史仍显示。

```json
{"code":200,"msg":"操作成功","data":[{"id":"102","schemeId":"101","parentId":null,"code":"FABRIC","name":"面料","sortOrder":0,"status":"0","remark":"","selectable":true}]}
```

## 四、错误、并发与历史

编码/同级名称冲突、体系不存在或停用、跨体系父级、自为父级或移动到后代、子树深度超限、降低体系深度破坏现有树、启用停用父级下节点均拒绝。停用父级需先停用下级；停用体系可统一阻止新引用。

示例：`{"code":500,"msg":"上级分类不能是自身或下级，禁止循环","data":null}`。分类被业务外键引用时不允许删除，只可停用。业务保存必须再校验体系和节点可用性，不能信任前端选项。

节点写入按体系行锁串行化，两个并发反向移动不会同时成功。重复提交无业务幂等键，超时先查询编码/ID确认结果。CSV按父级在前逐条调用创建，部分成功可保留，失败逐条反馈；不宣称整个导入原子事务。

## 五、MCP 与审计

- 体系：`category_schemes`、`category_scheme_options`、`category_scheme_create`、`category_scheme_update`、`category_scheme_delete`。
- 节点：`category_list`、`category_options`、`category_create`、`category_update`、`category_delete`。
- 查询列表/选项使用 `schemeId:string`；新增和更新用 `body:object`，更新/删除另有 `id:string`，对象字段见对应参数表。
- 写前展示目标体系、路径、变化及停用影响并确认，使用员工本人权限；记录对象身份、层级关系及字段前后值。
- 工具已实际发现；体系/节点的新增、更新、查询和删除均通过MCP实际调用，低权限写入拒绝亦已验证。
