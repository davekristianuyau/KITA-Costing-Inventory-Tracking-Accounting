# Implementation Plan: Local Production-Replica Deployment

**Branch**: `019-local-production-deploy` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/019-local-production-deploy/spec.md`

## Summary

Deploy the entire KITA system to the local **Floci** AWS emulator using the **real** `modules/aws`
Terraform module, leave it running, and prove it works with real traffic — reachable in a browser through
the ALB, exactly as production will be.

The emulator's actual capabilities were established empirically on 2026-08-01 (see [research.md](./research.md)),
which changed the design twice. Two findings matter most:

1. **Floci runs a DNS server on UDP 53** that is documented nowhere. Containers using Docker's default
   resolver get NXDOMAIN for every Floci name. With Floci as resolver, **the ALB fully works** — the
   browser reaches the app through the load balancer, so the public entry path is production-identical.
2. **Cloud Map is the single gap** — management API only. It is covered by a Docker network alias applied
   after task placement, which leaves the deployed application configuration byte-identical to production.

## Technical Context

**Language/Version**: Bash for the deploy tooling (matching `sim/` and `scripts/`); Terraform ≥ 1.9 (already pinned); Java 17 / Spring Boot 3.5 and React/Vite for the deployed units, unchanged by this feature
**Primary Dependencies**: Floci AWS emulator `floci/floci:latest`; Docker Engine + Compose; `amazon/aws-cli` (containerised); the existing `infra/terraform/modules/aws`
**Storage**: PostgreSQL 16 provisioned through the emulator's **RDS** (a real engine, not a stand-in), schema-per-service via each service's own Flyway migrations
**Testing**: the deployment's own verification suite (real HTTP against the running system) plus fault-injection cases; existing `tests/run.sh` contract tests untouched
**Target Platform**: developer workstation — Windows + Docker Desktop is the primary case; scripts stay POSIX so Linux works
**Project Type**: infrastructure/deployment tooling over an existing multi-service web application
**Performance Goals**: warm re-run < 5 min (SC-008); cold run deliberately unbounded, dominated by image builds
**Constraints**: no real cloud credentials and no real cloud resources (FR-003); no local-only copy of the infrastructure definitions (FR-002); local-only substitutes confined to tooling and enumerated (FR-009/FR-022)
**Scale/Scope**: one client + environment per run; 9 deployable units (8 services + the frontend) plus a database

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| **I. Specification-Driven** | ✅ Pass | Spec plus 4 recorded clarifications precede this plan; 4 prioritized, independently testable stories. |
| **II. Test-Driven (NON-NEGOTIABLE)** | ✅ Pass | Verification checks are written **before** the deploy path that satisfies them, and each is observed failing first. Fault injection (break an internal name, use an unpermitted caller) proves the checks can actually fail — see [quickstart.md](./quickstart.md). |
| **III. Security & Data Integrity** | ✅ Pass | Authorization fully enforced, no permissive fallback (FR-024). Dummy emulator credentials only (FR-003). No secrets in the repo or in output (FR-023); emulator state and generated passwords stay gitignored. |
| **IV. Environment Isolation** | ✅ Pass | Every resource scoped by `{client}-{env}`; the deployment targets only the emulator endpoint and cannot reach a real account. Teardown removes only what it created (FR-017). |
| **V. Observability** | ✅ Pass | FR-014 requires resolving generated workload names and printing a runnable log command per failing unit — deliberately independent of any emulator log service. |
| **VI. Simplicity & YAGNI** | ⚠️ Justified | One new mechanism (the DNS alias). See Complexity Tracking — it is the *only* mechanism that works, which is a tested claim rather than an assumption. |
| **VII. Automated Quality Gates** | ✅ Pass | The command's own verification is the gate and fails fast (FR-013). No CI gate is added or migrated (FR-026); making this a pipeline job is deliberately deferred. |

**Post-Phase-1 re-check**: still passing. The design introduced no further deviation; the alias remains the only one.

## Project Structure

### Documentation (this feature)

```text
specs/019-local-production-deploy/
├── plan.md              # This file
├── research.md          # Phase 0 — the empirical Floci findings and the decisions they forced
├── data-model.md        # Phase 1 — release set, environment, verification result, known gaps
├── quickstart.md        # Phase 1 — how to run and validate, including fault injection
├── contracts/
│   ├── deploy-command.md    # CLI surface, exit codes, output contract
│   └── release-set.md       # the 9 deployable units and their declared shape
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
sim/local-deploy/                 # NEW — the deployment harness
├── deploy-local.sh               # the one command: preflight → build → push → apply → alias → verify
├── teardown.sh                   # remove everything this created, nothing else
├── lib/
│   ├── preflight.sh              # runtime reachable, ports free, tooling present
│   ├── images.sh                 # build, tag, create ECR repos idempotently, push
│   ├── discovery.sh              # attach Cloud Map aliases after placement (the one local-only shim)
│   ├── verify.sh                 # real-traffic checks incl. cross-service + permission refusal
│   └── report.sh                 # entry URL, credentials, per-unit log commands on failure
├── terraform/
│   └── main.tf                   # wrapper root: aws provider → Floci, calls modules/aws, full release set
└── README.md                     # which local stack to use when (FR-026)

