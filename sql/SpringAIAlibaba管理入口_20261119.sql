-- Spring AI Alibaba Agent Studio 管理入口；仅登记权限，不默认授权任何角色。
BEGIN;

INSERT INTO sys_menu(menu_id,parent_id,menu_name,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time)
SELECT 1763300000000000019,1761400000000000001,'Spring AI Alibaba',190,'spring-ai-alibaba','system/spring-ai-alibaba/index','N','Y','C','0','0','system:spring-ai-alibaba:list','#',now()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:spring-ai-alibaba:list')
ON CONFLICT(menu_id) DO NOTHING;

UPDATE sys_menu
SET parent_id=1761400000000000001,menu_name='Spring AI Alibaba',order_num=190,path='spring-ai-alibaba',component='system/spring-ai-alibaba/index',menu_type='C',visible='0',status='0',perms='system:spring-ai-alibaba:list'
WHERE menu_id=1763300000000000019;

COMMIT;
