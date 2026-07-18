plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
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

    // Windows + Docker Desktop 29 workaround ONLY. On Linux/CI the default unix socket is used
    // and none of this applies. On Windows the docker-java client can't handshake over the named
    // pipe (it resolves the Docker Desktop context and defaults to API 1.32, which Engine 29
    // rejects). So, for local Windows runs, enable Docker Desktop Settings > General >
    // "Expose daemon on tcp://localhost:2375 without TLS" and we:
    //  1) point Testcontainers at the plaintext TCP daemon on 127.0.0.1 (not localhost → IPv6);
    //  2) DOCKER_CONFIG → an empty dir so no `currentContext` overrides DOCKER_HOST;
    //  3) pin the Docker API version (>= 1.40) via the `api.version` system property.
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "tcp://127.0.0.1:2375")
        environment(
            "DOCKER_CONFIG", layout.projectDirectory.dir("config/docker-noctx").asFile.absolutePath)
        environment("DOCKER_API_VERSION", "1.43")
        systemProperty("api.version", "1.43")
    }
}
