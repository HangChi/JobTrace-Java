# Tasks: Request Email Binding Code

**Input**: Design documents from `specs/017-email-binding-code/`

## Phase 1: Compatibility foundation

- [x] T001 Confirm route, code format, rate limits, and delivery contract in `specs/017-email-binding-code/research.md`
- [x] T002 Add safe error types and application ports in `src/main/java/com/jobtrace/identityaccess/application/` and `domain/`
- [x] T003 [P] [US1] Add use-case validation and generation tests in `src/test/java/com/jobtrace/identityaccess/AccountEmailCodeUseCaseTest.java`

## Phase 2: User Story 1 - Request a binding code

**Goal**: Issue and deliver a code that stage 016 can redeem.

**Independent Test**: Seed an active owner session, request a code, capture delivery, and redeem it via the existing binding use case.

- [x] T004 [US1] Implement secure six-digit generation and request orchestration in `src/main/java/com/jobtrace/identityaccess/application/RequestAccountEmailCode.java` and `infrastructure/SecureEmailCodeGenerator.java`
- [x] T005 [P] [US1] Add database integration tests for owner, conflict, rate limits, replacement, and compatibility in `src/test/java/com/jobtrace/identityaccess/AccountEmailCodeDatabaseTest.java`
- [x] T006 [US1] Implement existing SQL rate limits and code replacement in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountEmailCodeStore.java`
- [x] T007 [P] [US1] Test delivery status, payload, unavailable endpoint, and authentication in `src/test/java/com/jobtrace/identityaccess/AccountEmailCodeDeliveryTest.java`
- [x] T008 [US1] Implement bounded HTTP delivery in `src/main/java/com/jobtrace/identityaccess/infrastructure/HttpAccountEmailCodeDelivery.java` and configuration in `src/main/resources/application.yaml`
- [x] T009 [P] [US1] Test HTTP contract and exact bridge assertion in `src/test/java/com/jobtrace/identityaccess/AccountEmailCodeContractTest.java` and `AccountEmailCodeSecurityTest.java`
- [x] T010 [US1] Add POST controller, no-store responses, metrics, and bridge protection in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T011 [P] [US1] Validate OpenAPI and representative p95 in `src/test/java/com/jobtrace/identityaccess/AccountEmailCodeQualityTest.java`

## Phase 3: Evidence and documentation

- [x] T012 Document route ownership and rollback in `docs/migration-slices/email-binding-code.md` and `docs/migration.md`
- [x] T013 Run focused and full local verification and record results in `specs/017-email-binding-code/quickstart.md`

## Dependencies and execution order

T001-T003 establish compatibility. T004-T008 complete the application and storage/delivery boundary. T009-T011 verify HTTP and quality behavior. T012-T013 finish the slice. All work is performed sequentially by one agent; `[P]` identifies independent files, not subagent dispatch.

## MVP scope

User Story 1 is the complete 017 slice. Registration code requests and other mail templates remain outside scope.
