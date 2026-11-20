/*
 * 文件用途：实现织云内嵌智能体的ChatDocumentValidatorTest服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的ChatDocumentValidatorTest服务能力。
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
