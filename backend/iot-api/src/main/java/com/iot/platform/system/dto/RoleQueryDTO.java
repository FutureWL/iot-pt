package com.iot.platform.system.dto;

import lombok.Data;

@Data
public class RoleQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
}