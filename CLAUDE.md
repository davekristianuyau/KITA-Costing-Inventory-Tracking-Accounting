# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KITA — Costing, Inventory Tracking, and Accounting. An Odoo/SAP-HANA-class ERP built with the
**Spec Kit** workflow (`/speckit.*`: constitution → specify → clarify → plan → tasks → analyze →
implement). Solo developer; clients are mostly local. Each client gets an isolated deployment.

## Architecture

- **Frontend**: React + Vite + Tailwind SPA (`frontend/`) — the **service console**: login → per-service
  workspaces, driven by declarative manifests. Served by Nginx. Also targets a future React Native app.
- **Public entry point**: `edge-gateway` (8080) — verifies the session cookie, **strips every inbound
  `X-Kita-*`**, resolves roles from HR per request, and routes to that client's backend. The plain
  `gateway` (8081) is the unauthenticated dev/in-stack router.
- **Backend**: Spring Boot 3.5 / Java 17 microservices (Gradle multi-module under `backend/`). Only the
  edge + frontend are public.
- **Data**: PostgreSQL, **one schema per service** (Flyway per module; the Hikari `search_path` is the
  linchpin). Redis for cache/sessions.
- **Infra**: one Terraform codebase; the cloud is chosen by a `--cloud` flag (platform overlay), not a
  code change. Services deploy as a version-consistent **Release Set**. Local multi-cloud emulation via
  **Floci** (`sim/`).

### Services

| Module | Port | Owns |
|---|---|---|
| `edge-gateway` | 8080 | session verification, header stripping, per-request role resolution, routing |
| `gateway` | 8081 | in-stack router (no auth) |
| `identity-service` | 8090 | accounts, login, session tokens |
| `reference-service` | 8082 | empty skeleton — not implemented |
| `operations-service` | 8083 | catalog, inventory, BOM, production, sales, costing |
| `hr-service` | 8085 | employees, payroll, deductions, attendance, leave, **account↔employee links + roles** |
| `crm-service` | 8086 | customers, cascading discounts, loyalty tiers, mandated discounts |
| `procurement-service` | 8087 | suppliers, purchase orders, receiving, reorder suggestions |
| `workflow-service` | 8088 | back-office orchestration, maker–checker, activity audit |

## Current State (specs)

Specs live in `specs/`; each has spec/plan/tasks/contracts. **Everything except 001 is implemented and
merged to `main`.**

| Spec | Status |
|---|---|
| 001-multi-cloud-cicd | 🚧 **46/62 — the only unfinished spec.** See below. |
| 002-source-scaffold | ✅ folder structure + config skeletons |
| 003-sales-inventory-bom | ✅ `operations-service` (70 tasks) |
| 004-hr-payroll | ✅ `hr-service` — PR #6 + gap-fix #7 |
| 005-customer-discounts | ✅ `crm-service` — PR #8 |
| 006-supplier-purchasing | ✅ `procurement-service` — PR #9/#10 |
| 007-back-office-workflows | ✅ `workflow-service` (T060 = a manual quickstart walk, still unrun) |
| 008-docker-cache-database | ✅ containerized DB/cache; schema-per-service |
| 009-client-login-deploy-sim | ✅ PR #16 — login + per-client deployment sim |
| 010-floci-multicloud-ci | ✅ PR #17 — infra gate green; AWS deploys the real 001 module to Floci. **GCP/Azure deliberately dropped.** |
| 011-service-console-ui | ✅ console foundation: login/shell/theme/workspace framework |
| 012-operations-ui | ✅ PR #20 — first per-service UI + the shared reference/list framework |
| 013-hr-ui | ✅ PR #21 |
| 014-crm-ui | ✅ PR #22 — frontend-only |
| 015-procurement-ui | ✅ PR #23 — frontend-only |
| 016-workflow-ui | ✅ PR #24 (T037 = a sim UI walk, unblocked by 018/017 but still unchecked) |
| 017-account-employee-identity | ✅ PR #26 — a signed-in account resolves to **its own employee**; HR is the source of status **and** roles |
| 018-secure-service-contracts | ✅ PR #25 — real receiver contracts, mTLS, refusal audit, `contractTest` drift guard |

