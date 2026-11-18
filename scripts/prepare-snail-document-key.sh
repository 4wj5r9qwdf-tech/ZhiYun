#!/usr/bin/env bash
# 为同机的 SnailAI 和执行进程准备共享签名密钥；不输出或覆盖已有密钥。
set -euo pipefail
key_file="${SNAIL_AI_DOCUMENT_BRIDGE_KEY_FILE:-${HOME}/.config/zhiyun/snail-document-bridge.key}"
if [[ ! -e "$key_file" ]]; then
  umask 077
  mkdir -p "$(dirname "$key_file")"
  temporary_key="$(mktemp "${key_file}.XXXXXX")"
  trap 'rm -f "$temporary_key"' EXIT
  head -c 32 /dev/urandom > "$temporary_key"
  chmod 600 "$temporary_key"
  # 硬链接保证并发启动时只发布一个完整密钥。
  ln "$temporary_key" "$key_file" 2>/dev/null || [[ -f "$key_file" ]]
fi
if [[ ! -f "$key_file" || ! -r "$key_file" || "$(wc -c < "$key_file")" -ne 32 ]]; then
  echo '错误：智能体文件工具签名密钥不可用。' >&2
  exit 1
fi
