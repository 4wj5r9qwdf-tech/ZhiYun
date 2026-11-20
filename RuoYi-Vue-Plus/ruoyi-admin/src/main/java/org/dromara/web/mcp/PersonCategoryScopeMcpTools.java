/*
 * 文件用途：向受控智能体暴露织云 ERP 的人员分类数据范围 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的人员分类数据范围 MCP 工具能力。
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

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.web.controller.PersonController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 人员分类权限 MCP 工具；使用专属配置权限并复用人员控制器的数据范围校验和审计。
 * Employee category-scope MCP tools require the dedicated permission and reuse controller scope checks and audit.
 */
@Component
@RequiredArgsConstructor
public class PersonCategoryScopeMcpTools {

    private static final String CONFIG_PERMISSION = "system:person:categoryScope";

    private final PersonController personController;

    /** 查询目标人员的分类授权及分类树目录。 / Read an employee's category scope and taxonomy. */
    @McpTool(name = "person_category_scope_get", description = "查询人员分类权限与可配置分类树；仅可查本人数据范围内的普通人员，需要 system:person:categoryScope 权限")
    public R<?> get(
        @McpToolParam(description = "目标人员 userId，十进制字符串；必须是本人数据范围内的普通人员") String userId) {
        StpUtil.checkPermission(CONFIG_PERMISSION);
        return personController.categoryScope(parseId(userId, "目标人员ID"));
    }

    /** 全量替换目标人员的分类授权；调用前应向员工展示授权差异并确认。 / Replace grants after showing the diff and obtaining confirmation. */
    @McpTool(name = "person_category_scope_set", description = "设置人员分类权限；执行前必须展示目标人员、原授权和新授权并取得员工确认；仅可操作本人数据范围内的普通人员，需要 system:person:categoryScope 权限")
    public R<?> set(
        @McpToolParam(description = "目标人员 userId，十进制字符串") String userId,
        @McpToolParam(description = "授权对象：mode 必填，ALL全部可见、NONE全部不可见、SELECTED指定范围；grants 必须为数组。每项必须恰好包含 schemeId 或 categoryId 之一，ID使用十进制字符串，includeChildren为布尔值。ALL/NONE 的 grants 传空数组") Map<String, Object> scope) {
        StpUtil.checkPermission(CONFIG_PERMISSION);
        if (scope == null || !(scope.get("mode") instanceof String mode)
            || !(scope.get("grants") instanceof List<?> rawGrants)) {
            throw new ServiceException("授权对象必须包含mode和grants数组");
        }
        List<PersonController.CategoryGrant> grants = new ArrayList<>();
        for (Object value : rawGrants) {
            if (!(value instanceof Map<?, ?> item)) {
                throw new ServiceException("每项分类授权必须是对象");
            }
            Long schemeId = optionalId(item.get("schemeId"), "分类类型ID");
            Long categoryId = optionalId(item.get("categoryId"), "分类节点ID");
            Object includeChildrenValue = item.get("includeChildren");
            if (!(includeChildrenValue instanceof Boolean includeChildren)) {
                throw new ServiceException("includeChildren必须为布尔值");
            }
            grants.add(new PersonController.CategoryGrant(schemeId, categoryId, includeChildren));
        }
        return personController.saveCategoryScope(parseId(userId, "目标人员ID"),
            new PersonController.CategoryScope(mode, grants));
    }

    /** 校验并解析十进制字符串主键，避免客户端浮点数造成大整数精度丢失。 / Parse IDs only from decimal strings. */
    private Long parseId(String value, String label) {
        return McpInputValidator.requirePositiveId(value, label);
    }

    /** 可选授权ID为空时返回null；非空值必须是十进制字符串。 / Parse a nullable authorization ID. */
    private Long optionalId(Object value, String label) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String id)) {
            throw new ServiceException(label + "必须以十进制字符串传递");
        }
        return parseId(id, label);
    }
}
