#!/usr/bin/env bash
# 文件用途：执行“status.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“status.sh”对应的本地开发、诊断或数据核验辅助操作。
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
# 织云 ERP - 状态检查脚本
# 用法：bash scripts/status.sh
set -e

echo "=== 织云 ERP 服务状态 ==="

# 1. 监控中心
echo "[监控中心] :9090"
if ss -tlnp 2>/dev/null | grep -q ':9090'; then
  echo "  ✓ 运行中"
else
  echo "  ✗ 未运行"
fi

# 2. 后端
echo "[后端] :8080"
if ss -tlnp 2>/dev/null | grep -q ':8080'; then
  echo "  ✓ 运行中"
  # Hello 接口验证
  RESULT=$(curl -sS --max-time 3 http://127.0.0.1:8080/hello 2>/dev/null | head -c 200)
  if echo "$RESULT" | grep -q '"code":200'; then
    echo "  ✓ Hello 接口正常"
  else
    echo "  ✗ Hello 接口异常: $RESULT"
  fi
else
  echo "  ✗ 未运行"
fi

# 3. 前端
echo "[前端] :5273"
if ss -tlnp 2>/dev/null | grep -q ':5273'; then
  echo "  ✓ 运行中"
else
  echo "  ✗ 未运行"
fi

# 4. 独立框架控制台与内部调度端口
for PORT_INFO in "8800:SnailJob 管理台" "17888:SnailJob 调度 RPC" "8900:SnailAI 管理台" "18888:SnailAI gRPC"; do
  PORT="${PORT_INFO%%:*}"
  NAME="${PORT_INFO##*:}"
  echo "[$NAME] :$PORT"
  if ss -tlnp 2>/dev/null | grep -q ":$PORT"; then
    echo "  ✓ 运行中"
  else
    echo "  ✗ 未运行"
  fi
done

# 5. 依赖服务
echo "[依赖服务]"
for PORT_INFO in "5432:PostgreSQL" "6379:Redis" "9000:MinIO"; do
  PORT="${PORT_INFO%%:*}"
  NAME="${PORT_INFO##*:}"
  if ss -tlnp 2>/dev/null | grep -q ":$PORT"; then
    echo "  ✓ $NAME (:$PORT)"
  else
    echo "  ✗ $NAME (:$PORT) 未运行"
  fi
done

echo ""
bash "$(cd "$(dirname "$0")" && pwd)/status-spring-ai-alibaba.sh"

echo "启动: bash scripts/start.sh   停止: bash scripts/stop.sh   重启: bash scripts/stop.sh && bash scripts/start.sh"
