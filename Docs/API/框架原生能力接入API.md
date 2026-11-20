# 框架原生能力接入 API/MCP 核对

> 更新日期：2026-11-17
> 范围：RuoYi-Vue-Plus 系统管理、审批工作流、代码生成、监控及配套独立控制台的 ERP 接入核对。本文记录集成入口和权限边界；完整请求字段以运行中的 Swagger 为准。

运行服务的 Swagger 配置实际返回五组接口：`1.织云业务与基础资料`（161 个路径）、`2.系统管理接口`（121 个路径）、`3.审批与工作流接口`（56 个路径）、`4.代码生成接口`（12 个路径）和 `9.框架示例（开发测试）`（70 个路径）。框架示例组保留在 Swagger 供开发测试，不放到织云业务导航。

## 接入清单

| 框架能力 | ERP 入口 | 入口权限 | API 来源 | MCP 状态 |
|---|---|---|---|---|
| 通知公告 | 系统管理 / 通知公告 | `system:notice:list`；详情、新增、修改、删除分别校验 `system:notice:query/add/edit/remove` | 系统管理组；`/system/notice/**` | 暂无 MCP 工具；不开放智能体发布全员公告 |
| 登录日志 | 系统管理 / 登录日志 | `monitor:logininfo:list`；导出、删除、解锁分别校验对应操作码 | 系统管理组；`/monitor/logininfor/**` | 不接入 |
| 操作日志、在线用户、缓存监控 | 系统管理 / 系统日志、在线用户、缓存监控 | `monitor:operlog:*`、`monitor:online:*`、`monitor:cache:list` | 系统管理组；`/monitor/**` | 不接入系统运维操作工具 |
| 菜单、字典、参数、文件、存储配置、客户端 | 系统管理 / 对应维护功能 | `system:menu:*`、`system:dict:*`、`system:config:*`、`system:oss:*`、`system:ossConfig:*`、`system:client:*` | 系统管理组；`/system/**`、`/resource/oss/**` | 不开放系统权限、密钥、客户端及配置写入工具 |
| 流程类别、流程定义、流程实例、任务监控、流程表达式、请假示例 | 审批 / 对应功能 | `workflow:category:*`、`workflow:definition:*`、`workflow:instance:*`、`workflow:task:*`、`workflow:spel:*`、`workflow:leave:*` | 审批与工作流组；`/workflow/**` | 框架管理端全量维护能力不开放 MCP；织云员工审批中心待办/已办/发起进度及业务绑定通过独立工具接入，见[审批中心 API](审批中心API.md) |
| 代码生成 | 系统管理 / 代码生成 | `tool:gen:*` | 代码生成组；`/tool/gen/**` | 不开放可生成/下载/删除代码的 MCP 工具 |
| Spring Boot Admin | 系统管理 / Admin 监控 | `monitor:admin:list` 控制 ERP 入口 | Admin 独立控制台，不属于 ERP REST 控制器 | 无 MCP；独立登录 |
| SnailJob | 系统管理 / SnailJob 任务调度 | `system:snailjob:list` 控制 ERP 入口 | SnailJob 独立控制台 | 无 MCP；独立登录 |
| SnailAI 管理控制台与员工入口 | 系统管理 / SnailAI 智能体；ERP 顶栏 / 智能助手 | `system:snailai:list` 控制管理台入口；ERP 员工入口沿用当前登录人身份与权限 | SnailAI 独立控制台；员工聊天通过 ERP 后端代理 SnailAI Client/OpenAPI，并按会话连接 ERP MCP | 不开放控制台管理 MCP；员工入口按当前人员权限过滤工具，写操作由 ERP 页面确认。实际聊天与 MCP 调用仍待运行验收 |
| Swagger API 文档 | 系统管理 / 接口文档 | `system:api-docs:list` 控制 ERP 入口 | `/swagger-ui/index.html`、`/v3/api-docs/**` | Swagger 是开发联调界面，不等于 MCP 工具 |

