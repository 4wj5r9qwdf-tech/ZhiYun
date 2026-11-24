/*
 * 文件用途：验证建筑列表对档案状态枚举的筛选契约。
 * 文件版本：1.0.0
 * 功能目的：使状态筛选接受在用、闲置和维修三个已定义值，并拒绝无效值。
 * 设计思路：直接调用控制器并捕获 SQL 参数，不连接数据库或读取业务数据。
 * 实现方法：核对状态 2 被绑定到查询条件，非法状态在访问 JdbcTemplate 前被拒绝。
 * 关键约束：建筑状态编码 0/1/2 的含义须与档案字段矩阵一致。
 * 依赖与引用：BuildingController.java；Docs/API/建筑档案API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-建筑状态查询契约测试；覆盖维修状态与非法筛选
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（2 项状态筛选测试）
 * 测试范围与记录位置：Maven Surefire 报告
 */
package org.dromara.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class BuildingControllerTest {

    @Test
    void listAllowsMaintenanceStatusAndBindsItAsString() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(java.util.List.of());
        BuildingController controller = new BuildingController(jdbcTemplate);

        var result = controller.list(null, null, "2");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("AND b.status = ?"));
        assertArrayEquals(new Object[] {"2"}, parameters.getValue());
        assertEquals(0, result.getData().size());
    }

    @Test
    void listRejectsUnknownStatusBeforeQuerying() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BuildingController controller = new BuildingController(jdbcTemplate);

        var result = controller.list(null, null, "3");

        assertTrue(result.getMsg().contains("0（在用）、1（闲置）或2（维修）"));
        verifyNoInteractions(jdbcTemplate);
    }
}
