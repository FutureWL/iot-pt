package com.iot.platform.iotconsole;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * IoT 统计 - 消息计数/在线设备/连接数
 *
 * <p>用 LongAdder(高并发写);在线设备表由 ProtocolDispatcher 提供。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "iot.console", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IotMetricsService {

    private final LongAdder txTotal = new LongAdder();
    private final LongAdder rxTotal = new LongAdder();
    private final LongAdder errTotal = new LongAdder();
    private final AtomicLong lastTickMs = new AtomicLong(System.currentTimeMillis());
    private final LongAdder txSinceTick = new LongAdder();
    private final LongAdder rxSinceTick = new LongAdder();

    public void incTx() { txTotal.increment(); txSinceTick.increment(); }
    public void incRx() { rxTotal.increment(); rxSinceTick.increment(); }
    public void incErr() { errTotal.increment(); }

    /** 每秒被 IoTConsoleController 调用,计算 tps */
    public synchronized void tick() {
        long now = System.currentTimeMillis();
        long last = lastTickMs.getAndSet(now);
        // 真正计算 tps 在调用方:用 sinceTick 增量 / 实际秒数
    }

    public long getTxTotal() { return txTotal.sum(); }
    public long getRxTotal() { return rxTotal.sum(); }
    public long getErrTotal() { return errTotal.sum(); }
    public long getTxSinceTick() { return txSinceTick.sumThenReset(); }
    public long getRxSinceTick() { return rxSinceTick.sumThenReset(); }
}
