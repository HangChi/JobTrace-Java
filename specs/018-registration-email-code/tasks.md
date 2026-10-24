# Tasks: Request Registration Email Code

**Input**: Design documents from `specs/018-registration-email-code/`

## Phase 1: Compatibility foundation

- [x] T001 Confirm legacy route, anonymous origin check, registration HMAC, and null-owner code semantics in `specs/018-registration-email-code/research.md`
- [x] T002 Add registration-purpose HMAC port and compatibility tests in `src/main/java/com/jobtrace/identityaccess/application/` and `src/test/java/com/jobtrace/identityaccess/RegistrationCodeHasherTest.java`
- [x] T003 Extract shared code limits from stage 017 into `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresEmailCodeRateLimiter.java`

## Phase 2: User Story 1 - Request a registration code

**Goal**: An anonymous prospective user receives a registration-purpose code without affecting binding codes.

**Independent Test**: Issue an anonymous request, inspect delivery and stored HMAC, replace the code after cooldown, and confirm binding-purpose rows are unchanged.

- [x] T004 [P] [US1] Add use-case validation tests in `src/test/java/com/jobtrace/identityaccess/RegistrationCodeUseCaseTest.java`
- [x] T005 [US1] Implement validated request orchestration in `src/main/java/com/jobtrace/identityaccess/application/RequestRegistrationEmailCode.java`
- [x] T006 [P] [US1] Add database tests for conflict, limits, replacement, and purpose isolation in `src/test/java/com/jobtrace/identityaccess/RegistrationCodeDatabaseTest.java`
- [x] T007 [US1] Implement registration-purpose PostgreSQL insertion in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresRegistrationEmailCodeStore.java`
- [x] T008 [P] [US1] Add anonymous origin/security and HTTP contract tests in `src/test/java/com/jobtrace/identityaccess/RegistrationCodeContractTest.java` and `RegistrationCodeSecurityTest.java`
- [x] T009 [US1] Add exact public route, same-origin guard, no-store responses, and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/` and `src/main/java/com/jobtrace/shared/security/SecurityConfig.java`
- [x] T010 [P] [US1] Validate OpenAPI and representative p95 in `src/test/java/com/jobtrace/identityaccess/RegistrationCodeQualityTest.java`

## Phase 3: Evidence and documentation

- [x] T011 Document routing, configuration and rollback in `docs/migration-slices/registration-email-code.md` and `docs/migration.md`
- [x] T012 Run focused and whole-repository local verification and record results in `specs/018-registration-email-code/quickstart.md`

## Dependencies and execution order

T001-T003 establish compatibility and shared infrastructure. T004-T007 complete issuance. T008-T010 verify public routing and quality. T011-T012 close the slice. `[P]` means independent files; this stage is executed sequentially by one agent without subagents.

## MVP scope

User Story 1 is the entire 018 slice. Account creation and code redemption remain with the existing service.
