/*
 * 文件用途：承载织云 ERP 对上游框架“JavadocOperationCustomizer”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“JavadocOperationCustomizer”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
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
