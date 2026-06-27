package com.iot.platform.protocol.tcp;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.IotProperties;
import com.iot.platform.protocol.core.*;
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
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP 协议适配器(Netty 实现) - 精简版
 *
 * <p>只负责:监听 socket、解析 JSON 行、调 dispatcher.dispatch
 * 业务处理由 dispatcher → publisher → processor 负责</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.protocol.tcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcpProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private final ProtocolDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile boolean running = false;
    private MessageHandler handler;

    /** deviceId -> Channel(仅用于下行) */
    private final Map<Long, Channel> deviceChannels = new ConcurrentHashMap<>();

    @Override public String getName() { return "tcp"; }

    @PostConstruct
    public void init() {
        log.info("[TCP] 适配器初始化 port={}", properties.getProtocol().getTcp().getPort());
    }

    @Override
    public void start() {
        int port = properties.getProtocol().getTcp().getPort();
        if (port <= 0) { log.warn("[TCP] port 未配置,跳过启动"); return; }
        bossGroup = new NioEventLoopGroup(properties.getProtocol().getTcp().getBossThreads());
        workerGroup = new NioEventLoopGroup(properties.getProtocol().getTcp().getWorkerThreads());
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
                            p.addLast(new IdleStateHandler(60, 30, 0));
                            p.addLast(new LineBasedFrameDecoder(8192));
                            p.addLast(new StringDecoder(CharsetUtil.UTF_8));
                            p.addLast(new TcpServerHandler());
                        }
                    });
            bootstrap.bind(port).sync();
            running = true;
            log.info("[TCP] 适配器已启动,监听 port={}", port);
        } catch (Exception e) {
            log.error("[TCP] 启动失败: {}", e.getMessage());
            shutdown();
        }
    }

    @Override public void stop() { running = false; shutdown(); }
    @PreDestroy public void destroy() { shutdown(); }

    private void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("[TCP] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }
    @Override public void setMessageHandler(MessageHandler h) { this.handler = h; }

    @Override
    public boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage) {
        if (session == null || !running) return false;
        Long deviceId = (Long) session.getAttributes().get("deviceId");
        if (deviceId == null) { log.warn("[TCP] 下行失败: session 缺少 deviceId"); return false; }
        Channel ch = deviceChannels.get(deviceId);
        if (ch == null || !ch.isActive()) { log.warn("[TCP] 下行失败: 设备 [{}] 不在线", session.getDeviceKey()); return false; }
        try {
            String line = objectMapper.writeValueAsString(downMessage) + "\n";
            ch.writeAndFlush(line);
            return true;
        } catch (Exception e) { log.error("[TCP] 下行失败: {}", e.getMessage()); return false; }
    }

    // ============= Netty Handler =============

    private class TcpServerHandler extends SimpleChannelInboundHandler<String> {

        private String authedProductKey;
        private Long authedDeviceId;
        private String authedDeviceKey;
        private InetSocketAddress remote;

        @Override public void channelActive(ChannelHandlerContext ctx) {
            remote = (InetSocketAddress) ctx.channel().remoteAddress();
            log.info("[TCP] 客户端连接: {}:{}", remote.getAddress().getHostAddress(), remote.getPort());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String line) {
            if (StrUtil.isBlank(line)) return;
            try {
                JsonNode frame = objectMapper.readTree(line);
                String type = frame.path("type").asText("");
                switch (type) {
                    case "auth":       handleAuth(ctx, frame); break;
                    case "property":   handleProperty(ctx, frame); break;
                    case "event":      handleEvent(ctx, frame); break;
                    case "set_reply":  log.info("[TCP] set_reply: {}", frame); break;
                    case "ping":       writeJson(ctx, objectMapper.createObjectNode().put("type", "pong")); break;
                    default:           writeJson(ctx, err("未知消息类型: " + type));
                }
            } catch (Exception e) {
                log.warn("[TCP] 解析失败: {} | line={}", e.getMessage(), line);
                writeJson(ctx, err("JSON 解析失败: " + e.getMessage()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (authedDeviceId != null) {
                deviceChannels.remove(authedDeviceId);
                log.info("[TCP] 设备[{}]断开连接", authedDeviceKey);
            } else {
                log.info("[TCP] 客户端断开(未认证)");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("[TCP] 异常: {}", cause.getMessage());
            ctx.close();
        }

        private void handleAuth(ChannelHandlerContext ctx, JsonNode f) {
            String productKey = f.path("productKey").asText("");
            String deviceKey = f.path("deviceKey").asText("");
            // 不查库,只信任 secret 由业务层(API 进程)验证
            // IoT 进程无法独立鉴权 → 通知 API 进程通过 dispatcher 发 AUTH 事件
            // 简化:IoT 进程信任客户端(由 API 进程后续处理)
            // 实际场景:可以加一个临时 trust 模式,secret 通过 Redis 查

            // 这里先做最简版:IoT 直接信任(等价于内部网信任)
            // TODO: 通过 Redis 查 deviceSecret
            this.authedProductKey = productKey;
            this.authedDeviceKey = deviceKey;
            // deviceId 需要在 deviceKey 通过时填,这里临时用 0,业务层会用 deviceKey 反查
            this.authedDeviceId = 0L;

            deviceChannels.put(authedDeviceId, ctx.channel());

            // 通知 dispatcher 上线(真实 deviceId 在 API 进程侧根据 deviceKey 反查后修正)
            dispatcher.onSessionConnect(DeviceSession.builder()
                    .sessionId(ctx.channel().id().asLongText())
                    .deviceKey(deviceKey)
                    .productKey(productKey)
                    .protocol("tcp")
                    .remoteAddress(remote.getAddress().getHostAddress())
                    .authenticated(true)
                    .connectTime(java.time.Instant.now())
                    .lastActiveTime(java.time.Instant.now())
                    .attributes(new java.util.HashMap<String,Object>() {{
                        put("deviceId", authedDeviceId);
                        put("channel", ctx.channel());
                    }})
                    .build());
            writeJson(ctx, objectMapper.createObjectNode().put("type", "auth_ack").put("status", "ok"));
        }

        private void handleProperty(ChannelHandlerContext ctx, JsonNode f) {
            if (authedDeviceKey == null) {
                writeJson(ctx, err("未认证,请先发送 auth 帧"));
                return;
            }
            long ts = f.path("ts").asLong(System.currentTimeMillis());
            JsonNode data = f.path("data");
            if (!data.isObject()) { writeJson(ctx, err("data 字段必须是 JSON 对象")); return; }

            // 构 DeviceMessage 调 dispatcher
            DeviceMessage msg = DeviceMessage.builder()
                    .messageId(IdUtil.fastSimpleUUID())
                    .protocol("tcp")
                    .deviceKey(authedDeviceKey)
                    .productKey(authedProductKey)
                    .type(MessageType.PROPERTY_REPORT)
                    .timestamp(ts)
                    .receivedAt(System.currentTimeMillis())
                    .rawPayload(f.toString())
                    .payload(parsePropertyPayload(data))
                    .build();
            if (handler != null) handler.onMessage(msg);

            writeJson(ctx, objectMapper.createObjectNode()
                    .put("type", "ack").put("ts", ts));
        }

        private void handleEvent(ChannelHandlerContext ctx, JsonNode f) {
            if (authedDeviceKey == null) { writeJson(ctx, err("未认证")); return; }
            String identifier = f.path("identifier").asText("");
            DeviceMessage msg = DeviceMessage.builder()
                    .messageId(IdUtil.fastSimpleUUID())
                    .protocol("tcp")
                    .deviceKey(authedDeviceKey)
                    .productKey(authedProductKey)
                    .type(MessageType.EVENT_REPORT)
                    .payload(parseEventPayload(f))
                    .receivedAt(System.currentTimeMillis())
                    .build();
            if (handler != null) handler.onMessage(msg);
        }

        private java.util.Map<String, Object> parsePropertyPayload(JsonNode data) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            data.fields().forEachRemaining(e -> map.put(e.getKey(), unwrap(e.getValue())));
            return map;
        }

        private java.util.Map<String, Object> parseEventPayload(JsonNode f) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("identifier", f.path("identifier").asText());
            map.put("value", unwrap(f.path("value")));
            return map;
        }

        private Object unwrap(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isDouble() || node.isFloat()) return node.asDouble();
            if (node.isBoolean()) return node.asBoolean();
            if (node.isTextual()) return node.asText();
            return node.toString();
        }

        private void writeJson(ChannelHandlerContext ctx, com.fasterxml.jackson.databind.JsonNode node) {
            try { ctx.writeAndFlush(objectMapper.writeValueAsString(node) + "\n"); }
            catch (Exception e) { log.error("[TCP] writeJson 失败", e); }
        }

        private com.fasterxml.jackson.databind.JsonNode err(String msg) {
            return objectMapper.createObjectNode().put("type", "err").put("message", msg);
        }
    }
}
