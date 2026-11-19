#!/usr/bin/env bash
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
