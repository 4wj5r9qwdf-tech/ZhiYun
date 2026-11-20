#!/usr/bin/env bash
# 文件用途：执行“start-framework-consoles.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“start-framework-consoles.sh”对应的本地开发、诊断或数据核验辅助操作。
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
