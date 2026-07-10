// Root backend build (SCAFFOLDING SKELETON).
// Declares the shared toolchain and quality gates for all service modules.
// Dependency and plugin versions are indicative; no application code exists yet.

// Root backend build. Shared toolchain + quality gates for all service modules.
// NOTE: Toolchain is Java 17 to match the locally installed JDK (plan targets 21; bump in CI
// once a 21 JDK is available — Spring Boot 3.3 supports 17+).
plugins {
    java
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    checkstyle
}

allprojects {
    group = "com.kita"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    checkstyle {
        toolVersion = "10.17.0"
        configFile = rootProject.file("config/checkstyle.xml")
    }

    // Minimal, no-network formatting rules (google-java-format can be enabled in CI).
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
