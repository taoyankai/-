# ============================================================================
#  华中商贸配送中心 · 服务端镜像（多阶段构建）
#  阶段一：Maven 编译（依赖走阿里云镜像）
#  阶段二：仅保留 JRE + jar，镜像更小、启动更快
# ============================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 先复制 pom 与 Maven 配置，利用 Docker 层缓存：源码变动时不必重新下载依赖
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -q dependency:resolve -DskipTests

# 再复制源码并编译
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 时区与健康检查依赖
RUN apk add --no-cache tzdata curl \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/cache/apk/*

COPY --from=build /build/target/hz-delivery-server.jar /app/app.jar

# 以非 root 运行，降低容器被提权的风险
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/logs && chown -R app:app /app
USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8" \
    TZ=Asia/Shanghai

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
