/*
 * 文件用途：校验智能体会话上传文件的大小、扩展名、MIME、内容签名和文件名安全。
 * 文件版本：1.0.0
 * 功能目的：校验智能体会话上传文件的大小、扩展名、MIME、内容签名和文件名安全。
 * 设计思路：多层校验共同防止伪装文件与路径注入；校验失败须在入库和转换之前终止。
 * 实现方法：标准化文件元信息，检查类型白名单、大小上限和二进制签名，再返回明确错误。
 * 关键约束：不能只信任浏览器 MIME 或文件后缀；文件名不能被解释为服务器路径。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailai-server/src/main/java/org/dromara/snailai/files/ChatDocumentController.java
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 仅接收允许的原始文档，并限制 Office 容器解压规模。 */
public final class ChatDocumentValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_PDF_BYTES = 50 * 1024 * 1024;

    static int maxBytes(String nameOrType) {
        return nameOrType != null && nameOrType.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? MAX_PDF_BYTES : MAX_BYTES;
    }

    private static final long MAX_EXPANDED_BYTES = 64L * 1024 * 1024;
    private static final Set<String> TEXT = Set.of("txt", "md", "csv");
    private static final Set<String> OFFICE = Set.of("docx", "xlsx", "pptx");

    private ChatDocumentValidator() { }

    /**
     * 校验文件名、大小和内容格式并返回规范化扩展名。
     * 扩展名只用于选择格式解析器，仍须通过相应的文本编码、PDF 魔数或 Office ZIP 结构检查。
     */
    public static String validate(String name, byte[] bytes) {
        // 文件名禁止路径分隔符和控制字符，避免路径穿越、响应头注入及审计显示混淆。
        if (name == null || name.isBlank() || name.length() > 200 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
            || name.chars().anyMatch(Character::isISOControl) || bytes == null || bytes.length == 0 || bytes.length > maxBytes(name)) {
            throw new IllegalArgumentException("文件名无效或文件大小超限（PDF 50 MB，其他文档 10 MB）");
        }
        int dot = name.lastIndexOf('.');
        String ext = dot < 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 文本必须可按 UTF-8 严格解码；仅看文件后缀会接受乱码或伪装内容。
        if (TEXT.contains(ext)) {
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));
            } catch (java.nio.charset.CharacterCodingException exception) {
                throw new IllegalArgumentException("文本文件须使用 UTF-8 编码");
            }
        } else if ("pdf".equals(ext)) {
            // PDF 至少要包含标准文件头标记，不能将任意二进制内容送入 Docling。
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                throw new IllegalArgumentException("PDF 文件格式无效");
            }
        } else if (OFFICE.contains(ext)) {
            // Office 文档是 ZIP 容器，额外检查必要主文件并限制条目数和解压后的总字节数。
            validateOffice(bytes, ext);
        } else {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、XLSX、PPTX、TXT、MD、CSV 文件");
        }
        return ext;
    }

    private static void validateOffice(byte[] bytes, String ext) {
        String required = switch (ext) { case "docx" -> "word/document.xml"; case "xlsx" -> "xl/workbook.xml"; default -> "ppt/presentation.xml"; };
        boolean hasManifest = false, hasMain = false;
        long expanded = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++count > 3000) throw new IllegalArgumentException("文档内容过多");
                if ("[Content_Types].xml".equals(entry.getName())) hasManifest = true;
                if (required.equals(entry.getName())) hasMain = true;
                byte[] buffer = new byte[8192];
                for (int read; (read = zip.read(buffer)) != -1;) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IllegalArgumentException("文档解压后过大");
                }
            }
        } catch (IOException exception) { throw new IllegalArgumentException("Office 文件格式无效", exception); }
        if (!hasManifest || !hasMain) throw new IllegalArgumentException("Office 文件格式与扩展名不匹配");
    }
}
