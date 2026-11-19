#!/usr/bin/env bash
# 启动本地 Spring AI Alibaba Agent Studio（PostgreSQL 适配版）。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM_ROOT="$PROJECT_ROOT/third_party/spring-ai-alibaba"
RUNTIME_ROOT="$PROJECT_ROOT/.runtime/spring-ai-alibaba"
SOURCE_ROOT="$RUNTIME_ROOT/source-worktree"
ADMIN_ROOT="$SOURCE_ROOT/spring-ai-alibaba-admin"
MIDDLEWARE_COMPOSE="$PROJECT_ROOT/deploy/spring-ai-alibaba/docker-compose.middleware.yml"
PG_ADAPTER="$PROJECT_ROOT/scripts/spring-ai-alibaba-postgresql.py"
ENV_FILE="$PROJECT_ROOT/.env"
SAA_DATABASE="spring_ai_alibaba_admin"
BACKEND_PORT=8091
FRONTEND_PORT=8910

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少项目根目录 .env；请先按 .env.example 配置现有 PostgreSQL 连接参数。" >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "需要可用的 Docker Engine 和 Docker Compose 插件。" >&2
  exit 1
fi
if ! command -v psql >/dev/null 2>&1 || ! command -v createdb >/dev/null 2>&1; then
  echo "需要使用本机 PostgreSQL 客户端连接现有 PostgreSQL 服务。" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
if [[ -z "$DB_USERNAME" || -z "$DB_PASSWORD" ]]; then
  echo ".env 中缺少 PostgreSQL 用户名或密码配置。" >&2
  exit 1
fi

mkdir -p "$PROJECT_ROOT/logs"
mkdir -p "$RUNTIME_ROOT/elasticsearch" "$RUNTIME_ROOT/nacos/logs" "$RUNTIME_ROOT/nacos/data" "$RUNTIME_ROOT/rocketmq/store"
if [[ "$(stat -c '%u' "$RUNTIME_ROOT/rocketmq/store")" != "3000" ]]; then
  sudo -n chown -R 3000:3000 "$RUNTIME_ROOT/rocketmq/store"
fi
export SAA_UID="$(id -u)"
export SAA_GID="$(id -g)"
NACOS_AUTH_TOKEN_FILE="$RUNTIME_ROOT/nacos-auth-token"
NACOS_AUTH_IDENTITY_KEY_FILE="$RUNTIME_ROOT/nacos-auth-identity-key"
NACOS_AUTH_IDENTITY_VALUE_FILE="$RUNTIME_ROOT/nacos-auth-identity-value"
if [[ ! -s "$NACOS_AUTH_TOKEN_FILE" ]]; then
  (umask 077; openssl rand -base64 32 | tr -d '\n' > "$NACOS_AUTH_TOKEN_FILE")
fi
if [[ ! -s "$NACOS_AUTH_IDENTITY_KEY_FILE" ]]; then
  (umask 077; openssl rand -hex 24 > "$NACOS_AUTH_IDENTITY_KEY_FILE")
fi
if [[ ! -s "$NACOS_AUTH_IDENTITY_VALUE_FILE" ]]; then
  (umask 077; openssl rand -base64 24 | tr -d '\n' > "$NACOS_AUTH_IDENTITY_VALUE_FILE")
fi
export NACOS_AUTH_TOKEN="$(cat "$NACOS_AUTH_TOKEN_FILE")"
export NACOS_AUTH_IDENTITY_KEY="$(cat "$NACOS_AUTH_IDENTITY_KEY_FILE")"
export NACOS_AUTH_IDENTITY_VALUE="$(cat "$NACOS_AUTH_IDENTITY_VALUE_FILE")"

docker compose -p spring-ai-alibaba-test -f "$MIDDLEWARE_COMPOSE" config --quiet
docker compose -p spring-ai-alibaba-test -f "$MIDDLEWARE_COMPOSE" up -d

echo "等待 Spring AI Alibaba 搜索服务就绪..."
ready=0
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:19200/_cluster/health >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 3
done
if [[ "$ready" != "1" ]]; then
  echo "Elasticsearch 尚未就绪；可查看 spring-ai-alibaba-elasticsearch 容器日志。" >&2
  exit 1
