/*
 * 文件用途：验证款式控制器的生产模式与单款规格输入边界。
 * 文件版本：1.0.0
 * 功能目的：阻止套装请求携带会被忽略的颜色、尺码或排除组合数据。
 * 设计思路：直接检查模式校验规则，不连接数据库或写入款式记录。
 * 实现方法：验证单款允许完整规格，套装允许省略规格但拒绝任何非 null 单款规格集合。
 * 关键约束：完整写入契约不得静默忽略调用方提交的字段。
 * 依赖与引用：StyleController.java；Docs/API/款式档案API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-款式模式字段契约；覆盖套装拒绝单款规格字段
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（2 项生产模式契约测试）
 * 测试范围与记录位置：StyleControllerContractTest；Maven Surefire 报告
 */
package org.dromara.web.controller;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StyleControllerContractTest {

    @Test
    void singleStyleCanSubmitSpecificationCollections() {
        assertDoesNotThrow(() -> StyleController.validateStyleSpecificationMode(
            "single", List.of(20001L), List.of(30001L), List.of()));
    }

    @Test
    void setStyleMustOmitSingleStyleSpecificationCollections() {
        assertDoesNotThrow(() -> StyleController.validateStyleSpecificationMode("combined", null, null, null));
        assertThrows(ServiceException.class, () -> StyleController.validateStyleSpecificationMode(
            "combined", List.of(), List.of(), List.of()));
        assertThrows(ServiceException.class, () -> StyleController.validateStyleSpecificationMode(
            "separate", null, null, List.of()));
    }
}
