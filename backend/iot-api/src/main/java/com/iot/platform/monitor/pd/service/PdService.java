package com.iot.platform.monitor.pd.service;
import com.iot.platform.monitor.pd.vo.PdRealtimeVO;
import java.util.List;
public interface PdService {
    List<PdRealtimeVO> realtime(Long deviceId);
}
