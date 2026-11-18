-- 将框架预置的部门负责人表达式接到织云组织架构。
UPDATE flow_spel
SET method_name='selectOrgLeaderById', method_params='initiatorOrgId',
    view_spel='#{@spelRuleComponent.selectOrgLeaderById(#initiatorOrgId)}',
    remark='发起人所在部门负责人', update_time=now()
WHERE component_name='spelRuleComponent'
  AND method_name='selectDeptLeaderById' AND method_params='initiatorDeptId';

-- 沿用框架原有按钮 ID 和角色关系，将维护权限显示在审批模块下。
UPDATE sys_menu
SET parent_id=1763200000000000001, status='0'
WHERE parent_id=1761400000000011620
  AND perms IN ('workflow:definition:query','workflow:definition:edit','workflow:definition:remove',
                'workflow:definition:export','workflow:definition:import','workflow:definition:copy','workflow:definition:active')
  AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1763200000000000001);
