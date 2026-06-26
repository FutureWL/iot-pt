package com.iot.platform.product.dto;

import lombok.Data;

@Data
public class IotProductQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String category;
    private String netType;
    private Integer status;
}