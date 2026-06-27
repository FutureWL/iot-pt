package com.iot.platform.config;

import com.iot.platform.protocol.core.ProtocolAdapter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 协议层配置
 *
 * <p>启用条件:
 *   - iot.protocol.enabled=true (默认) — 启协议适配器
 *   - 在 API 独立进程中设为 false,只订阅 Redis Stream
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iot.protocol-layer-enabled", havingValue = "true", matchIfMissing = true)
public class ProtocolAutoConfiguration {

    private final List<ProtocolAdapter> adapters;

    private final com.iot.platform.protocol.core.ProtocolDispatcher dispatcher;

    @PostConstruct
    public void startAdapters() {
        log.info("[protocol] 启动适配器: {}", adapters.stream().map(ProtocolAdapter::getName).toList());
        // 把所有 adapter 注册到 dispatcher(让它们的消息能传到 dispatcher)
        adapters.forEach(dispatcher::register);
        // 启动协议层
        adapters.forEach(a -> {
            try { a.start(); } catch (Exception e) { log.error("适配器启动失败: {}", a.getName(), e); }
        });
    }

    @PreDestroy
    public void stopAdapters() {
        log.info("[protocol] 停止适配器");
        adapters.forEach(a -> { try { a.stop(); } catch (Exception ignored) {} });
    }
}
