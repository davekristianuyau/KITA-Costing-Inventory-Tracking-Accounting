# operations-service — test coverage

Success criteria (spec 003) → covering tests.

| SC | Criterion | Test |
|----|-----------|------|
| SC-001 | on-hand = Σ movements | `StockLedgerIntegrationTest`, `AvailabilityReconciliationTest` |
| SC-002 | no oversell under concurrency | `SalesOrderConcurrencyTest` |
| SC-003 | sales order create → reserve → fulfill | `SalesOrderIntegrationTest` |
| SC-004 | multi-level BOM explosion | `BomServiceIntegrationTest`, `BomContractTest` |
| SC-005 | invalid/inactive party rejected | `SalesOrderIntegrationTest`, `GoodsReceiptIntegrationTest` |
| SC-006 | availability reconciles (on-hand/reserved/available) | `AvailabilityReconciliationTest` |
| SC-007 | exact decimal money/quantity math | `MarginUnitTest`, `ValuationServiceUnitTest`, `UomConversion*Test` |
| SC-008 | accounting can retrieve period movement data | `MovementDataContractTest` |
| SC-009 | kit/recipe sale deducts components (UoM-converted) | `KitSaleIntegrationTest` |
| SC-010 | production build atomic (no partial consumption) | `BuildIntegrationTest` |
| SC-011 | BOM cost roll-up + margin | `CostRollupIntegrationTest`, `MarginUnitTest` |
| — | FIFO/FEFO consumption (FR-031) | `FefoConsumptionIntegrationTest` |
| — | no negative stock (FR-004) | `StockLedgerIntegrationTest`, `StockLedgerServiceUnitTest` |

Run: `cd backend && ./gradlew :operations-service:test` (Docker required for Testcontainers;
on Windows/Docker-Desktop see the workaround in `operations-service/build.gradle.kts`).
