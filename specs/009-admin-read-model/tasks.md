# Tasks: Administration Read Model

**Input**: Design documents from `/specs/009-admin-read-model/`

**Tests**: TDD is required by the feature and project constitution.

## Phase 1: Setup and Contract Evidence

- [X] T001 Record exact legacy routes, defaults, ordering, fallbacks, Shanghai date semantics, clamping, partial availability, and exclusions in `src/test/resources/contracts/admin/README.md`
- [X] T002 [P] Add synthetic summary, user, detail, audit, deleted-identity, null-fallback, and empty expectations in `src/test/resources/contracts/admin/read-fixtures.legacy.json`
- [X] T003 [P] Add a test-only migration-head-compatible administration schema in `src/test/resources/postgres/admin-read-model.sql`
- [X] T004 [P] Add OpenAPI parser/path/parameter/response/no-store assertions in `src/test/java/com/jobtrace/admin/AdminReadOpenApiTest.java`

## Phase 2: Foundational

- [X] T005 Add strict user/audit query normalization tests in `src/test/java/com/jobtrace/admin/AdminReadParametersTest.java`
- [X] T006 [P] Add exact admin-path, method/path binding, role, expiry, and replay bridge tests in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`
- [X] T007 Define immutable administration query, page, summary, user, content, and audit types in `src/main/java/com/jobtrace/admin/domain/`
- [X] T008 Define read operations in `src/main/java/com/jobtrace/admin/application/AdminReadQuery.java`
- [X] T009 Add verified administrator extraction in `src/main/java/com/jobtrace/identityaccess/web/BridgePrincipalAdmin.java`
- [X] T010 Extend only exact administration GET shapes in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`

## Phase 3: User Story 1 - Operational Summary (P1)

- [X] T011 [P] [US1] Add summary JSON parity and partial-unavailable tests in `src/test/java/com/jobtrace/admin/AdminUseCasesTest.java`
- [X] T012 [P] [US1] Add summary SQL/window/gap-free trend tests in `src/test/java/com/jobtrace/admin/AdminReadQueryIntegrationTest.java`
- [X] T013 [US1] Implement independently degradable summary orchestration in `src/main/java/com/jobtrace/admin/application/GetAdminSummary.java`
- [X] T014 [US1] Implement summary reads in `src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminReadQuery.java`
- [X] T015 [US1] Expose `GET /api/admin/summary` in `src/main/java/com/jobtrace/admin/web/AdminReadController.java`

## Phase 4: User Story 2 - Managed Users (P2)

- [X] T016 [P] [US2] Add user list/detail JSON parity tests in `src/test/java/com/jobtrace/admin/AdminReadQueryIntegrationTest.java`
- [X] T017 [P] [US2] Add filter, order, fallback, clamp, and detail composition integration tests in `src/test/java/com/jobtrace/admin/AdminReadQueryIntegrationTest.java`
- [X] T018 [P] [US2] Add admin/user/invalid-principal and safe-error HTTP tests in `src/test/java/com/jobtrace/admin/AdminWebTest.java`
- [X] T019 [US2] Implement managed-user list/detail orchestration in `src/main/java/com/jobtrace/admin/application/ListManagedUsers.java` and `GetManagedUserDetail.java`
- [X] T020 [US2] Implement set-based user and bounded detail reads in `src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminReadQuery.java`
- [X] T021 [US2] Expose user list/detail GET routes in `src/main/java/com/jobtrace/admin/web/AdminReadController.java`

## Phase 5: User Story 3 - Audit Events (P3)

- [X] T022 [P] [US3] Add audit JSON/snapshot parity tests in `src/test/java/com/jobtrace/admin/AdminReadQueryIntegrationTest.java`
- [X] T023 [P] [US3] Add audit filter/date/order/clamp integration tests in `src/test/java/com/jobtrace/admin/AdminReadQueryIntegrationTest.java`
- [X] T024 [US3] Implement audit listing orchestration in `src/main/java/com/jobtrace/admin/application/ListAdminAuditEvents.java`
- [X] T025 [US3] Implement parameterized audit reads in `src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminReadQuery.java`
- [X] T026 [US3] Expose audit GET route in `src/main/java/com/jobtrace/admin/web/AdminReadController.java`

## Phase 6: Cross-Cutting Validation

- [X] T027 Add bounded operation/outcome/latency metrics in `src/main/java/com/jobtrace/admin/web/AdminReadMetrics.java`
- [X] T028 [P] Add authorization, no-store, request-ID, safe dependency failure, and diagnostic redaction tests in `src/test/java/com/jobtrace/admin/AdminWebTest.java`
- [X] T029 [P] Assert no write SQL, mutation route, access function, migration, frontend, job-market admin, or sync surface in `src/test/java/com/jobtrace/admin/AdminReadOnlySurfaceTest.java`
- [X] T030 [P] Add administration dependency rules in `src/test/java/com/jobtrace/ArchitectureTest.java`
- [X] T031 Add reproducible 1,000-user fixed-query p95 checks in `src/test/java/com/jobtrace/admin/AdminReadPerformanceTest.java`
- [X] T032 Add the four GET operations to `specs/001-java-migration/contracts/openapi.yaml`
- [X] T033 Document the completed read slice in `docs/migration-slices/admin-read-model.md` and `docs/migration.md`
- [X] T034 Run `./mvnw verify`, confirm >=80% line/branch coverage, and record local evidence in `specs/009-admin-read-model/validation-report.md`

## Dependencies and Execution Order

- Setup precedes foundation; foundation blocks all stories.
- US1, US2, and US3 share the port/adapter/controller but remain independently testable.
- Cross-cutting validation follows all three stories.
- No task changes CI/CD, deployment, routing, production schema, or write ownership.

## Implementation Strategy

Implement tests before production behavior, complete stories in priority order, and keep every read bounded and independently reversible by leaving live traffic on the current service.
