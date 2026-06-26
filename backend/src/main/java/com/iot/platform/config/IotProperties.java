package com.iot.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IOT 平台配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "iot")
public class IotProperties {

    private Jwt jwt = new Jwt();
    private Tenant tenant = new Tenant();
    private Protocol protocol = new Protocol();
    private Rule rule = new Rule();

    @Data
    public static class Jwt {
        private String secret;
        private long expire = 86400;
        private long refresh = 604800;
        private String header = "Authorization";
        private String prefix = "Bearer ";
    }

    @Data
    public static class Tenant {
        private boolean enable = true;
        private Long superTenantId = 1L;
    }

    @Data
    public static class Protocol {
        private Mqtt mqtt = new Mqtt();
        private Tcp tcp = new Tcp();

        @Data
        public static class Mqtt {
            private boolean enabled = true;
            private String brokerUrl;
            private String clientId = "iot-platform-server";
            private String username;
            private String password;
            private int subscribeQos = 1;
        }

        @Data
        public static class Tcp {
            private boolean enabled = true;
            private int port = 33410;   // 主机端口(容器内由 docker-compose 映射)
            private int bossThreads = 1;
            private int workerThreads = 4;
        }
    }

    @Data
    public static class Rule {
        private boolean enabled = true;
        private int threadPoolSize = 4;
        private int queueCapacity = 1000;
    }
}
