package org.dromara.snailai.knowledge;

import com.aizuda.snail.ai.admin.service.rag.RagDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeDraftControllerTest {
    @Test void proposalIsOnlyDraftAndChecksAgentBinding() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagDocumentService documents = mock(RagDocumentService.class);
        when(jdbc.queryForObject(contains("FROM sai_agent"), eq(Long.class), any(), any(), any())).thenReturn(1L);
        when(jdbc.queryForObject(contains("FROM sai_rag_chunk"), eq(Long.class), any(), any())).thenReturn(0L);
        KnowledgeDraftController controller = new KnowledgeDraftController(jdbc, documents);
        Map<String,Object> draft = controller.propose(1L, 2L, "conv-1", new KnowledgeDraftController.Proposal(3L, "词汇", "术语内容", "员工提供"));
        assertEquals("PENDING", draft.get("status"));
        verify(jdbc).update(contains("INSERT INTO zy_snail_knowledge_draft"), any(), eq(1L), eq(2L), eq("conv-1"), eq(3L), any(), any(), any());
        verifyNoInteractions(documents);
    }
    @Test void unboundKnowledgeBaseIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("FROM sai_agent"), eq(Long.class), any(), any(), any())).thenReturn(0L);
        var controller = new KnowledgeDraftController(jdbc, mock(RagDocumentService.class));
        assertThrows(IllegalArgumentException.class, () -> controller.propose(1L, 2L, "c", new KnowledgeDraftController.Proposal(3L, "标题", "内容", "来源")));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void confirmationSubmitsOnlyOnceAndReportsProcessing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagDocumentService documents = mock(RagDocumentService.class);
        var user = new com.aizuda.snail.ai.persistence.admin.po.UserPO(); user.setId(1L);
        com.aizuda.snail.ai.persistence.security.UserSessionUtils.setUserSession(user);
        try {
            when(jdbc.queryForList(contains("status='PENDING'"), eq("id"), eq(1L)))
                .thenReturn(java.util.List.of(Map.of("agent_id", 2L, "rag_id", 3L, "title", "词汇", "content", "内容")));
            when(jdbc.queryForObject(contains("FROM sai_agent"), eq(Long.class), any(), any(), any())).thenReturn(1L);
            when(jdbc.queryForObject(contains("FROM sai_rag_chunk"), eq(Long.class), any(), any())).thenReturn(0L);
            when(jdbc.update(contains("status='SUBMITTING'"), eq("id"), eq(1L))).thenReturn(1);
            var result = new com.aizuda.snail.ai.admin.vo.rag.RagDocumentResponseVO(); result.setId(99L);
            when(documents.upload(any(), any())).thenReturn(result);
            var response = new KnowledgeDraftController(jdbc, documents).confirm("id");
            assertEquals("SUBMITTED", response.getData().get("status"));
            verify(documents).upload(any(), any());
            verify(jdbc).update(contains("status='SUBMITTED'"), eq(99L), eq("id"));
        } finally { com.aizuda.snail.ai.persistence.security.UserSessionUtils.clearUserSession(); }
    }

}
