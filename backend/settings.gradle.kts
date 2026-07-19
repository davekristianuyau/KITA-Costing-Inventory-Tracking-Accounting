// Backend Gradle multi-module settings (SCAFFOLDING SKELETON).
// Each module becomes its own deployable service image.
rootProject.name = "kita-backend"

include(":session-verify")
include(":gateway")
include(":edge-gateway")
include(":identity-service")
include(":reference-service")
include(":operations-service")
include(":hr-service")
include(":crm-service")
include(":procurement-service")
include(":workflow-service")
