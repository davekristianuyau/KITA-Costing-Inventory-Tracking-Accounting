# AWS deployment imitation (US4)

Proves the "client prefers AWS → its AWS deployment path runs end-to-end locally, with **no real cloud**"
model (SC-005). Runs the **real** Terraform against **[Floci](https://floci.io/)** — a fast, free, MIT
local cloud emulator and drop-in LocalStack replacement (same port 4566) — using the running feature-008
stack as the compute/DB stand-in.

## What is imitated vs represented

Floci emulates the AWS control-plane/storage/secrets we exercise here:

| Reused from the 001 AWS module | Emulated by Floci |
|---|---|
| `common` naming module (`{client}-{env}`) | ✅ (names match a real deploy) |
| VPC + subnet + internet gateway + security groups | ✅ EC2 |
| S3 bucket (+ public-access-block + SSE) | ✅ S3 |
| Secrets Manager DB-credentials secret | ✅ Secrets Manager |

The client's running feature-008 stack (its gateway + services + Postgres) is the compute/DB **stand-in**,
and the imitated DB-credentials secret points at that stand-in's Postgres. This exercises the real
Terraform + AWS SDK path locally and de-risks 001 without any cloud spend.

> Floci also has Azure (`floci-az`) and GCP (`floci-gcp`) emulators; imitating client-b's GCP deployment
> (and Azure) is a planned follow-up. Spec 009 currently scopes the imitation to AWS.

## Usage

```bash
# 1) bring up the multi-client sim (identity + edge + two client stacks + frontend)
bash sim/sim-up.sh

# 2) "deploy" client-a's AWS stack to Floci (starts Floci; runs Terraform in a container —
#    no host terraform/aws needed; uses only dummy credentials)
bash sim/aws-imitation/deploy.sh client-a

# 3) verify: resources exist in Floci, no real creds used, login reaches client-a's deployment
bash sim/aws-imitation/verify.sh client-a

# teardown
docker compose -p kita-floci -f sim/aws-imitation/docker-compose.floci.yml down -v
```

No AWS account, credentials, or spend are involved. The provider is pinned to `http://floci:4566`
with dummy `test`/`test` keys.
