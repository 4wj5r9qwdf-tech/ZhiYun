# 款式档案 API

款式档案一期于 2026-11-11 用户验收通过。研发、样衣及订单共用款式档案；后续单据、技术资料和吊挂对接需分别建设。接口根路径为 `/rd/style`，ID 使用十进制字符串传输，服务端依据当前人员的分类范围过滤与校验。

## 接口与权限

所有接口要求 `rd:style:list`；写接口还需相应操作权限。

| 方法和路径 | 作用 | 附加权限 |
|---|---|---|
| GET /list | 查询可见款式、标签、颜色尺码及可见套装组成；`membersRestricted` 指示存在不可见子款 | — |
| GET /category-options | 可见分类及路径祖先 | — |
| GET /default-category | 返回页面配置类型及不可用标记 | — |
| GET /customer-options | 可选客户 | — |
| GET /spec-options | 颜色、尺码和尺码组 | — |
| GET /{id}/configuration | 单款规格、套装组成与套装规格；`restricted` 指示子款不可见 | — |
| GET /{id}/tags | 已关联标签及分组 | — |
| POST / | 新增款式及最终关联 | `rd:style:add` |
| PUT /{id} | 修改款式及最终关联 | `rd:style:edit` |
| PUT /{id}/status | 传 `status`，0 启用、1 停用 | `rd:style:edit` |
| DELETE /{id} | 删除无引用款式，存在引用时拒绝 | `rd:style:remove` |
| POST /attachments | multipart 文件上传至私有存储，单文件最大 10 MB | 新增或编辑 |
| GET /{id}/attachments | 查询附件信息 | — |
| GET /{id}/attachments/{ossId} | 通过款式和附件归属校验后读取私有文件 | — |
| POST /cover | 兼容主图上传，PNG/JPEG 最大 5 MB | 新增或编辑 |
| GET /{id}/cover | 按款式权限读取主图 | — |

## 写入字段

`styleCode` 是必填唯一款号，创建后固定；`name`、`categoryId` 必填。客户 `customerId`、季节 `season`、说明 `description` 和 `remark` 可选。季节使用春、夏、秋、冬、春夏、秋冬、四季，历史值可保留。标签 ID 用 `tagIds`。

`productionMode` 为 `single`（单款）、`combined`（合并生产套装）或 `separate`（分开生产套装）。`sameHanger` 仅适用于合并生产。套装的 `members` 包含 `childId`、`roleName` 和每套正整数 `quantity`；单款以 `colorIds`、`sizeIds` 和 `excludedPairs`（每项含 `colorId`、`sizeId`）记录规格。详情读取后应保留已有项目，按最终集合提交。

套装配置通过 `setSpecs` 读写。每条含 `id`（新增不传、修改保留）、`colorName`（配色名称）、`sizeName`（套装标称尺码）、`remark`、`items`。明细提交 `childId`、`colorId`、`sizeId`，读取时另带当前组成的每套件数 `quantity`。各 ID 使用十进制字符串。提交最终集合时，遗漏的旧规格会移除；省略字段或传 null 保留旧规格，空数组表示清空。每条规格必须覆盖全部子款，使用其有效颜色尺码组合，且配色名称与标称尺码的组合不可重复。子款超出分类权限时，配置返回 `restricted=true` 和空规格集合，不得据此提交清空。

现有 MCP 工具 `style_configuration` 返回上述字段，`style_create` 和 `style_update` 通过同一请求对象接收该字段。

`attachments` 为文件 ID 和类型的集合，类型为 `style_image`、`main_label`、`size_label`、`wash_label`、`hang_tag`、`tag_sticker` 或 `document`；`coverOssId` 是当前款式图片中的主图。先上传再保存关联，私有附件受归属和分类权限保护。历史数据库中停用的 `customer_code` 列保留原数据，不再作为款式业务字段读写。

页面设置的款式默认分类类型限制页面展示范围，人员分类权限由服务端独立执行；留空时页面可展示授权范围内的分类。不可用配置应提示管理员核对配置或权限。

## MCP

代码提供 `style_list`、`style_category_options`、`style_spec_options`、`style_configuration`、`style_tags`、`style_create`、`style_update`、`style_set_status`、`style_delete`。工具继承当前员工登录态并显式检查对应权限，复用控制器的分类校验、事务及业务审计。文件上传与读取目前使用 REST 接口。2026-11-11 运行时已发现 9 个工具，查询以及临时款式的新增、删除调用成功，临时档案已清理；普通角色授权与撤权尚未实测。
