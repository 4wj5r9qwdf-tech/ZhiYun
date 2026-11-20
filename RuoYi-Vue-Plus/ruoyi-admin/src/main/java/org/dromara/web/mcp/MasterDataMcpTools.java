package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
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
 * MCP tools for accepted master data. Controller permission annotations run only through MVC AOP,
 * so each MCP entry point checks the same permission explicitly and delegates business rules to its controller.
 */
@Component
@RequiredArgsConstructor
public class MasterDataMcpTools {

    private final CustomerController customerController;
    private final CompanyController companyController;
    private final SysOrgController orgController;
    private final SysPostController postController;
    private final BuildingController buildingController;

    // 客户档案 customer_master；分类筛选ID需先从 customer_category_options 读取，不能猜测。
    // Customer master; read selectable category IDs from customer_category_options instead of guessing.
    /** 按筛选条件分页查询客户，并将缺省文本筛选归一化，避免 null 进入客户查询。 / Query customers with null-safe optional filters and the caller's category scope. */
    @McpTool(name = "customer_list", description = "分页查询客户档案；联系方式在详情中按当前员工权限脱敏。分类筛选先读取 customer_category_options；categoryId 与 schemeId 不能同时传，需要 system:customer:list")
    public R<?> customerList(
        @McpToolParam(description = "可选，客户编码/名称/简称关键词；省略或空白表示不筛选", required = false) String keyword,
        @McpToolParam(description = "可选，0启用、1停用；省略表示全部状态", required = false) String status,
        @McpToolParam(description = "可选，业务员用户ID十进制字符串；省略表示不按业务员筛选", required = false) String salesId,
        @McpToolParam(description = "可选，客户标签ID十进制字符串；省略表示不按标签筛选", required = false) String tagId,
        @McpToolParam(description = "可选，客户分类ID十进制字符串；传0表示未分类；其他值必须来自 customer_category_options 的可选节点；省略表示不按单个分类筛选", required = false) String categoryId,
        @McpToolParam(description = "可选，分类体系ID十进制字符串；用于筛选该分类类型下所有客户；必须与 categoryId 二选一，并优先从 customer_category_options 的 defaultSchemeId 或 rows.schemeId 读取", required = false) String schemeId,
        @McpToolParam(description = "可选页码；省略或小于1时按1处理", required = false) Integer pageNum,
        @McpToolParam(description = "可选每页条数；省略或小于1时按20处理，最大100", required = false) Integer pageSize) {
        StpUtil.checkPermission("system:customer:list");
        Long categoryFilter = McpInputValidator.optionalNonNegativeId(categoryId, "客户分类ID");
        Long schemeFilter = McpInputValidator.optionalPositiveId(schemeId, "客户分类类型ID");
        Long salesFilter = McpInputValidator.optionalPositiveId(salesId, "业务员ID");
        Long tagFilter = McpInputValidator.optionalPositiveId(tagId, "客户标签ID");
        if (categoryFilter != null && schemeFilter != null) return R.fail("categoryId 和 schemeId 只能选择一个筛选");
        String statusFilter = McpInputValidator.optionalBinaryFlag(status, "客户状态");
        return customerController.list(filterText(keyword), filterText(statusFilter), salesFilter, tagFilter, categoryFilter, schemeFilter,
            pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
    }

    /** 查询当前人员可用的客户分类，并返回客户默认分类类型；不授予额外分类权限。 / Return authorized category choices and the configured default scheme. */
    @McpTool(name = "customer_category_options", description = "查询客户分类树、当前人员可选择的分类及客户默认分类类型；受人员分类权限过滤，需要 system:customer:list")
    public R<?> customerCategoryOptions() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.categoryOptions();
    }

