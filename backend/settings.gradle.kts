// Backend Gradle multi-module settings (SCAFFOLDING SKELETON).
// Each module becomes its own deployable service image.
rootProject.name = "kita-backend"

include(":gateway")
include(":reference-service")
