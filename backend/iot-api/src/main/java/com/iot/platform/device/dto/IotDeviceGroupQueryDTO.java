package com.iot.platform.device.dto;

import lombok.Data;

@Data
public class IotDeviceGroupQueryDTO {
    private Integer current = 1;
    private Integer size = 10;
    private String keyword;
}