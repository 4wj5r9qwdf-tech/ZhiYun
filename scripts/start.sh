#!/usr/bin/env bash
# 织云 ERP - 启动脚本
# 用法：bash scripts/start.sh
# 启动：后端(RuoYi-Vue-Plus 8080) + 前端(Vue3 dev 5273)
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

# 1. 启动后端（8080）
echo "[1/2] 启动后端 (8080)..."
if port_listening 8080; then
  echo "  后端已在运行（:8080 监听中），跳过"
elif [ -f RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar ]; then
  set -a && source .env && set +a
  cd RuoYi-Vue-Plus
  nohup java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev \
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
echo "[1.5/2] 设置 MinIO 桶公开读 (zhizhun-files)..."
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

# 2. 启动前端（5273）
echo "[2/2] 启动前端 (5273)..."
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

echo ""
echo "=== 启动中，等待服务就绪 ==="
echo "后端: http://127.0.0.1:8080  (日志 $BACKEND_LOG)"
echo "前端: http://localhost:5273/login  (日志 $FRONTEND_LOG)"
echo "查看状态：bash scripts/status.sh"
