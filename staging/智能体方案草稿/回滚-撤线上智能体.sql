-- 撤线上智能体（2026-10-31 用户拍板"全部回滚"时执行过一次；方案定稿前保持系统无智能体痕迹）
-- 幂等：重复执行无副作用
BEGIN;

-- 1) 撤授权 + 撤菜单行
DELETE FROM sys_role_menu WHERE menu_id = 1763100000000000007;
DELETE FROM sys_menu WHERE menu_id = 1763100000000000007;

-- 2) 撤两张表（留痕/动作目录；op_type 列随表一起消失）
DROP TABLE IF EXISTS sys_agent_log;
DROP TABLE IF EXISTS sys_agent_action;

COMMIT;

-- 之后：重建不含 AgentController 的 jar 并重启；踢旧会话（权限缓存里可能还留着 system:agent:chat）
-- 撤销此回滚 = 重放《主建表-第12节-智能体表.sql》+ 恢复 AgentController/AgentChat（见 README）
