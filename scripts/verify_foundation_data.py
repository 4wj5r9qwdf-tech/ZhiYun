#!/usr/bin/env python3
"""计量单位/分类管理定向接口验证；仅创建并清理本次随机档案，凭据从环境读取。
Verify exact conversion, hierarchy invariants, permissions and FK protection with isolated data.
"""
import concurrent.futures
import json
import os
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

BASE = 'http://127.0.0.1:8080'
CLIENT = 'e5cd7e4891bf95d1d19206ce24a7b32e'
PREFIX = 'TEST_' + uuid.uuid4().hex[:12]
TOKEN = ''
UNIT_IDS, SCHEME_IDS, CATEGORY_IDS = [], [], []
REF = 'foundation_ref_' + uuid.uuid4().hex[:12]
USER_ID = 800000000000000000 + secrets.randbelow(1000000000000)


def request(method, path, body=None, token=None):
    """本地请求不经过代理，兼容业务错误和HTTP拒绝。 / Send local authenticated requests without proxy leakage."""
    headers = {'Content-Type': 'application/json', 'clientid': CLIENT}
    session = TOKEN if token is None else token
    if session:
        headers['Authorization'] = 'Bearer ' + session
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        try:
            return json.load(exc)
        except ValueError:
            return {'code': exc.code, 'msg': 'HTTP拒绝'}


def ok(response):
    """断言成功并取数据，保留业务原因便于定位。 / Assert the domain result, not merely HTTP success."""
    assert response.get('code') == 200, str(response)
    return response.get('data')


def rejected(response):
    """无效写入必须明确拒绝。 / Assert that invalid writes never report success."""
    assert response.get('code') != 200, '本应拒绝的操作被接受'


def sql(statement):
    """管理员仅执行本测试固定/随机标识语句，不输出业务数据或凭据。 / Execute isolated fixtures without printing secrets."""
    result = subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp', '-At'], input=statement, text=True, check=True, capture_output=True)
    return result.stdout.strip()


def create_unit(suffix, **extra):
    """创建独立测试单位并跟踪清理ID。 / Track every created unit for deterministic cleanup."""
    body = dict(code=PREFIX + suffix, name=PREFIX + suffix, dimension='length', decimalPlaces=3, factor='1', status='0', **extra)
    uid = ok(request('POST', '/system/unit', body))
    assert uid and isinstance(uid, str), '新增必须返回字符串ID'
    UNIT_IDS.append(uid)
    return uid, body


def create_scheme(suffix, **extra):
    """创建隔离分类体系。 / Create a dedicated test namespace."""
    body = dict(code=PREFIX + suffix, name=PREFIX + suffix, maxDepth=3, status='0', **extra)
    sid = ok(request('POST', '/system/category/schemes', body)); SCHEME_IDS.append(sid)
    return sid, body


def create_category(sid, suffix, parent=None):
    """创建可追踪分类节点。 / Create one tracked hierarchy node."""
    body = dict(schemeId=sid, code=PREFIX + suffix, name=PREFIX + suffix, parentId=parent, status='0')
    cid = ok(request('POST', '/system/category', body)); CATEGORY_IDS.append(cid)
    return cid, body


