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

## MCP 写入字段矩阵（契约迁移批次 1）

本矩阵覆盖款式创建、完整标量更新、关联集合、状态和删除工具。MCP Map 入参在转 DTO 前递归拒绝未知字段、数值 ID、错误枚举、重复集合成员及不完整的标量更新。所有 ID 必须是正十进制 JSON 字符串；示例 ID 均为格式示例，调用时从当前员工有权查看的查询结果复制。Controller 继续执行分类范围、客户状态、文件所有权、有效规格、套装完整性、业务引用、事务和审计检查。

### style_create.arguments.body 与 style_update.arguments.body 标量字段

更新是“完整标量对象 + 可选关联集合”：更新时下表标为“更新必填”的每个标量键都必须出现，不能只提交变化字段；创建时只要求款号、名称、分类。创建和更新均拒绝未列出的键。styleCode 创建后固定；更新时须原样提交查询所得款号。

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | null / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| arguments.body | object；{"styleCode":"ST001","name":"示例衬衣","categoryId":"10001"} | 创建、更新均必填 | null 拒绝；{} 因缺少必填字段拒绝 | 只接受本表和下表列出的款式字段；未知键拒绝 |
| arguments.body.styleCode | string；"ST001" | 创建必填；更新必填并复制现值 | null、空白拒绝 | 唯一款号，最长 60 字；创建后不可改 |
| arguments.body.name | string；"示例衬衣" | 创建和更新均必填 | null、空白拒绝 | 款式名称，最长 120 字；更新提交最终名称 |
| arguments.body.customerId | string 或 null；"50001" | 创建可省略（不关联客户）；更新必填 | null 清除客户关联；空白字符串、数字拒绝 | 客户 ID 从有效客户选项复制；服务端拒绝不存在或停用的客户 |
| arguments.body.categoryId | string；"10001" | 创建和更新均必填 | null、空白、数字拒绝 | 款式分类 ID；必须属于当前人员可维护范围，新增/改分类还须有效 |
| arguments.body.season | string 或 null；"春夏" | 创建可省略；更新必填 | null 或空白清除季节；空格会 trim | 允许春、夏、秋、冬、春夏、秋冬、四季；最长 40 字。历史已有值可原样保留 |
| arguments.body.description | string 或 null；"棉质长袖衬衣" | 创建可省略；更新必填 | null 或空白清空说明 | 款式说明，最长 1000 字；空格会 trim |
| arguments.body.coverOssId | string 或 null；"40001" | 创建可省略（无主图）；更新必填 | null 清除主图；空白、数字拒绝 | 文件 ID 从本款附件查询/上传结果复制；文件须是当前款式所属的私有图片，且不能被其他款式占用 |
| arguments.body.remark | string 或 null；"春季主推" | 创建可省略；更新必填 | null 或空白清空备注 | 备注最长 500 字；空格会 trim |
| arguments.body.productionMode | string；"single" | 创建可省略，默认 single；更新必填 | 创建传 null 使用 single；更新传 null 保留当前模式；空白及其他枚举值拒绝 | single 单款、combined 合并生产套装、separate 分开生产套装；切换模式会影响成员及单款规格，服务端还会检查下游引用 |
| arguments.body.sameHanger | boolean 或 null；false | 创建可省略，默认 false；更新必填 | 创建 null 默认 false；更新 null 保留当前值；字符串或数字拒绝 | 是否要求同架生产；只有 combined 可设为 true |

### arguments.body 关联集合与嵌套字段

