/*
 * 文件用途：向受控智能体暴露织云 ERP 的MCP 参数校验能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的MCP 参数校验能力。
 * 设计思路：工具契约以显式权限、输入白名单、当前用户身份和数据范围为边界，复用既有业务实现。
 * 实现方法：解析参数、校验身份权限和格式、调用业务层并按最小必要字段返回结构化结果。
 * 关键约束：不得扩大员工数据范围或返回非必要敏感信息。
 * 依赖与引用：Docs/API/API文档.md；Docs/AI/织云AI集成总体规划.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;

import java.util.Set;

/** MCP 入参公共校验器，统一解析 bigint 主键并把格式错误转换为业务提示。 / Shared MCP input validation for bigint IDs. */
public final class McpInputValidator {

    private static final Set<String> BINARY_FLAGS = Set.of("0", "1");

    /** 工具类不允许实例化。 / Prevent utility-class instantiation. */
    private McpInputValidator() {
    }

    /**
     * 只接受正十进制字符串并检查 Long 范围；不接受 JSON 数字以避免客户端精度损失。
     * Accept positive decimal strings only and enforce the Long range; reject JSON numbers to avoid precision loss.
     */
    public static Long requirePositiveId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[0-9]{1,19}")) {
            throw new ServiceException(field + "必须是正十进制字符串");
        }
        try {
            long id = Long.parseLong(normalized);
            if (id <= 0) {
                throw new ServiceException(field + "必须大于0");
            }
            return id;
        } catch (NumberFormatException error) {
            throw new ServiceException(field + "超出有效范围");
        }
    }

    /** 要求对象字段以十进制字符串承载主键；拒绝 JSON 数字、null 与其他类型。 / Require object ID fields to be decimal strings, rejecting JSON numbers and other types. */
    public static Long requirePositiveId(Object value, String field) {
        if (!(value instanceof String id)) {
            throw new ServiceException(field + "必须以十进制字符串传递");
        }
        return requirePositiveId(id, field);
    }

    /**
     * 解析可选主键；未传或空白表示不按该主键筛选，其他值仍必须满足严格 ID 格式。
     * Parse an optional ID; missing or blank values mean no filter, while provided values remain strictly validated.
     */
    public static Long optionalPositiveId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requirePositiveId(value, field);
    }

    /**
     * 解析 JSON 对象中的可选 bigint；要求以字符串传输，避免宽松转字符串掩盖数值精度损失。
     * Parse an optional JSON bigint field only from a string, preventing lossy numeric coercion.
     */
    public static Long optionalPositiveId(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String id)) {
            throw new ServiceException(field + "必须以十进制字符串传递");
        }
        return optionalPositiveId(id, field);
    }

    /**
     * 解析可选非负十进制 ID；仅供业务明确把0定义为特殊筛选值时使用。
     * Parse an optional non-negative decimal ID for filters whose business contract explicitly assigns meaning to zero.
     */
    public static Long optionalNonNegativeId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.matches("[0-9]{1,19}")) {
            throw new ServiceException(field + "必须是非负十进制字符串");
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException error) {
            throw new ServiceException(field + "超出有效范围");
        }
    }

    /** 校验可选的0/1状态筛选，空值表示不筛选；其他值拒绝，避免查询意外退化为全量。 / Validate an optional 0/1 status filter and reject malformed values instead of broadening the query. */
    public static String optionalBinaryFlag(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String flag)) {
            throw new ServiceException(field + "只能是字符串0或1");
        }
        String normalized = flag.trim();
        return normalized.isEmpty() ? null : requireBinaryFlag(normalized, field);
    }

    /** 校验服务端约定的0/1状态字段并返回原字符串。 / Validate a documented 0/1 flag and return the original string. */
    public static String requireBinaryFlag(Object value, String field) {
        if (!(value instanceof String flag) || !BINARY_FLAGS.contains(flag)) {
            throw new ServiceException(field + "只能是字符串0或1");
        }
        return flag;
    }
}
