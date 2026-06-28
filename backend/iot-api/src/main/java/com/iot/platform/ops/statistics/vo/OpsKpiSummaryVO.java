package com.iot.platform.ops.statistics.vo;
import lombok.Data; import java.io.Serializable;
@Data public class OpsKpiSummaryVO implements Serializable {
    private Double slaRate, avgResponseMin, faultRate;
    private Long totalWorkOrders, totalAlerts;
}
