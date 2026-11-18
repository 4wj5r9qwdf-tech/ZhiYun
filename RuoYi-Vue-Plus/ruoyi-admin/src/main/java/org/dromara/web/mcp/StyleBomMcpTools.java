package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
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

    @McpTool(name = "style_bom_list", description = "按款式查询有权查看的 BOM 版本")
    public R<?> list(@McpToolParam(description = "款式 ID，十进制字符串") String styleId) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.all(null, null, null, Long.valueOf(styleId));
    }
    /** 通过 MCP 读取已授权的 BOM 版本和颜色尺码用料明细。 / Read an authorized BOM version and its color-size material details through MCP. */

    @McpTool(name = "style_bom_detail", description = "查询 BOM 单据及全部用料规则")
    public R<?> detail(@McpToolParam(description = "BOM ID，十进制字符串") String id) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.detail(Long.valueOf(id));
    }
    /** 通过 MCP 检查已发布 BOM 对色码及套装规格的覆盖。 / Expose BOM coverage checks for style pairs and set specifications through MCP. */

    @McpTool(name = "style_bom_coverage", description = "核对已保存 BOM 的颜色尺码用料覆盖情况")
    public R<?> coverage(@McpToolParam(description = "BOM ID，十进制字符串") String id) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.coverage(Long.valueOf(id));
    }
    /** 通过 MCP 试算指定款式规格的用料需求，不写库存。 / Preview material demand through MCP without inventory side effects. */

    @McpTool(name = "style_bom_preview", description = "按成衣颜色尺码或套装规格试算 BOM 用量")
    public R<?> preview(
        @McpToolParam(description = "BOM ID，十进制字符串") String id,
        @McpToolParam(description = "生产件数或套数") int count,
        @McpToolParam(description = "套装规格 ID，十进制字符串", required = false) String setSpecId,
        @McpToolParam(description = "单款颜色 ID，十进制字符串", required = false) String colorId,
        @McpToolParam(description = "单款尺码 ID，十进制字符串", required = false) String sizeId) {
        StpUtil.checkPermission("rd:bom:list");
        return controller.preview(Long.valueOf(id), count,
            optionalId(setSpecId), optionalId(colorId), optionalId(sizeId));
    }
    /** 按业务权限保存 BOM 草稿或发布版本并返回版本标识。 / Save an authorized BOM draft or release and return its version reference. */

    @McpTool(name = "style_bom_save", description = "保存完整 BOM 草稿，或经用户确认后发布，编辑时须带最新修订标识")
    public R<?> save(@McpToolParam(description = "BOM 保存请求，包含完整用料规则；ID 使用十进制字符串") Map<String, Object> body) {
        StpUtil.checkPermission("rd:bom:list");
        StpUtil.checkPermission("rd:bom:edit");
        return controller.save(JsonUtils.parseObject(JsonUtils.toJsonString(body), StyleBomController.Input.class));
    }

    /** 可选规格ID允许省略或传空白；统一转换为 null 后交由业务校验。 / Normalize omitted optional specification IDs before business validation. */
    private Long optionalId(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
    }
}
