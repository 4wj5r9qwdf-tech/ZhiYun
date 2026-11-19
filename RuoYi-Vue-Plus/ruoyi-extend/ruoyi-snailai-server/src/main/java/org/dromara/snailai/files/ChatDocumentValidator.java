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

    public static String validate(String name, byte[] bytes) {
        if (name == null || name.isBlank() || name.length() > 200 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
            || name.chars().anyMatch(Character::isISOControl) || bytes == null || bytes.length == 0 || bytes.length > maxBytes(name)) {
            throw new IllegalArgumentException("文件名无效或文件大小超限（PDF 50 MB，其他文档 10 MB）");
        }
        int dot = name.lastIndexOf('.');
        String ext = dot < 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (TEXT.contains(ext)) {
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));
            } catch (java.nio.charset.CharacterCodingException exception) {
                throw new IllegalArgumentException("文本文件须使用 UTF-8 编码");
            }
        } else if ("pdf".equals(ext)) {
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                throw new IllegalArgumentException("PDF 文件格式无效");
            }
        } else if (OFFICE.contains(ext)) {
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
