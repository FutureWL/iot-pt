package com.iot.platform.monitor.temperature.vo;
import lombok.Data; import java.io.Serializable;
@Data public class TemperaturePointVO implements Serializable {
    private String sensorId, deviceKey, location;
    private Double temperature; private Integer batteryLevel; private String ts;
}
