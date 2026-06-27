package com.iot.platform.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品 + 物模型
 *
 * <p>thing_model 字段以 JSON 字符串存储,业务层用 ObjectMapper 读写</p>
 */
@Data
@TableName("iot_product")
public class IotProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 产品 Key,英文,设备会用 */
    private String productKey;

    private String productName;

    private String category;

    private String description;

    /** deviceSecret / dynamic / none */
    private String authType;

    /** 0=直连 1=网关 2=网关子设备 */
    private Integer nodeType;

    /** MQTT / TCP */
    private String netType;

    private Integer status;
    private String icon;

    /** 物模型 JSON 字符串 */
    private String thingModel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}