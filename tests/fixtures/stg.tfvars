# Throwaway cloud-agnostic env config for contract/smoke tests. Pair with any platform overlay
# (clouds/aws.tfvars | gcp.tfvars | azure.tfvars) via `--cloud` — the same file deploys to any cloud.
client_name              = "testco"
env                      = "stg"
size                     = "small"
db_backup_retention_days = 1

release_set = {
  frontend = {
    image       = "ghcr.io/kita/frontend"
    version     = "0.1.0"
    visibility  = "public"
    port        = 8080
    health_path = "/"
  }
  gateway = {
    image       = "ghcr.io/kita/gateway"
    version     = "0.1.0"
    visibility  = "public"
    port        = 8081
    health_path = "/actuator/health"
  }
  operations-service = {
    image       = "ghcr.io/kita/operations-service"
    version     = "0.1.0"
    visibility  = "private"
    port        = 8083
    health_path = "/actuator/health"
  }
}
