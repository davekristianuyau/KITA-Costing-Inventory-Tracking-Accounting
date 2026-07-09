# Operations-service image: multi-stage JVM build.
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY .. .
RUN gradle :operations-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/operations-service/build/libs/*.jar app.jar
EXPOSE 8083
HEALTHCHECK CMD wget -qO- http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
