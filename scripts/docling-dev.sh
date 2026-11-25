#!/usr/bin/env bash
# 文件用途：以资源与网络隔离约束管理本地 Docling 开发容器。
# 文件版本：1.0.2
# 功能目的：提供固定版本、内网隔离、受限资源及凭据外置的本地文档解析服务。
# 设计思路：限制容器网络、CPU、内存、进程、文件大小和页数，不挂载 ERP 文件或数据目录；已有容器与密钥失配风险时拒绝启动。
# 实现方法：仅启动操作读取用户配置目录中的专用 API key；状态查询和停止操作不创建或读取密钥。
# 关键约束：仅限本机开发验证；不输出密钥、不访问生产文件、不删除容器或密钥来停止服务。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY（Codex辅助）
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-24
# 修改标记：ZY-ERP-20261124-文档解析生命周期；复用运行容器并防止缺失密钥导致认证漂移
# 最近测试时间：2026-11-24
# 测试人员：JY（测试责任人）
# 测试状态：Shell 静态检查、当前容器启动/状态及缺失密钥保护均通过
# 测试范围与记录位置：见 Docs/功能任务/ZY-ERP-20261124-聊天附件文档解析服务接入修正.md
set -euo pipefail

# 固定容器标识用于服务生命周期管理；启动、状态和停止均操作本项目专属容器。
container_name="zhiyun-docling-dev"
network_name="zhiyun-docling-isolated"
image="quay.io/docling-project/docling-serve-cpu:v1.18.0"
key_dir="${XDG_CONFIG_HOME:-$HOME/.config}/zhiyun"
key_file="$key_dir/docling-dev.key"

case "${1:-}" in
  start)
    command -v docker >/dev/null 2>&1 || { printf 'Docker 不可用，未启动 Docling。\n' >&2; exit 1; }
    # 旧容器仍保存原 API key；不能在密钥文件丢失时另造新密钥并假装服务可用。
    if [[ ! -e "$key_file" ]] && docker container inspect "$container_name" >/dev/null 2>&1; then
      printf 'Docling 密钥文件缺失但容器仍存在；为避免认证不一致，拒绝生成新密钥。\n' >&2
      exit 1
    fi
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
    if docker container inspect "$container_name" >/dev/null 2>&1; then
      # 已运行容器不额外消耗启动内存；停止状态的容器重新启动前仍需过资源门槛。
      if [[ "$(docker inspect --format '{{.State.Running}}' "$container_name")" == true ]]; then
        exit 0
      fi
      available_kb="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
      if (( available_kb < 3145728 )); then
        printf '可用内存不足 3 GB，未启动 Docling，避免影响 ERP。\n' >&2
        exit 1
      fi
      docker start "$container_name" >/dev/null
      exit 0
    fi
    # 首次创建容器前检查空闲内存，避免与正在运行的 ERP 抢占资源。
    available_kb="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
    if (( available_kb < 3145728 )); then
      printf '可用内存不足 3 GB，未启动 Docling，避免影响 ERP。\n' >&2
      exit 1
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
    if command -v docker >/dev/null 2>&1 && docker container inspect "$container_name" >/dev/null 2>&1; then
      docker stop "$container_name" >/dev/null
    fi
    ;;
  status)
    command -v docker >/dev/null 2>&1 || { printf 'Docker 不可用。\n' >&2; exit 1; }
    docker ps -a --filter "name=^/${container_name}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container_name" 2>/dev/null || true)" == true ]]; then
      docker inspect --format '容器内部地址：{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}:5001' "$container_name"
    fi
    ;;
  *) printf '用法：%s {start|stop|status}\n' "$0" >&2; exit 2 ;;
esac
