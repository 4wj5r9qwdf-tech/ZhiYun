---
name: zhiyun-organization-structure
description: 查询和维护织云 ERP 组织节点、组织树及业务管辖关系。用于员工明确提出组织架构查询或维护。
---

# 组织架构 Skill

字段类型、完整更新和 `parentId` 空值语义以[组织架构 API 的 MCP 字段矩阵](../../API/组织架构API.md#MCP-输入字段矩阵契约迁移批次-1)为准；MCP 适配器按该表校验 Map 内部字段后才转换为框架 DTO。

## 适用范围

查询及维护组织节点、组织树和业务管辖关系。基础功能已由用户验收通过（2026-11-05）；组织类别或层级扩展不包含在本验收结论中。

## 契约与工具

- API 契约：[组织架构API.md](../../API/组织架构API.md)
- MCP 工具：`org_list`、`org_get`、`org_options`、`org_create`、`org_update`、`org_delete`、`org_manages_get`、`org_manages_set`、`org_manage_counts`。
- 工具已加入后端代码；运行时发现状态需在后端启动后用 MCP 客户端核对。未发现工具时不得假称 MCP 可调用。
- JSON-RPC 调用外层、参数类型和 ID 规则见[织云 ERP MCP 调用指南](../MCP调用指南.md)。

## 查询调用示例

查询当前员工数据范围内的组织；按条件筛选时，筛选对象必须放在名为 `query` 的参数中：

```json
{"name":"org_list","arguments":{}}
```

```json
{"name":"org_list","arguments":{"query":{"orgName":"生产部","status":"0"}}}
```

### 写入 arguments 示例

新增节点前先读取父级、建筑和负责人选项；普通主档写入提交确认过的完整字段：

~~~json
{"name":"org_create","arguments":{"body":{"parentId":"10000","orgName":"一车间","orgCategory":"workshop","buildingId":"30001","location":"一号厂房/1层","orderNum":10,"leader":"20001","phone":"021-55550000","email":"workshop@example.com","status":"0"}}}
~~~

更新对象必须包含完整最终字段；`parentId:null` 表示明确移到根，建筑和负责人 ID 仍使用字符串：

~~~json
{"name":"org_update","arguments":{"body":{"orgId":"10001","parentId":null,"orgName":"一车间","orgCategory":"workshop","buildingId":"30001","location":"一号厂房/1层","orderNum":10,"leader":"20001","phone":"021-55550000","email":"workshop@example.com","status":"0"}}}
~~~

管辖关系是最终全集替换。先调用 org_manages_get 读取旧名单，确认新名单后调用；传空数组会清空全部关系：

~~~json
{"name":"org_manages_set","arguments":{"orgId":"10001","managedOrgIds":["10002","10003"]}}
~~~

字段级空值及 `org_update` 的父级规则见 API 字段矩阵。示例 ID 须换成当前员工查询得到的真实 ID；工具 `inputSchema` 将 Map 暴露为 object，适配器负责拒绝数字型 ID，运行时发现和普通员工权限尚待验收。

## 操作规则

- 查询和写入均遵守当前员工权限及组织数据范围，不得通过其他身份代办。
- 新增或移动节点前展示父级路径、类别、建筑及负责人；修改前展示差异并取得本次确认。
- 部门电话和部门邮箱分别对应 `phone`、`email`，负责人是 `leader` 用户 ID；不得把部门联系方式当作负责人个人电话或邮箱。部门邮箱当前不自动接收审批通知，选择部门审批匹配部门人员。
- 删除前确认目标没有子级、人员、岗位或管辖引用；遇到后端拒绝时不得绕过校验。
- `org_manages_set` 是全量替换，必须显式传最终 ID 数组；空数组会清空关系，缺失或 `null` 会被拒绝。执行前查询旧名单，展示完整新旧清单并单独确认。
- 写操作超时后先查询核实，不盲目重试。
