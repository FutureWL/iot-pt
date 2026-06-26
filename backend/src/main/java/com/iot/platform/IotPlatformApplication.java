package com.iot.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 物联网平台启动类
 *
 * @author IoT Platform Team
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.iot.platform.**.mapper")
public class IotPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
        System.out.println("""

                 ____                    ____  _      ______
                |  _ \\                  |  _ \\| |    |  ____|
                | |_) | ___  _ __ ___   | |_) | |    | |__   ___  _ __
                |  _ < / _ \\| '_ ` _ \\  |  __/| |    |  __| / _ \\| '_ \\
                | |_) | (_) | | | | | | | |   | |____| |___| (_) | | | |
                |____/ \\___/|_| |_| |_| |_|   |______|______\\___/|_| |_|

                :: 物联网平台启动成功 ::
                """
        );
    }
}
