"""MCP Server 自测：list_tools + 真实调用（需后端 :8080 已启动）。

运行：mcp-server/.venv/bin/python mcp-server/selftest.py
覆盖：5 tool 注册 / group_list 只读 / group_create→回读→get→delete 全链 / 无权限 403 透传。
"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

import httpx
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

BACKEND = "http://127.0.0.1:8080"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
SERVER = Path(__file__).parent / ".venv" / "bin" / "python"
SELF = Path(__file__).parent / "server.py"

failures: list[str] = []


def check(name: str, cond: bool, detail: str = "") -> None:
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f" | {detail}" if detail else ""))
    if not cond:
        failures.append(name)


def login(username: str, password: str) -> str:
    r = httpx.post(f"{BACKEND}/auth/login", json={
        "clientId": CLIENT_ID, "grantType": "password",
        "username": username, "password": password,
    }, timeout=10).json()
    if r.get("code") != 200:
        raise RuntimeError(f"login {username} failed: {r.get('msg')}")
    return r["data"]["access_token"]


def tool_json(result) -> dict:
    """MCP call_tool 结果 → dict。"""
    for c in result.content:
        if getattr(c, "type", "") == "text":
            return json.loads(c.text)
    return {}


async def main() -> int:
    admin = login("admin", "ABCabc123")
    try:
        nocap = login("888888", "123456")   # 无角色员工，验证 403 透传
    except RuntimeError:
        nocap = None

    params = StdioServerParameters(command=str(SERVER), args=[str(SELF)])
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            # 1. tool 注册
            tools = await session.list_tools()
            names = sorted(t.name for t in tools.tools)
            check("5 tools registered", names == sorted(
                ["group_list", "group_get", "group_create", "group_update", "group_delete"]),
                str(names))

            # 2. group_list（admin，只读）
            r = tool_json(await session.call_tool(
                "group_list", {"employee_token": admin}))
            check("group_list ok", r.get("ok") is True and isinstance(r.get("data"), list),
                  f"count={len(r.get('data') or [])}")

            # 3. 建→回读→get→删 全链
            name = "__mcp_selftest__"
            r = tool_json(await session.call_tool("group_create", {
                "employee_token": admin, "dept_name": name,
                "parent_id": "2114510418550157313", "order_num": 97}))
            check("group_create ok", r.get("ok") is True, r.get("msg", ""))

            r = tool_json(await session.call_tool(
                "group_list", {"employee_token": admin, "dept_name": name}))
            rows = [x for x in (r.get("data") or []) if x.get("deptName") == name]
            check("read back new group", len(rows) == 1, f"got {len(rows)}")
            new_id = rows[0]["deptId"] if rows else None

            if new_id:
                r = tool_json(await session.call_tool(
                    "group_get", {"employee_token": admin, "dept_id": new_id}))
                check("group_get ok", r.get("ok") is True and r["data"]["deptCategory"] == "小组")

                # 4. 无权限 403 透传
                if nocap:
                    r = tool_json(await session.call_tool("group_delete", {
                        "employee_token": nocap, "dept_id": new_id}))
                    check("403 passthrough", r.get("ok") is False and r.get("code") == 403,
                          f"code={r.get('code')} msg={r.get('msg')}")

                # 5. admin 删除清理
                r = tool_json(await session.call_tool(
                    "group_delete", {"employee_token": admin, "dept_id": new_id}))
                check("group_delete ok", r.get("ok") is True, r.get("msg", ""))

    print("\n" + ("ALL PASS" if not failures else f"FAILED: {failures}"))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
