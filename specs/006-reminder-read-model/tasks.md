# Tasks: Private Reminder Read Model Migration

**Input**: Design documents from `specs/006-reminder-read-model/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`

**Tests**: Required by the specification and constitution. Write each behavior's tests before its implementation and confirm they fail for the intended missing behavior. Use synthetic data only.

**Organization**: Tasks are grouped by independently testable user story. `[P]` means file-level independence, not a requirement to use agents or multiple maintainers.

## Phase 1: Setup — Current Reminder Contract

**Purpose**: Establish a reliable current-service oracle without production access or Java runtime changes.

- [X] T001 Record current read source paths, the feature 008 OpenAPI `suggestions`/`history` drift, timestamp and email normalization, and synthetic fixture rules in `src/test/resources/contracts/reminders/README.md`.
- [X] T002 [P] Add synthetic active, due-boundary, empty, completed, cancelled, verified/unverified-email, and custom/default-preference scenarios to `src/test/resources/contracts/reminders/read-fixtures.legacy.json`.
- [X] T003 [P] Add a minimal test-only PostgreSQL schema for users, owned applications, scheduled reminders, and email notification attempts, including current constraints and indexes, in `src/test/resources/postgres/reminders-read-model.sql`; add no production migration.
- [X] T004 [P] Add parser/path/schema assertions for both planned GET operations in `src/test/java/com/jobtrace/reminders/ReminderReadOpenApiTest.java` against `specs/006-reminder-read-model/contracts/openapi.yaml`.

**Checkpoint**: Current read contracts and isolated storage fixtures are available; no application behavior has changed.

---

## Phase 2: Foundational — Trusted Owner and Shared Read Types

**Purpose**: Establish owner-only entry and reusable immutable projections before any reminder route is exposed.

**⚠️ CRITICAL**: Complete this phase before user-story implementation.

