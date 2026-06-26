package com.iot.platform.debug;

import cn.hutool.core.util.StrUtil;
import com.iot.platform.common.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 设备模拟器代理 - 给前端调用,内部用 Socket 模拟设备上报到 33410
 */
@Tag(name = "设备模拟器")
@Slf4j
@RestController
@RequestMapping("/debug/tcp")
public class DeviceSimulatorController {

    private static final int TCP_PORT = 33410;
    private static final int TIMEOUT_MS = 5000;

    @Operation(summary = "模拟 TCP 设备上报一条数据")
    @PostMapping("/simulate")
    public Map<String, Object> simulate(@RequestBody SimulateRequest req) throws Exception {
        if (StrUtil.isBlank(req.getProductKey())) throw new BusinessException("productKey 不能为空");
        if (StrUtil.isBlank(req.getDeviceKey())) throw new BusinessException("deviceKey 不能为空");
        if (StrUtil.isBlank(req.getSecret())) throw new BusinessException("secret 不能为空");
        if (req.getData() == null || req.getData().isEmpty()) throw new BusinessException("data 不能为空");
        if (StrUtil.isBlank(req.getType())) req.setType("property");
        if (req.getTs() == null) req.setTs(System.currentTimeMillis());

        // TCP 一次连接完成: 写 auth + 写 N 个 property 帧 + 收所有 ACK + 关闭
        try (Socket sock = new Socket("localhost", TCP_PORT)) {
            sock.setSoTimeout(TIMEOUT_MS);
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))) {

                // 1) auth
                Map<String, Object> auth = new LinkedHashMap<>();
                auth.put("type", "auth");
                auth.put("productKey", req.getProductKey());
                auth.put("deviceKey", req.getDeviceKey());
                auth.put("secret", req.getSecret());
                w.write(toJson(auth));
                w.newLine();
                w.flush();

                Map<String, Object> authResp = readOneJson(r);
                if (authResp == null || !Boolean.TRUE.equals(authResp.get("ok"))) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("ok", false);
                    err.put("stage", "auth");
                    err.put("response", authResp);
                    return err;
                }

                // 2) 单帧 property
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("type", req.getType());
                frame.put("ts", req.getTs());
                if ("property".equals(req.getType())) {
                    frame.put("data", req.getData());
                } else if ("event".equals(req.getType())) {
                    frame.put("name", req.getData().get("name"));
                    frame.put("value", req.getData());
                }
                w.write(toJson(frame));
                w.newLine();
                w.flush();

                Map<String, Object> resp = readOneJson(r);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("auth", authResp);
                result.put("ack", resp);
                return result;
            }
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("stage", "io");
            err.put("error", e.getMessage());
            return err;
        }
    }

    @Operation(summary = "生成模拟数据(随机/递增/常量)")
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody GenerateRequest req) {
        Random r = new Random();
        Map<String, Object> data = new LinkedHashMap<>();
        if (req.getSpec() == null) return data;
        for (Map.Entry<String, Object> e : req.getSpec().entrySet()) {
            String identifier = e.getKey();
            Object spec = e.getValue();
            if (!(spec instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> s = (Map<String, Object>) spec;
            String type = String.valueOf(s.getOrDefault("type", "float"));
            Object mode = s.get("mode"); // random/inc/const
            Object min = s.get("min");
            Object max = s.get("max");
            Object value = s.get("value");
            int seq = req.getSeq() == null ? 0 : req.getSeq();

            double lo = toDouble(min, 0);
            double hi = toDouble(max, 100);
            switch (type) {
                case "int": {
                    int v;
                    if ("inc".equals(mode)) v = toInt(value, 0) + seq;
                    else if ("const".equals(mode)) v = toInt(value, 0);
                    else v = lo == hi ? (int) lo : (int) (lo + r.nextInt((int) (hi - lo + 1)));
                    data.put(identifier, v);
                    break;
                }
                case "float": {
                    double v;
                    if ("inc".equals(mode)) v = toDouble(value, 0) + seq * 0.5;
                    else if ("const".equals(mode)) v = toDouble(value, 0);
                    else v = lo + r.nextDouble() * (hi - lo);
                    data.put(identifier, Math.round(v * 100.0) / 100.0);
                    break;
                }
                case "bool": {
                    boolean v;
                    if ("const".equals(mode)) v = Boolean.TRUE.equals(value);
                    else v = r.nextBoolean();
                    data.put(identifier, v);
                    break;
                }
                case "string": {
                    data.put(identifier, value == null ? "value-" + seq : String.valueOf(value));
                    break;
                }
                default:
                    data.put(identifier, value);
            }
        }
        return data;
    }

    private String toJson(Map<String, Object> m) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.writeValueAsString(m);
        } catch (Exception e) {
            log.error("toJson 失败: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readOneJson(BufferedReader r) throws IOException {
        String line = r.readLine();
        if (line == null) return null;
        // 用 ObjectMapper 简单解析 - 这里直接注入 service
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        return om.readValue(line, Map.class);
    }

    private double toDouble(Object o, double dft) {
        if (o == null) return dft;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return dft; }
    }

    private int toInt(Object o, int dft) {
        if (o == null) return dft;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return dft; }
    }

    // ============== DTO ==============

    public static class SimulateRequest {
        private String productKey;
        private String deviceKey;
        private String secret;
        private String type;     // property / event
        private Long ts;
        private Map<String, Object> data;
        public String getProductKey() { return productKey; }
        public void setProductKey(String v) { this.productKey = v; }
        public String getDeviceKey() { return deviceKey; }
        public void setDeviceKey(String v) { this.deviceKey = v; }
        public String getSecret() { return secret; }
        public void setSecret(String v) { this.secret = v; }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public Long getTs() { return ts; }
        public void setTs(Long v) { this.ts = v; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> v) { this.data = v; }
    }

    public static class GenerateRequest {
        private Integer seq;
        private Map<String, Object> spec;
        public Integer getSeq() { return seq; }
        public void setSeq(Integer v) { this.seq = v; }
        public Map<String, Object> getSpec() { return spec; }
        public void setSpec(Map<String, Object> v) { this.spec = v; }
    }
}