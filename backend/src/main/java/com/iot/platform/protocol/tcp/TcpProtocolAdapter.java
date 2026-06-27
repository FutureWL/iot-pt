package com.iot.platform.protocol.tcp;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.IotProperties;
import com.iot.platform.datamanage.service.TdengineWriter;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.protocol.core.*;
import com.iot.platform.rule.event.PropertyReportEvent;
import com.iot.platform.websocket.WebSocketEventPublisher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP 协议适配器(Netty 实现)
 *
 * <p>每行一个 JSON 帧(以 \n 结尾),见类注释中的协议</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.protocol.tcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcpProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TdengineWriter tdengineWriter;
    private final WebSocketEventPublisher wsPublisher;

    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile boolean running = false;
    private MessageHandler handler;

    /** deviceId -> Channel(用于下行) */
    private final Map<Long, Channel> deviceChannels = new ConcurrentHashMap<>();

    @Override public String getName() { return "tcp"; }

    @PostConstruct
    public void init() {
        IotProperties.Protocol.Tcp cfg = properties.getProtocol().getTcp();
        log.info("[TCP] 适配器初始化 port={}", cfg.getPort());
    }

    @Override
    public void start() {
        IotProperties.Protocol.Tcp cfg = properties.getProtocol().getTcp();
        int port = cfg.getPort();
        if (port <= 0) {
            log.warn("[TCP] port 未配置,跳过启动");
            return;
        }
        bossGroup = new NioEventLoopGroup(cfg.getBossThreads());
        workerGroup = new NioEventLoopGroup(cfg.getWorkerThreads());
        try {
            bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // 60s 读空闲就触发心跳检测
                            p.addLast(new IdleStateHandler(60, 30, 0));
                            p.addLast(new LineBasedFrameDecoder(8192));
                            p.addLast(new StringDecoder(CharsetUtil.UTF_8));
                            p.addLast(new TcpServerHandler());
                        }
                    });
            ChannelFuture f = bootstrap.bind(port).sync();
            running = true;
            log.info("[TCP] 适配器已启动,监听 port={}", port);
        } catch (Exception e) {
            log.error("[TCP] 启动失败: {}", e.getMessage());
            shutdown();
        }
    }

    @Override
    public void stop() { running = false; shutdown(); }

    @PreDestroy
    public void destroy() { shutdown(); }

    private void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("[TCP] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }

    @Override
    public void setMessageHandler(MessageHandler h) { this.handler = h; }

    @Override
    public boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage) {
        if (session == null || !running) return false;
        // deviceId 从 attributes 里取(我们存了进去)
        Long deviceId = (Long) session.getAttributes().get("deviceId");
        if (deviceId == null) {
            log.warn("[TCP] 下行失败: session 缺少 deviceId");
            return false;
        }
        Channel ch = deviceChannels.get(deviceId);
        if (ch == null || !ch.isActive()) {
            log.warn("[TCP] 下行失败: 设备 [{}] 不在线", session.getDeviceKey());
            return false;
        }
        try {
            String line = objectMapper.writeValueAsString(downMessage) + "\n";
            ch.writeAndFlush(line);
            log.info("[TCP] 下行发送 device={} payload={}", session.getDeviceKey(), downMessage);
            return true;
        } catch (Exception e) {
            log.error("[TCP] 下行失败: {}", e.getMessage());
            return false;
        }
    }

    // ========== 业务 Handler ==========

    private class TcpServerHandler extends SimpleChannelInboundHandler<String> {

        /** 当前 channel 已认证的 device(未认证前为 null) */
        private IotDevice authedDevice;
        private String authedProductKey;
        private IotProduct authedProduct;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
            log.info("[TCP] 客户端连接: {}:{}", addr.getAddress().getHostAddress(), addr.getPort());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String line) {
            if (StrUtil.isBlank(line)) return;
            try {
                JsonNode frame = objectMapper.readTree(line);
                String type = frame.path("type").asText("");
                switch (type) {
                    case "auth":   handleAuth(ctx, frame); break;
                    case "property": handleProperty(ctx, frame); break;
                    case "event":  handleEvent(ctx, frame); break;
                    case "set_reply": handleSetReply(ctx, frame); break;
                    case "ping":   writeJson(ctx, objectMapper.createObjectNode().put("type", "pong")); break;
                    default: writeJson(ctx, err("未知消息类型: " + type));
                }
            } catch (Exception e) {
                log.warn("[TCP] 解析失败: {} | line={}", e.getMessage(), line);
                writeJson(ctx, err("JSON 解析失败: " + e.getMessage()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (authedDevice != null) {
                deviceChannels.remove(authedDevice.getId());
                markOffline(authedDevice);
                log.info("[TCP] 设备[{}]断开连接", authedDevice.getDeviceKey());
            } else {
                log.info("[TCP] 客户端断开(未认证)");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("[TCP] 异常: {}", cause.getMessage());
            ctx.close();
        }

        // ----- 各类消息处理 -----

        private void handleAuth(ChannelHandlerContext ctx, JsonNode f) {
            String productKey = f.path("productKey").asText("");
            String deviceKey = f.path("deviceKey").asText("");
            String secret = f.path("secret").asText("");

            IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                    .eq(IotProduct::getProductKey, productKey));
            if (product == null) {
                writeJson(ctx, err("产品 Key 不存在"));
                ctx.close();
                return;
            }
            IotDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                    .eq(IotDevice::getProductId, product.getId())
                    .eq(IotDevice::getDeviceKey, deviceKey));
            if (device == null) {
                writeJson(ctx, err("设备 Key 不存在"));
                ctx.close();
                return;
            }
            if (StrUtil.isNotBlank(secret) && !secret.equals(device.getDeviceSecret())) {
                log.warn("[TCP] 设备密钥不匹配: 输入={} DB={}", secret, device.getDeviceSecret());
                writeJson(ctx, err("设备密钥错误"));
                ctx.close();
                return;
            }
            if (device.getStatus() != null && device.getStatus() == 2) {
                writeJson(ctx, err("设备已被禁用"));
                ctx.close();
                return;
            }

            this.authedDevice = device;
            this.authedProduct = product;
            this.authedProductKey = productKey;
            deviceChannels.put(device.getId(), ctx.channel());

            // 标记在线
            markOnline(device, ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress());

            // 响应
            Map<String, Object> resp = new HashMap<>();
            resp.put("type", "auth");
            resp.put("ok", true);
            resp.put("productKey", productKey);
            resp.put("deviceKey", deviceKey);
            writeJson(ctx, resp);
            log.info("[TCP] 设备[{}]认证通过,已登记到 channel 映射", deviceKey);
        }

        private void handleProperty(ChannelHandlerContext ctx, JsonNode f) {
            if (authedDevice == null) {
                writeJson(ctx, err("未认证,请先发送 auth 帧"));
                return;
            }
            long ts = f.path("ts").asLong(System.currentTimeMillis());
            JsonNode data = f.path("data");
            if (!data.isObject()) {
                writeJson(ctx, err("data 字段必须是 JSON 对象"));
                return;
            }
            int count = 0;
            var fields = data.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                String identifier = e.getKey();
                JsonNode valueNode = e.getValue();
                if (!identifierInThingModel(authedProduct, identifier)) {
                    log.warn("[TCP] 属性[{}]不在物模型中,跳过", identifier);
                    continue;
                }
                String valueJson;
                try {
                    valueJson = valueNode.isTextual() ? valueNode.asText() : objectMapper.writeValueAsString(valueNode);
                } catch (Exception ex) {
                    valueJson = valueNode.toString();
                }
                upsertProperty(authedDevice.getTenantId(), authedDevice.getId(),
                        authedProductKey, authedDevice.getDeviceKey(),
                        identifier, valueJson);
                count++;
            }
            writeJson(ctx, objectMapper.createObjectNode()
                    .put("type", "ack")
                    .put("ts", ts)
                    .put("count", count));
            log.info("[TCP] 设备[{}]属性上报 {} 条", authedDevice.getDeviceKey(), count);
        }

        private void handleEvent(ChannelHandlerContext ctx, JsonNode f) {
            if (authedDevice == null) {
                writeJson(ctx, err("未认证"));
                return;
            }
            String name = f.path("name").asText("");
            if (name.isEmpty()) {
                writeJson(ctx, err("event 消息缺少 name 字段"));
                return;
            }
            log.info("[TCP] 设备[{}]事件上报: {}", authedDevice.getDeviceKey(), name);
            writeJson(ctx, objectMapper.createObjectNode()
                    .put("type", "ack")
                    .put("event", name));
            // TODO: 写入 iot_event (P5 增强时会用到)
        }

        private void handleSetReply(ChannelHandlerContext ctx, JsonNode f) {
            if (authedDevice == null) return;
            String id = f.path("identifier").asText("");
            boolean ok = f.path("success").asBoolean(false);
            log.info("[TCP] 设备[{}]属性设置回复: id={} ok={}", authedDevice.getDeviceKey(), id, ok);
        }

        // ----- 工具 -----

        private void writeJson(ChannelHandlerContext ctx, Object obj) {
            try {
                String s = objectMapper.writeValueAsString(obj);
                ByteBuf buf = ctx.alloc().buffer(s.length() + 1);
                buf.writeBytes(s.getBytes(CharsetUtil.UTF_8));
                buf.writeByte('\n');
                ctx.writeAndFlush(buf);
            } catch (Exception e) {
                log.error("[TCP] 响应写入失败: {}", e.getMessage());
            }
        }

        private Object err(String msg) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "err");
            m.put("message", msg);
            return m;
        }

        private void markOnline(IotDevice d, String ip) {
            boolean wasOnline = d.getStatus() != null && d.getStatus() == 1;
            d.setStatus(1);
            d.setLastOnlineTime(LocalDateTime.now());
            d.setIpAddress(ip);
            if (d.getActiveTime() == null) d.setActiveTime(LocalDateTime.now());
            deviceMapper.updateById(d);
            // 状态变化才推 WS
            if (!wasOnline) {
                try {
                    wsPublisher.publishDeviceStatus(d.getTenantId(), d.getId(),
                            d.getDeviceKey(), 1);
                } catch (Exception ignored) {}
            }
        }
    }

    // ========== 共享方法(被 handler 调用) ==========

    private boolean identifierInThingModel(IotProduct p, String id) {
        if (p == null || p.getThingModel() == null) return false;
        try {
            JsonNode root = objectMapper.readTree(p.getThingModel());
            JsonNode props = root.path("properties");
            if (props.isArray()) {
                for (JsonNode n : props) {
                    if (id.equals(n.path("identifier").asText())) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void upsertProperty(Long tenantId, Long deviceId, String productKey, String deviceKey,
                                 String identifier, String valueJson) {
        IotDeviceProperty exist = propertyMapper.selectOne(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId)
                .eq(IotDeviceProperty::getIdentifier, identifier));
        if (exist == null) {
            IotDeviceProperty p = new IotDeviceProperty();
            p.setTenantId(tenantId);
            p.setDeviceId(deviceId);
            p.setIdentifier(identifier);
            p.setValueJson(valueJson);
            propertyMapper.insert(p);
        } else {
            exist.setValueJson(valueJson);
            exist.setUpdatedAt(LocalDateTime.now());
            propertyMapper.updateById(exist);
        }
        try { tdengineWriter.writeAsync(tenantId, deviceId, productKey, deviceKey, identifier, valueJson); } catch (Exception ignored) {}
        // 触发规则引擎
        Map<String, Object> shadows = loadAllShadows(deviceId);
        Object parsed = parseValue(valueJson);
        try {
            eventPublisher.publishEvent(new PropertyReportEvent(
                    this, tenantId, deviceId,
                    Long.parseLong(String.valueOf(deviceId)),
                    deviceKey, productKey, identifier, parsed, shadows));
        } catch (Exception ignored) {}
        // WebSocket 实时推送
        try {
            wsPublisher.publishShadowUpdate(tenantId, deviceId, deviceKey,
                    identifier, parsed,
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> loadAllShadows(Long deviceId) {
        Map<String, Object> map = new HashMap<>();
        for (IotDeviceProperty p : propertyMapper.selectList(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId))) {
            map.put(p.getIdentifier(), parseValue(p.getValueJson()));
        }
        return map;
    }

    private Object parseValue(String v) {
        if (v == null || v.isEmpty() || v.equals("null")) return null;
        try { return objectMapper.readTree(v); } catch (Exception e) { return v; }
    }

    private void markOffline(IotDevice d) {
        if (d == null) return;
        d.setStatus(0);
        d.setLastOfflineTime(LocalDateTime.now());
        deviceMapper.updateById(d);
        // 推 WS 让前端实时刷新
        try {
            wsPublisher.publishDeviceStatus(d.getTenantId(), d.getId(),
                    d.getDeviceKey(), 0);
        } catch (Exception ignored) {}
    }

    // 周期清理已断开的 channel 引用
    @PostConstruct
    public void startCleanup() {
        Thread t = new Thread(() -> {
            while (running) {
                try { Thread.sleep(30000); } catch (InterruptedException ignored) {}
                try {
                    Iterator<Map.Entry<Long, Channel>> it = deviceChannels.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Long, Channel> e = it.next();
                        if (e.getValue() == null || !e.getValue().isActive()) {
                            it.remove();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, "tcp-channel-cleanup");
        t.setDaemon(true);
        t.start();
    }
}