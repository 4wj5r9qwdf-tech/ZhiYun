#!/usr/bin/env bash
# 文件用途：执行“stop-spring-ai-alibaba.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“stop-spring-ai-alibaba.sh”对应的本地开发、诊断或数据核验辅助操作。
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
# 停止 Spring AI Alibaba Agent Studio 和专用中间件容器，保留本地数据库与文件。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME_ROOT="$PROJECT_ROOT/.runtime/spring-ai-alibaba"
if [[ -s "$RUNTIME_ROOT/nacos-auth-token" && -s "$RUNTIME_ROOT/nacos-auth-identity-key" && -s "$RUNTIME_ROOT/nacos-auth-identity-value" ]]; then
  NACOS_AUTH_TOKEN="$(< "$RUNTIME_ROOT/nacos-auth-token")"
  NACOS_AUTH_IDENTITY_KEY="$(< "$RUNTIME_ROOT/nacos-auth-identity-key")"
  NACOS_AUTH_IDENTITY_VALUE="$(< "$RUNTIME_ROOT/nacos-auth-identity-value")"
else
  NACOS_AUTH_TOKEN="status-only-placeholder"
  NACOS_AUTH_IDENTITY_KEY="status-only-placeholder"
  NACOS_AUTH_IDENTITY_VALUE="status-only-placeholder"
fi
export NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
for service in frontend backend; do
  pid_file="$PROJECT_ROOT/logs/spring-ai-alibaba-$service.pid"
  if [[ -f "$pid_file" ]]; then
    process_id="$(cat "$pid_file")"
    if [[ "$process_id" =~ ^[0-9]+$ ]] && kill -0 "$process_id" 2>/dev/null; then
      kill -- "-$process_id" 2>/dev/null || kill "$process_id" 2>/dev/null || true
      echo "已停止 Agent Studio $service 进程。"
    fi
    rm -f "$pid_file"
  fi
done

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose -p spring-ai-alibaba-test -f "$PROJECT_ROOT/deploy/spring-ai-alibaba/docker-compose.middleware.yml" down
fi
echo "已停止 Agent Studio 服务，数据库和运行数据已保留。"
