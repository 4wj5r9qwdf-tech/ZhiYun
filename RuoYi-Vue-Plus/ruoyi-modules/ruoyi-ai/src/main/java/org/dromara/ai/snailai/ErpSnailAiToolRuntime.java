package org.dromara.ai.snailai;

import com.aizuda.snail.ai.agent.core.resolver.BaseToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.ClientRagToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.ClientSkillToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.CustomToolCallbackProvider;
import com.aizuda.snail.ai.agent.core.runtime.tool.ToolRuntime;
import com.aizuda.snail.ai.agent.core.runtime.tool.ToolRuntime.ToolResolution;
import com.aizuda.snail.ai.common.dto.agent.ChatDispatchRequest;
import com.aizuda.snail.ai.common.dto.agent.ChatDispatchRequest.McpServerDescriptor;
import com.aizuda.snail.ai.common.enums.mcp.McpTransportTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds the caller's ERP MCP session and local Skills only to chats opened through the ERP. */
@Slf4j
public class ErpSnailAiToolRuntime extends ToolRuntime {

    private final SnailAiChatSessionStore sessions;
    private final ReadZhiyunSkillTool skillTool;
    private final SnailAiSkillCatalog skills;
    private final String mcpBaseUrl;
    private final String mcpEndpoint;
    private final String documentHost;
    private final String documentPrefix;
    private final String documentKeyFile;

    public ErpSnailAiToolRuntime(ClientSkillToolResolver skillResolver,
                                 ClientRagToolResolver ragResolver,
                                 BaseToolResolver baseToolResolver,
                                 CustomToolCallbackProvider customToolCallbackProvider,
                                 SnailAiChatSessionStore sessions,
                                 ReadZhiyunSkillTool skillTool,
                                 SnailAiSkillCatalog skills,
                                 String mcpBaseUrl,
                                 String mcpEndpoint, String documentHost, String documentPrefix, String documentKeyFile) {
        super(skillResolver, ragResolver, baseToolResolver, customToolCallbackProvider);
        this.sessions = sessions;
        this.skillTool = skillTool;
        this.skills = skills;
        this.mcpBaseUrl = mcpBaseUrl;
        this.mcpEndpoint = mcpEndpoint;
        this.documentHost = documentHost;
        this.documentPrefix = documentPrefix;
        this.documentKeyFile = documentKeyFile;
    }

    @Override
    public ToolResolution resolve(ChatDispatchRequest request) {
        SnailAiChatSession session = sessions.get(request.getSid());
        if (session == null) {
            ToolResolution resolution = super.resolve(request);
            // 上游可返回不可变列表，复制后过滤并保留原 MCP 资源的关闭责任。
            // The upstream list can be immutable; retain the resolver when wrapping it.
            List<ToolCallback> callbacks = new ArrayList<>(resolution.getTools());
            callbacks.removeIf(this::isLocalSkillTool);
            if (request.getSid() != null && request.getSid().startsWith("zydoc_") && request.getServerHttpPort() != null) {
                callbacks.addAll(List.of(ToolCallbacks.from(new ReadChatDocumentTool(request.getSid(), documentHost,
                    request.getServerHttpPort(), documentPrefix, documentKeyFile))));
            }
            return new ToolResolution(callbacks, resolution.getMcpResolver());
        }

        // 上游解析器会原地过滤 MCP 列表，必须传入可修改集合。
        // The upstream resolver filters MCP descriptors in place, so this list must be mutable.
        request.setMcpServers(new ArrayList<>(List.of(buildErpMcpServer(session))));
        request.setSkills(new ArrayList<>());
        String systemPrompt = request.getSystemPrompt();
        request.setSystemPrompt((systemPrompt == null ? "" : systemPrompt) + skills.prompt());

        if (session.getDocumentReader() != null) {
            request.setSystemPrompt(request.getSystemPrompt() + session.getDocumentReader().prompt());
        }
        ToolResolution resolution = super.resolve(request);
        List<ToolCallback> callbacks = new ArrayList<>(resolution.getTools());
        callbacks.removeIf(callback -> isLocalSkillTool(callback)
            || !SnailAiMcpToolPolicy.isAllowed(callback, session.getPermissions()));
        callbacks.add(ToolCallbacks.from(skillTool)[0]);
        if (session.getDocumentReader() != null) {
            callbacks.add(ToolCallbacks.from(session.getDocumentReader())[0]);
        }
        session.recordVisibleTools(callbacks);

        List<ToolCallback> secured = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            boolean requiresApproval = !SnailAiMcpToolPolicy.isReadOnly(callback) && !isLocalSkillTool(callback)
                && !"read_chat_document".equals(callback.getToolDefinition().name());
            secured.add(new ObservedToolCallback(callback, session, sessions, requiresApproval));
        }
        log.debug("ERP SnailAI tools scoped for userId={}, visibleToolCount={}",
            session.getUserId(), secured.size());
        return new ToolResolution(secured, resolution.getMcpResolver());
    }

    private boolean isLocalSkillTool(ToolCallback callback) {
        return "read_zhiyun_skill".equals(
            SnailAiMcpToolPolicy.normalizeName(callback.getToolDefinition().name()));
    }

    private McpServerDescriptor buildErpMcpServer(SnailAiChatSession session) {
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer " + session.getBearerToken(),
            "clientid", session.getClientId()
        );
        return McpServerDescriptor.builder()
            .id(0L)
            .name("zhiyun-erp")
            .transportType(McpTransportTypeEnum.STREAMABLE_HTTP.getValue())
            .baseUri(mcpBaseUrl)
            .endpoint(mcpEndpoint)
            .timeoutMs(30_000L)
            .headers(headers)
            .build();
    }

    private static final class ObservedToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final SnailAiChatSession session;
        private final SnailAiChatSessionStore sessions;
        private final ToolDefinition definition;
        private final boolean requiresApproval;

        private ObservedToolCallback(ToolCallback delegate, SnailAiChatSession session,
                                     SnailAiChatSessionStore sessions, boolean requiresApproval) {
            this.delegate = delegate;
            this.session = session;
            this.sessions = sessions;
            this.definition = delegate.getToolDefinition();
            this.requiresApproval = requiresApproval;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return execute(toolInput, null, false);
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return execute(toolInput, toolContext, true);
        }

        private String execute(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext,
                                boolean hasContext) {
            String name = SnailAiMcpToolPolicy.normalizeName(definition.name());
            SnailAiTraceService.ToolTraceHandle trace = session.startTool(name, definition.description(), toolInput);
            if (requiresApproval && !sessions.awaitApproval(session, name, definition.description(), toolInput)) {
                String message = "员工未确认或确认超时，操作未执行。";
                session.finishTool(trace, "NOT_APPROVED", "", message);
                return "{\"status\":\"cancelled\",\"message\":\"员工未确认该操作；操作未执行。\"}";
            }
            try {
                String result = hasContext ? delegate.call(toolInput, toolContext) : delegate.call(toolInput);
                session.finishTool(trace, "SUCCEEDED", result, "");
                return result;
            } catch (RuntimeException exception) {
                session.finishTool(trace, "FAILED", "", "工具执行异常：" + exception.getClass().getSimpleName());
                throw exception;
            }
        }
    }
}
