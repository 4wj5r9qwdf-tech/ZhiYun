# 文件用途：执行“verify_size.py”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“verify_size.py”对应的本地开发、诊断或数据核验辅助操作。
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
