# Phase 0 Research — Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

## D1. Local cloud emulator

- **Decision**: **Floci** for all three providers — AWS (`floci/floci`, port **4566**), GCP (`floci-io/floci-gcp`,
  port **4588**), Azure (`floci-io/floci-az`, port **4577**). Reached with dummy `test` credentials and provider
  `endpoints` overrides.
- **Rationale**: Proven in feature 009 for AWS (drop-in LocalStack replacement, applied 11 real resources); free
  (MIT); one tool family covers all three clouds. No real cloud, no spend (SC-003).
- **Alternatives**: LocalStack (AWS only in Community; GCP/Azure would need separate, different emulators);
  per-cloud official emulators (fragmented, partial); real cloud (cost + credentials — out of scope, FR-004).

## D2. Deploy the REAL 001 modules (not a parallel config)

- **Decision**: A thin **per-cloud wrapper root** under `sim/cloud-deploy/<cloud>/` configures the provider for
  Floci (endpoints + dummy creds) and calls the actual 001 module via
  `module "x" { source = "../../../infra/terraform/modules/<cloud>" ... }`. The production root
  (`infra/terraform/main.tf`) is **not** modified.
- **Rationale**: "Deploy as is" (spec) means exercising the real 001 module, not re-authoring a trimmed copy
  (009's `sim/aws-imitation` mirrored resources by hand — fine for a demo, but it doesn't validate the real
  module). A wrapper keeps the emulator-only provider config out of the production code path.
- **Alternatives**: add emulator endpoints to the production root providers (pollutes prod, risk of a real
  deploy hitting an emulator); keep 009-style hand-authored configs (duplicates the modules, drifts, not
  "as is").

## D3. Handling emulator-unsupported resources — additive compat flag

- **Decision**: Add an **additive `emulated` variable** (default `false`) to each 001 module. When `true`, it
  **skips only the resources the emulator cannot provision** (via `count`/`for_each` guards). Default `false`
  ⇒ a real-cloud deploy is byte-for-byte unchanged (FR-005). The exact guarded set per cloud comes from D4.
- **Rationale**: Keeps ONE module as the single source that applies to both real cloud and Floci. Guards are
  minimal and centered only on unsupported resources (e.g., managed compute/DB on GCP/Azure). Real-cloud
  behavior is provably unchanged because the guard defaults off.
- **Alternatives**: `terraform apply -target=...` a supported subset (brittle target/dependency lists, doesn't
  validate the module as a unit); full apply accepting partial failure (apply exits non-zero on any error —
  no clean pass/destroy). Both rejected.

## D4. Empirical coverage probe (FR-011) — the first task

- **Decision**: Before wiring the GCP/Azure checks, **probe each live Floci emulator** to record which of that
  module's resource types actually `apply` vs. fail, producing a per-cloud **coverage map**. That map decides
  which resources get the `emulated` guard (D3). Implemented as `sim/cloud-deploy/probe.sh <cloud>` and run as
  **task 1**.
- **Rationale**: The clarification (2026-07-19) mandates measuring coverage rather than assuming it from
  service-count lists. Evidence-driven guarding avoids both over-skipping and surprise apply failures.
- **Expected coverage (to be confirmed by the probe, not assumed final)**:
  - **AWS** — broad. 009 already applied VPC/subnet/IGW/SG/S3/Secrets Manager against Floci; Floci advertises
    RDS, ECS, ELB among ~68 services, so most of the AWS module (`aws_ecs_*`, `aws_db_instance`, `aws_lb*`,
    `aws_acm_certificate`, `aws_cloudwatch_log_group`, `aws_service_discovery_*`, IAM) is expected to apply,
    with few or no guards needed.
  - **GCP** — narrow. `floci-gcp` (~8 services incl. Cloud Storage + Secret Manager) is expected to cover
    `google_storage_bucket` + `google_secret_manager_secret*`, but **not** Compute (`google_compute_*`),
    Cloud SQL (`google_sql_*`), or VPC access connector → those get guarded.
  - **Azure** — narrow. `floci-az` (~15 services incl. Storage + Key Vault) is expected to cover
    `azurerm_storage_*` + `azurerm_key_vault*`, but **not** Container Apps (`azurerm_container_app*`),
    PostgreSQL Flexible (`azurerm_postgresql_flexible_*`), VNet, or Log Analytics → those get guarded.
