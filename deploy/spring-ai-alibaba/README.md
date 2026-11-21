# Spring AI Alibaba 织云补丁包

版本：`zhiyun-20261121-v2`

适用上游仓库：`https://github.com/alibaba/spring-ai-alibaba.git`

适用上游提交：`17e26ff6fe951d605771c1845229ca3edb043634`

## 补丁内容

- PostgreSQL JDBC 驱动配置，排除被上游依赖带入的 MySQL 驱动。
- 修复后端源码缺少 `ThreadPoolExecutor` 导入导致的编译错误。
- 修正 PostgreSQL 下工作区状态字段的数值比较。
- 将 Agent Studio 管理端 Mapper 中的 MySQL 分页语法改为 PostgreSQL 语法。
- 组件配置的输入参数或输出参数为空时按空列表处理，避免创建/发布组件时页面报错。
- PostgreSQL 初始化和前端依赖兼容辅助脚本 `spring-ai-alibaba-postgresql.py`。

补丁不包含业务数据迁移，也不修改数据库内容。部署前仍需按环境备份数据库和当前应用制品。

## 应用补丁

解压补丁包后，向脚本传入 **Spring AI Alibaba Git 仓库根目录**：

```bash
./apply-patches.sh /path/to/spring-ai-alibaba
```

脚本会校验补丁文件 SHA-256 和上游提交号，支持重复执行；版本不符或补丁冲突时会停止，不会尝试强行修改。

## 应用后构建

补丁作用于源码。应用后必须重新构建并替换部署制品：

```bash
cd /path/to/spring-ai-alibaba/spring-ai-alibaba-admin
mvn -pl spring-ai-alibaba-admin-server-start -am -DskipTests package

cd frontend
npm ci --ignore-scripts
npm run build:flow
python3 /path/to/unpacked-patch/spring-ai-alibaba-postgresql.py prepare-frontend /path/to/spring-ai-alibaba/spring-ai-alibaba-admin
BACK_END=java npm run build:app
```

`prepare-frontend` 应在依赖安装和流程编辑器构建后、应用前端构建前执行。它会修正已安装的 Umi HTTPS 依赖对 Node.js 内部 `http_parser` 模块的提前加载问题；该脚本已包含在补丁包中。

若使用容器部署，应使用补丁后的源码重新构建并发布镜像，再按部署平台的滚动更新流程替换容器。直接修改运行中容器的文件不会进入新镜像，容器重建后会丢失。

若使用虚拟机部署，应先备份旧 JAR 和前端静态资源，再替换新构建制品、重启服务、检查健康状态；失败时恢复旧制品并重启。

## 适用边界

当前织云仓库提供的是版本化源码补丁和应用脚本。生产环境的 Agent Studio 部署形态、镜像构建文件、制品仓库及发布流水线尚未确定，因此本包不会直接连接或修改生产服务，也不能单独把补丁写进已运行的 JAR 或镜像。确定生产部署方式后，可将本包接入对应的构建/发布步骤。
