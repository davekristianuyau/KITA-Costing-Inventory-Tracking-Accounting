# Quickstart: Multi-Cloud CI/CD Infrastructure Scaffolding

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

How to stand up and operate a KITA deployment once this feature is implemented. This is the
operator's happy path; it doubles as the manual acceptance walkthrough.

## Prerequisites

- Terraform >= 1.9 and the CLI for your target cloud (`aws` / `gcloud` / `az`), authenticated.
- Credentials available to CI as environment-scoped secrets (STG and PROD kept separate).
- Remote state backend created for the target cloud (one-time bootstrap).
- An application image published to the registry with an immutable tag/digest.

## 1. Onboard a client

Create the client's config from the example, one file per tier:

```text
infra/terraform/environments/acme/stg.tfvars
infra/terraform/environments/acme/prod.tfvars
```

Set at minimum `cloud_provider`, `client_name`, `env`, `app_image`, `app_version`
(see `contracts/config-schema.md`). Optionally set `region`, `custom_domain`, `size`.

Validate before anything else:

```bash
scripts/validate-config.sh --client acme --env stg
```

## 2. Deploy to STG

```bash
scripts/deploy.sh --client acme --env stg
```

Expected: infrastructure provisioned as `acme-stg-*`, app reachable at the output `app_url`,
`/health` green, smoke test passes (DB read/write works). Re-running makes **no** changes
(idempotent).

## 3. Choose / switch cloud (portability check)

Change only `cloud_provider` (and provider-specific values like `region`) in the tfvars,
then re-run `deploy.sh`. The same app comes up on the new provider with no pipeline or app
changes — this is the FR-003 / SC-002 acceptance check.

## 4. Promote to PROD (gated)

```bash
scripts/promote.sh --client acme --version <same-version-validated-in-stg>
```

Expected: refused unless that version is healthy in `acme-stg` (the gate). When approved
(GitHub Environment approval), PROD comes up serving the **same** artifact. A failed health
check auto-rolls back to the previous PROD version.

## 5. Update a running deployment

Bump `app_version` in the tfvars and deploy (STG), then promote (PROD). Health-gated rollout
means a bad version never takes traffic.

## 6. Tear down

```bash
scripts/teardown.sh --client acme --env stg
```

Expected: every `acme-stg-*` resource removed and confirmed gone; `acme-prod` and other
clients untouched.

## Acceptance mapping

| Step | Validates |
|------|-----------|
| 2 | US1, FR-001/004/005, SC-001/003 |
| 3 | US3, FR-002/003, SC-002 |
| 4 | US2, FR-008–011/006a, SC-005/006/007 |
| 5 | US5, FR-015 |
| 6 | US5, FR-015, SC-009 |
| 1–6 (naming) | FR-020/021, SC-012 |
