#!/usr/bin/env python3
"""为固定版本的 Spring AI Alibaba Agent Studio 准备 PostgreSQL 运行适配。"""

from __future__ import annotations

import argparse
import re
from pathlib import Path


CORE_DEPENDENCY = """        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-admin-server-core</artifactId>
        </dependency>"""
CORE_DEPENDENCY_WITHOUT_MYSQL = """        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-admin-server-core</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>com.mysql</groupId>
                    <artifactId>mysql-connector-j</artifactId>
                </exclusion>
            </exclusions>
        </dependency>"""
MYSQL_DEPENDENCY = """        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>${mysql.version}</version>
        </dependency>"""
POSTGRES_DEPENDENCY = """        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.4.4</version>
        </dependency>"""


def prepare_pom(admin_root: Path) -> None:
    pom = admin_root / "spring-ai-alibaba-admin-server-start/pom.xml"
    content = pom.read_text(encoding="utf-8")

    if CORE_DEPENDENCY in content:
        content = content.replace(CORE_DEPENDENCY, CORE_DEPENDENCY_WITHOUT_MYSQL, 1)
    elif CORE_DEPENDENCY_WITHOUT_MYSQL not in content:
        raise SystemExit("Agent Studio core Maven dependency changed; review PostgreSQL exclusion before building")

    if MYSQL_DEPENDENCY in content:
        content = content.replace(MYSQL_DEPENDENCY, POSTGRES_DEPENDENCY, 1)
    elif POSTGRES_DEPENDENCY not in content:
        raise SystemExit("Agent Studio JDBC Maven dependency changed; review PostgreSQL driver before building")

    pom.write_text(content, encoding="utf-8")

    # 固定版本的 Agent Studio 源码缺少 ThreadPoolExecutor 导入，补齐后才能编译。
    wrapper = admin_root / (
        "spring-ai-alibaba-admin-server-core/src/main/java/"
        "com/alibaba/cloud/ai/studio/core/utils/concurrent/"
        "RequestContextThreadPoolWrapper.java"
    )
    source = wrapper.read_text(encoding="utf-8")
    missing_import = "import java.util.concurrent.ThreadPoolExecutor;"
    if missing_import not in source:
        anchor = "import java.util.concurrent.ExecutorService;\n"
        if anchor not in source or "ThreadPoolExecutor" not in source:
            raise SystemExit("Agent Studio thread-pool source changed; review the compile compatibility patch")
        source = source.replace(anchor, anchor + missing_import + "\n", 1)
        wrapper.write_text(source, encoding="utf-8")

    # MySQL coerces the upstream display string "deleted" to 0 when comparing
    # the numeric status column. PostgreSQL rejects that varchar/smallint
    # comparison, so bind the enum's numeric database value instead.
    workspace_service = admin_root / (
        "spring-ai-alibaba-admin-server-core/src/main/java/"
        "com/alibaba/cloud/ai/studio/core/base/service/impl/WorkspaceServiceImpl.java"
    )
    source = workspace_service.read_text(encoding="utf-8")
    mysql_status_value = ".ne(WorkspaceEntity::getStatus, CommonStatus.DELETED.getValue())"
    postgres_status_value = ".ne(WorkspaceEntity::getStatus, CommonStatus.DELETED.getStatus())"
    if mysql_status_value in source:
        source = source.replace(mysql_status_value, postgres_status_value)
        workspace_service.write_text(source, encoding="utf-8")
    elif source.count(postgres_status_value) < 5:
        raise SystemExit("Agent Studio workspace status query changed; review PostgreSQL enum compatibility patch")

    # 将上游 MySQL 分页语法中的 offset、limit 顺序转为 PostgreSQL 写法。
    mapper_dir = admin_root / "spring-ai-alibaba-admin-server-start/src/main/resources/mapper"
    for mapper in mapper_dir.glob("*.xml"):
        source = mapper.read_text(encoding="utf-8")
        adapted = re.sub(r"\bLIMIT\s+#\{offset\}\s*,\s*#\{limit\}",
                         "LIMIT #{limit} OFFSET #{offset}", source, flags=re.IGNORECASE)
        if adapted != source:
            mapper.write_text(adapted, encoding="utf-8")


