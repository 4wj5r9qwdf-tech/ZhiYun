#!/usr/bin/env bash
# 生成可独立分发的织云 Spring AI Alibaba 源码补丁包。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE_ROOT="$PROJECT_ROOT/deploy/spring-ai-alibaba"
OUTPUT_DIR="$PACKAGE_ROOT/releases"
PACKAGE_NAME="zhiyun-spring-ai-alibaba-patch-20261120-v1.tar.gz"
STAGING_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGING_DIR"' EXIT

mkdir -p "$OUTPUT_DIR"
EXPECTED_PATCH_SHA256="8daa94e6ef76b2de8808e4f93a32ecf9f09ce41b0f1786fbd93652162d0ef166"
ACTUAL_PATCH_SHA256="$(sha256sum "$PACKAGE_ROOT/patches/0001-zhiyun-agent-studio-postgresql.patch" | awk '{print $1}')"
if [[ "$ACTUAL_PATCH_SHA256" != "$EXPECTED_PATCH_SHA256" ]]; then
  echo "补丁文件与版本清单不一致，拒绝打包。" >&2
  exit 1
fi

cp "$PACKAGE_ROOT/README.md" "$STAGING_DIR/README.md"
cp "$PACKAGE_ROOT/apply-patches.sh" "$STAGING_DIR/apply-patches.sh"
cp "$PACKAGE_ROOT/patches/manifest.json" "$STAGING_DIR/manifest.json"
mkdir -p "$STAGING_DIR/patches"
cp "$PACKAGE_ROOT/patches/0001-zhiyun-agent-studio-postgresql.patch" "$STAGING_DIR/patches/"

(
  cd "$STAGING_DIR"
  sha256sum README.md apply-patches.sh manifest.json patches/0001-zhiyun-agent-studio-postgresql.patch > SHA256SUMS
)

tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  -C "$STAGING_DIR" -cf - README.md apply-patches.sh manifest.json patches SHA256SUMS \
  | gzip -n > "$OUTPUT_DIR/$PACKAGE_NAME"

echo "补丁包已生成：$OUTPUT_DIR/$PACKAGE_NAME"
sha256sum "$OUTPUT_DIR/$PACKAGE_NAME"
