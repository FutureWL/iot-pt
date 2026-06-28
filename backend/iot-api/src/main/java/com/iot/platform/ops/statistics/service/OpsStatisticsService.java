package com.iot.platform.ops.statistics.service;
import com.iot.platform.ops.statistics.vo.OpsKpiSummaryVO;
import com.iot.platform.ops.statistics.vo.OpsKpiVO;
import java.util.List;
public interface OpsStatisticsService {
    OpsKpiSummaryVO summary(String range);
    List<OpsKpiVO> trend(String kpiType, String range);
    List<OpsKpiVO> groupRank(String kpiType);
    List<java.util.Map<String, Object>> faultType(String range);
}
