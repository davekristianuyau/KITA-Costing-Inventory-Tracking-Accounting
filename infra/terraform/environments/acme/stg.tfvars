# Cloud-agnostic env config. The cloud/region come from a platform overlay (clouds/<cloud>.tfvars),
# selected at deploy time with `--cloud`. You never edit this file to switch clouds.
client_name              = "acme"
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