## REST 路径索引（运行中 Swagger 核对）

请求字段、响应结构和模型以 Swagger 对应分组为准。以下路径来自运行中的 OpenAPI 文档，路径大小写也按实际接口保留。

### 个人通知与铃铛

| 能力 | REST 路径 |
|---|---|
| 当前用户通知列表及未读数 | `GET /system/profile/messages`、`GET /system/profile/messages/unread` |
| 当前用户标记单条已读或全部已读 | `POST /system/profile/messages/{id}/read`、`POST /system/profile/messages/read-all` |

通知列表只返回当前用户收到的全局或定向消息；`notice_content` 仅在 `category=notice` 时返回，用于铃铛内查看该消息发送时保存的公告正文快照，不授予公告管理权限，也不按公告编号开放任意公告查询。

### 系统管理接口

| 能力 | REST 路径 |
|---|---|
| 通知公告 | `GET /system/notice/list`、`GET /system/notice/{noticeId}`、`POST /system/notice`、`PUT /system/notice`、`DELETE /system/notice/{noticeIds}` |
| 登录日志 | `GET /monitor/loginInfo/list`、`POST /monitor/loginInfo/export`、`DELETE /monitor/loginInfo/{infoIds}`、`DELETE /monitor/loginInfo/clean`、`GET /monitor/loginInfo/unlock/{userName}` |
| 操作日志 | `GET /monitor/operlog/list`、`POST /monitor/operlog/export`、`DELETE /monitor/operlog/{operIds}`、`DELETE /monitor/operlog/clean` |
| 在线用户 | `GET /monitor/online`、`GET /monitor/online/list`、`DELETE /monitor/online/{tokenId}`、`DELETE /monitor/online/myself/{tokenId}` |
| 缓存监控 | `GET /monitor/cache` |
| 菜单 | `GET /system/menu/list`、`GET /system/menu/treeselect`、`GET /system/menu/roleMenuTreeselect/{roleId}`、`GET /system/menu/getRouters`、`GET /system/menu/{menuId}`、`POST /system/menu`、`PUT /system/menu`、`DELETE /system/menu/{menuId}`、`DELETE /system/menu/cascade/{menuIds}` |
| 字典 | `GET /system/dict/type/list`、`GET /system/dict/type/optionselect`、`GET /system/dict/type/{dictId}`、`POST /system/dict/type`、`PUT /system/dict/type`、`DELETE /system/dict/type/{dictIds}`、`DELETE /system/dict/type/refreshCache`；数据项为 `/system/dict/data/**`，含分页、按类型查询、详情、新增、修改、删除及导出 |
| 系统参数 | `GET /system/config/list`、`GET /system/config/{configId}`、`GET /system/config/configKey/{configKey}`、`POST /system/config`、`PUT /system/config`、`PUT /system/config/updateByKey`、`DELETE /system/config/{configIds}`、`DELETE /system/config/refreshCache`、`POST /system/config/export`；业务编码规则和默认分类参数也使用 `/system/config/*-code-rule`、`/system/config/*-default-category` 等专用路径 |
| 客户端 | `GET /system/client/list`、`GET /system/client/{id}`、`POST /system/client`、`PUT /system/client`、`PUT /system/client/changeStatus`、`DELETE /system/client/{ids}`、`POST /system/client/export` |
| 文件与对象存储配置 | 文件记录和上传下载位于 `/resource/oss/**`；配置列表、详情、新增、修改、启停和删除位于 `/resource/oss/config/**` |

### 审批与工作流接口

