package com.iot.platform.iotconsole;

import com.iot.platform.protocol.core.IotMessageEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * IoT 消息环形缓冲 - 供控制台 SSE 抓包用
 *
 * <p>保留最近 N 条 envelope(默认 1000),新消息覆盖最老的。
 * 同时维护一组订阅者,SSE 推送用。</p>
 */
@Slf4j
@Component
public class SpyBuffer {

    private final int capacity = 1000;
    private final Deque<IotMessageEnvelope> ring = new ArrayDeque<>(capacity);
    private final List<Consumer<IotMessageEnvelope>> subscribers = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public void add(IotMessageEnvelope env) {
        synchronized (lock) {
            if (ring.size() >= capacity) {
                ring.pollFirst();
            }
            ring.addLast(env);
        }
        // 推送给所有订阅者
        for (Consumer<IotMessageEnvelope> s : subscribers) {
            try { s.accept(env); } catch (Exception e) { /* ignore */ }
        }
    }

    public List<IotMessageEnvelope> snapshot(int n) {
        int limit = Math.min(n, ring.size());
        synchronized (lock) {
            IotMessageEnvelope[] arr = ring.toArray(new IotMessageEnvelope[0]);
            int start = Math.max(0, arr.length - limit);
            return List.of(java.util.Arrays.copyOfRange(arr, start, arr.length));
        }
    }

    public int size() {
        return ring.size();
    }

    public void subscribe(Consumer<IotMessageEnvelope> cb) {
        subscribers.add(cb);
    }

    public void unsubscribe(Consumer<IotMessageEnvelope> cb) {
        subscribers.remove(cb);
    }
}
