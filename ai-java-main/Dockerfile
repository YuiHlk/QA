# 后端 Spring Boot 服务 Dockerfile
# 多阶段构建：Maven 编译 + JRE 运行
# 构建上下文：项目根目录

# ==================== 编译阶段 ====================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制 POM 文件（利用 Docker 层缓存：POM 不变则依赖层复用）
COPY pom.xml /build/pom.xml
COPY ai-java-main/pom.xml /build/ai-java-main/pom.xml

# 下载依赖（关闭 SSL 证书校验，适配内/外网环境）
RUN mvn dependency:go-offline -pl ai-java-main -B -q \
      -Dmaven.wagon.http.ssl.insecure=true \
      -Dmaven.wagon.http.ssl.allowall=true \
      -Dhttps.protocols=TLSv1.2

# 复制源码并构建
COPY ai-java-main/src /build/ai-java-main/src
RUN mvn package -pl ai-java-main -DskipTests -B -q -T 1C

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre
WORKDIR /app

# 复制构建产物
COPY --from=builder /build/ai-java-main/target/*.jar /app/app.jar

# 创建非 root 用户运行应用
# 提前创建 uploads 目录并设权限，确保命名卷首次挂载时继承正确的 ownership
RUN groupadd -r spring && useradd -r -g spring spring && \
    mkdir -p /app/uploads && \
    chown -R spring:spring /app
USER spring

# 健康检查：bash 内建 TCP 探测，无需安装额外工具
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
    CMD bash -c 'echo > /dev/tcp/localhost/8080' || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
