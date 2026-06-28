package com.iot.platform.monitor.prpd.vo;
import lombok.Data; import java.io.Serializable;
@Data public class PrpdPointVO implements Serializable { private Double phase, amplitude; private Integer pulseCount; }
