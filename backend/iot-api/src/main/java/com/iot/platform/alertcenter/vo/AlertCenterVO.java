package com.iot.platform.alertcenter.vo;
import lombok.Data; import java.io.Serializable;
@Data public class AlertCenterVO implements Serializable {
    private Long id; private String level, alertType;
    private Long deviceId; private String deviceKey, deviceName, productKey;
    private String title, content, handler;
    private Integer status, workOrderId;
    private String alertTime, handleTime;
}
