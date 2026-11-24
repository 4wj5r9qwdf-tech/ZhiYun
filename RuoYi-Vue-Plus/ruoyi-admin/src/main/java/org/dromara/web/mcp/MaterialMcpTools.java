/*
 * 文件用途：向受控智能体暴露织云 ERP 的物料 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的物料 MCP 工具能力。
 * 设计思路：工具契约以显式权限、输入白名单、当前用户身份和数据范围为边界，复用既有业务实现。
 * 实现方法：解析参数、校验身份权限和格式、调用业务层并按最小必要字段返回结构化结果。
 * 关键约束：不得扩大员工数据范围或返回非必要敏感信息。
 * 依赖与引用：Docs/API/API文档.md；Docs/AI/织云AI集成总体规划.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP写工具确认契约；补齐物料新增、修改和状态变更的调用前展示
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（MCP 工具契约测试覆盖全部命名写工具）
 * 测试范围与记录位置：McpToolContractTest；Maven Surefire 报告
 */
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.web.controller.MaterialController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 物料档案 MCP 适配层；复用 REST 控制器的分类权限、业务校验和审计。
 * Material MCP adapter reuses category authorization, business validation and audit from the REST controller.
 */
@Component
@RequiredArgsConstructor
public class MaterialMcpTools {

    private static final Set<String> LIST_FILTER_FIELDS = Set.of("keyword", "schemeId", "categoryId", "status");

    private final MaterialController controller;

    /** 查询当前员工分类授权范围内的物料档案。 / List material records within the current employee's category grants. */
    @McpTool(name = "material_list", description = "查询物料档案；服务端按人员分类权限过滤，需要 inventory:material:list 权限")
    public R<?> list(
        @McpToolParam(description = "可选筛选对象：keyword字符串、schemeId/categoryId正十进制字符串、status为0启用或1停用；省略或空对象查询当前人员分类权限内的全部物料；未知字段和错误ID会返回参数错误", required = false)
        Map<String, Object> filter) {
        StpUtil.checkPermission("inventory:material:list");
        validateListFilter(filter);
        return controller.list(
            text(filter, "keyword"),
            McpInputValidator.optionalPositiveId(value(filter, "schemeId"), "分类体系ID"),
            McpInputValidator.optionalPositiveId(value(filter, "categoryId"), "物料分类ID"),
            text(filter, "status"));
    }

    /** 查询人员授权范围内可使用的物料分类类型和树节点。 / Read material taxonomy choices within the employee's grants. */
    @McpTool(name = "material_category_options", description = "查询物料可选分类类型和分类树；只返回当前人员授权范围内的节点，需要员工登录")
    public R<?> categories() {
        StpUtil.checkLogin();
        return controller.categoryOptions();
    }

    /** 查询当前员工可见的启用物料和启用规格。 / List enabled materials and variants visible to the current employee. */
    @McpTool(name = "material_options", description = "查询当前人员分类权限范围内启用的物料选项及启用规格，需要员工登录")
    public R<?> options(
        @McpToolParam(description = "可选的物料编码、名称、规格或型号关键字；省略或空白表示返回全部可见的启用物料", required = false)
        String keyword) {
        StpUtil.checkLogin();
        return controller.options(keyword);
    }

    /** 查询物料规格模板字段、数据类型、选项与单位定义。 / Read material specification templates and field definitions. */
    @McpTool(name = "material_spec_templates", description = "查询物料属性模板、启用状态、字段键、类型和单位，需要员工登录")
    public R<?> specTemplates() {
        StpUtil.checkLogin();
        return controller.specTemplates();
    }

    /** 获取可编辑的物料编码建议；正式保存仍会按数据库现状重新分配并校验。 / Get an editable code suggestion; final allocation and uniqueness are rechecked on save. */
    @McpTool(name = "material_next_code", description = "新增物料前获取可编辑编码建议；正式保存仍由服务端校验和安全分配，需要 inventory:material:add 权限")
    public R<?> nextCode() {
        StpUtil.checkPermission("inventory:material:add");
        return controller.nextCode();
    }

