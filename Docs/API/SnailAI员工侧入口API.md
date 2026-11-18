# SnailAI 员工侧入口 API

> 版本：v1
> 更新日期：2026-11-17
> 状态：接口代码与后端打包完成，ERP 后端已重启且 SnailAI Agent 服务已启动；实际员工会话下的模型回复、MCP 工具发现/调用及页面验收仍待验证。

> 2026-11-17 二开：已增加 ERP 侧对话诊断管理接口及页面，权限为 `system:snailai:trace:list`，不默认授权普通角色。记录新产生的聊天轮次、附件元信息、可见工具和工具执行状态；工具参数和结果正文暂不保存，异常只记录受限信息。历史记录不回填。实际模型调用、授权、并发及页面验收仍待验证。原始文档及语音仍待智能体后台接入，目标和阶段见《功能任务/ZY-ERP-20261117-单智能体二开.md》。

## 1. 认证与身份

- 所有接口要求已登录的 ERP 员工会话，沿用 ERP 的 Authorization 与 clientid 认证。
- 浏览器不能提交 openId、ERP 用户 ID、权限列表或 MCP 请求凭证。后端从当前 Sa-Token 会话取得用户 ID、昵称、token、clientid 和权限。
- SnailAI OpenAPI 用户以 ERP 用户 ID 注册，openId 只在后端取得并用于当前员工自己的智能体和对话。
- 每轮流式聊天生成短时 sid，由 ERP SnailAI Client 在本机内存中关联当前员工。MCP 请求携带该员工原始 token 与 clientid 到 ERP /mcp；MCP 端再次检查登录态、客户端范围、权限、数据范围和业务校验。
- 工具定义按当前员工权限过滤；无法识别权限的工具默认不展示。所有非只读工具在 ERP 页面出现一次性确认卡，员工确认后才继续调用，拒绝或超时均不执行。
- 本功能只把 Docs/Skills/**/SKILL.md 作为只读技能目录接入 ERP 对话。文件由 read_zhiyun_skill 按需读取，不会写入或修改 SnailAI 管理端的 Skill 数据。
- 图片上传到 SnailAI 原生资源库；ERP 只向浏览器返回随机票据，服务端将票据绑定当前 ERP userId 与资源 ID，1 小时失效，聊天时拒绝其他员工的票据。当前支持 PNG/JPG/JPEG，单张不超过 5 MB，每条消息最多 4 张；普通文档暂未接入。

## 2. 接口

