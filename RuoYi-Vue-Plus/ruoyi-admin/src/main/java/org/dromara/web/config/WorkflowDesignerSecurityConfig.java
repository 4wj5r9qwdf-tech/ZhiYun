/*
 * 文件用途：实现织云 ERP 的WorkflowDesignerSecurityConfig配置或基础能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云 ERP 的WorkflowDesignerSecurityConfig配置或基础能力。
 * 设计思路：沿用项目现有分层与框架扩展点，保持和相关业务模块的契约一致。
 * 实现方法：由所属框架加载后与对应业务模块协作；细节以本文件和关联接口为准。
 * 关键约束：仅对本项目实际变更负责，并保留上游版权/许可。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 工作流设计器插件未提供业务权限注解，此处对设计和读取分别校验。 / Enforce separate read and design permissions because the workflow plugin exposes no business permission annotations. */
@Configuration
public class WorkflowDesignerSecurityConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                // Saving a definition must never be allowed merely because the caller is logged in.
                if (path.equals("/warm-flow/save-json")) {
                    StpUtil.checkPermission("workflow:definition:edit");
                } else if (path.equals("/warm-flow/query-def") || path.startsWith("/warm-flow/query-def/")
                    || path.equals("/warm-flow/handler-type") || path.equals("/warm-flow/handler-result")
                    || path.equals("/warm-flow/handler-feedback") || path.equals("/warm-flow/handler-dict")
                    || path.equals("/warm-flow/node-ext") || path.equals("/warm-flow/listener-list")) {
                    StpUtil.checkPermission("workflow:definition:query");
                }
                return true;
            }
        }).addPathPatterns("/warm-flow/**");
    }
}
