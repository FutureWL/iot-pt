package com.iot.platform.system.log.service.impl;
import com.iot.platform.system.log.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.HashMap; import java.util.Map;
@Service @RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {
    @Override public Map<String, Object> page(Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("records", Collections.emptyList()); map.put("total", 0);
        map.put("size", params.getOrDefault("size", 10));
        map.put("current", params.getOrDefault("current", 1));
        return map;
    }
}
