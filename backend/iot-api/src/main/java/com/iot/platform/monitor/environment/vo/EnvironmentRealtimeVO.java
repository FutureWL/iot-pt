package com.iot.platform.monitor.environment.vo;
import lombok.Data; import java.io.Serializable;
@Data public class EnvironmentRealtimeVO implements Serializable {
    private Long deviceId; private String deviceKey;
    private Double temperature, humidity; private Integer waterStatus;
    private Double tiltAngle, vibrationRMS; private Boolean condensationRisk; private String ts;
}
