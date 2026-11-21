/*
 * 文件用途：人员档案查询与维护
 * 文件版本：1.0.0
 * 功能目的：人员档案查询与维护
 * 设计思路：用显式工具权限、允许字段白名单、人员数据范围和敏感字段最小化共同约束智能体调用。
 * 实现方法：逐工具校验身份权限和字段；查询复用人员 REST 逻辑并脱敏投影，写操作委托现有控制器。
 * 关键约束：密码、证件、银行卡、住址等敏感字段不得从 MCP 接收或返回。
 * 依赖与引用：Docs/API/人员信息API.md；Docs/Skills/人员信息/SKILL.md；sql/人员分类授权_20261106.sql
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-昨日功能回归；统一人员 MCP 可选参数描述
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：通过（全量后端测试覆盖 MCP 工具契约）
 * 测试范围与记录位置：人员 MCP 参数可选元数据契约；Maven Surefire 报告
 */
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.web.controller.PersonController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 人员档案 MCP 入口，复用 REST 权限、数据范围、校验和业务审计。 / Personnel MCP entry reuses REST permissions, data scope, validation, and business audit. */
@Component
@RequiredArgsConstructor
public class PersonMcpTools {

    private static final Set<String> CREATE_FIELDS = Set.of(
        "jobNo", "name", "gender", "email", "phoneNumber", "orgId", "status", "remark",
        "postIds", "roleIds", "businessDuties", "ext");
    private static final Set<String> UPDATE_FIELDS = Set.of(
        "name", "gender", "email", "phoneNumber", "orgId", "status", "remark",
        "postIds", "roleIds", "businessDuties", "ext");
    private static final Set<String> EXT_FIELDS = Set.of(
        "dingTalkAccount", "hireDate", "groupName", "groupId", "empType", "empStatus", "leaveDate",
        "signEntity", "contractYears", "contractStart", "contractEnd", "isDorm");

    private final PersonController controller;
    private final JdbcTemplate jdbcTemplate;

