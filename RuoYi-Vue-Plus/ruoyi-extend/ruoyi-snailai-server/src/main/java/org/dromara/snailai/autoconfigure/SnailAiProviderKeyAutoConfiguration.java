/*
 * 文件用途：实现织云内嵌智能体的SnailAiProviderKeyAutoConfiguration服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的SnailAiProviderKeyAutoConfiguration服务能力。
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
package org.dromara.snailai.autoconfigure;

import org.dromara.snailai.migration.ModelProviderKeyMigration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aizuda.snail.ai.features.resource.ResourceService;
import com.aizuda.snail.ai.openapi.service.OpenApiUserResolver;
import org.dromara.snailai.history.EmployeeChatHistoryController;
import org.dromara.snailai.docling.AuthenticatedDoclingClient;
import com.aizuda.snail.ai.features.rag.docling.DoclingClient;
import com.aizuda.snail.ai.features.rag.docling.DoclingProperties;
import com.aizuda.snail.ai.features.rag.docling.DoclingResponseParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import java.nio.file.Path;
import org.dromara.snailai.files.ChatDocumentController;
import org.dromara.snailai.files.ChatDocumentExceptionHandler;
import org.dromara.snailai.files.ChatDocumentAnalyzer;
import org.dromara.snailai.files.ChatDocumentToolBridge;
import org.dromara.snailai.knowledge.KnowledgeDraftController;
import com.aizuda.snail.ai.admin.service.rag.RagDocumentService;

@AutoConfiguration
public class SnailAiProviderKeyAutoConfiguration {

    /** 注册待确认知识草稿服务，供文件工具提出候选内容及后台人工确认。 */
    @Bean
    public KnowledgeDraftController knowledgeDraftController(JdbcTemplate jdbc, RagDocumentService documents) {
        return new KnowledgeDraftController(jdbc, documents);
    }

    /** 注册带 ERP 员工会话归属校验的聊天文件上传、查询、下载和解析接口。 */
    @Bean
    public ChatDocumentController chatDocumentController(JdbcTemplate jdbc, ResourceService resources, ChatDocumentAnalyzer analyzer) {
        return new ChatDocumentController(jdbc, resources, analyzer);
    }

    /** 注册原生智能体与 ERP 文件/知识草稿工具之间的签名桥接服务。 */
    @Bean
    public ChatDocumentToolBridge chatDocumentToolBridge(JdbcTemplate jdbc, ChatDocumentController documents, KnowledgeDraftController knowledge,
            @Value("${snail-ai.document-bridge.key-file:${user.home}/.config/zhiyun/snail-document-bridge.key}") String keyFile) {
        return new ChatDocumentToolBridge(jdbc, documents, Path.of(keyFile), knowledge);
    }

    /** 注入框架 Docling 客户端及响应解析器，负责按需处理原始附件。 */
    @Bean
    public ChatDocumentAnalyzer chatDocumentAnalyzer(DoclingClient docling, DoclingResponseParser parser) {
        return new ChatDocumentAnalyzer(docling, parser);
    }

    /** 将文件接口的受控参数异常映射到稳定 HTTP 错误响应。 */
    @Bean
    public ChatDocumentExceptionHandler chatDocumentExceptionHandler() {
        return new ChatDocumentExceptionHandler();
    }

    /** 使用框架客户端解析，密钥仅用于受限的 Docling 请求。 */
    /** 优先使用仅对已校验 Docling 源站请求加认证头的客户端实现。 */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "snail-ai.rag.docling", name = "api-key-file")
    public DoclingClient authenticatedDoclingClient(DoclingProperties properties, DoclingResponseParser parser,
            @Value("${snail-ai.rag.docling.api-key-file}") String keyFile) {
        return new AuthenticatedDoclingClient(properties, parser, Path.of(keyFile));
    }

    /** 注册 ERP 员工查看本人智能体会话历史和附件的接口。 */
    @Bean
    public EmployeeChatHistoryController employeeChatHistoryController(JdbcTemplate jdbc,
            OpenApiUserResolver users, ResourceService resources) {
        return new EmployeeChatHistoryController(jdbc, users, resources);
    }

    /** 在启动期间执行兼容旧 provider_key 字段宽度的数据库类型迁移。 */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ApplicationRunner modelProviderKeyMigration(JdbcTemplate jdbcTemplate) {
        return new ModelProviderKeyMigration(jdbcTemplate);
    }
}
