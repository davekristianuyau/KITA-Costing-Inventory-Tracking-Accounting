# Contract: Deployment Configuration Schema (tfvars)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

Each environment is deployed from `infra/terraform/environments/<client-name>/<tier>.tfvars`,
validated by `validate-config.sh` before any plan/apply (fail-fast, FR-006/007). Updated for the
multi-service **Release Set**.

## Required variables
| Variable | Type | Constraint |
|----------|------|-----------|
| `cloud_provider` | string | one of `aws`,`gcp`,`azure` (FR-002/003) |
| `client_name` | string | regex `^[a-z][a-z0-9-]{1,20}[a-z0-9]$` (FR-020) |
| `env` | string | one of `stg`,`prod` (FR-008) |
| `release_set` | map(object) | the coordinated services; see below (FR-022) |

### `release_set` — map of `service_name` → object
| Field | Type | Constraint |
|-------|------|-----------|
| `image` | string | non-empty registry reference |
| `version` | string | immutable tag/digest; floating tags (`latest`) rejected |
| `visibility` | string | `public` or `private`; at least one `public` (the gateway) required |
| `port` | number | documented service port |
| `health_path` | string | e.g. `/actuator/health` or `/health` |

## Optional variables (defaults)
| Variable | Default | Constraint |
|----------|---------|-----------|
| `region` | system default | valid for `cloud_provider` (FR-002a) |
| `custom_domain` | none | valid FQDN; applied at the public gateway (FR-001b) |
| `size` | `small` (stg) / `standard` (prod) | per-env sizing (FR-008a) |
| `db_backup_retention_days` | 7 (prod) / 1 (stg) | prod ≥ 7 with PITR (FR-014a) |
| `tags` | `{}` | merged with mandatory `{client,env,managed_by=terraform}` (FR-021) |

## Validation rules
1. Required vars present; unknown vars rejected.
2. `cloud_provider`/`env` within enums; else reject listing valid values (FR-007).
3. `client_name` matches regex; file path `<client>/<env>.tfvars` agrees with values.
4. Every `release_set` entry has an **immutable** `version` (digest or pinned tag) — no floating tags.
5. At least one service has `visibility = public` (the gateway); backend services `private` (FR-001c).
6. `custom_domain`, when set, is a valid FQDN.
7. `env = prod` ⇒ `db_backup_retention_days ≥ 7` and PITR on.
8. No secret value appears in a tfvars file (FR-013, SC-010).

## Example (`environments/acme/prod.tfvars`)
```hcl
cloud_provider = "aws"
client_name    = "acme"
env            = "prod"
region         = "ap-southeast-1"
custom_domain  = "erp.acme.example"
size           = "standard"
db_backup_retention_days = 14
release_set = {
  frontend           = { image = "ghcr.io/kita/frontend",           version = "sha256:…", visibility = "public",  port = 8080, health_path = "/" }
  gateway            = { image = "ghcr.io/kita/gateway",            version = "sha256:…", visibility = "public",  port = 8081, health_path = "/actuator/health" }
  operations-service = { image = "ghcr.io/kita/operations-service", version = "sha256:…", visibility = "private", port = 8083, health_path = "/actuator/health" }
}
tags = { owner = "kita" }
```
