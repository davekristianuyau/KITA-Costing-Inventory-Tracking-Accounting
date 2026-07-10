# Shared naming and tagging. Resource names derive from {client-name}-{env}[-service].

variable "client_name" { type = string }
variable "env" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}

locals {
  name_prefix = "${var.client_name}-${var.env}"
  base_tags = merge(var.tags, {
    client     = var.client_name
    env        = var.env
    managed_by = "terraform"
  })
}

output "name_prefix" { value = local.name_prefix }
output "tags" { value = local.base_tags }
