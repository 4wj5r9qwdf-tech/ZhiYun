#!/usr/bin/env python3
"""验证仓储权限目录、真实授权保存及普通角色隔离；仅清理本次随机测试资料。
Verify inventory menu reachability, role assignment and least-privilege behavior using isolated fixtures.
"""
import bcrypt
import json
import os
import secrets
import subprocess
import urllib.error
import urllib.request
import uuid

BASE='http://127.0.0.1:8080'
CLIENT='e5cd7e4891bf95d1d19206ce24a7b32e'
PREFIX='WP_'+uuid.uuid4().hex[:12]
ROLE=810000000000000000+secrets.randbelow(1000000000000)
USER=ROLE+1
TOKEN=''
STOCK='1762800000000000001'
WAREHOUSE='1762600000000000101'
LAYOUT='1762600000000000111'
WAREHOUSE_QUERY='1762600000000000105'
LAYOUT_QUERY='1762600000000000112'
warehouses=[]


def sql(statement):
    """执行隔离测试资料SQL，不输出凭据。 / Execute fixtures with captured output and no secrets in diagnostics."""
    return subprocess.run(['sudo','-n','-u','postgres','psql','-v','ON_ERROR_STOP=1','-d','zhizhun_erp','-At'],input=statement,text=True,capture_output=True,check=True).stdout.strip()


def request(method,path,body=None,token=None):
    """员工会话请求不经过代理。 / Send authenticated local requests without external proxying."""
    headers={'Content-Type':'application/json','clientid':CLIENT}
    session=TOKEN if token is None else token
    if session:headers['Authorization']='Bearer '+session
    req=urllib.request.Request(BASE+path,None if body is None else json.dumps(body).encode(),headers,method=method)
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=20) as response:return json.load(response)
    except urllib.error.HTTPError as error:return json.load(error)


def ok(response):
    """检查业务结果而非只看HTTP状态。 / Require a successful domain response."""
    assert response.get('code')==200,response
    return response.get('data')


def forbidden(response):
    """必须在权限层拒绝，业务参数错误不算权限测试通过。 / Require explicit permission denial instead of a generic failure."""
    assert response.get('code')==403,response


def flatten(nodes):
    """按原树递归获取节点，保留祖先验证入口。 / Traverse the actual server-provided permission tree."""
    return [row for node in nodes for row in [node,*flatten(node.get('children',[]))]]


def login(username,password):
    """密码只在内存中使用。 / Authenticate without printing credentials."""
    return ok(request('POST','/auth/login',{'clientId':CLIENT,'grantType':'password','username':username,'password':password},token=''))['access_token']


def assign(menu_ids):
    """通过真实角色权限保存接口授权，不直接写权限关联表。 / Exercise the same role assignment endpoint used by the UI."""
    ok(request('PUT','/system/role/permission',{'roleId':str(ROLE),'menuIds':menu_ids}))


def make_warehouse(suffix,token=None):
    """创建本次专用仓库并追踪清理。 / Create only test-owned business objects."""
    body={'warehouseCode':PREFIX+suffix,'warehouseName':PREFIX+suffix,'status':'0','inboundLocationLevel':0,'tagIds':[]}
    ok(request('POST','/system/warehouse',body,token=token))
    row=next(r for r in ok(request('GET','/system/warehouse/list?keyword='+PREFIX,token=token)) if r['warehouse_code']==body['warehouseCode'])
    wid=str(row['warehouse_id']);warehouses.append(wid)
    return wid,body


