package com.iot.platform.monitor.temperature.vo;
import lombok.Data; import java.io.Serializable;
@Data public class TemperatureStatsVO implements Serializable {
    private Double max, avg, min; private Integer alertCount, sensorCount;
}
