// session-verify: shared library that decrypts + asymmetrically-verifies the session token. Used by BOTH
// the edge-gateway and each per-client gateway so the security-critical crypto lives in ONE place.
plugins {
    `java-library`
}

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.40") // JWS (RS256) verify + JWE decrypt

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
