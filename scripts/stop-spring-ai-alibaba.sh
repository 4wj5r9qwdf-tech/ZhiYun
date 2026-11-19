#!/usr/bin/env bash
# 停止 Spring AI Alibaba Agent Studio 和专用中间件容器，保留本地数据库与文件。
set -euo pipefail

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
for service in frontend backend; do
  pid_file="$PROJECT_ROOT/logs/spring-ai-alibaba-$service.pid"
  if [[ -f "$pid_file" ]]; then
    process_id="$(cat "$pid_file")"
    if [[ "$process_id" =~ ^[0-9]+$ ]] && kill -0 "$process_id" 2>/dev/null; then
      kill -- "-$process_id" 2>/dev/null || kill "$process_id" 2>/dev/null || true
      echo "已停止 Agent Studio $service 进程。"
    fi
    rm -f "$pid_file"
  fi
done

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose -p spring-ai-alibaba-test -f "$PROJECT_ROOT/deploy/spring-ai-alibaba/docker-compose.middleware.yml" down
fi
echo "已停止 Agent Studio 服务，数据库和运行数据已保留。"
