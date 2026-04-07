# ---- Build Stage ----
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
COPY skillhub-domain/pom.xml skillhub-domain/
COPY skillhub-auth/pom.xml skillhub-auth/
COPY skillhub-search/pom.xml skillhub-search/
COPY skillhub-infra/pom.xml skillhub-infra/
COPY skillhub-storage/pom.xml skillhub-storage/
COPY skillhub-notification/pom.xml skillhub-notification/
COPY skillhub-app/pom.xml skillhub-app/
COPY mvnw .
COPY .mvn .mvn

# 配置阿里云 Maven 镜像加速
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>\
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"\
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">\
  <mirrors>\
    <mirror>\
      <id>aliyun</id>\
      <mirrorOf>central</mirrorOf>\
      <name>Alibaba Cloud Maven Mirror</name>\
      <url>https://maven.aliyun.com/repository/central</url>\
    </mirror>\
    <mirror>\
      <id>aliyun-public</id>\
      <mirrorOf>*</mirrorOf>\
      <name>Alibaba Cloud Public Maven Mirror</name>\
      <url>https://maven.aliyun.com/repository/public</url>\
    </mirror>\
  </mirrors>\
</settings>' > /root/.m2/settings.xml

RUN ./mvnw dependency:go-offline -B

COPY . .
RUN ./mvnw package -DskipTests -B

# ---- Runtime Stage ----
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /app/skillhub-app/target/*.jar app.jar

# Create storage directory and set permissions
RUN mkdir -p /var/lib/skillhub/storage && \
    chown -R app:app /app /var/lib/skillhub/storage

USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
