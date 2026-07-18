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
- **004-hr-payroll** — 🚧 **Implementing** on branch `004-hr-payroll` (spec+plan+tasks done; **45/60
  tasks**). `hr-service` (`backend/hr-service/`, port 8085, schema-in-public via Flyway V1–V6). DONE &
  committed: US1 employee records + effective-dated compensation; US2 payroll run (state machine
  DRAFT→COMPUTED→FINALIZED, idempotent finalize under pessimistic lock, register reconciliation);
  US3 deductions (generic TABLE/BRACKET/PERCENT/FIXED rule engine, **PH statutory seed** SSS/PhilHealth/
  Pag-IBIG/BIR in `V5`, loans drawn down at finalize); US6 time & attendance (worked-time + OT/holiday/
  night-diff premiums from raw DTRs feeding gross). **PENDING**: US4 leave (T046–T051), US5 payslips/
  register/remittance outputs (T052–T055), polish (T056–T060: JSON-log PII scrub, OpenAPI contract
  tests, README, adjustment-run path, CI gate).
- **005-customer-discounts** — 📝 Spec+plan+tasks done (branch `005-customer-discounts`). `crm-service`:
  customer records, multi-tier **cascading** discounts (‑25% then ‑5%), loyalty/repeat tiers,
  government-mandated discounts (generic engine + PH senior/PWD seed). **Not yet implemented.**
- **006-supplier-purchasing** — 📝 Spec+plan+tasks done (branch `006-supplier-purchasing`).
  `procurement-service`: supplier master, purchase-order lifecycle, receiving (posts goods receipt to
  operations-service), restock/reorder suggestions. **Not yet implemented.**

> Specs 004–006 split the "customers, suppliers, employees" request into three services. Each lives on
> its own feature branch (not merged to `main`); `main` won't contain their `specs/` or `backend/`
> modules until merged.

## Resume — where we left off (2026-07-14)

Implementing all three new services in verified, committed slices (MVP-first), one user story per turn.
**Currently on `004-hr-payroll`.** To continue on another device:

```bash
git fetch origin && git checkout 004-hr-payroll
sed -n '/## Phase 7/,/## Phase 9/p' specs/004-hr-payroll/tasks.md   # next: US4 leave (T046+)
cd backend && ./gradlew :hr-service:compileJava :hr-service:compileTestJava   # verify baseline
```

- **Order**: finish 004 (US4 → US5 → polish), then implement 005, then 006 — each MVP-first.
- **Per-slice rhythm** (follow `/speckit.implement` even without the command): write failing tests →
  implement → `:hr-service:compileJava`+`compileTestJava` → run pure unit tests → mark tasks `[X]` →
  commit + push to the feature branch.
- **Local test caveat**: Testcontainers integration tests need Docker Desktop's *Expose daemon on
  tcp://localhost:2375 without TLS* toggle (Windows). It's currently OFF here, so only pure unit tests
  run locally; the ITs run in **CI** (Linux). Enable the toggle to run the full suite locally.
- **Conventions in `hr-service`**: pure calculators (`PayrollCalculator`, `DeductionRuleEngine`/
  `DeductionCalculator`, `AttendanceCalculator`) hold the math and are unit-tested without a DB;
  `common/Money` does half-up-to-cents rounding; `common/security/CallerContext` reads gateway role
  headers with a dev stub; repositories are top-level interfaces; entities use `@UuidGenerator`.

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
- **Implementing a spec**: follow the `/speckit.implement` workflow/rules even when the command isn't
  typed — work in `tasks.md` order, respect dependencies and `[P]`, follow TDD where tests are defined,
  build/verify, and mark tasks `[X]` as they complete.
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
shell commands, and other important information, read the current plan:
`specs/009-client-login-deploy-sim/plan.md` (client login + per-client deployment simulation — first real
frontend login page (React/Vite) + NEW `identity-service` (Spring Boot: BCrypt auth, user/client directory,
JWT with a `client` claim) + NEW tenant-aware `edge-gateway` (Spring Cloud Gateway: `/auth`→identity,
`/api`→client backend by JWT claim, strips+sets trusted `X-Kita-*`). Local Docker `sim/` runs TWO isolated
feature-008 stacks as separate Compose projects on a shared network + edge/identity/frontend + LocalStack
imitating one client's AWS deploy (reusing 001 Terraform via tflocal; ECS/RDS are LocalStack Pro → compute is
the local stack stand-in). Clarified 2026-07-18: centralized auth+routing, 2 clients + LocalStack (AWS only),
real intended behavior delivered as a local sim. Isolation enforced at edge AND per-client gateway).
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
[2026-07-15 16:44] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-15 20:00] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-15 20:46] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-16 09:06] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-16 09:52] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-16 09:57] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-16 11:03] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-16 11:07] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-17 16:50] - Resume code: e9a88aa7-7391-444d-bcdd-989fea5418d7
[2026-07-17 17:52] - Resume code: 3e6aa670-1cc1-4331-a63f-2fb7971d57aa
[2026-07-17 18:00] - Resume code: 3e6aa670-1cc1-4331-a63f-2fb7971d57aa

<!-- BEGIN token-budget compact-backups -->

## Token Budget — backup guard

Files ending in `.full.md` inside `specs/` and `.specify/memory/`
(e.g. `spec.full.md`, `plan.full.md`) are pre-compaction backups created
by `/speckit.token-budget.compact`. **Do not read them.** They contain the
full uncompacted content; loading them cancels the token savings compaction
achieved. To revert an artifact to its original state, run
`/speckit.token-budget.restore` instead.

<!-- END token-budget compact-backups -->
[2026-07-17 18:32] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-17 18:37] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-17 20:28] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-17 21:40] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-17 23:22] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-18 01:06] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-18 11:25] - Resume code: 6d38e5b9-a4ed-413d-b213-7288fcc54c9a
[2026-07-18 12:38] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 13:09] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 13:21] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 13:30] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 14:30] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 21:17] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 21:29] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 21:49] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
[2026-07-18 22:02] - Resume code: c329ef5d-be31-42df-9df8-02e333b7d8ba