    /** 按员工权限和数据范围查询在册人员，列表只输出必要字段并遮蔽手机号。 / List in-scope active employees with a minimal, phone-masked projection. */
    @McpTool(name = "person_list", description = "分页查询当前人员数据范围内的在册员工；返回工号、姓名、组织、岗位、职责及脱敏手机号，不含证件或银行卡信息，需要 system:person:list 权限")
    public R<?> list(
        @McpToolParam(description = "可选模糊关键字，匹配工号、姓名或手机号", required = false) String keyword,
        @McpToolParam(description = "可选在职状态，例如试用、在职、离职、长假", required = false) String empStatus,
        @McpToolParam(description = "可选组织节点ID，必须为十进制字符串", required = false) String orgId,
        @McpToolParam(description = "可选性别代码", required = false) String gender,
        @McpToolParam(description = "可选岗位名称关键字", required = false) String posts,
        @McpToolParam(description = "可选角色名称关键字", required = false) String roles,
        @McpToolParam(description = "可选业务职责代码：SALES、FOLLOWER、PURCHASER、PLANNER、WAREHOUSE、QUALITY", required = false) String businessDuty,
        @McpToolParam(description = "可选排序：job_no、name、org、status、card、hire，可加 :asc 或 :desc", required = false) String orderBy,
        @McpToolParam(description = "可选是否包含所选组织的全部下级；省略时默认为false", required = false) Boolean subtree,
        @McpToolParam(description = "可选页码；省略时默认为1", required = false) Integer pageNum,
        @McpToolParam(description = "可选每页条数，最大100；省略时默认为20", required = false) Integer pageSize) {
        StpUtil.checkPermission("system:person:list");
        Long orgNodeId = parseOptionalId(orgId, "组织ID");
        int page = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : Math.min(pageSize, 100);
        if (page < 1 || page > 1_000_000 || size < 1) throw new ServiceException("页码须大于0，每页条数须为1至100");
        R<Map<String, Object>> response = controller.list(keyword, empStatus, orgNodeId, gender, posts, roles,
            businessDuty, orderBy, "0", "0", Boolean.TRUE.equals(subtree) ? "1" : "0", page, size);
        if (response.getCode() != 200 || response.getData() == null) return response;
        Map<String, Object> data = response.getData();
        Object rawRows = data.get("rows");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (rawRows instanceof List<?> values) {
            for (Object value : values) if (value instanceof Map<?, ?> row) rows.add(listProjection(row));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", data.get("total"));
        result.put("pageNum", page);
        result.put("pageSize", size);
        return R.ok(result);
    }

    /** 查询一名员工的工作资料；不返回证件号、银行卡号、住址和社保金额。 / Read a work profile without government-ID, bank, home-address, or social-insurance amounts. */
    @McpTool(name = "person_get", description = "查询指定员工的工作资料；手机号脱敏，证件号码、银行卡号、家庭住址和社保金额不返回，并遵守人员数据范围，需要 system:person:query 权限")
    public R<?> get(@McpToolParam(description = "人员ID，十进制字符串") String userId) {
        StpUtil.checkPermission("system:person:query");
        Long id = parseId(userId, "人员ID");
        R<Map<String, Object>> response = controller.getInfo(id);
        if (response.getCode() != 200 || response.getData() == null) return response;
        rejectSystemAccount(response.getData());
        return R.ok(detailProjection(response.getData()));
    }

    /** 查询可用于客户订单等业务责任人筛选的职责代码。 / Read business-duty codes used by document-owner selectors. */
    @McpTool(name = "person_business_duty_options", description = "查询人员可配置的业务职责代码和名称；职责不等同岗位或权限，需要 system:person:list 权限")
    public R<?> businessDutyOptions() {
        StpUtil.checkPermission("system:person:list");
        return controller.businessDutyOptions();
    }

    /** 新增员工登录档案；登录密码须由授权人员在人员页面设置，MCP 不接受或返回密码。 / Create an employee account without accepting or returning password data. */
    @McpTool(name = "person_create", description = "新增人员档案并创建登录账号；需明确确认工号、姓名、组织、岗位/角色和业务职责。MCP 不接收密码或证件、银行卡、家庭住址、社保金额等敏感字段；需要 system:person:add 权限；分配角色另需 system:user:edit。创建后由具备重置密码权限的人员在人员页面设置密码，员工才能登录")
    public R<?> create(@McpToolParam(description = "人员字段：jobNo（6位工号字符串）、name必填、gender、email、phoneNumber、orgId十进制字符串、status、remark、postIds/roleIds十进制字符串数组、businessDuties代码数组；ext 仅接受dingTalkAccount、hireDate、groupName、groupId、empType、empStatus、leaveDate、signEntity、contractYears、contractStart、contractEnd、isDorm；不要传password或敏感个人字段") Map<String, Object> body) {
        StpUtil.checkPermission("system:person:add");
        if (body != null && body.containsKey("password")) throw new ServiceException("MCP 不接收人员密码，请通过人员页面安全设置");
        Map<String, Object> payload = requireBody(body, CREATE_FIELDS);
        Object jobNo = payload.get("jobNo");
        if (!(jobNo instanceof String code) || !code.matches("\\d{6}")) throw new ServiceException("工号必须是6位数字字符串");
        if (!(payload.get("name") instanceof String name) || name.isBlank()) throw new ServiceException("请填写人员姓名");
        validateIds(payload);
        R<?> created = controller.add(payload);
        if (created.getCode() != 200) return created;
        // 新增接口原本不返回主键；按唯一工号回查刚创建的ID，避免智能体再猜测 bigint。 / Resolve the generated ID by its unique job number.
        List<Map<String, Object>> ids = jdbcTemplate.queryForList(
            "SELECT user_id::text AS \"personId\" FROM sys_user WHERE user_name=? AND del_flag='0'", code);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobNo", code);
        if (!ids.isEmpty()) result.put("personId", ids.getFirst().get("personId"));
        return R.ok("人员档案已创建。请由具备重置密码权限的人员在人员页面设置登录密码", result);
    }

    /** 按字段补丁更新人员，读取现值仅在服务端合并，未提交字段不会被清空。 / Apply a partial update merged server-side so omitted fields remain unchanged. */
    @McpTool(name = "person_update", description = "修改人员档案；按字段补丁合并，未提供的档案字段会在服务端保留。不得传工号或密码；展示目标人员及修改内容并确认。需要 system:person:query 和 system:person:edit 权限；改变角色另需 system:user:edit")
    public R<?> update(@McpToolParam(description = "人员ID，十进制字符串") String userId,
                       @McpToolParam(description = "只填写要改的字段：name、gender、email、phoneNumber、orgId、status、remark、postIds、roleIds、businessDuties 或 ext 内允许的工作字段；不支持jobNo/password及证件、银行卡、家庭住址、社保金额等敏感个人字段") Map<String, Object> changes) {
        StpUtil.checkPermission("system:person:query");
        StpUtil.checkPermission("system:person:edit");
        Long id = parseId(userId, "人员ID");
        Map<String, Object> patch = requireBody(changes, UPDATE_FIELDS);
        if (patch.isEmpty()) throw new ServiceException("请至少提供一个要修改的字段");
        validateIds(patch);
        R<Map<String, Object>> current = controller.getInfo(id);
        if (current.getCode() != 200 || current.getData() == null) return R.fail(current.getMsg());
        rejectSystemAccount(current.getData());
        Map<String, Object> payload = toEditPayload(id, current.getData());
        Map<String, Object> extPatch = patch.containsKey("ext") ? requireBody(asMap(patch.get("ext"), "ext"), EXT_FIELDS) : Map.of();
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if ("ext".equals(entry.getKey())) continue;
            payload.put(entry.getKey(), entry.getValue());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = (Map<String, Object>) payload.get("ext");
        ext.putAll(extPatch);
        R<?> updated = controller.edit(payload);
        if (updated.getCode() != 200) return updated;
        return R.ok(Map.of("personId", id.toString(), "updated", true));
    }

    /** 软删除人员并停用登录；禁止超级管理员及内置 admin 账号。 / Soft-delete and disable login, while protecting system accounts. */
    @McpTool(name = "person_delete", description = "软删除人员并停用其登录账号，历史关联保留且可恢复；调用前先读取人员并确认工号/姓名。禁止删除超级管理员和内置admin账号；需要 system:person:query 与 system:person:remove 权限")
    public R<?> delete(@McpToolParam(description = "人员ID，十进制字符串") String userId) {
        StpUtil.checkPermission("system:person:query");
        StpUtil.checkPermission("system:person:remove");
        Long id = parseId(userId, "人员ID");
        R<Map<String, Object>> current = controller.getInfo(id);
        if (current.getCode() != 200 || current.getData() == null) return R.fail(current.getMsg());
        rejectSystemAccount(current.getData());
        return controller.remove(id);
    }

    /** 恢复已软删除人员；后端仍校验工号冲突和人员数据范围。 / Restore a soft-deleted employee subject to server-side scope and job-number checks. */
    @McpTool(name = "person_restore", description = "恢复已软删除人员并恢复登录状态；先查询该人员并确认工号、姓名及工号未被占用。需要 system:person:query 和 system:person:edit 权限")
    public R<?> restore(@McpToolParam(description = "人员ID，十进制字符串") String userId) {
        StpUtil.checkPermission("system:person:query");
        StpUtil.checkPermission("system:person:edit");
        Long id = parseId(userId, "人员ID");
        R<Map<String, Object>> current = controller.getInfo(id);
        if (current.getCode() != 200 || current.getData() == null) return R.fail(current.getMsg());
        rejectSystemAccount(current.getData());
        if (!"1".equals(text(current.getData().get("del_flag")))) return R.fail("该人员未被删除，无需恢复");
        return controller.restore(id);
    }

    /** 生成限定字段的列表视图，不把控制器当前返回的原始手机号输出到 MCP。 / Project only approved list fields and never return raw phone data. */
    private Map<String, Object> listProjection(Map<?, ?> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personId", text(row.get("user_id_text")) == null ? text(row.get("user_id")) : text(row.get("user_id_text")));
        result.put("jobNo", row.get("job_no"));
        result.put("name", row.get("name"));
        result.put("gender", row.get("gender"));
        result.put("accountStatus", row.get("account_status"));
        result.put("phoneMasked", maskPhone(text(row.get("phone_number"))));
        result.put("organizationId", text(row.get("org_id")));
        result.put("organizationName", row.get("org_name"));
        result.put("employmentStatus", row.get("emp_status"));
        result.put("employmentType", row.get("emp_type"));
        result.put("hireDate", row.get("hire_date"));
        result.put("isDorm", row.get("is_dorm"));
        result.put("dingTalkAccount", row.get("dingtalk_account"));
        result.put("posts", row.get("posts"));
        result.put("roles", row.get("roles"));
        result.put("businessDuties", row.get("business_duties"));
        return result;
    }

    /** 详情返回工作所需字段，明确排除证件、银行、家庭住址及财务金额。 / Exclude government ID, bank, home address, and financial amounts. */
    private Map<String, Object> detailProjection(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personId", text(row.get("user_id")));
        result.put("jobNo", row.get("user_name"));
        result.put("name", row.get("nick_name"));
        result.put("gender", row.get("gender"));
        result.put("email", row.get("email"));
        result.put("phoneMasked", maskPhone(text(row.get("phone_number"))));
        result.put("accountStatus", row.get("status"));
        result.put("organizationId", text(row.get("org_id")));
        result.put("employmentStatus", row.get("emp_status"));
        result.put("employmentType", row.get("emp_type"));
        result.put("hireDate", row.get("hire_date"));
        result.put("leaveDate", row.get("leave_date"));
        result.put("groupId", text(row.get("group_id")));
        result.put("groupName", row.get("group_name"));
        result.put("postIds", idStrings(row.get("postIds")));
        result.put("roleIds", idStrings(row.get("roleIds")));
        result.put("businessDuties", row.get("businessDuties"));
        result.put("dingTalkAccount", row.get("dingtalk_account"));
        result.put("signEntity", row.get("sign_entity"));
        result.put("contractYears", row.get("contract_years"));
        result.put("contractStart", row.get("contract_start"));
        result.put("contractEnd", row.get("contract_end"));
        result.put("hasSocial", row.get("has_social"));
        result.put("hasCommercial", row.get("has_commercial"));
        result.put("commercialType", row.get("commercial_type"));
        result.put("socialCheckDate", row.get("social_check_date"));
        result.put("isDorm", row.get("is_dorm"));
        result.put("remark", row.get("remark"));
        return result;
    }

    /** 将后端完整详情在服务端转换成编辑请求，补丁只覆盖明确提供的字段。 / Build a full controller payload internally before applying a partial patch. */
    private Map<String, Object> toEditPayload(Long userId, Map<String, Object> row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("name", row.get("nick_name"));
        payload.put("gender", row.get("gender"));
        payload.put("email", row.get("email"));
        payload.put("phoneNumber", row.get("phone_number"));
        payload.put("orgId", text(row.get("org_id")));
        payload.put("status", row.get("status"));
        payload.put("remark", row.get("remark"));
        payload.put("postIds", idStrings(row.get("postIds")));
        payload.put("roleIds", idStrings(row.get("roleIds")));
        payload.put("businessDuties", row.get("businessDuties"));
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("dingTalkAccount", row.get("dingtalk_account"));
        ext.put("maritalStatus", row.get("marital_status"));
        ext.put("certType", row.get("cert_type"));
        ext.put("certNo", row.get("cert_no"));
        ext.put("nation", row.get("nation"));
        ext.put("education", row.get("education"));
        ext.put("birthDate", row.get("birth_date"));
        ext.put("graduateSchool", row.get("graduate_school"));
        ext.put("nativePlace", row.get("native_place"));
        ext.put("address", row.get("address"));
        ext.put("certExpireDate", row.get("cert_expire_date"));
        ext.put("hireDate", row.get("hire_date"));
        ext.put("groupName", row.get("group_name"));
        ext.put("groupId", text(row.get("group_id")));
        ext.put("empType", row.get("emp_type"));
        ext.put("leaveDate", row.get("leave_date"));
        ext.put("cardNo", row.get("card_no"));
        ext.put("empStatus", row.get("emp_status"));
        ext.put("bankCardNo", row.get("bank_card_no"));
        ext.put("signEntity", row.get("sign_entity"));
        ext.put("contractYears", row.get("contract_years"));
        ext.put("contractStart", row.get("contract_start"));
        ext.put("contractEnd", row.get("contract_end"));
        ext.put("hasSocial", row.get("has_social"));
        ext.put("socialAmount", row.get("social_amount"));
        ext.put("socialSubsidy", row.get("social_subsidy"));
        ext.put("hasCommercial", row.get("has_commercial"));
        ext.put("commercialType", row.get("commercial_type"));
        ext.put("socialCheckDate", row.get("social_check_date"));
        ext.put("isDorm", row.get("is_dorm"));
        payload.put("ext", ext);
        return payload;
    }

    /** 明确拒绝内置和超管账户，防止通过 MCP 改动系统登录身份。 / Protect built-in and super-admin accounts from MCP mutations. */
    private void rejectSystemAccount(Map<String, Object> row) {
        Long id = parseOptionalId(text(row.get("user_id")), "人员ID");
        if (id != null && (LoginHelper.isSuperAdmin(id) || "admin".equalsIgnoreCase(text(row.get("user_name"))))) {
            throw new ServiceException("内置系统账号或超级管理员账号不允许通过人员 MCP 操作");
        }
    }

    /** 校验并复制只允许的字段，拒绝静默忽略未知字段。 / Copy only explicit fields and reject unknown input. */
    private Map<String, Object> requireBody(Map<String, Object> body, Set<String> allowed) {
        if (body == null) throw new ServiceException("请求内容不能为空");
        for (String key : body.keySet()) if (!allowed.contains(key)) throw new ServiceException("不支持人员字段：" + key);
        return new LinkedHashMap<>(body);
    }

    /** 将 MCP 对象转换成字符串键的有序映射，拒绝非字符串键以避免字段名被静默丢弃。 / Convert MCP objects to ordered string-key maps and reject malformed keys. */
    private Map<String, Object> asMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw)) throw new ServiceException(field + "必须是对象");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new ServiceException(field + "字段名无效");
            result.put(key, entry.getValue());
        }
        return result;
    }

    /** 校验可修改的组织、岗位和角色ID形状；具体存在性及授权范围仍由人员控制器核验。 / Validate ID payload shapes while leaving existence and scope checks to the controller. */
    private void validateIds(Map<String, Object> payload) {
        validateOptionalId(payload.get("orgId"), "orgId");
        validateIdList(payload.get("postIds"), "postIds");
        validateIdList(payload.get("roleIds"), "roleIds");
        if (payload.get("ext") != null) {
            Map<String, Object> ext = requireBody(asMap(payload.get("ext"), "ext"), EXT_FIELDS);
            validateOptionalId(ext.get("groupId"), "ext.groupId");
        }
    }

    /** 校验多选主键必须以十进制字符串数组传入，避免 JSON 浮点数破坏 bigint。 / Require decimal-string arrays for multi-select bigint IDs. */
    private void validateIdList(Object value, String field) {
        if (value == null) return;
        if (!(value instanceof List<?> values)) throw new ServiceException(field + "必须是十进制字符串数组");
        for (Object item : values) parseId(item, field + "成员");
    }

    /** 校验可选主键字段；空值表示不改或清空，非空值必须是字符串ID。 / Validate an optional ID while preserving the API's null-versus-value semantics. */
    private void validateOptionalId(Object value, String field) {
        if (value == null || "".equals(value)) return;
        parseId(value, field);
    }

    /** 将空白可选ID归一化为 null，并使用统一主键解析器校验。 / Normalize blank optional IDs before strict parsing. */
    private Long parseOptionalId(String value, String field) {
        return value == null || value.isBlank() ? null : parseId(value, field);
    }

    /** 严格解析正十进制字符串主键，拒绝 JSON 数字及超出 Long 范围的值。 / Parse decimal-string IDs without accepting imprecise JSON numbers. */
    private Long parseId(Object value, String field) {
        if (!(value instanceof String text)) throw new ServiceException(field + "必须是十进制字符串");
        return McpInputValidator.requirePositiveId(text, field);
    }

    /** 将控制器返回的多选ID规范化为字符串列表，保证 MCP bigint 契约一致。 / Normalize multi-select IDs to the MCP decimal-string contract. */
    private List<String> idStrings(Object values) {
        if (!(values instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : list) if (value != null) result.add(value.toString());
        return result;
    }

    /** 对常见完整电话号码保留首三末四；短号码仅保留极少识别位，避免原样泄露。 / Mask phone numbers, including malformed short values, before returning them. */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String value = phone.trim();
        if (value.length() >= 8) return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        if (value.length() <= 4) return "*".repeat(value.length());
        return value.substring(0, 1) + "*".repeat(value.length() - 3) + value.substring(value.length() - 2);
    }

    /** 将可选数据库值转换为字符串；缺失值保持 null，供筛选与ID校验区分。 / Convert optional database values to strings while preserving null. */
    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
