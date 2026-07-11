# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KITA — Costing, Inventory Tracking, and Accounting. An Odoo/SAP-HANA-class ERP built with the
**Spec Kit** workflow (`/speckit.*`: constitution → specify → clarify → plan → tasks → implement).
Solo developer; clients are mostly local. Each client picks a cloud (AWS/GCP/Azure) and gets an
isolated deployment.

## Architecture

- **Frontend**: React (SPA; also targets a future React Native Android app), served by Nginx.
- **API gateway**: Spring Cloud Gateway — the single public entry point.
- **Backend**: Spring Boot 3.5 / Java 17 microservices (Gradle). Only the gateway + frontend are
  public; backend services are private.
- **Data**: managed PostgreSQL per cloud (RDS / Cloud SQL / Azure DB for PostgreSQL Flexible).
- **Infra**: one Terraform codebase deploys to AWS/GCP/Azure; the cloud is chosen by a `--cloud`
  flag (platform overlay), not a code change. Services deploy as a version-consistent **Release Set**.

## Current State (specs)

Specs live in `specs/`; each has spec/plan/tasks/contracts. Status:

- **002-source-scaffold** — ✅ Implemented. Folder structure + config skeletons only (no app code).
- **003-sales-inventory-bom** — ✅ Implemented + tested vs real PostgreSQL. The `operations-service`
  (`backend/operations-service/`) is a combined Odoo/SAP-style service: catalog, inventory (movement
  ledger, pessimistic-lock reservations, multi-location), BOM (explosion, cycle detection), production
  builds, sales, procurement, party (customer/supplier), and costing (AVCO default + FIFO/FEFO for
  perishables, cost roll-up/margin). All 70 tasks done.
- **001-multi-cloud-cicd** — 🚧 In progress (~46/62 tasks). DONE: all three cloud modules
  (`infra/terraform/modules/{aws,gcp,azure}`, `validate`-clean), config contract, AWS multi-service
  MVP, US2 gated STG/PROD promotion + health-gated deploy w/ auto-rollback, CI workflows, platform
  overlays, contract test suite. PENDING: US4 client isolation (onboard-client.sh, isolation/naming
  tests), US5 lifecycle (teardown.sh + workflow), and live-only integration tests + quickstart run.
  **Nothing has been `terraform apply`-ed** — that needs cloud credentials not present in dev.

## Key commands

```bash
# Backend (from backend/): JDK 17, Gradle wrapper 8.10.2
./gradlew :operations-service:build          # build + test + lint (Spotless/Checkstyle)
# Windows/Docker-Desktop Testcontainers workaround is baked into build.gradle.kts (needs TCP daemon)

# Infra (from repo root): Terraform >= 1.9
scripts/validate-config.sh --client acme --env stg --cloud aws
scripts/deploy.sh  --client acme --env stg  --cloud aws   # switch cloud = change --cloud
scripts/promote.sh --client acme --cloud aws              # gated STG→PROD
bash tests/run.sh all                                     # infra contract tests (live ones skip)
```

Infra config is split: `infra/terraform/clouds/{aws,gcp,azure}.tfvars` (platform: cloud+region, pick
with `--cloud`, never edit to switch) + `environments/<client>/{stg,prod}.tfvars` (cloud-agnostic:
client + Release Set). Deep dive: `infra/terraform/README.md`.

## Development Workflow

- Spec-driven: use `/speckit.*`. Commit + push specs when created/updated.
- **Commits**: simple messages, no AI/Co-Authored-By attribution (PR bodies may include it).
- **Comments**: short and minimal.
- Each feature on its own branch `NNN-short-name`; PR → merge to `main`. Don't sync `main` into
  branches whose spec is already implemented/done — only active ones.
- **Secrets** never in the repo, tfvars, or logs — DB credentials live in each cloud's secret store,
  scoped per environment (`{client}-{env}`).
- One folder/module per microservice under `backend/`; repositories are top-level interfaces.

## Session Management

**Resume Codes**: When ending a Claude Code session, add the resume code provided by Claude along with a timestamp to this file for reference.

### Session History

Format for entries:
```
[YYYY-MM-DD HH:MM] - Resume code: [code]
```

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
[2026-07-08 16:35] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 16:45] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:36] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:39] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:45] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:55] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:56] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 17:57] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 18:19] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d

## Active Technologies
- Terraform (HCL) >= 1.9; Bash scripts (orchestration); YAML (GitHub Actions). + Terraform providers `hashicorp/aws` (~> 5.x), `hashicorp/google` (001-multi-cloud-cicd)
- Managed PostgreSQL per cloud (AWS RDS / GCP Cloud SQL / Azure Database for (001-multi-cloud-cicd)

## Recent Changes
- 001-multi-cloud-cicd: Added Terraform (HCL) >= 1.9; Bash scripts (orchestration); YAML (GitHub Actions). + Terraform providers `hashicorp/aws` (~> 5.x), `hashicorp/google`
[2026-07-08 19:02] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 19:07] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 20:01] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 20:07] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:22] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:24] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:25] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:28] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:30] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:33] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:38] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:48] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 22:59] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-08 23:37] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 00:02] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 00:15] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 00:17] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:16] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:20] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:23] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:37] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:42] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:54] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 01:59] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 16:40] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 19:13] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 20:59] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 22:01] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-09 22:52] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-10 07:53] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-10 09:25] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-10 10:12] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-10 10:18] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-10 10:33] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-11 09:52] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
[2026-07-11 12:03] - Resume code: 329478f0-31c6-4c0b-8a02-071d99e1686d
