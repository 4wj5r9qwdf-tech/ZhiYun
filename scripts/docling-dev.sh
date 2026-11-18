#!/usr/bin/env bash
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