fi

if [[ ! -e "$SOURCE_ROOT/.git" ]]; then
  mkdir -p "$RUNTIME_ROOT"
  git -C "$UPSTREAM_ROOT" worktree add --detach "$SOURCE_ROOT" HEAD
fi
if [[ ! -f "$ADMIN_ROOT/pom.xml" ]]; then
  echo "Spring AI Alibaba 子模块中未找到官方 Agent Studio 源码。" >&2
  exit 1
fi

python3 "$PG_ADAPTER" prepare "$ADMIN_ROOT"

db_exists="$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='$SAA_DATABASE'" 2>/dev/null || true)"
if [[ "$db_exists" != "1" ]]; then
  if [[ "$DB_HOST" != "127.0.0.1" && "$DB_HOST" != "localhost" ]]; then
    echo "尚未创建 Agent Studio PostgreSQL 数据库；请由数据库管理员创建并把所有者设为项目 DB_USER。" >&2
    exit 1
  fi
  sudo -n -u postgres createdb -O "$DB_USERNAME" "$SAA_DATABASE"
fi

schema_ready="$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$SAA_DATABASE" -Atqc "SELECT to_regclass('public.account') IS NOT NULL" 2>/dev/null || true)"
if [[ "$schema_ready" != "t" ]]; then
  python3 "$PG_ADAPTER" schema "$ADMIN_ROOT" | PGPASSWORD="$DB_PASSWORD" psql -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$SAA_DATABASE"
fi

# Agent Studio 的模型和供应商实体使用 Boolean；将原始 0/1 开关转换为 PostgreSQL boolean。
PGPASSWORD="$DB_PASSWORD" psql -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$SAA_DATABASE" <<'SQL'
DO $$
DECLARE v_table_name text; invalid_count bigint;
BEGIN
  FOREACH v_table_name IN ARRAY ARRAY['model', 'provider'] LOOP
    IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema = 'public'
               AND c.table_name = v_table_name AND c.column_name = 'enable' AND c.data_type = 'smallint') THEN
      EXECUTE format('SELECT count(*) FROM public.%I WHERE enable NOT IN (0, 1)', v_table_name) INTO invalid_count;
      IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Unexpected enable values in %', v_table_name;
      END IF;
      EXECUTE format('ALTER TABLE public.%I ALTER COLUMN enable DROP DEFAULT', v_table_name);
      EXECUTE format('ALTER TABLE public.%I ALTER COLUMN enable TYPE boolean USING (enable <> 0)', v_table_name);
      EXECUTE format('ALTER TABLE public.%I ALTER COLUMN enable SET DEFAULT true', v_table_name);
    END IF;
  END LOOP;
END $$;
SQL

if [[ ! -f "$ADMIN_ROOT/spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar" ]]; then
  echo "首次构建 Spring AI Alibaba Agent Studio 后端..."
  mvn -f "$ADMIN_ROOT/pom.xml" -pl spring-ai-alibaba-admin-server-start -am -DskipTests package
fi

if [[ ! -x "$ADMIN_ROOT/frontend/node_modules/.bin/umi" ]]; then
  echo "首次安装 Agent Studio 前端依赖..."
  (cd "$ADMIN_ROOT/frontend" && npm install --ignore-scripts)
fi
if [[ ! -f "$ADMIN_ROOT/frontend/packages/spark-flow/dist/index.js" ]]; then
  echo "构建 Agent Studio 流程编辑器..."
  (cd "$ADMIN_ROOT/frontend" && npm run build:flow)
fi
python3 "$PG_ADAPTER" prepare-frontend "$ADMIN_ROOT"

FRONTEND_ENV="$ADMIN_ROOT/frontend/packages/main/.env"
if [[ ! -f "$FRONTEND_ENV" ]]; then
  cp "$ADMIN_ROOT/frontend/packages/main/.env.example" "$FRONTEND_ENV"
