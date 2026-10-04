# Tasks: Verified Account Registration

**Input**: Design documents from `specs/019-registration/`

## Phase 1: Compatibility foundation

- [x] T001 Confirm old registration route, schema, code-attempt function, credential format, challenge and rate-limit behavior in `specs/019-registration/research.md`
- [x] T002 Add field validation and registration failure types in `src/main/java/com/jobtrace/identityaccess/application/` and `domain/`
- [x] T003 Extend the shared PostgreSQL limiter for registration in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresEmailCodeRateLimiter.java`

## Phase 2: User Story 1 - Create a verified account (Priority: P1)

**Goal**: An anonymous user redeems an issued code to create one sign-in-compatible account without a session.

**Independent Test**: Issue a code, register, verify the credential and recovery email, then retry the used code and inspect no additional account.

- [x] T004 [P] [US1] Add validation and use-case tests in `src/test/java/com/jobtrace/identityaccess/RegistrationUseCaseTest.java`
- [x] T005 [P] [US1] Add database tests for attempts, conflicts, atomic creation and password compatibility in `src/test/java/com/jobtrace/identityaccess/RegistrationDatabaseTest.java`
- [x] T006 [P] [US1] Add HTTP contract and security tests in `src/test/java/com/jobtrace/identityaccess/RegistrationContractTest.java` and `RegistrationSecurityTest.java`
- [x] T007 [US1] Implement registration orchestration in `src/main/java/com/jobtrace/identityaccess/application/RegisterAccount.java`
- [x] T008 [US1] Implement code verification and user/credential transaction in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresRegistrationStore.java`
- [x] T009 [US1] Implement optional challenge verification in `src/main/java/com/jobtrace/identityaccess/infrastructure/HttpRegistrationChallengeVerifier.java`
- [x] T010 [US1] Implement exact anonymous route, origin guard, no-store handling and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/` and `src/main/java/com/jobtrace/shared/security/SecurityConfig.java`
- [x] T011 [P] [US1] Add representative p95 and contract quality tests in `src/test/java/com/jobtrace/identityaccess/RegistrationQualityTest.java`

## Phase 3: Evidence and documentation

- [x] T012 Document registration routing, configuration and rollback in `docs/migration-slices/registration.md` and `docs/migration.md`
- [x] T013 Run focused and full local verification; record test, coverage and latency evidence in `specs/019-registration/quickstart.md`

## Dependencies and execution order

T001-T003 establish compatibility. T004-T006 define failing behavior before T007-T010. T011 validates quality; T012-T013 close the slice. `[P]` denotes independent files only; execution remains with one agent, without subagents.

## MVP scope

User Story 1 is the whole 019 slice. The old service retains login, session issuance and production routing.
