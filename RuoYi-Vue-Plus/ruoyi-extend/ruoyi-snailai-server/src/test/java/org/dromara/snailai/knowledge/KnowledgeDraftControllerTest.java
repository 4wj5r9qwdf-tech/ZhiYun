/*
 * 文件用途：实现织云内嵌智能体的KnowledgeDraftControllerTest服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的KnowledgeDraftControllerTest服务能力。
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
