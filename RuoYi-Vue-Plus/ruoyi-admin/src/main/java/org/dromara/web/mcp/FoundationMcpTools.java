package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.web.controller.UnitController;
import org.dromara.web.controller.CategoryController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;

/** 公共基础档案工具，按当前员工权限委托现有入口。 / Expose explicit employee-authorized foundation tools. */
@Component @RequiredArgsConstructor
public class FoundationMcpTools {
    private final UnitController units;
    private final CategoryController categories;
    /** 查询全局计量单位目录。 / Execute the explicit authorized unit_list operation. */
    @McpTool(name="unit_list", description="查询全局计量单位目录")
    public R<?> unitList() { StpUtil.checkPermission("system:unit:list"); return units.list(); }

    /** 查询公共单位选项，selectable标记是否可新增引用。 / Execute the explicit authorized unit_options operation. */
    @McpTool(name="unit_options", description="查询公共单位选项，selectable标记是否可新增引用")
    public R<?> unitOptions() { StpUtil.checkLogin(); return units.options(); }

    /** 同参照单位固定换算，按目标单位精度四舍五入。 / Execute the explicit authorized unit_convert operation. */
    @McpTool(name="unit_convert", description="同参照单位固定换算，按目标单位精度四舍五入")
    public R<?> unitConvert(@McpToolParam(description="源单位ID十进制字符串") String fromId, @McpToolParam(description="目标单位ID十进制字符串") String toId, @McpToolParam(description="非负十进制数量字符串") String quantity) { StpUtil.checkLogin(); return units.convert(fromId,toId,quantity); }

    /** 新增计量单位；写操作前必须展示完整内容并取得员工确认。 / Execute the explicit authorized unit_create operation. */
    @McpTool(name="unit_create", description="新增计量单位；写操作前必须展示完整内容并取得员工确认")
    public R<?> unitCreate(@McpToolParam(description="单位字段code,name,symbol,dimension,decimalPlaces,baseUnitId,factor,sortOrder,status,remark，ID和系数用字符串") Map<String,Object> body) { StpUtil.checkPermission("system:unit:add"); return units.add(body); }

    /** 修改计量单位及状态；必须先展示前后差异并取得员工确认。 / Execute the explicit authorized unit_update operation. */
    @McpTool(name="unit_update", description="修改计量单位及状态；必须先展示前后差异并取得员工确认")
    public R<?> unitUpdate(@McpToolParam(description="单位ID十进制字符串") String id, @McpToolParam(description="完整单位字段对象，结构同unit_create") Map<String,Object> body) { StpUtil.checkPermission("system:unit:edit"); return units.edit(Long.valueOf(id),body); }

    /** 删除无引用单位；必须展示编码名称并取得员工本次确认。 / Execute the explicit authorized unit_delete operation. */
    @McpTool(name="unit_delete", description="删除无引用单位；必须展示编码名称并取得员工本次确认")
    public R<?> unitDelete(@McpToolParam(description="单位ID十进制字符串") String id) { StpUtil.checkPermission("system:unit:remove"); return units.remove(Long.valueOf(id)); }

    /** 查询全部分类体系。 / Execute the explicit authorized category_schemes operation. */
    @McpTool(name="category_schemes", description="查询全部分类体系")
    public R<?> categorySchemes() { StpUtil.checkPermission("system:category:list"); return categories.schemes(); }

    /** 查询公共分类体系选项，停用项仅供历史显示。 / Execute the explicit authorized category_scheme_options operation. */
    @McpTool(name="category_scheme_options", description="查询公共分类体系选项，停用项仅供历史显示")
    public R<?> categorySchemeOptions() { StpUtil.checkLogin(); return categories.schemeOptions(); }

    /** 查询单体系所有分类节点。 / Execute the explicit authorized category_list operation. */
    @McpTool(name="category_list", description="查询单体系所有分类节点")
    public R<?> categoryList(@McpToolParam(description="体系ID十进制字符串") String schemeId) { StpUtil.checkPermission("system:category:list"); return categories.list(Long.valueOf(schemeId)); }

    /** 查询公共分类选项，selectable反映体系和祖先状态。 / Execute the explicit authorized category_options operation. */
    @McpTool(name="category_options", description="查询公共分类选项，selectable反映体系和祖先状态")
    public R<?> categoryOptions(@McpToolParam(description="体系ID十进制字符串") String schemeId) { StpUtil.checkLogin(); return categories.options(Long.valueOf(schemeId)); }

    /** 新增分类节点；先展示体系和父级路径并取得员工确认。 / Execute the explicit authorized category_create operation. */
    @McpTool(name="category_create", description="新增分类节点；先展示体系和父级路径并取得员工确认")
    public R<?> categoryCreate(@McpToolParam(description="分类字段schemeId,parentId,code,name,sortOrder,status,remark，ID均为字符串") Map<String,Object> body) { StpUtil.checkPermission("system:category:add"); return categories.add(body); }

    /** 修改或移动分类；必须展示层级和字段变化并取得员工确认。 / Execute the explicit authorized category_update operation. */
    @McpTool(name="category_update", description="修改或移动分类；必须展示层级和字段变化并取得员工确认")
    public R<?> categoryUpdate(@McpToolParam(description="分类ID十进制字符串") String id, @McpToolParam(description="完整分类字段对象，结构同category_create") Map<String,Object> body) { StpUtil.checkPermission("system:category:edit"); return categories.edit(Long.valueOf(id),body); }

    /** 删除无下级及无引用的分类；须取得员工本次确认。 / Execute the explicit authorized category_delete operation. */
    @McpTool(name="category_delete", description="删除无下级及无引用的分类；须取得员工本次确认")
    public R<?> categoryDelete(@McpToolParam(description="分类ID十进制字符串") String id) { StpUtil.checkPermission("system:category:remove"); return categories.remove(Long.valueOf(id)); }

    /** 新增分类体系；须展示完整字段并取得员工确认。 / Execute the explicit authorized category_scheme_create operation. */
    @McpTool(name="category_scheme_create", description="新增分类体系；须展示完整字段并取得员工确认")
    public R<?> categorySchemeCreate(@McpToolParam(description="体系字段code,name,maxDepth,sortOrder,status,remark") Map<String,Object> body) { StpUtil.checkPermission("system:category:add"); return categories.addScheme(body); }

    /** 修改分类体系及状态；须展示对新业务选择的影响并取得确认。 / Execute the explicit authorized category_scheme_update operation. */
    @McpTool(name="category_scheme_update", description="修改分类体系及状态；须展示对新业务选择的影响并取得确认")
    public R<?> categorySchemeUpdate(@McpToolParam(description="体系ID十进制字符串") String id, @McpToolParam(description="完整体系字段，结构同category_scheme_create") Map<String,Object> body) { StpUtil.checkPermission("system:category:edit"); return categories.editScheme(Long.valueOf(id),body); }

    /** 删除无分类及无引用的体系；须取得员工本次确认。 / Execute the explicit authorized category_scheme_delete operation. */
    @McpTool(name="category_scheme_delete", description="删除无分类及无引用的体系；须取得员工本次确认")
    public R<?> categorySchemeDelete(@McpToolParam(description="体系ID十进制字符串") String id) { StpUtil.checkPermission("system:category:remove"); return categories.removeScheme(Long.valueOf(id)); }

}
