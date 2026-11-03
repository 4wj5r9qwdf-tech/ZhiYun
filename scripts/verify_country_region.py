#!/usr/bin/env python3
"""国家/地区与客户接口烟测；仅创建并清理本次随机前缀的数据，不输出凭据。"""
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import uuid

BASE = 'http://127.0.0.1:8080'
CLIENT = 'e5cd7e4891bf95d1d19206ce24a7b32e'
token = ''
key = uuid.uuid4().hex
customer_name = '__country_test_' + key
legacy_name = '__country_legacy_' + key
country_code = 'ZZ' + key[:8].upper()
country_name = '__country_region_' + key
renamed_country = country_name + '_edited'


def call(method, path, payload=None):
    headers = {'Content-Type': 'application/json', 'clientid': CLIENT}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, None if payload is None else json.dumps(payload).encode('utf-8'), headers, method=method)
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=20) as resp:
        return json.load(resp)


def ok(response):
    if response.get('code') != 200:
        raise AssertionError('接口失败：' + str(response.get('msg', response.get('code'))))
    return response.get('data')


def sql(query):
    # SQL 内容仅由本脚本的固定语句、随机十六进制和已转整数的测试 ID 构成。
    return subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp', '-At'],
                          input=query, text=True, check=True, capture_output=True).stdout.strip()


def verify_legacy_migration():
    """在临时表运行迁移脚本原文：唯一匹配、歧义、未知值和重复执行均不得丢原文。"""
    migration = Path('sql/国家地区_20261103.sql').read_text(encoding='utf-8')
    block = migration.split('-- 只匹配唯一的中文名、英文名或编码；不确定的旧值继续保留，交由用户在编辑时选择。', 1)[1].split('INSERT INTO sys_menu', 1)[0]
    fixture = """BEGIN;
      CREATE TEMP TABLE sys_country_region(country_code varchar(12),name_zh text,name_en text);
      CREATE TEMP TABLE customer_master(customer_id bigint,country_region text,country_region_code varchar(12));
      CREATE TEMP TABLE customer_address(address_id bigint,country_region text,country_region_code varchar(12));
      INSERT INTO sys_country_region VALUES ('CN','中国','China'),('US','美国','United States'),('AA','重名地区','First'),('AB','重名地区','Second');
      INSERT INTO customer_master VALUES (1,'中国',NULL),(2,'未知旧值',NULL),(3,'重名地区',NULL),(4,'cn',NULL);
      INSERT INTO customer_address VALUES (1,'United States',NULL),(2,'无法识别',NULL);
    """
    assertions = """DO $$ BEGIN
      IF (SELECT country_region_code FROM customer_master WHERE customer_id=1) IS DISTINCT FROM 'CN' THEN RAISE EXCEPTION '中文匹配失败'; END IF;
      IF (SELECT country_region_code FROM customer_master WHERE customer_id=4) IS DISTINCT FROM 'CN' THEN RAISE EXCEPTION '编码匹配失败'; END IF;
      IF EXISTS(SELECT 1 FROM customer_master WHERE customer_id IN (2,3) AND country_region_code IS NOT NULL) THEN RAISE EXCEPTION '未知或歧义值被错误匹配'; END IF;
      IF (SELECT country_region FROM customer_master WHERE customer_id=1) IS DISTINCT FROM '中国' THEN RAISE EXCEPTION '原始文本被改变'; END IF;
      IF (SELECT country_region_code FROM customer_address WHERE address_id=1) IS DISTINCT FROM 'US' OR (SELECT country_region_code FROM customer_address WHERE address_id=2) IS NOT NULL THEN RAISE EXCEPTION '地址映射失败'; END IF;
    END $$;
    ROLLBACK;"""
    sql(fixture + block + block + assertions)


