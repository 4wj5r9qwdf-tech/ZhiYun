#!/usr/bin/env bash
# 织云 ERP - 停止脚本
# 用法：bash scripts/stop.sh
# 停止：后端 + 前端
set -e

echo "=== 织云 ERP 停止 ==="

# 1. 停后端
echo "[1/2] 停止后端..."
pkill -f 'ruoyi-admin.jar' 2>/dev/null && echo "  后端已停止" || echo "  后端未运行"

# 2. 停前端
echo "[2/2] 停止前端..."
pkill -f 'pnpm dev' 2>/dev/null && echo "  前端已停止" || echo "  前端未运行"
pkill -f 'vite' 2>/dev/null || true

echo ""
echo "=== 已停止 ==="
