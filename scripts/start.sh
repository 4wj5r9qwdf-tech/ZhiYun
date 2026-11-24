#!/usr/bin/env bash
# 文件用途：执行“start.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“start.sh”对应的本地开发、诊断或数据核验辅助操作。
# 设计思路：将可重复环境操作收敛到脚本，依赖环境变量并避免凭据进入代码或输出。
# 实现方法：检查参数和本地环境，再执行目标命令或只读核验并返回明确退出状态。
# 关键约束：不输出凭据、不操作生产数据、不执行无关破坏性操作。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：Codex（智能体）
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-24
# 修改标记：ZY-ERP-20261124-SAA平台退役；移除 Agent Studio 自动启动
# 最近测试时间：2026-11-24
# 测试人员：Codex（智能体）
# 测试状态：静态检查通过；未执行服务全量回归
# 测试范围与记录位置：见 Docs/功能任务/ZY-ERP-20261124-SAA测试平台退役.md
# 织云 ERP - 启动脚本
# 用法：bash scripts/start.sh
# 启动：监控中心(9090) + 后端(RuoYi-Vue-Plus 8080) + 前端(Vue3 dev 5273) + SnailJob + SnailAI
# 日志：写项目内 logs/（已 gitignore）；不再用 /tmp（agent 沙箱里 /tmp 是隔离 tmpfs，外部看不见日志）
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
mkdir -p logs

BACKEND_LOG="$PROJECT_ROOT/logs/ruoyi-admin.log"
FRONTEND_LOG="$PROJECT_ROOT/logs/frontend.log"

# 端口是否已被监听（1=在 0=否）
port_listening() { ss -tln 2>/dev/null | grep -qE ":$1([[:space:]]|$)"; }

# ===== agent 环境探测 =====
# 特征1：/tmp 是独立 tmpfs（bwrap 沙箱会 --tmpfs /tmp）
# 特征2：祖先进程链里有 bwrap 或 DSH agent（node dsh）
IN_SANDBOX=0
grep -qE '^tmpfs +/tmp ' /proc/mounts 2>/dev/null && IN_SANDBOX=1
_ANC=$$
for _i in 1 2 3 4 5 6 7 8; do
  _PP=$(awk '{print $4}' "/proc/$_ANC/stat" 2>/dev/null) || break
  [[ -z "$_PP" || "$_PP" == "0" || "$_PP" == "1" ]] && break
  _CMD=$(tr '\0' ' ' < "/proc/$_PP/cmdline" 2>/dev/null)
  case "$_CMD" in
    *bwrap*|*dsh*) IN_SANDBOX=1; break ;;
  esac
  _ANC=$_PP
done

echo "=== 织云 ERP 启动 ==="
if [[ "$IN_SANDBOX" == "1" ]]; then
  echo "⚠ 检测到 agent 会话环境（bwrap/DSH），后台进程的存活没有保证："
  echo "  · 在【普通 WSL 终端】里跑本脚本：nohup 进程正常存活，无此限制；"
  echo "  · 在 agent 会话里：进程可能随命令/终端会话结束被回收，"
  echo "    若需要服务持续运行，请让 agent 在【持久终端】里执行本脚本。"
  echo ""
fi

# 1. 启动 Spring Boot Admin 监控中心（9090）
echo "[1/5] 启动监控中心 (9090)..."
if port_listening 9090; then
  echo "  监控中心已在运行（:9090 监听中），跳过"
elif [ -f RuoYi-Vue-Plus/ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar ]; then
  MONITOR_LOG="$PROJECT_ROOT/logs/ruoyi-monitor-admin.log"
  nohup bash scripts/run-monitor-admin.sh \
    > "$MONITOR_LOG" 2>&1 < /dev/null &
  MONITOR_PID=$!
  disown "$MONITOR_PID" 2>/dev/null || true
  echo "  监控中心 PID: $MONITOR_PID  日志: $MONITOR_LOG"
