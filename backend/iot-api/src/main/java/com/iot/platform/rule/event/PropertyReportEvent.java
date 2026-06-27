package com.iot.platform.rule.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * 设备属性上报事件
 *
 * <p>由协议层(MQTT/TCP)发布,规则引擎订阅</p>
 */
@Getter
public class PropertyReportEvent extends ApplicationEvent {

    private final Long tenantId;
    private final Long deviceId;
    private final Long productId;
    private final String deviceKey;
    private final String productKey;
    private final String identifier;
    private final Object value;
    private final Map<String, Object> currentShadows;

    public PropertyReportEvent(Object source, Long tenantId, Long deviceId, Long productId,
                               String deviceKey, String productKey,
                               String identifier, Object value,
                               Map<String, Object> currentShadows) {
        super(source);
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.productId = productId;
        this.deviceKey = deviceKey;
        this.productKey = productKey;
        this.identifier = identifier;
        this.value = value;
        this.currentShadows = currentShadows;
    }
}