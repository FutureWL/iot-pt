package com.iot.platform.config;

import com.iot.platform.protocol.core.ProtocolAdapter;
import com.iot.platform.protocol.core.ProtocolDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 协议层配置
 *
 * 1. 注册 ProtocolDispatcher 单例 Bean
 * 2. 应用启动时收集所有 ProtocolAdapter 实现并启动
 * 3. 应用关闭时优雅停止
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProtocolAutoConfiguration {

    private final List<ProtocolAdapter> adapters;

    @Bean
    public ProtocolDispatcher protocolDispatcher() {
        ProtocolDispatcher dispatcher = new ProtocolDispatcher();
        adapters.forEach(dispatcher::register);
        return dispatcher;
    }

    @PostConstruct
    public void startAdapters() {
        log.info("启动协议适配器: {}", adapters.stream().map(ProtocolAdapter::getName).toList());
        adapters.forEach(a -> {
            try {
                a.start();
            } catch (Exception e) {
                log.error("协议适配器启动失败: {}", a.getName(), e);
            }
        });
    }

    @PreDestroy
    public void stopAdapters() {
        log.info("停止协议适配器");
        adapters.forEach(a -> {
            try { a.stop(); } catch (Exception ignored) {}
        });
    }
}
