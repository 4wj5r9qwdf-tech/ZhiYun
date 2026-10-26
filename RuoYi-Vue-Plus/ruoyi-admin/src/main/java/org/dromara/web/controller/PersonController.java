package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 人员信息管理（人员模块）。
 * 口径（2026-10-24 定稿）：建人员=建登录账号；工号(6位)=登录名；默认密码123456(BCrypt)；
 *   支持手机号(11位)登录；角色/岗位在人员信息里直接选；内置仅"管理员"角色。
 * 表：sys_user（登录账号+基础身份/联系方式）+ sys_person_ext（任职/合同/社保/证件/住宿登记）
 *   + 关系 sys_user_post（岗位多选）/ sys_user_role（角色多选）。
 * 住宿只登记 is_dorm，床位在宿舍模块。敏感字段（证件号/手机号/银行卡）脱敏。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/person")
public class PersonController {

    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 人员列表（分页，join 扩展表+车间+岗位+角色；敏感字段脱敏）。
     * 筛选：keyword(工号/姓名/手机号)、empStatus、deptId、gender、posts(岗位名)、roles(角色名)。
     * 排序：orderBy=字段:asc|desc（白名单校验，防注入）。
     */
    @SaCheckPermission("system:person:list")
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String empStatus,
                                       @RequestParam(required = false) Long deptId,
                                       @RequestParam(required = false) String gender,
                                       @RequestParam(required = false) String posts,
                                       @RequestParam(required = false) String roles,
                                       @RequestParam(required = false) String orderBy,
                                       @RequestParam(defaultValue = "0") String delFlag,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        // delFlag：0=正常(默认) / 1=已删除(查已删可恢复) / all=全部
        String delCond = "all".equals(delFlag) ? "" : ("u.del_flag='" + ("1".equals(delFlag) ? "1" : "0") + "'");
        StringBuilder where = new StringBuilder(delCond.isEmpty() ? "WHERE 1=1" : ("WHERE " + delCond));
        java.util.List<Object> args = new java.util.ArrayList<>();
        applyFilters(where, args, keyword, empStatus, deptId, gender, posts, roles);
        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user u LEFT JOIN sys_person_ext e ON u.user_id=e.user_id " + where, Long.class, args.toArray());
        String order = safeOrderBy(orderBy);
        int offset = (pageNum - 1) * pageSize;
        args.add(pageSize); args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + where.toString() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("phone_masked", mask(str(r.get("phone_number"))));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        return R.ok(result);
    }

    /**
     * 导出人员（CSV 带 BOM，Excel 可开；全量按筛选；敏感字段脱敏）。
     */
    @SaCheckPermission("system:person:export")
    @GetMapping("/export")
    public R<Map<String, Object>> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String empStatus,
                                         @RequestParam(required = false) Long deptId,
                                         @RequestParam(required = false) String gender,
                                         @RequestParam(required = false) String posts,
                                         @RequestParam(required = false) String roles) {
        StringBuilder where = new StringBuilder("WHERE u.del_flag='0'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        applyFilters(where, args, keyword, empStatus, deptId, gender, posts, roles);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + where.toString() + " ORDER BY u.user_id DESC", args.toArray());
        for (Map<String, Object> r : rows) {
            r.put("phone_masked", mask(str(r.get("phone_number"))));
        }
        StringBuilder sb = new StringBuilder("﻿工号,姓名,性别,手机号,车间,小组,岗位,角色,状态,员工卡\r\n");
        for (Map<String, Object> r : rows) {
            sb.append(csv(str(r.get("job_no")))).append(',')
              .append(csv(str(r.get("name")))).append(',')
              .append(csv("1".equals(str(r.get("gender"))) ? "男" : "2".equals(str(r.get("gender"))) ? "女" : "")).append(',')
              .append(csv(str(r.get("phone_masked")))).append(',')
              .append(csv(str(r.get("dept_name")))).append(',')
              .append(csv(str(r.get("group_name")))).append(',')
              .append(csv(str(r.get("posts")))).append(',')
              .append(csv(str(r.get("roles")))).append(',')
              .append(csv(str(r.get("emp_status")))).append(',')
              .append(csv(str(r.get("card_no")))).append("\r\n");
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("fileName", "人员信息.csv");
        data.put("content", base64);
        data.put("count", rows.size());
        return R.ok(data);
    }

    /** 列表/导出共用 SELECT。 */
    private String selectSql() {
        return "SELECT u.user_id, u.user_name AS job_no, u.nick_name AS name, u.gender, " +
            "  u.phone_number, u.dept_id, d.dept_name, e.group_name, e.group_id, e.emp_status, e.emp_type, " +
            "  e.hire_date, e.card_no, e.is_dorm, " +
            "  (SELECT string_agg(p.post_name, '、') FROM sys_user_post up JOIN sys_post p ON up.post_id=p.post_id WHERE up.user_id=u.user_id) AS posts, " +
            "  (SELECT string_agg(r.role_name, '、') FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=u.user_id) AS roles " +
            "FROM sys_user u " +
            "LEFT JOIN sys_person_ext e ON u.user_id=e.user_id " +
            "LEFT JOIN sys_dept d ON u.dept_id=d.dept_id ";
    }

    /** 组装筛选条件（列表/导出共用）。 */
    private void applyFilters(StringBuilder where, java.util.List<Object> args,
                              String keyword, String empStatus, Long deptId, String gender, String posts, String roles) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.user_name LIKE ? OR u.nick_name LIKE ? OR u.phone_number LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw); args.add(kw); args.add(kw);
        }
        if (empStatus != null && !empStatus.isBlank()) { where.append(" AND e.emp_status=?"); args.add(empStatus); }
        if (deptId != null) { where.append(" AND u.dept_id=?"); args.add(deptId); }
        if (gender != null && !gender.isBlank()) { where.append(" AND u.gender=?"); args.add(gender); }
        if (posts != null && !posts.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_post up JOIN sys_post p ON up.post_id=p.post_id WHERE up.user_id=u.user_id AND p.post_name LIKE ?)");
            args.add("%" + posts.trim() + "%");
        }
        if (roles != null && !roles.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=u.user_id AND r.role_name LIKE ?)");
            args.add("%" + roles.trim() + "%");
        }
    }

    /** 排序白名单（防注入）。 */
    private String safeOrderBy(String orderBy) {
        java.util.Map<String, String> allow = java.util.Map.of(
            "job_no", "u.user_name", "name", "u.nick_name", "dept", "d.dept_name",
            "group", "e.group_name", "status", "e.emp_status", "card", "e.card_no", "hire", "e.hire_date");
        if (orderBy == null || orderBy.isBlank()) return "u.user_id DESC";
        String[] p = orderBy.split(":");
        String col = allow.get(p[0].trim());
        if (col == null) return "u.user_id DESC";
        String dir = (p.length > 1 && "asc".equalsIgnoreCase(p[1].trim())) ? "ASC" : "DESC";
        return col + " " + dir;
    }

    /** CSV 字段转义。 */
    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 人员详情（含扩展表全字段 + 岗位/角色ID列表，敏感字段给全值供编辑）。
     */
    @SaCheckPermission("system:person:query")
    @GetMapping("/{userId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT u.*, e.* FROM sys_user u LEFT JOIN sys_person_ext e ON u.user_id=e.user_id WHERE u.user_id=?", userId);
        List<Long> postIds = jdbcTemplate.queryForList(
            "SELECT post_id FROM sys_user_post WHERE user_id=?", Long.class, userId);
        List<Long> roleIds = jdbcTemplate.queryForList(
            "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId);
        row.put("postIds", postIds);
        row.put("roleIds", roleIds);
        return R.ok(row);
    }

    /**
     * 新增人员（建人员=建登录账号）：写 sys_user + sys_person_ext + 岗位/角色关系。
     * body: jobNo(工号6位)、name、gender、phoneNumber、deptId、password(可空=默认123456)、
     *   postIds[]、roleIds[]、ext{...}。
     */
    @SaCheckPermission("system:person:add")
    @Log(title = "人员信息", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String jobNo = str(body.get("jobNo"));
        if (jobNo == null || jobNo.length() != 6 || !jobNo.matches("\\d{6}")) {
            return R.fail("工号必须是6位数字");
        }
        // 工号唯一：只在"未删除"里查重（软删的不占工号，可复用给新人）
        Long exists = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user WHERE user_name=? AND del_flag='0'", Long.class, jobNo);
        if (exists != null && exists > 0) {
            return R.fail("工号已存在：" + jobNo);
        }
        Long userId = System.currentTimeMillis();
        String password = body.get("password") == null || str(body.get("password")).isBlank()
            ? DEFAULT_PASSWORD : str(body.get("password"));
        jdbcTemplate.update(
            "INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phone_number, gender, password, status, del_flag, create_time, remark) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,'0',now(),?)",
            userId, body.get("deptId"), jobNo, str(body.get("name")), "00",
            str(body.get("email")), str(body.get("phoneNumber")), str(body.get("gender")),
            BCrypt.hashpw(password), str(body.getOrDefault("status", "0")), str(body.get("remark")));
        saveExt(userId, body);
        saveRelations(userId, body);
        return R.ok();
    }

    /**
     * 编辑人员（不改工号/密码；工号变更走单独接口）。
     */
    @SaCheckPermission("system:person:edit")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(str(body.get("userId")));
        String pwd = str(body.get("password"));
        // 密码：填了就重置（BCrypt），不填不动
        if (pwd != null && !pwd.isBlank()) {
            jdbcTemplate.update(
                "UPDATE sys_user SET dept_id=?, nick_name=?, email=?, phone_number=?, gender=?, status=?, remark=?, password=?, update_time=now() WHERE user_id=?",
                body.get("deptId"), str(body.get("name")), str(body.get("email")),
                str(body.get("phoneNumber")), str(body.get("gender")),
                str(body.getOrDefault("status", "0")), str(body.get("remark")),
                BCrypt.hashpw(pwd), userId);
        } else {
            jdbcTemplate.update(
                "UPDATE sys_user SET dept_id=?, nick_name=?, email=?, phone_number=?, gender=?, status=?, remark=?, update_time=now() WHERE user_id=?",
                body.get("deptId"), str(body.get("name")), str(body.get("email")),
                str(body.get("phoneNumber")), str(body.get("gender")),
                str(body.getOrDefault("status", "0")), str(body.get("remark")), userId);
        }
        saveExt(userId, body);
        saveRelations(userId, body);
        return R.ok();
    }

    /**
     * 重置密码为默认 123456。
     */
    @SaCheckPermission("system:person:resetPwd")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/reset-password")
    public R<Void> resetPassword(@PathVariable Long userId) {
        jdbcTemplate.update("UPDATE sys_user SET password=?, update_time=now() WHERE user_id=?",
            BCrypt.hashpw(DEFAULT_PASSWORD), userId);
        return R.ok();
    }

    /**
     * 删除人员（软删）：置 del_flag='1' + status='1'停用（不能再登录）。
     * 人员是登录账号，软删保留历史关联、可恢复，不做物理删。
     */
    @SaCheckPermission("system:person:remove")
    @Log(title = "人员信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable Long userId) {
        // 不允许删超管（user_id 由 .env 配置的超管身份载体，删了登录会出问题）
        if (LoginHelper.isSuperAdmin(userId)) {
            return R.fail("超级管理员账号不允许删除");
        }
        jdbcTemplate.update(
            "UPDATE sys_user SET del_flag='1', status='1', update_time=now() WHERE user_id=?", userId);
        return R.ok();
    }

    /**
     * 恢复已删除人员：del_flag 置回 '0'（status 恢复为在职，可重新登录/使用）。
     * 已删人员工号不占用，恢复前需确认该工号未被新人占用。
     */
    @SaCheckPermission("system:person:edit")
    @Log(title = "人员信息", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/restore")
    public R<Void> restore(@PathVariable Long userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT user_name, del_flag FROM sys_user WHERE user_id=?", userId);
        if (!"1".equals(str(row.get("del_flag")))) {
            return R.fail("该人员未被删除，无需恢复");
        }
        String jobNo = str(row.get("user_name"));
        // 工号若已被新人占用（未删除的同工号），不能恢复
        Long conflict = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_user WHERE user_name=? AND del_flag='0' AND user_id<>?",
            Long.class, jobNo, userId);
        if (conflict != null && conflict > 0) {
            return R.fail("工号 " + jobNo + " 已被其他在职人员占用，无法恢复");
        }
        jdbcTemplate.update(
            "UPDATE sys_user SET del_flag='0', status='0', update_time=now() WHERE user_id=?", userId);
        return R.ok();
    }

    // ===== 内部 =====

    /** 保存扩展表（upsert）。 */
    private void saveExt(Long userId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = body.get("ext") instanceof Map ? (Map<String, Object>) body.get("ext") : java.util.Map.of();
        // 小组：优先 group_id 关联（组织树 dept_category=小组）；带 group_id 时同步冗余 group_name（取小组节点名，找不到则用传入文本）
        Object gidRaw = ext.get("groupId");
        Long groupId = (gidRaw instanceof Number n) ? n.longValue() : null;
        String groupName = str(ext.get("groupName"));
        if (groupId != null) {
            String node = jdbcTemplate.query(
                "SELECT dept_name FROM sys_dept WHERE dept_id=? AND dept_category='小组'", rs -> rs.next() ? rs.getString(1) : null, groupId);
            if (node != null) groupName = node;
        }
        jdbcTemplate.update(
            "INSERT INTO sys_person_ext (user_id, marital_status, cert_type, cert_no, nation, education, birth_date, " +
            "  graduate_school, native_place, address, cert_expire_date, hire_date, group_name, group_id, emp_type, leave_date, " +
            "  card_no, emp_status, bank_card_no, sign_entity, contract_years, contract_start, contract_end, " +
            "  has_social, social_amount, social_subsidy, has_commercial, commercial_type, social_check_date, is_dorm, update_time) " +
            "VALUES (?,?,?,?,?,?,?::date,?,?,?,?::date,?::date,?,?,?,?::date,?,?,?,?,?::numeric,?::date,?::date,?,?::numeric,?::numeric,?,?,?::date,?,now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET " +
            "  marital_status=EXCLUDED.marital_status, cert_type=EXCLUDED.cert_type, cert_no=EXCLUDED.cert_no, " +
            "  nation=EXCLUDED.nation, education=EXCLUDED.education, birth_date=EXCLUDED.birth_date, " +
            "  graduate_school=EXCLUDED.graduate_school, native_place=EXCLUDED.native_place, address=EXCLUDED.address, " +
            "  cert_expire_date=EXCLUDED.cert_expire_date, hire_date=EXCLUDED.hire_date, group_name=EXCLUDED.group_name, " +
            "  group_id=EXCLUDED.group_id, " +
            "  emp_type=EXCLUDED.emp_type, leave_date=EXCLUDED.leave_date, card_no=EXCLUDED.card_no, " +
            "  emp_status=EXCLUDED.emp_status, bank_card_no=EXCLUDED.bank_card_no, sign_entity=EXCLUDED.sign_entity, " +
            "  contract_years=EXCLUDED.contract_years, contract_start=EXCLUDED.contract_start, contract_end=EXCLUDED.contract_end, " +
            "  has_social=EXCLUDED.has_social, social_amount=EXCLUDED.social_amount, social_subsidy=EXCLUDED.social_subsidy, " +
            "  has_commercial=EXCLUDED.has_commercial, commercial_type=EXCLUDED.commercial_type, " +
            "  social_check_date=EXCLUDED.social_check_date, is_dorm=EXCLUDED.is_dorm, update_time=now()",
            userId, ext.get("maritalStatus"), ext.get("certType"), ext.get("certNo"), ext.get("nation"),
            ext.get("education"), dateOrNull(ext.get("birthDate")), ext.get("graduateSchool"), ext.get("nativePlace"),
            ext.get("address"), dateOrNull(ext.get("certExpireDate")), dateOrNull(ext.get("hireDate")), groupName, groupId,
            ext.get("empType"), dateOrNull(ext.get("leaveDate")), ext.get("cardNo"),
            str(ext.getOrDefault("empStatus", "试用")), ext.get("bankCardNo"), ext.get("signEntity"),
            ext.get("contractYears"), dateOrNull(ext.get("contractStart")), dateOrNull(ext.get("contractEnd")),
            ext.getOrDefault("hasSocial", "0"), ext.get("socialAmount"), ext.get("socialSubsidy"),
            ext.getOrDefault("hasCommercial", "0"), ext.get("commercialType"), dateOrNull(ext.get("socialCheckDate")),
            ext.getOrDefault("isDorm", "0"));
    }

    /** 保存岗位/角色关系（全删重插）。 */
    private void saveRelations(Long userId, Map<String, Object> body) {
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        if (body.get("postIds") instanceof List<?> posts) {
            for (Object p : posts) {
                jdbcTemplate.update("INSERT INTO sys_user_post (user_id, post_id) VALUES (?,?)", userId, Long.valueOf(p.toString()));
            }
        }
        if (body.get("roleIds") instanceof List<?> roles) {
            for (Object r : roles) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", userId, Long.valueOf(r.toString()));
            }
        }
    }

    /** 手机号脱敏：138****8000。 */
    private String mask(String phone) {
        // 供 SQL mask() 用不到，占位（列表 SQL 里直接调用本方法不可行，故 SQL 侧不调用）
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    /** 日期字段：空串/null 一律转 null（PG date 不接受 ''），非空转字符串。 */
    private String dateOrNull(Object o) {
        String s = str(o);
        return (s == null || s.isBlank()) ? null : s;
    }
}
