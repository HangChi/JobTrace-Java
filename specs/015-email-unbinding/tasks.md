# Tasks: Account Email Unbinding

**Input**: Design documents from `/specs/015-email-unbinding/`

## Phase 1: Foundation

- [x] T001 Verify the 014 password credential ports, existing rate-limit function, and email-code schema in `specs/015-email-unbinding/research.md`
- [x] T002 [US1] Add result and safe error types in `src/main/java/com/jobtrace/identityaccess/domain/`
- [x] T003 [US1] Add `AccountEmailUnbindingStore` and `UnbindAccountEmail` in `src/main/java/com/jobtrace/identityaccess/application/`; current password must be a non-empty string of at most 128 characters

## Phase 2: User Story 1 - Remove my recovery email

- [x] T004 [P] [US1] Test current password, invalid state, and successful use case in `src/test/java/com/jobtrace/identityaccess/AccountEmailUnbindingUseCaseTest.java`
- [x] T005 [US1] Implement shared email-change rate limit and atomic owner-scoped SQL in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountEmailUnbindingStore.java`
- [x] T006 [P] [US1] Test owner isolation, pending-code consumption, rate limit, and rollback with `src/test/resources/postgres/account-email-unbinding.sql` and `src/test/java/com/jobtrace/identityaccess/AccountEmailUnbindingDatabaseTest.java`
- [x] T007 [US1] Add exact DELETE controller, bounded request parser, errors, no-store filter, and metrics in `src/main/java/com/jobtrace/identityaccess/web/`
- [x] T008 [US1] Protect the DELETE route in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T009 [P] [US1] Test response and error contract in `src/test/java/com/jobtrace/identityaccess/AccountEmailUnbindingContractTest.java`
- [x] T010 [P] [US1] Test signed DELETE assertion, replay, and mismatch rejection in `src/test/java/com/jobtrace/identityaccess/AccountEmailUnbindingSecurityTest.java`
- [x] T011 [P] [US1] Test OpenAPI and representative p95 in `src/test/java/com/jobtrace/identityaccess/AccountEmailUnbindingQualityTest.java`

## Phase 3: Evidence and documentation

- [x] T012 Document the migration boundary in `docs/migration-slices/email-unbinding.md` and `docs/migration.md`
- [x] T013 Run focused tests and `./mvnw verify`, then record evidence in `specs/015-email-unbinding/quickstart.md`

## Dependencies and execution order

- T001-T003 establish the foundation; T004 precedes implementation of the use case.
- T005-T006 establish transactional storage before HTTP tests.
- T007-T008 enable T009-T011.
- T012-T013 finish the independently verifiable slice.

## Parallel opportunities

- Independent test files T004, T006, T009, T010, and T011 can be authored separately after their prerequisites are present. This stage is implemented by one agent, sequentially.

## Independent test criteria

- US1 is complete when one verified owner can clear only their recovery email and pending codes after current-password verification, and all failures leave durable state unchanged.

## MVP scope

- User Story 1 is the whole 015 slice.