### 001-multi-cloud-cicd — what's actually left

DONE: all three cloud modules (`infra/terraform/modules/{aws,gcp,azure}`, `validate`-clean), config
contract, AWS multi-service MVP, US2 gated STG/PROD promotion + health-gated deploy w/ auto-rollback,
CI workflows, platform overlays, contract test suite.

PENDING (16 tasks) — **reconcile against spec 010 before executing**, since 010 dropped GCP/Azure and
already deploys AWS to Floci, so some of these are obsolete or now runnable locally:

- T015–T017 live integration tests (deploy health, backend-private, idempotent apply)
- T047 GCP/Azure remote-state backends — **likely obsolete** (010 dropped both clouds)
- T048 single-region/in-region-data policy check
- T049–T052 US4 client isolation: isolation + naming tests, per-client tfvars convention, `onboard-client.sh`
- T053–T057 US5 lifecycle: release-set update, teardown test, `teardown.sh`, `teardown.yml`, update path
- T058 secret-leak scan in CI · T061 quickstart end-to-end on real AWS

**Nothing has been `terraform apply`-ed against a real cloud** — that needs credentials not present in dev.

## Resume — where we left off (2026-08-01)

All backend services, all five per-service UIs, secure service contracts, and account-to-employee
identity are **merged to `main`**. There is **no active feature branch**.

Candidate next work, in the order it makes sense:

1. **Finish 001** — start with the 010 reconciliation pass above, not with the task list.
2. **Real-AWS pipeline spec** (CI→ECR→RDS→ECS-on-EC2-Graviton→ALB on floci-aws; *not* Fargate) — the
   long-planned follow-on now that every service UI exists.
3. Two stray manual verifications: 007 T060 and 016 T037 (both were deferred for reasons that no longer
   hold — Docker works now, and 018 fixed the adapter drift that blocked T037).

### Known defects, tracked but deliberately unspecced

- `POST /api/operations/boms` → 500 `NullPointerException` (`"fromCode" is null`) when a component omits
  `uom`, though the DTO marks it optional. Spec 003's domain.
- `console-smoke` is red on `main` (the edge needs BOTH client networks). **It is the only known-red CI
  job** — `backend` was fixed by 017, so a red `backend` is now a *real regression*.

## Key commands

```bash
# Backend (from backend/): JDK 17, Gradle wrapper 8.10.2
./gradlew build                              # everything: compile + test + Spotless/Checkstyle
./gradlew :operations-service:build          # one service
./gradlew spotlessApply                      # run BEFORE build or the lint gate fails

# Frontend (from frontend/): Node at C:\Program Files\nodejs
npm test          # vitest — must run FROM frontend/ or the jsdom config is missed
npm run build

# Local stacks
docker compose up -d                                  # plain backend stack — UNAUTHENTICATED, see header
KITA_DEV_NO_AUTH=true docker compose up               # ...with the permissive role fallback (explicit opt-in)
bash sim/sim-up.sh                                    # two isolated client stacks + edge + console (authenticated)
bash sim/sim-smoke.sh ; bash sim/sim-down.sh

# Infra (from repo root): Terraform >= 1.9 (terraform.exe at C:\Terraform)
scripts/validate-config.sh --client acme --env stg --cloud aws
scripts/deploy.sh  --client acme --env stg  --cloud aws   # switch cloud = change --cloud
scripts/promote.sh --client acme --cloud aws              # gated STG→PROD
bash tests/run.sh all                                     # infra contract tests (live ones skip)
```

Infra config is split: `infra/terraform/clouds/{aws,gcp,azure}.tfvars` (platform: cloud+region, pick
with `--cloud`, never edit to switch) + `environments/<client>/{stg,prod}.tfvars` (cloud-agnostic:
client + Release Set). Deep dive: `infra/terraform/README.md`.

**CI** (`.github/workflows/ci.yml`) jobs: `backend`, `frontend`, `stack-smoke`, `sim-smoke`,
`cloud-deploy`, `console-smoke`, `infra`.

## Conventions

