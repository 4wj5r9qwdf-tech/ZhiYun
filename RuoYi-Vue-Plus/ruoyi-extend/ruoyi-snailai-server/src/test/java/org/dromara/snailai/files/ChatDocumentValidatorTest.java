package org.dromara.snailai.files;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ChatDocumentValidatorTest {
    @Test
    void acceptsOriginalTextAndRecognizedOfficeContainer() throws Exception {
        assertEquals("md", ChatDocumentValidator.validate("订单.md", "订单内容".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : new String[]{"[Content_Types].xml", "xl/workbook.xml"}) {
                zip.putNextEntry(new ZipEntry(entry)); zip.write("x".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        assertEquals("xlsx", ChatDocumentValidator.validate("订单.xlsx", out.toByteArray()));
        assertThrows(IllegalArgumentException.class, () -> ChatDocumentValidator.validate("订单.docx", out.toByteArray()));
    }

    @Test
    void rejectsTraversalSpoofedTypesAndInvalidUtf8() {
        assertThrows(IllegalArgumentException.class, () -> ChatDocumentValidator.validate("../订单.txt", new byte[]{65}));
        assertThrows(IllegalArgumentException.class, () -> ChatDocumentValidator.validate("订单.pdf", new byte[]{65, 66, 67, 68, 69}));
        assertThrows(IllegalArgumentException.class, () -> ChatDocumentValidator.validate("订单.md", new byte[]{(byte) 0xff}));
        assertThrows(IllegalArgumentException.class, () -> ChatDocumentValidator.validate("脚本.exe", new byte[]{65}));
    }
}