创建时可省略关联集合，表示空集合/不配置；更新时省略或传 null 表示保留原集合（款式切换生产模式对成员/规格的约束见各行），传 [] 才表示该字段对应的最终集合为空。tagIds、attachments、setSpecs、members 分别独立处理；颜色、尺码和排除组合只适用于 single，必须同一请求一起省略或一起提交。切换到 combined/separate 时须省略这三项或将其置 null；传入非 null 数组会拒绝，避免误以为套装已保存单款规格。

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | null / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| arguments.body.tagIds | string 数组；["60001"] | 可选；省略/null 保留现有标签 | null 保留；[] 清除全部款式标签；非数组拒绝 | 最多 100 个不同 ID；从款式可选标签中复制，停用标签仅允许保留现有关联 |
| arguments.body.members | object 数组；[{"childId":"70001","roleName":"上衣","quantity":1}] | 可选；合并/分开生产套装首次创建必须至少提供一项；单款模式会清除成员 | null 或省略在套装更新中保留原组成；[] 不能用于非单款套装（至少 1 个子款）；切到单款会清空成员 | 最多 100 项；子款须为不同的有效单款、不可为自身或重复；修改前先读 style_configuration 并保留完整最终组成 |
| arguments.body.members[].childId | string；"70001" | 每个成员必填 | null、空白、数字拒绝 | 子款 ID 从当前可见单款档案复制；受分类权限和有效状态校验 |
| arguments.body.members[].roleName | string；"上衣" | 每个成员必填 | null、空白拒绝 | 子款在套装中的角色名称，最长 60 字 |
| arguments.body.members[].quantity | integer；1 | 每个成员必填 | null、小数、字符串拒绝；必须大于 0 | 每套包含该子款的件数，范围 1 至 2,147,483,647 |
| arguments.body.colorIds | string 数组；["20001"] | 可选；必须与 sizeIds、excludedPairs 同时省略或同时提供 | null 与省略表示保留现有规格；[] 在三项都为空数组时清除颜色 | 单款颜色最终集合，最多 100 个不同颜色 ID；从规格选项复制；停用值仅可保留现有关联 |
| arguments.body.sizeIds | string 数组；["30001"] | 可选；必须与 colorIds、excludedPairs 同时省略或同时提供 | null 与省略表示保留现有规格；[] 在三项都为空数组时清除尺码 | 单款尺码最终集合，最多 100 个不同尺码 ID；从规格选项复制 |
| arguments.body.excludedPairs | object 数组；[{"colorId":"20001","sizeId":"30001"}] | 可选；必须与 colorIds、sizeIds 同时省略或同时提供 | null 与省略表示保留；[] 表示最终没有排除组合 | 每项须由本请求最终颜色和尺码组成，重复组合拒绝；最多 10,000 项 |
| arguments.body.excludedPairs[].colorId | string；"20001" | 每项必填 | null、空白、数字拒绝 | 该排除组合的颜色 ID，必须在 colorIds 中 |
| arguments.body.excludedPairs[].sizeId | string；"30001" | 每项必填 | null、空白、数字拒绝 | 该排除组合的尺码 ID，必须在 sizeIds 中 |
| arguments.body.attachments | object 数组；[{"ossId":"40002","category":"style_image"}] | 可选；省略/null 保留附件 | null 保留；[] 移除全部附件关联 | 最多 101 个不同文件；完整数组是最终附件集合。文件必须是本款已有文件或当前员工上传的私有文件 |
| arguments.body.attachments[].ossId | string；"40002" | 每项必填 | null、空白、数字拒绝 | 私有文件 ID；同一附件集合内不能重复 |
| arguments.body.attachments[].category | string；"style_image" | 每项必填 | null、空白、其他枚举值拒绝 | 枚举：style_image、main_label、size_label、wash_label、hang_tag、tag_sticker、document；除 document 外只能关联图片文件 |
| arguments.body.setSpecs | object 数组；[{"colorName":"藏青","sizeName":"M","items":[]}] | 可选；省略/null 保留旧套装规格 | null 保留；[] 清空全部套装规格；单款不能保留非空规格 | 最多 100 项，作为最终集合替换；若旧规格有遗漏即删除，须先读 style_configuration |
| arguments.body.setSpecs[].id | string 或 null；"80001" | 更新既有规格时复制 ID；新增规格省略 | 省略/null 表示新增规格；空白、数字拒绝 | ID 必须属于当前款式且不能重复；省略已有规格 ID 会按新增处理，旧 ID 因不在最终集合中而删除 |
| arguments.body.setSpecs[].colorName | string；"藏青" | 每项必填 | null、空白拒绝 | 套装配色显示名称，最长 120 字 |
| arguments.body.setSpecs[].sizeName | string；"M" | 每项必填 | null、空白拒绝 | 套装标称尺码，最长 120 字；同一款式内配色与标称尺码组合不可重复 |
| arguments.body.setSpecs[].remark | string 或 null；"主面料色" | 可选；省略不填写 | null 或空白清空备注 | 套装规格备注，最长 500 字 |
| arguments.body.setSpecs[].items | object 数组；[{"childId":"70001","colorId":"20001","sizeId":"30001"}] | 每条规格必填 | null 或 [] 均会因未覆盖子款而拒绝 | 每条规格最多 100 个子款配套项；必须覆盖套装全部成员且颜色尺码属于子款有效规格 |
| arguments.body.setSpecs[].items[].childId | string；"70001" | 每个配套项必填 | null、空白、数字拒绝 | 必须是当前套装成员，且一条规格内不能重复 |
| arguments.body.setSpecs[].items[].colorId | string；"20001" | 每个配套项必填 | null、空白、数字拒绝 | 子款已选的有效颜色 ID；排除组合不可选 |
| arguments.body.setSpecs[].items[].sizeId | string；"30001" | 每个配套项必填 | null、空白、数字拒绝 | 子款已选的有效尺码 ID；排除组合不可选 |

