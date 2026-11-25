#!/usr/bin/env bash
# 文件用途：检查织云本地应用、框架控制台、Docling 解析容器及依赖服务状态。
# 文件版本：1.0.1
# 功能目的：快速判断本地 ERP、SnailAI、Docling 及依赖服务是否运行。
# 设计思路：仅读取监听端口和容器状态，不读取或输出任何服务凭据。
# 实现方法：按服务检查本机监听端口，并单独检查命名的 Docling 开发容器。
# 关键约束：只读状态检查；本地容器状态不代表远程 Docling 服务状态。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY（Codex辅助）
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-24
# 修改标记：ZY-ERP-20261124-文档解析状态检查；增加本地 Docling 容器状态
# 最近测试时间：2026-11-24
# 测试人员：JY（测试责任人）
# 测试状态：Shell 静态检查通过；状态命令实际运行通过
# 测试范围与记录位置：见当前交付说明；本地 Docling 状态只说明开发容器运行状态
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

# 6. 本地 Docling 开发容器；只报告本机容器，不探测或泄露远程服务配置。
echo "[Docling 本地文档解析] zhiyun-docling-dev"
if command -v docker >/dev/null 2>&1; then
  DOCLING_RUNNING="$(docker inspect --format '{{.State.Running}}' zhiyun-docling-dev 2>/dev/null || true)"
  if [[ "$DOCLING_RUNNING" == "true" ]]; then
    DOCLING_ADDRESS="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' zhiyun-docling-dev 2>/dev/null || true)"
    if [[ "$DOCLING_ADDRESS" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "  ✓ 运行中（容器网络 ${DOCLING_ADDRESS}:5001）"
    else
      echo "  ✓ 容器运行中；容器网络地址暂不可用"
    fi
  else
    echo "  ✗ 未运行"
  fi
else
  echo "  ✗ Docker 不可用"
fi

echo ""
echo "启动: bash scripts/start.sh   停止: bash scripts/stop.sh   重启: bash scripts/stop.sh && bash scripts/start.sh"
