#!/usr/bin/env bash
# 文件用途：执行“stop.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“stop.sh”对应的本地开发、诊断或数据核验辅助操作。
# 设计思路：将可重复环境操作收敛到脚本，依赖环境变量并避免凭据进入代码或输出。
# 实现方法：检查参数和本地环境，再执行目标命令或只读核验并返回明确退出状态。
# 关键约束：不输出凭据、不操作生产数据、不执行无关破坏性操作。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-20
# 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
# 最近测试时间：本次整理未执行功能测试
# 测试人员：本次整理未执行
# 测试状态：待功能回归；历史测试记录待追溯
# 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
# 织云 ERP - 停止脚本
# 用法：bash scripts/stop.sh
# 停止：后端 + 监控中心 + SnailJob + SnailAI + Spring AI Alibaba + 前端
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

# 4.5 停止 Spring AI Alibaba 本地测试平台
echo "[4.5/5] 停止 Spring AI Alibaba..."
bash "$(cd "$(dirname "$0")" && pwd)/stop-spring-ai-alibaba.sh"

# 5. 停前端
echo "[5/5] 停止前端..."
pkill -f 'pnpm dev' 2>/dev/null && echo "  前端已停止" || echo "  前端未运行"
pkill -f 'vite' 2>/dev/null || true

echo ""
echo "=== 已停止 ==="
