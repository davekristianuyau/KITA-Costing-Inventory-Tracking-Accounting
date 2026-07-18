// edge-gateway: tenant-aware Spring Cloud Gateway — the single public edge. Verifies the session
// token (JWE decrypt + RS256 verify with the shared PUBLIC key) and routes /api by the client claim.
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40") // verify (RS256) + decrypt (JWE) — no minting
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
