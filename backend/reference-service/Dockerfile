# Reference-service image (SCAFFOLDING SKELETON): multi-stage JVM build.
# Enabled once the service has sources.

# --- build stage ---
FROM gradle:8.9-jdk21 AS build
WORKDIR /app
COPY . .
# RUN gradle :reference-service:bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# COPY --from=build /app/reference-service/build/libs/*.jar app.jar
EXPOSE 8082
# HEALTHCHECK CMD wget -qO- http://localhost:8082/actuator/health || exit 1
# ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["echo", "reference-service image is a scaffolding placeholder — implement the service first"]
