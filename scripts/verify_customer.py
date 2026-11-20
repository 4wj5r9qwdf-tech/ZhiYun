#!/usr/bin/env python3
# 文件用途：执行“verify_customer.py”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“verify_customer.py”对应的本地开发、诊断或数据核验辅助操作。
# 设计思路：将可重复环境操作收敛到脚本，依赖环境变量并避免凭据进入代码或输出。
# 实现方法：检查参数和本地环境，再执行目标命令或只读核验并返回明确退出状态。
# 关键约束：不输出凭据、不操作生产数据、不执行无关破坏性操作。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-20
# 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
# 最近测试时间：本次整理未执行功能测试
# 测试人员：本次整理未执行
# 测试状态：待功能回归；历史测试记录待追溯
# 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
"""WSL 客户档案接口烟测。先在 shell 导入 .env；不输出凭据，finally 只清理本次测试数据。"""
import json
import os
import subprocess
import urllib.request
import uuid

BASE = 'http://127.0.0.1:8080'
CLIENT = 'e5cd7e4891bf95d1d19206ce24a7b32e'
token = ''


def request(method, path, body=None):
    headers = {'Content-Type': 'application/json', 'clientid': CLIENT}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    # 本地接口不经过 Windows/WSL 代理，避免代理把 127.0.0.1 请求转发到外网。
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=15) as response:
        return json.load(response)


def ok(response):
    assert response.get('code') == 200, '接口返回失败，code=' + str(response.get('code'))
    return response.get('data')


name = '__customer_smoke_' + uuid.uuid4().hex
ids = []
tag_id = None
module_existed = True
reference_table = 'customer_delete_test_' + uuid.uuid4().hex
try:
    password = os.environ.get('SUPER_ADMIN_PASSWORD')
    if not password:
        raise RuntimeError('未设置 SUPER_ADMIN_PASSWORD，无法运行认证接口烟测')
    token = ok(request('POST', '/auth/login', {'clientId': CLIENT, 'grantType': 'password',
               'username': os.environ.get('SUPER_ADMIN_USERNAME', 'admin'), 'password': password}))['access_token']
    people = ok(request('GET', '/system/customer/sales-options'))
    assert people, '没有可选人员，请先维护人员信息'
    module_existed = any(m['moduleKey'] == 'customer_master' for m in ok(request('GET', '/system/tag/modules')))
    tag = ok(request('POST', '/system/tag/ensure', {'moduleKey': 'customer_master', 'moduleName': '客户档案', 'tagName': name}))
    tag_id = int(tag['tagId'])
    payload = {'customer_name': name, 'sales_representative_id': people[0]['value'],
               'tagIds': [str(tag_id)], 'contacts': [{'contact_name': '测试联系人', 'phone_number': '00000000000', 'is_primary': True}, {'contact_name': '测试联系人二', 'phone_number': '00000000001'}],
               'addresses': [{'address_type': '样衣收件', 'recipient_name': '测试收件人', 'recipient_phone': '00000000000', 'address_line': '接口烟测地址', 'is_default': True}, {'address_type': '办公联系', 'address_line': '接口烟测办公地址', 'is_default': True}]}
    customer_id = int(ok(request('POST', '/system/customer', payload)))
    ids.append(customer_id)
    detail = ok(request('GET', f'/system/customer/{customer_id}'))
    assert detail['customer_code'].startswith('KH')
    assert len(detail['contacts']) == len(detail['addresses']) == 2
    assert str(tag_id) in detail['tagIds']
    payload['customer_short_name'] = '烟测简称'
    ok(request('PUT', f'/system/customer/{customer_id}', payload))
    assert ok(request('GET', f'/system/customer/{customer_id}'))['customer_short_name'] == '烟测简称'
    # 无效联系人必须阻止整份保存，主档不能出现部分更新。
    invalid = {**payload, 'customer_short_name': '不应保存', 'contacts': [{'contact_name': '', 'phone_number': ''}]}
    assert request('PUT', f'/system/customer/{customer_id}', invalid)['code'] != 200
    assert ok(request('GET', f'/system/customer/{customer_id}'))['customer_short_name'] == '烟测简称'
    duplicate = int(ok(request('POST', '/system/customer', payload)))
    ids.append(duplicate)
    assert ok(request('GET', '/system/customer/duplicates?name=' + name)) == 2
    assert ok(request('GET', '/system/customer/list?keyword=' + name))['total'] == 2
    # 分类统计包含全部分页；两个新建客户均关联本次测试标签。
    counts = ok(request('GET', '/system/customer/category-counts'))
    assert counts['total'] >= 2
    assert any(str(item['tag_id']) == str(tag_id) and item['customer_count'] == 2 for item in counts['tags'])


    # 模拟未来业务表通过外键引用客户；有引用时拒绝删除，无引用后允许删除。
    subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'],
                   input=f'CREATE TABLE {reference_table} (customer_id bigint REFERENCES customer_master(customer_id)); INSERT INTO {reference_table} VALUES ({duplicate});',
                   text=True, check=True, stdout=subprocess.DEVNULL)
    assert request('DELETE', f'/system/customer/{duplicate}')['code'] != 200
    assert ok(request('GET', f'/system/customer/{duplicate}'))['customer_name'] == name
    subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'],
                   input=f'DROP TABLE {reference_table};', text=True, check=True, stdout=subprocess.DEVNULL)
    ok(request('DELETE', f'/system/customer/{duplicate}'))
    assert request('GET', f'/system/customer/{duplicate}')['code'] != 200
    ok(request('PUT', f'/system/tag/{tag_id}/disable'))
    assert ok(request('GET', f'/system/customer/{customer_id}'))['tagOptions'][0]['disabled'] is True
    ok(request('PUT', f'/system/customer/{customer_id}', payload))
    assert request('POST', '/system/customer', payload)['code'] != 200
    ok(request('PUT', f'/system/customer/{customer_id}/status', {'status': '1'}))
    assert ok(request('GET', f'/system/customer/{customer_id}'))['status'] == '1'
    assert all(str(row['value']) != str(customer_id) for row in ok(request('GET', '/system/customer/options')))
    ok(request('PUT', f'/system/customer/{customer_id}/status', {'status': '0'}))
    print('PASS：编号、新增、详情、编辑、多联系人/地址、标签关联、同名允许、筛选、无效数据原子性、停用与恢复')
finally:
    # 只删除本次创建且名称匹配随机前缀的数据，绝不清理其他客户或标签。
    sql = ['BEGIN;', f'DROP TABLE IF EXISTS {reference_table};']
    for cid in ids:
        cond = f"customer_id={cid} AND customer_name='{name}'"
        sql += [f"DELETE FROM sys_tag_relation WHERE module_key='customer_master' AND record_id IN (SELECT customer_id FROM customer_master WHERE {cond});",
                f"DELETE FROM customer_contact WHERE customer_id IN (SELECT customer_id FROM customer_master WHERE {cond});",
                f"DELETE FROM customer_address WHERE customer_id IN (SELECT customer_id FROM customer_master WHERE {cond});",
                f"DELETE FROM customer_master WHERE {cond};"]
    if tag_id:
        sql.append(f"DELETE FROM sys_tag WHERE tag_id={tag_id} AND tag_name='{name}' AND module_key='customer_master';")
    if not module_existed:
        sql.append("DELETE FROM sys_tag_module WHERE module_key='customer_master' AND NOT EXISTS(SELECT 1 FROM sys_tag WHERE module_key='customer_master');")
    sql.append('COMMIT;')
    subprocess.run(['sudo', '-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'],
                   input='\n'.join(sql), text=True, check=True, stdout=subprocess.DEVNULL)
