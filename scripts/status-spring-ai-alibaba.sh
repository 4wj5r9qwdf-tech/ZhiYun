#!/usr/bin/env bash
# 查询 Spring AI Alibaba Agent Studio 本地服务状态。
set -euo pipefail

echo "[Spring AI Alibaba Agent Studio] :8910"
if curl -fsS --max-time 3 http://127.0.0.1:8910/ >/dev/null 2>&1; then
  echo "  ✓ 前端可访问"
else
  echo "  ✗ 前端未运行"
fi

echo "[Spring AI Alibaba Agent Studio 后端] :8091"
if curl -fsS --max-time 3 http://127.0.0.1:8091/actuator/health >/dev/null 2>&1; then
  echo "  ✓ 健康检查通过"
else
  echo "  ✗ 后端未就绪"
fi

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "[Spring AI Alibaba 独立中间件]"
  PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  RUNTIME_ROOT="$PROJECT_ROOT/.runtime/spring-ai-alibaba"
  if [[ -s "$RUNTIME_ROOT/nacos-auth-token" && -s "$RUNTIME_ROOT/nacos-auth-identity-key" && -s "$RUNTIME_ROOT/nacos-auth-identity-value" ]]; then
    NACOS_AUTH_TOKEN="$(< "$RUNTIME_ROOT/nacos-auth-token")"
    NACOS_AUTH_IDENTITY_KEY="$(< "$RUNTIME_ROOT/nacos-auth-identity-key")"
    NACOS_AUTH_IDENTITY_VALUE="$(< "$RUNTIME_ROOT/nacos-auth-identity-value")"
  else
    NACOS_AUTH_TOKEN="status-only-placeholder"
    NACOS_AUTH_IDENTITY_KEY="status-only-placeholder"
    NACOS_AUTH_IDENTITY_VALUE="status-only-placeholder"
  fi
  export NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
  docker compose -p spring-ai-alibaba-test -f "$PROJECT_ROOT/deploy/spring-ai-alibaba/docker-compose.middleware.yml" ps --format '  {{.Name}}: {{.Status}}'
fi
