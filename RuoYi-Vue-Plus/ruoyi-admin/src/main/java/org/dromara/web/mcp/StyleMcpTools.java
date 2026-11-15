package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.web.controller.StyleController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 款式 MCP 复用控制器的分类授权、事务与业务审计。 / Reuse style authorization, transactions and audit. */
@Component
@RequiredArgsConstructor
public class StyleMcpTools {
    private final StyleController controller;

    private void requireList() {
        StpUtil.checkPermission("rd:style:list");
    }

    @McpTool(name = "style_list", description = "查询当前员工有权查看的款式及规格、套装组成，需要 rd:style:list 权限")
    public R<?> list() {
        requireList();
        return controller.list();
    }

    @McpTool(name = "style_category_options", description = "查询当前员工可使用的款式分类，需要 rd:style:list 权限")
    public R<?> categoryOptions() {
        requireList();
        return controller.categoryOptions();
    }

    @McpTool(name = "style_spec_options", description = "查询颜色、尺码及尺码组，需要 rd:style:list 权限")
    public R<?> specOptions() {
        requireList();
        return controller.specOptions();
    }

    @McpTool(name = "style_configuration", description = "查询款式颜色、尺码和套装子款，受人员分类权限约束，需要 rd:style:list 权限")
    public R<?> configuration(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        return controller.configuration(Long.valueOf(id));
    }

    @McpTool(name = "style_tags", description = "查询款式已关联的标签及分组，需要 rd:style:list 权限")
    public R<?> tags(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        return controller.tags(Long.valueOf(id));
    }

    @McpTool(name = "style_create", description = "新增单款或套装，提交完整档案和规格，需要 rd:style:list 与 rd:style:add 权限")
    public R<?> create(@McpToolParam(description = "款式字段对象，ID使用十进制字符串，包含款号、分类、生产方式、子款、颜色尺码、标签及附件") Map<String, Object> body) {
        requireList();
        StpUtil.checkPermission("rd:style:add");
        return controller.create(JsonUtils.parseObject(JsonUtils.toJsonString(body), StyleController.StyleInput.class));
    }

    @McpTool(name = "style_update", description = "修改款式及其完整关联，需要 rd:style:list 与 rd:style:edit 权限")
    public R<?> update(@McpToolParam(description = "款式ID，十进制字符串") String id,
                       @McpToolParam(description = "完整款式资料及最终关联集合，ID使用十进制字符串") Map<String, Object> body) {
        requireList();
        StpUtil.checkPermission("rd:style:edit");
        return controller.update(Long.valueOf(id), JsonUtils.parseObject(JsonUtils.toJsonString(body), StyleController.StyleInput.class));
    }

    @McpTool(name = "style_set_status", description = "启用或停用款式，需要 rd:style:list 与 rd:style:edit 权限")
    public R<?> status(@McpToolParam(description = "款式ID，十进制字符串") String id,
                       @McpToolParam(description = "目标状态，0启用、1停用") String status) {
        requireList();
        StpUtil.checkPermission("rd:style:edit");
        return controller.status(Long.valueOf(id), Map.of("status", status));
    }

    @McpTool(name = "style_delete", description = "删除没有业务引用的款式，需要 rd:style:list 与 rd:style:remove 权限")
    public R<?> delete(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        StpUtil.checkPermission("rd:style:remove");
        return controller.delete(Long.valueOf(id));
    }
}
