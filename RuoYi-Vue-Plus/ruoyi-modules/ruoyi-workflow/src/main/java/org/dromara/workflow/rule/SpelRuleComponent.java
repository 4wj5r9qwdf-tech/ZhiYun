package org.dromara.workflow.rule;

import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.api.OrgService;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.springframework.stereotype.Component;

/**
 * spel表达式规则组件
 * <p>
 * 通过该组件统一管理流程定义中的spel表达式
 * </p>
 *
 * @author Michelle.Chung
 */
@ConditionalOnEnable
@Slf4j
@Component
@RequiredArgsConstructor
public class SpelRuleComponent {

    private final OrgService orgService;

    /**
     * 通过发起人部门 ID 获取部门负责人。
     *
     * @param initiatorOrgId 发起人部门 ID
     * @return 部门负责人用户 ID
     */
    public Long selectOrgLeaderById(Long initiatorOrgId) {
        Long leaderId = orgService.selectOrgLeaderById(initiatorOrgId);
        if (ObjectUtil.isNull(leaderId)) {
            throw new ServiceException("当前部门未设置负责人，请联系管理员操作。");
        }
        return leaderId;
    }

}
