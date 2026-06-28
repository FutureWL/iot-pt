package com.iot.platform.monitor.prpd.vo;
import lombok.Data; import java.io.Serializable; import java.util.List;
@Data public class PrpdResultVO implements Serializable {
    private Long deviceId; private String deviceKey, deviceName, dischargeType, collectedAt;
    private Double confidence; private Integer pointCount; private List<PrpdPointVO> points;
}
