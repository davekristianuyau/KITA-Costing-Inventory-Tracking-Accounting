# Quickstart — Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

Validates the feature on one machine with Docker (no host `terraform`/cloud-CLI needed). Run from the repo root
on branch `010-floci-multicloud-ci`.

## 1. The infra gate is green (US1)

```bash
# Same checks CI runs (containerized Terraform 1.9.8):
bash tests/run.sh contract                 # infra contract tests pass
# fmt / validate / tflint now pass on the 001 modules (were red before this feature)
```

**Expect**: format, validate, and tflint all pass — **0** failures (SC-001). Introduce a stray syntax error in
an `infra/terraform` file and re-run → the gate fails and names the file (the gate still catches real problems).

## 2. Measure per-cloud Floci coverage (US2/US3 — the probe, FR-011)

```bash
bash sim/cloud-deploy/probe.sh aws
bash sim/cloud-deploy/probe.sh gcp
bash sim/cloud-deploy/probe.sh azure
```

**Expect**: a coverage map per cloud at `sim/cloud-deploy/coverage/<cloud>.md` listing each module resource type
as `supported`/`unsupported`. This is the documented "deploy as is" boundary and drives the `emulated` guards.

## 3. Deploy each cloud's real 001 module against Floci (US2/US3)

```bash
bash sim/cloud-deploy/deploy-check.sh aws     # apply → state list → destroy, no real cloud
bash sim/cloud-deploy/deploy-check.sh gcp
bash sim/cloud-deploy/deploy-check.sh azure
```

**Expect**: each returns `pass` — the emulator-supported resource set applies, the expected resources appear in
`terraform state list`, then everything is destroyed with **0** residue (SC-002/SC-006), using only dummy
credentials (SC-003).

## 4. One command for all three (US4 / SC-004)

```bash
bash sim/cloud-deploy/run-all.sh
```

**Expect**: all three clouds' deploy checks run and report an overall pass, completing in **< 15 minutes**.

## 5. Regression is caught (SC-005)

```bash
# Temporarily break a module (e.g. an invalid attribute), then:
bash sim/cloud-deploy/deploy-check.sh aws     # → FAIL (the check deploys, not just validates)
```

**Expect**: the deploy check fails on the broken module — proving it exercises a real apply. Revert the change.

## 6. Real-cloud behavior is unchanged (FR-005)

`emulated` defaults to `false`; with it off, `terraform plan` of each 001 module matches the pre-feature
baseline (no drift) — the emulator support is additive only.

## CI

- `infra` job: **blocking**, now green.
- `cloud-deploy` job: **non-blocking** (`continue-on-error`), runs steps 2–4 automatically on push.

Details: [contracts/](./contracts/), [data-model.md](./data-model.md), [research.md](./research.md).
