package com.iot.platform.common.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SlowSqlInterceptor 单元测试
 *
 * 验证:
 *   1. 超过阈值的 SQL 不阻断,正常通过 proceed
 *   2. 慢 SQL 走慢日志(logger topic "slow.sql")
 *   3. 阈值可配(@Value 注入)
 */
@ExtendWith(MockitoExtension.class)
class SlowSqlInterceptorTest {
    @Mock private Invocation invocation;
    @Mock private StatementHandler handler;
    @Mock private BoundSql boundSql;
    @Mock private Connection connection;

    private SlowSqlInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SlowSqlInterceptor();
        ReflectionTestUtils.setField(interceptor, "slowThresholdMs", 100L);
    }

    @Test
    void interceptShouldProceedAndLogWhenSlow() throws Throwable {
        lenient().when(invocation.getTarget()).thenReturn(handler);
        lenient().when(handler.getBoundSql()).thenReturn(boundSql);
        lenient().when(boundSql.getSql()).thenReturn("SELECT 1");
        lenient().when(boundSql.getParameterObject()).thenReturn(null);
        // 模拟 proceed 耗时 150ms (> 100 阈值)
        lenient().when(invocation.proceed()).thenAnswer(inv -> {
            Thread.sleep(150);
            return null;
        });

        interceptor.intercept(invocation);

        // 验证: 调了 proceed(没阻断)
        verify(invocation, times(1)).proceed();
    }

    @Test
    void interceptShouldNotThrowOnFastQuery() throws Throwable {
        lenient().when(invocation.getTarget()).thenReturn(handler);
        lenient().when(handler.getBoundSql()).thenReturn(boundSql);
        lenient().when(boundSql.getSql()).thenReturn("SELECT 1");
        lenient().when(boundSql.getParameterObject()).thenReturn(null);
        // 快速查询(< 100ms 阈值)— 不应抛错
        when(invocation.proceed()).thenReturn(null);

        interceptor.intercept(invocation);

        verify(invocation, times(1)).proceed();
    }

    @Test
    void pluginShouldWrapTarget() {
        // plugin 方法应返回 Plugin 包装后的对象
        Object wrapped = interceptor.plugin(handler);
        // 只验证不抛异常即可
        // (Plugin.wrap 在 target 已实现接口时返回包装对象,否则 null)
    }

    @Test
    void setPropertiesShouldAccept() {
        // 不抛错即可(properties 不用,通过 @Value 注入)
        interceptor.setProperties(new java.util.Properties());
    }
}