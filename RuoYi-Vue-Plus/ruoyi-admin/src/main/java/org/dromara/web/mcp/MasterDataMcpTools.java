/*
 * 文件用途：向受控智能体暴露织云 ERP 的主数据 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的主数据 MCP 工具能力。
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
 * 修改标记：ZY-ERP-20261121-MCP写工具确认契约；说明客户分类维护入口并补齐主数据写操作展示
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（MCP 工具契约测试覆盖全部命名写工具、客户分类维护提示及工具注册）
 * 测试范围与记录位置：McpToolContractTest；Maven Surefire 报告
 */
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    @McpTool(name = "customer_category_options", description = "查询客户分类树、当前人员可选择的分类及客户默认分类类型；本工具只查询。若需维护分类节点或分类体系，使用通用 category_create、category_update、category_delete 或 category_scheme_* 工具（需对应 system:category:* 权限，且工具须在当前目录可见）；需要 system:customer:list")
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
    @McpTool(name = "customer_set_status", description = "启用或停用客户；执行前读取并展示客户编码、名称、当前及目标状态，说明停用不删除历史引用并取得员工本次明确确认，需要 system:customer:edit")
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
    @McpTool(name = "company_create", description = "新增公司档案；body仅接受companyName、logoOssId、loginBgOssId、slogan、loginBgMode、nameColor、theme、isDefault；ID必须为十进制字符串，文件ID可用null清空；执行前展示公司名、文件、主题和默认状态并取得本次确认，需要 system:company:add")
    public R<?> companyCreate(@McpToolParam(description = "公司新增字段对象；companyName必填，其余字段规则见公司API输入矩阵。文件ID只接受十进制字符串或null") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:add");
        return companyController.add(companyBody(body, false));
    }

    /** 修改公司完整档案并复用 REST 控制器的数据校验和审计。 / Update a company through the existing validation and audit path. */
    @McpTool(name = "company_update", description = "完整替换公司可编辑字段；文件ID可为null表示清除，isDefault='1'会同时切换全局默认公司，'0'不取消当前默认；执行前展示完整变化和默认公司影响并取得本次确认，需要 system:company:edit")
    public R<?> companyUpdate(@McpToolParam(description = "完整公司字段对象；必须包含companyId、companyName、logoOssId、loginBgOssId、slogan、loginBgMode、nameColor、theme、status；isDefault可省略。详细空值见公司API输入矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:company:edit");
        return companyController.edit(companyBody(body, true));
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
    public R<?> orgList(@McpToolParam(description = "可选筛选对象；ID字段orgId/parentId/buildingId/leader须为十进制字符串，status为字符串0/1；省略或{}表示查询全部可见组织。详细字段见组织API矩阵", required = false) Map<String, Object> query) {
        StpUtil.checkPermission("system:org:list");
        SysOrgBo filter = orgBody(query, false, true);
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
    public R<?> orgCreate(@McpToolParam(description = "新增组织字段对象：parentId/orgCategory/buildingId/location/leader/phone/email/status可选，ID须用十进制字符串，orderNum须为JSON整数，status为字符串0/1；不接受客户端orgId。详细字段见组织API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:org:add");
        return orgController.add(orgBody(body, false, false));
    }

    /** 修改组织节点并复用已有的层级、负责人及关联数据校验。 / Update an organization with existing hierarchy, leader, and reference validation. */
    @McpTool(name = "org_update", description = "修改组织节点；执行前展示完整变化并取得确认，需要 system:org:edit")
    public R<?> orgUpdate(@McpToolParam(description = "完整替换组织字段对象，必须包含orgId、parentId、orgName、orgCategory、buildingId、location、orderNum、leader、phone、email、status；ID使用十进制字符串，parentId:null表示移到根；详细空值见组织API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:org:edit");
        return orgController.edit(orgBody(body, true, false));
    }

    /** 删除空组织节点；控制器负责阻止删除仍有下级或关联的节点。 / Delete an empty organization and rely on controller checks for remaining descendants or references. */
    @McpTool(name = "org_delete", description = "删除组织节点；执行前先读取并展示组织名称、完整上级路径及影响，说明存在子级、人员、岗位或管辖关系时服务端会拒绝，并取得员工本次明确确认，需要 system:org:remove")
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
        @McpToolParam(description = "可选岗位筛选对象；postId/orgId/belongOrgId用十进制字符串，status用字符串0/1；省略或{}不按对象字段筛选。详细字段见岗位API矩阵", required = false) Map<String, Object> query,
        @McpToolParam(description = "可选名称或编码关键词；省略或空白表示不按关键词筛选", required = false) String keyword,
        @McpToolParam(description = "可选页码；省略或小于1时按1处理", required = false) Integer pageNum,
        @McpToolParam(description = "可选每页条数；省略或小于1时按20处理，最大100", required = false) Integer pageSize) {
        StpUtil.checkPermission("system:post:list");
        SysPostBo filter = postBody(query, false, true);
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
    public R<?> postCreate(@McpToolParam(description = "岗位新增字段对象：orgId/postCode/postName/postSort必填；ID须为十进制字符串，postSort为JSON整数，status为字符串0/1。详细字段见岗位API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:post:add");
        return postController.add(postBody(body, false, false));
    }

    /** 修改岗位档案并保留岗位服务中的关联校验和审计逻辑。 / Update a position through its existing reference-validation and audit path. */
    @McpTool(name = "post_update", description = "修改岗位档案；执行前展示完整变化并取得确认，需要 system:post:edit")
    public R<?> postUpdate(@McpToolParam(description = "完整岗位替换对象，必须包含postId、orgId、belongOrgId、postCode、postName、postCategory、postSort、status、remark；ID使用十进制字符串，postSort为JSON整数。详细空值见岗位API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:post:edit");
        return postController.edit(postBody(body, true, false));
    }

    /** 切换岗位启停状态；员工关联时是否允许停用由控制器阻止。 / Change position status while preserving controller checks for assigned employees. */
    @McpTool(name = "post_set_status", description = "启用或停用岗位；执行前读取并展示岗位编码、名称、当前及目标状态，说明已分配员工时服务端会拒绝停用，并取得员工本次明确确认，需要 system:post:edit")
    public R<?> postSetStatus(
        @McpToolParam(description = "岗位ID，十进制字符串") String postId,
        @McpToolParam(description = "0启用、1停用") String status) {
        StpUtil.checkPermission("system:post:edit");
        if (!"0".equals(status) && !"1".equals(status)) return R.fail("岗位状态只能是0（启用）或1（停用）");
        return postController.changeStatus(requiredId(postId, "岗位ID"), Map.of("status", status));
    }

    /** 批量删除岗位；控制器负责校验岗位关联并记录操作。 / Delete multiple positions through controller reference checks and audit logging. */
    @McpTool(name = "post_delete", description = "批量删除岗位；执行前读取并逐项展示岗位编码、名称和受影响记录，取得员工本次明确确认；不得因引用校验失败擅自改为停用或解除关联，需要 system:post:remove")
    public R<?> postDelete(@McpToolParam(description = "岗位ID列表，逗号分隔的十进制字符串") String postIds) {
        StpUtil.checkPermission("system:post:remove");
        return postController.remove(parseLongs(postIds));
    }

    /** 返回岗位筛选器所需的组织树。 / Return the organization tree used by position filters. */
    @McpTool(name = "post_org_tree", description = "查询岗位筛选用组织树，需要 system:post:list")
    public R<?> postOrgTree(@McpToolParam(description = "可选组织筛选对象；ID字段用十进制字符串，status为字符串0/1；省略查询全部可见组织", required = false) Map<String, Object> query) {
        StpUtil.checkPermission("system:post:list");
        SysOrgBo filter = orgBody(query, false, true);
        filter.setStatus(McpInputValidator.optionalBinaryFlag(filter.getStatus(), "组织状态"));
        return postController.orgTree(filter);
    }

    // 建筑档案 building / Building master data.
    /** 按关键词、建筑用途和状态查询建筑。 / Search buildings by keyword, usage type, and status. */
    @McpTool(name = "building_list", description = "按关键词、用途和状态查询建筑档案，需要 system:building:list")
    public R<?> buildingList(
        @McpToolParam(description = "可选，建筑编码、名称或地址关键词；省略或空白表示不筛选", required = false) String keyword,
        @McpToolParam(description = "可选，建筑用途类别；省略表示全部用途", required = false) String buildingType,
        @McpToolParam(description = "可选建筑状态：0在用、1闲置、2维修；省略/空白表示全部状态", required = false) String status) {
        StpUtil.checkPermission("system:building:list");
        String statusFilter = optionalBuildingStatus(status);
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
    public R<?> buildingCreate(@McpToolParam(description = "新增建筑对象只接受buildingType/plantCode/plantName/floorInfo/landArea/buildingArea/address/builtYear/status/remark；plantName必填，不接受plantId；状态为0在用、1闲置、2维修。详细空值见建筑API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:add");
        return buildingController.add(buildingBody(body, false));
    }

    /** 修改建筑档案并复用已有更新校验及业务日志。 / Update a building through the existing validation and audit path. */
    @McpTool(name = "building_update", description = "修改建筑档案；执行前展示完整变化并取得确认，需要 system:building:edit")
    public R<?> buildingUpdate(@McpToolParam(description = "完整替换对象，必须提交plantId、buildingType、plantCode、plantName、floorInfo、landArea、buildingArea、address、builtYear、status、remark；plantId以字符串传入，状态为0/1/2。详细约束见建筑API矩阵") Map<String, Object> body) {
        StpUtil.checkPermission("system:building:edit");
        return buildingController.edit(buildingBody(body, true));
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

    /** 校验公司档案 Map 白名单和逐字段类型；文件引用 ID 严格使用字符串，避免 JSON 数字精度漂移。 / Validate company Map fields and require string-encoded file IDs. */
    Map<String, Object> companyBody(Map<String, Object> body, boolean updating) {
        if (body == null) throw new ServiceException("公司字段对象不能为空");
        Set<String> fields = updating
            ? Set.of("companyId", "companyName", "logoOssId", "loginBgOssId", "slogan", "loginBgMode", "nameColor", "theme", "status", "isDefault")
            : Set.of("companyName", "logoOssId", "loginBgOssId", "slogan", "loginBgMode", "nameColor", "theme", "isDefault");
        for (String key : body.keySet()) {
            if (!fields.contains(key)) throw new ServiceException("不支持的公司字段：" + key);
        }
        if (!updating && body.containsKey("companyId")) throw new ServiceException("新增公司不接受客户端指定companyId");
        Set<String> required = updating
            ? Set.of("companyId", "companyName", "logoOssId", "loginBgOssId", "slogan", "loginBgMode", "nameColor", "theme", "status")
            : Set.of("companyName");
        if (!body.keySet().containsAll(required)) throw new ServiceException("公司字段对象缺少必填字段");

        Map<String, Object> normalized = new LinkedHashMap<>(body);
        if (updating) normalized.put("companyId", mapRequiredId(body.get("companyId"), "companyId"));
        Object companyName = body.get("companyName");
        if (!(companyName instanceof String name) || name.isBlank() || name.length() > 100) {
            throw new ServiceException("companyName必须为1至100个字符的非空字符串");
        }
        for (String key : Set.of("logoOssId", "loginBgOssId")) {
            if (body.containsKey(key)) normalized.put(key, mapNullableId(body.get(key), key));
        }
        for (String key : Set.of("slogan", "theme")) {
            if (!body.containsKey(key)) continue;
            Object value = body.get(key);
            if (value != null && !(value instanceof String)) throw new ServiceException(key + "必须是字符串或null");
            if (value instanceof String text && text.length() > ("slogan".equals(key) ? 200 : 50)) {
                throw new ServiceException(key + "超过允许长度");
            }
        }
        if (body.containsKey("loginBgMode")) {
            Object value = body.get("loginBgMode");
            if (!(value instanceof String mode) || !Set.of("A", "B").contains(mode)) {
                throw new ServiceException("loginBgMode必须是字符串A或B");
            }
        }
        if (body.containsKey("nameColor")) {
            Object value = body.get("nameColor");
            if (value != null && (!(value instanceof String color) || color.length() > 20)) {
                throw new ServiceException("nameColor必须为不超过20个字符的字符串或null");
            }
        }
        if (body.containsKey("status")) {
            Object value = body.get("status");
            if (!(value instanceof String status) || !Set.of("0", "1").contains(status)) {
                throw new ServiceException("status必须是字符串0（启用）或1（停用）");
            }
        }
        if (body.containsKey("isDefault")) {
            Object value = body.get("isDefault");
            if (!(value instanceof String flag) || !Set.of("0", "1").contains(flag)) {
                throw new ServiceException("isDefault必须是字符串0或1");
            }
        }
        return normalized;
    }

    /** 文件引用可空；若提供非空值，必须为正十进制字符串。 / Validate nullable string-encoded file IDs. */
    private Object mapNullableId(Object raw, String field) {
        if (raw == null) return null;
        return mapRequiredId(raw, field);
    }

    /** 仅将十进制字符串 ID 转为 Long 供当前 JDBC Controller 使用。 / Convert only decimal string IDs for legacy JDBC controllers. */
    private Long mapRequiredId(Object raw, String field) {
        if (!(raw instanceof String value)) throw new ServiceException(field + "必须使用十进制字符串");
        return McpInputValidator.requirePositiveId(value, field);
    }

    /** 将组织 MCP Map 转为框架 DTO；ID 在转换前严格要求字符串，更新必须显式包含父级和完整可编辑字段。 / Validate organization IDs before DTO conversion. */
    SysOrgBo orgBody(Map<String, Object> body, boolean updating, boolean query) {
        if (body == null) {
            if (query) return new SysOrgBo();
            throw new ServiceException("组织字段对象不能为空");
        }
        Set<String> fields = Set.of("orgId", "parentId", "orgName", "orgCategory", "buildingId", "location", "orderNum", "leader", "phone", "email", "status");
        if (!query && !updating && body.containsKey("orgId")) throw new ServiceException("新增组织不接受客户端指定orgId");
        for (String key : body.keySet()) {
            if (!fields.contains(key)) throw new ServiceException("不支持的组织字段：" + key);
        }
        Set<String> required = updating
            ? fields
            : query ? Set.of() : Set.of("orgName", "orderNum");
        if (!body.keySet().containsAll(required)) throw new ServiceException("组织字段对象缺少必填字段");

        SysOrgBo result = new SysOrgBo();
        if (body.containsKey("orgId")) result.setOrgId(mapId(body, "orgId", false, query));
        if (body.containsKey("parentId")) result.setParentId(mapId(body, "parentId", true, query));
        if (body.containsKey("buildingId")) result.setBuildingId(mapId(body, "buildingId", false, query));
        if (body.containsKey("leader")) result.setLeader(mapId(body, "leader", false, query));
        if (body.containsKey("orgName")) result.setOrgName(mapText(body, "orgName", !query));
        if (body.containsKey("orgCategory")) result.setOrgCategory(mapText(body, "orgCategory", false));
        if (body.containsKey("location")) result.setLocation(mapText(body, "location", false));
        if (body.containsKey("phone")) result.setPhone(mapText(body, "phone", false));
        if (body.containsKey("email")) result.setEmail(mapText(body, "email", false));
        if (body.containsKey("status")) {
            Object raw = body.get("status");
            if (query) result.setStatus(McpInputValidator.optionalBinaryFlag(raw, "组织状态"));
            else if (!(raw instanceof String status) || !Set.of("0", "1").contains(status)) throw new ServiceException("status必须是字符串0或1");
            else result.setStatus(status);
        }
        if (body.containsKey("orderNum")) result.setOrderNum(mapInteger(body, "orderNum", !query));
        return result;
    }

    /** 将岗位 MCP Map 转为框架 DTO；岗位和组织 ID 都必须是十进制字符串。 / Validate position IDs before DTO conversion. */
    SysPostBo postBody(Map<String, Object> body, boolean updating, boolean query) {
        if (body == null) {
            if (query) return new SysPostBo();
            throw new ServiceException("岗位字段对象不能为空");
        }
        Set<String> fields = Set.of("postId", "orgId", "belongOrgId", "postCode", "postName", "postCategory", "postSort", "status", "remark");
        if (!query && !updating && body.containsKey("postId")) throw new ServiceException("新增岗位不接受客户端指定postId");
        for (String key : body.keySet()) {
            if (!fields.contains(key)) throw new ServiceException("不支持的岗位字段：" + key);
        }
        Set<String> required = updating
            ? fields
            : query ? Set.of() : Set.of("orgId", "postCode", "postName", "postSort");
        if (!body.keySet().containsAll(required)) throw new ServiceException("岗位字段对象缺少必填字段");

        SysPostBo result = new SysPostBo();
        if (body.containsKey("postId")) result.setPostId(mapId(body, "postId", false, query));
        if (body.containsKey("orgId")) result.setOrgId(mapId(body, "orgId", false, query));
        if (body.containsKey("belongOrgId")) result.setBelongOrgId(mapId(body, "belongOrgId", false, query));
        if (body.containsKey("postCode")) result.setPostCode(mapText(body, "postCode", !query));
        if (body.containsKey("postName")) result.setPostName(mapText(body, "postName", !query));
        if (body.containsKey("postCategory")) result.setPostCategory(mapText(body, "postCategory", false));
        if (body.containsKey("remark")) result.setRemark(mapText(body, "remark", false));
        if (body.containsKey("status")) {
            Object raw = body.get("status");
            if (query) result.setStatus(McpInputValidator.optionalBinaryFlag(raw, "岗位状态"));
            else if (!(raw instanceof String status) || !Set.of("0", "1").contains(status)) throw new ServiceException("status必须是字符串0或1");
            else result.setStatus(status);
        }
        if (body.containsKey("postSort")) result.setPostSort(mapInteger(body, "postSort", !query));
        return result;
    }

    /** 转换对象中的可空 ID；parentId 明确允许字符串0表示根，查询时空白表示无筛选。 / Parse mapped ID fields. */
    private Long mapId(Map<String, Object> body, String key, boolean allowZero, boolean query) {
        Object raw = body.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String text)) throw new ServiceException(key + "必须使用十进制字符串");
        if (query && text.isBlank()) return null;
        String value = text.trim();
        if (allowZero && "0".equals(value)) return 0L;
        return McpInputValidator.requirePositiveId(value, key);
    }

    /** 校验字段是否为文本，必填字段拒绝null和空白。 / Validate mapped string values. */
    private String mapText(Map<String, Object> body, String key, boolean nonblank) {
        Object raw = body.get(key);
        if (raw == null) {
            if (nonblank) throw new ServiceException(key + "不能为空");
            return null;
        }
        if (!(raw instanceof String value)) throw new ServiceException(key + "必须是字符串");
        if (nonblank && value.isBlank()) throw new ServiceException(key + "不能为空");
        return value;
    }

    /** 校验排序字段只接受JSON整数；可选查询字段未传则返回null。 / Parse JSON integer fields without string coercion. */
    private Integer mapInteger(Map<String, Object> body, String key, boolean required) {
        Object raw = body.get(key);
        if (raw == null) {
            if (required) throw new ServiceException(key + "必须为整数");
            return null;
        }
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) throw new ServiceException(key + "必须为JSON整数");
        return number.intValue();
    }

    /** 把字符串ID适配回当前建筑控制器要求的Number，同时校验字段白名单、JSON类型和完整更新对象。 / Adapt string IDs and validate building Map shapes. */
    Map<String, Object> buildingBody(Map<String, Object> body, boolean updating) {
        if (body == null) throw new ServiceException("建筑字段对象不能为空");
        Set<String> fields = updating
            ? Set.of("plantId", "buildingType", "plantCode", "plantName", "floorInfo", "landArea", "buildingArea", "address", "builtYear", "status", "remark")
            : Set.of("buildingType", "plantCode", "plantName", "floorInfo", "landArea", "buildingArea", "address", "builtYear", "status", "remark");
        for (String key : body.keySet()) {
            if (!fields.contains(key)) throw new ServiceException("不支持的建筑字段：" + key);
        }
        if (updating && !body.keySet().containsAll(fields)) throw new ServiceException("修改建筑必须提交完整字段");
        if (!(body.get("plantName") instanceof String name) || name.isBlank()) throw new ServiceException("plantName必须是非空字符串");
        Map<String, Object> normalized = new HashMap<>(body);
        if (updating) normalized.put("plantId", McpInputValidator.requirePositiveId(body.get("plantId"), "建筑ID"));
        for (String key : Set.of("buildingType", "plantCode", "floorInfo", "address", "remark")) {
            if (body.containsKey(key) && body.get(key) != null && !(body.get(key) instanceof String)) {
                throw new ServiceException(key + "必须是字符串");
            }
        }
        for (String key : Set.of("landArea", "buildingArea")) {
            Object value = body.get(key);
            if (value != null && (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))) {
                throw new ServiceException(key + "必须是JSON数值或null");
            }
        }
        Object year = body.get("builtYear");
        if (year != null && (!(year instanceof Number number) || number.doubleValue() != number.intValue())) {
            throw new ServiceException("builtYear必须是JSON整数或null");
        }
        Object status = body.get("status");
        if (body.containsKey("status") && status == null) throw new ServiceException("status不能为null");
        if (status != null && (!(status instanceof String flag) || !Set.of("0", "1", "2").contains(flag))) {
            throw new ServiceException("status必须是字符串0、1或2");
        }
        if (updating && status == null) throw new ServiceException("status不能为null");
        return normalized;
    }

    /** 建筑状态保留底层字典定义的在用、闲置、维修三种筛选值。 / Validate all persisted building statuses. */
    private String optionalBuildingStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim();
        if (!Set.of("0", "1", "2").contains(value)) throw new ServiceException("建筑状态只能是0（在用）、1（闲置）或2（维修）");
        return value;
    }
}
