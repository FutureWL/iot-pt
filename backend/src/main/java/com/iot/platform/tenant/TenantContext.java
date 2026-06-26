package com.iot.platform.tenant;

/**
 * 多租户上下文 - 基于 ThreadLocal
 *
 * 请求进入时由拦截器设置,业务层通过 getTenantId() 获取。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
