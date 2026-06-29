# ============================================================
# Backend Dockerfile - 多阶段构建
# 只构建 iot-api 模块(主服务,iot-broker / iot-console 是辅助服务)
# ============================================================

# ---- 阶段 1: 构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 缓存依赖(整个父 pom 的依赖)
COPY pom.xml .
COPY iot-common/pom.xml iot-common/
COPY iot-api/pom.xml iot-api/
COPY iot-broker/pom.xml iot-broker/
COPY iot-console/pom.xml iot-console/
RUN mvn -B -q -N install \
    && mvn -B -q -pl iot-api -am dependency:go-offline

# 复制源码并打包 iot-api
COPY iot-common/src iot-common/src
COPY iot-api/src iot-api/src
RUN mvn -B -q -pl iot-api -am package -DskipTests

# ---- 阶段 2: 运行 ----
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# 时区 + curl(健康检查用)
ENV TZ=Asia/Shanghai
RUN apt-get update && apt-get install -y --no-install-recommends tzdata curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 复制 iot-api.jar
COPY --from=builder /build/iot-api/target/iot-api.jar app.jar

# 上传/日志目录
RUN mkdir -p /app/uploadPath /app/logs

EXPOSE 9000

# 健康检查(actuator)
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=60s \
    CMD curl -fsS http://localhost:9000/api/actuator/health || exit 1

# JVM 参数: G1GC + 容器感知 + 快速启动
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# 注意: 用 ENTRYPOINT + shell 让 SPRING_PROFILES_ACTIVE 仍能从环境变量传
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]