# ============================================================
# Backend Dockerfile - 多阶段构建
# ============================================================

# ---- 阶段 1: 构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 缓存依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 复制源码并打包
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 阶段 2: 运行 ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN apt-get update && apt-get install -y --no-install-recommends tzdata curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 复制 jar
COPY --from=builder /build/target/iot-platform.jar app.jar

# 上传/日志目录
RUN mkdir -p /app/uploadPath /app/logs

EXPOSE 9000

# 健康检查
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=60s \
    CMD curl -fsS http://localhost:9000/api/actuator/health || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
