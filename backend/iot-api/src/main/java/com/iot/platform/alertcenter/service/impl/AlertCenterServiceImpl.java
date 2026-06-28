package com.iot.platform.alertcenter.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.alertcenter.service.AlertCenterService;
import com.iot.platform.alertcenter.vo.AlertCenterVO;
import com.iot.platform.alertcenter.vo.AlertLevelStatVO;
import com.iot.platform.rule.entity.IotAlert;
import com.iot.platform.rule.mapper.IotAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AlertCenterServiceImpl implements AlertCenterService {
    private final IotAlertMapper alertMapper;

    @Override
    public List<AlertLevelStatVO> stats() {
        List<IotAlert> alerts = alertMapper.selectList(
                new LambdaQueryWrapper<IotAlert>().eq(IotAlert::getStatus, 0));
        Map<String, Long> countByLevel = alerts.stream()
                .collect(Collectors.groupingBy(IotAlert::getLevel, Collectors.counting()));
        List<AlertLevelStatVO> result = new ArrayList<>();
        countByLevel.forEach((k, v) -> {
            AlertLevelStatVO vo = new AlertLevelStatVO();
            vo.setLevel(k); vo.setCount(v.intValue()); result.add(vo);
        });
        return result;
    }

    @Override
    public Map<String, Object> page(Map<String, Object> params) {
        Integer current = safeInt(params.get("current"), 1);
        Integer size = safeInt(params.get("size"), 10);
        String level = (String) params.get("level");
        Integer status = (Integer) params.get("status");

        Page<IotAlert> p = new Page<>(current, size);
        LambdaQueryWrapper<IotAlert> wrapper = new LambdaQueryWrapper<>();
        if (level != null) wrapper.eq(IotAlert::getLevel, level);
        if (status != null) wrapper.eq(IotAlert::getStatus, status);
        wrapper.orderByDesc(IotAlert::getCreatedAt);
        Page<IotAlert> result = alertMapper.selectPage(p, wrapper);

        Map<String, Object> map = new HashMap<>();
        List<AlertCenterVO> records = new ArrayList<>();
        for (IotAlert a : result.getRecords()) records.add(toVO(a));
        map.put("records", records);
        map.put("total", result.getTotal());
        map.put("size", result.getSize());
        map.put("current", result.getCurrent());
        return map;
    }

    private AlertCenterVO toVO(IotAlert a) {
        AlertCenterVO v = new AlertCenterVO();
        v.setId(a.getId());
        v.setLevel(a.getLevel());
        v.setDeviceId(a.getDeviceId());
        v.setDeviceKey(a.getDeviceKey());
        v.setProductKey(a.getProductKey());
        v.setTitle(a.getTitle());
        v.setContent(a.getContent());
        v.setHandler(a.getHandler());
        v.setStatus(a.getStatus());
        v.setAlertTime(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        v.setHandleTime(a.getHandleTime() != null ? a.getHandleTime().toString() : null);
        return v;
    }

    /** 安全转为 Integer:支持 String / Integer / Number(避免 ClassCastException) */
    private static Integer safeInt(Object v, Integer defaultVal) {
        if (v == null) return defaultVal;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

}
