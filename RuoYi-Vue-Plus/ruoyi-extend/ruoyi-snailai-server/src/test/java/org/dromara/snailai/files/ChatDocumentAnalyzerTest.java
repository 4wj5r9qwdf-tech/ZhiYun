package org.dromara.snailai.files;

import com.aizuda.snail.ai.features.rag.docling.DoclingClient;
import com.aizuda.snail.ai.features.rag.docling.DoclingConvertOptions;
import com.aizuda.snail.ai.features.rag.docling.DoclingParseResult;
import com.aizuda.snail.ai.features.rag.docling.DoclingResponseParser;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatDocumentAnalyzerTest {
    @Test
    void sendsOriginalOnDemandAndCleansTemporaryFiles() throws Exception {
        var client = mock(DoclingClient.class);
        var parser = mock(DoclingResponseParser.class);
        when(client.isEnabled()).thenReturn(true); when(client.isAvailable()).thenReturn(true);
        var result = new DoclingParseResult(); result.setSuccess(true);
        AtomicReference<Path> temporary = new AtomicReference<>();
        when(client.convert(any(), any(DoclingConvertOptions.class))).thenAnswer(call -> {
            Path file = ((java.io.File) call.getArgument(0)).toPath(); temporary.set(file);
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
            return result;
        });
        when(parser.toCleanMarkdown(result)).thenReturn("订单内容");
        Map<String, Object> data = new ChatDocumentAnalyzer(client, parser)
            .analyze(new ByteArrayInputStream(new byte[]{1, 2, 3}), "docx");
        assertEquals("订单内容", data.get("markdown"));
        assertFalse(Files.exists(temporary.get()));
        assertFalse(Files.exists(temporary.get().getParent()));
    }

    @Test
    void refusesDisabledParserWithoutProcessing() {
        var client = mock(DoclingClient.class);
        var analyzer = new ChatDocumentAnalyzer(client, mock(DoclingResponseParser.class));
        assertThrows(IllegalStateException.class, () -> analyzer.analyze(new ByteArrayInputStream(new byte[]{1}), "pdf"));
        verify(client, never()).convert(any(), any());
    }

    @Test
    void cleansTemporaryFilesWhenConversionFails() throws Exception {
        var client = mock(DoclingClient.class);
        when(client.isEnabled()).thenReturn(true); when(client.isAvailable()).thenReturn(true);
        AtomicReference<Path> temporary = new AtomicReference<>();
        when(client.convert(any(), any(DoclingConvertOptions.class))).thenAnswer(call -> {
            temporary.set(((java.io.File) call.getArgument(0)).toPath());
            throw new IllegalStateException("解析失败");
        });
        var analyzer = new ChatDocumentAnalyzer(client, mock(DoclingResponseParser.class));
        assertThrows(IllegalStateException.class, () -> analyzer.analyze(new ByteArrayInputStream(new byte[]{1}), "pdf"));
        assertFalse(Files.exists(temporary.get().getParent()));
    }
}
