package com.iot.platform.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class IotProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String productKey;
    private String productName;
    private String category;
    private String description;
    private String authType;
    private Integer nodeType;
    private String netType;
    private Integer status;
    private String icon;

    /** 物模型 JSON 字符串,前端按需 JSON.parse */
    private String thingModel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}