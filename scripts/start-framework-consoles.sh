#!/usr/bin/env bash
# 文件用途：启动 SnailJob、SnailAI 管理控制台及按配置启用的本地 Docling 服务。
# 文件版本：1.0.1
# 功能目的：统一启动本地框架控制台，并在未配置 Docling 覆盖项时自动启动隔离的开发解析容器。
# 设计思路：尊重 .env 显式启用/禁用和远程地址；默认本地模式受 Docker 与可用内存检查保护，失败时不阻止 ERP 控制台启动。
# 实现方法：加载本地环境、校验框架凭据、按需启动 Docling 并仅传递密钥文件路径，再启动 SnailJob 与 SnailAI。
# 关键约束：不输出密钥；Docling 开发容器仅用于本机开发，资源不足或 Docker 不可用时提示并继续启动控制台。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY（Codex辅助）
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-24
# 修改标记：ZY-ERP-20261124-文档解析自启动；本地 Docling 随框架控制台按需启动
# 最近测试时间：2026-11-24
# 测试人员：JY（测试责任人）
# 测试状态：Shell 静态检查通过；实际 XLSX 解析服务冒烟通过；启动脚本完整流程未在本工作区运行
# 测试范围与记录位置：本轮运行验证与限制见当前交付说明；本工作区缺少 .env，未启动升级分支服务
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

# 用户显式配置 enabled=false 时保持关闭；配置了远程地址或密钥文件时不覆盖用户服务地址。
# 未提供 Docling 覆盖配置时，自动尝试启动受资源限制的本地开发容器。
docling_key_file="${XDG_CONFIG_HOME:-$HOME/.config}/zhiyun/docling-dev.key"
if [[ -z "${SNAIL_AI_RAG_DOCLING_ENABLED+x}" \
    && -z "${SNAIL_AI_RAG_DOCLING_URL:-}" \
    && -z "${SNAIL_AI_RAG_DOCLING_API_KEY_FILE:-}" ]]; then
  if command -v docker >/dev/null 2>&1; then
    if bash "$PROJECT_ROOT/scripts/docling-dev.sh" start; then
      docling_address="$(docker inspect --format '{{if .State.Running}}{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}{{end}}' zhiyun-docling-dev 2>/dev/null || true)"
      if [[ "$docling_address" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ && -r "$docling_key_file" ]]; then
        export SNAIL_AI_RAG_DOCLING_ENABLED=true
        export SNAIL_AI_RAG_DOCLING_URL="http://${docling_address}:5001"
        export SNAIL_AI_RAG_DOCLING_API_KEY_FILE="$docling_key_file"
        echo "本地 Docling 文档解析服务已启用（容器网络地址 ${docling_address}:5001）。"
      else
        echo "警告：Docling 容器未提供可用地址或密钥文件，SnailAI 将按当前配置启动。" >&2
      fi
    else
      echo "警告：本地 Docling 未能启动，文档解析功能暂不可用；继续启动 SnailJob 与 SnailAI。" >&2
    fi
  else
    echo "提示：未安装 Docker，跳过本地 Docling；远程服务请在 .env 中显式配置。"
  fi
fi

# 若 .env 显式提供远程地址或密钥路径但未写 enabled，则开发 profile 的默认 false 会屏蔽该配置；显式 false 仍优先。
if [[ -z "${SNAIL_AI_RAG_DOCLING_ENABLED+x}" \
    && ( -n "${SNAIL_AI_RAG_DOCLING_URL:-}" || -n "${SNAIL_AI_RAG_DOCLING_API_KEY_FILE:-}" ) ]]; then
  export SNAIL_AI_RAG_DOCLING_ENABLED=true
  echo "已按 .env 中的 Docling 地址/密钥文件配置启用解析客户端。"
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