def prepare_frontend(admin_root: Path) -> None:
    # 上游接口可能省略空参数数组；规范化后再交给组件表单，避免空列表导致崩溃。
    component_dir = admin_root / "frontend/packages/main/src/pages/Component/AppComponent/components"
    for drawer_name in ("EditDrawer", "DetailDrawer"):
        drawer = component_dir / drawer_name / "index.tsx"
        source = drawer.read_text(encoding="utf-8")
        for config in ("componentDetailCfg", "componentParams"):
            original = f"input: {config}.input,\n"
            normalized = (f"input: {{\n"
                          f"            system_params: {config}.input?.system_params ?? [],\n"
                          f"            user_params: ({config}.input?.user_params ?? []).map((item: any) => ({{\n"
                          f"              ...item, params: item.params ?? [],\n"
                          f"            }})),\n"
                          f"          }},\n")
            source = source.replace(original, normalized)
            source = source.replace(f"output: {config}.output,", f"output: {config}.output ?? [],")
        if "input: componentDetailCfg.input," in source or "input: componentParams.input," in source:
            raise SystemExit("Agent Studio component configuration changed; review empty parameter handling")
        if source != drawer.read_text(encoding="utf-8"):
            drawer.write_text(source, encoding="utf-8")

    node_modules = admin_root / "frontend/node_modules"
    modules = [node_modules / "@umijs/bundler-utils/dist/https.js"]
    modules.extend((node_modules / "@umijs").rglob("bundler-utils/dist/https.js"))
    found = 0
    for https_module in set(modules):
        if not https_module.is_file():
            continue
        found += 1
        source = https_module.read_text(encoding="utf-8")
        eager_import = 'var import_spdy = __toESM(require("spdy"));\n'
        eager_server = "httpsConfig.http2 === false ? import_https.default.createServer : import_spdy.default.createServer;"
        lazy_server = 'httpsConfig.http2 === false ? import_https.default.createServer : require("spdy").createServer;'

        if eager_import in source:
            source = source.replace(eager_import, "", 1)
        elif eager_server not in source and lazy_server not in source:
            raise SystemExit("Umi HTTPS module changed; review Node.js http_parser compatibility patch")

        if eager_server in source:
            source = source.replace(eager_server, lazy_server, 1)
        https_module.write_text(source, encoding="utf-8")
    if not found:
        raise SystemExit("Umi HTTPS module is missing after installing Agent Studio dependencies")


def split_statements(source: str) -> list[str]:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    source = re.sub(r"(?m)^\s*(?:--|#).*$", "", source)
    statements: list[str] = []
    start = 0
    in_single = False
    in_backtick = False
    index = 0

    while index < len(source):
        char = source[index]
        if in_single:
            if char == "\\" and index + 1 < len(source):
                index += 2
                continue
            if char == "'":
                if index + 1 < len(source) and source[index + 1] == "'":
                    index += 2
                    continue
                in_single = False
        elif in_backtick:
            if char == "`":
                in_backtick = False
        elif char == "'":
            in_single = True
        elif char == "`":
            in_backtick = True
        elif char == ";":
            statement = source[start:index].strip()
            if statement:
                statements.append(statement)
            start = index + 1
        index += 1

    trailing = source[start:].strip()
    if trailing:
        statements.append(trailing)
    return statements


def remove_backticks(statement: str) -> str:
    result: list[str] = []
    in_single = False
    in_backtick = False
    index = 0

    while index < len(statement):
        char = statement[index]
        if in_single:
            result.append(char)
            if char == "\\" and index + 1 < len(statement):
                index += 1
                result.append(statement[index])
            elif char == "'":
                if index + 1 < len(statement) and statement[index + 1] == "'":
                    index += 1
                    result.append(statement[index])
                else:
                    in_single = False
        elif in_backtick:
            if char == "`":
                in_backtick = False
            else:
                result.append(char)
        elif char == "'":
            in_single = True
            result.append(char)
        elif char == "`":
            in_backtick = True
        else:
            result.append(char)
        index += 1

    return "".join(result)


def split_columns(body: str) -> list[str]:
    segments: list[str] = []
    start = 0
    depth = 0
    in_single = False
    in_backtick = False
    index = 0

    while index < len(body):
        char = body[index]
        if in_single:
            if char == "\\" and index + 1 < len(body):
                index += 2
                continue
            if char == "'":
                if index + 1 < len(body) and body[index + 1] == "'":
                    index += 2
                    continue
                in_single = False
        elif in_backtick:
            if char == "`":
                in_backtick = False
        elif char == "'":
            in_single = True
        elif char == "`":
            in_backtick = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "," and depth == 0:
            segments.append(body[start:index].strip())
            start = index + 1
        index += 1

    trailing = body[start:].strip()
    if trailing:
        segments.append(trailing)
    return segments


