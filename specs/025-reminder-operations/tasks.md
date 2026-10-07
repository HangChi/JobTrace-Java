# Tasks: Reminder Operations and Delivery

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Phase 1: Setup

- [x] T001 Record legacy routes and persistence invariants in `specs/025-reminder-operations/research.md` and `contracts/openapi.yaml`.

## Phase 2: Foundation

- [x] T002 Define lifecycle, retry, delivery and rollback boundaries in `specs/025-reminder-operations/data-model.md` and `plan.md`.

## Phase 3: User Story 1 — Manage reminders (P1)

**Independent Test**: Owned create/edit/cancel succeed; foreign links and stale versions leave rows unchanged.

- [x] T003 [US1] Add request and PostgreSQL/HTTP tests in `src/test/java/com/jobtrace/reminders/ReminderCommandIntegrationTest.java`.
- [x] T004 [US1] Implement validated create/edit/version payloads in `src/main/java/com/jobtrace/reminders/web/ReminderCommandRequest.java`.
- [x] T005 [US1] Implement owner-scoped transactional create, edit and cancellation in `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderCommandStore.java`.
- [x] T006 [US1] Expose create/edit/cancel routes and compatibility errors in `src/main/java/com/jobtrace/reminders/web/ReminderCommandController.java`.

## Phase 4: User Story 2 — Lifecycle (P2)

**Independent Test**: Complete, snooze and reopen preserve state, timestamps and version conflict behavior.

- [x] T007 [US2] Add lifecycle tests in `src/test/java/com/jobtrace/reminders/ReminderLifecycleIntegrationTest.java`.
- [x] T008 [US2] Implement owner-scoped complete/snooze/reopen operations in `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderCommandStore.java` and expose routes in `web/ReminderCommandController.java`.

## Phase 5: User Story 3 — Preferences (P3)

**Independent Test**: Valid settings persist for one owner; invalid settings and missing owner do not write.

- [x] T009 [US3] Add preference write tests in `src/test/java/com/jobtrace/reminders/ReminderPreferencesWriteTest.java`.
- [x] T010 [US3] Implement preference validation and owner-scoped PATCH in `src/main/java/com/jobtrace/reminders/web/ReminderPreferencesWriteController.java` and `infrastructure/PostgresReminderPreferencesStore.java`.

## Phase 6: User Story 4 — Retry and delivery (P4)

**Independent Test**: Failed current-schedule retry is atomic; secret-protected delivery is bounded and lease-safe with fake mail.

- [x] T011 [US4] Add retry and delivery security/persistence tests in `src/test/java/com/jobtrace/reminders/ReminderDeliveryIntegrationTest.java`.
- [x] T012 [US4] Implement atomic retry and claim/finalize persistence in `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderDeliveryStore.java`.
- [x] T013 [US4] Implement shared-webhook mail adapter and bounded delivery use case in `src/main/java/com/jobtrace/reminders/application/DeliverDueReminders.java` and `infrastructure/HttpReminderMailDelivery.java`.
- [x] T014 [US4] Expose retry and internal delivery routes in `src/main/java/com/jobtrace/reminders/web/ReminderDeliveryController.java`.

## Final Phase: Verification

- [x] T015 Run `./mvnw verify`, inspect changed-code coverage and record local p95 evidence in `specs/025-reminder-operations/quickstart.md`.
- [x] T016 Document route ownership and rollback in `docs/migration-slices/reminder-operations.md`.

## Dependencies and Strategy

T001–T002 establish the existing contract. T003–T006 deliver private commands; T007–T008 extend lifecycle; T009–T010 cover settings; T011–T014 cover retry/delivery. T015–T016 close the stage. Work is sequential and solo as requested; no subagents, frontend, CI/CD or deployment work.