else
  echo "  错误：监控中心 jar 不存在，请先构建 ruoyi-monitor-admin 模块"
  exit 1
fi

# 2. 启动后端（8080）
echo "[2/5] 启动后端 (8080)..."
if port_listening 8080; then
  echo "  后端已在运行（:8080 监听中），跳过"
elif [ -f RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar ]; then
  set -a && source .env && set +a
  bash "$PROJECT_ROOT/scripts/prepare-snail-document-key.sh"
  skills_directory="${SNAIL_AI_ERP_SKILLSDIRECTORY:-$PWD/Docs/Skills}"
  cd RuoYi-Vue-Plus
  nohup java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev "--snail-ai.erp.skills-directory=$skills_directory" \
    > "$BACKEND_LOG" 2>&1 < /dev/null &
  BACKEND_PID=$!
  disown "$BACKEND_PID" 2>/dev/null || true
  echo "  后端 PID: $BACKEND_PID  日志: $BACKEND_LOG"
  cd "$PROJECT_ROOT"
else
  echo "  错误：jar 不存在，请先构建：cd RuoYi-Vue-Plus && ./mvnw -pl ruoyi-admin -am package -DskipTests"
  exit 1
fi

# 1.5 设置 MinIO 桶公开读（logo/登录背景等图片浏览器直显；容器重启会丢，故每次启动都设）
echo "[2.5/5] 设置 MinIO 桶公开读 (zhizhun-files)..."
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^zhizhun-minio$'; then
  set -a && source .env && set +a
  docker exec zhizhun-minio sh -c "mc alias set local http://127.0.0.1:9000 '$OSS_ACCESS_KEY' '$OSS_SECRET_KEY' >/dev/null 2>&1 && mc anonymous set download local/$OSS_BUCKET" >/dev/null 2>&1 \
    && echo "  桶 $OSS_BUCKET 已设公开读" \
    || echo "  警告：设置桶公开读失败（图片可能 403），检查 MinIO 容器与 .env OSS_*"
  # 私有桶（合同/证件/工资单等敏感文件，走代理接口带鉴权访问，容器重启会丢故每次确保）
  docker exec zhizhun-minio sh -c "mc alias set local http://127.0.0.1:9000 '$OSS_ACCESS_KEY' '$OSS_SECRET_KEY' >/dev/null 2>&1 && mc mb local/zhizhun-private --ignore-existing && mc anonymous set none local/zhizhun-private" >/dev/null 2>&1 \
    && echo "  私有桶 zhizhun-private 已确保（私有）" \
    || echo "  警告：私有桶 zhizhun-private 未就绪"
else
  echo "  跳过：MinIO 容器未运行"
fi

# 3. 启动前端（5273）
echo "[3/5] 启动前端 (5273)..."
if port_listening 5273; then
  echo "  前端已在运行（:5273 监听中），跳过"
else
  cd frontend
  nohup pnpm dev > "$FRONTEND_LOG" 2>&1 < /dev/null &
  FRONTEND_PID=$!
  disown "$FRONTEND_PID" 2>/dev/null || true
  echo "  前端 PID: $FRONTEND_PID  日志: $FRONTEND_LOG"
  cd "$PROJECT_ROOT"
fi

# 4. 启动独立框架控制台（SnailJob 与 SnailAI）
bash "$PROJECT_ROOT/scripts/start-framework-consoles.sh"

echo ""
echo "=== 启动中，等待服务就绪 ==="
echo "监控中心: http://127.0.0.1:9090/admin  (日志 $PROJECT_ROOT/logs/ruoyi-monitor-admin.log)"
echo "后端: http://127.0.0.1:8080  (日志 $BACKEND_LOG)"
echo "前端: http://localhost:5273/login  (日志 $FRONTEND_LOG)"
echo "SnailJob: http://127.0.0.1:8800/snail-job/  SnailAI: http://127.0.0.1:8900/snail-ai/"
echo "查看状态：bash scripts/status.sh"
