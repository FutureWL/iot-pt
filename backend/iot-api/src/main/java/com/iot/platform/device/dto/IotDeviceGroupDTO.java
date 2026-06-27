package com.iot.platform.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IotDeviceGroupDTO {
    private Long id;
    private Long parentId;

    @NotBlank(message = "分组名不能为空")
    @Size(max = 128)
    private String groupName;

    @Size(max = 500)
    private String description;

    private Integer sort;
}