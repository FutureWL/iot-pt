package com.iot.platform.system.dict.service.impl;
import com.iot.platform.system.dict.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.HashMap; import java.util.Map;
@Service @RequiredArgsConstructor
public class SysDictServiceImpl implements SysDictService {
    @Override public Map<String, Object> pageTypes(Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("records", Collections.emptyList()); map.put("total", 0);
        return map;
    }
}
