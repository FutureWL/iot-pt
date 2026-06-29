package com.iot.platform.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis 慢 SQL 拦截器
 *
 * 超过阈值(默认 500ms)的 SQL 单独记到独立 logger(slow.sql),
 * 含 SQL 文本 + 耗时(毫秒),方便在 Grafana/ELK 里分析慢查询。
 *
 * 配置: application.yml
 *   iot:
 *     sql:
 *       slow-threshold-ms: 500   # 可调,默认 500
 */
@Slf4j(topic = "slow.sql")
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class SlowSqlInterceptor implements Interceptor {

    @Value("${iot.sql.slow-threshold-ms:500}")
    private long slowThresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowThresholdMs) {
                StatementHandler handler = (StatementHandler) invocation.getTarget();
                BoundSql boundSql = handler.getBoundSql();
                String sql = boundSql.getSql();
                Object paramObj = boundSql.getParameterObject();
                log.warn("cost={}ms | sql={} | params={}", cost, sql, paramObj);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return org.apache.ibatis.plugin.Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 阈值通过 @Value 注入,这里不需要
    }
}