package com.iot.platform.monitor.temperature.service;
import com.iot.platform.monitor.temperature.vo.TemperaturePointVO;
import com.iot.platform.monitor.temperature.vo.TemperatureStatsVO;
import java.util.List;
public interface TemperatureService {
    TemperatureStatsVO stats(Long deviceId);
    List<TemperaturePointVO> points(Long deviceId, String location);
}
