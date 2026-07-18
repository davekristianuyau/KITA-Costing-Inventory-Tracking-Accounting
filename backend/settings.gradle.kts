// Backend Gradle multi-module settings (SCAFFOLDING SKELETON).
// Each module becomes its own deployable service image.
rootProject.name = "kita-backend"

include(":gateway")
include(":identity-service")
include(":reference-service")
include(":operations-service")
include(":hr-service")
include(":crm-service")
include(":procurement-service")
include(":workflow-service")
