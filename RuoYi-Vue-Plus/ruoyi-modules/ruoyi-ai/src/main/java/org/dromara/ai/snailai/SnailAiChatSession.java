package org.dromara.ai.snailai;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Short-lived identity context for one ERP chat turn. / Holds identity only while one chat turn runs. */
@Getter
public class SnailAiChatSession {

    private final String sid;
    private final Long userId;
    private final String nickname;
    private final String bearerToken;
    private final String clientId;
    private final String traceId;
    private final SnailAiTraceService traceService;
    private final Set<String> permissions;
    private final Instant expiresAt;
    private final Sinks.Many<Map<String, Object>> approvalSink = Sinks.many().replay().limit(16);

    public SnailAiChatSession(String sid, Long userId, String nickname, String bearerToken,
                              String clientId, Set<String> permissions, Instant expiresAt,
                              String traceId, SnailAiTraceService traceService) {
        this.sid = sid;
        this.userId = userId;
        this.nickname = nickname;
        this.bearerToken = bearerToken;
        this.clientId = clientId;
        this.traceId = traceId;
        this.traceService = traceService;
        this.permissions = Set.copyOf(permissions);
        this.expiresAt = expiresAt;
    }

    public Flux<Map<String, Object>> approvals() {
        return approvalSink.asFlux();
    }

    public void recordVisibleTools(List<ToolCallback> callbacks) {
        List<Map<String, Object>> tools = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", callback.getToolDefinition().name());
            item.put("description", callback.getToolDefinition().description());
            item.put("readOnly", SnailAiMcpToolPolicy.isReadOnly(callback));
            tools.add(item);
        }
        traceService.updateVisibleTools(traceId, tools);
    }

    public SnailAiTraceService.ToolTraceHandle startTool(String toolName, String description, String arguments) {
        return traceService.startTool(traceId, toolName, description, arguments);
    }

    public void finishTool(SnailAiTraceService.ToolTraceHandle handle, String status, String result, String error) {
        traceService.finishTool(handle, status, result, error);
    }

    void publishApproval(Map<String, Object> event) {
        approvalSink.tryEmitNext(event);
    }

    public void finish() {
        approvalSink.tryEmitComplete();
    }
}
