# Quickstart & Validation: 019 Local Production-Replica Deployment

How to run the deployment and prove it actually works. Design rationale in [research.md](./research.md);
the command surface is [contracts/deploy-command.md](./contracts/deploy-command.md).

## Prerequisites

- Docker Desktop running, with the daemon socket mountable (Floci needs `/var/run/docker.sock` and `-u root`).
- JDK 17 and the Gradle wrapper for backend image builds; Node for the frontend image.
- Host ports free: the emulator API, the ALB entry port, and the ECR registry port. Preflight checks these.
- No real cloud credentials are needed. If any are present they are ignored (FR-003).

## 1. Deploy

```bash
bash sim/local-deploy/deploy-local.sh
```

Expected: preflight → build → push → apply → bind names → verify, ending with the entry URL and a
privileged sign-in. **Cold runs are dominated by image builds and are not time-bounded**; a warm re-run
(`--skip-build`) should finish in under 5 minutes (SC-008).

## 2. Use it in a browser (US1 — SC-001)

Open the printed URL. Sign in with the printed credentials, then complete a real task — create a record in
one of the service consoles and read it back.

Expected: it works, and the traffic reached the app **through the ALB**, not a published container port.
Confirm by checking that the entry URL is the load balancer's, not a per-container address.

## 3. Prove the verification can actually fail (US2 — SC-003, SC-004)

This is the part that matters most, and the part most likely to be skipped. **A verification suite that has
never failed is not evidence of anything.** Each case must be observed red before the deploy path that makes
it green.

| Fault to inject | Expected |
|---|---|
| Remove one unit's internal name binding | Run **fails**, naming the unreachable dependency — not a pass because the resources exist |
| Stop one unit after deployment | Run **fails**, names the unit, prints a working command to read its logs |
| Point a unit's peer URL at a name that resolves nowhere | Cross-service check **fails**; health checks alone must not rescue the run |
| Attempt a governed action as a caller without the role | Action is **refused**; if it succeeds, authorization is not enforced and the run must fail (FR-025) |

If any of these passes, the verification is theatre — fix it before proceeding. Both silent failures found
while researching this feature would have passed a resource-existence check.

## 4. Health is not enough (SC-004)

With every unit reporting healthy, deliberately break inter-service addressing and re-run verification.

Expected: **failure**. In 017 every service was individually healthy while every orchestrated write was
refused, because roles were not forwarded between services. A run that passes here has reproduced that bug
class rather than caught it.

## 5. Authorization is real (US1/FR-024 — SC-010)

Expected: a privileged account performs a governed action successfully, **and** an unprivileged one is
refused. Both halves are required — a system that grants everything passes the first check alone.

## 6. Repeatability (US4 — SC-006)

```bash
bash sim/local-deploy/teardown.sh
bash sim/local-deploy/deploy-local.sh
```

Expected: teardown removes everything this deployment created and nothing else — confirm unrelated
containers on the machine survive. The second deploy succeeds with **zero** manual cleanup.

Then the harder cases:

- **Interrupt a run midway** (Ctrl-C during apply), then re-run → converges, no manual cleanup (FR-016).
- **Restart the emulator**, then re-run → repositories are recreated rather than failing on
  `RepositoryNotFoundException` (R4).
- **Rebuild one image**, then re-run → the running system serves the new build.

## 7. Production parity, and its limits (US3 — SC-005, SC-007)

```bash
# every unit deployed by the shared module, not a local-only definition
grep -rn "resource \"aws_" sim/local-deploy/terraform/   # expect: none — only provider + module block
```

Expected: the wrapper root declares **no** resources of its own. Confirm deployed workloads run under the
intended compute model, and that the database connection uses the port the created instance actually
reports (R5).

**Then read the enumerated gaps** in [data-model.md](./data-model.md#known-local-gap) and confirm you can
tell in under a minute what local success does *not* prove. At plan time the honest list is:

- **ECS capacity provisioning is unverifiable locally** — the emulator places tasks without any capacity,
  real AWS does not. This is the highest-risk gap in the feature: local green says nothing about it.
- Cloud Map resolution is substituted locally; production's own Cloud Map path is never exercised here.
- TLS on the public entry is not exercised.

## Success signals

SC-001 one command → usable in a browser · SC-003 zero false successes · SC-004 a broken dependency is named
· SC-005 100% deployed from the shared module · SC-006 teardown + redeploy with no manual cleanup ·
SC-007 gaps enumerated in one place · SC-008 warm run < 5 min · SC-009 nothing created in a real cloud ·
SC-010 permitted action succeeds and unpermitted one is refused · SC-011 logs reachable from printed output.
