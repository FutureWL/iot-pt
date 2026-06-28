package com.iot.platform.monitor.gis.vo;
import lombok.Data; import java.io.Serializable;
@Data public class GisDeviceVO implements Serializable {
    private Long deviceId; private String deviceKey, deviceName, address;
    private Double lng, lat; private Integer status, alertCount;
}
