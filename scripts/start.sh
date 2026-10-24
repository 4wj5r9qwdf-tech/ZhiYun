#!/usr/bin/env bash
# 织云 ERP - 启动脚本
# 用法：bash scripts/start.sh
# 启动：后端(RuoYi-Vue-Plus 8080) + 前端(Vue3 dev 5273)
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== 织云 ERP 启动 ==="

# 1. 启动后端（8080）
echo "[1/2] 启动后端 (8080)..."
if [ -f RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar ]; then
  set -a && source .env && set +a
  cd RuoYi-Vue-Plus
  nohup java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev \
    > /tmp/ruoyi-admin.log 2>&1 &
  disown
  echo "  后端 PID: $!  日志: /tmp/ruoyi-admin.log"
  cd "$PROJECT_ROOT"
else
  echo "  错误：jar 不存在，请先构建：cd RuoYi-Vue-Plus && ./mvnw -pl ruoyi-admin -am package -DskipTests"
  exit 1
fi

# 2. 启动前端（5273）
echo "[2/2] 启动前端 (5273)..."
cd frontend
nohup pnpm dev > /tmp/frontend.log 2>&1 &
disown
echo "  前端 PID: $!  日志: /tmp/frontend.log"
cd "$PROJECT_ROOT"

echo ""
echo "=== 启动中，等待服务就绪 ==="
echo "后端: http://127.0.0.1:8080  (日志 /tmp/ruoyi-admin.log)"
echo "前端: http://localhost:5273/login  (日志 /tmp/frontend.log)"
echo "查看状态：bash scripts/status.sh"
