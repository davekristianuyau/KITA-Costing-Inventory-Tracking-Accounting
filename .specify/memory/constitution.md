<!--
SYNC IMPACT REPORT
==================
Version Change: (template) → 1.0.0
Ratification: Initial adoption of the KITA project constitution.
Modified Principles: N/A (initial version)
Added Principles:
  I. Specification-Driven Development
  II. Test-Driven Development (NON-NEGOTIABLE)
  III. Security & Data Integrity First (NON-NEGOTIABLE)
  IV. Environment Isolation
  V. Observability & Debuggability
  VI. Simplicity & YAGNI
  VII. Automated Quality Gates
Added Sections:
  - Additional Constraints (technology & data)
  - Development Workflow
  - Governance
Removed Sections: None
Templates Status:
  ✅ .specify/templates/plan-template.md - Generic Constitution Check gate; no changes required
  ✅ .specify/templates/spec-template.md - No principle-specific references; no changes required
  ✅ .specify/templates/tasks-template.md - No principle-specific references; no changes required
  ✅ .specify/templates/agent-file-template.md - No changes required
  ✅ .specify/templates/checklist-template.md - No changes required
Follow-up TODOs: None
Rationale: Initial constitution adapted from a platform constitution into a general,
  solo-developer engineering charter. All team-, platform-, and vendor-specific mandates
  (multi-team isolation, RBAC, SSO, AWS lock-in, chargeback) were removed in favor of
  technology-neutral principles suitable for a single developer working on this project.
-->

# KITA Constitution

KITA is a Costing, Inventory Tracking, and Accounting system. Because it handles
financial and inventory data, correctness, data integrity, and reproducibility are
first-class concerns. This constitution defines the non-negotiable engineering
practices for the project, scoped to a single developer working independently.

## Core Principles

### I. Specification-Driven Development

Every non-trivial feature MUST begin with a written specification before implementation.

- Each feature spec MUST capture prioritized user stories (P1, P2, P3), each
  independently testable.
- Acceptance scenarios MUST be expressed in Given-When-Then form.
- An implementation plan MUST verify constitution compliance before coding begins.
- Specs and plans live under `/specs/` in feature-based subdirectories.

**Rationale**: Writing the specification first forces intent to be clear, makes work
resumable across sessions, and prevents scope drift for a solo developer with no
second reviewer to catch ambiguity.

### II. Test-Driven Development (NON-NEGOTIABLE)

All feature and bugfix work MUST follow the Red-Green-Refactor cycle.

- Tests are written and confirmed to fail before implementation begins.
- Cover business logic with unit tests, module boundaries with integration tests,
  and public contracts (APIs, schemas) with contract tests.
- Financial and inventory calculations (costing, valuation, ledger balancing) MUST
  have explicit test coverage including edge cases and rounding behavior.
- All tests MUST pass before a change is committed to the main branch.

**Rationale**: An accounting system is only trustworthy if its arithmetic and state
transitions are provably correct. TDD is the cheapest way to keep that guarantee
without a QA team.

### III. Security & Data Integrity First (NON-NEGOTIABLE)

Financial and inventory data MUST be protected and kept internally consistent.

- Secrets, credentials, and connection strings MUST NOT be committed to the
  repository; use environment variables or a secrets store.
- Data at rest and in transit MUST be encrypted where the deployment target supports it.
- Monetary values MUST use exact/decimal representations — never binary floating point.
- Every write that changes financial state MUST be atomic (transactional) and, where
  applicable, produce an audit trail of what changed, when, and why.
- Input MUST be validated at trust boundaries before it reaches persistence.

**Rationale**: A costing/accounting system that leaks data or silently corrupts a
balance is worse than no system at all. These safeguards are not optional.

### IV. Environment Isolation

Development, staging, and production data and configuration MUST remain separate.

- No shared databases across environments; production data MUST NOT be used casually
  in development.
