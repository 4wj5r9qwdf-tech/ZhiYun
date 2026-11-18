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
