package org.dromara.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aizuda.snail.ai.common.model.PageResult;
import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiAgentIdentityRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiAgentVO;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatAttachmentRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationIdentityRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationQueryRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationVO;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiCreateConversationRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiMessageVO;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserQueryRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserAgentRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserRegisterRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserVO;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceResponse;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceUploadRequest;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiAgentClient;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiChatClient;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiConversationClient;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiUserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.domain.SnailAiAgentOption;
import org.dromara.ai.domain.SnailAiApprovalRequest;
import org.dromara.ai.domain.SnailAiChatRequest;
import org.dromara.ai.domain.SnailAiConversationRequest;
import org.dromara.ai.snailai.SnailAiAttachmentStore;
import org.dromara.ai.snailai.SnailAiAttachmentStore.UploadedAttachment;
import org.dromara.ai.snailai.SnailAiChatSession;
import org.dromara.ai.snailai.SnailAiChatSessionStore;
import org.dromara.ai.snailai.SnailAiResourceClient;
import org.dromara.ai.snailai.SnailAiHistoryClient;
import org.dromara.ai.snailai.SnailAiTraceService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.api.model.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

/**
 * ERP 员工侧 SnailAI 入口。
 * 所有 OpenAPI 身份均由当前 ERP 登录态确定，客户端不能指定其他员工身份。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/snail-ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "snail-ai.open-api", name = "enabled", havingValue = "true")
public class SnailAiController extends BaseController {

    private static final int SNAIL_AI_SUCCESS = 1;
    private final OpenApiUserClient userClient;
    private final OpenApiAgentClient agentClient;
    private final OpenApiConversationClient conversationClient;
    private final OpenApiChatClient chatClient;
    private final SnailAiChatSessionStore sessionStore;
    private final SnailAiResourceClient resourceClient;
    private final SnailAiHistoryClient historyClient;
    private final SnailAiAttachmentStore attachmentStore;
    private final SnailAiTraceService traceService;

    @Value("${snail-ai.erp.default-agent-id:2}")
    private Long defaultAgentId;

    /** 上传图片并绑定员工短时票据。 / Upload images using user-bound short-lived tickets. */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        StpUtil.checkLogin();
        Long userId = LoginHelper.getUserId();
        if (userId == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小须在 10 MB 以内");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.length() > 200) {
            throw new IllegalArgumentException("文件名无效");
        }
        String safeName = originalName.replaceAll("[\\\\/\\\\\\\\]", "_");
        String lowerName = safeName.toLowerCase(java.util.Locale.ROOT);
        byte[] content = file.getBytes();
        String mimeType = detectImageMimeType(content);
        boolean image = mimeType != null && (lowerName.endsWith(".png") && "image/png".equals(mimeType)
            || (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) && "image/jpeg".equals(mimeType));
        if (image && file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小须在 5 MB 以内");
        }

        Long resourceId = null;
        String extractedText = null;
        if (image) {
            OpenApiUserVO user = ensureOpenApiUser();
            OpenApiResourceUploadRequest request = new OpenApiResourceUploadRequest();
            request.setOpenId(user.getOpenId());
            request.setOriginalName(safeName);
            request.setFileSize(file.getSize());
            request.setContent(content);
            Result<OpenApiResourceResponse> result = resourceClient.upload(request);
            if (result == null || result.getStatus() != SNAIL_AI_SUCCESS || result.getData() == null
                || result.getData().getId() == null) {
                throw new IllegalStateException("图片上传失败，请检查 SnailAI 资源存储配置");
            }
            resourceId = result.getData().getId();
            log.info("SnailAI image uploaded for ERP userId={}, size={}, mimeType={}", userId, file.getSize(), mimeType);
        } else {
            throw new IllegalArgumentException("当前仅支持 PNG、JPG、JPEG 图片，文档上传正在重新设计");
        }
        String ticket = attachmentStore.put(userId, safeName, mimeType, file.getSize(), resourceId, extractedText);
        return R.ok(Map.of("id", ticket, "name", safeName, "mimeType", mimeType,
            "size", String.valueOf(file.getSize()), "kind", image ? "image" : "document",
            "extractedChars", String.valueOf(extractedText == null ? 0 : extractedText.length())));
    }

    /** 保留框架 OpenAPI 用户注册能力，但不向浏览器暴露可伪造的员工字段。 */
    @PostMapping("/user/register")
    public R<OpenApiUserVO> registerCurrentUser() {
        return R.ok(ensureOpenApiUser());
    }

    /** 返回当前 ERP 员工可使用的智能体；若未订阅列表为空，则回退到配置的织云默认智能体。 */
    @GetMapping("/agents")
    public R<List<SnailAiAgentOption>> listAgents() {
        OpenApiUserVO user = ensureOpenApiUser();
        List<OpenApiAgentVO> agents = listUserAgents(user.getOpenId());
        if (agents.isEmpty() && defaultAgentId != null) {
            OpenApiAgentVO fallback = getAgent(defaultAgentId);
            if (fallback != null) {
                subscribeDefaultAgent(user.getOpenId(), defaultAgentId);
                agents = List.of(fallback);
            }
        }
        return R.ok(agents.stream().map(SnailAiAgentOption::from).toList());
    }

    /** 新建 ERP 员工自己的 SnailAI 对话。 */
    @PostMapping("/conversations")
    public R<OpenApiConversationVO> createConversation(@Valid @RequestBody SnailAiConversationRequest body) {
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), body.getAgentId());
        OpenApiCreateConversationRequest request = new OpenApiCreateConversationRequest();
        request.setAgentId(body.getAgentId());
        request.setOpenId(user.getOpenId());
        request.setTitle(body.getTitle() == null || body.getTitle().isBlank() ? "ERP 智能问答" : body.getTitle().trim());
        Result<OpenApiConversationVO> result = conversationClient.createConversation(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS || result.getData() == null) {
            throw new IllegalStateException(result == null ? "新建智能体对话失败" : result.getMessage());
        }
        return R.ok(result.getData());
    }

    /** 只查询当前员工自己的对话记录。 */
    @GetMapping("/conversations")
    public R<List<OpenApiConversationVO>> listConversations(@RequestParam Long agentId) {
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), agentId);
        OpenApiConversationQueryRequest request = new OpenApiConversationQueryRequest();
        request.setAgentId(agentId);
        request.setOpenId(user.getOpenId());
        request.setPage(1);
        request.setSize(30);
        PageResult<List<OpenApiConversationVO>> result = conversationClient.listConversations(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            throw new IllegalStateException(result == null ? "查询智能体对话失败" : result.getMessage());
        }
        return R.ok(result.getData() == null ? List.of() : result.getData());
    }

    /** 员工本人对话分页和搜索。 / Paginate and search the current employee's conversations. */
    @GetMapping("/conversations/history")
    public R<Map<String, Object>> conversationHistory(@RequestParam Long agentId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), agentId);
        Result<Map<String, Object>> result = historyClient.conversations(Map.of(
            "openId", user.getOpenId(), "agentId", agentId, "page", Math.max(1, page),
            "size", Math.max(1, Math.min(50, size)), "keyword", keyword.length() > 100 ? keyword.substring(0, 100) : keyword));
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS || result.getData() == null) {
            throw new IllegalStateException("查询历史对话失败");
        }
        return R.ok(result.getData());
    }

    /** 对话消息使用当前 ERP 员工的 OpenAPI 身份查询，不能由客户端提交 openId。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public R<List<Map<String, Object>>> listMessages(@PathVariable String conversationId,
                                                   @RequestParam Long agentId) {
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), agentId);
        OpenApiConversationIdentityRequest request = new OpenApiConversationIdentityRequest();
        request.setAgentId(agentId);
        request.setConversationId(conversationId);
        request.setOpenId(user.getOpenId());
        Result<List<Map<String, Object>>> result = historyClient.messages(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            throw new IllegalStateException(result == null ? "查询智能体消息失败" : result.getMessage());
        }
        return R.ok(result.getData() == null ? List.of() : result.getData());
    }

    /** 通过员工本人会话代理历史图片；智能体后台再次校验图片归属。 / Proxy an owned history image. */
    @GetMapping("/conversations/{conversationId}/images/{resourceId}")
    public ResponseEntity<byte[]> historyImage(@PathVariable String conversationId, @PathVariable Long resourceId,
                                               @RequestParam Long agentId) {
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), agentId);
        Result<Map<String, String>> result = historyClient.image(Map.of("openId", user.getOpenId(),
            "agentId", agentId, "conversationId", conversationId, "resourceId", resourceId));
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS || result.getData() == null) {
            throw new IllegalArgumentException("历史图片不可访问");
        }
        String mimeType = result.getData().get("mimeType");
        if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
            throw new IllegalArgumentException("历史图片类型无效");
        }
        byte[] content = java.util.Base64.getDecoder().decode(result.getData().get("content"));
        if (content.length > 5 * 1024 * 1024) throw new IllegalArgumentException("历史图片超过允许大小");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mimeType))
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(content);
    }

    /** Forwards one stream and persists bounded chat/tool diagnostics for administrators. */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody SnailAiChatRequest body) {
        StpUtil.checkLogin();
        OpenApiUserVO user = ensureOpenApiUser();
        ensureAgentAccessible(user.getOpenId(), body.getAgentId());
        ensureConversationBelongsToUser(user.getOpenId(), body.getAgentId(), body.getConversationId());

        LoginUser loginUser = LoginHelper.getLoginUser();
        Long userId = LoginHelper.getUserId();
        String token = StpUtil.getTokenValue();
        HttpServletRequest servletRequest = ServletUtils.getRequest();
        String clientId = servletRequest == null ? null : servletRequest.getHeader(LoginHelper.CLIENT_KEY);
        Object tokenClientId = StpUtil.getExtra(LoginHelper.CLIENT_KEY);
        if (loginUser == null || userId == null || token == null || token.isBlank()
            || clientId == null || !Objects.equals(clientId, tokenClientId)) {
            throw new IllegalStateException("当前登录会话信息不完整，无法安全启动 ERP 智能体");
        }

        List<UploadedAttachment> selectedAttachments = new ArrayList<>();
        List<Map<String, Object>> attachmentSummaries = new ArrayList<>();
        if (body.getAttachmentIds() != null) {
            for (String ticket : body.getAttachmentIds()) {
                UploadedAttachment attachment = attachmentStore.getValid(ticket, userId);
                if (attachment == null) {
                    throw new IllegalArgumentException("附件已过期或不属于当前员工，请重新上传");
                }
                selectedAttachments.add(attachment);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("name", attachment.name());
                summary.put("mimeType", attachment.mimeType());
                summary.put("size", attachment.size());
                summary.put("kind", attachment.isImage() ? "image" : "document");
                if (attachment.isImage()) summary.put("resourceId", String.valueOf(attachment.resourceId()));
                summary.put("extractedChars", attachment.extractedText() == null ? 0 : attachment.extractedText().length());
                attachmentSummaries.add(summary);
            }
        }

        String userContent = body.getContent() == null ? "" : body.getContent().trim();
        if (userContent.isEmpty() && selectedAttachments.isEmpty()) {
            throw new IllegalArgumentException("请输入问题或添加附件");
        }
        StringBuilder modelContent = new StringBuilder(userContent);
        if (modelContent.length() == 0) {
            modelContent.append("请分析我上传的附件。");
        }
        Set<String> permissions = Set.copyOf(StpUtil.getPermissionList());
        String traceId = traceService.start(userId, loginUser.getNickname(), body.getAgentId(),
            body.getConversationId(), modelContent.toString(), attachmentSummaries);
        SnailAiChatSession session;
        try {
            session = sessionStore.open(userId, loginUser.getNickname(), token, clientId, permissions, traceId);
        } catch (RuntimeException exception) {
            traceService.complete(traceId, "", "FAILED", "无法创建智能体会话", 0);
            throw exception;
        }

        OpenApiChatRequest request = new OpenApiChatRequest();
        request.setAgentId(body.getAgentId());
        request.setOpenId(user.getOpenId());
        request.setConversationId(body.getConversationId());
        request.setContent(modelContent.toString());
        if (!selectedAttachments.isEmpty()) {
            List<OpenApiChatAttachmentRequest> attachments = new ArrayList<>();
            for (UploadedAttachment selected : selectedAttachments) {
                if (selected.isImage()) {
                    OpenApiChatAttachmentRequest attachment = new OpenApiChatAttachmentRequest();
                    attachment.setResourceId(selected.resourceId());
                    attachment.setType("IMAGE");
                    attachments.add(attachment);
                }
            }
            if (!attachments.isEmpty()) {
                request.setAttachments(attachments);
            }
        }
        request.setSid(session.getSid());
        request.setDeepPlanEnabled(false);
        request.setWebSearchEnabled(false);

        StringBuilder answerText = new StringBuilder();
        AtomicReference<String> traceError = new AtomicReference<>("");
        AtomicReference<String> traceStatus = new AtomicReference<>("SUCCEEDED");
        long startedAt = System.currentTimeMillis();
        Flux<ServerSentEvent<String>> assistantEvents = chatClient.chatStream(request)
            .doOnNext(event -> {
                if ("text".equalsIgnoreCase(event.getType()) && event.getData() != null
                    && answerText.length() < 100_000) {
                    String delta = extractTextDelta(String.valueOf(event.getData()));
                    int available = 100_000 - answerText.length();
                    answerText.append(delta, 0, Math.min(delta.length(), available));
                }
                if ("error".equalsIgnoreCase(event.getType())) {
                    traceStatus.set("FAILED");
                    traceError.set("SnailAI 返回错误事件");
                }
            })
            .map(event -> toEvent(event.getType(), Map.of(
                "type", event.getType(),
                "data", event.getData() == null ? "" : "text".equalsIgnoreCase(event.getType())
                    ? extractTextDelta(String.valueOf(event.getData())) : event.getData()
            )))
            .onErrorResume(error -> {
                log.warn("SnailAI stream failed for ERP userId={}, agentId={}", userId, body.getAgentId());
                traceStatus.set("FAILED");
                traceError.set("SnailAI 流式调用异常，请核对智能体模型或 MCP 配置");
                return Flux.just(toEvent("error", Map.of(
                    "type", "error",
                    "message", "AI 服务暂时不可用，请检查智能体模型配置后重试。"
                )));
            })
            .doFinally(signal -> {
                if (signal == SignalType.CANCEL && !"FAILED".equals(traceStatus.get())) {
                    traceStatus.set("CANCELLED");
                }
                if (signal != SignalType.CANCEL && answerText.isEmpty() && "SUCCEEDED".equals(traceStatus.get())) {
                    traceStatus.set("FAILED");
                    traceError.set("未收到可显示的模型回复，请查看 SnailAI 日志");
                }
                session.finish();
                traceService.complete(traceId, answerText.toString(), traceStatus.get(), traceError.get(),
                    System.currentTimeMillis() - startedAt);
            });

        Flux<ServerSentEvent<String>> approvalEvents = session.approvals()
            .map(event -> toEvent("approval-required", event));

        return Flux.merge(assistantEvents, approvalEvents)
            .doFinally(signal -> sessionStore.close(session.getSid()));
    }

    /** 用户确认与拒绝均由当前员工本人提交；审批编号为单次、短时、不可猜测值。 */
    @PostMapping("/approvals/resolve")
    public R<Boolean> resolveApproval(@Valid @RequestBody SnailAiApprovalRequest body) {
        StpUtil.checkLogin();
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return R.fail("当前登录员工不存在");
        }
        boolean resolved = sessionStore.resolveApproval(body.getApprovalId(), userId, body.getApproved());
        if (!resolved) {
            return R.fail("该操作确认已过期，或不属于当前员工");
        }
        return R.ok(true);
    }

    private OpenApiUserVO ensureOpenApiUser() {
        StpUtil.checkLogin();
        Long userId = LoginHelper.getUserId();
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || userId == null) {
            throw new IllegalStateException("当前登录用户为空");
        }

        OpenApiUserRegisterRequest registerRequest = new OpenApiUserRegisterRequest();
        registerRequest.setExternalId(String.valueOf(userId));
        registerRequest.setNickname(loginUser.getNickname());
        Result<OpenApiUserVO> registerResult = userClient.register(registerRequest);
        if (registerResult == null || registerResult.getStatus() != SNAIL_AI_SUCCESS
            || registerResult.getData() == null || registerResult.getData().getOpenId() == null) {
            throw new IllegalStateException(registerResult == null
                ? "注册 SnailAI 用户失败" : registerResult.getMessage());
        }
        return registerResult.getData();
    }

    private List<OpenApiAgentVO> listUserAgents(String openId) {
        OpenApiUserQueryRequest request = new OpenApiUserQueryRequest();
        request.setOpenId(openId);
        Result<List<OpenApiAgentVO>> result = agentClient.listUserAgents(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            throw new IllegalStateException(result == null ? "查询可用智能体失败" : result.getMessage());
        }
        return result.getData() == null ? new ArrayList<>() : result.getData();
    }

    private OpenApiAgentVO getAgent(Long agentId) {
        OpenApiAgentIdentityRequest request = new OpenApiAgentIdentityRequest();
        request.setAgentId(agentId);
        Result<OpenApiAgentVO> result = agentClient.getAgent(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            return null;
        }
        return result.getData();
    }

    private void ensureAgentAccessible(String openId, Long agentId) {
        if (agentId == null) {
            throw new IllegalArgumentException("请选择智能体");
        }
        boolean inUserList = listUserAgents(openId).stream()
            .anyMatch(agent -> Objects.equals(agent.getId(), agentId));
        if (inUserList) {
            return;
        }
        if (Objects.equals(agentId, defaultAgentId) && getAgent(agentId) != null) {
            subscribeDefaultAgent(openId, agentId);
            return;
        }
        throw new IllegalArgumentException("当前员工没有使用该智能体的权限");
    }

    /** 为已允许使用默认智能体的员工补齐框架要求的订阅。 / Subscribe the already-authorized default agent. */
    private void subscribeDefaultAgent(String openId, Long agentId) {
        OpenApiUserAgentRequest request = new OpenApiUserAgentRequest();
        request.setOpenId(openId);
        request.setAgentId(agentId);
        Result<Void> result = agentClient.subscribeAgent(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            throw new IllegalStateException("默认智能体订阅失败，请检查智能体发布和应用配置");
        }
    }

    private void ensureConversationBelongsToUser(String openId, Long agentId, String conversationId) {
        OpenApiConversationIdentityRequest request = new OpenApiConversationIdentityRequest();
        request.setAgentId(agentId);
        request.setConversationId(conversationId);
        request.setOpenId(openId);
        Result<List<OpenApiMessageVO>> result = conversationClient.getMessages(request);
        if (result == null || result.getStatus() != SNAIL_AI_SUCCESS) {
            throw new IllegalArgumentException("该对话不存在或不属于当前员工");
        }
    }

    private ServerSentEvent<String> toEvent(String name, Object payload) {
        return ServerSentEvent.<String>builder(org.dromara.common.json.utils.JsonUtils.toJsonString(payload))
            .event(name)
            .build();
    }

    /** SnailAI 的 text 事件 data 可能是 JSON 包装；仅提取已识别事件的正文。 */
    private String extractTextDelta(String data) {
        if (data == null || !data.stripLeading().startsWith("{")) return data == null ? "" : data;
        try {
            Map<?, ?> envelope = org.dromara.common.json.utils.JsonUtils.parseObject(data, Map.class);
            if (envelope != null && "text".equals(envelope.get("type"))
                && envelope.get("content") instanceof String content) return content;
        } catch (RuntimeException ignored) {
            // 非事件 JSON 可能是模型正文，保留原文。 / Preserve JSON that is model text.
        }
        return data;
    }

    private String detectImageMimeType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
            && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        return null;
    }

}
