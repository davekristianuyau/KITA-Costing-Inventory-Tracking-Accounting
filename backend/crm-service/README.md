# crm-service

KITA's customer bounded context (feature 005): customer master data, government-mandated
entitlements, loyalty tiers, and discount computation. Spring Boot + JPA + Flyway on PostgreSQL
(schema `crm`), behind the gateway at `/api/crm`.

It is also the **customer Party master** that `operations-service` validates against, and it prices
sales for that service without owning order capture.

## Modules

`customer` (master data + append-only attribute history), `entitlement` (senior/PWD eligibility),
`loyalty` (repeat-customer tiers), `discount` (the cascading engine, rules, stacking policy),
`common` (money/rounding, effective-dating, audit, security, error handling), `api` (REST
controllers).

## How a discount is computed

`POST /api/crm/discounts/compute` is **stateless** — it returns a price and never persists an order.

1. **Base** — `Σ quantity × unitPrice` over the line items.
2. **Tiers** — promotional rules effective for the sale date, plus the customer's evaluated loyalty
   tier, plus the statutory rules for entitlements they can actually claim.
3. **Cascade** — tiers apply in `priority` order, each to *what the previous tier left*, rounded per
   tier.
4. **Stacking** — the statutory and promotional paths are combined per the active policy.

The breakdown always reconciles: **`base − Σ(amountRemoved) = finalPrice`** to the cent, and the
price is never negative — a tier that would overshoot is capped and flagged `CAPPED`.

```
base 1000, tiers −25% then −5%
  −25% of 1000 → 250.00 removed   (750 left)
  −5%  of  750 →  37.50 removed   (712.50 left)
  final 712.50            1000 − 250 − 37.50 = 712.50 ✓
```

Ordering is by `priority` then `code`, so the result never depends on the order tiers were supplied
in. An **unknown or absent `customerId` is a walk-in** — no entitlements, no loyalty, never an error.

## Stacking policy

`GET`/`PUT /api/crm/discount-policy`. Default **`MOST_FAVORABLE`**: compute the statutory and
promotional paths independently and give the customer the lower price — *never silently both*. Also
supported: `STATUTORY_THEN_PROMO`, `PROMO_THEN_STATUTORY` (one continuous cascade in that order), and
`STATUTORY_ONLY`.

This is configurable rather than hard-coded because the statutory-vs-promotional interaction is
legally sensitive and genuinely varies by client and jurisdiction.

## Government-mandated discounts are data, not code

Statutory rules are effective-dated rows in `discount_rule`; a computation uses the version effective
for the **sale date**, so re-versioning a rule never re-prices an earlier quote. The Philippines
ruleset (senior citizen, PWD) ships as **adoptable seed data** in `V5__seed_ph_discounts.sql`.

> The seeded PH values are **representative**. Reconcile them against the governing statutes before
> production use, and adopt a change by inserting a new `(code, effective_date)` version rather than
> editing a row in place.

**VAT treatment** is modelled as its own cascade tier rather than a special case. Removing VAT from a
running amount `X` is `X × r/(1+r)`, so the exemption reconciles like any other line and composes
correctly whatever ran before it:

```
senior, VAT-inclusive 1120, VAT 12%, statutory 20%
  PH_SENIOR_VAT_EXEMPT  1120 × 0.12/1.12 → 120.00 removed  (1000 left)
  PH_SENIOR             1000 × 0.20      → 200.00 removed  ( 800 left)
  final 800.00           1120 − 120 − 200 = 800 ✓
```

A statutory rule carries its `entitlement_kind`, so the senior rule cannot fire for a PWD-only
customer. An entitlement is honoured **only** with its supporting ID reference on file; otherwise the
discount is withheld and the response carries `ENTITLEMENT_WITHHELD` (FR-014).

## Loyalty

Purchase history belongs to `operations-service`, so `POST /api/crm/customers/{id}/loyalty/evaluate`
takes the **measured activity** rather than querying orders here. The most demanding qualifying tier
is assigned and cached on the customer; re-evaluating with activity that no longer qualifies clears
it. A tier only contributes when its rule is effective for the sale date.

## Security & privacy

The gateway authenticates and forwards `X-Kita-Roles` (and `X-Kita-User`); this service only
interprets them. `CRM_ADMIN` manages customers, entitlements, rules, and policy; `SALES` reads
customers and computes discounts.

Entitlement **supporting ID references are stored but never returned or logged** — responses expose
only `supportingIdOnFile`, which is all a caller needs to know why a statutory discount did or did
not apply. Logs are structured JSON (`logback-spring.xml`) with masking as a backstop.

`crm.security.stub=true` (dev/test default) treats a caller with no role header as `CRM_ADMIN` so the
service is usable before the gateway is wired up. **Set `CRM_SECURITY_STUB=false` in any real
environment.**

## Endpoints

| Area | Endpoint |
|---|---|
| Customers | `POST/GET /api/crm/customers`, `GET/PATCH /api/crm/customers/{id}` |
| Entitlements | `POST/GET /api/crm/customers/{id}/entitlements` |
| Loyalty | `POST/GET /api/crm/loyalty/tiers`, `POST /api/crm/customers/{id}/loyalty/evaluate` |
| Discounts | `POST /api/crm/discounts/compute`, `GET/POST /api/crm/discount-rules`, `GET/PUT /api/crm/discount-policy` |
| Health | `GET /actuator/health` |

`specs/005-customer-discounts/contracts/crm-openapi.yaml` is the contract source of truth, enforced
bidirectionally by `OpenApiContractTest` — an undocumented or unimplemented operation fails the build.

## Run & test

Requires **JDK 17** and, for tests, a running **Docker** daemon (Testcontainers PostgreSQL 16).

```bash
cd backend
./gradlew :crm-service:spotlessApply      # google-java-format; run before build
./gradlew :crm-service:build              # compile + Spotless + Checkstyle + tests
./gradlew :crm-service:test --tests "*CascadingEngineTest*"
```

On Windows + Docker Desktop the tests reach the daemon over `tcp://127.0.0.1:2375`, so enable
*Settings → General → Expose daemon on tcp://localhost:2375 without TLS*. The workaround is applied
in `build.gradle.kts` and is a no-op on Linux/CI.

`AbstractCrmIT` truncates `discount_rule`, `stacking_policy`, and `loyalty_tier` between tests: most
tests configure their own rules, and a leaked rule or a changed policy would silently corrupt every
later test. `PhSeedIT` re-applies V5's INSERTs from the migration file so the shipped seed is still
verified.

Configuration is environment-driven (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`,
`CRM_SECURITY_STUB`); no secrets live in the repo.
