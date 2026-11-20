/*
 * 文件用途：将 SnailAI 管理端接入织云 ERP 的员工身份、MCP 工具、文件处理、对话记录和审计服务。
 * 文件版本：1.0.0
 * 功能目的：将 SnailAI 管理端接入织云 ERP 的员工身份、MCP 工具、文件处理、对话记录和审计服务。
 * 设计思路：沿用上游管理端与 Agent 执行结构，在 ERP 调用链显式注入员工会话和受控工具运行时。
 * 实现方法：配置 ERP OpenAPI、工具运行时、会话存储和文件/历史客户端等 Spring Bean。
 * 关键约束：框架原生后台仍走原上游路径；ERP 接入使用员工身份和服务端权限，不共享可伪造身份。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/规范/开发规范.md；RuoYi-Vue-Plus/ruoyi-admin/src/main/java/org/dromara/web/mcp/
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
