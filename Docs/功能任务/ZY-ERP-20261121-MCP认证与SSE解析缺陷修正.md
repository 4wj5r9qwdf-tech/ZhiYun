<!--
文件用途：记录 MCP 认证头与 ERP 智能助手 SSE 解析问题的修复范围和验证结果。
文件版本：1.0.0
功能目的：确保 MCP Basic 凭据按认证方案安全解析，且浏览器能按 SSE 标准处理不同换行和网络分块。
设计思路：认证输入先完成方案与格式验证再进入员工密码/客户端校验；SSE 解析器按完整行结束符识别事件分隔并保留未完成片段。
实现方法：修正 Basic scheme 检查；抽取可单测的 SSE 分帧工具；更正尺码控制器和认证器的文件头职责说明。
依赖与引用：RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/McpBasicAuthenticator.java；frontend/src/components/ErpAiAssistant.vue；frontend/src/utils/sseEventParser.js；Docs/规范/开发规范.md
初始开发人员：JY
初始开发时间：2026-11-21
当前责任人：JY
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-21
修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；修正认证方案校验、SSE 分帧和不准确文件头
最近测试时间：2026-11-21
测试人员：JY
测试状态：MCP 定向测试 3/3、SSE Node 测试 3/3、前端构建及 diff 检查通过；未执行浏览器端到端测试
测试范围与记录位置：本文第四节验证记录
-->
# 功能任务：MCP 认证与 SSE 解析缺陷修正

> 任务编号：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正<br>
> 状态：已完成（自动化验证通过）<br>
> 创建日期：2026-11-21<br>
> 业务负责人：项目维护者<br>
> 任务负责人：项目维护者<br>
> 参与人员：项目维护者、Codex（智能体）<br>
> 测试人员：项目维护者<br>
> 用户验收人：无需调整业务页面交互；未执行浏览器端到端验收<br>
> 目标集成分支 / PR：main（本轮推送目标）<br>
> 关联需求文档：Docs/规范/开发规范.md §4.1、§4.2

## 一、业务目标与范围

- 修复 MCP 认证器未验证 Authorization scheme、短头可能产生非预期异常的问题。
- 修复 ERP 智能助手事件解析仅能识别 LF 空行的问题，并保证 CRLF 被网络分块切断时不会提前分帧。
- 更正 SizeDataController 与 McpBasicAuthenticator 文件头中不符合实际实现的职责说明。
- 不改变 ERP 权限规则、员工身份、MCP 工具契约或业务数据。

## 二、实现说明

- Authorization 非空时只接受大小写不敏感的 Basic scheme；空凭据、非法 Base64、无用户名分隔符均返回统一认证格式错误。自定义 MCP 凭据头仅在 Authorization 缺失时继续使用。
- SSE 解析器按 CRLF、LF、CR 作为行结束符；两个完整连续行结束符形成事件边界。非最终分块尾部的 CR 暂留，待后续字节确认是否属于 CRLF；流结束时刷新末尾内容。
- SizeDataController 文件头明确记录该控制器直接执行权限校验、业务校验、JDBC 事务和审计；认证器文件头描述实际短时会话创建流程。
- 增加 MCP 认证头边界测试和 SSE 分帧测试。

## 三、变更文件

- RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/McpBasicAuthenticator.java
- RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/controller/SizeDataController.java
- RuoYi-Vue-Plus/ruoyi-admin/src/test/java/org/dromara/web/mcp/McpBasicAuthenticatorTest.java
- frontend/src/components/ErpAiAssistant.vue
- frontend/src/utils/sseEventParser.js
- frontend/src/utils/sseEventParser.d.ts
- frontend/tests/sseEventParser.test.js

## 四、验收与测试证据

| 检查项 | 命令 | 结果 |
|---|---|---|
| MCP 认证边界 | mvn -pl ruoyi-admin -am -Dtest=McpBasicAuthenticatorTest -Dsurefire.failIfNoSpecifiedTests=false test | 通过，3 个参数场景，0 失败 |
| SSE 分帧边界 | node --test tests/sseEventParser.test.js（工作目录 frontend） | 通过，3/3 |
| 前端类型检查与生产构建 | npm run build（工作目录 frontend） | 通过，vue-tsc 与 Vite 构建成功 |
| 差异格式检查 | git diff --check | 通过 |

- 未执行浏览器端到端验证，也未重跑尺码模块功能测试；尺码控制器只调整文件头说明。
- 自动化测试与构建不代表生产运行时或用户验收状态。

## 五、交接说明

- MCP 客户端使用 Authorization 时必须发送标准 Basic scheme；使用 X-MCP-Username/X-MCP-Password 时不得同时发送 Authorization。
- SSE 解析器是纯函数，可使用 Node 内置测试器独立验证，不新增前端测试框架或依赖。
