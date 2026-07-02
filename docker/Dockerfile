# Fast Knowledge — Maven 统一构建（含 web 前端）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY apps/server apps/server
COPY web web

RUN mvn -pl apps/server -am package -DskipTests -Pbundle -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata curl && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
COPY --from=build /build/apps/server/target/fast-knowledge-server-*.jar app.jar
ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod,bundle
EXPOSE 8088
VOLUME ["/data/knowledge"]
ENTRYPOINT ["java", "-jar", "app.jar"]
