package com.iot.platform.monitor.environment.service.impl;
import com.iot.platform.monitor.environment.service.EnvironmentService;
import com.iot.platform.monitor.environment.vo.EnvironmentRealtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.List;
@Service @RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {
    @Override public List<EnvironmentRealtimeVO> realtime(Long deviceId) { return Collections.emptyList(); }
}
