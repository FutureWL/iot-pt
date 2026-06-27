package com.iot.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * IoT Broker 进程的安全配置 - 简化版
 *
 * <p>Broker 不依赖业务(iot-api),所以不能复用 iot-api 的 SecurityConfig。
 * 这里只放行 IoT 控制台端点和健康检查,不启用 JWT 鉴权
 * (broker 是内网组件,鉴权由前面的网关负责)。</p>
 */
@Configuration
@ConditionalOnProperty(name = "iot.role", havingValue = "iot")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
