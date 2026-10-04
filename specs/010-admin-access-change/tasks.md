# Tasks: Administrator Access Changes

**Input**: Design documents from `/specs/010-admin-access-change/`

**Tests**: TDD and executable integration evidence are required by the specification and constitution.

## Phase 1: Setup and Contract Evidence

- [X] T001 Record legacy command, response, conflict, authorization, idempotency, audit, and session-revocation behavior in `src/test/resources/contracts/admin/README.md`
- [X] T002 [P] Add success, replay, conflict, validation, and safe-problem fixtures in `src/test/resources/contracts/admin/access-change.legacy.json`
- [X] T003 [P] Add a migration-head-compatible command schema and function in `src/test/resources/postgres/admin-access-change.sql`
- [X] T004 [P] Add PATCH contract assertions in `src/test/java/com/jobtrace/admin/AdminAccessOpenApiTest.java`

## Phase 2: Foundational

- [X] T005 Add command parsing and boundary tests in `src/test/java/com/jobtrace/admin/AdminAccessCommandTest.java`
- [X] T006 [P] Add exact PATCH path, method/path binding, role, expiry, replay, and excluded-route bridge tests in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`
- [X] T007 Define immutable command, action, state, result, response, and failure types in `src/main/java/com/jobtrace/admin/domain/`
- [X] T008 Define the access command port in `src/main/java/com/jobtrace/admin/application/AdminAccessCommand.java`
- [X] T009 Extend the bridge allowlist only for the exact administrator user PATCH shape in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`

## Phase 3: User Story 1 - Change Account Access Safely (P1)

**Independent Test**: All four actions produce one valid transition, one version increment, one audit event, and disable-only session revocation.

- [X] T010 [P] [US1] Add orchestration tests in `src/test/java/com/jobtrace/admin/ChangeManagedUserAccessTest.java`
- [X] T011 [P] [US1] Add PostgreSQL action, version, audit snapshot, and session tests in `src/test/java/com/jobtrace/admin/AdminAccessCommandIntegrationTest.java`
- [X] T012 [US1] Implement JSON-returning database command invocation in `src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminAccessCommand.java`
- [X] T013 [US1] Implement access-change orchestration and refreshed user response in `src/main/java/com/jobtrace/admin/application/ChangeManagedUserAccess.java`
- [X] T014 [US1] Add strict JSON request mapping in `src/main/java/com/jobtrace/admin/web/AdminAccessRequest.java`
- [X] T015 [US1] Expose `PATCH /api/admin/users/{id}` in `src/main/java/com/jobtrace/admin/web/AdminReadController.java`

## Phase 4: User Story 2 - Retry Without Duplicate Effects (P2)

**Independent Test**: Sequential and concurrent identical commands converge on one transition and audit event; changed content with the same request ID conflicts.

- [X] T016 [P] [US2] Add sequential replay and changed-payload integration tests in `src/test/java/com/jobtrace/admin/AdminAccessCommandIntegrationTest.java`
- [X] T017 [P] [US2] Add concurrent identical-command serialization tests in `src/test/java/com/jobtrace/admin/AdminAccessCommandIntegrationTest.java`
- [X] T018 [US2] Preserve replay metadata and original durable result mapping in `src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminAccessCommand.java`
- [X] T019 [US2] Return replayed success without a second user mutation in `src/main/java/com/jobtrace/admin/application/ChangeManagedUserAccess.java`

## Phase 5: User Story 3 - Understand Rejected Changes (P3)

**Independent Test**: Every policy and identity rejection returns the established safe status/code/latest state while changing no unauthorized account state.

- [X] T020 [P] [US3] Add stale-version, invalid-state, self-confirmation, last-admin, missing-user, and actor-state tests in `src/test/java/com/jobtrace/admin/AdminAccessCommandIntegrationTest.java`
- [X] T021 [P] [US3] Add administrator-only HTTP success/problem/no-store/request-ID tests in `src/test/java/com/jobtrace/admin/AdminAccessWebTest.java`
- [X] T022 [US3] Map durable failure codes to stable domain conflicts in `src/main/java/com/jobtrace/admin/application/ChangeManagedUserAccess.java`
- [X] T023 [US3] Add safe 400/404/409/503 problem responses with optional audit/latest-state fields in `src/main/java/com/jobtrace/admin/web/AdminReadController.java` and the shared storage handler

## Phase 6: Cross-Cutting Validation

- [X] T024 Add bounded access-change metrics in `src/main/java/com/jobtrace/admin/web/AdminReadMetrics.java`
- [X] T025 [P] Assert the only new mutation surface is the exact access command and no policy SQL is duplicated in `src/test/java/com/jobtrace/admin/AdminAccessSurfaceTest.java`
- [X] T026 [P] Add administration write dependency rules in `src/test/java/com/jobtrace/ArchitectureTest.java`
- [X] T027 Add reproducible 1-second-p95 command/replay checks in `src/test/java/com/jobtrace/admin/AdminAccessCommandIntegrationTest.java`
- [X] T028 Add the PATCH operation and schemas to `specs/001-java-migration/contracts/openapi.yaml`
- [X] T029 Document the access-change slice in `docs/migration-slices/admin-access-change.md` and `docs/migration.md`
- [X] T030 Run focused tests and `./mvnw verify`, confirm coverage/style/performance, and record evidence in `specs/010-admin-access-change/validation-report.md`

## Dependencies and Execution Order

- Setup precedes foundation; foundation blocks all stories.
- US1 establishes the durable command path. US2 verifies retry semantics over it. US3 completes safe rejection behavior.
- Cross-cutting validation follows all three stories.
- No task changes production schema, frontend, deployment, routing, or CI/CD.

## Implementation Strategy

Implement tests before behavior, reuse the established atomic database policy, complete the successful command first, then idempotency and safe conflicts, and finish with whole-repository verification.
