/*
 * 文件用途：实现织云内嵌智能体的员工智能体会话历史接口服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的员工智能体会话历史接口服务能力。
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
package org.dromara.snailai.history;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationIdentityRequest;
import com.aizuda.snail.ai.features.resource.ResourceService;
import com.aizuda.snail.ai.openapi.security.OpenApiSessionUtils;
import com.aizuda.snail.ai.openapi.service.OpenApiUserResolver;
import com.aizuda.snail.ai.persistence.resource.po.ResourcePO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 员工历史附件只通过已认证 OpenAPI 返回。 / Serve employee history through authenticated OpenAPI. */
@RestController
@RequestMapping("/openapi/v1/zhiyun/history")
public class EmployeeChatHistoryController {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final OpenApiUserResolver users;
    private final ResourceService resources;
    private final JsonMapper json = JsonMapper.builder().build();

    public EmployeeChatHistoryController(JdbcTemplate jdbc, OpenApiUserResolver users, ResourceService resources) {
        this.jdbc = jdbc;
        this.users = users;
        this.resources = resources;
    }

    /** 按当前应用及员工分页查询会话，关键词覆盖标题和消息。 / List only the caller's conversations. */
    @PostMapping("/conversations")
    public Result<Map<String, Object>> conversations(@RequestBody ConversationListRequest request) {
        if (request.openId() == null || request.agentId() == null) throw new IllegalArgumentException("用户或智能体无效");
        Long userId = users.resolvePlatformUser(OpenApiSessionUtils.current().getAppId(), request.openId()).getId();
        int page = Math.max(1, Math.min(request.page(), 100000));
        int size = Math.max(1, Math.min(request.size(), 50));
        String keyword = request.keyword() == null ? "" : request.keyword().trim();
        if (keyword.length() > 100) keyword = keyword.substring(0, 100);
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String search = "%" + escaped + "%";
        String where = """
             FROM sai_agent_conversation c WHERE c.user_id = ? AND c.agent_id = ?
               AND (? = '' OR c.title ILIKE ? ESCAPE '\\' OR EXISTS (
                 SELECT 1 FROM sai_agent_conversation_record r WHERE r.user_id = c.user_id
                   AND r.agent_id = c.agent_id AND r.conversation_id = c.conversation_id
                   AND r.content ILIKE ? ESCAPE '\\'))
            """;
        Long total = jdbc.queryForObject("SELECT count(*) " + where, Long.class,
            userId, request.agentId(), keyword, search, search);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.conversation_id AS "conversationId", c.title, c.create_dt AS "createDt", c.update_dt AS "updateDt",
                   (SELECT left(r.content, 120) FROM sai_agent_conversation_record r
                     WHERE r.user_id = c.user_id AND r.agent_id = c.agent_id
                       AND r.conversation_id = c.conversation_id AND r.role = 'user'
                     ORDER BY r.create_dt ASC, r.id ASC LIMIT 1) AS "firstQuestion"
            """ + where + " ORDER BY c.update_dt DESC, c.id DESC LIMIT ? OFFSET ?",
            userId, request.agentId(), keyword, search, search, size, (long) (page - 1) * size);
        return Result.ok(Map.of("rows", rows, "total", total == null ? 0 : total, "page", page, "size", size));
    }

    /**
     * 读取 ERP 员工身份映射到的指定智能体会话消息，并提取允许展示的附件元信息。
     * 先验证 OpenAPI 用户与会话所有权，再查询消息，避免仅凭会话 ID 读取他人对话。
     */
    @PostMapping("/messages")
    public Result<List<Map<String, Object>>> messages(@RequestBody OpenApiConversationIdentityRequest request) {
        Long userId = requireConversation(request.getOpenId(), request.getAgentId(), request.getConversationId());
        // 查询条件同时绑定员工、智能体和会话三元组；仅凭 conversationId 不足以确定访问权。
        List<Map<String, Object>> records = jdbc.queryForList("""
            SELECT role, content, metadata, create_dt FROM sai_agent_conversation_record
             WHERE user_id = ? AND agent_id = ? AND conversation_id = ? ORDER BY create_dt ASC, id ASC
            """, userId, request.getAgentId(), request.getConversationId());
        List<Map<String, Object>> result = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", Objects.toString(record.get("role"), ""));
            item.put("content", Objects.toString(record.get("content"), ""));
            item.put("attachments", imageMetadata(Objects.toString(record.get("metadata"), "")));
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 返回当前员工会话中的 PNG/JPEG 图片内容，供 ERP 对话界面展示或后续视觉分析。
     * 图片 ID 必须出现在该会话消息元数据中，且对象存储所有者和业务类型必须匹配。
     */
    @PostMapping("/image")
    public Result<Map<String, String>> image(@RequestBody ImageRequest request) throws IOException {
        Long userId = requireConversation(request.openId(), request.agentId(), request.conversationId());
        Long resourceId = request.resourceId();
        if (resourceId == null || resourceId <= 0) throw new IllegalArgumentException("图片编号无效");
        List<String> metadata = jdbc.queryForList("""
            SELECT metadata FROM sai_agent_conversation_record
             WHERE user_id = ? AND agent_id = ? AND conversation_id = ? AND metadata IS NOT NULL
            """, String.class, userId, request.agentId(), request.conversationId());
        // 先验证图片 ID 出现在当前会话附件清单，再验证资源归属与类型，两个条件缺一不可。
        boolean belongs = metadata.stream().flatMap(value -> imageMetadata(value).stream())
            .anyMatch(item -> resourceId.toString().equals(item.get("id")));
        if (!belongs) throw new IllegalArgumentException("该图片不属于当前对话");
        ResourcePO resource = resources.requireResource(resourceId);
        if (!Objects.equals(resource.getCreatorId(), userId) || !"CHAT_IMAGE".equals(resource.getBizType())
            || !("image/png".equals(resource.getMimeType()) || "image/jpeg".equals(resource.getMimeType()))) {
            throw new IllegalArgumentException("该图片不可访问");
        }
        try (InputStream input = resources.load(resourceId)) {
            // 只多读一个字节检测大小上限，避免异常资源一次性占满内存。
            byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("图片超过允许的大小");
            return Result.ok(Map.of("mimeType", resource.getMimeType(), "content", Base64.getEncoder().encodeToString(bytes)));
        }
    }

    /** 管理员追踪记录关联的历史图片；只在消息唯一匹配时返回。 / Resolve only an unambiguous trace attachment. */
    @PostMapping("/trace-images")
    public Result<List<Map<String, String>>> traceImages(@RequestBody TraceImageRequest request) throws IOException {
        String appId = OpenApiSessionUtils.current().getAppId();
        if (request.externalId() == null || request.agentId() == null || request.conversationId() == null
            || request.startedAt() == null || request.attachments() == null || request.attachments().size() > 4) {
            throw new IllegalArgumentException("记录信息不完整");
        }
        List<Long> usersFound = jdbc.queryForList("SELECT platform_user_id FROM sai_openapi_user WHERE app_id = ? AND external_id = ?",
            Long.class, appId, request.externalId());
        if (usersFound.size() != 1) return Result.ok(List.of());
        Long userId = usersFound.get(0);
        Long ownsConversation = jdbc.queryForObject("SELECT count(*) FROM sai_agent_conversation WHERE user_id = ? AND agent_id = ? AND conversation_id = ?",
            Long.class, userId, request.agentId(), request.conversationId());
        if (ownsConversation == null || ownsConversation != 1) return Result.ok(List.of());
        java.time.LocalDateTime started = java.time.LocalDateTime.parse(request.startedAt());
        List<String> candidates = jdbc.queryForList("""
            SELECT metadata FROM sai_agent_conversation_record
             WHERE user_id = ? AND agent_id = ? AND conversation_id = ? AND role = 'user'
               AND content = ? AND create_dt BETWEEN ? AND ? AND metadata IS NOT NULL
            """, String.class, userId, request.agentId(), request.conversationId(), request.question(),
            started.minusSeconds(30), started.plusSeconds(30));
        List<List<Map<String, String>>> matches = new ArrayList<>();
        for (String candidate : candidates) {
            List<Map<String, String>> images = imageMetadata(candidate);
            if (images.size() != request.attachments().size()) continue;
            boolean same = true;
            for (int i = 0; i < images.size(); i++) {
                ResourcePO resource = resources.getById(Long.valueOf(images.get(i).get("id")));
                Map<String, Object> expected = request.attachments().get(i);
                if (resource == null || !Objects.equals(resource.getCreatorId(), userId)
                    || !"CHAT_IMAGE".equals(resource.getBizType())
                    || (expected.get("resourceId") != null && !images.get(i).get("id").equals(expected.get("resourceId").toString()))
                    || !Objects.equals(resource.getOriginalName(), Objects.toString(expected.get("name"), ""))
                    || !Objects.toString(resource.getFileSize(), "").equals(Objects.toString(expected.get("size"), ""))) {
                    same = false; break;
                }
            }
            if (same) matches.add(images);
        }
        if (matches.size() != 1) return Result.ok(List.of());
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> image : matches.get(0)) {
            ResourcePO resource = resources.requireResource(Long.valueOf(image.get("id")));
            if (!("image/png".equals(resource.getMimeType()) || "image/jpeg".equals(resource.getMimeType()))) continue;
            try (InputStream input = resources.load(resource.getId())) {
                byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
                if (bytes.length > MAX_IMAGE_BYTES) continue;
                result.add(Map.of("name", image.get("name"), "mimeType", resource.getMimeType(),
                    "content", Base64.getEncoder().encodeToString(bytes)));
            }
        }
        return Result.ok(result);
    }

    private Long requireConversation(String openId, Long agentId, String conversationId) {
        if (openId == null || agentId == null || conversationId == null || conversationId.length() > 100) {
            throw new IllegalArgumentException("对话身份无效");
        }
        String appId = OpenApiSessionUtils.current().getAppId();
        Long userId = users.resolvePlatformUser(appId, openId).getId();
        Long count = jdbc.queryForObject("""
            SELECT count(*) FROM sai_agent_conversation WHERE user_id = ? AND agent_id = ? AND conversation_id = ?
            """, Long.class, userId, agentId, conversationId);
        if (count == null || count == 0) throw new IllegalArgumentException("该对话不存在或不属于当前员工");
        return userId;
    }

    private List<Map<String, String>> imageMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return List.of();
        try {
            Map<?, ?> root = json.readValue(metadata, Map.class);
            if (!(root.get("attachments") instanceof List<?> attachments)) return List.of();
            List<Map<String, String>> images = new ArrayList<>();
            for (Object value : attachments) {
                if (!(value instanceof Map<?, ?> item) || !"IMAGE".equals(item.get("type"))
                    || item.get("resourceId") == null) continue;
                String id = item.get("resourceId").toString();
                if (!id.matches("[0-9]{1,19}")) continue;
                images.add(Map.of("id", id, "name", Objects.toString(item.get("fileName"), "图片")));
            }
            return images;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public record ImageRequest(String openId, Long agentId, String conversationId, Long resourceId) { }
    public record ConversationListRequest(String openId, Long agentId, int page, int size, String keyword) { }
    public record TraceImageRequest(String externalId, Long agentId, String conversationId, String question,
                                    String startedAt, List<Map<String, Object>> attachments) { }
}
