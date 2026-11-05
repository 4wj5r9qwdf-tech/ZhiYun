# 织云 ERP（ZhiYun）

织云 ERP 是一款专门为服装加工厂开发的 ERP 系统，基于 [RuoYi-Vue-Plus 6.0（PostgreSQL 版）](RuoYi-Vue-Plus/) 二次开发，覆盖人员组织、订单、生产、库存、财务及厂务管理等业务。

## 功能模块

| 状态 | 模块 |
|---|---|
| ✅ 已实装 | 登录与认证（工号/手机号 + 密码，无验证码；超管走配置文件）· 公司档案（双公司展示层切换）· 工作台（功能卡片/待办红点/全局搜索）· 个人中心 · 人员管理 · 组织架构 · 岗位管理 · 角色管理（数据范围 5 档）· 建筑档案 · 列表页标准模板 |
| 📐 需求已规划（开发状态以状态矩阵为准） | 订单 · 生产（裁剪→打码→分包→吊挂→缝制→后整→质检→外协）· 库存 · 宿舍 · 食堂 · 车辆 · 物业资产 · 财务与采购 · 报表中心 |

需求与模块详情见 [Docs/系统需求规划.md](Docs/系统需求规划.md)（主索引）与 [Docs/生产流程规划.md](Docs/生产流程规划.md)。当前开发覆盖情况见 [Docs/功能实现状态.md](Docs/功能实现状态.md)。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 + Spring Boot（RuoYi-Vue-Plus 6.0，PG 驱动版），Sa-Token 认证 |
| 主库 | PostgreSQL 16 + pgvector（知识库向量检索） |
| 缓存/消息 | Redis 7 |
| 对象存储 | MinIO（Docker 容器，公开桶 `zhizhun-files` + 私有桶 `zhizhun-private`） |
| 审批 | Warm-Flow（RuoYi 原生集成） |
| 定时任务 | snail-job（XXL-Job 替代件，随 RuoYi-Vue-Plus 内置） |
| 前端 | Vue 3 + Vite + TypeScript + Ant Design Vue 4.x（PC 后台，开发端口 5273） |
| 大模型 | OpenAI 兼容协议网关（LLM/TTS/ASR/Embedding，密钥只进 `.env`） |

## 目录结构

```
├─ RuoYi-Vue-Plus/   后端（Java/Spring Boot 多模块）
├─ frontend/          前端（Vue3，PC 后台）
├─ sql/               织云自建表 DDL（主建表.sql 为唯一权威建表入口）+ 组织树迁移脚本
├─ Docs/              全部需求/规范/知识库文档（中文命名）
├─ scripts/           start.sh / stop.sh / status.sh 一键启停
└─ .env.example       环境配置模板（真实值写入 .env，.env 不进 Git）
```

## 快速开始

开发工作流：源码直接在 WSL `/home/it/ZhiYun` 中编辑、构建和测试，并从该工作区推送 GitHub。Windows `D:\ZhiYun` 是迁移期副本，目前主要保留文档等内容，不作为代码日常编辑源。

**依赖**：JDK 21 · PostgreSQL 16（含 pgvector）· Redis 7 · Docker · Node.js 24 · pnpm

1. **初始化数据库**（库名 `zhizhun_erp`）：
   ```bash
   psql -d zhizhun_erp -f RuoYi-Vue-Plus/script/sql/postgres/postgres_ry_vue.sql   # RuoYi 基础表
   psql -d zhizhun_erp -f RuoYi-Vue-Plus/script/sql/postgres/postgres_ry_job.sql
   psql -d zhizhun_erp -f RuoYi-Vue-Plus/script/sql/postgres/postgres_ry_workflow.sql
   psql -d zhizhun_erp -f sql/主建表.sql                                           # 织云自建地基表
   ```
2. **配置环境变量**：`cp .env.example .env`，填入数据库/Redis/MinIO/大模型网关等真实值（`.env` 不进 Git）。
3. **启动 MinIO 容器**（开发环境容器名 `zhizhun-minio`，端口 9000；参考 `RuoYi-Vue-Plus/script/docker/docker-compose.yml`）。
4. **构建并启动**（一条命令拉起后端 8080 + 前端 5273，并自动确保 MinIO 两桶权限）：
   ```bash
   cd RuoYi-Vue-Plus && ./mvnw -pl ruoyi-admin -am package -DskipTests   # 首次/改后端后
   cd .. && bash scripts/start.sh
   ```
5. **访问**：前端 http://localhost:5273/login，后端健康检查 http://127.0.0.1:8080/hello
   - 普通员工：工号（6 位）/手机号 + 密码，新建人员默认密码 `123456`（测试环境）
   - 超级管理员：账号 `admin`，密码在 **你部署项目时** 于 `.env` 的 `SUPER_ADMIN_USERNAME / SUPER_ADMIN_PASSWORD` 自行创建设置（不进数据库、不进 Git）

启动服务：`cd ~/ZhiYun
bash scripts/start.sh`;
停止服务：`bash scripts/stop.sh`；
查看状态：`bash scripts/status.sh`。

## 文档入口

- 项目协作入口：[AGENTS.md](AGENTS.md)（AI 助手执行边界与规范导航）
- 需求基线：[Docs/系统需求规划.md](Docs/系统需求规划.md) → 各 `Docs/模块-*.md` / [Docs/生产流程规划.md](Docs/生产流程规划.md)
- 开发顺序：[Docs/开发路线图.md](Docs/开发路线图.md)（按阶段和依赖，不排日期）
- 打样功能：[Docs/打样功能规划.md](Docs/打样功能规划.md)（独立“研发”模块）
- 开发顺序：[Docs/开发路线图.md](Docs/开发路线图.md)（按阶段和依赖，不排日期）
- 打样功能：[Docs/打样功能规划.md](Docs/打样功能规划.md)（独立“研发”模块）
- 开发规范：[Docs/开发规范.md](Docs/开发规范.md) · [Docs/前端规范.md](Docs/前端规范.md)（Ant Design Vue + 列表页标准模板）· [Docs/API文档规范.md](Docs/API文档规范.md) · [Docs/内嵌智能体规范.md](Docs/内嵌智能体规范.md)
- 开发环境：[Docs/开发环境与同步流程.md](Docs/开发环境与同步流程.md) · [Docs/部署与迁移.md](Docs/部署与迁移.md)
- WSL 智能体迁移交接：[Docs/WSL智能体环境迁移交接.md](Docs/WSL智能体环境迁移交接.md)
- 测试与现状：[Docs/功能实现状态.md](Docs/功能实现状态.md) · [Docs/测试与验收清单.md](Docs/测试与验收清单.md)
- 过程记录：[Docs/开发日志.md](Docs/开发日志.md) · [Docs/版本记录.md](Docs/版本记录.md)（发版时维护）

## 许可

底座框架 RuoYi-Vue-Plus 采用 [MIT 许可](LICENSE)（见 `LICENSE`，保留上游版权声明）；本仓库二开部分暂不单独声明许可。
