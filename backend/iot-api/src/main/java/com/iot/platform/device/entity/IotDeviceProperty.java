package com.iot.platform.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备影子(属性当前值)
 *
 * <p>无 deleted 字段,删除为物理删除</p>
 */
@Data
@TableName("iot_device_property")
public class IotDeviceProperty implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long deviceId;

    /** 物模型属性标识 */
    private String identifier;

    /** 属性值,JSON 格式存储 */
    private String valueJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}