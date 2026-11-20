/*
 * 文件用途：实现织云内嵌智能体的智能体会话文件分析器服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的智能体会话文件分析器服务能力。
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
package org.dromara.snailai.files;

import com.aizuda.snail.ai.features.rag.docling.DoclingClient;
import com.aizuda.snail.ai.features.rag.docling.DoclingConvertOptions;
import com.aizuda.snail.ai.features.rag.docling.DoclingParseResult;
import com.aizuda.snail.ai.features.rag.docling.DoclingResponseParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按请求解析已有原件；不在上传时自动将文档替换成文本。 */
public class ChatDocumentAnalyzer {
    private static final Set<String> FORMATS = Set.of("pdf", "docx", "xlsx", "pptx", "md");
    private static final int MAX_MARKDOWN_CHARS = 300_000;
    private static final long MAX_IMAGE_CHARS = 8L * 1024 * 1024;
    private final DoclingClient docling;
    private final DoclingResponseParser parser;

    public ChatDocumentAnalyzer(DoclingClient docling, DoclingResponseParser parser) {
        this.docling = docling;
        this.parser = parser;
    }

    /**
     * 按需解析已授权文件的文字、表格和图片元信息；上传时保留原件，不自动转写或替换文件。
     * 结果尺寸受解析后文本、图片数量和图片负载上限约束，超限时要求拆分文档后重试。
     */
    public Map<String, Object> analyze(InputStream input, String type) throws IOException {
        // 在创建临时文件前拒绝未知格式或未配置的解析服务，避免无效输入占用磁盘和外部服务。
        if (!FORMATS.contains(type)) throw new IllegalArgumentException("当前解析器不支持此文件类型");
        if (!docling.isEnabled() || !docling.isAvailable()) throw new IllegalStateException("文档解析服务暂不可用");
        // 使用独立私有临时目录，只将本文件发送至受限的解析容器。
        // Use a private temporary directory and send only this file to the converter.
        Path dir = Files.createTempDirectory("snail-document-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path file = dir.resolve("input." + type);
        try {
            // 复制输入流时最多读取 limit + 1 字节，既能检测超限，也避免信任 MultipartFile 上报的长度。
            int limit = ChatDocumentValidator.maxBytes(file.getFileName().toString());
            long size;
            try (var output = Files.newOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                size = 0;
                while (size <= limit) {
                    int count = input.read(buffer, 0, (int) Math.min(buffer.length, limit + 1 - size));
                    if (count < 0) break;
                    output.write(buffer, 0, count);
                    size += count;
                }
            }
            if (size == 0 || size > limit) throw new IllegalArgumentException("原始文件大小无效");
            // 解析服务返回前只传入刚写入的私有临时文件；成功标记之外的响应一律作为失败处理。
            DoclingParseResult result = docling.convert(file.toFile(), DoclingConvertOptions.defaults());
            if (result == null || !result.isSuccess()) throw new IllegalStateException("文档解析失败");
            String markdown = parser.toCleanMarkdown(result);
            if (markdown == null) markdown = "";
            List<DoclingParseResult.DoclingElement> elements = result.getElements() == null ? List.of() : result.getElements();
            List<DoclingParseResult.DoclingImage> images = result.getImages() == null ? List.of() : result.getImages();
            long imageChars = images.stream().filter(x -> x.getBase64() != null).mapToLong(x -> x.getBase64().length()).sum();
            long elementChars = elements.stream().filter(x -> x.getText() != null).mapToLong(x -> x.getText().length()).sum();
            // 限制文本、元素和 Base64 图片总量，避免大文件解析结果耗尽应用内存或上下文窗口。
            if (markdown.length() > MAX_MARKDOWN_CHARS || elementChars > MAX_MARKDOWN_CHARS
                || images.size() > 100 || imageChars > MAX_IMAGE_CHARS) {
                throw new IllegalArgumentException("解析结果过大，请缩小文档或拆分后重试");
            }
            return Map.of("markdown", markdown, "elements", elements,
                "images", images, "pageCount", result.getPageCount() == null ? 0 : result.getPageCount(),
                "tableCount", result.getTableCount() == null ? 0 : result.getTableCount());
        } finally {
            // 成功和异常路径都删除临时原件，数据库/对象存储中的用户附件不受影响。
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
