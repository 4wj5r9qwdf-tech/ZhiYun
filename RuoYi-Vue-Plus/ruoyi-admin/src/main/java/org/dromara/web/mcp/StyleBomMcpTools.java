/*
 * 文件用途：向受控智能体暴露织云 ERP 的款式BOM MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的款式BOM MCP 工具能力。
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
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.web.controller.StyleBomController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 款式 BOM 工具复用单据权限、分类检查及业务审计。 / Reuse BOM permissions, scope checks and audit. */
@Component
@RequiredArgsConstructor
public class StyleBomMcpTools {
    private final StyleBomController controller;

    /** 按款式查询当前员工有权限查看的 BOM 版本。 / List BOM versions visible to the current employee for a style. */
    @McpTool(name = "style_bom_list", description = "按款式查询有权查看的 BOM 版本")
    public R<?> list(@McpToolParam(description = "款式 ID，正十进制字符串；从款式查询结果复制") String styleId) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.all(null, null, null, requiredId(styleId, "款式ID"));
    }

    /** 读取已授权 BOM 单据及颜色尺码用料规则。 / Read an authorized BOM document and its color-size material rules. */
    @McpTool(name = "style_bom_detail", description = "查询 BOM 单据及全部用料规则")
    public R<?> detail(@McpToolParam(description = "BOM ID，正十进制字符串；从 style_bom_list 结果复制") String id) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.detail(requiredId(id, "BOM ID"));
    }

    /** 检查已保存 BOM 对款式颜色尺码及套装规格的用料覆盖。 / Check saved BOM material coverage for style color-size pairs and set specifications. */
    @McpTool(name = "style_bom_coverage", description = "核对已保存 BOM 的颜色尺码用料覆盖情况")
    public R<?> coverage(@McpToolParam(description = "BOM ID，正十进制字符串；从 style_bom_list 结果复制") String id) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.coverage(requiredId(id, "BOM ID"));
    }

    /** 试算指定款式规格的用料需求，不修改库存或 BOM。 / Preview material demand for selected style specifications without changing inventory or BOM data. */
    @McpTool(name = "style_bom_preview", description = "按成衣颜色尺码或套装规格试算 BOM 用量")
    public R<?> preview(
        @McpToolParam(description = "BOM ID，正十进制字符串；从 style_bom_list 结果复制") String id,
        @McpToolParam(description = "生产件数或套数") int count,
        @McpToolParam(description = "可选，套装规格 ID 十进制字符串；单款试算时省略", required = false) String setSpecId,
        @McpToolParam(description = "可选，单款颜色 ID 十进制字符串；套装试算时省略", required = false) String colorId,
        @McpToolParam(description = "可选，单款尺码 ID 十进制字符串；套装试算时省略", required = false) String sizeId) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.preview(requiredId(id, "BOM ID"), count,
            optionalId(setSpecId), optionalId(colorId), optionalId(sizeId));
    }

    /** 保存完整 BOM 草稿或发布版本；业务校验和数据审计由控制器复用。 / Save a complete BOM draft or release while reusing controller validation and audit. */
    @McpTool(name = "style_bom_save", description = "保存完整 BOM 草稿，或经用户确认后发布，编辑时须带最新修订标识")
    public R<?> save(@McpToolParam(description = "BOM 保存请求，包含完整用料规则；ID 使用十进制字符串") Map<String, Object> body) {
        if (body == null) return R.fail("BOM请求不能为空");
        StpUtil.checkPermission("rd:bom:list");
        StpUtil.checkPermission("rd:bom:edit");
        return controller.save(JsonUtils.parseObject(JsonUtils.toJsonString(body), StyleBomController.Input.class));
    }

    /** 可选规格 ID 可省略或传空白；非空值必须是正十进制 ID。 / Optional specification IDs may be omitted or blank; supplied values must be positive decimal IDs. */
    private Long optionalId(String value) {
        return McpInputValidator.optionalPositiveId(value, "规格ID");
    }

    /** 解析正十进制主键并将非法输入转为明确的业务参数错误。 / Parse a positive decimal ID and report malformed values as business input errors. */
    private Long requiredId(String value, String field) {
        return McpInputValidator.requirePositiveId(value, field);
    }
}
