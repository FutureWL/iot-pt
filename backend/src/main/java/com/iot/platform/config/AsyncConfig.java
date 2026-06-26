package com.iot.platform.config;

import com.iot.platform.config.IotProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务执行器
 */
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

    private final IotProperties properties;

    /** 规则引擎专用线程池 */
    @Bean("ruleEngineExecutor")
    public Executor ruleEngineExecutor() {
        IotProperties.Rule r = properties.getRule();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(r.getThreadPoolSize());
        exec.setMaxPoolSize(r.getThreadPoolSize() * 2);
        exec.setQueueCapacity(r.getQueueCapacity());
        exec.setThreadNamePrefix("rule-engine-");
        exec.setKeepAliveSeconds(60);
        exec.setRejectedExecutionHandler((task, ex) -> {
            // 拒绝时直接执行,避免告警丢失
            if (!ex.isShutdown()) {
                task.run();
            }
        });
        exec.initialize();
        return exec;
    }
}