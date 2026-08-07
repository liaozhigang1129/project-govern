# ===== Stage 1: Build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# 本仓使用系统 mvn(无 mvnw wrapper),用 -s 加载阿里云镜像
COPY backend/pom.xml .
COPY backend/.mvn-settings.xml ./
RUN mvn -s .mvn-settings.xml -B -q dependency:go-offline
COPY backend/src src
RUN mvn -s .mvn-settings.xml -B -q package -DskipTests

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pmo && adduser -S pmo -G pmo
WORKDIR /app
COPY --from=build /workspace/target/pmo-pms-backend.jar app.jar
RUN mkdir -p /app/uploads && chown -R pmo:pmo /app
USER pmo
EXPOSE 8088
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8088/api/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]