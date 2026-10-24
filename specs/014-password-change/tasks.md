# Tasks: Account Password Change

**Input**: Design documents from `/specs/014-password-change/`

## Phase 1: Compatible credential foundation

- [x] T001 [US1] Add the maintained scrypt dependency rationale and version in `pom.xml` and `specs/014-password-change/research.md`
- [x] T002 [US1] Add password-change command, credential snapshot, result, and safe failure domain types in `src/main/java/com/jobtrace/identityaccess/domain/`
- [x] T003 [US1] Add credential-store and password-hasher ports plus the validated use case in `src/main/java/com/jobtrace/identityaccess/application/`
- [x] T004 [P] [US1] Add validation, wrong-password, success, and safe-state unit coverage in `src/test/java/com/jobtrace/identityaccess/ChangeAccountPasswordTest.java`
- [x] T005 [P] [US1] Add fixed Better Auth compatibility-vector and generated-hash coverage in `src/test/java/com/jobtrace/identityaccess/BetterAuthScryptPasswordHasherTest.java`

## Phase 2: Atomic owner-scoped persistence

- [x] T006 [US1] Implement Better Auth-compatible NFKC scrypt hashing in `src/main/java/com/jobtrace/identityaccess/infrastructure/BetterAuthScryptPasswordHasher.java`
- [x] T007 [US1] Implement credential loading and transactional conditional replacement/session revocation in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountPasswordStore.java`
- [x] T008 [US1] Add reusable credential/session database fixtures and atomic owner-isolation coverage in `src/test/resources/postgres/account-password-change.sql` and `src/test/java/com/jobtrace/identityaccess/AccountPasswordDatabaseTest.java`

## Phase 3: HTTP and security contract

- [x] T009 [US1] Add request parsing, exact POST controller, stable errors, no-store handling, and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T010 [US1] Protect only exact POST `/api/profile/password` in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T011 [P] [US1] Add response, validation, unknown-field, current-session, and method-surface tests in `src/test/java/com/jobtrace/identityaccess/AccountPasswordContractTest.java`
- [x] T012 [P] [US1] Add POST-bound assertion and fail-closed tests in `src/test/java/com/jobtrace/identityaccess/AccountPasswordSecurityTest.java`

## Phase 4: Quality evidence

- [x] T013 [P] [US1] Add rollback, dependency-failure, safe-diagnostics, and bounded-metric tests in `src/test/java/com/jobtrace/identityaccess/AccountPasswordFailureAndMetricsTest.java`
- [x] T014 [P] [US1] Add representative p95 coverage with the production hash parameters in `src/test/java/com/jobtrace/identityaccess/AccountPasswordPerformanceTest.java`
- [x] T015 [P] [US1] Add OpenAPI verification in `src/test/java/com/jobtrace/identityaccess/AccountPasswordOpenApiTest.java`
- [x] T016 [P] Document the slice in `docs/migration-slices/password-change.md` and `docs/migration.md`
- [x] T017 Run focused tests and `./mvnw verify`, then record evidence in `specs/014-password-change/quickstart.md`

## Dependencies and execution order

- T001-T003 establish the cryptographic and command boundaries before T006-T009.
- T004-T005 are authored before the corresponding production implementations.
- T006-T008 enable transactional integration, rollback, and performance checks.
- T009-T010 enable HTTP and authorization checks.
- T017 follows all code, tests, and documentation.

## Parallel opportunities

- T004 and T005 target independent test concerns after the ports are defined.
- T011, T012, T013, T014, T015, and T016 use separate files after the production boundary exists.

## Independent test criteria

- US1 is complete when a verified owner can replace a compatible credential, retain exactly the current owner session, revoke every other owner session atomically, receive stable contract responses, and cannot affect foreign state.

## MVP scope

- User Story 1 is the complete independently deployable slice; no additional story is required.
