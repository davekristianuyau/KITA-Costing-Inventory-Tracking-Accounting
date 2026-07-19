# Edge gateway image: multi-stage JVM build. Build context is ./backend (whole Gradle project).
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :edge-gateway:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /app/edge-gateway/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
