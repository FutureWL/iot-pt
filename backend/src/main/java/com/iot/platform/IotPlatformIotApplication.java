package com.iot.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IoT 网关进程启动类
 *
 * <p>只跑 MQTT/TCP 协议适配器,业务处理(写库/规则/WS)在 API 进程。</p>
 *
 * <p>启动方式:
 * <pre>
 *   IOT_ROLE=iot IOT_ROLE=iot IOT_PROTOCOL_LAYER_ENABLED=true \
 *   java -jar iot-platform-iot.jar
 * </pre>
 * </p>
 *
 * <p>通过 application.yml 配置:
 * <pre>
 *   iot:
 *     role: iot
 *     protocol:
 *       enabled: true
 * </pre>
 * </p>
 */
@Slf4j
@EnableAsync
@SpringBootApplication(
    scanBasePackages = "com.iot.platform",
    exclude = {
        // IoT 进程不需要数据库(数据在 API 进程写)
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
    }
)
public class IotPlatformIotApplication {

    public static void main(String[] args) {
        // 关掉 web 容器,纯后台进程
        System.setProperty("spring.main.banner-mode", "log");
        SpringApplication app = new SpringApplication(IotPlatformIotApplication.class);
        app.setLogStartupInfo(true);
        app.run(args);
        log.info("""

                 _   ___  ___    _    ___   _____
                | |_|_ _|/ __|  /_\\  |_ _| |_   _|
                |  _|| |\\\\ \\\\ / _ \\\\  | |    | |
                | |  | | ___) | /_| | | |    | |
                |_| |___|____/ /_/   \\_| |    |_|    IoT Gateway

                """);
    }
}