def verify_mcp(low_token):
    """验证真实MCP发现、读写复用和员工权限，不把注解存在当作可调用。 / Exercise real MCP discovery and authorized mutations."""
    headers = {'Content-Type':'application/json','clientid':CLIENT,'Authorization':'Bearer '+TOKEN,'Accept':'application/json, text/event-stream'}
    def rpc(body, current_headers):
        req=urllib.request.Request(BASE+'/mcp',json.dumps(body).encode(),current_headers,method='POST')
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=20) as response:
            raw=response.read().decode();metadata=dict(response.headers)
        if not raw:return {},metadata
        if raw.lstrip().startswith('{'):return json.loads(raw),metadata
        return json.loads('\n'.join(line[5:].lstrip() for line in raw.splitlines() if line.startswith('data:'))),metadata
    def initialize(current_headers):
        initialized,metadata=rpc({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2025-06-18','capabilities':{},'clientInfo':{'name':'zhiyun-foundation-verifier','version':'1'}}},current_headers)
        assert 'result' in initialized,initialized
        session=next((v for k,v in metadata.items() if k.lower()=='mcp-session-id'),None)
        if session:current_headers['Mcp-Session-Id']=session
        current_headers['MCP-Protocol-Version']=initialized['result']['protocolVersion']
        rpc({'jsonrpc':'2.0','method':'notifications/initialized'},current_headers)
    initialize(headers)
    tools,_=rpc({'jsonrpc':'2.0','id':2,'method':'tools/list','params':{}},headers)
    expected={'unit_list','unit_options','unit_convert','unit_create','unit_update','unit_delete','category_schemes','category_scheme_options','category_list','category_options','category_create','category_update','category_delete','category_scheme_create','category_scheme_update','category_scheme_delete'}
    assert expected <= {item['name'] for item in tools['result']['tools']}
    counter=2
    def call(name,arguments):
        nonlocal counter
        counter+=1
        result,_=rpc({'jsonrpc':'2.0','id':counter,'method':'tools/call','params':{'name':name,'arguments':arguments}},headers)
        assert 'result' in result and not result['result'].get('isError'),result
        return ok(json.loads(next(c['text'] for c in result['result']['content'] if c['type']=='text')))
    for name in ['unit_list','unit_options','category_schemes','category_scheme_options']:call(name,{})
    unit_body={'code':PREFIX+'MCP','name':PREFIX+'MCP','dimension':'count','decimalPlaces':0,'factor':'1','status':'0'}
    uid=call('unit_create',{'body':unit_body});UNIT_IDS.append(uid)
    call('unit_update',{'id':uid,'body':{**unit_body,'status':'1'}})
    call('unit_delete',{'id':uid})
    scheme_body={'code':PREFIX+'MCP','name':PREFIX+'MCP','maxDepth':3,'status':'0'}
    sid=call('category_scheme_create',{'body':scheme_body});SCHEME_IDS.append(sid)
    category_body={'schemeId':sid,'code':'NODE','name':PREFIX+'MCP_NODE','status':'0'}
    cid=call('category_create',{'body':category_body});CATEGORY_IDS.append(cid)
    assert any(r['id']==cid for r in call('category_list',{'schemeId':sid}))
    call('category_update',{'id':cid,'body':{**category_body,'status':'1'}})
    assert not next(r for r in call('category_options',{'schemeId':sid}) if r['id']==cid)['selectable']
    call('category_delete',{'id':cid})
    call('category_scheme_update',{'id':sid,'body':{**scheme_body,'status':'1'}})
    call('category_scheme_delete',{'id':sid})
    denied_headers={'Content-Type':'application/json','clientid':CLIENT,'Authorization':'Bearer '+low_token,'Accept':'application/json, text/event-stream'}
    initialize(denied_headers)
    denied,_=rpc({'jsonrpc':'2.0','id':3,'method':'tools/call','params':{'name':'unit_create','arguments':{'body':unit_body}}},denied_headers)
    outcome=denied.get('result',{})
    assert 'error' in denied or outcome.get('isError') or any('403' in c.get('text','') or '权限' in c.get('text','') for c in outcome.get('content',[])),denied
    print('PASS MCP：16工具发现、单位/体系/节点真实读写和低权限写入拒绝')


try:
    TOKEN = ok(request('POST', '/auth/login', {'clientId': CLIENT, 'grantType': 'password', 'username': os.environ.get('SUPER_ADMIN_USERNAME', 'admin'), 'password': os.environ['SUPER_ADMIN_PASSWORD']}))['access_token']
    root, root_body = create_unit('M')
    child_body = dict(code=PREFIX+'CM', name=PREFIX+'CM', dimension='length', decimalPlaces=3, baseUnitId=root, factor='0.01', status='0')
    child = ok(request('POST', '/system/unit', child_body)); UNIT_IDS.append(child)
    mass_body = dict(code=PREFIX+'KG', name=PREFIX+'KG', dimension='mass', decimalPlaces=3, factor='1', status='0')
    mass = ok(request('POST', '/system/unit', mass_body)); UNIT_IDS.append(mass)
    assert Decimal(ok(request('GET', f'/system/unit/convert?fromId={child}&toId={root}&quantity=123.45'))['quantity']) == Decimal('1.235')
    assert ok(request('GET', f'/system/unit/convert?fromId={root}&toId={child}&quantity=0'))['quantity'] == '0.000'
    rejected(request('GET', f'/system/unit/convert?fromId={root}&toId={mass}&quantity=1'))
    rejected(request('GET', f'/system/unit/convert?fromId={root}&toId={child}&quantity=-1'))
    rejected(request('GET', f'/system/unit/convert?fromId={root}&toId={child}&quantity=1e999999999'))
    rejected(request('POST', '/system/unit', child_body))
    rejected(request('POST', '/system/unit', {**child_body,'code':PREFIX+'BAD','name':PREFIX+'BAD','factor':'0'}))
    rejected(request('PUT', f'/system/unit/{root}', {**root_body,'decimalPlaces':4}))
    rejected(request('DELETE', f'/system/unit/{root}'))
    rejected(request('PUT', f'/system/unit/{root}', {**root_body,'status':'1'}))
    ok(request('PUT', f'/system/unit/{child}', {**child_body,'status':'1'}))
    ok(request('PUT', f'/system/unit/{root}', {**root_body,'status':'1'}))
    assert not next(r for r in ok(request('GET','/system/unit/options')) if r['id']==child)['selectable']
    rejected(request('POST','/system/unit',{**child_body,'code':PREFIX+'NEW','name':PREFIX+'NEW'}))
    ok(request('PUT',f'/system/unit/{root}',root_body)); ok(request('PUT',f'/system/unit/{child}',child_body))
    print('PASS 单位：精确换算、舍入、零值、跨维度/负数/重复/换算语义保护、停用及引用校验')

    s1, s1body = create_scheme('S1'); s2, _ = create_scheme('S2')
    a, abody = create_category(s1,'A'); b, bbody = create_category(s1,'B',a); c, cbody = create_category(s1,'C',b)
    rejected(request('POST','/system/category',{**cbody,'code':PREFIX+'D','name':PREFIX+'D','parentId':c}))
    rejected(request('PUT',f'/system/category/{a}',{**abody,'parentId':c}))
    rejected(request('POST','/system/category',{**bbody,'schemeId':s2}))
    rejected(request('POST','/system/category',{**bbody,'code':PREFIX+'UNIQUE'}))
    rejected(request('PUT',f'/system/category/schemes/{s1}',{**s1body,'maxDepth':2}))
    rejected(request('PUT',f'/system/category/schemes/{s1}',{**s1body,'code':PREFIX+'RENAMED'}))
    rejected(request('DELETE',f'/system/category/{a}'))
    rejected(request('PUT',f'/system/category/{a}',{**abody,'status':'1'}))
    rejected(request('DELETE',f'/system/category/schemes/{s1}'))
    ok(request('PUT',f'/system/category/{c}',{**cbody,'status':'1'}));ok(request('PUT',f'/system/category/{b}',{**bbody,'status':'1'}));ok(request('PUT',f'/system/category/{a}',{**abody,'status':'1'}))
    assert all(not r['selectable'] for r in ok(request('GET',f'/system/category/options?schemeId={s1}')))
    rejected(request('PUT',f'/system/category/{b}',bbody))
    for cid, body in [(a,abody),(b,bbody),(c,cbody)]:ok(request('PUT',f'/system/category/{cid}',body))
    ok(request('PUT',f'/system/category/schemes/{s1}',{**s1body,'status':'1'}))
    assert all(not r['selectable'] for r in ok(request('GET',f'/system/category/options?schemeId={s1}')))
    rejected(request('POST','/system/category',{**abody,'code':PREFIX+'BLOCK','name':PREFIX+'BLOCK'}))
    ok(request('PUT',f'/system/category/schemes/{s1}',s1body))
    x,xbody=create_category(s2,'X');y,ybody=create_category(s2,'Y')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        responses=list(executor.map(lambda item:request('PUT',f'/system/category/{item[0]}',item[1]),[(x,{**xbody,'parentId':y}),(y,{**ybody,'parentId':x})]))
    assert sum(r['code']==200 for r in responses)==1, '并发反向移动必须阻止成环'
    print('PASS 分类：体系隔离、同级重名、循环及并发成环、深度、停用祖先和体系约束')

    sql(f'CREATE TABLE {REF}(unit_id bigint REFERENCES base_unit(unit_id),category_id bigint REFERENCES base_category(category_id)); INSERT INTO {REF} VALUES ({mass},{c});')
    sql(f"DO $$$$ DECLARE app_role text; BEGIN SELECT pg_get_userbyid(relowner) INTO app_role FROM pg_class WHERE oid='public.sys_tag'::regclass; EXECUTE format('GRANT SELECT ON {REF} TO %I',app_role); END $$$$;".replace('$$$$','$$'))
    rejected(request('DELETE',f'/system/unit/{mass}'));rejected(request('DELETE',f'/system/category/{c}'))
    rejected(request('PUT',f'/system/unit/{mass}',{**mass_body,'decimalPlaces':4}))
    sql(f'DROP TABLE {REF};')
    ok(request('DELETE',f'/system/category/{c}'));ok(request('DELETE',f'/system/category/{b}'));ok(request('DELETE',f'/system/category/{a}'));ok(request('DELETE',f'/system/category/schemes/{s1}'))
    print('PASS 引用保护与删除：真实外键阻止删除/语义变更，清空节点后允许删除体系')

    import bcrypt
    password=secrets.token_urlsafe(20); hashed=bcrypt.hashpw(password.encode(),bcrypt.gensalt()).decode()
    username=PREFIX.lower()
    sql(f"INSERT INTO sys_user(user_id,user_name,nick_name,password,status,del_flag) VALUES ({USER_ID},'{username}','基础资料权限测试','{hashed}','0','0');")
    low=ok(request('POST','/auth/login',{'clientId':CLIENT,'grantType':'password','username':username,'password':password},token=''))['access_token']
    for path in ['/system/unit/list','/system/category/schemes']:
        rejected(request('GET',path,token=low))
    rejected(request('POST','/system/unit',root_body,token=low)); rejected(request('POST','/system/category',abody,token=low))
    ok(request('GET','/system/unit/options',token=low));ok(request('GET','/system/category/scheme-options',token=low))
    rejected(request('GET','/system/unit/options',token=''))
    print('PASS 权限：无管理权限员工不可管理，登录业务可读公共选项，匿名不可读')
    verify_mcp(low)
    time.sleep(1)
    # 日志实际载荷在框架JSON字段中，校验含中文动作及变更字段，不输出日志内容。
    audit_count=sql(f"SELECT count(*) FROM sys_oper_log WHERE title IN ('计量单位','分类管理','分类体系') AND oper_param LIKE '%{PREFIX}%';")
    assert int(audit_count)>0, '缺少基础资料业务操作日志'
    assert int(sql(f"SELECT count(*) FROM sys_oper_log WHERE oper_param LIKE '%{PREFIX}%' AND oper_param LIKE '%changes%' AND oper_param LIKE '%名称%';"))>0, '日志缺少真实业务字段差异'
    print('PASS 审计入口：真实写入产生基础资料日志')
finally:
    statements=['BEGIN;',f'DROP TABLE IF EXISTS {REF};']
    if CATEGORY_IDS:
        # 测试节点可能经历并发移动，先断开本次节点父链再清理。
        ids=','.join(CATEGORY_IDS);statements += [f'UPDATE base_category SET parent_id=NULL WHERE category_id IN ({ids});',f'DELETE FROM base_category WHERE category_id IN ({ids});']
    if SCHEME_IDS:statements.append('DELETE FROM base_category_scheme WHERE scheme_id IN ('+','.join(SCHEME_IDS)+');')
    if UNIT_IDS:
        ids=','.join(UNIT_IDS);statements += [f'UPDATE base_unit SET base_unit_id=NULL,factor=1 WHERE unit_id IN ({ids});',f'DELETE FROM base_unit WHERE unit_id IN ({ids});']
    statements += [f'DELETE FROM sys_user_role WHERE user_id={USER_ID};',f'DELETE FROM sys_user WHERE user_id={USER_ID};','COMMIT;']
    sql('\n'.join(statements))
    print('已清理本次测试档案、权限账号及引用测试表')
