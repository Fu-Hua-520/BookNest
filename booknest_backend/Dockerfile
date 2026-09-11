# 第一阶段：构建 (Build Stage)
# 使用 Maven 镜像作为基础环境（多模块父工程，打包 booknest-server 子模块）
FROM maven:3.8.5-eclipse-temurin-17 AS build
WORKDIR /app

# 1. 复制父 pom 和子模块 pom 以利用 Docker 缓存
COPY pom.xml .
COPY booknest-common/pom.xml booknest-common/
COPY booknest-pojo/pom.xml booknest-pojo/
COPY booknest-server/pom.xml booknest-server/

# 阿里云镜像 settings，加速依赖下载（容器内读不到宿主机 ~/.m2/settings.xml）
COPY settings.xml /root/.m2/settings.xml

# 下载依赖；用 cache mount 缓存本地仓库，重复构建不再全量重下
RUN --mount=type=cache,target=/root/.m2/repository mvn dependency:go-offline -B

# 2. 复制源码并执行打包
COPY booknest-common/src booknest-common/src
COPY booknest-pojo/src booknest-pojo/src
COPY booknest-server/src booknest-server/src

RUN --mount=type=cache,target=/root/.m2/repository mvn clean package -DskipTests -B

# 第二阶段：运行阶段 (Runtime Stage)
# 使用轻量级 JRE 环境
FROM eclipse-temurin:17-jre
WORKDIR /app

# 设置时区为亚洲/上海
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' > /etc/timezone

# 从构建阶段复制 jar 包（booknest-server 模块产物）
COPY --from=build /app/booknest-server/target/booknest-server-*.jar app.jar

# 暴露 8080 端口
EXPOSE 8080

# 启动参数说明：
# -Dspring.profiles.active=prod: 激活生产环境配置
# -Xmx512m -Xms256m: 针对核心内存 2G 的机器进行优化，限制 JVM 内存，防止撑爆宿主机
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
