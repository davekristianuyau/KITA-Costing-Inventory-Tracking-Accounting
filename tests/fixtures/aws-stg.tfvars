# Throwaway AWS Release Set for contract/smoke tests.
cloud_provider           = "aws"
client_name              = "testco"
env                      = "stg"
region                   = "us-east-1"
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
