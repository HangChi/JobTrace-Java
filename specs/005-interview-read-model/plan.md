# Implementation Plan: Private Interview Read Model Migration

**Branch**: `codex/005-interview-read-model` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-interview-read-model/spec.md`

## Summary

Add owner-scoped, read-only Java compatibility for the existing private interview list, private review detail, and application dialog with interview summaries. Reuse feature 003's signed identity and feature 004's application detail. Query the unchanged PostgreSQL schema with explicit, parameterized SQL; preserve strict interview filters, tuple cursor order, child ordering, and owner isolation. Prove contract and performance parity with synthetic legacy fixtures and isolated PostgreSQL tests. No Java deployment, frontend change, schema migration, write transfer, or traffic activation is in scope.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1 Web MVC, Spring Security, Spring JDBC, Jakarta Validation, Jackson, and Micrometer; no new production dependency

**Storage**: Existing PostgreSQL 17 `interview_reviews`, `interview_questions`, `interview_action_items`, `applications`, and `application_stage_occurrences`; read-only queries with Flyway disabled

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers PostgreSQL, ArchUnit, OpenAPI parser, JaCoCo, checked-in synthetic legacy fixtures, and existing Java-only artifact CI

**Target Platform**: Java 21 Linux runtime; local and CI verification only, with no deployment in feature 005

**Project Type**: Spring Boot modular monolith with unchanged embedded React/TypeScript frontend artifact

**Performance Goals**: Private list, review detail, and application dialog each at or below 500 ms p95 under a documented representative fixture; bounded query counts for child loading

**Constraints**: Exact private success-response contract and legacy filter/cursor behavior; trusted signed principal only; owner predicate on every data path; safe Java problem envelope; no writes, public/community routes, schema changes, JPA, frontend changes, or cutover

**Scale/Scope**: Three protected GET operations, six interview stages, three review statuses, three results, three publication filter modes, 1–100 item cursor pages, ordered question/action collections, and one cross-context dialog composition

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **I. Maintainable Code by Design**: `interviews` owns its domain, query ports, and SQL adapter; a thin `applicationdialog` orchestrator composes existing application detail with interview summaries. No package cycle, new service, or new production dependency is planned. Input and SQL rules remain explicit and allowlisted.
- **II. Testing Is a Release Gate**: Unit tests cover criteria, cursor, mapping, and use-case errors; PostgreSQL integration tests cover owner isolation, child records, filters, and ordering; MockMvc/contract tests cover all three routes and signed identity; performance and architecture tests cover cross-context risks. Changed production code must reach at least 80% line and branch coverage and pass all existing CI gates.
- **III. Consistent and Accessible User Experience**: No browser source or interaction changes. Existing private JSON fields, date display values, empty collections, validation outcomes, and no-store behavior remain the target. The existing frontend's accessibility checks continue to run; no new accessibility exception is needed.
- **IV. Measured Performance Budgets**: Each interactive read has a 500 ms p95 budget with a reproducible owner fixture, warm-up, and measured samples. Query-count bounds prevent N+1 child loading; no background work is added to the request path.
- **Delivery and review**: Feature 005 remains code-and-CI only. Future implementation will require a PR, green gates, resolved conversations, and a written Solo-Maintainer Mode self-review. Production activation would require a separate release decision and rollback plan.

Post-design recheck: passed. The design reuses approved modules and dependencies, preserves the no-deployment decision, and introduces no constitutional exception.

## Security and Data Boundaries

1. Extend the feature 003 bridge to only the planned private GET paths; each controller must require `BridgeIdentity`, not merely generic Spring authentication.
2. Owner identity comes solely from the verified principal, never a header, path, or query parameter. A published review remains private to its owner on these routes.
3. Review list/count SQL binds `review.owner_id`; detail resolves an owned review before loading questions/actions, with an owner-bound child join as defense in depth.
4. The dialog resolves the owned application through feature 004 before returning interview summaries; summary SQL binds both review and application owner. Missing and cross-owner IDs return the same not-found outcome.
5. Cursor/date/enum inputs are parsed and parameterized before SQL. Only fixed query fragments are used for order; a cursor never grants access or encodes owner authority.
6. Response headers are `private, no-store`; logs and metrics use request ID, bounded operation/outcome labels, and duration only. Private content, IDs, search terms, and assertions are excluded.

## Project Structure

### Documentation (this feature)

```text
specs/005-interview-read-model/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── checklists/requirements.md
```

`tasks.md` is intentionally not created in the plan phase.

### Source Code (future implementation)

```text
src/main/java/com/jobtrace/interviews/
├── domain/              # immutable summary, page, detail, criteria, cursor
├── application/         # list/detail/for-application use cases and query port
├── infrastructure/      # PostgreSQL read adapter and row mapping
└── web/                 # private GET controller, parameters, metrics

src/main/java/com/jobtrace/applicationdialog/
├── application/         # compose feature 004 detail with interview summaries
└── web/                 # GET /api/applications/{id}/detail

src/test/java/com/jobtrace/interviews/
src/test/java/com/jobtrace/applicationdialog/
src/test/resources/contracts/interviews/
src/test/resources/postgres/
```

**Structure Decision**: Keep `applications` and `interviews` independently testable. The dialog package is the single cross-context consumer and depends on their application-level use cases; neither domain package depends on the other. Update ArchUnit rules to enforce this direction and detect cycles. The existing React/TypeScript frontend remains untouched.

## Verification Stages

1. **Legacy baseline**: Record private response fixtures and exact parser, cursor, error, and child-order semantics from the existing service. Include assessment, unlinked stage, published-by-owner, empty, and cross-owner examples.
2. **Domain and parsing**: Add immutable models, strict parameter validation, and cursor decoding tests before implementing the query adapter.
3. **Storage parity**: Add a test-only PostgreSQL schema aligned to the legacy migration head; verify list/count/detail/dialog SQL, child ordering, owner predicates, and stable cursor traversal with two owners.
4. **Protected HTTP reads**: Add three GET handlers behind the signed bridge; test missing/forged/expired/path-mismatched/replayed assertions, normal principals, safe problems, no-store headers, and dependency outages.
5. **Convergence**: Parse the feature and repository OpenAPI contracts, compare synthetic legacy JSON fixtures, measure 500 ms p95 and query bounds, run architecture/coverage/static checks and `./mvnw verify`, then record written self-review and PR CI evidence. Do not deploy or route production traffic.

## Key Risks and Mitigations

- **Cross-context existence leak**: A dialog that queries interviews before confirming application ownership could reveal counts or timing. Resolve the owned application first and compare missing/cross-owner outcomes.
- **Legacy parser mismatch**: Interview filters reject invalid values while feature 004 application filters may ignore them. Use interview-specific normalization tests rather than reuse the application parser.
- **Publication confusion**: Published reviews still appear in the owner's private list, but public/community reads have different access and possible side effects. Keep route and query boundaries separate.
- **Stale schema assumptions**: Later legacy migrations added assessment and publication fields. Test against the recorded migration head and avoid production migrations in Java.

## Complexity Tracking

No constitution violations require justification. The new dialog orchestration package makes the existing cross-context response explicit without adding a deployment unit or dependency.
