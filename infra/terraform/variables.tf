variable "cloud_provider" {
  type        = string
  description = "Target cloud: aws | gcp | azure."
  validation {
    condition     = contains(["aws", "gcp", "azure"], var.cloud_provider)
    error_message = "cloud_provider must be one of: aws, gcp, azure."
  }
}

variable "client_name" {
  type        = string
  description = "Stable, lowercase, provider-safe client identifier."
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}[a-z0-9]$", var.client_name))
    error_message = "client_name must match ^[a-z][a-z0-9-]{1,20}[a-z0-9]$."
  }
}

variable "env" {
  type        = string
  description = "Environment tier: stg | prod."
  validation {
    condition     = contains(["stg", "prod"], var.env)
    error_message = "env must be one of: stg, prod."
  }
}

variable "region" {
  type        = string
  description = "Cloud region; empty uses the provider default."
  default     = ""
}

variable "custom_domain" {
  type        = string
  description = "Optional FQDN served at the public gateway."
  default     = ""
}

variable "size" {
  type        = string
  description = "Sizing profile: small | standard | large."
  default     = "small"
  validation {
    condition     = contains(["small", "standard", "large"], var.size)
    error_message = "size must be one of: small, standard, large."
  }
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "gcp_project" {
  type    = string
  default = ""
}

# The coordinated Release Set: service_name -> image/version/visibility/port/health.
variable "release_set" {
  type = map(object({
    image       = string
    version     = string
    visibility  = string
    port        = number
    health_path = string
  }))
  validation {
    condition     = length([for s in var.release_set : s if s.visibility == "public"]) >= 1
    error_message = "At least one service must have visibility = public (the gateway/frontend)."
  }
  validation {
    condition     = alltrue([for s in var.release_set : contains(["public", "private"], s.visibility)])
    error_message = "Each service visibility must be public or private."
  }
}
