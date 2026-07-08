# Gateway image (SCAFFOLDING SKELETON): multi-stage JVM build.
# Enabled once the service has sources.

# --- build stage ---
FROM gradle:8.9-jdk21 AS build
WORKDIR /app
COPY . .
# RUN gradle :gateway:bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# COPY --from=build /app/gateway/build/libs/*.jar app.jar
EXPOSE 8081
# HEALTHCHECK CMD wget -qO- http://localhost:8081/actuator/health || exit 1
# ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["echo", "gateway image is a scaffolding placeholder — implement the service first"]
