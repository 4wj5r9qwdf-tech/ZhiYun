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

    @PostMapping("/messages")
    public Result<List<Map<String, Object>>> messages(@RequestBody OpenApiConversationIdentityRequest request) {
        Long userId = requireConversation(request.getOpenId(), request.getAgentId(), request.getConversationId());
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

    @PostMapping("/image")
    public Result<Map<String, String>> image(@RequestBody ImageRequest request) throws IOException {
        Long userId = requireConversation(request.openId(), request.agentId(), request.conversationId());
        Long resourceId = request.resourceId();
        if (resourceId == null || resourceId <= 0) throw new IllegalArgumentException("图片编号无效");
        List<String> metadata = jdbc.queryForList("""
            SELECT metadata FROM sai_agent_conversation_record
             WHERE user_id = ? AND agent_id = ? AND conversation_id = ? AND metadata IS NOT NULL
            """, String.class, userId, request.agentId(), request.conversationId());
        boolean belongs = metadata.stream().flatMap(value -> imageMetadata(value).stream())
            .anyMatch(item -> resourceId.toString().equals(item.get("id")));
        if (!belongs) throw new IllegalArgumentException("该图片不属于当前对话");
        ResourcePO resource = resources.requireResource(resourceId);
        if (!Objects.equals(resource.getCreatorId(), userId) || !"CHAT_IMAGE".equals(resource.getBizType())
            || !("image/png".equals(resource.getMimeType()) || "image/jpeg".equals(resource.getMimeType()))) {
            throw new IllegalArgumentException("该图片不可访问");
        }
        try (InputStream input = resources.load(resourceId)) {
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