### style_set_status 与 style_delete

| 字段路径 | JSON 类型/格式/示例值 | 必填条件与省略结果 | null / 空白 / 空集合结果 | 用途、来源、默认、校验与副作用 |
|---|---|---|---|---|
| style_set_status.arguments.id | string；"90001" | 必填；省略拒绝 | null、空白、数字拒绝 | 从 style_list 复制款式 ID；当前员工须能访问目标分类 |
| style_set_status.arguments.status | string；"1" | 必填；省略拒绝 | null、空白、数字拒绝；仅 "0"/"1" | "0" 启用、"1" 停用；状态变化可能影响后续引用 |
| style_delete.arguments.id | string；"90001" | 必填；省略拒绝 | null、空白、数字拒绝 | 从 style_list 复制款式 ID；有业务引用时服务端拒绝删除 |

### MCP arguments 示例

新增单款示例；示例颜色/尺码/分类 ID 必须替换为前序查询在当前权限下返回的实际字符串 ID。[] 表示本次不建立对应关联。

~~~json
{"name":"style_create","arguments":{"body":{"styleCode":"ST-DEMO-001","name":"示例衬衣","customerId":null,"categoryId":"10001","season":"春夏","description":"棉质长袖","coverOssId":null,"remark":"示例档案","tagIds":[],"productionMode":"single","sameHanger":false,"members":[],"colorIds":["20001"],"sizeIds":["30001"],"excludedPairs":[],"attachments":[],"setSpecs":[]}}}
~~~

完整更新示例。下例有意只替换标量字段并清空款式标签；未出现的关联集合会保留。按用户要求改变关联时，提交其完整最终集合。

~~~json
{"name":"style_update","arguments":{"id":"90001","body":{"styleCode":"ST-DEMO-001","name":"示例衬衣（春夏款）","customerId":"50001","categoryId":"10001","season":"春夏","description":"棉质长袖","coverOssId":"40001","remark":"已核对","productionMode":"single","sameHanger":false,"tagIds":[]}}}
~~~

状态调用示例：{"name":"style_set_status","arguments":{"id":"90001","status":"0"}}。删除示例：{"name":"style_delete","arguments":{"id":"90001"}}。状态和删除前均须向员工展示目标记录、当前与目标状态/删除影响，并取得本次明确确认；工具本身不会弹出确认窗口。

## MCP

代码提供 `style_list`、`style_category_options`、`style_spec_options`、`style_configuration`、`style_tags`、`style_create`、`style_update`、`style_set_status`、`style_delete`。工具继承当前员工登录态并显式检查对应权限，复用控制器的分类校验、事务及业务审计。每个款式写工具都要求调用前先读取目标，并向员工展示本次实际写入字段/关联及影响，取得本次明确确认后才能调用。文件上传与读取目前使用 REST 接口。2026-11-11 运行时已发现 9 个工具，查询以及临时款式的新增、删除调用成功，临时档案已清理；普通角色授权与撤权尚未实测。
