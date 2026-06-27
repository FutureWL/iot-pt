package com.iot.platform.datamanage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 实时数据:Redis 缓存 + MySQL 影子 + TDengine recentTs 注入
 *
 * <p>读路径:
 * <ol>
 *   <li>先查 Redis({@code iot:tenant:{tid}:realtime:v1},TTL 5s)</li>
 *   <li>miss 则查 MySQL(原行为,不变)</li>
 *   <li>查 TDengine {@code max(ts) GROUP BY tbname},给每个属性注入 {@code recentTs}</li>
 *   <li>JSON 序列化写回 Redis,返回</li>
 * </ol>
 *
 * <p>WebSocket 增量更新由 {@code MqttProtocolAdapter} publish {@code shadow.update}
 * 直接到前端,不走这条路径(避免 cache 里残留旧值)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeDataService {

    /** 缓存 key 前缀。版本号 v1 用来以后结构变更时手动失效。 */
    private static final String CACHE_KEY_PREFIX = "iot:tenant:";
    private static final String CACHE_KEY_SUFFIX = ":realtime:v1";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RealtimeTdengineService tdengineService;

    /**
     * 拉取该租户下所有设备的当前属性快照(走 Redis 缓存)
     */
    public List<Map<String, Object>> listLive() {
        Long tenantId = TenantContext.getTenantId();

        // 1) Redis 命中
        String cacheKey = cacheKey(tenantId);
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                List<Map<String, Object>> hit = objectMapper.readValue(
                        cached, new TypeReference<List<Map<String, Object>>>() {});
                log.info("[实时数据] Redis 命中 tenant={}, size={} 设备", tenantId,
                        hit == null ? 0 : hit.size());
                return hit;
            }
        } catch (Exception e) {
            log.warn("[实时数据] Redis 读失败,降级查 MySQL: {}", e.getMessage());
        }

        // 2) MySQL 查(原逻辑)
        List<Map<String, Object>> devices = loadFromMySQL(tenantId);

        // 3) TDengine 注入 recentTs(失败不影响主流程)
        try {
            tdengineService.injectRecentTs(tenantId, devices);
        } catch (Exception e) {
            log.warn("[实时数据] TDengine 注入 recentTs 失败: {}", e.getMessage());
        }

        // 4) 回填 Redis
        try {
            String json = objectMapper.writeValueAsString(devices);
            redis.opsForValue().set(cacheKey, json, CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
            log.debug("[实时数据] 已写 Redis key={}, jsonLen={}", cacheKey, json.length());
        } catch (Exception e) {
            log.warn("[实时数据] Redis 写失败: {}", e.getMessage());
        }

        return devices;
    }

    /**
     * 强制失效缓存(协议上报、设备状态变更时可调用;但 WS 已经实时推,这里更多是兜底)
     */
    public void invalidateCache() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;
        try {
            redis.delete(cacheKey(tenantId));
        } catch (Exception e) {
            log.debug("[实时数据] 失效缓存失败(忽略): {}", e.getMessage());
        }
    }

    // ============== MySQL 查询(原逻辑,等价迁移) ==============

    private List<Map<String, Object>> loadFromMySQL(Long tenantId) {
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

    private String cacheKey(Long tenantId) {
        return CACHE_KEY_PREFIX + (tenantId == null ? "0" : tenantId) + CACHE_KEY_SUFFIX;
    }
}