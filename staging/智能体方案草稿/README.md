# 智能体方案草稿（未定稿，不进运行系统）

> 2026-10-31：用户仍在打磨方案，尚未发话开工。抢跑落地的部分已按用户指示全部回滚，
> 原样归档在这里，方案定稿后按下述路径恢复或直接重写。

## 内容清单
| 文件 | 说明 |
| --- | --- |
| `AgentController.java` | 后端 `/system/agent/actions`（按登录人权限过滤动作）+ `/system/agent/chat`（LLM 循环+工具回环+留痕）；恢复位置 `RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/controller/` |
| `AgentChat.vue` | 前端对话页；恢复位置 `frontend/src/views/` |
| `主建表-第12节-智能体表.sql` | sys_agent_action / sys_agent_log / 菜单行 / 管理员授权 / 6 条种子动作 |
| `已还原文件.补丁` | frontend/src/router/index.ts、frontend/src/config/modules.ts、sql/主建表.sql 三处改动（`git apply` 恢复；注意 router/modules 是 10-31 快照） |
| `回滚-撤线上智能体.sql` | 撤线上库的 SQL（2026-10-31 已执行过） |

## 恢复步骤（方案定稿后）
1. `psql ... -f 主建表-第12节-智能体表.sql`（或定稿后的新 DDL）
2. `cp AgentController.java RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/controller/`
3. `cp AgentChat.vue frontend/src/views/ && git apply 已还原文件.补丁`（或按定稿重接路由/卡片）
4. `cd RuoYi-Vue-Plus && source ~/.bashrc && ./mvnw -q -pl ruoyi-admin -am package -DskipTests`
5. `bash scripts/stop.sh && sleep 8 && bash scripts/start.sh`（持久终端 pty-1）
6. `.env` 填好 `LLM_1_API_KEY/LLM_1_MODEL` 并确认 `LLM_1_BASE_URL` 网关在跑
7. 踢管理员/老板旧会话重新登录

## 已知未跑通的一环
LLM 网关：`.env` 指向 `http://127.0.0.1:8000/v1`，但 8000 未部署、key/model 为空。
E2E 验证到"权限过滤 + 优雅降级"为止（无 LLM 时 chat 返回"AI 网关暂不可用"）。