- **Per-service layout**: pure calculators hold the math and are unit-tested without a DB (e.g.
  `PayrollCalculator`, `DeductionRuleEngine`, `AttendanceCalculator`); `common/Money` does
  half-up-to-cents rounding; repositories are **top-level interfaces**; entities use `@UuidGenerator`.
- **Authorization**: services read the trusted `X-Kita-Roles` header via `CallerContext`. `OWNER` means
  "every role this service has". `workflow-service` is the exception — its `CallerContext` does *not*
  read roles; the decision lives in `ActionAuthorizer` against `authorization_mapping`.
- **The permissive `stub` fallback is retired** in every deployed path (017). It survives only in each
  service's `src/test/resources/application.yml`, for isolated test runs.
- **Service-to-service calls bypass the edge**, so `RemoteCall` must forward both `X-Kita-User` *and*
  `X-Kita-Roles`.
- **Maker–checker is enforced in more than one place** — `BackOfficePipeline` *and* each workflow
  (`SalesOrderWorkflow`, `ReceivingWorkflow`). When changing a rule there, grep for every enforcement
  point; an exemption applied to only one of them is worse than none.
- **Local test caveat**: Testcontainers ITs need Docker Desktop's *Expose daemon on tcp://localhost:2375
  without TLS* toggle (Windows). With it off, only pure unit tests run locally; ITs run in CI (Linux).
- Docker image builds on this machine fail spuriously **in parallel** — build sequentially.

## Development Workflow

- Spec-driven: use `/speckit.*` (the hyphenated `speckit-*` skill variants). Commit + push specs when
  created/updated.
- **Always run `/speckit-analyze` before implementing** — it has found a real gap **5/5 times** on this
  project, including one CRITICAL that coverage analysis structurally could not catch (the task existed
  and simply named the wrong class).
- **Implementing a spec**: follow the `/speckit.implement` workflow/rules even when the command isn't
  typed — work in `tasks.md` order, respect dependencies and `[P]`, follow TDD where tests are defined,
  build/verify, and mark tasks `[X]` as they complete.
- **Verify on a running stack before declaring done.** Both 017 and 018 shipped bugs that every unit
  test passed over and only a live request exposed.
- **Commits**: simple messages, no AI/Co-Authored-By attribution (PR bodies may include it).
- **Comments**: short and minimal.
- Each feature on its own branch `NNN-short-name`; PR → merge to `main`. Don't sync `main` into branches
  whose spec is already implemented/done — only active ones.
- **Secrets** never in the repo, tfvars, or logs — DB credentials live in each cloud's secret store,
  scoped per environment (`{client}-{env}`).
- One folder/module per microservice under `backend/`.

## Session Management

**Resume Codes**: When ending a Claude Code session, add the resume code provided by Claude along with a
timestamp to this file for reference.

### Session History

Format for new entries:

```
[YYYY-MM-DD HH:MM] - Resume code: [code]
```

Entries before 2026-08-01 were collapsed to one line per distinct code (165 near-duplicate lines):

| Resume code | Period | Sessions |
|---|---|---|
| `329478f0-31c6-4c0b-8a02-071d99e1686d` | 2026-07-08 → 07-11 | 45 |
| `e9a88aa7-7391-444d-bcdd-989fea5418d7` | 2026-07-15 → 07-17 | 9 |
| `3e6aa670-1cc1-4331-a63f-2fb7971d57aa` | 2026-07-17 | 2 |
| `6d38e5b9-a4ed-413d-b213-7288fcc54c9a` | 2026-07-17 → 07-18 | 7 |
| `c329ef5d-be31-42df-9df8-02e333b7d8ba` | 2026-07-18 | 10 |
| `6591e3b3-6df0-4d40-ac04-424bc6833524` | 2026-07-18 → 07-19 | 31 |
| `d6bcabc1-b370-4ef1-8fb2-850c875dc02a` | 2026-07-20 | 11 |
| `c546350b-ead7-4f6a-a7e1-5660e7c55787` | 2026-07-22 → 07-23 | 12 |
| `f390186a-3d0c-4e43-a41f-ce1e359363e1` | 2026-07-23 → 08-01 | 18 |

