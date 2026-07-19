variable "client_name" {
  type        = string
  description = "The client whose AWS deployment is being imitated (e.g. client-a)."
}

variable "env" {
  type    = string
  default = "stg"
}

variable "region" {
  type    = string
  default = "us-east-1"
}

# All AWS calls are pinned to the Floci local emulator — this config NEVER targets real AWS (SC-005).
variable "floci_endpoint" {
  type    = string
  default = "http://floci:4566"
}

# The running feature-008 stack that stands in for the compute + DB.
# Defaults to the client's Postgres container; the DB-credentials secret points at it.
variable "db_standin_host" {
  type    = string
  default = ""
}
