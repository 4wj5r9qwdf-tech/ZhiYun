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

    public Map<String, Object> analyze(InputStream input, String type) throws IOException {
        if (!FORMATS.contains(type)) throw new IllegalArgumentException("当前解析器不支持此文件类型");
        if (!docling.isEnabled() || !docling.isAvailable()) throw new IllegalStateException("文档解析服务暂不可用");
        // 使用独立私有临时目录，只将本文件发送至受限的解析容器。
        // Use a private temporary directory and send only this file to the converter.
        Path dir = Files.createTempDirectory("snail-document-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path file = dir.resolve("input." + type);
        try {
            byte[] bytes = input.readNBytes(ChatDocumentValidator.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > ChatDocumentValidator.MAX_BYTES) throw new IllegalArgumentException("原始文件大小无效");
            Files.write(file, bytes);
            DoclingParseResult result = docling.convert(file.toFile(), DoclingConvertOptions.defaults());
            if (result == null || !result.isSuccess()) throw new IllegalStateException("文档解析失败");
            String markdown = parser.toCleanMarkdown(result);
            if (markdown == null) markdown = "";
            List<DoclingParseResult.DoclingElement> elements = result.getElements() == null ? List.of() : result.getElements();
            List<DoclingParseResult.DoclingImage> images = result.getImages() == null ? List.of() : result.getImages();
            long imageChars = images.stream().filter(x -> x.getBase64() != null).mapToLong(x -> x.getBase64().length()).sum();
            long elementChars = elements.stream().filter(x -> x.getText() != null).mapToLong(x -> x.getText().length()).sum();
            if (markdown.length() > MAX_MARKDOWN_CHARS || elementChars > MAX_MARKDOWN_CHARS
                || images.size() > 100 || imageChars > MAX_IMAGE_CHARS) {
                throw new IllegalArgumentException("解析结果过大，请缩小文档或拆分后重试");
            }
            return Map.of("markdown", markdown, "elements", elements,
                "images", images, "pageCount", result.getPageCount() == null ? 0 : result.getPageCount(),
                "tableCount", result.getTableCount() == null ? 0 : result.getTableCount());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
