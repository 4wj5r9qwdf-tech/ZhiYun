#!/usr/bin/env bash
# 启动框架管理控制台；HTTP 管理端绑定回环地址，RPC 端口由上游组件监听。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
mkdir -p logs

if [[ ! -f .env ]]; then
  echo "错误：缺少 .env，请按 .env.example 配置本地服务凭据。" >&2
  exit 1
fi
set -a
source .env
set +a

bash "$PROJECT_ROOT/scripts/prepare-snail-document-key.sh"

for REQUIRED_NAME in SNAILJOB_DB_PASSWORD SNAILAI_DB_PASSWORD SNAIL_AI_CRYPTO_KEY SNAIL_AI_CRYPTO_IV; do
  if [[ -z "${!REQUIRED_NAME:-}" ]]; then
    echo "错误：.env 中未配置 $REQUIRED_NAME。" >&2
    exit 1
  fi
done

# Docling 可用时让 SnailAI 复用自带客户端；密钥只传文件路径。
# Enable the upstream client only when the local service and key are present.
docling_key_file="${XDG_CONFIG_HOME:-$HOME/.config}/zhiyun/docling-dev.key"
if [[ -z "${SNAIL_AI_RAG_DOCLING_ENABLED+x}" && -r "$docling_key_file" ]] && command -v docker >/dev/null 2>&1; then
  docling_address="$(docker inspect --format '{{if .State.Running}}{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}{{end}}' zhiyun-docling-dev 2>/dev/null || true)"
  if [[ "$docling_address" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    export SNAIL_AI_RAG_DOCLING_ENABLED=true
    export SNAIL_AI_RAG_DOCLING_URL="http://${docling_address}:5001"
    export SNAIL_AI_RAG_DOCLING_API_KEY_FILE="$docling_key_file"
  fi
fi

port_listening() {
  ss -tln 2>/dev/null | grep -qE ":$1([[:space:]]|$)"
}

start_service() {
  local name="$1" port="$2" jar="$3" log="$4"
  if port_listening "$port"; then
    echo "$name 已在运行（:$port），跳过。"
    return
  fi
  if [[ ! -f "$jar" ]]; then
    echo "错误：$name 启动包不存在：$jar" >&2
    exit 1
  fi

  nohup java -jar "$jar" --spring.profiles.active=dev > "$log" 2>&1 < /dev/null &
  local service_pid=$!
  disown "$service_pid" 2>/dev/null || true
  echo "$name 启动中（PID $service_pid，日志 $log）"

  for _ in $(seq 1 45); do
    if port_listening "$port"; then
      echo "$name 已监听 :$port。"
      return
    fi
    sleep 1
  done

  echo "$name 未能在 45 秒内监听 :$port；查看日志：$log" >&2
  exit 1
}

start_service "SnailJob" 8800 \
  "$PROJECT_ROOT/RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar" \
  "$PROJECT_ROOT/logs/ruoyi-snailjob-server.log"
start_service "SnailAI" 8900 \
  "$PROJECT_ROOT/RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailai-server/target/ruoyi-snailai-server.jar" \
  "$PROJECT_ROOT/logs/ruoyi-snailai-server.log"

echo "SnailJob 控制台：http://127.0.0.1:8800/snail-job/（调度 RPC：17888）"
echo "SnailAI 控制台：http://127.0.0.1:8900/snail-ai/（gRPC：18888）"
