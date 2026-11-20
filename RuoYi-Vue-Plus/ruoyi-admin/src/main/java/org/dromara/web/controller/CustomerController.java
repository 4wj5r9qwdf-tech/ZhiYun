/*
 * 文件用途：为织云 ERP 的CustomerController提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的CustomerController提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 客户主数据：编号由数据库生成；主档、联系人、地址和通用标签关联在同一事务内保存。 / Customer master data; the database generates IDs and the master record, contacts, addresses, and tags are saved transactionally. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/customer")
public class CustomerController {
    private final JdbcTemplate jdbc;
    private final org.dromara.web.service.CategoryAccessService categories;
    private static final String CATEGORY_KEY = "sales.customer.defaultCategoryScheme";
    private static final String SELECT = "SELECT c.*, c.category_id::text AS \"categoryId\", bc.category_name AS \"categoryName\", c.customer_id AS id, u.nick_name AS sales_name, COALESCE(cr.name_zh,c.country_region) AS country_display FROM customer_master c LEFT JOIN base_category bc ON bc.category_id=c.category_id LEFT JOIN sys_user u ON u.user_id=c.sales_representative_id LEFT JOIN sys_country_region cr ON cr.country_code=c.country_region_code ";

    /** 分页查询支持客户分类和人员分类权限；未分类客户仅对未受限范围的人员可见。 / Enforce employee category scope for customer list results. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/list")
    public R<Map<String,Object>> list(@RequestParam(defaultValue="") String keyword,
        @RequestParam(defaultValue="") String status, @RequestParam(required=false) Long salesId,
        @RequestParam(required=false) Long tagId, @RequestParam(required=false) Long categoryId,
        @RequestParam(required=false) Long schemeId, @RequestParam(defaultValue="1") int pageNum,
        @RequestParam(defaultValue="20") int pageSize) {
        if (categoryId != null && categoryId < 0) return R.fail("客户分类编号无效");
        if (schemeId != null && schemeId <= 0) return R.fail("客户分类类型编号无效");
        if (categoryId != null && schemeId != null) return R.fail("客户分类和分类类型筛选只能选择一个");
        // HTTP 请求参数有默认值，但 MCP 等 Java 内部调用会绕过 Spring 参数绑定，故在业务入口统一兜底。
        // HTTP binds defaults, while direct Java callers such as MCP bypass that layer; normalize optional filters here.
        String keywordFilter = keyword == null ? "" : keyword.trim();
        String statusFilter = status == null ? "" : status.trim();
        // 非法状态不能静默退化为无状态筛选，否则拼错参数会扩大客户查询结果。
        // Reject malformed status filters instead of silently widening the customer result set.
        if (!statusFilter.isEmpty() && !Set.of("0", "1").contains(statusFilter)) return R.fail("客户状态筛选只能是0（启用）或1（停用）");
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (!keywordFilter.isBlank()) { where += " AND (c.customer_code ILIKE ? OR c.customer_name ILIKE ? OR c.customer_short_name ILIKE ?)"; for(int i=0;i<3;i++) args.add("%"+keywordFilter+"%"); }
        if ("0".equals(statusFilter) || "1".equals(statusFilter)) { where += " AND c.status=?"; args.add(statusFilter); }
        if (salesId != null) { where += " AND c.sales_representative_id=?"; args.add(salesId); }
        if (tagId != null) { where += " AND EXISTS (SELECT 1 FROM sys_tag_relation r WHERE r.module_key='customer_master' AND r.record_id=c.customer_id AND r.tag_id=?)"; args.add(tagId); }
        if (categoryId != null) {
            if (categoryId == 0L) where += " AND c.category_id IS NULL";
            else {
                where += " AND c.category_id IN (WITH RECURSIVE branch(id) AS (SELECT category_id FROM base_category WHERE category_id=? AND del_flag='0' UNION SELECT n.category_id FROM base_category n JOIN branch b ON n.parent_id=b.id WHERE n.del_flag='0') SELECT id FROM branch)";
                args.add(categoryId);
            }
        } else if (schemeId != null) {
            where += " AND c.category_id IN (SELECT category_id FROM base_category WHERE scheme_id=? AND del_flag='0')";
            args.add(schemeId);
        }
        // 分类选项只负责前端展示；列表查询在服务端再次按人员授权过滤，避免绕过页面参数读取其他分类客户。
        // Category options only guide the UI; enforce employee grants again in SQL to prevent cross-category reads.
        StringBuilder scopedWhere = new StringBuilder(where).append(" AND ");
        categories.appendScope(scopedWhere, args, "c.category_id", categories.scope());
        where = scopedWhere.toString();
        Long total=jdbc.queryForObject("SELECT count(*) FROM customer_master c"+where,Long.class,args.toArray());
        args.add(Math.max(1,Math.min(pageSize,100))); args.add(Math.max(0,pageNum-1)*Math.max(1,Math.min(pageSize,100)));
        return R.ok(Map.of("rows",jdbc.queryForList(SELECT+where+" ORDER BY c.customer_id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total));
    }

    /** 保留标签计数契约，并按当前人员分类范围统计。 / Preserve the tag-count contract while applying the caller's category scope. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/category-counts")
    public R<Map<String,Object>> categoryCounts() {
        List<Object> totalArgs = new ArrayList<>();
        StringBuilder totalScope = new StringBuilder("WHERE ");
        categories.appendScope(totalScope, totalArgs, "c.category_id", categories.scope());
        Long total = jdbc.queryForObject("SELECT count(*) FROM customer_master c " + totalScope, Long.class, totalArgs.toArray());
        List<Object> tagArgs = new ArrayList<>();
        StringBuilder tagScope = new StringBuilder("WHERE r.module_key='customer_master' AND ");
        categories.appendScope(tagScope, tagArgs, "c.category_id", categories.scope());
        var tags = jdbc.queryForList("SELECT r.tag_id::text AS tag_id, count(DISTINCT c.customer_id) AS customer_count FROM sys_tag_relation r JOIN customer_master c ON c.customer_id=r.record_id " + tagScope + " GROUP BY r.tag_id", tagArgs.toArray());
        return R.ok(Map.of("total", total == null ? 0L : total, "tags", tags));
    }

    /** 按当前人员授权生成分类树；默认分类类型只提供初始展示位置，不扩大可见范围。 / Build the category tree from employee grants; the default scheme does not expand access. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/category-options")
    public R<Map<String,Object>> categoryOptions() {
        Map<String,Object> result = new LinkedHashMap<>(categories.options());
        List<String> configured = jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key=?", String.class, CATEGORY_KEY);
        result.put("defaultSchemeId", configured.isEmpty() ? "" : Objects.toString(configured.getFirst(), ""));
        return R.ok(result);
    }

    /** 详情允许读取停用客户供历史单据使用，但仍检查分类授权并按权限遮蔽联系方式。 / Keep disabled customers readable for historical documents while enforcing category scope and masking contact data. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/{id}")
    public R<Map<String,Object>> detail(@PathVariable Long id) {
        List<Map<String,Object>> found=jdbc.queryForList(SELECT+" WHERE c.customer_id=?",id);
        if(found.isEmpty()) return R.fail("客户不存在");
        Map<String,Object> row=found.get(0);
        if (!customerCategoryAllowed(number(row.get("category_id")))) return R.fail("客户不存在或当前人员无权查看");
        row.put("contacts",jdbc.queryForList("SELECT * FROM customer_contact WHERE customer_id=? ORDER BY contact_id",id));
        row.put("addresses",jdbc.queryForList("SELECT * FROM customer_address WHERE customer_id=? ORDER BY address_id",id));
        row.put("tagIds",jdbc.queryForList("SELECT tag_id::text FROM sys_tag_relation WHERE module_key='customer_master' AND record_id=?",String.class,id));
        row.put("tagOptions",jdbc.queryForList("SELECT t.tag_id::text AS value,t.tag_name AS label,g.group_name AS \"groupName\",(t.status<>'0') AS disabled FROM sys_tag_relation r JOIN sys_tag t ON t.tag_id=r.tag_id JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE r.module_key='customer_master' AND r.record_id=?",id));
        // 列表只读权限不赋予完整联系方式；能维护客户的人员才可读取原值。
        // List permission alone does not expose contact details; only customer editors may read the original values.
        if(!StpUtil.hasPermission("system:customer:edit")) {
            maskChildren(row,"contacts",List.of("phone_number","email"));
            maskChildren(row,"addresses",List.of("recipient_phone"));
        }
        return R.ok(row);
    }

    /** 按名称统计重复客户；忽略大小写和首尾空白，仅供页面提示，不阻止保存。 / Count case-insensitive duplicate names for user confirmation without blocking saves. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/duplicates")
    public R<Long> duplicates(@RequestParam String name,@RequestParam(defaultValue="0") Long id) {
        List<Object> args = new ArrayList<>(List.of(name, id));
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM customer_master c WHERE lower(trim(c.customer_name))=lower(trim(?)) AND c.customer_id<>? AND ");
        categories.appendScope(sql, args, "c.category_id", categories.scope());
        return R.ok(jdbc.queryForObject(sql.toString(),Long.class,args.toArray()));
    }

    /** 仅返回具备业务员职责的在职人员；职责资格不授予客户档案操作权限。 / Return active salespeople; business duty eligibility does not grant customer-record permissions. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/sales-options")
    public R<List<Map<String,Object>>> salesOptions() {
        return R.ok(jdbc.queryForList("SELECT u.user_id::text AS value,u.nick_name AS label,u.org_id::text AS org_id,o.org_name,e.group_id::text AS group_id,g.org_name AS group_name,COALESCE((SELECT string_agg(p.post_name,'、') FROM sys_user_post up JOIN sys_post p ON p.post_id=up.post_id WHERE up.user_id=u.user_id),'') AS posts FROM sys_user u LEFT JOIN sys_org o ON o.org_id=u.org_id LEFT JOIN sys_person_ext e ON e.user_id=u.user_id LEFT JOIN sys_org g ON g.org_id=e.group_id WHERE u.del_flag='0' AND u.status='0' AND u.user_name<>'admin' AND EXISTS(SELECT 1 FROM sys_user_business_duty d WHERE d.user_id=u.user_id AND d.duty_code='SALES') ORDER BY u.nick_name"));
    }

    /** 新业务只能选择启用且在当前人员分类范围内的客户；详情仍可读取停用档案。 / Offer active in-scope customers to new documents while retaining detail access for disabled records. */
    @SaCheckPermission("system:customer:list")
    @GetMapping("/options")
    public R<List<Map<String,Object>>> options() {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT c.customer_id::text AS value,c.customer_name AS label,c.customer_code FROM customer_master c WHERE c.status='0' AND ");
        categories.appendScope(sql, args, "c.category_id", categories.scope());
        sql.append(" ORDER BY c.customer_name");
        return R.ok(jdbc.queryForList(sql.toString(),args.toArray()));
    }

    /** 新增客户并复用统一保存逻辑，保证子项、分类、标签和审计在同一事务内处理。 / Create a customer through the shared transactional save path. */
    @SaCheckPermission("system:customer:add")
    @Log(title="客户档案",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @Transactional
    @PostMapping
    public R<Long> add(@RequestBody Map<String,Object> body) { return save(null,body); }

    /** 更新客户主档及其完整子项集合；未提交分类字段时保留现有分类。 / Update the customer aggregate while preserving category when that field is omitted. */
    @SaCheckPermission("system:customer:edit")
    @Log(title="客户档案",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @Transactional
    @PutMapping("/{id}")
    public R<Long> edit(@PathVariable Long id,@RequestBody Map<String,Object> body) { return save(id,body); }

    /** 状态变更不删除档案或历史引用；修改前锁定记录并核对人员分类权限。 / Change availability without deleting references, locking the row and checking category access first. */
    @SaCheckPermission("system:customer:edit")
    @Log(title="客户档案状态",businessType=BusinessType.UPDATE)
    @Transactional
    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id,@RequestBody Map<String,String> body) {
        String status=body.get("status");
        if(!Set.of("0","1").contains(status==null?"":status)) return R.fail("客户状态无效");
        List<Map<String,Object>> previous = jdbc.queryForList(
            "SELECT customer_name,status,category_id FROM customer_master WHERE customer_id=? FOR UPDATE",id);
        if(previous.isEmpty()) return R.fail("客户不存在");
        if (!customerCategoryAllowed(number(previous.getFirst().get("category_id")))) return R.fail("客户不存在或当前人员无权操作");
        if(jdbc.update("UPDATE customer_master SET status=?,update_by=?,update_time=now() WHERE customer_id=?",status,LoginHelper.getUserId(),id)==0) return R.fail("客户不存在");
        Map<String,Object> before = previous.getFirst();
        Object oldStatus = before.get("status");
        org.dromara.common.core.utils.ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
            "id",id.toString(),"name",before.get("customer_name"),"status",status,
            "changes",Objects.equals(oldStatus,status) ? Map.of() :
                Map.of("status",Arrays.asList(oldStatus,status))));
        return R.ok();
    }

    /**
     * 仅允许删除未被业务表引用的客户；联系人和地址是档案子项，与主档一并删除。
     * 检查数据库声明的外键（包括对子项的引用），防止历史业务因级联删除而丢失。
     * 新增订单、样衣等业务表时必须以外键声明客户及子项关联。
     * Delete only unreferenced customers; contacts and addresses are removed with the master record.
     * Inspect declared foreign keys, including references to child records, so historical business data is never cascaded away.
     * New order or sample tables must declare foreign keys to the customer and its child records.
     */
    @SaCheckPermission("system:customer:remove")
    @Log(title="客户档案",businessType=BusinessType.DELETE,isSaveRequestData=false,isSaveResponseData=false)
    @Transactional
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        List<Map<String,Object>> target = jdbc.queryForList("SELECT customer_id,category_id FROM customer_master WHERE customer_id=? FOR UPDATE", id);
        if (target.isEmpty()) return R.fail("客户不存在");
        if (!customerCategoryAllowed(number(target.getFirst().get("category_id")))) return R.fail("客户不存在或当前人员无权操作");
        List<Map<String,Object>> refs = jdbc.queryForList("""
            SELECT n.nspname AS schema_name, t.relname AS table_name, a.attname AS column_name,
                   p.relname AS parent_table, pa.attname AS parent_column, cardinality(c.conkey) AS key_count
            FROM pg_constraint c
            JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
            JOIN pg_class p ON p.oid=c.confrelid JOIN pg_namespace pn ON pn.oid=p.relnamespace
            JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=c.conkey[1]
            JOIN pg_attribute pa ON pa.attrelid=p.oid AND pa.attnum=c.confkey[1]
            WHERE c.contype='f' AND pn.nspname=current_schema()
              AND p.relname IN ('customer_master','customer_contact','customer_address')
              AND NOT (n.nspname=current_schema() AND t.relname IN ('customer_contact','customer_address') AND p.relname='customer_master')
            """);
        for (Map<String,Object> ref : refs) {
            if (((Number) ref.get("key_count")).intValue() != 1) return R.fail("客户存在需人工检查的关联，无法删除");
            String table = quoteIdentifier(ref.get("schema_name")) + "." + quoteIdentifier(ref.get("table_name"));
            String column = quoteIdentifier(ref.get("column_name"));
            String parent = Objects.toString(ref.get("parent_table"));
            String parentColumn = quoteIdentifier(ref.get("parent_column"));
            String sql = parent.equals("customer_master")
                ? "SELECT EXISTS(SELECT 1 FROM " + table + " WHERE " + column + "=?)"
                : "SELECT EXISTS(SELECT 1 FROM " + table + " r JOIN " + quoteIdentifier(parent) +
                  " p ON r." + column + "=p." + parentColumn + " WHERE p.customer_id=?)";
            if (Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, id))) return R.fail("客户已被业务引用，不能删除，可改为停用");
        }
        Map<String,Object> before = auditMaster(id);
        jdbc.update("DELETE FROM sys_tag_relation WHERE module_key='customer_master' AND record_id=?", id);
        jdbc.update("DELETE FROM customer_contact WHERE customer_id=?", id);
        jdbc.update("DELETE FROM customer_address WHERE customer_id=?", id);
        jdbc.update("DELETE FROM customer_master WHERE customer_id=?", id);
        recordAudit("删除客户",id,Objects.toString(before.get("客户名称")),before,Map.of());
        return R.ok();
    }

    /** 数据库元数据返回的标识符仍需转义，不能作为 SQL 片段直接拼接。 / Quote metadata-derived identifiers before embedding them in SQL. */
    private static String quoteIdentifier(Object value) {
        return "\"" + Objects.toString(value).replace("\"", "\"\"") + "\"";
    }

    /** 所有校验先于写入；运行异常使 Spring 回滚主表和从表，避免留下半份客户。 / Validate the full aggregate before writes; runtime failures roll back master and child records together. */
    private R<Long> save(Long id,Map<String,Object> b) {
        if (b == null) return R.fail("客户请求不能为空");
        String name=text(b,"customer_name",200),shortName=text(b,"customer_short_name",100),credit=text(b,"unified_social_credit_code",18),remark=text(b,"remark",2000);
        if(name.isBlank()) return R.fail("客户名称不能为空");
        if(!credit.isEmpty() && !credit.matches("[0-9A-Z]{18}")) return R.fail("统一社会信用代码应为18位大写字母或数字");
        Long sales=number(b.get("sales_representative_id"));
        if(sales==null || jdbc.queryForObject("SELECT count(*) FROM sys_user u WHERE u.user_id=? AND u.del_flag='0' AND u.user_name<>'admin' AND ((u.status='0' AND EXISTS(SELECT 1 FROM sys_user_business_duty d WHERE d.user_id=u.user_id AND d.duty_code='SALES')) OR EXISTS(SELECT 1 FROM customer_master old WHERE old.customer_id=? AND old.sales_representative_id=u.user_id))",Long.class,sales,id==null?0L:id)==0) return R.fail("请选择具有“业务员”职责的在职人员");
        // 在写入之前读取原有地区与地址，用于允许保留停用地区和无法自动匹配的历史手填值。
        // Load saved region values before validation so unchanged legacy or disabled values can remain intact.
        Map<String,Object> oldCustomer = Map.of();
        Map<Long,Map<String,Object>> oldAddresses = new HashMap<>();
        if(id != null) {
            List<Map<String,Object>> found = jdbc.queryForList("SELECT country_region,country_region_code,category_id FROM customer_master WHERE customer_id=? FOR UPDATE",id);
            if(found.isEmpty()) return R.fail("客户不存在");
            oldCustomer = found.get(0);
            if (!customerCategoryAllowed(number(oldCustomer.get("category_id")))) return R.fail("客户不存在或当前人员无权操作");
            for(Map<String,Object> row:jdbc.queryForList("SELECT address_id,country_region,country_region_code FROM customer_address WHERE customer_id=?",id)) oldAddresses.put(number(row.get("address_id")),row);
        }
        CountryValue customerCountry;
        try { customerCountry = resolveCountry(b,oldCustomer,"客户所在国家/地区"); }
        catch(IllegalArgumentException e) { return R.fail(e.getMessage()); }
        List<Map<String,Object>> contacts=children(b,"contacts"),addresses=children(b,"addresses");
        List<CountryValue> addressCountries = new ArrayList<>();
        long primary=0; Set<String> defaults=new HashSet<>();
        for(Map<String,Object> c:contacts) {
            if(text(c,"contact_name",100).isBlank() || text(c,"phone_number",100).isBlank()) return R.fail("联系人姓名、联系电话不能为空");
            text(c,"contact_title",100);text(c,"email",200);text(c,"contact_role",100);
            if(Boolean.TRUE.equals(c.get("is_primary"))) primary++;
        }
        if(primary>1) return R.fail("每个客户最多一位默认联系人");
        for(Map<String,Object> a:addresses) {
            String type=text(a,"address_type",100);
            if(type.isBlank() || text(a,"address_line",500).isBlank()) return R.fail("地址用途、详细地址不能为空");
            if(type.contains("样衣") && (text(a,"recipient_name",100).isBlank() || text(a,"recipient_phone",100).isBlank())) return R.fail("样衣收件地址须填写收件人和电话");
            for(String k:List.of("recipient_name","recipient_phone","country_region","province","city","district")) text(a,k,100);
            text(a,"postal_code",30);
            Long addressId = number(a.get("address_id"));
            if(addressId != null && !oldAddresses.containsKey(addressId)) return R.fail("地址不属于当前客户，请重新打开后重试");
            try { addressCountries.add(resolveCountry(a,addressId==null?Map.of():oldAddresses.get(addressId),"地址国家/地区")); }
            catch(IllegalArgumentException e) { return R.fail(e.getMessage()); }
            if(Boolean.TRUE.equals(a.get("is_default"))&&!defaults.add(type)) return R.fail("同一用途最多一个默认地址");
        }
        if (b.containsKey("tagIds") && !(b.get("tagIds") instanceof List<?>)) return R.fail("客户标签必须以ID数组传入");
        List<Long> tags=new ArrayList<>();
        if(b.get("tagIds") instanceof List<?> values) for(Object v:values) {
            Long tag=number(v);
            // 停用标签允许原客户保留，但不能新增关联到另一个客户。
            // Keep an inactive tag already assigned to this customer, but do not allow a new assignment.
            if(tag==null || jdbc.queryForObject("SELECT count(*) FROM sys_tag t WHERE t.tag_id=? AND t.module_key='customer_master' AND (t.status='0' OR EXISTS(SELECT 1 FROM sys_tag_relation r WHERE r.module_key='customer_master' AND r.record_id=? AND r.tag_id=t.tag_id))",Long.class,tag,id==null?0L:id)==0) return R.fail("客户标签无效或已停用");
            if(!tags.contains(tag)) tags.add(tag);
        }
        Long user=LoginHelper.getUserId();
        List<Map<String,Object>> previous = id == null ? List.of() : jdbc.queryForList(
            "SELECT customer_id FROM customer_master WHERE customer_id=? FOR UPDATE",id);
        if(id != null && previous.isEmpty()) return R.fail("客户不存在");
        Map<String,Object> before = id == null ? Map.of() : auditMaster(id);
        // 省略分类字段的旧调用保留原值；受限人员必须把客户保存在其可见分类内。
        // Preserve omitted categories for existing callers; scoped users must keep each customer in an authorized category.
        Long previousCategory = id == null ? null : number(oldCustomer.get("category_id"));
        Long categoryId = previousCategory;
        if (b.containsKey("categoryId")) {
            try {
                String value = Objects.toString(b.get("categoryId"), "").trim();
                categoryId = value.isEmpty() ? null : Long.valueOf(value);
            } catch (NumberFormatException ex) { return R.fail("客户分类编号无效"); }
            if (categoryId != null && !Objects.equals(previousCategory, categoryId)) {
                if (jdbc.queryForList("SELECT category_id FROM base_category WHERE category_id=? AND del_flag='0' FOR SHARE", categoryId).isEmpty()
                    || !categories.categoryAllowed(categoryId) || !categories.categoryIsActive(categoryId)) return R.fail("客户分类无效或无权使用");
            }
        }
        if (!customerCategoryAllowed(categoryId)) return R.fail("当前人员没有可用客户分类权限；请选择授权分类后再保存");
        if(id==null) id=jdbc.queryForObject("INSERT INTO customer_master(customer_name,customer_short_name,unified_social_credit_code,country_region,country_region_code,sales_representative_id,remark,create_by) VALUES (?,?,?,?,?,?,?,?) RETURNING customer_id",Long.class,name,shortName,credit,customerCountry.name(),customerCountry.code(),sales,remark,user);
        else if(jdbc.update("UPDATE customer_master SET customer_name=?,customer_short_name=?,unified_social_credit_code=?,country_region=?,country_region_code=?,sales_representative_id=?,remark=?,update_by=?,update_time=now() WHERE customer_id=?",name,shortName,credit,customerCountry.name(),customerCountry.code(),sales,remark,user,id)==0) return R.fail("客户不存在");
        // 档案子项按客户整组保存；下游单据必须保存开单时快照，不能依赖这些可变档案行。
        // Replace child collections as complete sets; downstream documents must keep their own issue-time snapshots.
        jdbc.update("DELETE FROM customer_contact WHERE customer_id=?",id);
        for(Map<String,Object> c:contacts) jdbc.update("INSERT INTO customer_contact(customer_id,contact_name,contact_title,phone_number,email,contact_role,is_primary) VALUES (?,?,?,?,?,?,?)",id,c.get("contact_name"),c.get("contact_title"),c.get("phone_number"),c.get("email"),c.get("contact_role"),Boolean.TRUE.equals(c.get("is_primary")));
        jdbc.update("DELETE FROM customer_address WHERE customer_id=?",id);
        for(int i=0;i<addresses.size();i++) { Map<String,Object> a=addresses.get(i); CountryValue region=addressCountries.get(i); jdbc.update("INSERT INTO customer_address(customer_id,address_type,recipient_name,recipient_phone,country_region,country_region_code,province,city,district,address_line,postal_code,is_default) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,a.get("address_type"),a.get("recipient_name"),a.get("recipient_phone"),region.name(),region.code(),a.get("province"),a.get("city"),a.get("district"),a.get("address_line"),a.get("postal_code"),Boolean.TRUE.equals(a.get("is_default"))); }
        if (b.containsKey("categoryId")) {
            String value = Objects.toString(b.get("categoryId"), "").trim();
            jdbc.update("UPDATE customer_master SET category_id=? WHERE customer_id=?", value.isEmpty() ? null : Long.valueOf(value), id);
        }
        jdbc.update("DELETE FROM sys_tag_relation WHERE module_key='customer_master' AND record_id=?",id);
        for(Long tag:tags) jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by) VALUES ('customer_master',?,?,?)",id,tag,user);
        recordAudit(previous.isEmpty() ? "新增客户" : "修改客户",id,name,before,auditMaster(id));
        return R.ok(id);
    }

    /** 安全读取 JDBC 返回的 bigint；空值或非数字快照不参与分类授权匹配。 / Safely convert a JDBC bigint value; invalid snapshots do not match a category grant. */
    private Long number(Object value) { try{return Long.valueOf(String.valueOf(value));}catch(NumberFormatException e){return null;} }
    /** 全部可见人员可以查看未分类客户；受限人员只能操作授权分类内的客户。 / Allow unclassified records only for unrestricted category scopes. */
    private boolean customerCategoryAllowed(Long categoryId) {
        org.dromara.web.service.CategoryAccessService.Scope scope = categories.scope();
        return "ALL".equals(scope.mode()) || (categoryId != null && categories.categoryAllowed(categoryId));
    }
    /** 国家地区目录解析后的编码和规范中文名快照。 / Resolved country or region code and canonical Chinese-name snapshot. */
    private record CountryValue(String code, String name) {}
    /**
     * 新选择必须是目录中的启用编码；停用编码仅允许原记录继续保留。
     * 旧库未匹配的手填文本只有与该记录原值一致时可继续保存，不允许通过接口新造手填值。
     * 保存编码与名称快照，后续目录名称变化不会改写已有记录。
     * New selections must use an enabled directory code; a disabled or legacy free-text value is accepted only when unchanged.
     * Persist the code and canonical name snapshot so future directory edits do not rewrite this customer's saved value.
     */
    private CountryValue resolveCountry(Map<String,Object> input,Map<String,Object> old,String label) {
        String code=text(input,"country_region_code",12), name=text(input,"country_region",100);
        String oldCode=Objects.toString(old.get("country_region_code"),""), oldName=Objects.toString(old.get("country_region"),"");
        if(code.isEmpty()) {
            if(name.isEmpty()) return new CountryValue(null, "");
            if(oldCode.isEmpty() && name.equals(oldName.trim())) return new CountryValue(null,oldName);
            throw new IllegalArgumentException(label+"请从国家/地区资料中选择");
        }
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT name_zh,status FROM sys_country_region WHERE country_code=?",code);
        if(rows.isEmpty()) throw new IllegalArgumentException(label+"编码不存在");
        Map<String,Object> region=rows.get(0);
        if(!"0".equals(Objects.toString(region.get("status"))) && !code.equals(oldCode)) throw new IllegalArgumentException(label+"已停用，请选择其他地区");
        // 保存时始终使用服务端资料名称，防止客户端伪造名称与编码不一致。
        // Always persist the canonical server-side label so clients cannot pair a valid code with a forged name.
        return new CountryValue(code,Objects.toString(region.get("name_zh"),""));
    }
    /** 按详情查看权限遮蔽联系人/地址中的敏感字段，只修改响应对象，不改数据库。 / Mask sensitive child-record fields in the response without changing stored data. */
    @SuppressWarnings("unchecked")
    private void maskChildren(Map<String,Object> row,String key,List<String> fields) {
        for(Map<String,Object> item:(List<Map<String,Object>>)row.get(key)) for(String field:fields) {
            String value=Objects.toString(item.get(field),"");
            item.put(field,value.isEmpty()?"":"***");
        }
    }
    /** 限制用户输入长度，在进入数据库前给出明确字段错误。 / Enforce field-length limits before values reach database writes. */
    private String text(Map<String,Object> b,String key,int max) {
        String s=Objects.toString(b.get(key),"").trim();
        if(s.length()>max) throw new IllegalArgumentException(key+" 超过长度限制 "+max);
        return s;
    }
    /** 校验联系人和地址为对象数组并限制数量；缺失子项按空集合处理。 / Validate child collections and cap their size; omitted collections become empty. */
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> children(Map<String,Object> b,String key) {
        if (!b.containsKey(key)) return List.of();
        if (!(b.get(key) instanceof List<?> list)) throw new IllegalArgumentException(key + "必须以数组传入");
        if(list.size()>100 || list.stream().anyMatch(v->!(v instanceof Map))) throw new IllegalArgumentException("联系人或地址格式无效，最多100条");
        return (List<Map<String,Object>>)(List<?>)list;
    }
    /** 对比保存前后快照并提交脱敏审计对象，日志只记录实际变化。 / Compare snapshots and attach a privacy-safe audit payload containing only changed fields. */
    private void recordAudit(String action,Long id,String name,Map<String,Object> before,Map<String,Object> after) {
        Map<String,Object> changes = new LinkedHashMap<>();
        Set<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        fields.forEach(field -> {
            Object value = after.get(field);
            Object oldValue = before.get(field);
            if (!Objects.equals(oldValue,value)) {
                changes.put(field, "关联业务员".equals(field)
                    ? Arrays.asList(auditSalesName(oldValue),auditSalesName(value))
                    : Arrays.asList(maskAuditValue(oldValue),maskAuditValue(value)));
            }
        });
        org.dromara.common.core.utils.ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
            "action",action,"id",id.toString(),"name",name,"changes",changes));
    }

    /** 脱敏审计快照中的联系方式，避免操作日志成为敏感数据副本。 / Mask phone and email values so audit logs do not become a second source of sensitive data. */
    private Object maskAuditValue(Object value) {
        if (!(value instanceof List<?> rows)) return value;
        return rows.stream().map(row -> {
            if (!(row instanceof Map<?,?> source)) return row;
            Map<Object,Object> safe = new LinkedHashMap<>(source);
            for (String key : List.of("phone","email")) {
                if (safe.get(key) instanceof String text && !text.isBlank()) {
                    safe.put(key,"phone".equals(key) ? "***"+text.substring(Math.max(0,text.length()-4)) : text.substring(0,1)+"***");
                }
            }
            return safe;
        }).toList();
    }

    /** 读取审计对比所需的客户、联系人、地址和标签快照；联系方式在写入审计前另行脱敏。 / Read the customer aggregate snapshot for change comparison; contacts are masked before logging. */
    private Map<String,Object> auditMaster(Long id) {
        Map<String,Object> snapshot = jdbc.queryForMap("""
            SELECT customer_code AS "客户编码", status AS "状态",
                   customer_name AS "客户名称", customer_short_name AS "客户简称",
                   unified_social_credit_code AS "统一社会信用代码",
                   country_region AS "国家或地区", country_region_code AS "国家或地区编码",
                   sales_representative_id AS "关联业务员", remark AS "备注",
                   (SELECT category_name FROM base_category WHERE category_id=customer_master.category_id) AS "客户分类",
                   category_id::text AS "客户分类编号"
            FROM customer_master WHERE customer_id=?
            """,id);
        snapshot.put("联系人",jdbc.queryForList("""
            SELECT contact_name,contact_title,contact_role,is_primary,
                   phone_number AS phone,
                   email
            FROM customer_contact WHERE customer_id=? ORDER BY 1,2,3,4,5,6
            """,id));
        snapshot.put("地址",jdbc.queryForList("""
            SELECT address_type,recipient_name,country_region,country_region_code,province,city,district,
                   address_line,postal_code,is_default,
                   recipient_phone AS phone
            FROM customer_address WHERE customer_id=? ORDER BY 1,2,3,4,5,6,7,8,9,10,11
            """,id));
        snapshot.put("标签",jdbc.queryForList("""
            SELECT t.tag_name||' / ID '||t.tag_id FROM sys_tag_relation r JOIN sys_tag t ON t.tag_id=r.tag_id
            WHERE r.module_key='customer_master' AND r.record_id=? ORDER BY t.tag_id
            """,String.class,id));
        return snapshot;
    }

    /** 将业务员主键解析为可读名称用于审计差异，找不到人员时保留 ID 以便追溯。 / Resolve salesperson labels for audit while retaining the ID as a traceable fallback. */
    private String auditSalesName(Object id) {
        if(id == null) return null;
        return jdbc.queryForList("SELECT nick_name FROM sys_user WHERE user_id=?",String.class,id)
            .stream().filter(Objects::nonNull).findFirst().map(name -> name+" / ID "+id).orElse("ID "+id);
    }
}