fi
if ! grep -Fxq 'WEB_SERVER="http://127.0.0.1:8091"' "$FRONTEND_ENV"; then
  if grep -q '^WEB_SERVER=' "$FRONTEND_ENV"; then
    sed -i 's#^WEB_SERVER=.*#WEB_SERVER="http://127.0.0.1:8091"#' "$FRONTEND_ENV"
  else
    printf '%s\n' 'WEB_SERVER="http://127.0.0.1:8091"' >> "$FRONTEND_ENV"
  fi
fi

if ! curl -fsS --max-time 2 "http://127.0.0.1:$BACKEND_PORT/actuator/health" >/dev/null 2>&1; then
  BACKEND_LOG="$PROJECT_ROOT/logs/spring-ai-alibaba-backend.log"
  BACKEND_PID_FILE="$PROJECT_ROOT/logs/spring-ai-alibaba-backend.pid"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$SAA_DATABASE"
  export SPRING_DATASOURCE_USERNAME="$DB_USERNAME"
  export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
  export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
  export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
  export SPRING_JPA_PROPERTIES_JAKARTA_PERSISTENCE_JDBC_DRIVER=org.postgresql.Driver
  export SPRING_REDIS_HOST=127.0.0.1
  export SPRING_REDIS_PORT=6379
  export SPRING_REDIS_DATABASE=15
  if [[ -n "${REDIS_PASSWORD:-}" ]]; then
    export SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD"
  fi
  export SPRING_ELASTICSEARCH_URIS=http://127.0.0.1:19200
  export SPRING_ELASTICSEARCH_URL=http://127.0.0.1:19200
  export NACOS_SERVER_ADDR=127.0.0.1:18848
  export ROCKETMQ_ENDPOINTS=127.0.0.1:18081
  export MANAGEMENT_OTLP_TRACING_EXPORT_ENDPOINT=http://127.0.0.1:14318/v1/traces
  nohup setsid java -jar "$ADMIN_ROOT/spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar" \
    --server.address=127.0.0.1 \
    --server.port="$BACKEND_PORT" \
    --spring.datasource.driver-class-name=org.postgresql.Driver \
    --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
    --spring.jpa.properties.jakarta.persistence.jdbc.driver=org.postgresql.Driver \
    > "$BACKEND_LOG" 2>&1 < /dev/null &
  echo "$!" > "$BACKEND_PID_FILE"
fi

echo "等待 Spring AI Alibaba 后端启动..."
ready=0
for attempt in $(seq 1 90); do
  if curl -fsS --max-time 3 "http://127.0.0.1:$BACKEND_PORT/actuator/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 3
done
if [[ "$ready" != "1" ]]; then
  echo "Agent Studio 后端尚未就绪；查看 logs/spring-ai-alibaba-backend.log。" >&2
  exit 1
fi

if ! curl -fsS --max-time 2 "http://127.0.0.1:$FRONTEND_PORT/" >/dev/null 2>&1; then
  (cd "$ADMIN_ROOT/frontend/packages/main" && npm run setup)
  FRONTEND_LOG="$PROJECT_ROOT/logs/spring-ai-alibaba-frontend.log"
  FRONTEND_PID_FILE="$PROJECT_ROOT/logs/spring-ai-alibaba-frontend.pid"
  (
    cd "$ADMIN_ROOT/frontend/packages/main"
    nohup setsid env WEB_SERVER="http://127.0.0.1:$BACKEND_PORT" HOST=127.0.0.1 PORT="$FRONTEND_PORT" npm run dev > "$FRONTEND_LOG" 2>&1 < /dev/null &
    echo "$!" > "$FRONTEND_PID_FILE"
  )
fi

echo "等待 Spring AI Alibaba 前端启动..."
ready=0
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 3 "http://127.0.0.1:$FRONTEND_PORT/" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then
  echo "Agent Studio 前端尚未就绪；查看 logs/spring-ai-alibaba-frontend.log。" >&2
  exit 1
fi

echo "Spring AI Alibaba Agent Studio 已启动：http://127.0.0.1:$FRONTEND_PORT"
echo "后端健康检查：http://127.0.0.1:$BACKEND_PORT/actuator/health"
echo "未启动 MySQL；Agent Studio 使用现有 PostgreSQL 的独立数据库。"
