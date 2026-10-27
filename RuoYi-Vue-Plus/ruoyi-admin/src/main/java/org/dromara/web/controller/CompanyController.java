package org.dromara.web.controller;

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
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        Long companyId = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO sys_company (company_id, company_name, logo_oss_id, login_bg_oss_id, " +
            "  slogan, login_bg_mode, name_color, is_default, theme, status, del_flag, create_time) " +
            "VALUES (?,?,?,?,?,?,?,'0',?,'0','0',now())",
            companyId, body.get("companyName"), toLong(body.get("logoOssId")),
            toLong(body.get("loginBgOssId")), body.get("slogan"),
            body.getOrDefault("loginBgMode", "A"), body.getOrDefault("nameColor", "#FFFFFF"), body.get("theme"));
        // 若设为默认，取消其他默认
        if ("1".equals(body.get("isDefault"))) {
            setDefault(companyId);
        }
        return R.ok();
    }

    /**
     * 修改公司。
     */
    @SaCheckPermission("system:company:edit")
    @Log(title = "公司档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long companyId = Long.valueOf(body.get("companyId").toString());
        jdbcTemplate.update(
            "UPDATE sys_company SET company_name=?, logo_oss_id=?, login_bg_oss_id=?, " +
            "  slogan=?, login_bg_mode=?, name_color=?, theme=?, status=?, update_time=now() " +
            "WHERE company_id=?",
            body.get("companyName"), toLong(body.get("logoOssId")), toLong(body.get("loginBgOssId")),
            body.get("slogan"), body.getOrDefault("loginBgMode", "A"),
            body.getOrDefault("nameColor", "#FFFFFF"),
            body.get("theme"), body.getOrDefault("status", "0"), companyId);
        if ("1".equals(body.get("isDefault"))) {
            setDefault(companyId);
        }
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
    @DeleteMapping("/{companyId}")
    public R<Void> remove(@PathVariable Long companyId) {
        jdbcTemplate.update("UPDATE sys_company SET del_flag='1', update_time=now() WHERE company_id=?", companyId);
        return R.ok();
    }

    /**
     * 设为默认展示公司（置唯一）。
     */
    @SaCheckPermission("system:company:edit")
    @Log(title = "公司档案", businessType = BusinessType.UPDATE)
    @PutMapping("/default/{companyId}")
    public R<Void> setDefault(@PathVariable Long companyId) {
        jdbcTemplate.update("UPDATE sys_company SET is_default='0' WHERE is_default='1'");
        jdbcTemplate.update("UPDATE sys_company SET is_default='1', update_time=now() WHERE company_id=?", companyId);
        return R.ok();
    }
}
