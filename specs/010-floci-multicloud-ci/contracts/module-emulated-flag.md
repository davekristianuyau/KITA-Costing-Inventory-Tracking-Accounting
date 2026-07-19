# Contract — `emulated` module flag (001 modules)

An additive variable on each `infra/terraform/modules/<cloud>` module that lets the SAME module deploy against
both a real cloud (default) and a Floci emulator (skipping unsupported resources).

## Interface

```hcl
variable "emulated" {
  type    = bool
  default = false # real cloud: full module, unchanged
}
```

- When `false` (default / real cloud): every resource is created exactly as today.
- When `true` (emulator): resources whose type is `unsupported` in that cloud's Coverage Map (probe output) are
  skipped via `count = var.emulated && <unsupported> ? 0 : 1` (or `for_each` guards). Dependent outputs/
  references degrade gracefully (e.g. a DB-credentials secret still applies but its URL may reference a skipped
  instance's placeholder — kept valid).

## Invariants (MUST hold)

- **FR-005 / real-cloud unchanged**: with `emulated = false`, `terraform plan` output for each module is
  byte-for-byte identical to the pre-feature module. Verified by a plan-diff check.
- Guards exist **only** around resources the probe marked `unsupported` — no blanket skipping.
- No secret, credential, or endpoint value differs between `emulated` on/off for resources that exist in both.

## Acceptance

- `emulated = false` plan == baseline plan (no drift) for all three modules.
- `emulated = true` apply succeeds against the matching Floci emulator (deploy-check passes).
