# Tasks: Account Email Binding

**Input**: Design documents from `/specs/016-email-binding/`

## Phase 1: Compatibility foundation

- [x] T001 Verify legacy HMAC input, database function, email uniqueness, and response parity in `specs/016-email-binding/research.md`
- [x] T002 [US1] Add shared email-change rate-limit port and binding result/error types in `src/main/java/com/jobtrace/identityaccess/application/` and `domain/`
- [x] T003 [P] [US1] Add HMAC compatibility vectors in `src/test/java/com/jobtrace/identityaccess/AccountEmailBindingHasherTest.java`
- [x] T004 [US1] Implement legacy-compatible HMAC in `src/main/java/com/jobtrace/identityaccess/infrastructure/BetterAuthEmailCodeHasher.java` and secret configuration in `src/main/resources/application.yaml`

## Phase 2: User Story 1 - Bind a verified recovery email

- [x] T005 [P] [US1] Test validation, password and state failures in `src/test/java/com/jobtrace/identityaccess/AccountEmailBindingUseCaseTest.java`
- [x] T006 [US1] Implement `BindAccountEmail` and persistence port in `src/main/java/com/jobtrace/identityaccess/application/`; email at most 254 characters, code exactly six digits after trim, password 1–128 characters
- [x] T007 [US1] Implement transaction, code function call, durable wrong attempts, and unique conflict mapping in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountEmailBindingStore.java`
- [x] T008 [P] [US1] Add database fixtures and integration tests for ownership, replay, attempts, conflict, and rollback in `src/test/resources/postgres/account-email-binding.sql` and `src/test/java/com/jobtrace/identityaccess/AccountEmailBindingDatabaseTest.java`
- [x] T009 [US1] Add PATCH controller, request parser, no-store handling, and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T010 [US1] Extend exact bridge protection in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T011 [P] [US1] Test HTTP parity and signed-assertion failures in `src/test/java/com/jobtrace/identityaccess/AccountEmailBindingContractTest.java` and `AccountEmailBindingSecurityTest.java`
- [x] T012 [P] [US1] Test OpenAPI and representative p95 in `src/test/java/com/jobtrace/identityaccess/AccountEmailBindingQualityTest.java`

## Phase 3: Evidence and documentation

- [x] T013 Document routing and secret compatibility in `docs/migration-slices/email-binding.md` and `docs/migration.md`
- [x] T014 Run focused and full `./mvnw verify`, then record results in `specs/016-email-binding/quickstart.md`

## Dependencies and execution order

- T001-T004 establish compatibility before binding.
- T005 precedes T006; T006-T008 establish the command before HTTP work.
- T009-T010 enable T011-T012; T013-T014 finish the slice.

## Parallel opportunities

- Independent test files T003, T005, T008, T011, and T012 can be authored separately after their prerequisites. This stage is implemented by one agent, sequentially.

## Independent test criteria

- US1 completes when a verified owner can bind a lowercased email using an already-issued compatible code and current password, while all rejected requests leave profile state unchanged.

## MVP scope

- User Story 1 is the whole 016 slice.
