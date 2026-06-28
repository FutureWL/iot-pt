package com.iot.platform.monitor.gis.service.impl;
import com.iot.platform.monitor.gis.service.GisService;
import com.iot.platform.monitor.gis.vo.GisDeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.List;
@Service @RequiredArgsConstructor
public class GisServiceImpl implements GisService {
    @Override public List<GisDeviceVO> devices() { return Collections.emptyList(); }
}
