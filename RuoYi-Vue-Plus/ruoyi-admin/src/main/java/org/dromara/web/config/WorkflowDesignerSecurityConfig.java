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
