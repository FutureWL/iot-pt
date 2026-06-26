package com.iot.platform.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 设备影子写入(模拟设备上报属性 / 平台下行)
 */
@Data
public class IotDeviceShadowDTO {

    @NotNull(message = "请选择产品")
    private Long productId;

    /** 物模型属性标识 */
    @NotBlank(message = "请选择属性")
    private String identifier;

    /** 属性值(任意类型,序列化为 JSON) */
    private Object value;
}