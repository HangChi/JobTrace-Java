# Tasks: Java Migration Foundation

**Input**: Design documents from `specs/001-java-migration/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: The specification requires automated backend, frontend, contract, and build verification, so test tasks are included before their corresponding implementation tasks.

**Organization**: Tasks are grouped by user story so each story remains independently demonstrable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes independent files and has no incomplete dependency.
- **[Story]**: Maps the task to a user story from `spec.md`.

## Phase 1: Setup

**Purpose**: Complete repository-level conventions that support every later slice.

- [x] T001 Record the source repository API, module, migration, and test inventory in `docs/source-inventory.md`
- [x] T002 [P] Configure Java formatting and static analysis in `pom.xml` and `config/checkstyle/checkstyle.xml`
- [x] T003 [P] Configure frontend coverage thresholds in `frontend/vite.config.ts` and `frontend/package.json`
- [x] T004 [P] Add dependency update policy and automated update configuration in `.github/dependabot.yml`
- [x] T005 Document local secret handling and configuration precedence in `docs/configuration.md`

---

## Phase 2: Foundational

**Purpose**: Establish shared behavior required before migrating any business capability.

**⚠️ CRITICAL**: User-story implementation begins only after this phase passes CI.

- [x] T006 [P] Add PostgreSQL Testcontainers support and reusable test configuration in `src/test/java/com/jobtrace/testing/PostgresIntegrationTest.java`
- [x] T007 [P] Add RFC 9457-compatible safe error responses in `src/main/java/com/jobtrace/shared/web/GlobalExceptionHandler.java`
- [x] T008 [P] Add request ID generation and response propagation in `src/main/java/com/jobtrace/shared/web/RequestIdFilter.java`
- [x] T009 Add structured logging with sensitive-field redaction tests in `src/main/java/com/jobtrace/shared/observability/SafeLogger.java` and `src/test/java/com/jobtrace/shared/observability/SafeLoggerTest.java`
- [x] T010 Add typed environment validation for database and auth-bridge settings in `src/main/java/com/jobtrace/shared/config/JobTraceProperties.java`
- [x] T011 Add modular package boundaries and architecture tests in `src/test/java/com/jobtrace/ArchitectureTest.java`
- [x] T012 Add API contract validation utilities in `src/test/java/com/jobtrace/testing/OpenApiContractTest.java`

**Checkpoint**: Shared security, errors, observability, database testing, and module boundaries are available.

---

## Phase 3: User Story 1 - Reproducible contributor setup (Priority: P1) 🎯 MVP

**Goal**: A contributor can clone, configure, verify, and run both applications through one documented path.

**Independent Test**: Follow `specs/001-java-migration/quickstart.md` from a clean clone and confirm every command and expected outcome.

### Tests for User Story 1

- [x] T013 [P] [US1] Add database-backed readiness integration tests in `src/test/java/com/jobtrace/shared/health/HealthControllerIntegrationTest.java`
- [x] T014 [P] [US1] Add frontend health-client tests in `frontend/src/shared/api/health.test.ts`
- [x] T015 [P] [US1] Add a clean-clone verification script test in `scripts/test-quickstart.sh`

### Implementation for User Story 1

- [x] T016 [P] [US1] Add a local PostgreSQL service with health checks in `compose.yaml`
- [x] T017 [P] [US1] Implement the typed health client in `frontend/src/shared/api/health.ts`
- [x] T018 [US1] Show backend liveness and readiness states in `frontend/src/App.tsx`
- [x] T019 [US1] Document Maven, npm, and Docker Compose development commands in `specs/001-java-migration/quickstart.md`
- [x] T020 [US1] Verify and correct every command and expected response in `specs/001-java-migration/quickstart.md`

**Checkpoint**: A clean clone can be configured, tested, and run locally in under 20 minutes.

---

## Phase 4: User Story 2 - Single production artifact (Priority: P2)

**Goal**: One Java artifact serves the compiled browser application and health endpoints without Node.js in production.

**Independent Test**: Build the JAR, copy it to a Java-only runtime environment, start it, and verify the application shell and both health routes.

### Tests for User Story 2

- [x] T021 [P] [US2] Add an executable-JAR smoke test in `src/test/java/com/jobtrace/packaging/PackagedApplicationTest.java`
- [x] T022 [P] [US2] Add direct browser-route fallback tests in `src/test/java/com/jobtrace/shared/web/SpaForwardControllerTest.java`
- [x] T023 [P] [US2] Add a production-container smoke script in `scripts/test-production-artifact.sh`

### Implementation for User Story 2

- [x] T024 [US2] Implement safe SPA route fallback without intercepting API paths in `src/main/java/com/jobtrace/shared/web/SpaForwardController.java`
- [x] T025 [US2] Add a minimal Java-only runtime image in `Dockerfile`
- [x] T026 [US2] Add frontend asset digest and source revision to build metadata in `pom.xml`
- [x] T027 [US2] Add production artifact verification to `.github/workflows/ci.yml`
- [x] T028 [US2] Document Java-only deployment and rollback in `docs/operations.md`

**Checkpoint**: The packaged application runs without Node.js and distinguishes liveness from readiness.

---

## Phase 5: User Story 3 - Safe incremental migration (Priority: P3)

**Goal**: The team can migrate and roll back one read-only capability while preserving observable behavior and protecting the legacy database.

**Independent Test**: Run the analytics-summary fixtures against old and new implementations, compare normalized results, route a test request to Java, and route it back without a schema change.

### Tests for User Story 3

- [x] T029 [P] [US3] Capture analytics-summary legacy contract fixtures in `src/test/resources/contracts/analytics-summary/`
- [x] T030 [P] [US3] Add a dual-implementation contract comparison harness in `src/test/java/com/jobtrace/migration/ContractComparisonTest.java`
- [x] T031 [P] [US3] Add tests proving Flyway remains disabled for legacy connections in `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java`
- [x] T032 [P] [US3] Add owner-isolation acceptance cases for the pilot in `src/test/java/com/jobtrace/analytics/AnalyticsSummarySecurityTest.java`

### Implementation for User Story 3

- [x] T033 [P] [US3] Define migration-slice metadata and validation in `src/main/java/com/jobtrace/migration/MigrationSlice.java`
- [x] T034 [P] [US3] Document the legacy schema head and checksum capture procedure in `docs/database-baseline.md`
- [x] T035 [US3] Implement the read-only analytics summary query in `src/main/java/com/jobtrace/analytics/infrastructure/PostgresAnalyticsSummaryQuery.java`
- [x] T036 [US3] Implement the analytics summary use case in `src/main/java/com/jobtrace/analytics/application/GetAnalyticsSummary.java`
- [x] T037 [US3] Expose the pilot contract in `src/main/java/com/jobtrace/analytics/web/AnalyticsSummaryController.java`
- [x] T038 [US3] Extend the API definition with the verified pilot operation in `specs/001-java-migration/contracts/openapi.yaml`
- [x] T039 [US3] Document traffic ownership, observation, and rollback for the pilot in `docs/migration-slices/analytics-summary.md`

**Checkpoint**: The first read-only slice is contract-equivalent, owner-isolated, reversible, and does not modify the schema.

---

## Phase 6: Polish and cross-cutting concerns

**Purpose**: Close repository and operational gaps before declaring the foundation complete.

- [ ] T040 [P] Add accessibility checks for the migration shell in `frontend/src/App.a11y.test.tsx`
- [ ] T041 [P] Add dependency and secret scanning to `.github/workflows/security.yml`
- [ ] T042 Add performance smoke budgets for health and analytics reads in `src/test/java/com/jobtrace/performance/ReadPerformanceTest.java`
- [ ] T043 Run the complete quickstart and record verification evidence in `specs/001-java-migration/validation-report.md`
- [ ] T044 Add the selected remote URL and push instructions to `docs/repository-setup.md`
- [ ] T045 Apply and document remote `main` branch protection in `docs/repository-setup.md`
- [ ] T046 Add the repository-owner-approved license in `LICENSE`

---

## Dependencies and execution order

### Phase dependencies

- **Setup**: Starts immediately.
- **Foundational**: Depends on Setup and blocks all user stories.
- **US1**: Starts after Foundational and is the recommended MVP.
- **US2**: Starts after Foundational; its final smoke test uses the verified frontend and backend build from US1.
- **US3**: Starts after Foundational; contract capture can run in parallel with US1, but pilot activation waits for US1 verification.
- **Polish**: Starts after the stories selected for the release are complete. Remote and license tasks require owner input.

### User story dependencies

- **US1** has no other story dependency.
- **US2** is independently testable but reuses the verified build commands established by US1.
- **US3** is independently testable against fixtures; production routing waits for the shared foundation and an explicit write-owner declaration.

### Parallel opportunities

- T002–T004 can run in parallel.
- T006–T008 can run in parallel before T009–T012 integrate the foundation.
- T013–T017 can run in parallel, followed by T018–T020.
- T021–T023 can run in parallel, followed by T024–T028.
- T029–T034 can run in parallel, followed by T035–T039 in dependency order.
- T040–T041 can run in parallel after the selected stories complete.

## Parallel example: User Story 3

```text
Task: T029 Capture analytics-summary legacy contract fixtures in src/test/resources/contracts/analytics-summary/
Task: T030 Add a dual-implementation contract comparison harness in src/test/java/com/jobtrace/migration/ContractComparisonTest.java
Task: T031 Add tests proving Flyway remains disabled in src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java
Task: T032 Add owner-isolation cases in src/test/java/com/jobtrace/analytics/AnalyticsSummarySecurityTest.java
Task: T034 Document baseline capture in docs/database-baseline.md
```

## Implementation strategy

### MVP first

1. Complete Setup and Foundational phases.
2. Complete US1.
3. Validate the quickstart from a clean clone.
4. Stop and review before adding deployment or migrated business behavior.

### Incremental delivery

1. Contributor baseline proves the repository can be changed safely.
2. Single-artifact packaging proves Node.js is absent from production.
3. The analytics pilot proves the contract-based migration loop.
4. Later domain specifications repeat that loop one bounded module at a time.

## Format validation

All tasks use the required checkbox, sequential task ID, optional parallel marker, required user-story label within story phases, actionable description, and exact file path.
