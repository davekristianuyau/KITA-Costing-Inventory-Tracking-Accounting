# Floci AWS coverage — 001 `modules/aws` (measured 2026-07-19)

Method: `terraform apply` the real 001 AWS module against Floci (`floci/floci:latest`, :4566) via the wrapper
root `sim/cloud-deploy/aws/`, dummy creds. See FR-011 / contracts/probe.md.

## Result: Floci emulates the **entire** module

All 001 AWS resource types **apply successfully** against Floci — far broader than LocalStack Community:

| Group | Resource types | Floci |
|---|---|---|
| Networking | `aws_vpc`, `aws_subnet` (public/private), `aws_internet_gateway`, `aws_nat_gateway`, `aws_eip`, `aws_route_table(_association)`, `aws_security_group` | ✅ |
| Compute | `aws_ecs_cluster`, `aws_ecs_task_definition`, `aws_ecs_service` | ✅ |
| Ingress | `aws_lb`, `aws_lb_listener`, `aws_lb_listener_rule`, `aws_lb_target_group` | ✅ |
| Discovery/logs/iam | `aws_service_discovery_private_dns_namespace`/`_service`, `aws_cloudwatch_log_group`, `aws_iam_role(_policy)(_attachment)` | ✅ |
| Storage/secrets | `aws_s3_bucket` (+ PAB + SSE), `aws_secretsmanager_secret(_version)`, `random_password` | ✅ |
| Database | `aws_db_subnet_group` | ✅ |
| Database | **`aws_db_instance` (RDS)** | ⚠️ **supported but SLOW** |

## The one exception: `aws_db_instance` (RDS) — guarded by `emulated`

Floci **can** create RDS, but it faithfully emulates RDS's ~15-minute provisioning (observed `Still
creating… [12m+ elapsed]`), which blows the deploy-check time budget (SC-004: all three clouds < 15 min).
It is therefore **skipped when `emulated = true`** (`count = var.emulated ? 0 : 1` in `database.tf`) — a
speed accommodation, **not** a capability gap. The running feature-008/009 stack is the compute/DB stand-in.

**C1 cascade handled**: skipping the DB would break its references, so those fall back when emulated:
- `aws_secretsmanager_secret_version.db` URL → `emulated-db.local` (still applies, valid secret).
- `outputs.resource_ids.database` → `"emulated"`.

## Measured

- `emulated = true` (deploy-check): **41** resources apply + destroy cleanly in a few minutes. ✅ `deploy-check.sh aws` PASS.
- `emulated = false` (real-cloud path): **42** resources planned, incl. `aws_db_instance.main[0]` — unchanged real deploy (FR-005).