- [X] T005 [P] Add unit tests for status selection normalization, fixed-instant due classification, same-time ordering assumptions, and empty response groups in `src/test/java/com/jobtrace/reminders/ReminderReadRulesTest.java`.
- [X] T006 [P] Add bridge regression tests for exact reminder overview/settings paths, method/path-bound assertions, and excluded mutation/internal delivery paths in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`.
- [X] T007 Define immutable status, attempt-state, reminder, summary, email-availability, selection, and preference types in `src/main/java/com/jobtrace/reminders/domain/ReminderCatalog.java`, `Reminder.java`, `ReminderSummary.java`, `ReminderEmailAvailability.java`, `ReminderSelection.java`, and `ReminderPreferences.java` to satisfy T005; preserve source nullability and 200-item cap.
- [X] T008 Define owner-scoped overview, verified-email, and preferences operations in `src/main/java/com/jobtrace/reminders/application/ReminderReadQuery.java`; never accept a caller-selected owner.
- [X] T009 Extend `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java` for the two exact private GET paths to satisfy T006; reuse `BridgePrincipalOwner` and keep bridge enablement opt-in.

**Checkpoint**: Both stories can use the same trusted owner and query contract without transferring write or delivery ownership.

---

## Phase 3: User Story 1 — See My Active Reminders (Priority: P1) 🎯 MVP

**Goal**: Return the private active overview with due/upcoming groups, current email state and verified-address availability.

**Independent Test**: Seed two owners and active reminders around one fixed instant; compare complete JSON, order, 200-item cap, and email availability with synthetic current-service fixtures. No cross-owner data or writes occur.

### Tests for User Story 1

- [X] T010 [P] [US1] Add structural JSON parity tests for active, empty, due-boundary, and verified/unverified-email fixtures in `src/test/java/com/jobtrace/reminders/ReminderActiveContractTest.java`.
- [X] T011 [P] [US1] Add use-case tests for required trusted owner, active default, immutable groups, and query failure propagation in `src/test/java/com/jobtrace/reminders/GetReminderSummaryTest.java`.
- [X] T012 [P] [US1] Add PostgreSQL tests for owner-bound reminder/application/attempt/email joins, current-schedule attempt only, and zero foreign data disclosure in `src/test/java/com/jobtrace/reminders/ReminderActiveOwnerIsolationIntegrationTest.java`.
- [X] T013 [P] [US1] Add PostgreSQL tests for due-at-exact-instant classification, same-time UUID order, 200-item cap, stored status unchanged, and bounded query count in `src/test/java/com/jobtrace/reminders/ReminderActiveQueryIntegrationTest.java`.
- [X] T014 [P] [US1] Add HTTP tests for default/unknown status, no-store success, ordinary-principal rejection, and safe invalid identity in `src/test/java/com/jobtrace/reminders/ReminderSummaryControllerTest.java`.

### Implementation for User Story 1

- [X] T015 [US1] Implement `GetReminderSummary` with one injected clock instant and owner validation in `src/main/java/com/jobtrace/reminders/application/GetReminderSummary.java` to satisfy T011.
- [X] T016 [US1] Implement parameterized active-list and verified-email reads with owner-consistent joins, fixed-instant grouping, current-schedule attempt, and 200-item cap in `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderReadQuery.java` to satisfy T012–T013.
- [X] T017 [US1] Expose only `GET /api/reminders` with the trusted owner, established selection fallback, `private, no-store`, and safe errors in `src/main/java/com/jobtrace/reminders/web/ReminderReadController.java` to satisfy T010 and T014.
- [X] T018 [US1] Add bounded operation/outcome and latency metrics without owner, title, address, ID, or selection labels in `src/main/java/com/jobtrace/reminders/web/ReminderReadMetrics.java`.
- [X] T019 [US1] Run T010–T014 and record independent MVP parity, isolation, and read-only evidence in `specs/006-reminder-read-model/validation-report.md`.

**Checkpoint**: The active overview works independently; completed history and settings reads are not needed for this MVP.

---

## Phase 4: User Story 2 — Review Reminder History (Priority: P2)

**Goal**: Return completed or cancelled reminders in history without mixing them into active groups.

**Independent Test**: With two owners and all lifecycle states, compare each selected history JSON with the current service; active groups stay empty and no foreign reminder is revealed.

### Tests for User Story 2

- [X] T020 [P] [US2] Add completed/cancelled JSON parity, nullable timestamp, empty-history, and unknown-selection tests in `src/test/java/com/jobtrace/reminders/ReminderHistoryContractTest.java`.
- [X] T021 [P] [US2] Add PostgreSQL tests for owner-bound completed/cancelled selection, ordering, cap, and mixed-state exclusion in `src/test/java/com/jobtrace/reminders/ReminderHistoryQueryIntegrationTest.java`.
- [X] T022 [P] [US2] Add HTTP tests for both history selections and the established unknown-status active fallback in `src/test/java/com/jobtrace/reminders/ReminderHistoryControllerTest.java`.

### Implementation for User Story 2

- [X] T023 [US2] Extend `src/main/java/com/jobtrace/reminders/application/GetReminderSummary.java` and `src/main/java/com/jobtrace/reminders/domain/ReminderSelection.java` to forward completed/cancelled modes while returning empty active groups.
- [X] T024 [US2] Extend `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderReadQuery.java` with owner-bound historical status filters and source-compatible ordering to satisfy T021.
- [X] T025 [US2] Complete history response selection in `src/main/java/com/jobtrace/reminders/web/ReminderReadController.java` and run T020–T022 as an independent history checkpoint.

**Checkpoint**: Active and history overview selections are independently verified; settings read remains separate.

---

## Phase 5: User Story 3 — Read My Reminder Defaults (Priority: P3)

**Goal**: Return the owner's persisted reminder display and creation defaults without changing them.

**Independent Test**: Compare custom and default settings for two owners with current-service fixtures; no settings or verified address from another owner appears.

### Tests for User Story 3

- [X] T026 [P] [US3] Add custom/default preference JSON parity tests in `src/test/java/com/jobtrace/reminders/ReminderPreferencesContractTest.java`.
- [X] T027 [P] [US3] Add PostgreSQL tests for owner isolation, all five values, defaults, and no profile mutation in `src/test/java/com/jobtrace/reminders/ReminderPreferencesQueryIntegrationTest.java`.
- [X] T028 [P] [US3] Add no-store HTTP and trusted-owner denial tests for the settings read in `src/test/java/com/jobtrace/reminders/ReminderPreferencesControllerTest.java`.

### Implementation for User Story 3

- [X] T029 [US3] Add `GetReminderPreferences` use case in `src/main/java/com/jobtrace/reminders/application/GetReminderPreferences.java`, reusing the immutable projection from the foundation.
- [X] T030 [US3] Implement owner-bound preference reads and exact established defaults in `src/main/java/com/jobtrace/reminders/infrastructure/PostgresReminderReadQuery.java` to satisfy T027.
- [X] T031 [US3] Expose only `GET /api/reminder-settings` with trusted owner, no-store, safe errors, and bounded metrics in `src/main/java/com/jobtrace/reminders/web/ReminderReadController.java` to satisfy T026 and T028.
- [X] T032 [US3] Run T026–T028 and record the independent settings result in `specs/006-reminder-read-model/validation-report.md`.

**Checkpoint**: All three read stories work independently; the existing service still owns every write and delivery operation.

---

## Phase 6: Polish and Release Gates

**Purpose**: Prove security, schema safety, performance, contract parity, and review readiness without deployment.

- [X] T033 [P] Add only the two protected GET operations and response schemas to `specs/001-java-migration/contracts/openapi.yaml`; preserve existing mutation and public-route exclusions.
- [X] T034 [P] Add absent, forged, expired, path-mismatched, replayed, and ordinary-principal denial tests for both reads in `src/test/java/com/jobtrace/reminders/ReminderReadSecurityTest.java`.
- [X] T035 [P] Add safe storage-outage, request-ID, log-redaction, and no-private-label metrics tests for both reads in `src/test/java/com/jobtrace/reminders/ReminderReadFailureAndMetricsTest.java`.
- [X] T036 [P] Add fixed-clock representative-load tests with warm-up, at least 40 samples, both p95 ≤ 500 ms, and bounded query counts in `src/test/java/com/jobtrace/reminders/ReminderReadPerformanceTest.java`.
- [X] T037 [P] Assert no production migration, Flyway activation, non-GET reminder handler, settings mutation, internal delivery, email transport, or frontend route change in `src/test/java/com/jobtrace/reminders/ReminderReadOnlySurfaceTest.java` and `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java`.
- [X] T038 [P] Add architecture rules preserving the reminder read context boundary and avoiding application/analytics package cycles in `src/test/java/com/jobtrace/ArchitectureTest.java`.
- [X] T039 [P] Document the current-service writer/delivery/session/schema owner and no-deployment decision in `docs/migration-slices/reminder-read-model.md` and `docs/migration.md`.
- [X] T040 Run `./mvnw verify` with Docker and record fixture parity, two-owner PostgreSQL isolation, OpenAPI, architecture, ≥ 80% changed-code line/branch coverage, Checkstyle, p95, query counts, and Java-only artifact evidence in `specs/006-reminder-read-model/validation-report.md`.
- [X] T041 Review the result against `spec.md` and all four constitution principles, record Solo-Maintainer Mode self-review in `specs/006-reminder-read-model/validation-report.md`, open a PR to `main`, pass required CI/security checks, and record its URL and unscheduled deployment status.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) provides fixtures, schema, and contract parser.
- Foundational (Phase 2) follows setup and blocks all three user stories.
- US1 (Phase 3) is the MVP. US2 reuses its summary route and storage adapter, so follows US1 in the single-maintainer workflow.
- US3 can be independently specified and tested after the shared foundation, but follows US2 to avoid overlapping controller and adapter edits.
- Polish (Phase 6) follows all selected stories and blocks PR merge, not production deployment.

### Within Each Story

1. Write fixtures and tests first; observe the intended failure before adding production behavior.
2. Add immutable projections and use cases before storage and HTTP composition.
3. Verify each story's independent contract and owner-isolation checkpoint before proceeding.
4. Keep the existing service as sole writer and scheduler throughout.

### Parallel Opportunities

- T002–T004 target separate fixture, schema, and parser files.
- T005 and T006 are independent tests; T007 follows T005, T009 follows T006.
- T010–T014, T020–T022, and T026–T028 target separate story test files; `[P]` denotes file independence only.
- T033–T039 mostly target separate files, except T037/T038 may overlap existing cross-cutting test changes and should be serialized where needed.
- The user has one human maintainer and requested no subagents; no task marker implies delegation.

## Implementation Strategy

1. Complete Setup and Foundational, then finish US1 as the read-only MVP.
2. Add US2 history selection and US3 settings read without changing reminder writes or delivery.
3. Finish cross-cutting gates and PR review. Do not merge until all required checks pass.
4. Deployment, routing, canary, rollback rehearsal, reminder writer transfer, and email delivery migration require separate authorization and work.