[2026-08-01 12:16] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1

<!-- SPECKIT START -->
**Active feature: `019-local-production-deploy`** — read `specs/019-local-production-deploy/plan.md`.

One command (`/deploy-local`) deploys the whole system to the local **Floci** AWS emulator using the
**real** `infra/terraform/modules/aws`, leaves it running, and proves it works with real traffic — browsable
through the ALB exactly as production will be. Bash + Terraform ≥1.9 over the existing services.

**Floci capabilities, established empirically 2026-08-01 — do not re-derive** (`research.md` has the
evidence; `[[floci-emulators-reference]]` mirrors it):
- **Floci runs a DNS server on UDP 53, documented nowhere.** Containers on Docker's default resolver
  (127.0.0.11) get NXDOMAIN for every Floci name. This one fact inverted a conclusion mid-session: the ALB
  was declared "API-only" by a test that never asked Floci's resolver.
- **ALB fully works** — resolves and proxies real HTTP; creating a listener binds that port *inside* the
  Floci container, so publishing it lets the host browser reach the app **through the load balancer**.
- **ECS runs EC2-launch-type services** as real containers. Floci needs no ASG/capacity provider; **real AWS
  does** — so an EC2 switch that passes locally can still leave production tasks `PENDING`. Highest-risk gap.
- **ECR** works with no `docker login`; Floci spawns its own `registry:2` sibling that binds 5100 (never
  publish 5100 from Floci), and a Floci restart **wipes ECR API state** while blobs survive → create repos
  idempotently.
- **RDS spawns a real PostgreSQL 16.3 immediately** (the old "~15 min" note is wrong) → deploy with
  `emulated=false`. Exposed a latent prod bug: `database.tf` hardcodes `:5432` but the endpoint is Floci
  proxying a nonstandard port → must use the instance's `port`.
- **Cloud Map is the ONE gap** (management API only; Route 53 likewise — records return `INSYNC` and still
  NXDOMAIN). Covered by a Docker network alias after placement, leaving deployed config byte-identical.
  `nginx.conf` proxies to the **bare** host `edge`, so alias short names too, not just the FQDN.

**Clarified (2026-08-01):** capacity provisioning is **in scope**, recorded as not verifiable locally;
authorization is **fully enforced** (no permissive fallback) and verification must prove an unpermitted
action is *refused*; all four local stacks **coexist** (each existing one backs a CI gate); on failure the
command resolves generated container names and prints a runnable log command.

**The thesis:** verification must exercise **real traffic**, never resource existence — `terraform state
list` would have passed both silent failures found while researching this. US1 deploy+browse → US2 honest
verification → US3 ECS-on-EC2 + capacity → US4 teardown/repeat.
See [[spec-017-account-employee-identity-progress]] + [[spec-010-floci-multicloud-ci]].
<!-- SPECKIT END -->

## Active Technologies

- **Backend**: Java 17, Spring Boot 3.5.0, Gradle 8.10.2, Flyway, PostgreSQL, Redis, Testcontainers
- **Frontend**: React, TypeScript, Vite, Tailwind, Vitest
- **Infra**: Terraform >= 1.9 (`hashicorp/aws` ~> 5.x), Bash orchestration, GitHub Actions, Floci
  (local multi-cloud emulation), Docker Compose

<!-- BEGIN token-budget compact-backups -->

## Token Budget — backup guard

Files ending in `.full.md` inside `specs/` and `.specify/memory/`
(e.g. `spec.full.md`, `plan.full.md`) are pre-compaction backups created
by `/speckit.token-budget.compact`. **Do not read them.** They contain the
full uncompacted content; loading them cancels the token savings compaction
achieved. To revert an artifact to its original state, run
`/speckit.token-budget.restore` instead.

<!-- END token-budget compact-backups -->
[2026-08-01 13:09] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 13:33] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 14:50] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 14:57] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:08] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:11] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:20] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:29] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:40] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 15:55] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:04] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:06] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:39] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:41] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:46] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:47] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 16:56] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
[2026-08-01 17:30] - Resume code: f390186a-3d0c-4e43-a41f-ce1e359363e1
