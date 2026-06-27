package com.iot.platform.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IotDeviceDTO {

    /** 编辑时必传 */
    private Long id;

    @NotNull(message = "请选择产品")
    private Long productId;

    @NotBlank(message = "设备 Key 不能为空")
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_:-]{2,64}$",
             message = "设备 Key 只能含字母数字下划线短横线冒号")
    private String deviceKey;

    @NotBlank(message = "设备名不能为空")
    @Size(max = 128)
    private String deviceName;

    /** 留空=不修改;新建时后端自动生成 */
    private String deviceSecret;

    private Long groupId;
    private String location;
    private String tags;
    private String description;
}