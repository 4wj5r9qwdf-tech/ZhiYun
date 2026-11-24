/*
 * 文件用途：验证物料档案兼容字段的更新省略语义。
 * 文件版本：1.0.0
 * 功能目的：避免模板规格字段改动时无意清空历史自由文本规格。
 * 设计思路：直接验证提取后的字段归一化规则，不依赖数据库和外部服务。
 * 实现方法：覆盖更新省略、显式清空、显式新值和新增省略四种输入。
 * 关键约束：显式 null/空串可清空；字段省略才代表保留已有值。
 * 依赖与引用：MaterialController.java；Docs/API/物料档案API.md
 * 初始开发人员：Codex（智能体）
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-物料契约空值修复；覆盖旧规格兼容字段省略与清空
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（1 项字段归一化测试）
 * 测试范围与记录位置：Maven Surefire 报告
 */
package org.dromara.web.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialControllerTest {

    @Test
    void legacySpecificationDistinguishesOmittedFromExplicitBlank() {
        assertEquals("旧自由文本尺寸",
            MaterialController.legacySpecification(false, null, "旧自由文本尺寸", false));
        assertEquals("",
            MaterialController.legacySpecification(true, null, "旧自由文本尺寸", false));
        assertEquals("",
            MaterialController.legacySpecification(true, "   ", "旧自由文本尺寸", false));
        assertEquals("新版尺寸",
            MaterialController.legacySpecification(true, "  新版尺寸  ", "旧自由文本尺寸", false));
        assertEquals("",
            MaterialController.legacySpecification(false, null, null, true));
    }
}
