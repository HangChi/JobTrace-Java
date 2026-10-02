# Implementation Plan: Application Read Model Migration

**Branch**: `codex/004-application-read-model` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-application-read-model/spec.md`

## Summary

Add an owner-scoped, read-only applications module to the Java modular monolith for the existing
application list and core detail contracts. Reuse the feature 003 signed identity principal, query
the unchanged PostgreSQL schema with explicit SQL, reproduce legacy filtering, sorting, cursor,
follow-up, stage, and event behavior, and prove parity with deterministic fixtures and
Testcontainers. Do not change the browser frontend, legacy routes, database schema, write ownership,
or production traffic.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Security, Spring JDBC,
Jakarta Validation

**Storage**: Existing PostgreSQL 17 applications, application stage occurrences, and application
events; read-only access with Flyway disabled

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, MockMvc, Testcontainers PostgreSQL,
ArchUnit, OpenAPI parser, JaCoCo, and checked-in legacy response fixtures

**Target Platform**: Java 21 Linux runtime and local/CI verification; no production deployment in
feature 004

**Project Type**: Spring Boot modular-monolith web application with an unchanged embedded
React/TypeScript frontend artifact

**Performance Goals**: Application list and detail reads at or below 500 ms p95 with the documented
representative data set

**Constraints**: Preserve the existing JSON and query contract; require the signed identity bridge;
scope every SQL statement by owner; no writes, schema changes, JPA mappings, frontend changes,
interview aggregation, canary, or traffic cutover

**Scale/Scope**: Two protected GET operations, four sorts with two directions, six filter groups,
page and cursor navigation, three application statuses, six application types, eight recruitment
stages, and core application detail history

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **Maintainable Code**: The module follows `web -> application -> domain`; one query adapter owns
  explicit SQL and controller concerns do not leak into query mapping. No new service or dependency
  is introduced.
- **Testing Gate**: Query parsing, cursor validation, ordering, contract mapping, owner isolation,
  authentication, missing records, dependency failure, performance, and architecture receive
  automated coverage. Changed code remains subject to 80% line and branch coverage.
- **Consistent UX**: No frontend or user-visible workflow changes. Existing response fields,
  defaults, empty states, and problem outcomes remain the compatibility target.
- **Performance Budgets**: Both reads retain the constitution's 500 ms p95 budget. Tests use a
  reproducible seeded data set and verify query-count bounds as well as elapsed time.
- **Delivery**: Feature 004 ends at reviewed code and CI evidence. Solo-Maintainer Mode still
  requires a pull request, green automated gates, resolved conversations, and a written review;
  deployment and production observation are explicitly deferred.

Post-design recheck: passed. Explicit SQL is justified by the existing PostgreSQL contract and
repository rule to use Spring JDBC or jOOQ rather than mechanical JPA mappings. The feature adds no
constitutional exception.

## Security and Data Boundaries

1. The feature 003 filter validates the request-bound assertion and establishes the principal.
2. Controllers take the owner only from that principal; query parameters never supply an owner.
3. Every list, count, stage, event, and detail query includes the same owner predicate at the
   database boundary.
4. Cross-owner and missing detail identifiers both map to the same `404 not_found` response.
5. All SQL sort expressions are selected from fixed application enums; no request string becomes
   an identifier or SQL fragment.
6. Responses use `private, no-store`; logs and metrics use request ID, operation, outcome class, and
   duration only.

## Project Structure

### Documentation (this feature)

```text
specs/004-application-read-model/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (Java repository)

```text
src/main/java/com/jobtrace/applications/
├── application/
│   ├── GetApplicationDetail.java
│   ├── ListApplications.java
│   └── ApplicationReadQuery.java
├── domain/
│   ├── ApplicationDetail.java
│   ├── ApplicationListCriteria.java
│   ├── ApplicationPage.java
│   └── ApplicationSummary.java
├── infrastructure/
│   └── PostgresApplicationReadQuery.java
└── web/
    ├── ApplicationReadController.java
    └── ApplicationListParameters.java

src/test/java/com/jobtrace/applications/
├── ApplicationReadControllerTest.java
├── ApplicationReadContractTest.java
├── ApplicationReadSecurityTest.java
├── ApplicationReadQueryIntegrationTest.java
├── ApplicationOwnerIsolationIntegrationTest.java
└── ApplicationReadPerformanceTest.java

src/test/resources/contracts/applications/
├── empty-page.legacy.json
├── representative-page.legacy.json
└── representative-detail.legacy.json
```

**Structure Decision**: Add one `applications` bounded-context package beside `analytics` and
`identityaccess`. Domain records contain contract data and validation-free value semantics;
application services own use-case checks; infrastructure owns SQL and row mapping; web owns HTTP
parameter normalization and response headers. No frontend source is touched.

## Verification Stages

1. **Contract baseline**: Capture deterministic legacy list and detail responses and document query
   defaults, cursor structure, ordering, and problem outcomes.
2. **Domain and parsing**: Implement immutable response models, criteria normalization, and strict
   cursor decoding with unit tests.
3. **Storage parity**: Implement owner-scoped list/count/detail SQL and verify it against the legacy
   schema in Testcontainers.
4. **Protected HTTP reads**: Expose the two GET operations behind the existing signed identity
   bridge with no-store responses and uniform problems.
5. **Convergence**: Run contract, isolation, performance, architecture, coverage, frontend artifact,
   secret, and Java-only production-artifact checks; record self-review evidence.

## Complexity Tracking

No constitution violations require justification. The feature reuses existing dependencies,
runtime, identity bridge, error model, and database.
