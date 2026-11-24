/*
 * 文件用途：验证公共计量换算服务的数量精度边界。
 * 文件版本：1.0.0
 * 功能目的：防止换算数量将“整数位上限”和“小数位上限”误合并成总有效数字上限。
 * 设计思路：直接测试不依赖数据库的精度校验器，覆盖最大合法值及相邻非法边界。
 * 实现方法：使用 BigDecimal 构造 30 位整数、12 位小数及越界值，并断言业务异常。
 * 关键约束：测试只验证公开 API 契约的输入范围，不改变舍入或单位换算规则。
 * 依赖与引用：FoundationDataService；Docs/API/计量单位API.md；Docs/规范/开发规范.md
 * 初始开发人员：Codex（智能体）
 * 初始开发时间：2026-11-21
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-精度边界核对；覆盖计量换算输入精度边界
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（4项；0失败、0错误）
 * 测试范围与记录位置：FoundationDataService.validateConversionQuantity；Maven Surefire 报告
 */
package org.dromara.web.service;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundationDataServiceTest {

    @Test
    void acceptsThirtyIntegerDigitsAndTwelveFractionalDigits() {
        BigDecimal maximum = new BigDecimal("9".repeat(30) + "." + "9".repeat(12));

        assertDoesNotThrow(() -> FoundationDataService.validateConversionQuantity(maximum));
    }

    @Test
    void rejectsMoreThanThirtyIntegerDigits() {
        BigDecimal overflow = new BigDecimal("1" + "0".repeat(30));

        assertThrows(ServiceException.class, () -> FoundationDataService.validateConversionQuantity(overflow));
    }

    @Test
    void rejectsMoreThanTwelveFractionalDigits() {
        BigDecimal overflow = new BigDecimal("1." + "0".repeat(12) + "1");

        assertThrows(ServiceException.class, () -> FoundationDataService.validateConversionQuantity(overflow));
    }

    @Test
    void rejectsNegativeConversionQuantity() {
        assertThrows(ServiceException.class,
            () -> FoundationDataService.validateConversionQuantity(new BigDecimal("-0.1")));
    }
}
