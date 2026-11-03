package org.dromara.web.controller;

import org.dromara.system.controller.system.BusinessAudit;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 国家/地区基础资料。编码创建后保持稳定，客户所在地区、订单目的地及发货目的地
 * 分别引用编码；停用只禁止新引用，不删除既有记录或改变已有业务快照。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/country-region")
public class CountryRegionController {
    private final JdbcTemplate jdbc;

    /** 已登录的业务表单可获取选择项；停用项携带状态，供已有引用正确显示。 */
    @SaCheckLogin
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(jdbc.queryForList("SELECT country_code,name_zh,name_en,status FROM sys_country_region ORDER BY CASE WHEN country_code='CN' THEN 0 ELSE 1 END,name_zh,country_code"));
    }

    /** 基础资料管理页读取完整目录；搜索在客户端对小规模基础资料进行。 */
    @SaCheckPermission("system:country:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return options();
    }

    /** 新增时由维护人员指定稳定编码，系统初始化资料使用 ISO 两位编码。 */
    @SaCheckPermission("system:country:add")
    @Log(title="国家地区", businessType=BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String code = value(body, "country_code"), zh = value(body, "name_zh"), en = value(body, "name_en");
        if (!code.matches("[A-Z][A-Z0-9_-]{1,11}")) return R.fail("编码须以大写字母开头，长度2至12位，可包含数字、下划线和短横线");
        String error = validateNames(zh, en);
        if (error != null) return R.fail(error);
        if (jdbc.queryForObject("SELECT count(*) FROM sys_country_region WHERE country_code=?", Long.class, code) > 0) return R.fail("国家/地区编码已存在");
        jdbc.update("INSERT INTO sys_country_region(country_code,name_zh,name_en,create_by) VALUES (?,?,?,?)", code, zh, en, LoginHelper.getUserId());
        BusinessAudit.record("新增国家或地区",code,"名称",java.util.Map.of(),auditSnapshot(code,false));
        return R.ok();
    }

    /** 修改名称，编码不可更改；已有客户名称快照保持原样，后续表单可显示当前名称。 */
    @SaCheckPermission("system:country:edit")
    @Log(title="国家地区", businessType=BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{code}")
    public R<Void> edit(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String zh = value(body, "name_zh"), en = value(body, "name_en");
        String error = validateNames(zh, en);
        if (error != null) return R.fail(error);
        var auditBefore = auditSnapshot(code,true);
        if (jdbc.update("UPDATE sys_country_region SET name_zh=?,name_en=?,update_by=?,update_time=now() WHERE country_code=?", zh, en, LoginHelper.getUserId(), code) == 0) return R.fail("国家/地区不存在");
        BusinessAudit.record("修改国家或地区",code,"名称",auditBefore,auditSnapshot(code,false));
        return R.ok();
    }

    /** 软停用国家地区，历史客户和地址仍可读取。 */
    @SaCheckPermission("system:country:edit")
    @Log(title="国家地区状态", businessType=BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{code}/status")
    public R<Void> status(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String status = value(body, "status");
        if (!Set.of("0", "1").contains(status)) return R.fail("状态无效");
        var auditBefore = auditSnapshot(code,true);
        if (jdbc.update("UPDATE sys_country_region SET status=?,update_by=?,update_time=now() WHERE country_code=?", status, LoginHelper.getUserId(), code) == 0) return R.fail("国家/地区不存在");
        BusinessAudit.record("变更国家或地区状态",code,"名称",auditBefore,auditSnapshot(code,false));
        return R.ok();
    }

    private String validateNames(String zh, String en) {
        if (zh.isBlank()) return "请输入中文名称";
        if (zh.length() > 100 || en.length() > 200) return "名称超过长度限制";
        return null;
    }
    private String value(Map<String, Object> body, String key) { return Objects.toString(body.get(key), "").trim(); }
    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(jdbc,"SELECT name_zh AS \"名称\", name_en AS \"英文名称\", status AS \"状态\" FROM sys_country_region WHERE country_code=?"+(lock ? " FOR UPDATE" : ""),id);
        return row;
    }
}