| 能力 | REST 路径 |
|---|---|
| 流程类别 | `/workflow/category/**`：列表、树、详情、新增、修改、导出、删除 |
| 流程定义 | `/workflow/definition/**`：列表、详情、新增、修改、发布、撤销发布、启停、复制、导入导出、XML 查看、删除 |
| 流程实例 | `/workflow/instance/**`：当前/运行中/已完成列表、详情、历史任务、变量查看修改、暂停恢复、作废、撤销及管理端删除 |
| 审批任务 | `/workflow/task/**`：待办、已办、抄送、详情、下一节点、完成、退回、催审、改派、终止及任务操作 |
| 流程表达式 | `/workflow/spel/**`：列表、详情、新增、修改、删除 |
| 请假示例 | `/workflow/leave/**`：列表、详情、新增、修改、提交并启动流程、导出、删除。此接口仅供框架示例，不代表织云请假业务 |

### 代码生成接口

`/tool/gen/**` 包括数据表列表及导入、表和字段详情、生成配置修改、预览、同步数据库、单表/批量生成代码、下载和删除。生成及下载会产生代码文件，须由有 `tool:gen:*` 权限的维护人员在页面中执行。

> 独立 Admin、SnailJob、SnailAI 控制台不属于上述 ERP REST 路径；其链接由 ERP 菜单权限控制，控制台使用自身服务接口和认证。

## 权限结论

- 功能卡片和路由守卫在前端按菜单权限码控制；REST 写接口继续由后端 `Sa-Token` 权限注解校验，菜单隐藏不替代服务端授权。
- 本次核对的页面和操作权限码共 88 项，开发库权限菜单树中 88 项均存在。SnailJob、SnailAI、接口文档 3 项缺失记录已由 `sql/框架前端功能接入_20261117.sql` 补入开发库，并已合并到 `sql/主建表.sql`。
- 新增的 3 项菜单未被写入任何角色授权关系。管理员需按岗位职责在角色管理中分配；本轮未使用普通角色逐项做端到端授权和撤权验收。
- Admin、SnailJob、SnailAI 是独立服务。ERP 权限只限制 ERP 中的入口显示和路由访问；外部控制台使用各自认证，不接收 ERP 登录令牌，也没有继承 ERP 的角色和数据范围。不要将“入口挂权限”表述为“控制台 SSO 或内部权限已统一”。

## API 与 MCP 范围

- 当前接口文档按织云业务基础资料、系统管理、审批工作流、代码生成、框架示例分组。框架 REST 路由由 Swagger 展示，逐接口权限以控制器实现为准。
- 2026-11-17 的认证 MCP `tools/list` 实际发现 125 个工具：123 个织云业务工具及 2 个 `ruoyi-demo` 示例工具。工具发现不代表每个 REST 端点都有 MCP 映射或每个写工具都完成了调用验证。
- 2026-11-20 审批中心接入后曾发现 151 个工具；随后人员档案 MCP 新增 7 个工具并重启开发后端，最新认证 `tools/list` 共发现 158 个工具，其中人员档案 7/7、人员分类权限 2/2、物料档案 9/9、审批中心 13/13。工具目录发现不代替普通员工的业务权限和读写调用核验。
- MCP 主要服务于有明确员工权限、业务校验和审计路径的织云资料操作。系统菜单/角色、客户端、对象存储凭据、系统参数、代码生成、独立运维控制台等高权限操作保留在 ERP/独立控制台中，不通过 MCP 暴露通用写入口。
- 框架管理端流程定义和全量任务监控不开放 MCP。织云业务审批的已验收页面和业务单据穿透已由审批中心独立提供 MCP 工具；不将该业务入口扩展为流程定义、运维或管理员代办能力。

## 尚待后续处理

SnailAI 独立管理控制台与 ERP 员工入口是两个入口。员工侧代码已接入：ERP 后端将当前登录员工映射到 SnailAI 用户，并把该轮员工身份用于 ERP MCP 工具过滤和服务端权限复核；写操作还需该员工在 ERP 对话框中确认。真实员工会话下的流式回复、`tools/list`、工具调用、权限过滤和确认闭环仍待运行验收；代码构建成功不等同于运行链路已验收。状态以《功能实现状态.md》和《测试与验收清单.md》为准。
