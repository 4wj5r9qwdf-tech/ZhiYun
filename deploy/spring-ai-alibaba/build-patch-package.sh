#!/usr/bin/env bash
# 文件用途：生成可独立分发的织云 Spring AI Alibaba 源码补丁与构建辅助包。
# 文件版本：1.0.0
# 功能目的：将固定上游版本补丁、PostgreSQL/前端构建辅助脚本和校验清单打包为可复现归档。
# 设计思路：打包前核对补丁 SHA-256，使用临时目录生成固定元数据归档，避免把本地临时文件带入发布物。
# 实现方法：复制清单文件、计算包内 SHA-256，并按稳定排序、时间戳和归属生成 gzip 压缩包。
# 关键约束：不覆盖补丁源文件；补丁校验失败时停止；生成物只写入项目 releases 目录。
# 依赖与引用：deploy/spring-ai-alibaba/README.md；scripts/spring-ai-alibaba-postgresql.py
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：2026-11-20
# 当前责任人：JY（项目维护责任）
# 最近修改人员：JY
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-21
# 修改标记：ZY-ERP-20261121-构建验证；将构建辅助脚本随补丁包一并分发
# 最近测试时间：2026-11-21
# 测试人员：JY
# 测试状态：通过（包内校验、字节级可复现打包及归档前端构建验证）
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE_ROOT="$PROJECT_ROOT/deploy/spring-ai-alibaba"
OUTPUT_DIR="$PACKAGE_ROOT/releases"
PACKAGE_NAME="zhiyun-spring-ai-alibaba-patch-20261121-v2.tar.gz"
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
cp "$PROJECT_ROOT/scripts/spring-ai-alibaba-postgresql.py" "$STAGING_DIR/spring-ai-alibaba-postgresql.py"
cp "$PACKAGE_ROOT/patches/manifest.json" "$STAGING_DIR/manifest.json"
mkdir -p "$STAGING_DIR/patches"
cp "$PACKAGE_ROOT/patches/0001-zhiyun-agent-studio-postgresql.patch" "$STAGING_DIR/patches/"

(
  cd "$STAGING_DIR"
  sha256sum README.md apply-patches.sh spring-ai-alibaba-postgresql.py manifest.json \
    patches/0001-zhiyun-agent-studio-postgresql.patch > SHA256SUMS
)

tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  -C "$STAGING_DIR" -cf - README.md apply-patches.sh spring-ai-alibaba-postgresql.py manifest.json patches SHA256SUMS \
  | gzip -n > "$OUTPUT_DIR/$PACKAGE_NAME"

echo "补丁包已生成：$OUTPUT_DIR/$PACKAGE_NAME"
sha256sum "$OUTPUT_DIR/$PACKAGE_NAME"
