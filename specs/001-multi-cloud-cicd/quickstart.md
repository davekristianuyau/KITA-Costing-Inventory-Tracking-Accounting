# Quickstart: Multi-Cloud CI/CD (multi-service)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

Operator happy path once implemented; doubles as the manual acceptance walkthrough. Deploys the
KITA **service set** (frontend, gateway, backend services) as a coordinated Release Set.

## Prerequisites
- Terraform >= 1.9 + the target cloud CLI, authenticated.
- Remote state backend bootstrapped for the target cloud.
- All service images published to the registry with immutable tags/digests.
- Per-environment credentials available to CI (STG and PROD separate).

## 1. Onboard a client
Create `infra/terraform/environments/acme/{stg,prod}.tfvars` — **cloud-agnostic**: `client_name`,
`env`, `size`, and the `release_set` map (frontend/gateway/operations-service → image+version+visibility);
optionally `custom_domain`. The cloud + region live in the ready-made platform overlays
`infra/terraform/clouds/{aws,gcp,azure}.tfvars` — you never edit a tfvars to switch cloud. Validate:
```bash
scripts/validate-config.sh --client acme --env stg --cloud aws
```

## 2. Deploy the service set to STG
```bash
scripts/deploy.sh --client acme --env stg --cloud aws
```
Expected: private network + per-service compute + shared PostgreSQL + public gateway provisioned as
`acme-stg-*`; the gateway URL is reachable; **backend services are NOT public**; aggregate health
green; smoke test passes (gateway → operations-service round trip). Re-run ⇒ no changes (idempotent).

## 3. Switch cloud (portability)
Re-run with a different `--cloud` (e.g. `--cloud gcp`) → equivalent multi-service stack on the new
provider, no file/pipeline/app changes (FR-003, SC-002).

## 4. Promote the Release Set to PROD (gated)
```bash
scripts/promote.sh --client acme --cloud aws
```
Expected: refused unless the PROD Release Set matches STG's and STG is healthy; on approval, PROD
serves the **same** set; failed aggregate health auto-rolls back to the previous set.

## 5. Update to a new Release Set
Bump image versions in tfvars → deploy (STG) → promote (PROD). Health-gated rollout; a bad set never
takes traffic.

## 6. Tear down
```bash
scripts/teardown.sh --client acme --env stg
```
Expected: every `acme-stg-*` resource removed and confirmed; `acme-prod` and other clients untouched.

## Acceptance mapping
| Step | Validates |
|------|-----------|
| 2 | US1/US6, FR-001/001c/004/004a/005, SC-001/003/013/014 |
| 3 | US3, FR-002/003, SC-002 |
| 4 | US2, FR-008–011/022/006a, SC-005/006/007 |
| 5 | US5, FR-015/022 |
| 6 | US5, FR-015, SC-009 |
| 1–6 (naming) | FR-020/021, SC-012 |
