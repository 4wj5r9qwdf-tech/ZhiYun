"""
织云 ERP MCP Server —— 小组管理（group）能力原子

传输：stdio（供智能体对话壳以 MCP 协议接入）
安全红线（《内嵌智能体规范.md》四）：
  - 每个 tool 必须由调用方传入 employee_token（发起员工本人的 Sa-Token），
    本进程只做 HTTP 转发，不直连数据库、不持有任何万能账号；
  - 权限由后端 Sa-Token 按 token 校验，无权限时后端返回 403，本进程原样透传。
配置：MCP_BACKEND_URL / MCP_CLIENT_ID 环境变量（不写死密钥；clientId 为公开应用标识）。
启动：mcp-server/.venv/bin/python mcp-server/server.py
"""
from __future__ import annotations

import os
from typing import Any

import httpx
from mcp.server.mcpserver import MCPServer

BACKEND_URL = os.environ.get("MCP_BACKEND_URL", "http://127.0.0.1:8080").rstrip("/")
CLIENT_ID = os.environ.get("MCP_CLIENT_ID", "e5cd7e4891bf95d1d19206ce24a7b32e")

mcp = MCPServer("zhizhun-erp")


def _headers(employee_token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {employee_token}",
        "clientid": CLIENT_ID,
        "Content-Type": "application/json",
    }


def _call(method: str, path: str, employee_token: str, *, params: dict | None = None,
          json_body: dict | None = None) -> dict[str, Any]:
    """统一调后端并返回 {ok, code, msg, data}；网络异常也收敛为该结构。"""
    url = f"{BACKEND_URL}{path}"
    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.request(method, url, headers=_headers(employee_token),
                                  params=params, json=json_body)
        body = resp.json()
        code = body.get("code", resp.status_code)
        return {"ok": code == 200, "code": code, "msg": body.get("msg", ""), "data": body.get("data")}
    except httpx.HTTPError as e:
        return {"ok": False, "code": -1, "msg": f"后端不可达: {e}", "data": None}
    except ValueError:
        return {"ok": False, "code": -1, "msg": f"后端返回非 JSON (HTTP {resp.status_code})", "data": None}


# ---------- 只读 ----------

@mcp.tool()
def group_list(employee_token: str, org_category: str = "小组",
               parent_id: str | None = None, org_name: str | None = None,
               status: str | None = None) -> dict[str, Any]:
    """查组织树节点列表（平铺）。默认只查小组（org_category='小组'）。

    Args:
        employee_token: 发起员工本人的 Sa-Token（登录 /auth/login 返回的 access_token）。
        org_category: 类别模糊匹配；查小组保持默认"小组"，传空串查全部部门。
        parent_id: 按直接上级 ID 过滤（字符串 bigint，禁止转 number）。
        org_name: 名称模糊匹配。
        status: '0'=启用 '1'=停用。

    Returns:
        {ok, code, msg, data: [ {orgId, parentId, orgName, orgCategory, ancestors, ...} ]}
        401=token 过期；403=该员工无 system:org:list 权限。
    """
    params: dict[str, Any] = {}
    if org_category:
        params["orgCategory"] = org_category
    if parent_id:
        params["parentId"] = parent_id
    if org_name:
        params["orgName"] = org_name
    if status:
        params["status"] = status
    return _call("GET", "/system/org/list", employee_token, params=params)


@mcp.tool()
def group_get(employee_token: str, org_id: str) -> dict[str, Any]:
    """查单个部门/小组详情（含 ancestors 层级链）。

    Args:
        employee_token: 发起员工本人的 Sa-Token。
        org_id: 部门/小组 ID（字符串 bigint，禁止转 number）。

    Returns:
        {ok, code, msg, data: {orgId, parentId, ancestors, orgName, orgCategory, ...}}
    """
    return _call("GET", f"/system/org/{org_id}", employee_token)


# ---------- 写 ----------

@mcp.tool()
def group_create(employee_token: str, org_name: str, parent_id: str,
                 order_num: int = 0, org_category: str = "小组",
                 status: str = "0", leader: str | None = None,
                 phone: str | None = None, email: str | None = None) -> dict[str, Any]:
    """新建部门/小组。org_category 默认"小组"=建小组；parent_id 可为部门或已建小组（支持二级小组）。

    Args:
        employee_token: 发起员工本人的 Sa-Token。
        org_name: 名称，≤30 字符，同级不可重名。
        parent_id: 挂靠上级 ID（字符串）；"0"=顶层。可填小组 ID 实现二级小组。
        order_num: 排序，数字越小越靠前。
        org_category: 保持"小组"；建普通部门时按"N 级"传。
        status: '0'=启用 '1'=停用。
        leader: 负责人 user_id（字符串），不是姓名文本。

    Returns:
        {ok, code, msg, data: null}。成功后用 group_list(org_name=...) 回读取新 orgId
        （接口不回包新 ID）。重名/重复提交返回 ok=false + 具体 msg。
    """
    body: dict[str, Any] = {
        "orgName": org_name, "parentId": parent_id, "orderNum": order_num,
        "orgCategory": org_category, "status": status,
    }
    if leader:
        body["leader"] = leader
    if phone:
        body["phone"] = phone
    if email:
        body["email"] = email
    return _call("POST", "/system/org", employee_token, json_body=body)


@mcp.tool()
def group_update(employee_token: str, org_id: str, org_name: str | None = None,
                 parent_id: str | None = None, order_num: int | None = None,
                 status: str | None = None, leader: str | None = None) -> dict[str, Any]:
    """修改部门/小组（只更新传入的字段；改 parent_id 会级联重算该节点及下级的 ancestors）。

    Args:
        employee_token: 发起员工本人的 Sa-Token。
        org_id: 要修改的节点 ID（字符串，必填）。
        org_name: 新名称（同级重名会被拒）。
        parent_id: 新挂靠上级（不能填自己；填自己会返回业务错误）。
        order_num: 新排序。
        status: '0'=启用 '1'=停用（停用要求无启用子节点且节点下无人）。
        leader: 新负责人 user_id（字符串）。

    Returns:
        {ok, code, msg, data: null}
    """
    body: dict[str, Any] = {"orgId": org_id}
    for key, val in (("orgName", org_name), ("parentId", parent_id),
                     ("orderNum", order_num), ("status", status), ("leader", leader)):
        if val is not None:
            body[key] = val
    return _call("PUT", "/system/org", employee_token, json_body=body)


@mcp.tool()
def group_delete(employee_token: str, org_id: str) -> dict[str, Any]:
    """删除部门/小组（软删）。5 道前置校验：默认部门/有下级/有人员/有岗位/被管辖引用，任一不过返回 ok=false。

    Args:
        employee_token: 发起员工本人的 Sa-Token。
        org_id: 要删除的节点 ID（字符串）。二级小组需先删其子组。

    Returns:
        {ok, code, msg, data: null}；失败 msg 即校验原因（如"存在下级部门,不允许删除"）。
    """
    return _call("DELETE", f"/system/org/{org_id}", employee_token)


if __name__ == "__main__":
    mcp.run()  # stdio transport
