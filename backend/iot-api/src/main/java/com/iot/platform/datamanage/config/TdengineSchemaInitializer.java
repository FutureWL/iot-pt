package com.iot.platform.datamanage.config;

import com.iot.platform.datamanage.service.TdengineWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * TDengine super table 初始化
 *
 * <p>应用启动时确保 5 个超级表(iot_prop_int/bigint/double/bool/string)存在,
 * 避免第一次查询或写入前表不存在导致 500 错误。</p>
 *
 * <p>历史背景: 之前只有 TdengineWriter 在首次写入时 auto-create,但查询服务
 * (TdengineQueryService / RealtimeTdengineService) 不写只读,空表时查询必
 * 报 "Table does not exist"。本组件在启动时统一建表,所有读路径都安全。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TdengineSchemaInitializer {
    private final TdengineWriter writer;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        String[] types = {"int", "bigint", "double", "bool", "string"};
        for (String t : types) {
            writer.ensureSuperTable("iot_prop_" + t);
        }
        log.info("[TDengine] 5 个超级表初始化完成 (iot_prop_int/bigint/double/bool/string)");
    }
}