    /** 新增物料档案；调用前须展示分类、单位、颜色、规格和全部属性并取得确认。 / Create a material after confirming its taxonomy, units, colors, variants and properties. */
    @McpTool(name = "material_create", description = "新增物料档案；调用前向员工展示编码、名称、分类、规格、型号、库存单位和颜色完整字段并取得本次明确确认，需要 inventory:material:add 权限")
    public R<?> create(
        @McpToolParam(description = "物料字段：code、name、categoryId、inventoryUnitId、materialUnits、colorIds、specTemplateCode、specValues、multiSpec、variantFields、variants、fabricWeightGsm、fabricWidthCm、composition、model、brand、materialDesc、sortOrder、remark；variants每条含code、name、status、attributes；所有ID为十进制字符串；自动编码建议可编辑")
        Map<String, Object> body) {
        StpUtil.checkPermission("inventory:material:add");
        return controller.add(body);
    }

    /** 全量更新物料资料及关联集合；未展示的字段不得擅自移除。 / Update the material and its final relations without dropping unreviewed data. */
    @McpTool(name = "material_update", description = "修改物料档案；调用前先读取当前记录，展示完整字段前后差异和关联影响并取得员工本次明确确认，需要 inventory:material:edit 权限")
    public R<?> update(
        @McpToolParam(description = "物料ID正十进制字符串") String id,
        @McpToolParam(description = "完整字段，含最终colorIds和materialUnits；属性模板及共用值为specTemplateCode和specValues；维护规格须提交multiSpec、variantFields、specValues及完整variants，已有规格带字符串id，停用状态为1")
        Map<String, Object> body) {
        StpUtil.checkPermission("inventory:material:edit");
        return controller.edit(McpInputValidator.requirePositiveId(id, "物料ID"), body);
    }

    /** 启用或停用物料；停用后阻止新业务引用，既有历史记录继续保留。 / Change material availability while preserving historical references. */
    @McpTool(name = "material_set_status", description = "启用或停用物料；执行前读取并展示物料编码、名称、当前及目标状态，说明停用会阻止新业务引用但保留历史，并取得员工本次明确确认，需要 inventory:material:edit 权限")
    public R<?> status(
        @McpToolParam(description = "物料ID正十进制字符串") String id,
        @McpToolParam(description = "目标状态：0启用、1停用") String status) {
        StpUtil.checkPermission("inventory:material:edit");
        return controller.status(McpInputValidator.requirePositiveId(id, "物料ID"),
            Map.of("status", McpInputValidator.requireBinaryFlag(status, "物料状态")));
    }

    /** 删除无业务引用的物料；存在引用时由服务端拒绝。 / Delete an unreferenced material; the server rejects records still in use. */
    @McpTool(name = "material_delete", description = "删除物料；存在业务引用时后端拒绝并要求停用，执行前展示物料编码名称并取得确认，需要 inventory:material:remove 权限")
    public R<?> delete(@McpToolParam(description = "物料ID正十进制字符串") String id) {
        StpUtil.checkPermission("inventory:material:remove");
        return controller.remove(McpInputValidator.requirePositiveId(id, "物料ID"));
    }

    /** 拒绝未知筛选字段及错误类型，避免参数写错后静默扩大查询范围。 / Reject unknown filters and malformed types rather than broadening a query silently. */
    private void validateListFilter(Map<String, Object> filter) {
        if (filter == null) {
            return;
        }
        for (String key : filter.keySet()) {
            if (!LIST_FILTER_FIELDS.contains(key)) {
                throw new ServiceException("不支持物料筛选字段：" + key);
            }
        }
        for (String key : Set.of("keyword", "status")) {
            Object value = filter.get(key);
            if (value != null && !(value instanceof String)) {
                throw new ServiceException(key + "必须是字符串");
            }
        }
        Object status = filter.get("status");
        if (status instanceof String value && !value.isBlank() && !Set.of("0", "1").contains(value.trim())) {
            throw new ServiceException("物料状态只能是0或1");
        }
    }

    /** 读取已校验的文本筛选值并去除首尾空格。 / Read a validated string filter and trim surrounding whitespace. */
    private String text(Map<String, Object> map, String key) {
        Object value = value(map, key);
        return value == null ? null : ((String) value).trim();
    }

    /** 安全读取可选筛选对象中的字段。 / Safely read an optional filter field. */
    private Object value(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }
}