try:
    verify_legacy_migration()
    password = os.environ.get('SUPER_ADMIN_PASSWORD')
    if not password:
        raise RuntimeError('未设置 SUPER_ADMIN_PASSWORD，无法执行认证烟测')
    token = ok(call('POST', '/auth/login', {'clientId': CLIENT, 'grantType': 'password',
               'username': os.environ.get('SUPER_ADMIN_USERNAME', 'admin'), 'password': password}))['access_token']
    countries = ok(call('GET', '/system/country-region/options'))
    assert any(r['country_code'] == 'CN' and r['name_zh'] == '中国' for r in countries)
    assert any(r['country_code'] == 'US' for r in countries)
    assert len(countries) >= 240
    people = ok(call('GET', '/system/customer/sales-options'))
    if not people:
        raise RuntimeError('没有可选人员，无法验证客户地区关联')
    sales = int(people[0]['value'])
    ok(call('POST', '/system/country-region', {'country_code': country_code, 'name_zh': country_name, 'name_en': 'Smoke Test'}))
    payload = {'customer_name': customer_name, 'sales_representative_id': str(sales),
               'country_region_code': 'CN', 'country_region': '客户端伪造名称', 'contacts': [],
               'addresses': [{'address_type': '办公联系', 'address_line': '接口烟测地址',
                              'country_region_code': country_code, 'country_region': '客户端伪造名称'}], 'tagIds': []}
    cid = int(ok(call('POST', '/system/customer', payload)))
    detail = ok(call('GET', f'/system/customer/{cid}'))
    assert detail['country_region_code'] == 'CN' and detail['country_region'] == '中国'
    assert detail['addresses'][0]['country_region_code'] == country_code and detail['addresses'][0]['country_region'] == country_name
    # 客户与地址的地区各自保存；服务端名称以标准目录为准。
    assert detail['country_region_code'] != detail['addresses'][0]['country_region_code']
    payload['country_region_code'] = country_code
    payload['country_region'] = country_name
    payload['addresses'] = detail['addresses']
    ok(call('PUT', f'/system/customer/{cid}', payload))
    detail = ok(call('GET', f'/system/customer/{cid}'))
    ok(call('PUT', f'/system/country-region/{country_code}', {'name_zh': renamed_country, 'name_en': 'Smoke Test Updated'}))
    unchanged = ok(call('GET', f'/system/customer/{cid}'))
    assert unchanged['country_region'] == country_name and unchanged['country_display'] == renamed_country
    assert unchanged['addresses'][0]['country_region'] == country_name
    assert any(r['country_code'] == country_code and r['name_zh'] == renamed_country for r in ok(call('GET', '/system/country-region/options')))
    ok(call('PUT', f'/system/country-region/{country_code}/status', {'status': '1'}))
    payload['addresses'] = detail['addresses']
    ok(call('PUT', f'/system/customer/{cid}', payload))
    after = ok(call('GET', f'/system/customer/{cid}'))
    assert after['addresses'][0]['country_region_code'] == country_code
    # 已停用地区可保留旧引用，但不能添加到新客户或新地址。
    forbidden = dict(payload, customer_name=customer_name + '_new', country_region_code=country_code, addresses=[])
    assert call('POST', '/system/customer', forbidden)['code'] != 200
    assert call('POST', '/system/customer', dict(forbidden, country_region_code='NOT_EXIST'))['code'] != 200
    ok(call('PUT', f'/system/country-region/{country_code}/status', {'status': '0'}))
    # 从旧库模拟未匹配文本；更新客户时原文不得丢失，也不得以该接口写入新的随意文本。
    output = sql(f"INSERT INTO customer_master(customer_name,sales_representative_id,country_region) VALUES ('{legacy_name}',{sales},'未匹配历史地区') RETURNING customer_id;")
    legacy_id = int(output.splitlines()[0])
    old = ok(call('GET', f'/system/customer/{legacy_id}'))
    assert old['country_region'] == '未匹配历史地区' and old['country_region_code'] is None
    old['customer_short_name'] = '保留原值'
    ok(call('PUT', f'/system/customer/{legacy_id}', old))
    assert ok(call('GET', f'/system/customer/{legacy_id}'))['country_region'] == '未匹配历史地区'
    old['country_region'] = '试图新增手填地区'
    assert call('PUT', f'/system/customer/{legacy_id}', old)['code'] != 200
    old['country_region'] = ''
    ok(call('PUT', f'/system/customer/{legacy_id}', old))
    assert ok(call('GET', f'/system/customer/{legacy_id}'))['country_region'] == ''
    print('PASS：迁移唯一匹配/歧义保留/重复执行、标准目录、客户与地址分离、名称快照、停用保留/禁止新选、历史手填值')
finally:
    # 精确限定本次随机名称与编码，避免修改或清理任何真实客户或标准地区。
    sql(f"""BEGIN;
      DELETE FROM sys_tag_relation WHERE module_key='customer_master' AND record_id IN (SELECT customer_id FROM customer_master WHERE customer_name IN ('{customer_name}','{customer_name}_new','{legacy_name}'));
      DELETE FROM customer_contact WHERE customer_id IN (SELECT customer_id FROM customer_master WHERE customer_name IN ('{customer_name}','{customer_name}_new','{legacy_name}'));
      DELETE FROM customer_address WHERE customer_id IN (SELECT customer_id FROM customer_master WHERE customer_name IN ('{customer_name}','{customer_name}_new','{legacy_name}'));
      DELETE FROM customer_master WHERE customer_name IN ('{customer_name}','{customer_name}_new','{legacy_name}');
      DELETE FROM sys_country_region WHERE country_code='{country_code}' AND name_zh IN ('{country_name}','{renamed_country}');
      COMMIT;""")
