# Contract: Deployment Configuration Schema (tfvars)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

Every environment is deployed from a variable file
`infra/terraform/environments/<client-name>/<tier>.tfvars` that MUST satisfy this schema.
`validate-config.sh` enforces it before any `terraform plan`/`apply`; a violation fails
fast (FR-006, FR-007) and provisions nothing.

## Required variables

| Variable | Type | Constraint | Source |
|----------|------|-----------|--------|
| `cloud_provider` | string | one of `aws`, `gcp`, `azure` | FR-002/FR-003 |
| `client_name` | string | regex `^[a-z][a-z0-9-]{1,20}[a-z0-9]$` | FR-020 |
| `env` | string | one of `stg`, `prod` | FR-008 |
| `app_image` | string | non-empty registry reference | R7 |
| `app_version` | string | immutable tag or digest; non-empty | R7 / FR-010 |

## Optional variables (with defaults)

| Variable | Type | Default | Constraint |
|----------|------|---------|-----------|
| `region` | string | system default region | must be a valid region for `cloud_provider` (FR-002a) |
| `custom_domain` | string | none | valid FQDN when set (FR-001b) |
| `size` | string | `small` for stg, `standard` for prod | one of `small`, `standard`, `large` (FR-008a) |
| `db_backup_retention_days` | number | `7` (prod), `1` (stg) | >= 1; prod must be >= 7 with PITR on (FR-014a) |
| `tags` | map(string) | `{}` | merged with mandatory `{client, env, managed_by=terraform}` tags (FR-021) |

## Validation rules (enforced by `validate-config.sh`)

1. All required variables present and non-empty; unknown variables rejected.
2. `cloud_provider` and `env` within their enums; otherwise reject listing valid values (FR-007).
3. `client_name` matches the regex; the file path `environments/<client_name>/<env>.tfvars`
   MUST agree with the `client_name` and `env` values (no mismatched names).
4. `app_version` MUST be immutable (a digest, or a tag the pipeline pins to a digest) — a
   floating tag like `latest` is rejected (guarantees STG/PROD promote the same artifact).
5. When `custom_domain` is set, it is a syntactically valid FQDN.
6. For `env = prod`: `db_backup_retention_days >= 7` and PITR enabled.
7. No secret value may appear in a tfvars file (secrets come from the secret store); a
   scan for secret-like keys fails the check (FR-013, SC-010).

## Example (`environments/acme/prod.tfvars`)

```hcl
cloud_provider           = "aws"
client_name              = "acme"
env                      = "prod"
region                   = "ap-southeast-1"
app_image                = "ghcr.io/kita/app"
app_version              = "sha256:9f2c...e1"   # immutable digest
custom_domain            = "erp.acme.example"
size                     = "standard"
db_backup_retention_days = 14
tags                     = { owner = "kita" }
```
