#!/usr/bin/env python3
"""仓储位置接口验收：使用随机测试仓库，清理仅限本次数据；认证信息从环境加载。"""
import json
import os
import subprocess
import urllib.request
import uuid

BASE = 'http://127.0.0.1:8080'
CLIENT = 'e5cd7e4891bf95d1d19206ce24a7b32e'
TOKEN = ''
PREFIX = '__location_smoke_' + uuid.uuid4().hex[:12]
warehouses = []
checks = 0


def request(method, path, body=None):
    headers = {'Content-Type': 'application/json', 'clientid': CLIENT}
    if TOKEN:
        headers['Authorization'] = 'Bearer ' + TOKEN
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=20) as response:
        return json.load(response)


def ok(response):
    global checks
    assert response.get('code') == 200, response.get('msg', '接口失败')
    checks += 1
    return response.get('data')


def reject(response):
    global checks
    assert response.get('code') != 200, '无效操作未被拒绝'
    checks += 1


def warehouse(suffix):
    payload = {'warehouseCode': PREFIX + suffix, 'warehouseName': PREFIX + suffix,
               'status': '0', 'inboundLocationLevel': 0, 'tagIds': []}
    ok(request('POST', '/system/warehouse', payload))
    row = next(row for row in ok(request('GET', '/system/warehouse/list?keyword=' + PREFIX)) if row['warehouse_code'] == payload['warehouseCode'])
    warehouses.append(str(row['warehouse_id']))
    return str(row['warehouse_id']), payload


def create(wid, level, code, parent=None):
    payload = {'warehouseId': wid, 'locationLevel': level, 'parentLocationId': parent,
               'locationCode': PREFIX + code, 'locationName': PREFIX + code,
               'locationStatus': 'active', 'agvReachable': True}
    ok(request('POST', '/system/warehouse-location', payload))
    row = next(row for row in ok(request('GET', f'/system/warehouse-location/list?warehouseId={wid}&level={level}')) if row['location_code'] == payload['locationCode'])
    return str(row['location_id']), payload


