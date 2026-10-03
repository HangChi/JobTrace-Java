# Tasks: Account Session Read Model

**Input**: Design documents from `/specs/011-session-read-model/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Required by FR-013 and the constitution; write contract and behavior tests before implementation.

## Phase 1: Setup

- [X] T001 Confirm the legacy route, response shape, active-row filter, current marker, and schema in `specs/011-session-read-model/research.md`
- [X] T002 Define the isolated GET contract in `specs/011-session-read-model/contracts/openapi.yaml`

## Phase 2: Foundational

- [X] T003 [P] Add synthetic legacy-compatible session fixtures in `src/test/resources/contracts/identityaccess/session-read.legacy.json`
- [X] T004 [P] Add reusable PostgreSQL session test setup in `src/test/java/com/jobtrace/identityaccess/AccountSessionReadDatabaseTest.java`
- [X] T005 Add failing contract and domain/use-case tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionContractTest.java` and `src/test/java/com/jobtrace/identityaccess/ListAccountSessionsTest.java`

## Phase 3: User Story 1 - Review Active Login Sessions (Priority: P1) MVP

**Goal**: Return only active sessions owned by the authenticated account, preserving fields, ordering, and current-device marking.

**Independent Test**: Seed active, expired, foreign, tied, null-agent, and empty-owner rows and verify the GET response without any mutation.

- [X] T006 [P] [US1] Add owner-isolation, expiry, ordering, and read-only integration tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionQueryIntegrationTest.java`
- [X] T007 [P] [US1] Add web, header-bound validation, and cache-control tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionWebTest.java`
- [X] T008 [P] [US1] Add signed-bridge authorization and replay tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionSecurityTest.java`
- [X] T009 [P] [US1] Add OpenAPI and GET-only surface tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionOpenApiTest.java` and `src/test/java/com/jobtrace/identityaccess/AccountSessionReadOnlySurfaceTest.java`
- [X] T010 [US1] Implement the immutable response model in `src/main/java/com/jobtrace/identityaccess/domain/AccountSession.java`
- [X] T011 [US1] Implement the read port and use case in `src/main/java/com/jobtrace/identityaccess/application/AccountSessionReadQuery.java` and `src/main/java/com/jobtrace/identityaccess/application/ListAccountSessions.java`
- [X] T012 [US1] Implement the parameterized PostgreSQL adapter in `src/main/java/com/jobtrace/identityaccess/infrastructure/PostgresAccountSessionReadQuery.java`
- [X] T013 [US1] Implement the private non-cacheable GET and bounded metrics in `src/main/java/com/jobtrace/identityaccess/web/AccountSessionReadController.java` and `src/main/java/com/jobtrace/identityaccess/web/AccountSessionReadMetrics.java`
- [X] T014 [US1] Protect the exact GET path in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`

## Phase 4: Polish and Cross-Cutting Validation

- [X] T015 [P] Add dependency-failure and safe-metrics tests in `src/test/java/com/jobtrace/identityaccess/AccountSessionFailureAndMetricsTest.java`
- [X] T016 [P] Add the 500-session p95/read-count benchmark in `src/test/java/com/jobtrace/identityaccess/AccountSessionPerformanceTest.java`
- [X] T017 Document ownership, rollback, and activation prerequisites in `docs/migration-slices/session-read-model.md` and `docs/migration.md`
- [X] T018 Run focused tests and `./mvnw verify`, confirm coverage/style/performance, and record evidence in `specs/011-session-read-model/validation-report.md`

## Dependencies and Execution Order

- Setup precedes foundation; foundation blocks the user story.
- Tests precede production behavior. The domain model and port/use case precede the adapter and controller.
- Bridge routing protection follows the exact controller path.
- Cross-cutting validation follows the complete story.
- No task changes schema, session writes, frontend, deployment, routing, production traffic, or CI/CD.

## Parallel Opportunities

- Synthetic fixtures and database setup affect separate files.
- Integration, web, security, and contract/surface tests affect separate files once the shared fixture is defined.
- Failure/metrics and performance tests affect separate files after the feature works.

## Implementation Strategy

Deliver the one P1 story as the MVP: establish the contract and fixtures, write failing tests, implement the narrow read path, then verify isolation, security, read-only behavior, diagnostics, performance, and whole-repository quality gates.
