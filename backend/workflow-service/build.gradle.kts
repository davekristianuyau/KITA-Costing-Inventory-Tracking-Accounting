plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// 018 US2 — the consumer-contract tests bind against the receivers' REAL DTO records, which means the
// receiver projects must be on a test classpath. They are kept in their OWN source set on purpose:
// putting them on the main `test` classpath drags in each receiver's whole jar, including its
// db/migration/*.sql (Flyway then sees several "version 1" migrations and every @SpringBootTest fails)
// and its application.yml. `contractTest` boots no Spring context, so that pollution is harmless there.
sourceSets {
    create("contractTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val contractTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}
configurations["contractTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Receiver DTOs — contract source set only, so a field renamed on either side fails the build
    // (SC-003) without their migrations/config leaking into the Spring-context tests.
    contractTestImplementation("org.springframework.boot:spring-boot-starter-test")
    contractTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    contractTestImplementation(project(":operations-service"))
    contractTestImplementation(project(":procurement-service"))
    contractTestImplementation(project(":crm-service"))
    contractTestImplementation(project(":hr-service"))
}

val contractTest by tasks.registering(Test::class) {
    description = "Consumer-contract tests bound to the receivers' real DTOs (018 US2)."
    group = "verification"
    testClassesDirs = sourceSets["contractTest"].output.classesDirs
    classpath = sourceSets["contractTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

// Drift must gate the build, not just a manual run.
tasks.check { dependsOn(contractTest) }

tasks.test {
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // Windows + Docker Desktop workaround ONLY (see operations-service for the full explanation).
    // On Linux/CI the default unix socket is used and none of this applies.
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "tcp://127.0.0.1:2375")
        environment(
            "DOCKER_CONFIG", layout.projectDirectory.dir("config/docker-noctx").asFile.absolutePath)
        environment("DOCKER_API_VERSION", "1.43")
        systemProperty("api.version", "1.43")
    }
}
