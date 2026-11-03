from pathlib import Path
import os,uuid,subprocess
# 复用现有客户烟测的认证请求函数，不运行客户测试主体。
ns={}
source=Path('scripts/verify_customer.py').read_text().split("name = '__customer_smoke_'")[0]
exec(source,ns)
req=ns['request'];ok=ns['ok']
ns['token']=ok(req('POST','/auth/login',{'clientId':ns['CLIENT'],'grantType':'password','username':os.environ.get('SUPER_ADMIN_USERNAME','admin'),'password':os.environ['SUPER_ADMIN_PASSWORD']}))['access_token']
name='__size_test_'+uuid.uuid4().hex
ids=[];groups=[]
try:
 for suffix in ['S','M']:
  ids.append(int(ok(req('POST','/system/size-data/size',{'name':name+suffix,'sort_order':1}))))
 payload={'name':name,'sizeIds':[str(ids[1]),str(ids[0])],'sort_order':0}
 gid=int(ok(req('POST','/system/size-data/size-group',payload)));groups.append(gid)
 row=next(x for x in ok(req('GET','/system/size-data/size-group/list')) if int(x['id'])==gid)
 assert [int(x['id']) for x in row['items']]==[ids[1],ids[0]]
 assert req('POST','/system/size-data/size-group',{**payload,'sizeIds':[str(ids[0]),str(ids[0])]})['code']!=200
 ok(req('PUT',f'/system/size-data/size/{ids[0]}/status',{'status':'1'}))
 ok(req('PUT',f'/system/size-data/size-group/{gid}',payload))
 assert req('POST','/system/size-data/size-group',payload)['code']!=200
 ok(req('PUT',f'/system/size-data/size/{ids[0]}/status',{'status':'0'}))
 print('PASS: size/group create, ordered details, duplicate rejection, inactive retention and new-reference rejection')
finally:
 sql=['BEGIN;']
 for gid in groups:
  sql += [f"DELETE FROM sys_size_group_item WHERE group_id={gid};",f"DELETE FROM sys_size_group WHERE id={gid} AND name='{name}';"]
 for sid in ids: sql += [f"DELETE FROM sys_size WHERE id={sid} AND name LIKE '{name}%';"]
 sql+=['COMMIT;']
 subprocess.run(['sudo','-n','-u','postgres','psql','-v','ON_ERROR_STOP=1','-d','zhizhun_erp'],input='\n'.join(sql),text=True,check=True,stdout=subprocess.DEVNULL)
