/*
 * 文件用途：验证组织更新中根节点父级 null 与自父级校验的契约。
 * 文件版本：1.0.0
 * 功能目的：允许明确传入 null 表示根节点，同时阻止组织把自身设为直接上级。
 * 设计思路：直接调用控制器并模拟服务依赖，不连接数据库或写入组织记录。
 * 实现方法：覆盖 parentId=null 和 parentId=orgId 两种更新请求。
 * 关键约束：null 父级不能触发空指针；自父级仍须在服务写入前拒绝。
 * 依赖与引用：SysOrgController.java；SysOrgBo.java
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-组织父级空值契约测试
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（2 项父级契约测试）
 * 测试范围与记录位置：Maven Surefire 报告
 */
package org.dromara.system.controller.system;

import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.mapper.SysOrgManageMapper;
import org.dromara.system.service.ISysOrgService;
import org.dromara.system.service.ISysPostService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysOrgControllerContractTest {

    @Test
    void editAllowsNullParentForRootNode() {
        JdbcTemplate auditJdbc = mock(JdbcTemplate.class);
        ISysOrgService orgService = mock(ISysOrgService.class);
        when(auditJdbc.queryForList(anyString(), any(Object[].class))).thenReturn(java.util.List.of());
        when(orgService.checkOrgNameUnique(any(SysOrgBo.class))).thenReturn(true);
        when(orgService.updateOrg(any(SysOrgBo.class))).thenReturn(0);
        SysOrgController controller = new SysOrgController(
            auditJdbc, orgService, mock(ISysPostService.class), mock(SysOrgManageMapper.class));
        SysOrgBo org = new SysOrgBo();
        org.setOrgId(100L);
        org.setParentId(null);
        org.setOrgName("根组织契约测试");
        org.setStatus("0");

        controller.edit(org);

        verify(orgService).updateOrg(org);
    }

    @Test
    void editRejectsSelfAsParentBeforeSaving() {
        JdbcTemplate auditJdbc = mock(JdbcTemplate.class);
        ISysOrgService orgService = mock(ISysOrgService.class);
        when(orgService.checkOrgNameUnique(any(SysOrgBo.class))).thenReturn(true);
        SysOrgController controller = new SysOrgController(
            auditJdbc, orgService, mock(ISysPostService.class), mock(SysOrgManageMapper.class));
        SysOrgBo org = new SysOrgBo();
        org.setOrgId(100L);
        org.setParentId(100L);
        org.setOrgName("自父级契约测试");
        org.setStatus("0");

        var result = controller.edit(org);

        assertTrue(result.getMsg().contains("上级部门不能是自己"));
        verify(orgService, never()).updateOrg(any(SysOrgBo.class));
    }
}
