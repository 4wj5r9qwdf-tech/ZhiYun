package org.dromara.web.controller;

import org.dromara.system.controller.system.BusinessAudit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 公司档案管理（系统管理模块）。
 * 数据表 sys_company（前端规范 A.1：登录页/首页公司名+logo）。
 * 权限码预留：system:company:list/query/add/edit/remove，
 *   前期超管可用，后期超管在角色里给普通账号勾选（用户定：权限后期自己配）。
 * 文件上传（logo/登录背景）走 RuoYi 自带 /system/oss/upload（返回 oss_id），
 *   本 Controller 只做 CRUD + 关联 oss_id。
 *
 * @author 织云ERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/company")
public class CompanyController {

    private final JdbcTemplate jdbcTemplate;

    @SaCheckPermission("system:company:list")
    @GetMapping("/file-usage/{ids}")
    public R<java.util.List<Map<String, Object>>> fileUsage(@PathVariable Long[] ids) {
        if (ids.length == 0 || ids.length > 100) {
            return R.fail("文件数量无效");
        }
        String marks = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
        return R.ok(jdbcTemplate.queryForList(
            "SELECT company_name, logo_oss_id::text AS logo_oss_id, login_bg_oss_id::text AS login_bg_oss_id " +
            "FROM sys_company WHERE del_flag='0' AND (logo_oss_id IN (" + marks +
            ") OR login_bg_oss_id IN (" + marks + ")) ORDER BY company_id",
            java.util.stream.Stream.concat(java.util.Arrays.stream(ids), java.util.Arrays.stream(ids)).toArray()));
    }

