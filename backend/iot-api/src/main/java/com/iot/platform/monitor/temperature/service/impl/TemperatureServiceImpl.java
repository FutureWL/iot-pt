package com.iot.platform.monitor.temperature.service.impl;
import com.iot.platform.monitor.temperature.service.TemperatureService;
import com.iot.platform.monitor.temperature.vo.TemperaturePointVO;
import com.iot.platform.monitor.temperature.vo.TemperatureStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.List;
@Service @RequiredArgsConstructor
public class TemperatureServiceImpl implements TemperatureService {
    @Override public TemperatureStatsVO stats(Long deviceId) { return new TemperatureStatsVO(); }
    @Override public List<TemperaturePointVO> points(Long deviceId, String location) { return Collections.emptyList(); }
}
