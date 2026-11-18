#!/usr/bin/env bash
# 使用 ERP 超管环境凭据启动本地 Spring Boot Admin 监控台。
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
cd RuoYi-Vue-Plus
exec java -jar ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar --spring.profiles.active=dev
