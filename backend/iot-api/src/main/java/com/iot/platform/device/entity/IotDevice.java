package com.iot.platform.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备实体
 */
@Data
@TableName("iot_device")
public class IotDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long productId;
    private Long groupId;

    /** 设备唯一标识(MAC/序列号) */
    private String deviceKey;

    private String deviceName;

    @JsonIgnore
    private String deviceSecret;

    /** MQTT / TCP */
    private String protocol;

    /** 0=离线 1=在线 2=禁用 */
    private Integer status;

    private LocalDateTime activeTime;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime lastOfflineTime;
    private String ipAddress;
    private String firmwareVersion;
    private String location;
    private String tags;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}