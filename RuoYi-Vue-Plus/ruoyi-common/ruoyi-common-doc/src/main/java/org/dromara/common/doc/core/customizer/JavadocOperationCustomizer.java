package org.dromara.common.doc.core.customizer;

import io.swagger.v3.oas.models.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.dromara.common.doc.core.resolver.JavadocResolver;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Optional;

/**
 * 方法 JavaDoc 与扩展描述增强。
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
public class JavadocOperationCustomizer implements GlobalOperationCustomizer {

    /**
     * JavaDoc 提供器。
     */
    private final Optional<JavadocProvider> javadocProvider;

    /**
     * JavaDoc 扩展解析器列表。
     */
    private final List<JavadocResolver> javadocResolvers;

    /**
     * 自定义接口操作的 JavaDoc 描述。
     *
     * @param operation     OpenAPI 操作对象
     * @param handlerMethod 处理器方法
     * @return 自定义后的 OpenAPI 操作对象
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        javadocProvider.ifPresent(provider -> {
            String description = provider.getMethodJavadocDescription(handlerMethod.getMethod());
            if (StringUtils.isNotBlank(description)) {
                // 接口文档面向中文联调人员；去掉代码注释中以 " / " 分隔的英文对照，保留中文说明。
                operation.setSummary(toChineseApiText(provider.getFirstSentence(description)));
                operation.setDescription(toChineseApiText(description));
            }
        });

        if (CollectionUtils.isEmpty(javadocResolvers)) {
            return operation;
        }

        StringBuilder description = new StringBuilder(Optional.ofNullable(operation.getDescription()).orElse(""));
        List<String> resolvedDescriptions = javadocResolvers.stream()
            .sorted()
            // 只执行支持当前 HandlerMethod 的扩展解析器，避免无注解接口被追加权限说明
            .filter(resolver -> resolver.supports(handlerMethod))
            .map(resolver -> resolver.resolve(handlerMethod, operation))
            .filter(StringUtils::isNotBlank)
            .toList();
        if (!resolvedDescriptions.isEmpty()) {
            // 在原方法 JavaDoc 后追加权限等扩展描述，保持现有 resolver 扩展点
            resolvedDescriptions.forEach(description::append);
            operation.setDescription(description.toString());
        }
        return operation;
    }

    /**
     * 去除每行中文注释后以 " / " 分隔的英文翻译，不改动源码注释本身。
     *
     * @param text JavaDoc 文本
     * @return 供 Swagger 页面展示的中文文本
     */
    private String toChineseApiText(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return java.util.Arrays.stream(text.split("\\R", -1))
            .map(line -> {
                int translationStart = line.indexOf(" / ");
                return translationStart >= 0 ? line.substring(0, translationStart).stripTrailing() : line;
            })
            .collect(java.util.stream.Collectors.joining("\n"))
            .trim();
    }

}
