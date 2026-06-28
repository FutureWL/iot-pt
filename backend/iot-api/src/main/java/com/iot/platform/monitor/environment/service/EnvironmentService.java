package com.iot.platform.monitor.environment.service;
import com.iot.platform.monitor.environment.vo.EnvironmentRealtimeVO;
import java.util.List;
public interface EnvironmentService { List<EnvironmentRealtimeVO> realtime(Long deviceId); }
