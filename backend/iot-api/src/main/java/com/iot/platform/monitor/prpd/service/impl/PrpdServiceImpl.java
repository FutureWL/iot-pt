package com.iot.platform.monitor.prpd.service.impl;
import com.iot.platform.monitor.prpd.service.PrpdService;
import com.iot.platform.monitor.prpd.vo.PrpdResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class PrpdServiceImpl implements PrpdService {
    @Override public PrpdResultVO latest(Long deviceId) { return null; }
}
