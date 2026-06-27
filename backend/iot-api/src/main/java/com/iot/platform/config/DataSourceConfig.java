package com.iot.platform.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 多数据源配置
 * - bizDataSource: MySQL 业务数据(主数据源)
 * - tsDataSource:  TDengine 时序数据
 */
@Configuration
public class DataSourceConfig {

    @Bean(name = "bizDataSource", destroyMethod = "close")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource bizDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "tsDataSource", destroyMethod = "close")
    @ConfigurationProperties(prefix = "spring.taos")
    public DataSource tsDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
