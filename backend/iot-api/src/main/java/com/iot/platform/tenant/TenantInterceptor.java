package com.iot.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器 - 从 JWT 中提取 tenantId 写入上下文
 *
 * 注:这里简化实现,实际从 SecurityContext 获取登录用户。
 */
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 由 SecurityFilter 在更早阶段把 tenantId 写入 TenantContext
        // 这里只是兜底清理
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
