package org.dromara.snailai.files;

import com.aizuda.snail.ai.feature.agent.chain.AgentChatContext;
import com.aizuda.snail.ai.persistence.admin.po.UserPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatDocumentToolBridgeTest {
    @TempDir Path temp;
    private Path keyFile() throws Exception {
        Path path = temp.resolve("bridge.key");
        Files.write(path, new byte[32]);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }
    @Test
    void grantsOnlyFilesOwnedByThisUserAndConversation() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChatDocumentController documents = mock(ChatDocumentController.class);
        String file = "390bc4d9-70df-4c74-83c5-2cb0af5ae104";
        when(jdbc.queryForList(anyString(), eq(7L), eq(8L), eq("conversation-1")))
            .thenReturn(List.of(Map.of("id", file, "name", "订单.docx")));
        when(documents.analyzeForTool(file, 7L, 8L, "conversation-1"))
            .thenReturn(Map.of("content", "订单内容"));
        Path key = keyFile();
        ChatDocumentToolBridge bridge = new ChatDocumentToolBridge(jdbc, documents, key, null);
        AgentChatContext ctx = new AgentChatContext(8L, "conversation-1", "处理订单", null, null, null);
        UserPO user = new UserPO();
        user.setId(7L);
        ctx.setUser(user);
        bridge.handle(ctx);
        assertNotNull(ctx.getSid());
        assertTrue(ctx.getSystemPrompt().contains(file));
        assertEquals(HttpStatus.FORBIDDEN, bridge.analyze(file, null, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, bridge.analyze(file, ctx.getSid(), "wrong").getStatusCode());
        String signature = ChatDocumentSignature.sign(key, ctx.getSid());
        assertEquals(HttpStatus.FORBIDDEN, bridge.analyze("another-file", ctx.getSid(), signature).getStatusCode());
        assertEquals(HttpStatus.OK, bridge.analyze(file, ctx.getSid(), signature).getStatusCode());
        verify(documents).analyzeForTool(file, 7L, 8L, "conversation-1");
    }

    @Test
    void leavesExistingSidAndOpenApiSessionsUntouched() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChatDocumentToolBridge bridge = new ChatDocumentToolBridge(jdbc, mock(ChatDocumentController.class), keyFile(), null);
        UserPO user = new UserPO();
        user.setId(7L);
        AgentChatContext existing = new AgentChatContext(8L, "c", "q", null, null, null);
        existing.setUser(user);
        existing.setSid("existing-session");
        bridge.handle(existing);
        assertEquals("existing-session", existing.getSid());
        AgentChatContext api = new AgentChatContext(8L, "c", "q", null, user, null);
        api.setUser(user);
        bridge.handle(api);
        assertNull(api.getSid());
        verifyNoInteractions(jdbc);
    }
    @Test void boundAdministratorCanCreateOnlySignedDraft() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(7L), eq(8L), eq("c"))).thenReturn(List.of());
        var knowledge = mock(org.dromara.snailai.knowledge.KnowledgeDraftController.class);
        var proposal = new org.dromara.snailai.knowledge.KnowledgeDraftController.Proposal(3L, "标题", "内容", "来源");
        when(knowledge.propose(7L, 8L, "c", proposal)).thenReturn(Map.of("status", "PENDING"));
        Path key = keyFile();
        var bridge = new ChatDocumentToolBridge(jdbc, mock(ChatDocumentController.class), key, knowledge);
        var user = new UserPO(); user.setId(7L); user.setRole(2);
        var agent = new com.aizuda.snail.ai.persistence.agent.po.AgentPO(); agent.setRagEnabled(true); agent.setRagIds("3");
        var ctx = new AgentChatContext(8L, "c", "创建草稿", null, null, null); ctx.setUser(user); ctx.setAgent(agent);
        bridge.handle(ctx);
        assertNotNull(ctx.getSid());
        assertEquals(HttpStatus.FORBIDDEN, bridge.propose(proposal, ctx.getSid(), "bad").getStatusCode());
        assertEquals(HttpStatus.OK, bridge.propose(proposal, ctx.getSid(), ChatDocumentSignature.sign(key, ctx.getSid())).getStatusCode());
        verify(knowledge).propose(7L, 8L, "c", proposal);
    }

}
