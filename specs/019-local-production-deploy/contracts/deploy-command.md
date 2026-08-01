# Contract: the deploy command

The user-facing surface of this feature. Invoked as `/deploy-local` (skill) or directly as
`bash sim/local-deploy/deploy-local.sh`. The skill is a thin wrapper — it adds no behaviour, so both paths
observe the same contract.

## Invocation

```bash
bash sim/local-deploy/deploy-local.sh [--client <name>] [--env <name>] [--skip-build] [--timeout <seconds>]
bash sim/local-deploy/teardown.sh     [--client <name>] [--env <name>]
```

| Flag | Default | Purpose |
|---|---|---|
| `--client` | a local-only default | Resource-name prefix (Deployment environment) |
| `--env` | `stg` | Never `prod` locally — avoids the module's production sizing path |
| `--skip-build` | off | Reuse existing images; the warm path measured by SC-008 |
| `--timeout` | bounded default | Per-unit health wait; expiry fails the run (FR-015) |

## Phases and ordering

Ordering is part of the contract — later phases depend on earlier ones, and each must fail the run rather
than proceed on bad state.

1. **Preflight** (FR-018) — container runtime reachable and socket-mountable; required host ports free;
   build tooling present. Fails **before any resource is created**.
2. **Build** — images for all nine units. A build failure stops here, before infrastructure is touched.
3. **Publish** — create ECR repositories idempotently, tag, push. Repository creation must tolerate both
   "already exists" and "unexpectedly missing" (R4).
4. **Apply** — `terraform apply` on the wrapper root against the emulator, with `emulated = false`.
5. **Bind internal names** — attach aliases for every unit's FQDN *and* short name (R6, R7).
6. **Verify** — real traffic (see below).
7. **Report** — entry URL and sign-in credentials on success; per-unit log commands on failure.

## Verification contract (FR-010 – FR-013)

A run passes only when **all** of the following hold. Resource existence is never sufficient.

| Check | Kind | Passes when |
|---|---|---|
| Entry point reachable from the host | `reachability` | The printed URL returns success **from the developer's machine**, through the ALB (FR-012) |
| Every unit healthy | `health` | Each unit's `health_path` reports healthy within the timeout |
| Cross-service action | `cross_service` | An action requiring one service to call another succeeds (FR-011) |
| Permission refusal | `permission` | An action by a caller lacking permission is **refused** (FR-025) |

A run consisting only of `reachability` and `health` checks is a **contract violation**, not a pass — that
combination is precisely what passed while the system was broken in 017.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Deployed and verified. Entry URL and credentials printed. |
| `1` | Preflight failed. Nothing was created. |
| `2` | Build or publish failed. No infrastructure was touched. |
| `3` | Apply failed. Partial resources may exist; re-running converges (FR-016). |
| `4` | Verification failed. **Environment left running for diagnosis** (FR-014). |

Any non-zero exit MUST name the failing component. Exit `0` MUST NOT be returned when any check failed —
this is the single most important guarantee in the feature (SC-003).

## Output contract

**On success** — must include, at minimum:

- the entry URL, reachable from the developer's browser
- the credentials for at least one fully privileged account (FR-019, FR-024)
- a one-line summary of what was verified, so a pass is legible rather than assumed

**On failure** — must include:

- which check failed and which unit was at fault
- a **copy-pasteable command** to read that unit's logs, with the generated container name already
  resolved (FR-014) — the developer must never have to hunt for it
- the statement that the environment is still running

**Never** printed: secrets beyond the demo sign-in credentials; database passwords; any real cloud
credential (FR-023).

## Idempotence and teardown

- Re-running after success, after a partial failure, or after an emulator restart converges without manual
  cleanup (FR-016).
- Teardown removes every resource, container, network and image tag the deployment created, and **nothing
  else** on the machine (FR-017). Unrelated containers are untouched — a destructive teardown that
  over-reaches would make the command unsafe to use casually, defeating its purpose.
