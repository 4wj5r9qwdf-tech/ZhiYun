#!/usr/bin/env bash
# 文件用途：停止织云本地 ERP、框架控制台、前端及本地 Docling 开发解析容器。
# 文件版本：1.0.1
# 功能目的：通过统一入口停止本地开发服务，释放其占用的端口与计算资源。
# 设计思路：逐个停止本项目命名的进程与 Docling 开发容器，不操作数据库和基础依赖容器。
# 实现方法：按服务名终止应用进程，并调用 Docling 开发脚本停止本地解析容器。
# 关键约束：不删除容器、镜像、数据卷或凭据；不停止 PostgreSQL、Redis、MinIO。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY（Codex辅助）
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-24
# 修改标记：ZY-ERP-20261124-文档解析服务生命周期；统一停止本地 Docling 开发容器
# 最近测试时间：2026-11-24
# 测试人员：JY（测试责任人）
# 测试状态：Shell 静态检查通过；本地容器状态由开发脚本检查
# 测试范围与记录位置：见当前交付说明；停止动作只针对命名的本地 Docling 开发容器
# 织云 ERP - 停止脚本
# 用法：bash scripts/stop.sh
# 停止：后端 + 监控中心 + SnailJob + SnailAI + 前端 + 本地 Docling 开发容器
set -e

echo "=== 织云 ERP 停止 ==="

# 1. 停后端
echo "[1/5] 停止后端..."
pkill -f 'ruoyi-admin.jar' 2>/dev/null && echo "  后端已停止" || echo "  后端未运行"

# 2. 停监控中心
echo "[2/5] 停止监控中心..."
pkill -f 'ruoyi-monitor-admin.jar' 2>/dev/null && echo "  监控中心已停止" || echo "  监控中心未运行"

# 3. 停独立框架控制台
echo "[3/5] 停止 SnailJob..."
pkill -f 'ruoyi-snailjob-server.jar' 2>/dev/null && echo "  SnailJob 已停止" || echo "  SnailJob 未运行"
echo "[4/5] 停止 SnailAI..."
pkill -f 'ruoyi-snailai-server.jar' 2>/dev/null && echo "  SnailAI 已停止" || echo "  SnailAI 未运行"

# 5. 停前端
echo "[5/5] 停止前端..."
pkill -f 'pnpm dev' 2>/dev/null && echo "  前端已停止" || echo "  前端未运行"
pkill -f 'vite' 2>/dev/null || true

# 本地 Docling 是可重启的开发容器；仅停止，不删除容器、镜像或密钥文件。
echo "[6/6] 停止本地 Docling..."
if command -v docker >/dev/null 2>&1 \
    && [[ "$(docker inspect --format '{{.State.Running}}' zhiyun-docling-dev 2>/dev/null || true)" == "true" ]]; then
  bash "$(cd "$(dirname "$0")" && pwd)/docling-dev.sh" stop
  echo "  Docling 已停止"
else
  echo "  Docling 未运行"
fi

echo ""
echo "=== 已停止 ==="
