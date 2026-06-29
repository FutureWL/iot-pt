package com.iot.platform.datamanage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * TDengine 时序写入
 *
 * <p>按值类型分发到对应超级表:int/bigint/double/bool/string</p>
 */
@Slf4j
@Service
public class TdengineWriter {

    private final DataSource tsDataSource;
    private final ObjectMapper objectMapper;

    public TdengineWriter(@Qualifier("tsDataSource") DataSource tsDataSource, ObjectMapper objectMapper) {
        this.tsDataSource = tsDataSource;
        this.objectMapper = objectMapper;
    }

    @Async("ruleEngineExecutor")
    public void writeAsync(Long tenantId, Long deviceId, String productKey, String deviceKey,
                           String identifier, String valueJson) {
        try {
            write(tenantId, deviceId, productKey, deviceKey, identifier, valueJson);
        } catch (Exception e) {
            log.error("[TDengine] 写入失败: device={} id={} value={}", deviceKey, identifier, valueJson, e);
        }
    }

    public void write(Long tenantId, Long deviceId, String productKey, String deviceKey,
                      String identifier, String valueJson) throws Exception {
        String type = inferType(valueJson);
        String superTable = "iot_prop_" + type;
        String childTable = "d_" + tenantId + "_" + deviceId + "_" + safeIdent(identifier);

        String sql = "INSERT INTO " + childTable +
                " USING " + superTable +
                " TAGS (?, ?, ?, ?, ?) " +
                " VALUES (NOW, ?)";

        try (Connection conn = tsDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(tenantId));
            ps.setString(2, productKey);
            ps.setString(3, String.valueOf(deviceId));
            ps.setString(4, deviceKey);
            ps.setString(5, identifier);
            ps.setString(6, typedValue(valueJson, type));
            ps.executeUpdate();
        } catch (Exception first) {
            // 第一次可能因为子表不存在,自动建超级表后重试
            if (first.getMessage() != null && first.getMessage().toLowerCase().contains("does not exist")) {
                ensureSuperTable(superTable);
                try (Connection conn = tsDataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, String.valueOf(tenantId));
                    ps.setString(2, productKey);
                    ps.setString(3, String.valueOf(deviceId));
                    ps.setString(4, deviceKey);
                    ps.setString(5, identifier);
                    ps.setString(6, typedValue(valueJson, type));
                    ps.executeUpdate();
                }
            } else {
                throw first;
            }
        }
    }

    private String inferType(String v) {
        if (v == null || v.equals("null")) return "string";
        try {
            JsonNode n = objectMapper.readTree(v);
            if (n.isBoolean()) return "bool";
            if (n.isInt() || n.isLong()) return "bigint";
            if (n.isNumber()) return "double";
            if (n.isTextual()) return "string";
        } catch (Exception ignored) {}
        return "string";
    }

    private String typedValue(String v, String type) {
        if (v == null) return "0";
        if ("bool".equals(type)) {
            return v.equals("true") || v.equals("1") ? "true" : "false";
        }
        if ("string".equals(type)) {
            String s = v;
            if (s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1);
            }
            return s.replace("'", "''");
        }
        return v;
    }

    private String safeIdent(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * 确保超级表存在(创建如果不存在)
     * public 让 TdengineSchemaInitializer 在启动时调用,
     * 避免第一次查询时表不存在的 500
     */
    public void ensureSuperTable(String superTable) {
        try (Connection conn = tsDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + superTable + " " +
                             "(ts TIMESTAMP, val " + valueColumnType(superTable) + ") " +
                             "TAGS (tenant_id BIGINT, product_key BINARY(32), device_id BIGINT, device_key BINARY(64), identifier BINARY(64))")) {
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("[TDengine] 建表 {} 失败(可能已存在): {}", superTable, e.getMessage());
        }
    }

    private String valueColumnType(String superTable) {
        if (superTable.endsWith("int")) return "INT";
        if (superTable.endsWith("bigint")) return "BIGINT";
        if (superTable.endsWith("double")) return "DOUBLE";
        if (superTable.endsWith("bool")) return "BOOL";
        return "NCHAR(255)";
    }
}