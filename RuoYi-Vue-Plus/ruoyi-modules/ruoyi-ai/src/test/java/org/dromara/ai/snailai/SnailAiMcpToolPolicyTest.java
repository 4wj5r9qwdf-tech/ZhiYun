/*
 * 文件用途：验证 ERP SnailAI MCP 只读分类不会把写工具错误地标成无需确认。
 * 文件版本：1.0.0
 * 功能目的：防止未知或歧义的 MCP 工具因名称后缀被误判为无需员工确认。
 * 设计思路：直接检查精确只读白名单和默认写操作规则的分类结果。
 * 实现方法：覆盖已知读写工具、已知后缀碰撞和未来未知读式名称。
 * 关键约束：未知或歧义工具应按写操作处理；扩展只读后缀时须确认不会覆盖写工具。
 * 依赖与引用：SnailAiMcpToolPolicy；Docs/AI/织云AI集成总体规划.md
 * 初始开发人员：JY（织云项目自研测试）
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP只读白名单收紧；未知 list/get 名称也必须确认
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（3 项工具分类测试）
 * 测试范围与记录位置：SnailAiMcpToolPolicyTest；Maven Surefire 报告
 */
package org.dromara.ai.snailai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnailAiMcpToolPolicyTest {

    /** 批量替换尺码标签必须按写操作拦截；款式标签查询仍保持只读。 / Treat size tag replacement as a write while preserving style tag reads. */
    @Test
    void tagSuffixCollisionDoesNotBypassWriteApproval() {
        assertFalse(SnailAiMcpToolPolicy.isReadOnlyName("size_batch_set_tags"));
        assertTrue(SnailAiMcpToolPolicy.isReadOnlyName("style_tags"));
    }

    /** MCP 命名空间归一化后仍执行同一只读判定。 / Namespaced tool names retain the same safety classification. */
    @Test
    void normalizedNamesKeepTheirSafetyClassification() {
        assertFalse(SnailAiMcpToolPolicy.isReadOnlyName("erp_size_batch_set_tags"));
        assertTrue(SnailAiMcpToolPolicy.isReadOnlyName("zhiyun_erp_style_tags"));
    }

    /** 新工具不能仅凭 list/get/options 后缀自动跳过确认。 / A new tool must not bypass approval based on a read-like suffix. */
    @Test
    void unknownReadLikeNamesDefaultToWriteApproval() {
        assertFalse(SnailAiMcpToolPolicy.isReadOnlyName("future_feature_list"));
        assertFalse(SnailAiMcpToolPolicy.isReadOnlyName("erp_future_feature_get"));
        assertTrue(SnailAiMcpToolPolicy.isReadOnlyName("erp_color_list"));
    }
}
