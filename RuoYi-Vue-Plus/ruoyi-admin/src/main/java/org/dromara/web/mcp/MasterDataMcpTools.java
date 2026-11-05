package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.controller.system.SysOrgController;
import org.dromara.system.controller.system.SysPostController;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.bo.SysPostBo;
import org.dromara.web.controller.BuildingController;
import org.dromara.web.controller.CompanyController;
import org.dromara.web.controller.CustomerController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 已验收基础档案的 MCP 工具。Controller 的 Sa-Token 注解只由 MVC AOP 执行，
 * 因此 MCP 入口逐项显式复核相同权限，并继续委托现有 Controller 业务逻辑。
 */
@Component
@RequiredArgsConstructor
public class MasterDataMcpTools {

    private final CustomerController customerController;
    private final CompanyController companyController;
    private final SysOrgController orgController;
    private final SysPostController postController;
    private final BuildingController buildingController;

    // 客户档案 customer_master
    @McpTool(name = "customer_list", description = "分页查询客户档案；联系方式在详情中按当前员工权限脱敏，需要 system:customer:list")
    public R<?> customerList(
        @McpToolParam(description = "可选，客户编码/名称/简称关键词") String keyword,
        @McpToolParam(description = "可选，0启用、1停用") String status,
        @McpToolParam(description = "可选，业务员用户ID十进制字符串") String salesId,
        @McpToolParam(description = "可选，客户标签ID十进制字符串") String tagId,
        @McpToolParam(description = "页码，默认1") Integer pageNum,
        @McpToolParam(description = "每页条数，默认20，最大100") Integer pageSize) {
        StpUtil.checkPermission("system:customer:list");
        return customerController.list(blank(keyword), blank(status), parseLong(salesId), parseLong(tagId),
            pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
    }

    @McpTool(name = "customer_category_counts", description = "查询客户总数和标签分类数量，需要 system:customer:list")
    public R<?> customerCategoryCounts() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.categoryCounts();
    }

    @McpTool(name = "customer_get", description = "查询客户详情、联系人、地址和标签；普通查询权限下联系方式会脱敏，需要 system:customer:list")
    public R<?> customerGet(@McpToolParam(description = "客户ID，十进制字符串") String customerId) {
        StpUtil.checkPermission("system:customer:list");
        return customerController.detail(Long.valueOf(customerId));
    }

    @McpTool(name = "customer_check_duplicate", description = "按客户名称检查同名数量；仅提示，不阻止保存，需要 system:customer:list")
    public R<?> customerCheckDuplicate(
        @McpToolParam(description = "待检查客户名称") String name,
        @McpToolParam(description = "可选，当前客户ID；新增时传0") String excludeId) {
        StpUtil.checkPermission("system:customer:list");
        return customerController.duplicates(name, parseLong(excludeId) == null ? 0L : parseLong(excludeId));
    }

