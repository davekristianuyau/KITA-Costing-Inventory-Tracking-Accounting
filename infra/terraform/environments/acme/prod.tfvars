cloud_provider           = "aws"
client_name              = "acme"
env                      = "prod"
region                   = "us-east-1"
size                     = "standard"
db_backup_retention_days = 7

# Release Set: identical image versions to stg.tfvars — promotion applies the STG-validated
# set to PROD with no rebuild. Bump versions in STG first, validate, then mirror here.
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
