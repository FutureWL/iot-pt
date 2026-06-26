package com.iot.platform.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iot.platform.common.BusinessException;
import com.iot.platform.device.dto.IotDeviceShadowDTO;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.device.service.IotDeviceShadowService;
import com.iot.platform.device.vo.IotDeviceShadowVO;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 设备影子服务
 *
 * <p>当前协议层还是骨架,没有真实设备上报,所以提供"手动 upsert"接口,方便测试和演示</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotDeviceShadowServiceImpl implements IotDeviceShadowService {

    private final IotDevicePropertyMapper propertyMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<IotDeviceShadowVO> list(Long deviceId) {
        Long tenantId = TenantContext.getTenantId();
        IotDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getId, deviceId)
                .eq(IotDevice::getTenantId, tenantId));
        if (d == null) throw new BusinessException("设备不存在");

        IotProduct p = productMapper.selectById(d.getProductId());
        // 解析物模型,得到所有属性定义
        Map<String, JsonNode> propDefs = parsePropertyDefs(p == null ? null : p.getThingModel());

        // 读影子
        List<IotDeviceProperty> shadows = propertyMapper.selectList(
                new LambdaQueryWrapper<IotDeviceProperty>()
                        .eq(IotDeviceProperty::getDeviceId, deviceId)
                        .orderByAsc(IotDeviceProperty::getIdentifier));

        // 合并: 物模型里有的属性都列出来,没上报的 value 为 null
        List<IotDeviceShadowVO> result = new ArrayList<>();
        // 用物模型属性顺序
        for (Map.Entry<String, JsonNode> e : propDefs.entrySet()) {
            JsonNode def = e.getValue();
            IotDeviceShadowVO vo = new IotDeviceShadowVO();
            vo.setDeviceId(deviceId);
            vo.setIdentifier(e.getKey());
            vo.setName(textOrEmpty(def, "name"));
            vo.setType(textOrEmpty(def, "type"));
            vo.setUnit(textOrEmpty(def, "unit"));
            vo.setAccessMode(textOrEmpty(def, "accessMode"));
            // 找当前影子
            shadows.stream()
                    .filter(s -> s.getIdentifier().equals(e.getKey()))
                    .findFirst()
                    .ifPresent(s -> {
                        vo.setId(s.getId());
                        vo.setValueJson(s.getValueJson());
                        vo.setUpdatedAt(s.getUpdatedAt());
                    });
            result.add(vo);
        }
        // 影子中物模型没有的(动态添加的),也展示
        for (IotDeviceProperty s : shadows) {
            if (!propDefs.containsKey(s.getIdentifier())) {
                IotDeviceShadowVO vo = new IotDeviceShadowVO();
                vo.setId(s.getId());
                vo.setDeviceId(deviceId);
                vo.setIdentifier(s.getIdentifier());
                vo.setValueJson(s.getValueJson());
                vo.setUpdatedAt(s.getUpdatedAt());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(Long deviceId, IotDeviceShadowDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        IotDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getId, deviceId)
                .eq(IotDevice::getTenantId, tenantId));
        if (d == null) throw new BusinessException("设备不存在");

        // 校验 identifier 是否在物模型中(若有物模型)
        IotProduct p = productMapper.selectById(d.getProductId());
        if (p != null) {
            Map<String, JsonNode> defs = parsePropertyDefs(p.getThingModel());
            if (!defs.containsKey(dto.getIdentifier())) {
                throw new BusinessException("属性「" + dto.getIdentifier() + "」不在该产品物模型中");
            }
        }

        String valueJson = serializeValue(dto.getValue());
        IotDeviceProperty exist = propertyMapper.selectOne(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId)
                .eq(IotDeviceProperty::getIdentifier, dto.getIdentifier()));
        if (exist == null) {
            IotDeviceProperty p2 = new IotDeviceProperty();
            p2.setTenantId(tenantId);
            p2.setDeviceId(deviceId);
            p2.setIdentifier(dto.getIdentifier());
            p2.setValueJson(valueJson);
            propertyMapper.insert(p2);
            log.info("影子写入(新增): deviceId={}, id={}, value={}", deviceId, dto.getIdentifier(), valueJson);
        } else {
            exist.setValueJson(valueJson);
            exist.setUpdatedAt(LocalDateTime.now());
            propertyMapper.updateById(exist);
            log.info("影子写入(更新): deviceId={}, id={}, value={}", deviceId, dto.getIdentifier(), valueJson);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(Long deviceId, String identifier) {
        Long tenantId = TenantContext.getTenantId();
        propertyMapper.delete(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId)
                .eq(IotDeviceProperty::getIdentifier, identifier)
                .eq(IotDeviceProperty::getTenantId, tenantId));
    }

    // ============ 辅助 ============

    private Map<String, JsonNode> parsePropertyDefs(String thingModelJson) {
        Map<String, JsonNode> map = new java.util.LinkedHashMap<>();
        if (StrUtil.isBlank(thingModelJson)) return map;
        try {
            JsonNode root = objectMapper.readTree(thingModelJson);
            JsonNode props = root.path("properties");
            if (props.isArray()) {
                for (JsonNode n : props) {
                    String id = n.path("identifier").asText();
                    if (!id.isEmpty()) map.put(id, n);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String serializeValue(Object value) {
        try {
            if (value == null) return "null";
            if (value instanceof String) {
                String s = (String) value;
                // 如果是合法 JSON,直接用;否则包成字符串
                try {
                    objectMapper.readTree(s);
                    return s;
                } catch (Exception e) {
                    return objectMapper.writeValueAsString(s);
                }
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + value + "\"";
        }
    }
}