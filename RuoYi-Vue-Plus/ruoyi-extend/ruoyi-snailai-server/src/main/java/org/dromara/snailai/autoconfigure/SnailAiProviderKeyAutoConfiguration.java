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

    @Bean
    public KnowledgeDraftController knowledgeDraftController(JdbcTemplate jdbc, RagDocumentService documents) {
        return new KnowledgeDraftController(jdbc, documents);
    }

    @Bean
    public ChatDocumentController chatDocumentController(JdbcTemplate jdbc, ResourceService resources, ChatDocumentAnalyzer analyzer) {
        return new ChatDocumentController(jdbc, resources, analyzer);
    }

    @Bean
    public ChatDocumentToolBridge chatDocumentToolBridge(JdbcTemplate jdbc, ChatDocumentController documents, KnowledgeDraftController knowledge,
            @Value("${snail-ai.document-bridge.key-file:${user.home}/.config/zhiyun/snail-document-bridge.key}") String keyFile) {
        return new ChatDocumentToolBridge(jdbc, documents, Path.of(keyFile), knowledge);
    }

    @Bean
    public ChatDocumentAnalyzer chatDocumentAnalyzer(DoclingClient docling, DoclingResponseParser parser) {
        return new ChatDocumentAnalyzer(docling, parser);
    }

    @Bean
    public ChatDocumentExceptionHandler chatDocumentExceptionHandler() {
        return new ChatDocumentExceptionHandler();
    }

    /** 使用框架客户端解析，密钥仅用于受限的 Docling 请求。 */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "snail-ai.rag.docling", name = "api-key-file")
    public DoclingClient authenticatedDoclingClient(DoclingProperties properties, DoclingResponseParser parser,
            @Value("${snail-ai.rag.docling.api-key-file}") String keyFile) {
        return new AuthenticatedDoclingClient(properties, parser, Path.of(keyFile));
    }

    @Bean
    public EmployeeChatHistoryController employeeChatHistoryController(JdbcTemplate jdbc,
            OpenApiUserResolver users, ResourceService resources) {
        return new EmployeeChatHistoryController(jdbc, users, resources);
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ApplicationRunner modelProviderKeyMigration(JdbcTemplate jdbcTemplate) {
        return new ModelProviderKeyMigration(jdbcTemplate);
    }
}
