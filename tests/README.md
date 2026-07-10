# KITA Infrastructure Tests

Bash assertions for the multi-cloud Terraform infra. Two suites:

- **contract/** — static + config checks. No cloud credentials, no `apply`. Run anywhere.
- **integration/** — post-deploy smoke tests. Require a live deployment (credentials + remote state).

Run:

```bash
tests/run.sh            # contract only (default)
tests/run.sh integration
tests/run.sh all
```

## Contract tests (runnable now)

| Test | Task | Verifies |
|------|------|----------|
| `test_aws_module_interface.sh`   | T013 | aws module declares all 8 inputs + 7 outputs of the module-interface contract |
| `test_gcp_module_interface.sh`   | T039 | gcp module declares the same interface |
| `test_azure_module_interface.sh` | T040 | azure module declares the same interface |
| `test_config_schema.sh`          | T014 | validate-config accepts a valid Release Set; rejects `latest`, no-public, secrets |
| `test_invalid_provider.sh`       | T042 | unsupported `cloud_provider` rejected before provisioning (FR-007) |
| `test_provider_switch.sh`        | T041 | switching clouds is config-only; all outputs wired from aws/gcp/azure (FR-003) |

## Integration tests (need a live env)

Written with the promotion/lifecycle increments; each needs `deploy.sh` to have run against real
cloud credentials:

| Test | Task | Verifies (Success Criterion) |
|------|------|------------------------------|
| `test_deploy_health.sh`     | T015 | gateway aggregate health UP; gateway→service round trip |
| `test_backend_private.sh`   | T016 | backend services have no public endpoint (SC-013) |
| `test_idempotent_apply.sh`  | T017 | second apply = zero changes (SC-003) |
| `test_stg_prod_isolation.sh`| T028 | STG/PROD share no resources (SC-005) |
| `test_promotion_gate.sh`    | T029 | promote refuses a Release Set not healthy in STG (SC-006) |
| `test_auto_rollback.sh`     | T031 | failed aggregate health keeps the previous Release Set (SC-014) |
| `test_client_isolation.sh`  | T049 | two clients share nothing (SC-008) |
| `test_teardown_complete.sh` | T054 | teardown removes 100% of an env's resources (SC-009) |

## Success-criteria coverage (SC-001..SC-014)

| SC | Covered by |
|----|-----------|
| SC-002 (config-only cloud switch) | `test_provider_switch.sh` |
| SC-003 (idempotent apply)         | `test_idempotent_apply.sh` |
| SC-005 (STG/PROD isolation)       | `test_stg_prod_isolation.sh` |
| SC-006 (promotion gate)           | `test_promotion_gate.sh` |
| SC-008 (client isolation)         | `test_client_isolation.sh` |
| SC-009 (complete teardown)        | `test_teardown_complete.sh` |
| SC-010 (no secret leaks)          | CI secret-leak scan (T058) |
| SC-012 (naming/tags)              | `test_naming_convention.sh` (T050) |
| SC-013 (backends private)         | `test_backend_private.sh` |
| SC-014 (aggregate health)         | `test_deploy_health.sh`, `test_auto_rollback.sh` |