    @McpTool(name = "customer_sales_options", description = "查询业务员及组织、岗位、小组筛选选项，不返回人员敏感信息，需要 system:customer:list")
    public R<?> customerSalesOptions() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.salesOptions();
    }

    @McpTool(name = "customer_options", description = "查询可供新业务引用的启用客户，需要 system:customer:list")
    public R<?> customerOptions() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.options();
    }

    @McpTool(name = "customer_create", description = "新增客户档案；执行前必须展示主档、联系人、地址及标签并取得员工确认，需要 system:customer:add")
    public R<?> customerCreate(@McpToolParam(description = "客户字段对象：customer_name、customer_short_name、unified_social_credit_code、country_region、country_region_code、sales_representative_id、remark、contacts、addresses、tagIds") Map<String, Object> body) {
        StpUtil.checkPermission("system:customer:add");
        return customerController.add(body);
    }

    @McpTool(name = "customer_update", description = "修改客户档案；按完整主档、联系人、地址和标签集合提交，执行前展示变化并取得确认，需要 system:customer:edit")
    public R<?> customerUpdate(
        @McpToolParam(description = "客户ID，十进制字符串") String customerId,
        @McpToolParam(description = "客户字段对象，结构同 customer_create") Map<String, Object> body) {
        StpUtil.checkPermission("system:customer:edit");
        return customerController.edit(Long.valueOf(customerId), body);
    }

    @McpTool(name = "customer_set_status", description = "启用或停用客户；停用不删除历史引用，执行前取得确认，需要 system:customer:edit")
    public R<?> customerSetStatus(
        @McpToolParam(description = "客户ID，十进制字符串") String customerId,
        @McpToolParam(description = "0启用、1停用") String status) {
        StpUtil.checkPermission("system:customer:edit");
        return customerController.status(Long.valueOf(customerId), Map.of("status", status));
    }

    @McpTool(name = "customer_delete", description = "删除未被业务引用的客户档案；不可逆，执行前展示客户编码和名称并取得本次确认，需要 system:customer:remove")
    public R<?> customerDelete(@McpToolParam(description = "客户ID，十进制字符串") String customerId) {
        StpUtil.checkPermission("system:customer:remove");
        return customerController.remove(Long.valueOf(customerId));
    }

    // 公司档案 company
    @McpTool(name = "company_list", description = "分页查询公司档案，需要 system:company:list")
    public R<?> companyList(
        @McpToolParam(description = "页码，默认1") Integer pageNum,
        @McpToolParam(description = "每页条数，默认20，最大100") Integer pageSize) {
        StpUtil.checkPermission("system:company:list");
        return companyController.list(pageQuery(pageNum, pageSize));
    }

    @McpTool(name = "company_get", description = "按ID查询公司档案，需要 system:company:query")
    public R<?> companyGet(@McpToolParam(description = "公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:query");
        return companyController.getInfo(Long.valueOf(companyId));
    }

    @McpTool(name = "company_file_usage", description = "按OSS文件ID查询公司logo/登录背景引用，最多100个，需要 system:company:list")
    public R<?> companyFileUsage(@McpToolParam(description = "OSS文件ID，英文逗号分隔的十进制字符串") String ids) {
        StpUtil.checkPermission("system:company:list");
        Long[] fileIds = parseLongs(ids);
        return companyController.fileUsage(fileIds);
    }

    @McpTool(name = "company_create", description = "新增公司档案；执行前展示公司名、文件、主题和默认状态并取得确认，需要 system:company:add")
    public R<?> companyCreate(@McpToolParam(description = "公司字段对象：companyName、logoOssId、loginBgOssId、slogan、loginBgMode、nameColor、theme、isDefault") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:add");
        return companyController.add(body);
    }

    @McpTool(name = "company_update", description = "修改公司档案；执行前展示完整变更并取得确认，需要 system:company:edit")
    public R<?> companyUpdate(@McpToolParam(description = "完整公司字段对象，必须包含companyId") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:edit");
        return companyController.edit(body);
    }

    @McpTool(name = "company_set_default", description = "切换默认展示公司，会同步取消旧默认项；执行前展示影响并取得确认，需要 system:company:edit")
    public R<?> companySetDefault(@McpToolParam(description = "目标公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:edit");
        return companyController.setDefault(Long.valueOf(companyId));
    }

    @McpTool(name = "company_delete", description = "软删除公司档案；执行前展示公司身份并取得确认，需要 system:company:remove")
    public R<?> companyDelete(@McpToolParam(description = "公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:remove");
        return companyController.remove(Long.valueOf(companyId));
    }

    // 组织架构 org
    @McpTool(name = "org_list", description = "按条件查询组织节点，需要 system:org:list")
    public R<?> orgList(@McpToolParam(description = "组织筛选条件，可传orgName、status、orgCategory等字段") SysOrgBo query) {
        StpUtil.checkPermission("system:org:list");
        return orgController.list(query == null ? new SysOrgBo() : query);
    }

    @McpTool(name = "org_get", description = "按ID查询组织节点详情并检查数据范围，需要 system:org:query")
    public R<?> orgGet(@McpToolParam(description = "组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:query");
        return orgController.getInfo(Long.valueOf(orgId));
    }

    @McpTool(name = "org_options", description = "按ID查询组织选项，需要 system:org:query")
    public R<?> orgOptions(@McpToolParam(description = "可选，组织ID逗号分隔；不传返回全部可选组织") String orgIds) {
        StpUtil.checkPermission("system:org:query");
        Long[] ids = blank(orgIds) == null ? null : parseLongs(orgIds);
        return orgController.optionselect(ids);
    }

    @McpTool(name = "org_create", description = "新增组织节点；执行前展示上级、类别、建筑和负责人并取得确认，需要 system:org:add")
    public R<?> orgCreate(@McpToolParam(description = "组织字段对象：parentId、orgName、orgCategory、buildingId、location、orderNum、leader、phone、email、status") SysOrgBo body) {
        StpUtil.checkPermission("system:org:add");
        return orgController.add(body);
    }

    @McpTool(name = "org_update", description = "修改组织节点；执行前展示完整变化并取得确认，需要 system:org:edit")
    public R<?> orgUpdate(@McpToolParam(description = "完整组织字段对象，必须包含orgId") SysOrgBo body) {
        StpUtil.checkPermission("system:org:edit");
        return orgController.edit(body);
    }

    @McpTool(name = "org_delete", description = "删除无子级、无人员、无岗位且未被管辖的组织；执行前取得本次确认，需要 system:org:remove")
    public R<?> orgDelete(@McpToolParam(description = "组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:remove");
        return orgController.remove(Long.valueOf(orgId));
    }

    @McpTool(name = "org_manages_get", description = "查询某组织当前管辖的组织ID，需要 system:org:query")
    public R<?> orgManagesGet(@McpToolParam(description = "管辖方组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:query");
        return orgController.listManages(Long.valueOf(orgId));
    }

    @McpTool(name = "org_manages_set", description = "全量替换某组织的管辖关系；空列表会清空，执行前展示旧/新名单并取得确认，需要 system:org:edit")
    public R<?> orgManagesSet(
        @McpToolParam(description = "管辖方组织ID，十进制字符串") String orgId,
        @McpToolParam(description = "最终被管辖组织ID列表，全部为十进制字符串；空列表表示清空") List<String> managedOrgIds) {
        StpUtil.checkPermission("system:org:edit");
        List<Long> ids = managedOrgIds == null ? List.of() : managedOrgIds.stream().map(Long::valueOf).toList();
        return orgController.updateManages(Long.valueOf(orgId), ids);
    }

    @McpTool(name = "org_manage_counts", description = "查询各组织的被管辖数量和名称，需要 system:org:query")
    public R<?> orgManageCounts() {
        StpUtil.checkPermission("system:org:query");
        return orgController.manageCounts();
    }

    // 岗位管理 post
    @McpTool(name = "post_list", description = "分页查询岗位并支持名称/编码关键词，需要 system:post:list")
    public R<?> postList(
        @McpToolParam(description = "岗位查询条件，可传postName、postCode、orgId、status等") SysPostBo query,
        @McpToolParam(description = "可选名称或编码关键词") String keyword,
        @McpToolParam(description = "页码，默认1") Integer pageNum,
        @McpToolParam(description = "每页条数，默认20") Integer pageSize) {
        StpUtil.checkPermission("system:post:list");
        return postController.list(query == null ? new SysPostBo() : query, pageQuery(pageNum, pageSize), keyword);
    }

    @McpTool(name = "post_get", description = "按ID查询岗位详情，需要 system:post:query")
    public R<?> postGet(@McpToolParam(description = "岗位ID，十进制字符串") String postId) {
        StpUtil.checkPermission("system:post:query");
        return postController.getInfo(Long.valueOf(postId));
    }

    @McpTool(name = "post_options", description = "查询岗位选择项，可按组织或岗位ID过滤，需要 system:post:query")
    public R<?> postOptions(
        @McpToolParam(description = "可选，岗位ID逗号分隔") String postIds,
        @McpToolParam(description = "可选，组织ID") String orgId) {
        StpUtil.checkPermission("system:post:query");
        return postController.optionselect(blank(postIds) == null ? null : parseLongs(postIds), parseLong(orgId));
    }

    @McpTool(name = "post_create", description = "新增岗位；执行前展示岗位编码、名称、归属组织和权限配置并取得确认，需要 system:post:add")
    public R<?> postCreate(@McpToolParam(description = "岗位字段对象：orgId、belongOrgId、postCode、postName、postCategory、postSort、status、remark") SysPostBo body) {
        StpUtil.checkPermission("system:post:add");
        return postController.add(body);
    }

    @McpTool(name = "post_update", description = "修改岗位档案；执行前展示完整变化并取得确认，需要 system:post:edit")
    public R<?> postUpdate(@McpToolParam(description = "完整岗位字段对象，必须包含postId") SysPostBo body) {
        StpUtil.checkPermission("system:post:edit");
        return postController.edit(body);
    }

    @McpTool(name = "post_set_status", description = "启用或停用岗位；已分配员工时不可停用，执行前取得确认，需要 system:post:edit")
    public R<?> postSetStatus(
        @McpToolParam(description = "岗位ID，十进制字符串") String postId,
        @McpToolParam(description = "0启用、1停用") String status) {
        StpUtil.checkPermission("system:post:edit");
        return postController.changeStatus(Long.valueOf(postId), Map.of("status", status));
    }

    @McpTool(name = "post_delete", description = "批量删除岗位；执行前列明岗位编码和名称并取得确认，需要 system:post:remove")
    public R<?> postDelete(@McpToolParam(description = "岗位ID列表，逗号分隔的十进制字符串") String postIds) {
        StpUtil.checkPermission("system:post:remove");
        return postController.remove(parseLongs(postIds));
    }

    @McpTool(name = "post_org_tree", description = "查询岗位筛选用组织树，需要 system:post:list")
    public R<?> postOrgTree(@McpToolParam(description = "组织筛选条件，可传orgName、status、orgCategory等") SysOrgBo query) {
        StpUtil.checkPermission("system:post:list");
        return postController.orgTree(query == null ? new SysOrgBo() : query);
    }

    // 建筑档案 building
    @McpTool(name = "building_list", description = "按关键词、用途和状态查询建筑档案，需要 system:building:list")
    public R<?> buildingList(
        @McpToolParam(description = "可选，建筑编码、名称或地址关键词") String keyword,
        @McpToolParam(description = "可选，建筑用途类别") String buildingType,
        @McpToolParam(description = "可选，0启用、1停用") String status) {
        StpUtil.checkPermission("system:building:list");
        return buildingController.list(blank(keyword), blank(buildingType), blank(status));
    }

    @McpTool(name = "building_get", description = "按ID查询建筑档案，需要 system:building:query")
    public R<?> buildingGet(@McpToolParam(description = "建筑ID，十进制字符串") String plantId) {
        StpUtil.checkPermission("system:building:query");
        return buildingController.getInfo(Long.valueOf(plantId));
    }

    @McpTool(name = "building_create", description = "新增建筑档案；执行前展示建筑用途、编码、名称和面积信息并取得确认，需要 system:building:add")
    public R<?> buildingCreate(@McpToolParam(description = "建筑字段对象：buildingType、plantCode、plantName、floorInfo、landArea、buildingArea、address、builtYear、status、remark") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:add");
        return buildingController.add(body);
    }

    @McpTool(name = "building_update", description = "修改建筑档案；执行前展示完整变化并取得确认，需要 system:building:edit")
    public R<?> buildingUpdate(@McpToolParam(description = "完整建筑字段对象，必须包含plantId") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:edit");
        return buildingController.edit(body);
    }

    @McpTool(name = "building_delete", description = "软删除未关联组织节点的建筑；执行前展示建筑身份并取得确认，需要 system:building:remove")
    public R<?> buildingDelete(@McpToolParam(description = "建筑ID，十进制字符串") String plantId) {
        StpUtil.checkPermission("system:building:remove");
        return buildingController.remove(Long.valueOf(plantId));
    }

    private PageQuery pageQuery(Integer pageNum, Integer pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum == null || pageNum < 1 ? 1 : pageNum);
        query.setPageSize(pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100));
        return query;
    }

    private Long parseLong(String value) {
        return blank(value) == null ? null : Long.valueOf(value.trim());
    }

    private Long[] parseLongs(String value) {
        String text = blank(value);
        if (text == null) return new Long[0];
        return Arrays.stream(text.split(",")).map(String::trim).filter(item -> !item.isEmpty())
            .map(Long::valueOf).distinct().toArray(Long[]::new);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
