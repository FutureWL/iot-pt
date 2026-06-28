package com.iot.platform.monitor.pd.service.impl;
import com.iot.platform.monitor.pd.service.PdService;
import com.iot.platform.monitor.pd.vo.PdRealtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.List;
@Service @RequiredArgsConstructor
public class PdServiceImpl implements PdService {
    @Override public List<PdRealtimeVO> realtime(Long deviceId) { return Collections.emptyList(); }
}