infra/terraform/modules/aws/      # MODIFIED — the real module, shared with real-cloud deploys
├── compute.tf                    # FARGATE → EC2, awsvpc → bridge, drop network_configuration
├── capacity.tf                   # NEW — launch template + ASG + ECS capacity provider (real-cloud only)
└── database.tf                   # use the created DB's port instead of a hardcoded 5432

.claude/skills/deploy-local/      # NEW — the /deploy-local trigger
└── SKILL.md                      # thin wrapper: invokes the harness, interprets results
```

**Structure Decision**: the harness lives in `sim/local-deploy/`, beside the existing `sim/cloud-deploy/`
(apply-then-destroy check) and `sim/sim-up.sh` (Compose simulation), because all three are local
environment tooling and FR-026 keeps them coexisting. The Terraform **wrapper root** holds only provider
wiring and the release set; every resource still comes from `infra/terraform/modules/aws`, satisfying
FR-002. Module edits land in the shared module deliberately — they are corrections production needs too.

## Key design decisions

Evidence and alternatives in [research.md](./research.md); the decisions themselves:

1. **Browser entry is the ALB, not a published container port.** Floci binds the listener port inside its
   own container, so publishing that exposes the real path: browser → ALB → target group → task. Verified
   HTTP 200 from the Windows host.
2. **Containers must use Floci as their DNS resolver.** This is the entire difference between "the ALB
   doesn't work" and "the ALB works perfectly", and it is undocumented.
3. **Only the frontend is `public`.** `frontend/nginx.conf` already proxies `/auth/` and `/api/` to the
   edge, so the ALB needs exactly one target. Everything else stays private, as in production.
4. **`nginx.conf` proxies to the bare host `edge`**, not the Cloud Map FQDN the module injects — so the
   alias set must include short names as well, or nginx must be templated from the injected URL. A real
   mismatch that would otherwise present as a dead frontend.
5. **Run with `emulated = false`.** The flag existed only to skip slow RDS provisioning; RDS is in fact
   immediate, so the real database path gets exercised instead of bypassed.
6. **Verification exercises traffic, never resource existence.** The existing check would have passed both
   silent failures found during research.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A local-only DNS alias step outside the infrastructure definitions | Floci serves no Cloud Map DNS, so services cannot resolve each other by their production names without it, and every deployed unit addresses peers by those names. | **Tested and rejected**: Cloud Map `register-instance` succeeds but never resolves; Route 53 zones and records return `INSYNC` and never resolve. Rewriting peer URLs to IPs or to a proxy was rejected because it changes the deployed configuration — abandoning the parity that justifies this feature — and IPs churn on every task replacement. The alias is the only mechanism that works *and* leaves the application untouched. |
| Editing the shared production module rather than a local override | FR-002 forbids a local-only copy, and the compute-model and DB-port changes are corrections production needs anyway. | A local override would let local and production drift — precisely the failure this feature exists to prevent. |
