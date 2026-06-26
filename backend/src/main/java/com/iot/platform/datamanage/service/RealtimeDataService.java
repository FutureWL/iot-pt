package com.iot.platform.datamanage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实时数据: 从 MySQL 影子读所有在线设备的当前属性
 */
@Service
@RequiredArgsConstructor
public class RealtimeDataService {

    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;

    /**
     * 拉取该租户下所有 status=1 的设备,展开其物模型属性 + 当前影子值
     */
    public List<Map<String, Object>> listLive() {
        Long tenantId = TenantContext.getTenantId();
        List<IotDevice> devices = deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .orderByDesc(IotDevice::getLastOnlineTime));
        if (devices.isEmpty()) return List.of();

        List<Long> deviceIds = devices.stream().map(IotDevice::getId).toList();
        // 一次查所有影子
        List<IotDeviceProperty> allShadows = propertyMapper.selectList(
                new LambdaQueryWrapper<IotDeviceProperty>()
                        .in(IotDeviceProperty::getDeviceId, deviceIds)
                        .orderByAsc(IotDeviceProperty::getIdentifier));
        Map<Long, List<IotDeviceProperty>> shadowByDevice = new HashMap<>();
        for (IotDeviceProperty p : allShadows) {
            shadowByDevice.computeIfAbsent(p.getDeviceId(), k -> new ArrayList<>()).add(p);
        }

        // 缓存产品物模型
        Map<Long, IotProduct> productCache = new HashMap<>();
        for (IotDevice d : devices) {
            if (!productCache.containsKey(d.getProductId())) {
                productCache.put(d.getProductId(),
                        productMapper.selectById(d.getProductId()));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (IotDevice d : devices) {
            IotProduct p = productCache.get(d.getProductId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", d.getId());
            item.put("deviceKey", d.getDeviceKey());
            item.put("deviceName", d.getDeviceName());
            item.put("productKey", p == null ? null : p.getProductKey());
            item.put("productName", p == null ? null : p.getProductName());
            item.put("status", d.getStatus());
            item.put("lastOnlineTime", d.getLastOnlineTime());
            item.put("location", d.getLocation());
            item.put("tags", d.getTags());

            // 物模型属性 + 影子值
            List<Map<String, Object>> props = new ArrayList<>();
            Map<String, JsonNodeExt> propDefs = parsePropertyDefs(p == null ? null : p.getThingModel());
            List<IotDeviceProperty> shadows = shadowByDevice.getOrDefault(d.getId(), List.of());
            // 先按物模型顺序
            for (Map.Entry<String, JsonNodeExt> e : propDefs.entrySet()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("identifier", e.getKey());
                prop.put("name", e.getValue().name);
                prop.put("type", e.getValue().type);
                prop.put("unit", e.getValue().unit);
                prop.put("accessMode", e.getValue().accessMode);
                String val = shadows.stream()
                        .filter(s -> s.getIdentifier().equals(e.getKey()))
                        .findFirst()
                        .map(IotDeviceProperty::getValueJson)
                        .orElse(null);
                prop.put("value", val);
                prop.put("updatedAt", shadows.stream()
                        .filter(s -> s.getIdentifier().equals(e.getKey()))
                        .findFirst()
                        .map(IotDeviceProperty::getUpdatedAt)
                        .orElse(null));
                props.add(prop);
            }
            item.put("properties", props);
            result.add(item);
        }
        return result;
    }

    private Map<String, JsonNodeExt> parsePropertyDefs(String tsl) {
        Map<String, JsonNodeExt> map = new LinkedHashMap<>();
        if (tsl == null || tsl.isEmpty()) return map;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(tsl);
            com.fasterxml.jackson.databind.JsonNode props = root.path("properties");
            if (props.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : props) {
                    String id = n.path("identifier").asText();
                    if (!id.isEmpty()) {
                        map.put(id, new JsonNodeExt(
                                n.path("name").asText(),
                                n.path("type").asText(),
                                n.path("unit").asText(),
                                n.path("accessMode").asText()));
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static class JsonNodeExt {
        String name, type, unit, accessMode;
        JsonNodeExt(String n, String t, String u, String a) { name=n; type=t; unit=u; accessMode=a; }
    }
}