def postgres_type(column: str) -> str:
    column = re.sub(
        r"\b(BIGINT|INT)(?:\s*\(\s*\d+\s*\))?(?:\s+UNSIGNED)?(?:\s+NOT\s+NULL)?\s+AUTO_INCREMENT\b",
        lambda match: f"{match.group(1).upper()} GENERATED BY DEFAULT AS IDENTITY",
        column,
        flags=re.IGNORECASE,
    )
    column = re.sub(r"\s+ON\s+UPDATE\s+CURRENT_TIMESTAMP(?:\s*\(\s*\d+\s*\))?", "", column, flags=re.IGNORECASE)
    column = re.sub(r"\s+COMMENT\s+'(?:\\.|''|[^'])*'", "", column, flags=re.IGNORECASE)
    column = re.sub(r"\bBIGINT\s*\(\s*\d+\s*\)", "BIGINT", column, flags=re.IGNORECASE)
    column = re.sub(r"\bINT\s*\(\s*\d+\s*\)", "INTEGER", column, flags=re.IGNORECASE)
    column = re.sub(r"\bBIGINT\s+UNSIGNED\b", "BIGINT", column, flags=re.IGNORECASE)
    column = re.sub(r"\bINT\s+UNSIGNED\b", "INTEGER", column, flags=re.IGNORECASE)
    column = re.sub(r"\bTINYINT(?:\s*\(\s*\d+\s*\))?", "SMALLINT", column, flags=re.IGNORECASE)
    column = re.sub(r"\bDATETIME\s*\(\s*(\d+)\s*\)", r"TIMESTAMP(\1)", column, flags=re.IGNORECASE)
    column = re.sub(r"\bDATETIME\b", "TIMESTAMP", column, flags=re.IGNORECASE)
    column = re.sub(r"\bLONGTEXT\b|\bMEDIUMTEXT\b|\bJSON\b", "TEXT", column, flags=re.IGNORECASE)
    column = re.sub(r"\bUNSIGNED\b", "", column, flags=re.IGNORECASE)
    return remove_backticks(column).strip()


def convert_create_table(statement: str) -> str:
    match = re.match(r"(?is)^\s*CREATE\s+TABLE\s+`?([\w.]+)`?\s*\(", statement)
    if not match:
        raise ValueError("unrecognized CREATE TABLE statement")
    table = match.group(1)
    open_paren = match.end() - 1
    depth = 0
    in_single = False
    in_backtick = False
    close_paren = -1
    index = open_paren

    while index < len(statement):
        char = statement[index]
        if in_single:
            if char == "\\" and index + 1 < len(statement):
                index += 2
                continue
            if char == "'":
                if index + 1 < len(statement) and statement[index + 1] == "'":
                    index += 2
                    continue
                in_single = False
        elif in_backtick:
            if char == "`":
                in_backtick = False
        elif char == "'":
            in_single = True
        elif char == "`":
            in_backtick = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                close_paren = index
                break
        index += 1

    if close_paren < 0:
        raise ValueError(f"unterminated CREATE TABLE {table}")

    definitions: list[str] = []
    indexes: list[str] = []
    for segment in split_columns(statement[open_paren + 1 : close_paren]):
        unique = re.match(r"(?is)^UNIQUE\s+(?:KEY|INDEX)\s+`?([\w]+)`?\s*\((.*)\)$", segment)
        ordinary = re.match(r"(?is)^(?:KEY|INDEX)\s+`?([\w]+)`?\s*\((.*)\)$", segment)
        if unique:
            name, columns = unique.groups()
            definitions.append(f"CONSTRAINT {name} UNIQUE ({remove_backticks(columns).strip()})")
        elif ordinary:
            name, columns = ordinary.groups()
            indexes.append(f"CREATE INDEX IF NOT EXISTS {table}_{name} ON {table} ({remove_backticks(columns).strip()});")
        else:
            definitions.append(postgres_type(segment))

    create = f"CREATE TABLE {table} (\n  " + ",\n  ".join(definitions) + "\n);"
    return "\n".join([create, *indexes])


def convert_statement(statement: str) -> str:
    if re.match(r"(?is)^\s*DROP\s+TABLE\s+", statement):
        converted = remove_backticks(statement)
        converted = re.sub(r"(?i);?\s*$", "", converted)
        return converted + " CASCADE;"
    if re.match(r"(?is)^\s*CREATE\s+TABLE\s+", statement):
        return convert_create_table(statement)

    converted = remove_backticks(statement).strip()
    if re.search(r"(?i)\bON\s+DUPLICATE\s+KEY\s+UPDATE\b", converted):
        raise ValueError("MySQL ON DUPLICATE KEY UPDATE requires a PostgreSQL-specific rewrite")
    return converted + ";"


def emit_schema(admin_root: Path) -> None:
    sql_root = admin_root / "docker/middleware/init/mysql"
    files = [sql_root / "agentscope-schema.sql", sql_root / "admin-schema.sql"]
    print("BEGIN;")
    print("SET LOCAL TIME ZONE 'UTC';")
    for path in files:
        if not path.is_file():
            raise SystemExit(f"Expected upstream schema file is missing: {path.name}")
        for statement in split_statements(path.read_text(encoding="utf-8")):
            try:
                print(convert_statement(statement))
            except ValueError as error:
                raise SystemExit(f"PostgreSQL schema conversion failed in {path.name}: {error}") from error
    print("COMMIT;")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "prepare-frontend", "schema"))
    parser.add_argument("admin_root", type=Path)
    args = parser.parse_args()

    if args.action == "prepare":
        prepare_pom(args.admin_root)
    elif args.action == "prepare-frontend":
        prepare_frontend(args.admin_root)
    else:
        emit_schema(args.admin_root)


if __name__ == "__main__":
    main()