    /**
     * 查询公司列表（分页）。
     */
    @SaCheckPermission("system:company:list")
    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(PageQuery pageQuery) {
        long offset = (pageQuery.getPageNum() - 1L) * pageQuery.getPageSize();
        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_company WHERE del_flag='0'", Long.class);
        java.util.List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT c.company_id, c.company_name, c.logo_oss_id, c.login_bg_oss_id, " +
            "       c.slogan, c.login_bg_mode, c.name_color, c.is_default, c.theme, c.status, " +
            "       COALESCE(o.url,'') AS logo_url, COALESCE(bg.url,'') AS login_bg_url " +
            "FROM sys_company c " +
            "LEFT JOIN sys_oss o ON c.logo_oss_id = o.oss_id " +
            "LEFT JOIN sys_oss bg ON c.login_bg_oss_id = bg.oss_id " +
            "WHERE c.del_flag='0' ORDER BY c.is_default DESC, c.company_id " +
            "LIMIT " + pageQuery.getPageSize() + " OFFSET " + offset);
        return R.ok(new PageResult<>(rows, total));
    }

    /**
     * 查询公司详情。
     */
    @SaCheckPermission("system:company:query")
    @GetMapping("/{companyId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long companyId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT c.company_id, c.company_name, c.logo_oss_id, c.login_bg_oss_id, " +
            "       c.slogan, c.login_bg_mode, c.name_color, c.is_default, c.theme, c.status, " +
            "       COALESCE(o.url,'') AS logo_url, COALESCE(bg.url,'') AS login_bg_url " +
            "FROM sys_company c " +
            "LEFT JOIN sys_oss o ON c.logo_oss_id = o.oss_id " +
            "LEFT JOIN sys_oss bg ON c.login_bg_oss_id = bg.oss_id " +
            "WHERE c.company_id=? AND c.del_flag='0'", companyId);
        return R.ok(row);
    }

    /**
     * 新增公司。
     */
    @SaCheckPermission("system:company:add")
    @Log(title = "公司档案", businessType = BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        boolean makeDefault = "1".equals(body.get("isDefault"));
        if (makeDefault) lockDefaultSwitch();
        var previousDefaults = makeDefault ? defaultCompanies() : null;        Long companyId = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO sys_company (company_id, company_name, logo_oss_id, login_bg_oss_id, " +
            "  slogan, login_bg_mode, name_color, is_default, theme, status, del_flag, create_time) " +
            "VALUES (?,?,?,?,?,?,?,'0',?,'0','0',now())",
            companyId, body.get("companyName"), toLong(body.get("logoOssId")),
            toLong(body.get("loginBgOssId")), body.get("slogan"),
            body.getOrDefault("loginBgMode", "A"), body.getOrDefault("nameColor", "#FFFFFF"), body.get("theme"));
        // 若设为默认，取消其他默认
        if (makeDefault) {
            applyDefault(companyId);
        }
        BusinessAudit.record("新增公司",companyId,"名称",withPreviousDefault(java.util.Map.of(),previousDefaults),withDefaultChange(auditSnapshot(companyId,false),previousDefaults));
        return R.ok();
    }

    /**
     * 修改公司。
     */
    @SaCheckPermission("system:company:edit")
    @Log(title = "公司档案", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        boolean makeDefault = "1".equals(body.get("isDefault"));
        if (makeDefault) lockDefaultSwitch();
        var previousDefaults = makeDefault ? defaultCompanies() : null;        Long companyId = Long.valueOf(body.get("companyId").toString());
        var auditBefore = auditSnapshot(companyId,true);
        if (auditBefore.isEmpty() || "1".equals(auditBefore.get("删除标记"))) return R.fail("公司不存在");
        jdbcTemplate.update(
            "UPDATE sys_company SET company_name=?, logo_oss_id=?, login_bg_oss_id=?, " +
            "  slogan=?, login_bg_mode=?, name_color=?, theme=?, status=?, update_time=now() " +
            "WHERE company_id=?",
            body.get("companyName"), toLong(body.get("logoOssId")), toLong(body.get("loginBgOssId")),
            body.get("slogan"), body.getOrDefault("loginBgMode", "A"),
            body.getOrDefault("nameColor", "#FFFFFF"),
            body.get("theme"), body.getOrDefault("status", "0"), companyId);
        if (makeDefault) {
            applyDefault(companyId);
        }
        BusinessAudit.record("修改公司",companyId,"名称",withPreviousDefault(auditBefore,previousDefaults),withDefaultChange(auditSnapshot(companyId,false),previousDefaults));
        return R.ok();
    }

    /**
     * oss_id 安全转 Long（前端传字符串，DB 列是 bigint；空→null）。
     */
    private Long toLong(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return Long.valueOf(s);
    }

    /**
     * 删除公司（软删）。
     */
    @SaCheckPermission("system:company:remove")
    @Log(title = "公司档案", businessType = BusinessType.DELETE)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{companyId}")
    public R<Void> remove(@PathVariable Long companyId) {
        var auditBefore = auditSnapshot(companyId,true);
        jdbcTemplate.update("UPDATE sys_company SET del_flag='1', update_time=now() WHERE company_id=?", companyId);
        BusinessAudit.record("删除公司",companyId,"名称",auditBefore,auditSnapshot(companyId,false));
        return R.ok();
    }

    /**
     * 设为默认展示公司（置唯一）。
     */
    @SaCheckPermission("system:company:edit")
    @Log(title = "公司档案", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/default/{companyId}")
    public R<Void> setDefault(@PathVariable Long companyId) {
        lockDefaultSwitch();
        var before = auditSnapshot(companyId,true);
        if (before.isEmpty() || "1".equals(before.get("删除标记"))) return R.fail("公司不存在");
        var previous = defaultCompanies();
        applyDefault(companyId);
        BusinessAudit.record("设置默认公司",companyId,"名称",withPreviousDefault(before,previous),
            withDefaultChange(auditSnapshot(companyId,false),previous));
        return R.ok();
    }

    private void lockDefaultSwitch() {
        jdbcTemplate.execute("LOCK TABLE sys_company IN SHARE ROW EXCLUSIVE MODE");
    }

    private void applyDefault(Long companyId) {
        jdbcTemplate.update("UPDATE sys_company SET is_default='0', update_time=now() WHERE is_default='1' AND company_id<>?",companyId);
        jdbcTemplate.update("UPDATE sys_company SET is_default='1', update_time=now() WHERE company_id=? AND is_default IS DISTINCT FROM '1'",companyId);
    }

    private java.util.List<String> defaultCompanies() {
        return jdbcTemplate.queryForList(
            "SELECT company_name||' / ID '||company_id FROM sys_company WHERE is_default='1' ORDER BY company_id",String.class);
    }

    private Map<String,Object> withPreviousDefault(Map<String,Object> snapshot,java.util.List<String> defaults) {
        if (defaults == null) return snapshot;
        var result = new java.util.LinkedHashMap<>(snapshot);
        result.put("默认公司名单",defaults);
        return result;
    }

    private Map<String,Object> withDefaultChange(Map<String,Object> snapshot,java.util.List<String> previous) {
        return withPreviousDefault(snapshot,previous == null ? null : defaultCompanies());
    }

    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(jdbcTemplate,"SELECT company_name AS \"名称\", logo_oss_id::text AS \"标志文件\", login_bg_oss_id::text AS \"登录背景文件\", slogan AS \"标语\", login_bg_mode AS \"背景模式\", name_color AS \"名称颜色\", is_default AS \"默认公司\", theme AS \"主题\", status AS \"状态\", del_flag AS \"删除标记\" FROM sys_company WHERE company_id=?"+(lock ? " FOR UPDATE" : ""),id);
        return row;
    }
}
