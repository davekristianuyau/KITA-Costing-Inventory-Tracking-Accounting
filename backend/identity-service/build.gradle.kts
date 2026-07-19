// identity-service: central authentication + user/client directory + asymmetric JWT (JWE) issuance.
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.security:spring-security-crypto") // BCrypt only (no filter chain)
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")                    // JWS (RS256) + JWE
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.test {
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // Windows + Docker Desktop workaround ONLY (see operations-service for the full explanation).
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "tcp://127.0.0.1:2375")
        environment(
            "DOCKER_CONFIG", layout.projectDirectory.dir("config/docker-noctx").asFile.absolutePath)
        environment("DOCKER_API_VERSION", "1.43")
        systemProperty("api.version", "1.43")
    }
}
