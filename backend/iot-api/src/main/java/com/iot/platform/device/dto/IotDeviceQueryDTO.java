package com.iot.platform.device.dto;

import lombok.Data;

@Data
public class IotDeviceQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private Long productId;
    private Long groupId;
    private Integer status;
}