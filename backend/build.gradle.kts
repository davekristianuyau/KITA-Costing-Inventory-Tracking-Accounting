// Root backend build (SCAFFOLDING SKELETON).
// Declares the shared toolchain and quality gates for all service modules.
// Dependency and plugin versions are indicative; no application code exists yet.

plugins {
    java
    // Applied per-module when sources exist:
    // id("org.springframework.boot") version "3.3.2" apply false
    // id("io.spring.dependency-management") version "1.1.6" apply false
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
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    checkstyle {
        toolVersion = "10.17.0"
        configFile = rootProject.file("config/checkstyle.xml")
    }

    // Spotless (google-java-format) configuration is applied here once modules add sources.
}
