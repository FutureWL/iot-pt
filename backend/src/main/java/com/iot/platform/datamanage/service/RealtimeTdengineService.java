package com.iot.platform.datamanage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时数据 - TDengine 查询层
 *
 * <p>利用 TDengine 时序库的 {@code max(ts) GROUP BY tbname} 拿到
 * "每个属性子表的最后上报时间戳",这比 MySQL {@code iot_device_property.updated_at}
 * 更精确——后者只在 upsert 时被覆写,而 TDengine 是真正的"最后一行"。</p>
 *
 * <p>子表命名规则(由 TdengineWriter 确定):
 * {@code d_{tenantId}_{deviceId}_{identifier}},类型分 5 个超级表
 * {@code iot_prop_int/bigint/double/bool/string}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeTdengineService {

    private static final String[] SUPER_TABLES = {"int", "bigint", "double", "bool", "string"};

    private final @Qualifier("tsDataSource") DataSource tsDataSource;

    /**
     * 批量查某租户下所有设备的所有属性的"最近上报时间戳"。
     *
     * @return Map&lt;deviceId, Map&lt;identifier, lastTsMs&gt;&gt;
     */
    public Map<Long, Map<String, Long>> getLatestTimestamps(Long tenantId) {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        if (tenantId == null) return result;

        // 子表前缀: d_{tenantId}_
        String tbPrefix = "d_" + tenantId + "_";

        for (String type : SUPER_TABLES) {
            String superTable = "iot_prop_" + type;
            String sql = "SELECT tbname, MAX(ts) FROM " + superTable +
                    " WHERE tenant_id = ? GROUP BY tbname";
            try (Connection conn = tsDataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, String.valueOf(tenantId));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tbname = rs.getString(1);
                        long maxTs = rs.getTimestamp(2) == null ? 0L : rs.getTimestamp(2).getTime();
                        if (tbname == null || !tbname.startsWith(tbPrefix)) continue;
                        // 解析: d_{tenantId}_{deviceId}_{identifier}
                        String rest = tbname.substring(tbPrefix.length());
                        int firstUs = rest.indexOf('_');
                        if (firstUs <= 0) continue;
                        long deviceId;
                        String identifier;
                        try {
                            deviceId = Long.parseLong(rest.substring(0, firstUs));
                            identifier = rest.substring(firstUs + 1);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        result.computeIfAbsent(deviceId, k -> new LinkedHashMap<>())
                                .put(identifier, maxTs);
                    }
                }
            } catch (Exception e) {
                log.warn("[TDengine 实时] 查询 {} 最近时间失败: {}", superTable, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 把 TDengine 查到的 ts 注入到 properties 列表(原地修改)
     */
    public void injectRecentTs(Long tenantId,
                               java.util.List<java.util.Map<String, Object>> devices) {
        if (tenantId == null || devices == null || devices.isEmpty()) return;
        Map<Long, Map<String, Long>> tsMap = getLatestTimestamps(tenantId);
        if (tsMap.isEmpty()) return;

        for (java.util.Map<String, Object> dev : devices) {
            Object did = dev.get("deviceId");
            if (!(did instanceof Number)) continue;
            long deviceId = ((Number) did).longValue();
            Map<String, Long> idMap = tsMap.get(deviceId);
            if (idMap == null) continue;
            Object propsObj = dev.get("properties");
            if (!(propsObj instanceof java.util.List<?>)) continue;
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> props =
                    (java.util.List<java.util.Map<String, Object>>) propsObj;
            for (java.util.Map<String, Object> p : props) {
                Object ident = p.get("identifier");
                if (!(ident instanceof String)) continue;
                Long ts = idMap.get(ident);
                if (ts != null && ts > 0) {
                    p.put("recentTs", ts);
                }
            }
        }
    }
}