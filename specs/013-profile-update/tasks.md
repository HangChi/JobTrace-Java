# Tasks: Account Profile Update

**Input**: Design documents from `/specs/013-profile-update/`

## Phase 1: Foundational command boundary

- [x] T001 [US1] Add profile command/result/not-found domain types in `src/main/java/com/jobtrace/identityaccess/domain/`
- [x] T002 [US1] Add the owner-scoped command port and validated use case in `src/main/java/com/jobtrace/identityaccess/application/`
- [x] T003 [P] [US1] Add unit coverage for normalization, image states, validation, success, and missing owners in `src/test/java/com/jobtrace/identityaccess/UpdateAccountProfileTest.java`

## Phase 2: Durable owner-scoped update

- [x] T004 [US1] Implement one parameterized update-returning adapter in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountProfileCommand.java`
- [x] T005 [US1] Add reusable profile database fixtures and owner-isolation integration coverage in `src/test/java/com/jobtrace/identityaccess/AccountProfileDatabaseTest.java` and `AccountProfileUpdateIntegrationTest.java`

## Phase 3: HTTP and security contract

- [x] T006 [US1] Add request parsing, PATCH controller, no-store errors, and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T007 [US1] Protect only exact PATCH `/api/profile` in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T008 [P] [US1] Add response, image-state, validation, unknown-field, and surface tests in `src/test/java/com/jobtrace/identityaccess/AccountProfileContractTest.java`
- [x] T009 [P] [US1] Add PATCH-bound assertion and fail-closed tests in `src/test/java/com/jobtrace/identityaccess/AccountProfileSecurityTest.java`

## Phase 4: Quality evidence

- [x] T010 [P] [US1] Add storage-failure, safe-diagnostics, metric-label, and latency-budget tests in `src/test/java/com/jobtrace/identityaccess/AccountProfileFailureAndMetricsTest.java`
- [x] T011 [P] [US1] Add representative one-operation p95 coverage in `src/test/java/com/jobtrace/identityaccess/AccountProfilePerformanceTest.java`
- [x] T012 [P] [US1] Add OpenAPI verification in `src/test/java/com/jobtrace/identityaccess/AccountProfileOpenApiTest.java`
- [x] T013 [P] Document the slice in `docs/migration-slices/profile-update.md` and `docs/migration.md`
- [x] T014 Run focused tests and `./mvnw verify`, then record evidence in `specs/013-profile-update/quickstart.md`

## Dependencies and execution order

- T001-T002 establish the command boundary before T004 and T006.
- T004 enables database, failure, and performance checks.
- T006-T007 enable HTTP and authorization checks.
- T014 follows all code, tests, and documentation.