- Promotion between environments MUST be an intentional, explicit action.
- Configuration MUST be environment-scoped and never hard-coded.

**Rationale**: Keeping environments independent prevents experiments and test data
from ever polluting real financial records.

### V. Observability & Debuggability

The system MUST make its own behavior inspectable.

- Structured logging MUST be emitted for significant operations and all errors.
- Failures MUST log enough context (inputs, identifiers, stack) to diagnose without
  reproducing under a debugger.
- A health/status check MUST exist for any long-running service.

**Rationale**: A solo developer relies on good logs instead of a team to triage
issues; debuggability is a design requirement, not an afterthought.

### VI. Simplicity & YAGNI

Prefer the simplest solution that satisfies the current requirement.

- Introduce abstractions, patterns, and dependencies only when a concrete, present
  need justifies them.
- Any added complexity that appears to violate this principle MUST be explicitly
  justified in the implementation plan's Complexity Tracking section.
- Delete dead code and unused dependencies rather than leaving them "just in case".

**Rationale**: Complexity a single maintainer cannot hold in their head becomes
unmaintainable. YAGNI conserves the project's most limited resource — attention.

### VII. Automated Quality Gates

Quality checks MUST be automated and enforced consistently, not run by memory.

- The full test suite, linting, and type checks (where the language supports them)
  MUST run before merge to the main branch.
- A change MUST NOT merge while any gate fails (fail-fast).
- Where feasible, gates run in CI so behavior is identical across machines.

**Rationale**: Automation gives a solo developer the discipline a review process
would otherwise provide, and keeps standards from eroding under time pressure.

## Additional Constraints

### Technology & Data

- The technology stack is chosen per project need and documented in each feature's
  implementation plan; this constitution mandates no specific vendor or framework.
- Chosen tools MUST be pinned to explicit versions for reproducible builds.
- Schema and data migrations MUST be versioned, reversible where practical, and
  applied through a repeatable mechanism — never by ad-hoc manual edits to production.
- Backups of financial data MUST exist and be restorable before production use.

### Code Organization

- Source, tests, and specifications MUST be organized in clearly separated directories.
- Tests mirror the structure of the code they exercise (unit, integration, contract).
- Specifications live under `/specs/` with one subdirectory per feature
  (`spec.md`, `plan.md`, `tasks.md`).

## Development Workflow

1. **Specify**: Write the feature spec with prioritized, independently testable stories.
2. **Plan**: Produce an implementation plan, verify constitution compliance, and
   record any justified complexity.
3. **Develop**: Work on a feature branch (`feat/###-feature-name`); write failing
   tests first, then implement, running tests locally as you go.
4. **Verify & Integrate**: Ensure all quality gates pass, then merge to the main
   branch. Commit or push only when the work is verified.

## Governance

### Constitution Authority

This constitution supersedes other development practices for the project. Where guidance
conflicts, this document wins.

### Amendment Process

1. Propose the amendment with rationale and impact analysis.
2. Determine the version bump (MAJOR.MINOR.PATCH) per the versioning policy.
3. Update this file and any dependent templates or docs affected by the change.
4. Ratify by committing the updated constitution with a descriptive message.

### Versioning Policy

- **MAJOR**: Backward-incompatible governance changes or principle removals/redefinitions.
- **MINOR**: New principles added or materially expanded guidance.
- **PATCH**: Clarifications, wording improvements, typo fixes, non-semantic refinements.

### Compliance Verification

- Every implementation plan MUST include a Constitution Check gate.
- Complexity that violates the Simplicity principle MUST be explicitly justified.
- Constitution violations MUST be resolved before merge to the main branch.

### Runtime Guidance

For detailed workflow guidance during feature implementation, refer to the templates in
`.specify/templates/` and the Spec Kit command workflows.

**Version**: 1.0.0 | **Ratified**: 2026-07-08 | **Last Amended**: 2026-07-08