try:
    TOKEN=login(os.environ.get('SUPER_ADMIN_USERNAME','admin'),os.environ['SUPER_ADMIN_PASSWORD'])
    password=secrets.token_urlsafe(20);hashed=bcrypt.hashpw(password.encode(),bcrypt.gensalt()).decode();username=PREFIX.lower()
    sql(f"INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status) VALUES({ROLE},'{PREFIX}','{PREFIX}',999,'0'); INSERT INTO sys_user(user_id,user_name,nick_name,password,status,del_flag) VALUES({USER},'{username}','仓储权限测试','{hashed}','0','0'); INSERT INTO sys_user_role(user_id,role_id) VALUES({USER},{ROLE});")
    tree=ok(request('GET',f'/system/menu/roleMenuTreeselect/{ROLE}'))['menus']
    root=next(n for n in tree if str(n['id'])==STOCK)
    assert root['label']=='库存' if 'label' in root else root['name']=='库存'
    pages={str(n['id']):n for n in root['children']}
    assert WAREHOUSE in pages and LAYOUT in pages
    for page in [pages[WAREHOUSE],pages[LAYOUT]]:
        assert page['menuType']=='C'
        assert len(page['children'])==4
        assert all(n['menuType']=='F' for n in page['children'])
    print('PASS 权限树：库存下两个页面及各四个操作节点均可达，权限ID不变')

    wid,body=make_warehouse('BASE')
    location={'warehouseId':wid,'locationLevel':1,'locationCode':PREFIX+'ZONE','locationName':PREFIX+'ZONE','locationStatus':'active'}
    ok(request('POST','/system/warehouse-location',location))
    zone=next(r for r in ok(request('GET',f'/system/warehouse-location/list?warehouseId={wid}')) if r['location_code']==location['locationCode'])
    zid=str(zone['location_id'])

    assign([STOCK,WAREHOUSE,WAREHOUSE_QUERY])
    readonly=login(username,password)
    permissions=ok(request('GET','/system/profile/permissions',token=readonly))
    assert 'inventory:warehouse:list' in permissions and not any(p.startswith('inventory:location:') for p in permissions)
    ok(request('GET','/system/warehouse/list',token=readonly));ok(request('GET',f'/system/warehouse/{wid}',token=readonly))
    forbidden(request('GET','/system/warehouse-location/tree',token=readonly))
    forbidden(request('POST','/system/warehouse',{},token=readonly))
    forbidden(request('PUT',f'/system/warehouse/{wid}/status',{'status':'1'},token=readonly))
    forbidden(request('DELETE',f'/system/warehouse/{wid}',token=readonly))
    routes=ok(request('GET','/system/menu/getRouters',token=readonly))
    stock=next(r for r in routes if r.get('path')=='/stock')
    assert {r['path'] for r in stock['children']}=={'warehouse'}
    checked=ok(request('GET',f'/system/menu/roleMenuTreeselect/{ROLE}'))['checkedKeys']
    # 严格回显只返回叶子，界面按祖先闭包保存，不能误判为父级授权丢失。
    assert WAREHOUSE_QUERY in {str(k) for k in checked}
    stored=set(sql(f'SELECT menu_id FROM sys_role_menu WHERE role_id={ROLE}').splitlines())
    assert stored=={STOCK,WAREHOUSE,WAREHOUSE_QUERY}
    print('PASS 仓库只读角色：页面与详情可读，布局不可见，新增/启停/删除均403，权限保存回显一致')

    assign([STOCK,LAYOUT,LAYOUT_QUERY])
    layout=login(username,password)
    permissions=ok(request('GET','/system/profile/permissions',token=layout))
    assert 'inventory:location:list' in permissions and 'inventory:warehouse:list' not in permissions
    ok(request('GET','/system/warehouse-location/options',token=layout))
    ok(request('GET',f'/system/warehouse-location/tree?warehouseId={wid}',token=layout))
    ok(request('GET',f'/system/warehouse-location/list?warehouseId={wid}',token=layout))
    ok(request('GET',f'/system/warehouse-location/{zid}',token=layout))
    forbidden(request('GET','/system/warehouse/list',token=layout))
    forbidden(request('POST','/system/warehouse-location',{},token=layout))
    forbidden(request('PUT',f'/system/warehouse-location/{zid}/status',{'locationStatus':'disabled'},token=layout))
    forbidden(request('DELETE',f'/system/warehouse-location/{zid}',token=layout))
    print('PASS 布局只读角色：独立读取布局及仓库选择，不依赖仓库管理权限，写操作均403')

    ids=sql("SELECT menu_id FROM sys_menu WHERE perms LIKE 'inventory:warehouse:%' OR perms LIKE 'inventory:location:%'").splitlines()
    assign([STOCK,*ids]);writer=login(username,password)
    new_id,new_body=make_warehouse('WRITE',writer)
    ok(request('PUT','/system/warehouse',{**new_body,'warehouseId':new_id,'remark':'权限验证修改'},token=writer))
    new_location={**location,'warehouseId':new_id,'locationCode':PREFIX+'WRITE_ZONE','locationName':PREFIX+'WRITE_ZONE'}
    ok(request('POST','/system/warehouse-location',new_location,token=writer))
    new_zone=next(r for r in ok(request('GET',f'/system/warehouse-location/list?warehouseId={new_id}',token=writer)) if r['location_code']==new_location['locationCode'])
    new_zid=str(new_zone['location_id'])
    ok(request('PUT','/system/warehouse-location',{**new_location,'locationId':new_zid,'remark':'权限验证修改'},token=writer))
    ok(request('PUT',f'/system/warehouse-location/{new_zid}/status',{'locationStatus':'disabled'},token=writer))
    ok(request('DELETE',f'/system/warehouse-location/{new_zid}',token=writer))
    ok(request('PUT',f'/system/warehouse/{new_id}/status',{'status':'1'},token=writer))
    ok(request('DELETE',f'/system/warehouse/{new_id}',token=writer))
    print('PASS 完整授权角色：仓库和布局新增、修改、启停、删除均真实成功')

    assign([]);revoked=login(username,password)
    forbidden(request('GET','/system/warehouse/list',token=revoked));forbidden(request('GET','/system/warehouse-location/tree',token=revoked))
    assert not any(p.startswith('inventory:') for p in ok(request('GET','/system/profile/permissions',token=revoked)))
    print('PASS 撤权：重新登录后两个功能和操作权限全部失效')
finally:
    statements=['BEGIN;']
    if warehouses:
        ids=','.join(warehouses)
        statements += [f"DELETE FROM sys_tag_relation WHERE module_key='warehouse_master' AND record_id IN ({ids});",f'DELETE FROM inv_warehouse_location WHERE warehouse_id IN ({ids});',f'DELETE FROM inv_warehouse WHERE warehouse_id IN ({ids});']
    statements += [f'DELETE FROM sys_role_menu WHERE role_id={ROLE};',f'DELETE FROM sys_role_org WHERE role_id={ROLE};',f'DELETE FROM sys_user_role WHERE user_id={USER};',f'DELETE FROM sys_user WHERE user_id={USER};',f'DELETE FROM sys_role WHERE role_id={ROLE};','COMMIT;']
    sql('\n'.join(statements))
    print('已清理本次测试角色、账号、仓库和布局；未给真实角色追加权限')
