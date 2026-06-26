package com.iot.platform.device.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备影子视图(某设备某属性的当前值)
 */
@Data
public class IotDeviceShadowVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long deviceId;
    private String identifier;

    /** 物模型里的属性定义(可空,允许影子比模型超前) */
    private String name;
    private String type;
    private String unit;
    private String accessMode;

    /** 当前值(原始 JSON 字符串) */
    private String valueJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}