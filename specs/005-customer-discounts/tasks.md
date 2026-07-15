---
description: "Task list for Customer Records & Discounts Service (crm-service)"
---

# Tasks: Customer Records & Discounts Service

**Input**: Design documents from `/specs/005-customer-discounts/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED (TDD per Constitution II). Contract tests (OpenAPI), Testcontainers integration +
migration tests, and discount-computation unit tests (cascading order, per-tier rounding,
reconciliation, caps, stacking, VAT treatment) are written before the code they cover.

**Module**: new Gradle module `backend/crm-service`, package root `com.kita.crm`, schema `crm`.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no dependency)

---

## Phase 1: Setup

- [X] T001 Add `:crm-service` to `backend/settings.gradle.kts` and create `backend/crm-service/build.gradle.kts` mirroring `operations-service` (Spring Web, Data JPA, Validation, Actuator, Flyway, PostgreSQL, Testcontainers; Spotless/Checkstyle; Windows Testcontainers workaround)
- [X] T002 Create package skeleton under `backend/crm-service/src/main/java/com/kita/crm/{customer,loyalty,entitlement,discount,common,api}` and `CrmServiceApplication`
- [X] T003 [P] Add `backend/crm-service/src/main/resources/application.yml` (datasource via env, JPA, Flyway, Actuator, JSON logging) — no secrets
- [X] T004 [P] Flyway baseline `.../db/migration/V1__init_crm_schema.sql` (schema `crm`, extensions, common columns)
- [X] T005 [P] Add the `crm-service` build to CI `.github/workflows/ci.yml`

---

## Phase 2: Foundational (Blocking)

- [X] T006 [P] `common/Money.java` + rounding policy (half-up to minor unit) + `BigDecimal` helpers, with unit tests
- [X] T007 [P] `common/EffectiveDated` support (rule effective for a sale date)
- [X] T008 [P] `common/AuditEvent` entity + repository + append-only writer (PII-scrubbed)
- [X] T009 [P] `common/` global exception handler + validation error DTO for the API boundary
- [X] T010 Role guards + API security scaffolding (privileged vs. read); Testcontainers singleton base test (`AbstractCrmIT`) with per-test TRUNCATE

**Checkpoint**: build compiles; migrations apply; base test harness runs.

---

## Phase 3: User Story 1 - Maintain customer records (Priority: P1)

**Goal**: create/update/retrieve customers with entitlements + history; serve as customer Party master.
**Independent Test**: create a customer with a senior/PWD entitlement, edit attributes, deactivate, and
read back the record + history; fetch by id (operations-service party validation path).

### Tests (write first, must FAIL) ⚠️
- [X] T011 [P] [US1] Contract test for `/customers`, `/customers/{id}`, `/customers/{id}/entitlements`
- [X] T012 [P] [US1] Integration test: customer + entitlement persistence, history retention, ID-ref not exposed

### Implementation
- [X] T013 [P] [US1] Flyway `V2__customer.sql` (customer, customer_attribute_history, entitlement)
- [X] T014 [P] [US1] `customer/Customer` + `entitlement/Entitlement` entities + repositories
- [X] T015 [US1] `customer/CustomerService` (create/update, status, history) + `entitlement/EntitlementService`
- [X] T016 [US1] `api/CustomerController` + DTOs; validation (unique customer_code); entitlement ID refs excluded from responses/logs; unknown customer at lookup handled

**Checkpoint**: customer directory + party lookup works and is independently testable.

---

## Phase 4: User Story 2 - Cascading discount computation (Priority: P1) 🎯 MVP

**Goal**: compute final price by applying tiers sequentially with a reconciling breakdown.
**Independent Test**: configure ‑25% then ‑5%; compute base 1000 → 712.50 with breakdown 250.00 then
37.50; base − Σ = final; empty breakdown when no discount.

### Tests (write first, must FAIL) ⚠️
- [X] T017 [P] [US2] Contract test for `/discounts/compute`, `/discount-rules`, `/discount-policy`
- [X] T018 [P] [US2] Unit tests for the cascading fold per `contracts/discount-engine.md`: the ‑25%/‑5% example, per-tier rounding, reconciliation (SC-001/002), deterministic order (FR-006)
- [X] T019 [P] [US2] Unit tests for caps: discounts exceeding base → final 0, flag CAPPED (SC-005); walk-in/unknown customer → base price

### Implementation
- [X] T020 [P] [US2] Flyway `V3__discount_rules.sql` (discount_rule + versions, stacking_policy)
- [X] T021 [P] [US2] `discount/DiscountRule` + `discount/StackingPolicy` entities + repositories
- [X] T022 [US2] `discount/CascadingEngine` — ordered fold with per-tier rounding, cap ≥ 0, breakdown output
- [X] T023 [US2] `discount/DiscountComputationService` — build tier list (promotional), compute, reconcile; flags
- [X] T024 [US2] `api/DiscountController` (compute stateless) + `discount-rules` + `discount-policy` endpoints + DTOs

**Checkpoint**: cascading discounts compute correctly and reconcile. **(MVP with US1)**

---

## Phase 5: User Story 3 - Loyalty / repeat-customer tiers (Priority: P2)

**Goal**: qualifying customers contribute a loyalty discount tier to the cascade.
**Independent Test**: configure a tier with a threshold; a qualifying customer's computation includes
the loyalty tier; a non-qualifying one does not.

### Tests (write first, must FAIL) ⚠️
- [X] T025 [P] [US3] Contract test for `/loyalty/tiers` and `/customers/{id}/loyalty/evaluate`
- [X] T026 [P] [US3] Integration test: tier eligibility from qualifying activity; re-evaluation on change (FR-010/011, SC-004)

### Implementation
- [X] T027 [P] [US3] Flyway `V4__loyalty.sql` (loyalty_tier)
- [X] T028 [P] [US3] `loyalty/LoyaltyTier` entity + repository
- [X] T029 [US3] `loyalty/LoyaltyService` — evaluate tier from configurable criteria (count/value over period); activity sourced from operations-service/port or passed in
- [X] T030 [US3] Include the customer's loyalty tier in the cascade in `DiscountComputationService`; `api` wiring for tiers + evaluate

**Checkpoint**: repeat customers automatically receive their tier discount.

---

## Phase 6: User Story 4 - Government-mandated discounts (Priority: P2)

**Goal**: statutory discounts (senior/PWD) via the engine, with VAT treatment and configurable stacking.
**Independent Test**: with the PH seed rule and a promo cascade both applicable, an entitled customer
gets the more-favorable outcome (default policy); missing supporting ID → withheld + flag.

### Tests (write first, must FAIL) ⚠️
- [X] T031 [P] [US4] Unit tests for statutory tiers + VAT treatment and the stacking policy modes per `contracts/discount-engine.md` (SC-003)
- [X] T032 [P] [US4] Unit test: entitlement without supporting ID → statutory withheld, flag ENTITLEMENT_WITHHELD (FR-014)

### Implementation
- [X] T033 [P] [US4] Flyway `V5__seed_ph_discounts.sql` (senior/PWD statutory rules + VAT treatment as seed data)
- [X] T034 [US4] Extend the engine with statutory tier building + VAT treatment and apply `StackingPolicy` (MOST_FAVORABLE default) in `DiscountComputationService`
- [X] T035 [US4] Enforce supporting-ID requirement for statutory eligibility; surface flags/reasons in the compute response

**Checkpoint**: government-mandated discounts computed and combined per policy.

---

## Phase 7: Polish & Cross-Cutting

- [X] T036 [P] Structured JSON logging with PII/entitlement-ID scrubbing (FR-003/016)
- [X] T037 [P] OpenAPI contract wired as source of truth; contract tests green against `contracts/crm-openapi.yaml`
- [X] T038 [P] `backend/crm-service/README.md` (module purpose, run/test, seed rules, compute API)
- [X] T039 Audit trail on rule/entitlement/policy changes (FR-016) verified end-to-end
- [X] T040 Full `:crm-service:build` green (Spotless/Checkstyle/tests) in CI; fail-fast gate

---

## Dependencies & Execution Order
- Setup (P1) → Foundational (P2) → US1 → US2 → {US3 ∥ US4} → Polish.
- US2 depends on the engine + rules (its own phase); US3 & US4 extend the computation from US2.
- Within a phase, `[P]` tasks (distinct files) run in parallel; tests precede implementation (TDD).

## Implementation Strategy
MVP = Setup + Foundational + US1 + US2 (customer records + cascading discount compute with reconciling
breakdown). Then US3 (loyalty) and US4 (government-mandated + stacking) complete the discount model.

## Notes
- Highest-risk: cascading correctness + reconciliation (T018/T022), stacking policy + VAT (T031/T034),
  caps/withholding (T019/T032) — keep those tests rigorous with golden values.
- Money exact decimal; per-tier rounding; base − Σ(removed) = final to the cent.
- Commit after each task/group and push per project workflow.
