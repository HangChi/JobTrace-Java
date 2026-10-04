---
description: "Implementation tasks for account-session revocation"
---

# Tasks: Account Session Revocation

**Input**: Design documents from `/specs/012-session-revocation/`

## Phase 1: Foundational boundaries

- [x] T001 [US1] Add the revocation result and safe not-found domain types in `src/main/java/com/jobtrace/identityaccess/domain/`
- [x] T002 [US1] Add the owner-scoped command port and validated use case in `src/main/java/com/jobtrace/identityaccess/application/`
- [x] T003 [P] [US1] Add unit coverage for validation, success, and missing rows in `src/test/java/com/jobtrace/identityaccess/RevokeAccountSessionTest.java`

## Phase 2: Durable mutation

- [x] T004 [US1] Implement one parameterized owner-scoped `DELETE ... RETURNING` adapter in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountSessionCommand.java`
- [x] T005 [US1] Add database and concurrency coverage in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationIntegrationTest.java`

## Phase 3: HTTP contract and authorization

- [x] T006 [US1] Add the strict request model, DELETE controller, error mapping, and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T007 [US1] Protect the exact DELETE route with request-bound bridge authentication in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T008 [US1] Add response, validation, not-found, and no-store contract coverage in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationContractTest.java`
- [x] T009 [US1] Add DELETE-bound assertion and fail-closed authorization coverage in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationSecurityTest.java`
- [x] T010 [US1] Update read/write surface tests so only GET and DELETE are exposed in `src/test/java/com/jobtrace/identityaccess/AccountSessionReadOnlySurfaceTest.java`

## Phase 4: Operational and quality evidence

- [x] T011 [P] [US1] Add storage-failure, safe-diagnostics, metric-label, and latency-budget coverage in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationFailureAndMetricsTest.java`
- [x] T012 [P] [US1] Add representative performance coverage in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationPerformanceTest.java`
- [x] T013 [P] [US1] Add OpenAPI contract verification in `src/test/java/com/jobtrace/identityaccess/AccountSessionRevocationOpenApiTest.java`
- [x] T014 [P] Document the migration slice in `docs/migration-slices/session-revocation.md` and update `docs/migration.md`
- [x] T015 Run the focused session suite and full `./mvnw verify`, then record evidence in `specs/012-session-revocation/quickstart.md`

## Dependencies and execution order

- T001-T002 establish the use-case boundary before T004 and T006.
- T004 enables integration, concurrency, and performance checks.
- T006-T007 enable HTTP contract and authorization checks.
- T015 follows all implementation, tests, and documentation.
