/*
 * 文件用途：实现织云内嵌智能体的ChatDocumentAnalyzerTest服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的ChatDocumentAnalyzerTest服务能力。
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