    /** 返回客户总数及标签聚合，供客户列表筛选和统计展示使用。 / Return customer and tag counts for list summaries. */
    @McpTool(name = "customer_category_counts", description = "查询客户总数和标签分类数量，需要 system:customer:list")
    public R<?> customerCategoryCounts() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.categoryCounts();
    }

    /** 读取客户主档及关联联系人、地址和标签；敏感联系方式由控制器按权限脱敏。 / Read a customer and related contacts, addresses, and tags with permission-aware masking. */
    @McpTool(name = "customer_get", description = "查询客户详情、联系人、地址和标签；普通查询权限下联系方式会脱敏，需要 system:customer:list")
    public R<?> customerGet(@McpToolParam(description = "客户ID，十进制字符串") String customerId) {
        StpUtil.checkPermission("system:customer:list");
        return customerController.detail(requiredId(customerId, "客户ID"));
    }

    /** 查询客户名称重复数量，仅用于提示；排除当前记录以支持编辑页校验。 / Check duplicate-name count without blocking writes, optionally excluding the edited record. */
    @McpTool(name = "customer_check_duplicate", description = "按客户名称检查同名数量；仅提示，不阻止保存，需要 system:customer:list")
    public R<?> customerCheckDuplicate(
        @McpToolParam(description = "待检查客户名称") String name,
        @McpToolParam(description = "可选，当前客户ID十进制字符串；新增时省略，省略按0处理", required = false) String excludeId) {
        StpUtil.checkPermission("system:customer:list");
        Long excludedId = McpInputValidator.optionalPositiveId(excludeId, "排除客户ID");
        return customerController.duplicates(name, excludedId == null ? 0L : excludedId);
    }

    /** 提供客户业务员筛选项，不暴露人员敏感字段。 / Return salesperson filter options without sensitive personnel fields. */
    @McpTool(name = "customer_sales_options", description = "查询业务员及组织、岗位、小组筛选选项，不返回人员敏感信息，需要 system:customer:list")
    public R<?> customerSalesOptions() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.salesOptions();
    }

    /** 返回可被其他业务单据引用的启用客户选项。 / Return active customers available for downstream business documents. */
    @McpTool(name = "customer_options", description = "查询可供新业务引用的启用客户，需要 system:customer:list")
    public R<?> customerOptions() {
        StpUtil.checkPermission("system:customer:list");
        return customerController.options();
    }

    /** 新增客户档案并保留分类、联系人、地址和标签契约；由 REST 控制器执行数据范围和业务审计。 / Create a customer through the REST path so category scope and audit rules remain enforced. */
    @McpTool(name = "customer_create", description = "新增客户档案；分类ID先从 customer_category_options 读取；执行前必须展示主档、分类、联系人、地址及标签并取得员工确认，需要 system:customer:add")
    public R<?> customerCreate(@McpToolParam(description = "客户字段对象：customer_name、customer_short_name、unified_social_credit_code、country_region、country_region_code、可选categoryId（十进制分类节点ID或空字符串）、sales_representative_id、remark、contacts、addresses、tagIds") Map<String, Object> body) {
        StpUtil.checkPermission("system:customer:add");
        return customerController.add(body);
    }

    /** 按客户档案完整结构更新；未提供分类保留原值，空分类按接口约定清除。 / Update the customer aggregate while preserving or clearing category according to the request contract. */
    @McpTool(name = "customer_update", description = "修改客户档案；分类ID先从 customer_category_options 读取；按完整主档、分类、联系人、地址和标签集合提交，执行前展示变化并取得确认，需要 system:customer:edit")
    public R<?> customerUpdate(
        @McpToolParam(description = "客户ID，十进制字符串") String customerId,
        @McpToolParam(description = "客户字段对象，结构同 customer_create；省略categoryId保留现分类，空字符串清空分类，不能设置无权分类") Map<String, Object> body) {
        StpUtil.checkPermission("system:customer:edit");
        return customerController.edit(requiredId(customerId, "客户ID"), body);
    }

    /** 切换客户启停状态，历史单据引用由原档案关系保留。 / Change customer availability while retaining historical references. */
    @McpTool(name = "customer_set_status", description = "启用或停用客户；停用不删除历史引用，执行前取得确认，需要 system:customer:edit")
    public R<?> customerSetStatus(
        @McpToolParam(description = "客户ID，十进制字符串") String customerId,
        @McpToolParam(description = "0启用、1停用") String status) {
        StpUtil.checkPermission("system:customer:edit");
        if (!"0".equals(status) && !"1".equals(status)) return R.fail("客户状态只能是0（启用）或1（停用）");
        return customerController.status(requiredId(customerId, "客户ID"), Map.of("status", status));
    }

    /** 删除未被业务引用的客户，引用校验和审计由客户控制器统一执行。 / Delete an unreferenced customer through the controller's reference checks and audit path. */
    @McpTool(name = "customer_delete", description = "删除未被业务引用的客户档案；不可逆，执行前展示客户编码和名称并取得本次确认，需要 system:customer:remove")
    public R<?> customerDelete(@McpToolParam(description = "客户ID，十进制字符串") String customerId) {
        StpUtil.checkPermission("system:customer:remove");
        return customerController.remove(requiredId(customerId, "客户ID"));
    }

    // 公司档案 company / Company master data.
    /** 分页查询公司档案，并限制通用分页参数范围。 / Page company records with bounded pagination. */
    @McpTool(name = "company_list", description = "分页查询公司档案，需要 system:company:list")
    public R<?> companyList(
        @McpToolParam(description = "可选页码；省略或小于1时按1处理", required = false) Integer pageNum,
        @McpToolParam(description = "可选每页条数；省略或小于1时按20处理，最大100", required = false) Integer pageSize) {
        StpUtil.checkPermission("system:company:list");
        return companyController.list(pageQuery(pageNum, pageSize));
    }

    /** 读取单个公司档案详情。 / Read one company record by its identifier. */
    @McpTool(name = "company_get", description = "按ID查询公司档案，需要 system:company:query")
    public R<?> companyGet(@McpToolParam(description = "公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:query");
        return companyController.getInfo(requiredId(companyId, "公司ID"));
    }

    /** 查询 OSS 文件是否被公司 Logo 或登录背景引用，供删除文件前核对。 / Find company references to OSS files before file deletion. */
    @McpTool(name = "company_file_usage", description = "按OSS文件ID查询公司logo/登录背景引用，最多100个，需要 system:company:list")
    public R<?> companyFileUsage(@McpToolParam(description = "OSS文件ID，英文逗号分隔的十进制字符串") String ids) {
        StpUtil.checkPermission("system:company:list");
        String normalizedIds = blank(ids);
        if (normalizedIds != null && normalizedIds.split(",", -1).length > 100) return R.fail("每次最多查询100个OSS文件ID");
        Long[] fileIds = parseLongs(ids);
        return companyController.fileUsage(fileIds);
    }

    /** 新增公司档案；文件与默认公司约束由公司控制器处理。 / Create a company while preserving controller-managed file and default-company rules. */
    @McpTool(name = "company_create", description = "新增公司档案；执行前展示公司名、文件、主题和默认状态并取得确认，需要 system:company:add")
    public R<?> companyCreate(@McpToolParam(description = "公司字段对象：companyName、logoOssId、loginBgOssId、slogan、loginBgMode、nameColor、theme、isDefault") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:add");
        return companyController.add(body);
    }

    /** 修改公司完整档案并复用 REST 控制器的数据校验和审计。 / Update a company through the existing validation and audit path. */
    @McpTool(name = "company_update", description = "修改公司档案；执行前展示完整变更并取得确认，需要 system:company:edit")
    public R<?> companyUpdate(@McpToolParam(description = "完整公司字段对象，必须包含companyId") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:edit");
        return companyController.edit(body);
    }

    /** 切换默认展示公司，由控制器在同一业务操作中撤销旧默认项。 / Switch the default company and let the controller clear the previous default atomically. */
    @McpTool(name = "company_set_default", description = "切换默认展示公司，会同步取消旧默认项；执行前展示影响并取得确认，需要 system:company:edit")
    public R<?> companySetDefault(@McpToolParam(description = "目标公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:edit");
        return companyController.setDefault(requiredId(companyId, "公司ID"));
    }

    /** 软删除公司档案并复用控制器关联检查。 / Soft-delete a company through the controller's reference checks. */
    @McpTool(name = "company_delete", description = "软删除公司档案；执行前展示公司身份并取得确认，需要 system:company:remove")
    public R<?> companyDelete(@McpToolParam(description = "公司ID，十进制字符串") String companyId) {
        StpUtil.checkPermission("system:company:remove");
        return companyController.remove(requiredId(companyId, "公司ID"));
    }

    // 组织架构 org / Organization hierarchy.
    /** 按筛选对象查询调用人可见的组织节点。 / Query organization nodes visible to the caller. */
    @McpTool(name = "org_list", description = "按条件查询组织节点，需要 system:org:list")
    public R<?> orgList(@McpToolParam(description = "可选组织筛选对象，可传orgName、status、orgCategory等字段；省略表示查询全部可见组织", required = false) SysOrgBo query) {
        StpUtil.checkPermission("system:org:list");
        SysOrgBo filter = query == null ? new SysOrgBo() : query;
        filter.setStatus(McpInputValidator.optionalBinaryFlag(filter.getStatus(), "组织状态"));
        return orgController.list(filter);
    }

    /** 查询组织详情，并由控制器继续执行数据范围校验。 / Read organization details with controller-enforced data scope. */
    @McpTool(name = "org_get", description = "按ID查询组织节点详情并检查数据范围，需要 system:org:query")
    public R<?> orgGet(@McpToolParam(description = "组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:query");
        return orgController.getInfo(requiredId(orgId, "组织ID"));
    }

    /** 返回组织选择项；未传 ID 时使用控制器定义的完整可选集合。 / Return organization options, optionally restricted to supplied IDs. */
    @McpTool(name = "org_options", description = "按ID查询组织选项，需要 system:org:query")
    public R<?> orgOptions(@McpToolParam(description = "可选，组织ID十进制字符串，多个用英文逗号分隔；省略返回全部可选组织", required = false) String orgIds) {
        StpUtil.checkPermission("system:org:query");
        Long[] ids = blank(orgIds) == null ? null : parseLongs(orgIds);
        return orgController.optionselect(ids);
    }

    /** 新增组织节点；负责人和上下级规则由组织控制器校验。 / Create an organization node with controller-enforced hierarchy and leader rules. */
    @McpTool(name = "org_create", description = "新增组织节点；执行前展示上级、类别、建筑和负责人并取得确认，需要 system:org:add")
    public R<?> orgCreate(@McpToolParam(description = "组织字段对象：parentId、orgName、orgCategory、buildingId、location、orderNum、leader（负责人用户ID）、phone（部门电话）、email（部门邮箱）、status") SysOrgBo body) {
        StpUtil.checkPermission("system:org:add");
        return orgController.add(body);
    }

    /** 修改组织节点并复用已有的层级、负责人及关联数据校验。 / Update an organization with existing hierarchy, leader, and reference validation. */
    @McpTool(name = "org_update", description = "修改组织节点；执行前展示完整变化并取得确认，需要 system:org:edit")
    public R<?> orgUpdate(@McpToolParam(description = "完整组织字段对象，必须包含orgId；leader为负责人用户ID，phone为部门电话，email为部门邮箱") SysOrgBo body) {
        StpUtil.checkPermission("system:org:edit");
        return orgController.edit(body);
    }

    /** 删除空组织节点；控制器负责阻止删除仍有下级或关联的节点。 / Delete an empty organization and rely on controller checks for remaining descendants or references. */
    @McpTool(name = "org_delete", description = "删除无子级、无人员、无岗位且未被管辖的组织；执行前取得本次确认，需要 system:org:remove")
    public R<?> orgDelete(@McpToolParam(description = "组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:remove");
        return orgController.remove(requiredId(orgId, "组织ID"));
    }

    /** 查询组织当前管辖关系，不改变组织树本身。 / Read an organization's managed-organization links without changing its hierarchy. */
    @McpTool(name = "org_manages_get", description = "查询某组织当前管辖的组织ID，需要 system:org:query")
    public R<?> orgManagesGet(@McpToolParam(description = "管辖方组织ID，十进制字符串") String orgId) {
        StpUtil.checkPermission("system:org:query");
        return orgController.listManages(requiredId(orgId, "组织ID"));
    }

    /** 全量替换管辖关系；空列表代表清空，不能把它当作“未修改”。 / Replace all managed-organization links; an empty list explicitly clears them. */
    @McpTool(name = "org_manages_set", description = "全量替换某组织的管辖关系；空列表会清空，执行前展示旧/新名单并取得确认，需要 system:org:edit")
    public R<?> orgManagesSet(
        @McpToolParam(description = "管辖方组织ID，十进制字符串") String orgId,
        @McpToolParam(description = "最终被管辖组织ID列表，全部为十进制字符串；空列表表示清空") List<String> managedOrgIds) {
        StpUtil.checkPermission("system:org:edit");
        if (managedOrgIds == null) return R.fail("请明确传入 managedOrgIds；空数组表示清空管辖关系");
        List<Long> ids = managedOrgIds.stream().map(id -> requiredId(id, "被管辖组织ID")).distinct().toList();
        return orgController.updateManages(requiredId(orgId, "管辖方组织ID"), ids);
    }

    /** 汇总返回各组织直接管辖信息，用于组织管理页面概况。 / Return managed-organization counts and names for organization summaries. */
    @McpTool(name = "org_manage_counts", description = "查询各组织的被管辖数量和名称，需要 system:org:query")
    public R<?> orgManageCounts() {
        StpUtil.checkPermission("system:org:query");
        return orgController.manageCounts();
    }

    // 岗位管理 post / Job positions.
    /** 按筛选条件分页查询岗位，并统一限制分页大小。 / Search positions with bounded pagination. */
    @McpTool(name = "post_list", description = "分页查询岗位并支持名称/编码关键词，需要 system:post:list")
    public R<?> postList(
        @McpToolParam(description = "可选岗位筛选对象，可传postName、postCode、orgId、status等；省略表示不按对象字段筛选", required = false) SysPostBo query,
        @McpToolParam(description = "可选名称或编码关键词；省略或空白表示不按关键词筛选", required = false) String keyword,
        @McpToolParam(description = "可选页码；省略或小于1时按1处理", required = false) Integer pageNum,
        @McpToolParam(description = "可选每页条数；省略或小于1时按20处理，最大100", required = false) Integer pageSize) {
        StpUtil.checkPermission("system:post:list");
        SysPostBo filter = query == null ? new SysPostBo() : query;
        filter.setStatus(McpInputValidator.optionalBinaryFlag(filter.getStatus(), "岗位状态"));
        return postController.list(filter, pageQuery(pageNum, pageSize), keyword);
    }

    /** 查询岗位详情。 / Read one job-position record by identifier. */
    @McpTool(name = "post_get", description = "按ID查询岗位详情，需要 system:post:query")
    public R<?> postGet(@McpToolParam(description = "岗位ID，十进制字符串") String postId) {
        StpUtil.checkPermission("system:post:query");
        return postController.getInfo(requiredId(postId, "岗位ID"));
    }

    /** 返回岗位选择项，可按组织或岗位 ID 缩小结果集。 / Return position options optionally filtered by organization or position IDs. */
    @McpTool(name = "post_options", description = "查询岗位选择项，可按组织或岗位ID过滤，需要 system:post:query")
    public R<?> postOptions(
        @McpToolParam(description = "可选，岗位ID十进制字符串，多个用英文逗号分隔；省略不按岗位ID筛选", required = false) String postIds,
        @McpToolParam(description = "可选，组织ID十进制字符串；省略不按组织筛选", required = false) String orgId) {
        StpUtil.checkPermission("system:post:query");
        return postController.optionselect(blank(postIds) == null ? null : parseLongs(postIds), parseLong(orgId));
    }

    /** 新增岗位；编码唯一性和归属组织规则由岗位控制器验证。 / Create a position with controller-enforced code and organization rules. */
    @McpTool(name = "post_create", description = "新增岗位；执行前展示岗位编码、名称、归属组织和权限配置并取得确认，需要 system:post:add")
    public R<?> postCreate(@McpToolParam(description = "岗位字段对象：orgId、belongOrgId、postCode、postName、postCategory、postSort、status、remark") SysPostBo body) {
        StpUtil.checkPermission("system:post:add");
        return postController.add(body);
    }

    /** 修改岗位档案并保留岗位服务中的关联校验和审计逻辑。 / Update a position through its existing reference-validation and audit path. */
    @McpTool(name = "post_update", description = "修改岗位档案；执行前展示完整变化并取得确认，需要 system:post:edit")
    public R<?> postUpdate(@McpToolParam(description = "完整岗位字段对象，必须包含postId") SysPostBo body) {
        StpUtil.checkPermission("system:post:edit");
        return postController.edit(body);
    }

    /** 切换岗位启停状态；员工关联时是否允许停用由控制器阻止。 / Change position status while preserving controller checks for assigned employees. */
    @McpTool(name = "post_set_status", description = "启用或停用岗位；已分配员工时不可停用，执行前取得确认，需要 system:post:edit")
    public R<?> postSetStatus(
        @McpToolParam(description = "岗位ID，十进制字符串") String postId,
        @McpToolParam(description = "0启用、1停用") String status) {
        StpUtil.checkPermission("system:post:edit");
        if (!"0".equals(status) && !"1".equals(status)) return R.fail("岗位状态只能是0（启用）或1（停用）");
        return postController.changeStatus(requiredId(postId, "岗位ID"), Map.of("status", status));
    }

    /** 批量删除岗位；控制器负责校验岗位关联并记录操作。 / Delete multiple positions through controller reference checks and audit logging. */
    @McpTool(name = "post_delete", description = "批量删除岗位；执行前列明岗位编码和名称并取得确认，需要 system:post:remove")
    public R<?> postDelete(@McpToolParam(description = "岗位ID列表，逗号分隔的十进制字符串") String postIds) {
        StpUtil.checkPermission("system:post:remove");
        return postController.remove(parseLongs(postIds));
    }

    /** 返回岗位筛选器所需的组织树。 / Return the organization tree used by position filters. */
    @McpTool(name = "post_org_tree", description = "查询岗位筛选用组织树，需要 system:post:list")
    public R<?> postOrgTree(@McpToolParam(description = "可选组织筛选对象，可传orgName、status、orgCategory等；省略表示查询全部可见组织", required = false) SysOrgBo query) {
        StpUtil.checkPermission("system:post:list");
        SysOrgBo filter = query == null ? new SysOrgBo() : query;
        filter.setStatus(McpInputValidator.optionalBinaryFlag(filter.getStatus(), "组织状态"));
        return postController.orgTree(filter);
    }

    // 建筑档案 building / Building master data.
    /** 按关键词、建筑用途和状态查询建筑。 / Search buildings by keyword, usage type, and status. */
    @McpTool(name = "building_list", description = "按关键词、用途和状态查询建筑档案，需要 system:building:list")
    public R<?> buildingList(
        @McpToolParam(description = "可选，建筑编码、名称或地址关键词；省略或空白表示不筛选", required = false) String keyword,
        @McpToolParam(description = "可选，建筑用途类别；省略表示全部用途", required = false) String buildingType,
        @McpToolParam(description = "可选，0启用、1停用；省略表示全部状态", required = false) String status) {
        StpUtil.checkPermission("system:building:list");
        String statusFilter = McpInputValidator.optionalBinaryFlag(status, "建筑状态");
        return buildingController.list(blank(keyword), blank(buildingType), blank(statusFilter));
    }

    /** 查询单个建筑档案详情。 / Read one building record by identifier. */
    @McpTool(name = "building_get", description = "按ID查询建筑档案，需要 system:building:query")
    public R<?> buildingGet(@McpToolParam(description = "建筑ID，十进制字符串") String plantId) {
        StpUtil.checkPermission("system:building:query");
        return buildingController.getInfo(requiredId(plantId, "建筑ID"));
    }

    /** 新增建筑档案；编码、面积和用途校验由建筑控制器执行。 / Create a building with controller-enforced code, area, and usage validation. */
    @McpTool(name = "building_create", description = "新增建筑档案；执行前展示建筑用途、编码、名称和面积信息并取得确认，需要 system:building:add")
    public R<?> buildingCreate(@McpToolParam(description = "建筑字段对象：buildingType、plantCode、plantName、floorInfo、landArea、buildingArea、address、builtYear、status、remark") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:add");
        return buildingController.add(body);
    }

    /** 修改建筑档案并复用已有更新校验及业务日志。 / Update a building through the existing validation and audit path. */
    @McpTool(name = "building_update", description = "修改建筑档案；执行前展示完整变化并取得确认，需要 system:building:edit")
    public R<?> buildingUpdate(@McpToolParam(description = "完整建筑字段对象，必须包含plantId") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:edit");
        return buildingController.edit(body);
    }

    /** 软删除未被组织节点引用的建筑；引用校验交由建筑控制器执行。 / Soft-delete an unreferenced building through controller reference checks. */
    @McpTool(name = "building_delete", description = "软删除未关联组织节点的建筑；执行前展示建筑身份并取得确认，需要 system:building:remove")
    public R<?> buildingDelete(@McpToolParam(description = "建筑ID，十进制字符串") String plantId) {
        StpUtil.checkPermission("system:building:remove");
        return buildingController.remove(requiredId(plantId, "建筑ID"));
    }

    /** 生成安全分页参数，缺省回退到第1页/20条并将最大页大小限制为100。 / Build bounded paging defaults to prevent unbounded MCP queries. */
    private PageQuery pageQuery(Integer pageNum, Integer pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum == null || pageNum < 1 ? 1 : pageNum);
        query.setPageSize(pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100));
        return query;
    }

    /** 将可选十进制 ID 解析为 Long；空白表示未提供，非空非法值返回明确的参数错误。 / Parse an optional decimal ID, mapping blanks to null and rejecting malformed values. */
    private Long parseLong(String value) {
        String normalized = blank(value);
        return normalized == null ? null : requiredId(normalized, "筛选ID");
    }

    /** 解析逗号分隔的 ID 列表、去重并保持输入顺序。 / Parse comma-separated IDs, removing duplicates while preserving order. */
    private Long[] parseLongs(String value) {
        String text = blank(value);
        if (text == null) return new Long[0];
        return Arrays.stream(text.split(",", -1)).map(String::trim)
            .map(item -> requiredId(item, "ID列表成员")).distinct().toArray(Long[]::new);
    }

    /** 解析必填正十进制主键，拒绝空值、符号、非数字及 Long 溢出。 / Parse a required positive decimal ID and reject malformed or out-of-range values. */
    private Long requiredId(String value, String field) {
        return McpInputValidator.requirePositiveId(value, field);
    }

    /** 将 null、空串和纯空格统一转换为 null，其余文本去除首尾空格。 / Normalize blank text to null and trim nonblank values. */
    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** MCP 字符串筛选参数即使未提供，也向业务查询传空串，避免 null 进入筛选逻辑。 / Normalize omitted MCP text filters to empty strings before invoking business queries. */
    private String filterText(String value) {
        return value == null ? "" : value.trim();
    }
}
