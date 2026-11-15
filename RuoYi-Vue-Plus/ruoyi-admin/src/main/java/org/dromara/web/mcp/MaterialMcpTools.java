package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.web.controller.MaterialController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 物料档案 MCP 入口，复用 REST 控制器的分类权限、业务校验和审计。 / MCP tools reuse REST authorization, validation, and audit. */
@Component
@RequiredArgsConstructor
public class MaterialMcpTools {
    private final MaterialController controller;

    /** 按人员分类范围查询物料。 / List materials within the employee's category grants. */
    @McpTool(name="material_list",description="查询物料档案；服务端按人员分类权限过滤，需要 inventory:material:list 权限")
    public R<?> list(@McpToolParam(description="可选搜索和筛选：keyword、schemeId、categoryId、status") Map<String,Object> filter){
        StpUtil.checkPermission("inventory:material:list");
        return controller.list(text(filter,"keyword"),number(filter.get("schemeId")),number(filter.get("categoryId")),text(filter,"status"));
    }

    /** 读取可用分类供授权范围内的物料维护选择。 / Read visible category choices. */
    @McpTool(name="material_category_options",description="查询物料可选分类类型和分类树；只返回当前人员授权范围内的节点，需要员工登录")
    public R<?> categories(){StpUtil.checkLogin();return controller.categoryOptions();}

    /** 读取启用物料业务选项。 / Read enabled material choices within category scope. */
    @McpTool(name="material_options",description="查询当前人员分类权限范围内启用的物料选项及启用规格，需要员工登录")
    public R<?> options(@McpToolParam(description="可选的物料编码、名称、规格或型号关键字")String keyword){StpUtil.checkLogin();return controller.options(keyword);}

    /** 读取规格模板的字段定义。 / Load available specification field definitions. */
    @McpTool(name="material_spec_templates", description="查询物料属性模板、启用状态、字段键、类型和单位，需要员工登录")
    public R<?> specTemplates() {
        StpUtil.checkLogin();
        return controller.specTemplates();
    }

    /** 取得物料编码可编辑建议。 / Retrieve an editable material-code suggestion. */
    @McpTool(name="material_next_code",description="新增物料前获取可编辑编码建议；正式保存仍由服务端校验和安全分配，需要 inventory:material:add 权限")
    public R<?> nextCode(){StpUtil.checkPermission("inventory:material:add");return controller.nextCode();}

    /** 新增物料前须展示分类、单位、颜色等完整数据并取得确认。 / Create a material only after confirming all business references. */
    @McpTool(name="material_create",description="新增物料档案；须展示编码、名称、分类、规格、型号、库存单位和颜色完整字段并取得员工确认，需要 inventory:material:add 权限")
    public R<?> create(@McpToolParam(description="物料字段：code、name、categoryId、inventoryUnitId、materialUnits、colorIds、specTemplateCode、specValues、multiSpec、variantFields、variants、fabricWeightGsm、fabricWidthCm、composition、model、brand、materialDesc、sortOrder、remark；variants 每条含 code、name、status、attributes；variantFields 指定按规格填写的模板字段；materialUnits 为物料专属单位配置数组；ID均为十进制字符串；自动编码建议可编辑")Map<String,Object> body){StpUtil.checkPermission("inventory:material:add");return controller.add(body);}

    /** 修改物料字段及完整颜色集合。 / Update a material and its final color set. */
    @McpTool(name="material_update",description="修改物料档案；展示完整字段前后差异并取得确认，需要 inventory:material:edit 权限")
    public R<?> update(@McpToolParam(description="物料ID十进制字符串")String id,@McpToolParam(description="完整字段，含最终 colorIds 和 materialUnits；属性模板及共用值为 specTemplateCode 和 specValues；维护规格须提交 multiSpec、variantFields、specValues 和完整 variants，已保存规格须带字符串 id，停用用 status=1")Map<String,Object> body){StpUtil.checkPermission("inventory:material:edit");return controller.edit(Long.valueOf(id),body);}

    /** 修改启用状态并保留历史引用。 / Change availability without removing historical references. */
    @McpTool(name="material_set_status",description="启用或停用物料；停用阻止新业务引用但保留历史，执行前取得确认，需要 inventory:material:edit 权限")
    public R<?> status(@McpToolParam(description="物料ID十进制字符串")String id,@McpToolParam(description="目标状态：0启用、1停用")String status){StpUtil.checkPermission("inventory:material:edit");return controller.status(Long.valueOf(id),Map.of("status",status));}

    /** 删除无业务引用物料。 / Delete only when no downstream business references exist. */
    @McpTool(name="material_delete",description="删除物料；存在业务引用时后端拒绝并要求停用，执行前展示物料编码名称并取得确认，需要 inventory:material:remove 权限")
    public R<?> delete(@McpToolParam(description="物料ID十进制字符串")String id){StpUtil.checkPermission("inventory:material:remove");return controller.remove(Long.valueOf(id));}

    private String text(Map<String,Object> map,String key){Object value=map.get(key);return value==null?null:value.toString();}
    private Long number(Object value){try{return value==null?null:Long.valueOf(value.toString());}catch(Exception e){return null;}}
}
