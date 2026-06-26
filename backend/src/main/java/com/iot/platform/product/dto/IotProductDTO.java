package com.iot.platform.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IotProductDTO {

    private Long id;

    @NotBlank(message = "产品 Key 不能为空")
    @Size(min = 2, max = 32, message = "产品 Key 长度 2-32")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,31}$",
             message = "产品 Key 需以字母开头,只能含字母数字下划线短横线")
    private String productKey;

    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128)
    private String productName;

    private String category;

    @Size(max = 500)
    private String description;

    /** deviceSecret / dynamic / none */
    @NotBlank(message = "请选择认证方式")
    private String authType;

    /** 0=直连 1=网关 2=网关子设备 */
    private Integer nodeType;

    /** MQTT / TCP */
    @NotBlank(message = "请选择联网方式")
    private String netType;

    private Integer status;
    private String icon;

    /** 物模型 JSON 字符串 */
    private String thingModel;
}