try:
    reject(request('POST', '/system/warehouse-location', {}))
    password = os.environ.get('SUPER_ADMIN_PASSWORD')
    if not password:
        raise RuntimeError('缺少 SUPER_ADMIN_PASSWORD，无法运行认证接口验收')
    TOKEN = ok(request('POST', '/auth/login', {'clientId': CLIENT, 'grantType': 'password',
        'username': os.environ.get('SUPER_ADMIN_USERNAME', 'admin'), 'password': password}))['access_token']
    wid, wp = warehouse('a')
    other, _ = warehouse('b')
    zone, zp = create(wid, 1, 'zone')
    rack, rp = create(wid, 2, 'rack', zone)
    bin_id, bp = create(wid, 3, 'bin', rack)
    assert any(row['location_id'] == zone for row in ok(request('GET', '/system/warehouse-location/tree')))
    assert ok(request('GET', f'/system/warehouse-location/{bin_id}'))['parent_location_id'] == rack
    reject(request('POST', '/system/warehouse-location', {**zp, 'parentLocationId': zone, 'locationCode': PREFIX + 'badzone'}))
    reject(request('POST', '/system/warehouse-location', {**rp, 'warehouseId': other}))
    reject(request('POST', '/system/warehouse-location', {**bp, 'parentLocationId': zone}))
    reject(request('POST', '/system/warehouse-location', {**zp, 'locationCode': zp['locationCode'].upper()}))
    reject(request('POST', '/system/warehouse-location', {**zp, 'locationCode': PREFIX + 'capacity', 'capacityLimit': 5}))
    reject(request('DELETE', f'/system/warehouse/{wid}'))
    reject(request('DELETE', f'/system/warehouse-location/{zone}'))
    reject(request('PUT', f'/system/warehouse-location/{zone}/status', {'locationStatus': 'disabled'}))
    reject(request('PUT', '/system/warehouse-location', {**zp, 'locationId': zone, 'locationStatus': 'disabled'}))
    reject(request('PUT', '/system/warehouse-location', {**rp, 'locationId': rack, 'warehouseId': other}))
    ok(request('PUT', '/system/warehouse-location', {**bp, 'locationId': bin_id, 'locationName': PREFIX + 'edited', 'remark': '接口验收修改', 'capacityLimit': 12, 'capacityUnit': '箱'}))
    assert ok(request('GET', f'/system/warehouse-location/{bin_id}'))['location_name'] == PREFIX + 'edited'
    for lid in [bin_id, rack, zone]:
        ok(request('PUT', f'/system/warehouse-location/{lid}/status', {'locationStatus': 'disabled'}))
    reject(request('PUT', f'/system/warehouse-location/{rack}/status', {'locationStatus': 'active'}))
    # 父级停用后仍可编辑停用子级的备注；不能借编辑把它直接启用。
    ok(request('PUT', '/system/warehouse-location', {**rp, 'locationId': rack, 'locationStatus': 'disabled', 'remark': '停用后仍可维护'}))
    reject(request('PUT', '/system/warehouse-location', {**rp, 'locationId': rack, 'locationStatus': 'active'}))
    for lid in [zone, rack, bin_id]:
        ok(request('PUT', f'/system/warehouse-location/{lid}/status', {'locationStatus': 'active'}))
    for depth in [1, 2, 3, 0]:
        ok(request('PUT', '/system/warehouse', {**wp, 'warehouseId': wid, 'inboundLocationLevel': depth}))
    ok(request('PUT', f'/system/warehouse/{wid}/status', {'status': '1'}))
    reject(request('POST', '/system/warehouse-location', {**zp, 'locationCode': PREFIX + 'disabledwarehouse'}))
    assert len(ok(request('GET', f'/system/warehouse-location/list?warehouseId={wid}'))) == 3
    ok(request('PUT', f'/system/warehouse/{wid}/status', {'status': '0'}))
    # 声明一个临时业务引用，检查逻辑删除和编码变更也能保护历史关联。
    table = 'location_smoke_ref_' + uuid.uuid4().hex[:12]
    subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'], input=f'CREATE TABLE {table}(location_id bigint REFERENCES inv_warehouse_location(location_id)); INSERT INTO {table} VALUES ({bin_id});', text=True, check=True, stdout=subprocess.DEVNULL)
    try:
        reject(request('DELETE', f'/system/warehouse-location/{bin_id}'))
        reject(request('PUT', '/system/warehouse-location', {**bp, 'locationId': bin_id, 'locationCode': PREFIX + 'changed'}))
    finally:
        subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'], input=f'DROP TABLE {table};', text=True, check=True, stdout=subprocess.DEVNULL)
    for lid in [bin_id, rack, zone]:
        ok(request('DELETE', f'/system/warehouse-location/{lid}'))
        reject(request('GET', f'/system/warehouse-location/{lid}'))
    ok(request('DELETE', f'/system/warehouse/{wid}'))
    print(f'PASS：{checks} 项接口检查；完整层级、全部仓库、编辑、父子状态、重复编码、跨仓保护、入库深度、业务引用与删除')
finally:
    # 双重限定 ID 和随机前缀；清理本次生成的档案，保留业务操作日志。
    for wid in warehouses:
        sql = f"""BEGIN;
DELETE FROM inv_warehouse_location WHERE warehouse_id={int(wid)} AND warehouse_id IN (SELECT warehouse_id FROM inv_warehouse WHERE warehouse_code LIKE '{PREFIX}%');
DELETE FROM sys_tag_relation WHERE module_key='warehouse_master' AND record_id={int(wid)} AND record_id IN (SELECT warehouse_id FROM inv_warehouse WHERE warehouse_code LIKE '{PREFIX}%');
DELETE FROM inv_warehouse WHERE warehouse_id={int(wid)} AND warehouse_code LIKE '{PREFIX}%';
COMMIT;"""
        subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'], input=sql, text=True, check=True, stdout=subprocess.DEVNULL)
