package com.iot.platform.monitor.pd.vo;
import lombok.Data; import java.io.Serializable;
@Data public class PdRealtimeVO implements Serializable {
    private Long deviceId; private String deviceKey, deviceName;
    private Double amplitude; private Integer pulseCount;
    private String channelType; private Double phaseAngle; private Double threshold;
    private String status; private String ts;
}
