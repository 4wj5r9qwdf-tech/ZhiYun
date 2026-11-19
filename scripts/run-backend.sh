#!/usr/bin/env bash
# 交给 systemd 或用户终端运行后端，环境只在子进程加载，不输出凭据。
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
skills_directory="${SNAIL_AI_ERP_SKILLSDIRECTORY:-$PWD/Docs/Skills}"
cd RuoYi-Vue-Plus
exec java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev "--snail-ai.erp.skills-directory=$skills_directory"
