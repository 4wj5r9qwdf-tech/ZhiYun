/*
 * 文件用途：为从 ERP 发起的 SnailAI 对话装配当前员工 MCP、项目 Skills、文件工具和工具执行确认。
 * 文件版本：1.0.0
 * 功能目的：为从 ERP 发起的 SnailAI 对话装配当前员工 MCP、项目 Skills、文件工具和工具执行确认。
 * 设计思路：原生 SnailAI 会话与 ERP 会话分流；ERP 工具集按员工权限过滤，写操作先记录诊断并等待本人确认。
 * 实现方法：解析会话上下文，构建员工 Bearer MCP 描述，筛选工具回调，包装写工具并记录执行结果。
 * 关键约束：不得复用上游原生会话身份；权限过滤须在工具发现和实际调用路径生效；未确认的写工具不执行。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/AI/项目上下文索引.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiMcpToolPolicy.java
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
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

    /**
     * 根据 SID 区分原生后台会话与 ERP 员工会话，并构造各自允许使用的工具目录。
     * ERP 会话的 MCP 凭据和权限来自服务端会话对象，不从模型请求或提示词恢复。
     */
    @Override
    public ToolResolution resolve(ChatDispatchRequest request) {
        SnailAiChatSession session = sessions.get(request.getSid());
        if (session == null) {
            // 原生后台仍使用上游工具解析；移除 ERP 专用 Skill，文件工具仅在本轮已有签名授权时附加。
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
        // ERP 会话只连接当前员工授权过的 ERP MCP 入口，并清空上游 Skill 目录以免混入非项目技能。
        request.setMcpServers(new ArrayList<>(List.of(buildErpMcpServer(session))));
        request.setSkills(new ArrayList<>());
        String systemPrompt = request.getSystemPrompt();
        request.setSystemPrompt((systemPrompt == null ? "" : systemPrompt) + skills.prompt());

        if (session.getDocumentReader() != null) {
            request.setSystemPrompt(request.getSystemPrompt() + session.getDocumentReader().prompt());
        }
        ToolResolution resolution = super.resolve(request);
        List<ToolCallback> callbacks = new ArrayList<>(resolution.getTools());
        // MCP 工具权限在服务端会话中再次过滤；上游 MCP 提供了工具不代表当前员工可调用。
        callbacks.removeIf(callback -> isLocalSkillTool(callback)
            || !SnailAiMcpToolPolicy.isAllowed(callback, session.getPermissions()));
        callbacks.add(ToolCallbacks.from(skillTool)[0]);
        if (session.getDocumentReader() != null) {
            callbacks.add(ToolCallbacks.from(session.getDocumentReader())[0]);
        }
        session.recordVisibleTools(callbacks);

        // 记录过滤后的可见目录供页面审批与诊断使用，再为写工具套上确认和审计代理。
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

        /** 先记录工具调用；写操作必须等当前员工确认，只有确认后才调用上游执行器。 */
        private String execute(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext,
                                boolean hasContext) {
            String name = SnailAiMcpToolPolicy.normalizeName(definition.name());
            SnailAiTraceService.ToolTraceHandle trace = session.startTool(name, definition.description(), toolInput);
            // 超时、拒绝、会话关闭和其他非确认结果一律失败关闭，不调用 delegate。
            if (requiresApproval && !sessions.awaitApproval(session, name, definition.description(), toolInput)) {
                String message = "员工未确认或确认超时，操作未执行。";
                session.finishTool(trace, "NOT_APPROVED", "", message);
                return "{\"status\":\"cancelled\",\"message\":\"员工未确认该操作；操作未执行。\"}";
            }
            try {
                // 保留 Spring AI 的 ToolContext 调用重载，避免权限代理改变上游工具的上下文语义。
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
