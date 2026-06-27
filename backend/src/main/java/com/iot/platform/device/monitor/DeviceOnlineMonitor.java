package com.iot.platform.device.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.iot.platform.config.IotProperties;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.mapper.IotDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 设备超时离线监控器
 *
 * <p>用于弥补 MQTT 协议没有 last-will 的场景:
 * 设备发送属性上报 → 后端 {@code markOnline} 写入 {@code status=1, lastOnlineTime=now};
 * 若此后 N 秒内未再收到任何消息(模拟器被关闭、设备掉电、网络断),定时任务会把
 * {@code status=1 && lastOnlineTime < now - N} 的设备改回 {@code status=0}。</p>
 *
 * <p>TCP 设备已经由 {@code TcpProtocolAdapter.channelInactive → markOffline} 即时处理,
 * 这个任务主要是给 MQTT 兜底,两者并存不会冲突(idempotent)。</p>
 *
 * <p>前端 5 秒轮询 {@code /data/realtime} 会自然拉到新状态,无需额外 WebSocket 推送。</p>
 *
 * <p>关闭方式: {@code iot.device.offline-monitor-enabled=false}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.device", name = "offline-monitor-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceOnlineMonitor {

    private final IotProperties properties;
    private final IotDeviceMapper deviceMapper;

    /** fixedRate 用毫秒(从配置读取,默认 30s) */
    @Scheduled(fixedRateString = "#{${iot.device.offline-monitor-interval-seconds:30} * 1000}",
               initialDelay = 30_000)
    public void scanOfflineDevices() {
        int timeoutSec = properties.getDevice().getOfflineTimeoutSeconds();
        if (timeoutSec <= 0) return;

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSec);

        // 先查数量,日志更友好(并避免空扫白跑)
        Long count = deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getStatus, 1)
                .and(w -> w.isNull(IotDevice::getLastOnlineTime)
                        .or().lt(IotDevice::getLastOnlineTime, threshold)));
        if (count == null || count == 0) return;

        // 一次性批量更新(MyBatis-Plus 自动忽略逻辑删除)
        int updated = deviceMapper.update(null, new LambdaUpdateWrapper<IotDevice>()
                .eq(IotDevice::getStatus, 1)
                .and(w -> w.isNull(IotDevice::getLastOnlineTime)
                        .or().lt(IotDevice::getLastOnlineTime, threshold))
                .set(IotDevice::getStatus, 0)
                .set(IotDevice::getLastOfflineTime, LocalDateTime.now()));

        log.info("[设备超时离线] 扫描完成 timeout={}s, 命中 {} 台, 已置为离线 {} 台", timeoutSec, count, updated);
    }
}