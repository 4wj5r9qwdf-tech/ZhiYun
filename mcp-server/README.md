# 织云 ERP MCP Server（小组管理 group）

面向内嵌智能体的 MCP 能力入口（stdio），把小组管理的 5 个能力原子暴露为 tool。
规范依据：《Docs/内嵌智能体规范.md》《Docs/API文档.md》。

## 安全设计（红线）

- **本进程不直连数据库、不持有任何账号**，只把 tool 参数里的 `employee_token`（发起员工本人 Sa-Token）转发给后端 HTTP API；
- **权限由后端 Sa-Token 按 token 校验**：无权限 → 后端 403 → 本进程原样透传（员工看不到/调不通无权限动作）；
- 操作留痕：后端 `@Log` 记录操作人 = 该员工（记工号不记 AI）。

## 环境配置

| 环境变量 | 说明 | 默认 |
|---|---|---|
| `MCP_BACKEND_URL` | 后端地址 | `http://127.0.0.1:8080` |
| `MCP_CLIENT_ID` | 登录应用标识（公开值，同前端） | `e5cd7e4891bf95d1d19206ce24a7b32e` |

## 安装与启动

```bash
# 一次性：建 venv 装依赖（系统 Python 受 PEP 668 保护，必须 venv）
python3 -m venv mcp-server/.venv
mcp-server/.venv/bin/pip install mcp

# 启动（stdio，由对话壳/CLI 以 MCP 协议拉起）
mcp-server/.venv/bin/python mcp-server/server.py
```

## Tool 清单（5 个）

| tool | 读/写 | 对应 API | 说明 |
|---|---|---|---|
| `group_list` | 只读 | `GET /system/dept/list` | 默认 `dept_category='小组'`；支持 parent_id/dept_name/status 过滤 |
| `group_get` | 只读 | `GET /system/dept/{id}` | 详情含 ancestors 层级链 |
| `group_create` | 写 | `POST /system/dept` | 建小组（parent 可为小组 → 二级小组）；成功后需回读取新 ID |
| `group_update` | 写 | `PUT /system/dept` | 只更新传入字段；改 parent 级联重算 ancestors |
| `group_delete` | 写 | `DELETE /system/dept/{id}` | 5 道前置校验，失败 msg 透传 |

所有 tool 第一个参数均为 `employee_token`（员工本人 token，由对话壳注入）。
契约细节（幂等/副作用/前置/权限）见 [Docs/API/小组管理API.md](../Docs/API/小组管理API.md)。

## 自测

```bash
mcp-server/.venv/bin/python mcp-server/selftest.py   # 需后端 :8080 已启动
```
