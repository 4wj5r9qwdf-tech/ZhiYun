/*
 * 文件用途：承载织云 ERP 对上游框架“ClassTagOperationCustomizer”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“ClassTagOperationCustomizer”能力的项目扩展或适配。
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

import cn.hutool.core.io.IoUtil;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller 类级标签增强。
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
public class ClassTagOperationCustomizer implements GlobalOperationCustomizer, GlobalOpenApiCustomizer {

    /**
     * JavaDoc 提供器。
     */
    private final Optional<JavadocProvider> javadocProvider;

    /**
     * SpringDoc 属性解析工具。
     */
    private final PropertyResolverUtils propertyResolverUtils;

    /**
     * 织云业务控制器在 Swagger 中使用的简短中文名称。
     */
    private static final Map<String, String> BUSINESS_TAG_NAMES = Map.ofEntries(
        Map.entry("ApprovalBindingController", "审批对象配置"),
        Map.entry("AuthController", "账号认证"),
        Map.entry("BuildingController", "建筑档案"),
        Map.entry("CaptchaController", "验证码"),
        Map.entry("CategoryController", "分类管理"),
        Map.entry("ColorController", "颜色管理"),
        Map.entry("CompanyController", "公司档案"),
        Map.entry("CountryRegionController", "国家地区"),
        Map.entry("CustomerController", "客户档案"),
        Map.entry("CustomerOrderApprovalController", "客户订单审批"),
        Map.entry("CustomerOrderController", "客户订单"),
        Map.entry("HelloController", "服务连通性"),
        Map.entry("IndexController", "工作台"),
        Map.entry("MaterialController", "物料档案"),
        Map.entry("OrderApprovalSetupController", "客户订单审批设置"),
        Map.entry("PersonController", "人员管理"),
        Map.entry("PrivateFileController", "私有文件"),
        Map.entry("ProfileController", "个人中心"),
        Map.entry("PublicInfoController", "登录页公共信息"),
        Map.entry("SizeDataController", "尺码资料"),
        Map.entry("StyleBomController", "款式 BOM"),
        Map.entry("StyleController", "款式档案"),
        Map.entry("TagController", "标签管理"),
        Map.entry("UnitController", "计量单位"),
        Map.entry("WarehouseController", "仓库管理"),
        Map.entry("WarehouseLocationController", "仓库布局")
    );

    /**
     * 已解析的 OpenAPI 顶层标签缓存。
     */
    private final Map<String, Tag> tags = new ConcurrentHashMap<>();

    /**
     * 自定义接口操作的标签信息。
     *
     * @param operation     OpenAPI 操作对象
     * @param handlerMethod 处理器方法
     * @return 自定义后的 OpenAPI 操作对象
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Class<?> beanType = handlerMethod.getBeanType();
        List<io.swagger.v3.oas.annotations.tags.Tag> classTags = getClassTags(beanType);
        if (!CollectionUtils.isEmpty(classTags)) {
            // 优先使用 Controller 类上的 @Tag / @Tags，保持 Swagger 原生注解语义
            addAnnotationTags(operation, classTags);
            return operation;
        }

        String tagName = getClassJavadocTagName(beanType);
        if (StringUtils.isBlank(tagName)) {
            return operation;
        }

        String autoTagName = OpenAPIService.splitCamelCase(beanType.getSimpleName());
        if (!shouldUseClassJavadocTag(operation, autoTagName)) {
            return operation;
        }

        // 无显式 @Tag 时，将 springdoc 自动生成的类名 tag 替换为稳定中文短标题。
        removeOperationTag(operation, autoTagName);
        addOperationTag(operation, tagName);
        // 标签名使用稳定短名称；接口详情由方法注释承载，避免把整段类注释塞进标签栏。
        tags.putIfAbsent(tagName, new Tag().name(tagName));
        return operation;
    }

    /**
     * 自定义 OpenAPI 顶层标签集合。
     *
     * @param openApi OpenAPI 文档对象
     */
    @Override
    public void customise(OpenAPI openApi) {
        // GroupedOpenApi 共用同一组 customizer 实例；仅保留当前分组实际引用的标签，避免其他模块留下空标签。
        Set<String> usedTagNames = new HashSet<>();
        if (openApi.getPaths() != null) {
            for (PathItem pathItem : openApi.getPaths().values()) {
                if (pathItem == null) {
                    continue;
                }
                for (Operation operation : pathItem.readOperations()) {
                    if (operation != null && !CollectionUtils.isEmpty(operation.getTags())) {
                        usedTagNames.addAll(operation.getTags());
                    }
                }
            }
        }
        if (!CollectionUtils.isEmpty(openApi.getTags())) {
            openApi.getTags().removeIf(tag -> !usedTagNames.contains(tag.getName()));
        }
        // 只把当前分组实际使用的类级标签描述补充到 OpenAPI 顶层。
        tags.values().forEach(tag -> {
            if (usedTagNames.contains(tag.getName())
                && (openApi.getTags() == null || openApi.getTags().stream().noneMatch(item -> Objects.equals(item.getName(), tag.getName())))) {
                openApi.addTagsItem(tag);
            }
        });
    }

    /**
     * 获取 Controller 类上的 Swagger 标签注解。
     *
     * @param beanType Controller 类型
     * @return 标签注解列表
     */
    private List<io.swagger.v3.oas.annotations.tags.Tag> getClassTags(Class<?> beanType) {
        Set<Tags> tagsSet = AnnotatedElementUtils.findAllMergedAnnotations(beanType, Tags.class);
        Set<io.swagger.v3.oas.annotations.tags.Tag> mergedTags = tagsSet.stream()
            .flatMap(item -> Stream.of(item.value()))
            .collect(Collectors.toSet());
        mergedTags.addAll(AnnotatedElementUtils.findAllMergedAnnotations(beanType, io.swagger.v3.oas.annotations.tags.Tag.class));
        return new ArrayList<>(mergedTags);
    }

    /**
     * 将类级 Swagger 标签追加到接口操作与顶层标签缓存。
     *
     * @param operation OpenAPI 操作对象
     * @param classTags 类级标签注解
     */
    private void addAnnotationTags(Operation operation, List<io.swagger.v3.oas.annotations.tags.Tag> classTags) {
        classTags.stream()
            .map(io.swagger.v3.oas.annotations.tags.Tag::name)
            .map(name -> propertyResolverUtils.resolve(name, Locale.getDefault()))
            .filter(StringUtils::isNotBlank)
            .forEach(name -> addOperationTag(operation, name));

        AnnotationsUtils.getTags(classTags.toArray(new io.swagger.v3.oas.annotations.tags.Tag[0]), true)
            .ifPresent(items -> items.forEach(tag -> {
                tag.name(propertyResolverUtils.resolve(tag.getName(), Locale.getDefault()));
                tag.description(propertyResolverUtils.resolve(tag.getDescription(), Locale.getDefault()));
                if (StringUtils.isNotBlank(tag.getName())) {
                    tags.putIfAbsent(tag.getName(), tag);
                }
            }));
    }

    /**
     * 读取织云业务短标签或从类 JavaDoc 中提取中文短标签。
     *
     * @param beanType Controller 类型
     * @return 标签名称，未配置 JavaDoc 时返回 null
     */
    private String getClassJavadocTagName(Class<?> beanType) {
        String businessTagName = BUSINESS_TAG_NAMES.get(beanType.getSimpleName());
        if (StringUtils.isNotBlank(businessTagName)) {
            return businessTagName;
        }
        if (javadocProvider.isEmpty()) {
            return null;
        }
        String description = javadocProvider.get().getClassJavadoc(beanType);
        if (StringUtils.isBlank(description)) {
            return null;
        }
        // 类注释中 "中文 / English" 的格式用于代码维护，Swagger 标签只保留中文短标题。
        List<String> lines = IoUtil.readLines(new StringReader(description), new ArrayList<>());
        String firstLine = lines.stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
        return normalizeTagName(firstLine, beanType);
    }

    /**
     * 清理 JavaDoc 标签标题，避免将描述、双语翻译和类名后缀挤进 Swagger 标签栏。
     *
     * @param firstLine 类注释首个非空行
     * @param beanType 控制器类型
     * @return 可读的短标签
     */
    private String normalizeTagName(String firstLine, Class<?> beanType) {
        if (StringUtils.isBlank(firstLine)) {
            return null;
        }
        String name = firstLine.trim();
        int translationStart = name.indexOf(" / ");
        if (translationStart >= 0) {
            name = name.substring(0, translationStart).trim();
        }
        name = name.replaceAll("[。；;，,：:\\s]+$", "");
        int boundary = -1;
        for (char separator : new char[]{'（', '(', '，', ',', '；', ';', '：', ':'}) {
            int index = name.indexOf(separator);
            if (index > 0 && (boundary < 0 || index < boundary)) {
                boundary = index;
            }
        }
        if (boundary > 0) {
            name = name.substring(0, boundary).trim();
        }
        if (StringUtils.isBlank(name)) {
            return OpenAPIService.splitCamelCase(beanType.getSimpleName());
        }
        return name;
    }

    /**
     * 判断当前操作是否可以使用类 JavaDoc 标签替换默认标签。
     *
     * @param operation   OpenAPI 操作对象
     * @param autoTagName SpringDoc 自动生成的标签名
     * @return true 可以替换 false 不替换
     */
    private boolean shouldUseClassJavadocTag(Operation operation, String autoTagName) {
        return CollectionUtils.isEmpty(operation.getTags()) || operation.getTags().contains(autoTagName);
    }

    /**
     * 为接口操作追加标签。
     *
     * @param operation OpenAPI 操作对象
     * @param tagName   标签名称
     */
    private void addOperationTag(Operation operation, String tagName) {
        if (operation.getTags() == null) {
            operation.setTags(new ArrayList<>());
        }
        if (!operation.getTags().contains(tagName)) {
            operation.addTagsItem(tagName);
        }
    }

    /**
     * 从接口操作中移除指定标签。
     *
     * @param operation OpenAPI 操作对象
     * @param tagName   标签名称
     */
    private void removeOperationTag(Operation operation, String tagName) {
        if (!CollectionUtils.isEmpty(operation.getTags())) {
            operation.getTags().removeIf(item -> Objects.equals(item, tagName));
        }
    }

}