| 方法与路径 | 说明 | 幂等性 | 副作用 | 前置条件 | 是否需审批 |
|---|---|---|---|---|---|
| GET /snail-ai/agents | 查询当前员工可使用的智能体；没有订阅项时使用配置的默认智能体 | 幂等 | 必要时注册 ERP 用户到 SnailAI | ERP 登录；SnailAI OpenAPI 可用 | 否 |
| POST /snail-ai/conversations | 为当前员工和指定智能体新建对话 | 非幂等；重复请求新建不同对话 | 新建 SnailAI 对话 | 当前员工可使用指定智能体 | 否 |
| POST /snail-ai/attachments | 上传 PNG/JPG/JPEG 图片并返回当前员工专属的短时票据 | 非幂等；重复上传创建独立资源 | 写入 SnailAI 资源存储 | ERP 登录；单张不超过 5 MB | 否 |
| GET /snail-ai/conversations?agentId={id} | 查询当前员工最近对话，最多 30 条 | 幂等 | 必要时注册 ERP 用户到 SnailAI | 当前员工可使用指定智能体 | 否 |
| GET /snail-ai/conversations/history?agentId={id}&page=1&size=20&keyword= | 按本人对话标题及消息内容搜索并分页，返回首条提问摘要 | 幂等 | 必要时注册 ERP 用户到 SnailAI | ERP 登录；智能体后台按应用、员工及智能体过滤 | 否 |
| GET /snail-ai/conversations/{conversationId}/messages?agentId={id} | 查询当前员工对话消息 | 幂等 | 必要时注册 ERP 用户到 SnailAI | 对话归属由当前 openId 校验 | 否 |
| GET /snail-ai/conversations/{conversationId}/images/{resourceId}?agentId={id} | 读取本人对话中关联的历史 PNG/JPEG 图片 | 幂等 | 无 | ERP 登录；智能体后台校验应用、员工、对话和图片归属 | 否 |
| POST /snail-ai/chat/stream | 流式发送文字及可选图片附件，并转发模型回复与写操作确认事件 | 非幂等；重复请求会产生重复对话消息 | 写入 SnailAI 对话消息；可能调用 ERP MCP | 登录员工、本人对话、可用智能体、配置可用的模型；附件票据须属于当前员工 | ERP MCP 写工具需员工逐次确认 |
| POST /snail-ai/approvals/resolve | 对等待中的一次工具操作确认或拒绝 | 一次性审批号只能完成一次 | 放行或拒绝当前 MCP 工具回调 | 相同登录员工、审批未过期、工具回调仍在等待 | 本请求就是员工确认 |
| POST /snail-ai/user/register | 手动确保当前 ERP 用户已注册到 SnailAI | 服务端按相同 ERP 用户外部 ID 注册 | 注册或更新 SnailAI 用户资料 | ERP 登录、SnailAI OpenAPI 可用 | 否 |
| GET /snail-ai/admin/traces | 分页查询员工对话轮次 | 幂等 | 无 | `system:snailai:trace:list` | 否 |
| GET /snail-ai/admin/traces/{traceId} | 查看单轮提问、回复及工具事件 | 幂等 | 无 | `system:snailai:trace:list` | 否 |
| GET /snail-ai/admin/traces/{traceId}/images | 预览该轮可唯一关联的图片 | 幂等 | 记录管理员查看日志 | `system:snailai:trace:list`；服务端核对员工、会话和图片归属 | 否 |
| GET /snail-ai/admin/traces/conversations | 按员工、智能体和对话编号聚合诊断轮次并分页 | 幂等 | 无 | `system:snailai:trace:list` | 否 |
| GET /snail-ai/admin/traces/conversations/{traceId} | 以一轮为锚点，读取同一员工及智能体的完整对话诊断 | 幂等 | 无 | `system:snailai:trace:list` | 否 |

### 聊天事件

历史消息响应中的 `attachments` 包含图片编号及名称；浏览器使用员工认证请求 ERP 图片接口，ERP 通过受应用认证的智能体后台接口读取原图，不返回资源实际存储路径。先前已保存在 SnailAI 消息 metadata 的图片可以恢复；没有消息关联或图片已被删除时无法恢复。接口实现已构建并部署至开发服务，员工页面和跨账号隔离仍待运行验收。

历史入口支持分页、按对话内容搜索和继续对话；页面切换时若有未发送输入或待发送图片，需要先处理草稿。切换对话只读取本人已有记录，历史确认卡及管理员跨员工图片预览不属于本接口的交付状态。

接口使用 text/event-stream。text、thinking、done、error 与 approval-required 事件通过 SSE 的 event 字段区分；事件 data 为 JSON。

approval-required 包含一次性 approvalId、工具名、工具说明和待执行参数。浏览器必须先展示这些字段，只有员工点击“确认执行”才调用审批接口。审批号只授权本次当前员工会话，不得复用或转交给其他员工。

## 3. MCP 与 Skill 运行边界

- ERP /mcp 不以服务器账号代办，MCP 工具沿用发起员工的原 Sa-Token 与 clientid。MCP 控制器中的 StpUtil.checkPermission 和已有业务审计仍是最终执行校验。
- 本轮 ERP 入口将当前 SnailAI Agent 的 MCP 连接收窄为 ERP MCP；不把 SnailAI 服务端保存的外部 MCP 凭证带入 ERP 会话。
- 只暴露识别到权限规则、且当前员工拥有对应权限的 ERP 工具；未登记的工具和框架 demo 工具关闭。
- Docs/Skills 由 ERP 客户端读取，聊天时只把技能目录摘要附入本轮提示词，完整内容通过 read_zhiyun_skill 按需加载。SnailAI 管理端的 Skill 列表不会因此自动出现这些文件。
- 当前只记录代码与构建状态；尚未通过 ERP 员工浏览器对话实测 SnailAI 模型回复、客户端 tools/list 或 MCP 工具调用，不能据此声称上述运行时步骤已验收。
