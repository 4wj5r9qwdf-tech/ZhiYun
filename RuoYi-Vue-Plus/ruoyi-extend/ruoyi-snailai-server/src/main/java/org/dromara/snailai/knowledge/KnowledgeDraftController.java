package org.dromara.snailai.knowledge;

import com.aizuda.snail.ai.admin.security.annotation.LoginRequired;
import com.aizuda.snail.ai.admin.service.rag.RagDocumentService;
import com.aizuda.snail.ai.admin.vo.rag.RagDocumentUploadRequestVO;
import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.persistence.security.UserSessionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 知识候选需由后台管理员核对后提交，上游负责文档处理与向量化。 */
@RestController
@RequestMapping("/zhiyun/knowledge-drafts")
public class KnowledgeDraftController {
    private final JdbcTemplate jdbc;
    private final RagDocumentService documents;
    public KnowledgeDraftController(JdbcTemplate jdbc, RagDocumentService documents) { this.jdbc = jdbc; this.documents = documents; }
    public record Proposal(Long ragId, String title, String content, String source) {}

    public Map<String, Object> propose(Long owner, Long agent, String conversation, Proposal proposal) {
        if (proposal == null || proposal.ragId() == null || proposal.title() == null || proposal.title().isBlank()
            || proposal.title().length() > 200 || proposal.content() == null || proposal.content().isBlank()
            || proposal.content().length() > 20000 || proposal.source() == null || proposal.source().isBlank()
            || proposal.source().length() > 1000 || conversation == null || !conversation.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("知识草稿内容无效");
        assertBinding(agent, proposal.ragId());
        Long duplicate = jdbc.queryForObject("SELECT count(*) FROM sai_rag_chunk WHERE rag_id = ? AND content = ?", Long.class, proposal.ragId(), proposal.content().trim());
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO zy_snail_knowledge_draft(id,owner_id,agent_id,conversation_id,rag_id,title,content,source) VALUES (?,?,?,?,?,?,?,?)",
            id, owner, agent, conversation, proposal.ragId(), proposal.title().trim(), proposal.content().trim(), proposal.source().trim());
        return Map.of("id", id, "status", "PENDING", "exactDuplicateCount", duplicate == null ? 0 : duplicate,
            "message", "草稿已生成，尚未入库，必须由管理员在页面确认。");
    }
    private void assertBinding(Long agent, Long rag) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM sai_agent a JOIN sai_rag r ON r.id = ? WHERE a.id = ? AND a.rag_enabled = true AND ? = ANY(string_to_array(a.rag_ids, ','))", Long.class, rag, agent, String.valueOf(rag));
        if (count == null || count != 1) throw new IllegalArgumentException("智能体未启用或未绑定该知识库");
    }
    @LoginRequired
    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestParam Long agentId, @RequestParam String conversationId) {
        Long owner = UserSessionUtils.currentUserSession().getId();
        return Result.ok(jdbc.queryForList("SELECT d.id, d.rag_id AS \"ragId\", d.title, d.content, d.source, d.status, d.document_id AS \"documentId\", d.created_at AS \"createdAt\", doc.status AS \"documentStatus\", (SELECT count(*) FROM sai_rag_chunk c WHERE c.rag_id=d.rag_id AND c.content=d.content) AS \"exactDuplicateCount\" FROM zy_snail_knowledge_draft d LEFT JOIN sai_rag_document doc ON doc.id=d.document_id WHERE d.owner_id=? AND d.agent_id=? AND d.conversation_id=? ORDER BY d.created_at", owner, agentId, conversationId));
    }
    @LoginRequired
    @PostMapping("/{id}/reject")
    public Result<String> reject(@PathVariable String id) {
        int changed = jdbc.update("UPDATE zy_snail_knowledge_draft SET status='REJECTED' WHERE id=? AND owner_id=? AND status='PENDING'", id, UserSessionUtils.currentUserSession().getId());
        if (changed != 1) throw new IllegalArgumentException("草稿不存在或状态已变化");
        return Result.ok("已拒绝");
    }
    @LoginRequired
    @PostMapping("/{id}/confirm")
    public Result<Map<String,Object>> confirm(@PathVariable String id) {
        Long owner = UserSessionUtils.currentUserSession().getId();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT agent_id,rag_id,title,content FROM zy_snail_knowledge_draft WHERE id=? AND owner_id=? AND status='PENDING'", id, owner);
        if (rows.size() != 1) throw new IllegalArgumentException("草稿不存在或状态已变化");
        Map<String,Object> row = rows.get(0);
        Long agent = ((Number)row.get("agent_id")).longValue(), rag = ((Number)row.get("rag_id")).longValue();
        assertBinding(agent, rag);
        Long duplicate = jdbc.queryForObject("SELECT count(*) FROM sai_rag_chunk WHERE rag_id = ? AND content = ?", Long.class, rag, row.get("content").toString());
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("知识库已有完全相同的条目，请先检查已有内容");
        if (jdbc.update("UPDATE zy_snail_knowledge_draft SET status='SUBMITTING',confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=? AND status='PENDING'", id, owner) != 1)
            throw new IllegalArgumentException("草稿状态已变化");
        try {
            RagDocumentUploadRequestVO request = new RagDocumentUploadRequestVO();
            request.setRagId(rag); request.setDedupStrategy(2); request.setDedupAction(0);
            String title = row.get("title").toString().replaceAll("[^\\p{L}\\p{N}_ -]", "_");
            byte[] content = row.get("content").toString().getBytes(StandardCharsets.UTF_8);
            var result = documents.upload(new TextFile(title + ".txt", content), request);
            jdbc.update("UPDATE zy_snail_knowledge_draft SET status='SUBMITTED',document_id=? WHERE id=? AND status='SUBMITTING'", result.getId(), id);
            return Result.ok(Map.of("status", "SUBMITTED", "documentId", result.getId(), "message", "已提交知识库处理，请在后台查看解析和向量化状态。"));
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE zy_snail_knowledge_draft SET status='FAILED' WHERE id=? AND status='SUBMITTING'", id);
            throw exception;
        }
    }
    private record TextFile(String filename, byte[] data) implements MultipartFile {
        public String getName() { return "file"; } public String getOriginalFilename() { return filename; }
        public String getContentType() { return "text/plain"; } public boolean isEmpty() { return data.length == 0; }
        public long getSize() { return data.length; } public byte[] getBytes() { return data.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), data); }
    }
}
