/*
 * 文件用途：实现织云内嵌智能体的ChatDocumentControllerTest服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的ChatDocumentControllerTest服务能力。
 * 设计思路：模型和文件服务只处理经 ERP 身份验证的请求；模型输出不能直接成为正式业务数据。
 * 实现方法：校验会话/员工/文件/配置后调用分析、存储或提议逻辑并返回结构化结果。
 * 关键约束：文件访问、敏感数据和知识发布遵守服务端权限与人工确认边界。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.snailai.files;

import com.aizuda.snail.ai.admin.security.annotation.LoginRequired;
import com.aizuda.snail.ai.features.resource.ResourceService;
import com.aizuda.snail.ai.persistence.admin.po.UserPO;
import com.aizuda.snail.ai.persistence.security.UserSessionUtils;
import com.aizuda.snail.ai.persistence.resource.po.ResourcePO;
import com.aizuda.snail.ai.features.resource.enums.ResourceBizTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatDocumentControllerTest {
    @AfterEach void clearUser() { UserSessionUtils.clearUserSession(); }

    @Test
    void foreignConversationAndMissingOwnedRecordCannotLoadResource() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceService resources = mock(ResourceService.class);
        UserPO user = new UserPO(); user.setId(21L); UserSessionUtils.setUserSession(user);
        ChatDocumentAnalyzer analyzer = mock(ChatDocumentAnalyzer.class);
        ChatDocumentController controller = new ChatDocumentController(jdbc, resources, analyzer);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> controller.download("id", 2L, "session-1"));
        verifyNoInteractions(resources);

        assertThrows(IllegalArgumentException.class, () -> controller.analyze("id", 2L, "session-1"));
        verifyNoInteractions(analyzer);

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(), any(), any(), any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> controller.download("id", 2L, "session-1"));
        verifyNoInteractions(resources);
    }

    @Test
    void endpointsRequireLogin() throws Exception {
        for (String method : List.of("upload", "list", "download", "analyze")) {
            assertTrue(java.util.Arrays.stream(ChatDocumentController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(method)).allMatch(m -> m.isAnnotationPresent(LoginRequired.class)));
        }
    }

    @Test
    void uploadStoresOriginalAndBindsAuthenticatedOwner() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceService resources = mock(ResourceService.class);
        UserPO user = new UserPO(); user.setId(21L); UserSessionUtils.setUserSession(user);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(0L);
        ResourcePO stored = new ResourcePO(); stored.setId(99L);
        when(resources.upload(any(InputStream.class), eq("订单.md"), eq(6L), anyString(), eq(2L), eq(21L))).thenReturn(stored);
        var file = new MockMultipartFile("file", "订单.md", "text/markdown", "原件".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = new ChatDocumentController(jdbc, resources, mock(ChatDocumentAnalyzer.class)).upload(2L, "session-1", file);
        assertNotNull(result.getData().get("id"));
        verify(jdbc).update(anyString(), anyString(), eq(99L), eq(21L), eq(2L), eq("session-1"), eq("订单.md"), eq(6), eq("md"));
    }

    @Test
    void analysisUsesOnlyOwnedOriginal() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceService resources = mock(ResourceService.class);
        ChatDocumentAnalyzer analyzer = mock(ChatDocumentAnalyzer.class);
        UserPO user = new UserPO(); user.setId(21L); UserSessionUtils.setUserSession(user);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(0L);
        when(jdbc.queryForList(anyString(), eq("file-id"), eq(21L), eq(2L), eq("session-1")))
            .thenReturn(List.of(Map.of("resource_id", 99L, "original_name", "订单.md", "file_type", "md")));
        ResourcePO stored = new ResourcePO(); stored.setId(99L); stored.setCreatorId(21L);
        stored.setBizType(ResourceBizTypeEnum.ATTACHMENT.getValue());
        when(resources.requireResource(99L)).thenReturn(stored);
        when(resources.load(99L)).thenReturn(new java.io.ByteArrayInputStream(new byte[]{1}));
        when(analyzer.analyze(any(InputStream.class), eq("md"))).thenReturn(Map.of("markdown", "结果"));
        var controller = new ChatDocumentController(jdbc, resources, analyzer);
        assertEquals("结果", controller.analyze("file-id", 2L, "session-1").getData().get("markdown"));

        stored.setCreatorId(22L);
        assertThrows(IllegalArgumentException.class, () -> controller.analyze("file-id", 2L, "session-1"));
        verify(analyzer, times(1)).analyze(any(InputStream.class), eq("md"));
    }
}
