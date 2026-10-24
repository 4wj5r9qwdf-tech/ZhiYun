#!/usr/bin/env bash
set -euo pipefail
export GH_CONFIG_DIR=/home/it/ZhiYun/.ghcfg
mkdir -p "$GH_CONFIG_DIR"
gh auth login --hostname github.com --git-protocol https --web 2>&1 | tee "$GH_CONFIG_DIR/login.log"
echo "=== gh auth login returned rc=$? ==="
echo "=== verify status ==="
gh auth status 2>&1 || true
echo "=== hosts.yml ==="
cat "$GH_CONFIG_DIR/hosts.yml" 2>/dev/null || true
