package com.iot.platform.iotconsole;

import com.iot.platform.protocol.core.IotMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * IoT 消息拦截器 - 抓包所有 envelope
 *
 * <p>激活条件: iot.console.spy-enabled=true(默认)
 * 关闭时零开销(不注册 SpyBuffer / 不订阅 envelope)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.console", name = "spy-enabled", havingValue = "true", matchIfMissing = true)
public class IotMessageSpy {

    private final SpyBuffer buffer;

    /**
     * 外部调用入口(由 ProtocolDispatcher 在发完 publisher 后调)
     */
    public void onEnvelope(IotMessageEnvelope env) {
        if (env == null) return;
        buffer.add(env);
    }
}
