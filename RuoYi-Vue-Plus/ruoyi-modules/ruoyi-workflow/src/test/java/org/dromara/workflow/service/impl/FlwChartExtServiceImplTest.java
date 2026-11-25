/*
 * 文件用途：验证流程图提示按当前组织显示待审批人员。
 * 文件版本：1.1.0
 * 功能目的：覆盖 Warm-Flow 2.1 升级后织云组织 DTO 的提示渲染与查询行为。
 * 设计思路：使用内存 mock 验证输出字段和查询参数，不连接真实数据库。
 * 实现方法：构造无审批历史但有当前办理人的流程节点，断言用户、组织、状态展示。
 * 依赖与引用：FlwChartExtServiceImpl；Docs/功能任务/ZY-ERP-20261124-RuoYi-Vue-Plus-6.1.0升级.md
 * 当前责任人：JY；本次修改：Codex（智能体），2026-11-24
 * 修改标记：ZY-ERP-20261124-RuoYi6.1.0；适配组织模型
 */
package org.dromara.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.dromara.common.core.service.DictService;
import org.dromara.system.api.OrgService;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlwChartExtServiceImplTest {

    @Test
    void showsCurrentAssigneeWithoutApprovalHistory() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), FlowUser.class);
        UserService userService = mock(UserService.class);
        OrgService orgService = mock(OrgService.class);
        FlowHisTaskMapper historyMapper = mock(FlowHisTaskMapper.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowUserMapper flowUserMapper = mock(FlowUserMapper.class);
        DictService dictService = mock(DictService.class);
        FlwChartExtServiceImpl service = new FlwChartExtServiceImpl(
            userService, orgService, historyMapper, taskMapper, flowUserMapper, dictService);
        ReflectionTestUtils.setField(service, "nodeTooltip", true);

        NodeJson node = new NodeJson().setNodeCode("approve").setNodeName("部门审批");
        DefJson definition = new DefJson()
            .setFlowName("请假申请")
            .setInstance(new FlowInstance().setId(1L))
            .setNodeList(List.of(node));
        service.initPromptContent(definition);

        when(historyMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of(
            new FlowTask().setId(10L).setNodeCode("approve")));
        when(flowUserMapper.selectList(any())).thenReturn(List.of(
            new FlowUser().setAssociated(10L).setType("1").setProcessedBy("7")));
        UserDTO user = new UserDTO();
        user.setUserId(7L);
        user.setOrgId(3L);
        user.setNickName("张三");
        user.setUserName("zhangsan");
        when(userService.selectListByIds(any())).thenReturn(List.of(user));
        when(orgService.selectOrgNamesByIds(any())).thenReturn(Map.of(3L, "研发组织"));
        when(dictService.getAllDictByDictType(any())).thenReturn(Map.of());

        service.execute(definition);

        var info = node.getPromptContent().getInfo();
        assertTrue(info.stream().anyMatch(item -> item.getPrefix().contains("张三")
            && item.getPrefix().contains("研发组织")));
        assertTrue(info.stream().anyMatch(item -> "用户账号: ".equals(item.getPrefix())
            && "zhangsan".equals(item.getContent())));
        assertTrue(info.stream().anyMatch(item -> "审批状态: ".equals(item.getPrefix())
            && "待审批".equals(item.getContent())));
        verify(userService).selectListByIds(argThat(ids -> ids.size() == 1 && ids.contains(7L)));
        verify(orgService).selectOrgNamesByIds(argThat(ids -> ids.size() == 1 && ids.contains(3L)));
        assertEquals(4, info.size());
    }
}
