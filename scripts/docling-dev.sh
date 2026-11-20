#!/usr/bin/env bash
# 文件用途：执行“docling-dev.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“docling-dev.sh”对应的本地开发、诊断或数据核验辅助操作。
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
set -euo pipefail

# 仅供本地验证；密钥存用户私有配置目录，绝不写入仓库。
# Development only; keep the service key outside the repository.
key_dir="${XDG_CONFIG_HOME:-$HOME/.config}/zhiyun"
key_file="$key_dir/docling-dev.key"
umask 077
mkdir -p "$key_dir"
if [[ ! -e "$key_file" ]]; then
  (set -o noclobber; openssl rand -hex 32 > "$key_file") || {
    [[ -s "$key_file" ]] || { printf '无法创建 Docling 服务密钥。\n' >&2; exit 1; }
  }
fi
[[ -s "$key_file" ]] || { printf 'Docling 服务密钥为空，请检查本地配置。\n' >&2; exit 1; }
export ZY_DOCLING_API_KEY
ZY_DOCLING_API_KEY="$(<"$key_file")"
export DOCLING_SERVE_API_KEY="$ZY_DOCLING_API_KEY"
container_name="zhiyun-docling-dev"
network_name="zhiyun-docling-isolated"
image="quay.io/docling-project/docling-serve-cpu:v1.18.0"

case "${1:-}" in
  start)
    # 显式检查空闲内存，避免与正在运行的 ERP 抢占资源。
    # Refuse to compete with ERP when available memory is too low.
    available_kb="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
    if (( available_kb < 3145728 )); then
      printf '可用内存不足 3 GB，未启动 Docling，避免影响 ERP。\n' >&2
      exit 1
    fi
    if docker container inspect "$container_name" >/dev/null 2>&1; then
      docker start "$container_name" >/dev/null
      exit 0
    fi
    if ! docker network inspect "$network_name" >/dev/null 2>&1; then
      docker network create --internal "$network_name" >/dev/null
    fi
    docker run -d --name "$container_name" --restart no \
      --network "$network_name" \
      --memory 3g --cpus 2 --pids-limit 256 --cap-drop ALL \
      --security-opt no-new-privileges:true \
      -e DOCLING_SERVE_API_KEY -e DOCLING_SERVE_ENABLE_REMOTE_SERVICES=false \
      -e DOCLING_SERVE_ALLOW_EXTERNAL_PLUGINS=false -e DOCLING_SERVE_ENABLE_UI=false \
      -e DOCLING_SERVE_ENG_LOC_NUM_WORKERS=1 -e DOCLING_SERVE_MAX_FILE_SIZE=10485760 \
      -e DOCLING_SERVE_MAX_NUM_PAGES=100 -e DOCLING_SERVE_MAX_DOCUMENT_TIMEOUT=180 \
      -e OMP_NUM_THREADS=2 -e HF_HUB_OFFLINE=1 "$image" >/dev/null
    ;;
  stop)
    if docker container inspect "$container_name" >/dev/null 2>&1; then
      docker stop "$container_name" >/dev/null
    fi
    ;;
  status)
    docker ps -a --filter "name=^/${container_name}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container_name" 2>/dev/null || true)" == true ]]; then
      docker inspect --format '容器内部地址：{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}:5001' "$container_name"
    fi
    ;;
  *) printf '用法：%s {start|stop|status}\n' "$0" >&2; exit 2 ;;
esac
