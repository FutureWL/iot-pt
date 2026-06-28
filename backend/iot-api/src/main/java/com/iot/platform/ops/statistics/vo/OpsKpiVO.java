package com.iot.platform.ops.statistics.vo;
import lombok.Data; import java.io.Serializable;
@Data public class OpsKpiVO implements Serializable {
    private String period, kpiType, group; private Double value; private Integer rank;
}
