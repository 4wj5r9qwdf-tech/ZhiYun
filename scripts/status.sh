#!/usr/bin/env bash
# 织云 ERP - 状态检查脚本
# 用法：bash scripts/status.sh
set -e

echo "=== 织云 ERP 服务状态 ==="

# 1. 后端
echo "[后端] :8080"
if ss -tlnp 2>/dev/null | grep -q ':8080'; then
  echo "  ✓ 运行中"
  # Hello 接口验证
  RESULT=$(curl -sS --max-time 3 http://127.0.0.1:8080/hello 2>/dev/null | head -c 200)
  if echo "$RESULT" | grep -q '"code":200'; then
    echo "  ✓ Hello 接口正常"
  else
    echo "  ✗ Hello 接口异常: $RESULT"
  fi
else
  echo "  ✗ 未运行"
fi

# 2. 前端
echo "[前端] :5273"
if ss -tlnp 2>/dev/null | grep -q ':5273'; then
  echo "  ✓ 运行中"
else
  echo "  ✗ 未运行"
fi

# 3. 依赖服务
echo "[依赖服务]"
for PORT_INFO in "5432:PostgreSQL" "6379:Redis" "9000:MinIO"; do
  PORT="${PORT_INFO%%:*}"
  NAME="${PORT_INFO##*:}"
  if ss -tlnp 2>/dev/null | grep -q ":$PORT"; then
    echo "  ✓ $NAME (:$PORT)"
  else
    echo "  ✗ $NAME (:$PORT) 未运行"
  fi
done

echo ""
echo "启动: bash scripts/start.sh   停止: bash scripts/stop.sh   重启: bash scripts/stop.sh && bash scripts/start.sh"