- **Alternatives**: trust the published service lists (risk: emulator implements/omits differently than docs);
  skip probing and guard by guesswork (rejected by the clarification).

## D5. Terraform version — pin via the containerized runner

- **Decision**: All Terraform runs (probe + deploy checks) use the **`hashicorp/terraform` container pinned to
  `1.9.8`** (matching the version the CI fmt/validate gate already pins and the 009 runner used with AWS
  provider 5.100). The host Terraform install (local `C:\Terraform` v1.15.8) is **not** used by the checks, so
  local and CI are identical (FR-010).
- **Rationale**: Containerized = reproducible everywhere with zero host setup (consistent with 009). A single
  pin removes drift between the newer local install and CI.
- **Alternatives**: bump everything to 1.15.x (newer, but re-validates all providers for no functional gain
  now); let each environment use its own host Terraform (defeats FR-010).

## D6. Fix the blocking gate (FR-001)

- **Decision**: Make the current tflint failures pass by adding the missing **`required_version`** and
  **`required_providers`** blocks to the 001 modules tflint flags (`modules/aws`, `modules/gcp`,
  `modules/azure`, `modules/common`), and run `terraform fmt`. This is **additive metadata** (version/provider
  constraints) — no change to any resource or deploy behavior.
- **Rationale**: tflint's `terraform_required_providers` / `terraform_required_version` rules want each module
  to declare its provider + version constraints; adding them is standard hygiene and turns the gate green
  without touching behavior (SC-001).
- **Alternatives**: disable the tflint rules (hides a real best-practice gap); leave the gate red (defeats the
  quality gate — the whole motivation).

## D7. CI wiring (FR-006)

- **Decision**: Keep the **fmt / validate / tflint / contract-test gate BLOCKING** (now green after D6). Add a
  separate **NON-BLOCKING** `cloud-deploy` job (`continue-on-error: true`) that runs the probe + apply + destroy
  for all three clouds against Floci — mirroring how feature 009's heavy `sim-smoke` job is wired.
- **Rationale**: The apply checks start three emulators and run real Terraform (minutes); blocking every push
  on that is too slow. The fast gate stays authoritative for merges; the deploy job catches real deploy
  regressions as signal without gating routine work.
- **Alternatives**: blocking deploy job (slows every push, flaky on emulator startup); local-only, no CI (loses
  continuous enforcement — FR-006).

## D8. Verifying a deploy — Terraform-native, cloud-agnostic

- **Decision**: A deploy check is **apply → `terraform state list` (assert the expected resources are present)
  → destroy**, all via the pinned container. Success = `apply` exit 0 + expected resources in state + `destroy`
  exit 0 + no residue. No per-cloud SDK/CLI required.
- **Rationale**: Uniform across all three clouds; a successful `apply` means the provider received a create
  success from the emulator's API, which is exactly the coverage signal we want. Keeps the harness simple
  (Constitution VI) and host-dependency-free.
- **Alternatives**: per-cloud CLI assertions (aws/gcloud/az) — more fidelity but three more toolchains and more
  brittleness for little gain over state-based verification.

## Resolved unknowns

- Per-cloud "deploy as is" boundary → **measured by the D4 probe** (task 1), then encoded as the D3 `emulated`
  guards. | Terraform version → **1.9.8, containerized** (D5). | Local-run time bound → target **< 15 min** for
  all three clouds (SC-004; refined once the probe shows real apply times).
