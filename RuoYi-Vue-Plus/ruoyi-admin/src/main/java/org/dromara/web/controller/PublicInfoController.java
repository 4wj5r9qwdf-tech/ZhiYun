/*
 * 文件用途：为织云 ERP 的公开基础资料接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的公开基础资料接口提供服务端 HTTP 接口。
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
                "SELECT c.company_id, c.company_name, c.theme, c.slogan, c.login_bg_mode, c.name_color, " +
                "       COALESCE(o.url, '') AS logo_url, " +
                "       COALESCE(bg.url, '') AS login_bg_url " +
                "FROM sys_company c " +
                "LEFT JOIN sys_oss o ON c.logo_oss_id = o.oss_id " +
                "LEFT JOIN sys_oss bg ON c.login_bg_oss_id = bg.oss_id " +
                "WHERE c.is_default = '1' AND c.status = '0' AND c.del_flag = '0' " +
                "LIMIT 1");
            data.putAll(row);
        } catch (Exception e) {
            data.put("company_name", "织云ERP");
            data.put("logo_url", "");
            data.put("slogan", "");
            data.put("login_bg_url", "");
            data.put("login_bg_mode", "A");
            data.put("name_color", "#FFFFFF");
        }
        return R.ok(data);
    }
}
