# Fast Knowledge — 多阶段构建 Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN mvn -f backend/pom.xml package -DskipTests -q

FROM node:20-alpine AS frontend-build
WORKDIR /build
COPY frontend/package*.json frontend/
RUN cd frontend && npm ci
COPY frontend/ frontend/
RUN cd frontend && npm run build

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
COPY --from=backend-build /build/backend/target/fast-knowledge-backend-*.jar app.jar
COPY --from=frontend-build /build/frontend/dist /app/static
ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod,bundle
EXPOSE 8088
VOLUME ["/data/knowledge"]
ENTRYPOINT ["java", "-jar", "app.jar"]
