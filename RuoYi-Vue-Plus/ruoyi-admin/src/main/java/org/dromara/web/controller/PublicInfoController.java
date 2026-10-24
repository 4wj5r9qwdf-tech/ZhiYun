package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录页公共信息接口：返回当前展示公司（登录页/首页显示公司名+logo）。
 * 数据来源：sys_company（前端规范 A.1），is_default=是 的公司。
 * 公司名/logo 由管理端"公司档案"维护，不写死代码。
 *
 * @author 织云ERP
 */
@SaIgnore
@RequiredArgsConstructor
@RestController
public class PublicInfoController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取当前展示公司（登录页/首页用）。
     *
     * @return 公司名 + logo 地址 + 主题
     */
    @GetMapping("/public/company")
    public R<Map<String, Object>> company() {
        Map<String, Object> data = new HashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT c.company_id, c.company_name, c.theme, " +
                "       COALESCE(o.url, '') AS logo_url " +
                "FROM sys_company c " +
                "LEFT JOIN sys_oss o ON c.logo_oss_id = o.oss_id " +
                "WHERE c.is_default = '1' AND c.status = '0' AND c.del_flag = '0' " +
                "LIMIT 1");
            data.putAll(row);
        } catch (Exception e) {
            data.put("company_name", "织云ERP");
            data.put("logo_url", "");
        }
        return R.ok(data);
    }
}
