package org.dromara.ai.config;

import com.aizuda.snail.ai.agent.core.resolver.BaseToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.ClientRagToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.ClientSkillToolResolver;
import com.aizuda.snail.ai.agent.core.resolver.CustomToolCallbackProvider;
import com.aizuda.snail.ai.agent.core.runtime.tool.ToolRuntime;
import com.aizuda.snail.ai.agent.core.executor.prompt.PromptFactory;
import org.dromara.ai.snailai.ErpSnailAiToolRuntime;
import org.dromara.ai.snailai.ReadZhiyunSkillTool;
import org.dromara.ai.snailai.SnailAiChatSessionStore;
import org.dromara.ai.snailai.SnailAiResourceClient;
import org.dromara.ai.snailai.SnailAiHistoryClient;
import org.dromara.ai.snailai.SnailAiSkillCatalog;
import org.dromara.ai.snailai.SpringAiCompatiblePromptFactory;
import com.aizuda.snail.ai.agent.common.config.SnailAiAgentProperties;
import com.aizuda.snail.ai.openapi.client.core.config.SnailAiOpenApiProperties;
import com.aizuda.snail.ai.openapi.client.core.rpc.OpenApiHttpInvokeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;

/** Activates employee-scoped ERP MCP and local Skill support for the SnailAI client. */
@Configuration
@ConditionalOnProperty(prefix = "snail-ai", name = "enabled", havingValue = "true")
public class SnailAiErpIntegrationConfig {

    /** 适配当前 Spring AI 图片接口。 / Adapt SnailAI image messages to the current Spring AI API. */
    @Bean
    @Primary
    public PromptFactory erpCompatiblePromptFactory() {
        return new SpringAiCompatiblePromptFactory();
    }

    @Bean
    public SnailAiResourceClient snailAiResourceClient(HttpClient client,
                                                        SnailAiOpenApiProperties openApiProperties,
                                                        SnailAiAgentProperties agentProperties) {
        return (SnailAiResourceClient) Proxy.newProxyInstance(
            SnailAiResourceClient.class.getClassLoader(),
            new Class<?>[]{SnailAiResourceClient.class},
            new OpenApiHttpInvokeHandler(client, openApiProperties, agentProperties));
    }

    @Bean
    public SnailAiHistoryClient snailAiHistoryClient(HttpClient client,
            SnailAiOpenApiProperties openApiProperties, SnailAiAgentProperties agentProperties) {
        return (SnailAiHistoryClient) Proxy.newProxyInstance(SnailAiHistoryClient.class.getClassLoader(),
            new Class<?>[]{SnailAiHistoryClient.class}, new OpenApiHttpInvokeHandler(client, openApiProperties, agentProperties));
    }

    @Bean
    @Primary
    public ToolRuntime erpSnailAiToolRuntime(
        ClientSkillToolResolver skillResolver,
        ClientRagToolResolver ragResolver,
        BaseToolResolver baseToolResolver,
        CustomToolCallbackProvider customToolCallbackProvider,
        SnailAiChatSessionStore sessions,
        ReadZhiyunSkillTool skillTool,
        SnailAiSkillCatalog skills,
        @Value("${snail-ai.erp.mcp-base-url:}") String configuredMcpBaseUrl,
        @Value("${snail-ai.erp.mcp-endpoint:/mcp}") String mcpEndpoint,
        @Value("${snail-ai.server.host:127.0.0.1}") String documentHost,
        @Value("${snail-ai.open-api.prefix:snail-ai}") String documentPrefix,
        @Value("${snail-ai.document-bridge.key-file:${user.home}/.config/zhiyun/snail-document-bridge.key}") String documentKeyFile,
        @Value("${server.port:8080}") int serverPort,
        @Value("${server.servlet.context-path:}") String contextPath
    ) {
        String baseUrl = StringUtils.hasText(configuredMcpBaseUrl)
            ? configuredMcpBaseUrl.trim()
            : "http://127.0.0.1:" + serverPort + (contextPath == null ? "" : contextPath.trim());
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = mcpEndpoint.startsWith("/") ? mcpEndpoint : "/" + mcpEndpoint;
        return new ErpSnailAiToolRuntime(skillResolver, ragResolver, baseToolResolver,
            customToolCallbackProvider, sessions, skillTool, skills, baseUrl, endpoint, documentHost, documentPrefix, documentKeyFile);
    }
}
