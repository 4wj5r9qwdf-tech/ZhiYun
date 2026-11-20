#!/usr/bin/env bash
# 将织云维护补丁应用到匹配版本的 Spring AI Alibaba 源码目录。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PATCH_FILE="$SCRIPT_DIR/patches/0001-zhiyun-agent-studio-postgresql.patch"
PATCH_SHA256="8daa94e6ef76b2de8808e4f93a32ecf9f09ce41b0f1786fbd93652162d0ef166"
UPSTREAM_COMMIT="17e26ff6fe951d605771c1845229ca3edb043634"

if [[ $# -ne 1 ]]; then
  echo "用法：$0 <Spring AI Alibaba 源码仓库目录>" >&2
  exit 2
fi

SOURCE_ROOT="$(cd "$1" 2>/dev/null && pwd)" || {
  echo "源码目录不存在：$1" >&2
  exit 2
}

if [[ ! -e "$SOURCE_ROOT/.git" || ! -f "$SOURCE_ROOT/spring-ai-alibaba-admin/pom.xml" ]]; then
  echo "目标目录不是 Spring AI Alibaba 仓库根目录：$SOURCE_ROOT" >&2
  exit 2
fi
if [[ ! -f "$PATCH_FILE" ]]; then
  echo "补丁文件不存在：$PATCH_FILE" >&2
  exit 2
fi
if ! command -v sha256sum >/dev/null 2>&1; then
  echo "需要 sha256sum 校验补丁包完整性。" >&2
  exit 2
fi

actual_sha256="$(sha256sum "$PATCH_FILE" | awk '{print $1}')"
if [[ "$actual_sha256" != "$PATCH_SHA256" ]]; then
  echo "补丁文件校验失败；请重新获取完整的补丁包。" >&2
  exit 1
fi

actual_commit="$(git -C "$SOURCE_ROOT" rev-parse HEAD)"
if [[ "$actual_commit" != "$UPSTREAM_COMMIT" ]]; then
  echo "补丁仅适用于上游提交 $UPSTREAM_COMMIT。"
  echo "目标源码当前提交为 $actual_commit，请勿强行套用。" >&2
  exit 1
fi

if git -C "$SOURCE_ROOT" apply --reverse --check "$PATCH_FILE" >/dev/null 2>&1; then
  echo "织云补丁已经应用，无需重复处理。"
  exit 0
fi

if ! git -C "$SOURCE_ROOT" apply --check "$PATCH_FILE"; then
  echo "目标源码与补丁上下文不匹配；未修改任何文件。" >&2
  exit 1
fi

git -C "$SOURCE_ROOT" apply "$PATCH_FILE"
git -C "$SOURCE_ROOT" diff --check
echo "织云 Spring AI Alibaba 补丁已应用。请重新构建 Agent Studio 制品后再更新服务。"
