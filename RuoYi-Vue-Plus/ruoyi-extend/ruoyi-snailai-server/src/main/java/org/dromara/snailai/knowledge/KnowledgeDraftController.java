/*
 * 文件用途：接收智能体提出的知识草稿并提供查看、驳回和人工确认接口。
 * 文件版本：1.0.0
 * 功能目的：接收智能体提出的知识草稿并提供查看、驳回和人工确认接口。
 * 设计思路：智能体输出仅为候选内容，正式知识写入必须经过有权限人员确认并保留状态记录。
 * 实现方法：按会话身份创建提议；列表按范围返回；驳回/确认时校验草稿状态并更新审计。
 * 关键约束：不能把模型生成结果直接发布为正式知识；重复确认和越权访问必须拒绝。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；sql/SnailAI知识草稿_20261118.sql
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

    /**
     * 从智能体会话登记待审核草稿；调用方必须先校验签名会话及管理员草稿权限。
     * 本方法只写待确认记录，精确重复数用于人工判断，不会自动调用正式知识库上传。
     */
    public Map<String, Object> propose(Long owner, Long agent, String conversation, Proposal proposal) {
        // 对标题、正文、来源和会话标识设上限，避免不受控的模型输出进入草稿表。
        if (proposal == null || proposal.ragId() == null || proposal.title() == null || proposal.title().isBlank()
            || proposal.title().length() > 200 || proposal.content() == null || proposal.content().isBlank()
            || proposal.content().length() > 20000 || proposal.source() == null || proposal.source().isBlank()
            || proposal.source().length() > 1000 || conversation == null || !conversation.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("知识草稿内容无效");
        // 草稿只能提交到当前智能体已启用并绑定的知识库，不能由模型参数扩大知识库范围。
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
    /** 仅返回当前登录用户在指定智能体会话中创建的草稿和相应知识文档处理状态。 */
    @LoginRequired
    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestParam Long agentId, @RequestParam String conversationId) {
        Long owner = UserSessionUtils.currentUserSession().getId();
        return Result.ok(jdbc.queryForList("SELECT d.id, d.rag_id AS \"ragId\", d.title, d.content, d.source, d.status, d.document_id AS \"documentId\", d.created_at AS \"createdAt\", doc.status AS \"documentStatus\", (SELECT count(*) FROM sai_rag_chunk c WHERE c.rag_id=d.rag_id AND c.content=d.content) AS \"exactDuplicateCount\" FROM zy_snail_knowledge_draft d LEFT JOIN sai_rag_document doc ON doc.id=d.document_id WHERE d.owner_id=? AND d.agent_id=? AND d.conversation_id=? ORDER BY d.created_at", owner, agentId, conversationId));
    }
    /** 将本人仍处于待确认状态的草稿标记为驳回；条件更新避免重复操作覆盖已变化状态。 */
    @LoginRequired
    @PostMapping("/{id}/reject")
    public Result<String> reject(@PathVariable String id) {
        int changed = jdbc.update("UPDATE zy_snail_knowledge_draft SET status='REJECTED' WHERE id=? AND owner_id=? AND status='PENDING'", id, UserSessionUtils.currentUserSession().getId());
        if (changed != 1) throw new IllegalArgumentException("草稿不存在或状态已变化");
        return Result.ok("已拒绝");
    }
    /**
     * 由当前用户确认草稿后提交给 SnailAI 文档服务处理，返回文档编号供后台追踪解析/向量化状态。
     * 该操作先抢占 PENDING 状态，避免并发重复提交；外部上传失败时将状态明确置为 FAILED。
     */
    @LoginRequired
    @PostMapping("/{id}/confirm")
    public Result<Map<String,Object>> confirm(@PathVariable String id) {
        Long owner = UserSessionUtils.currentUserSession().getId();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT agent_id,rag_id,title,content FROM zy_snail_knowledge_draft WHERE id=? AND owner_id=? AND status='PENDING'", id, owner);
        if (rows.size() != 1) throw new IllegalArgumentException("草稿不存在或状态已变化");
        Map<String,Object> row = rows.get(0);
        Long agent = ((Number)row.get("agent_id")).longValue(), rag = ((Number)row.get("rag_id")).longValue();
        assertBinding(agent, rag);
        // 确认时重新查重，覆盖草稿创建后知识库内容已经变化的情况。
        Long duplicate = jdbc.queryForObject("SELECT count(*) FROM sai_rag_chunk WHERE rag_id = ? AND content = ?", Long.class, rag, row.get("content").toString());
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("知识库已有完全相同的条目，请先检查已有内容");
        // compare-and-set 只允许一个确认请求获得上传权，其他并发请求会因状态已变化而退出。
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
            // 外部文档服务不受本地数据库事务控制；失败时显式留下失败状态，避免草稿永久停在提交中。
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
