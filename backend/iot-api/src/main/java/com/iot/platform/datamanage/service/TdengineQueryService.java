package com.iot.platform.datamanage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TDengine 历史数据查询
 */
@Slf4j
@Service
public class TdengineQueryService {

    private final DataSource tsDataSource;

    public TdengineQueryService(@Qualifier("tsDataSource") DataSource tsDataSource) {
        this.tsDataSource = tsDataSource;
    }

    /**
     * 查询某设备某属性在时间范围内的数据
     *
     * @param deviceId    设备 id
     * @param identifier  属性标识
     * @param type        int/bigint/double/bool/string
     * @param tenantId    租户
     * @param startMs     开始时间(毫秒)
     * @param endMs       结束时间(毫秒)
     * @return list of [timestampMs, valueString]
     */
    public List<Map<String, Object>> query(Long tenantId, Long deviceId, String identifier,
                                           String type, long startMs, long endMs) throws Exception {
        if (type == null || type.isEmpty()) type = "double";
        String superTable = "iot_prop_" + type;
        String childTable = "d_" + tenantId + "_" + deviceId + "_" + safeIdent(identifier);

        // 用 LIMIT 截断避免一次拉太多
        String sql = "SELECT ts, val FROM " + childTable +
                " WHERE ts >= ? AND ts <= ? ORDER BY ts ASC LIMIT 5000";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = tsDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(startMs));
            ps.setTimestamp(2, new Timestamp(endMs));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("ts", rs.getTimestamp(1).getTime());
                    point.put("value", rs.getString(2));
                    result.add(point);
                }
            }
        } catch (Exception e) {
            // 子表不存在(设备从未上报过数据) → 返回空,不报 500
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("does not exist")) {
                log.info("[TDengine] 子表 {} 不存在,返回空(设备可能未上报过数据)", childTable);
                return List.of();
            }
            throw e;
        }
        return result;
    }

    /**
     * 简单聚合: 某设备某属性在时间范围内的最大/最小/平均
     */
    public Map<String, Object> stats(Long tenantId, Long deviceId, String identifier,
                                     String type, long startMs, long endMs) throws Exception {
        if (type == null || type.isEmpty()) type = "double";
        String superTable = "iot_prop_" + type;
        String childTable = "d_" + tenantId + "_" + deviceId + "_" + safeIdent(identifier);
        String sql = "SELECT COUNT(*), MIN(val), MAX(val), AVG(val) FROM " + childTable +
                " WHERE ts >= ? AND ts <= ?";
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = tsDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(startMs));
            ps.setTimestamp(2, new Timestamp(endMs));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("count", rs.getLong(1));
                    result.put("min", rs.getString(2));
                    result.put("max", rs.getString(3));
                    result.put("avg", rs.getString(4));
                }
            }
        } catch (Exception e) {
            // 子表不存在(设备从未上报过数据) → 返回空统计
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("does not exist")) {
                log.info("[TDengine] 子表 {} 不存在,返回空统计", childTable);
                result.put("count", 0L);
                result.put("min", null);
                result.put("max", null);
                result.put("avg", null);
                return result;
            }
            throw e;
        }
        return result;
    }

    private String safeIdent(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}