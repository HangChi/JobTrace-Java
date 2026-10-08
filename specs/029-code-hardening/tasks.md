# Tasks: Java Migration Code Hardening

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/route-matrix.md](contracts/route-matrix.md)

## Phase 1: Setup

- [x] T001 Record stage-029 scope and quality gates in `specs/029-code-hardening/spec.md` and `plan.md`.
- [x] T002 Capture the 65 legacy API route dispositions in `specs/029-code-hardening/contracts/route-matrix.md`.

## Phase 2: Foundational

- [x] T003 Add an executable route-inventory guard in `src/test/java/com/jobtrace/migration/RouteParityInventoryTest.java` that checks the matrix against the pinned legacy route list and Java-owned exclusions.

## Phase 3: User Story 1 — Compatible workflows (P1)

**Independent test**: Contract fixtures and HTTP tests for each supported workflow group agree with the matrix; crawler routes remain legacy-only.

- [x] T004 [US1] Audit legacy route dispositions in `specs/029-code-hardening/contracts/route-matrix.md` and correct the missing `/api/health` route and cache behavior in `src/main/java/com/jobtrace/shared/health/HealthController.java`.
- [x] T005 [US1] Add representative create-to-read owner-isolation HTTP regression in `src/test/java/com/jobtrace/applications/ApplicationCommandIntegrationTest.java` and health parity in `src/test/java/com/jobtrace/shared/health/HealthControllerIntegrationTest.java`.

## Phase 4: User Story 2 — Safe failures and concurrency (P1)

**Independent test**: Anonymous, cross-owner, stale, replay and malformed requests fail safely; one concurrent transition commits at most once.

- [x] T006 [US2] Add malformed-body, missing-input and dependency-failure regression in `src/test/java/com/jobtrace/shared/web/GlobalExceptionHandlerTest.java` and `GlobalErrorHttpContractTest.java`.
- [x] T007 [US2] Correct observed error mapping and redact unexpected exception logs in `src/main/java/com/jobtrace/shared/web/GlobalExceptionHandler.java`.
- [x] T008 [US2] Add ordinary-user admin denial in `src/test/java/com/jobtrace/identityaccess/BrowserAuthenticationIntegrationTest.java`, retaining existing origin/CSRF/owner tests.
- [x] T009 [US2] Add concurrent stale-version and once-only replay regressions in `src/test/java/com/jobtrace/applications/ApplicationCommandIntegrationTest.java` and `src/test/java/com/jobtrace/identityaccess/ClaimAssertionUseCaseTest.java`.

## Phase 5: User Story 3 — Explicit data ownership (P2)

**Independent test**: Java startup against a synthetic legacy database creates no Flyway history or marketplace ingestion run; approval gate remains documented.

- [x] T010 [US3] Verify and document schema and marketplace writer ownership in `docs/database-baseline.md` and `docs/migration-slices/job-market-operations.md`.
- [x] T011 [US3] Add static activation/ingestion guards in `src/test/java/com/jobtrace/migration/MigrationActivationGateTest.java` and retain the PostgreSQL startup assertion in `LegacySchemaSafetyTest.java`.

## Phase 6: Validation and handoff

- [x] T012 Run `./mvnw verify`, inspect executed/skipped tests and changed-code coverage, and record results in `specs/029-code-hardening/validation-report.md`.
- [x] T013 Record the user's deferred live/manual checks without claiming completion in `specs/029-code-hardening/quickstart.md`.

## Dependencies

T001–T003 precede route parity changes; T006 precedes T007; T008–T009 may use separate test files but are executed by one agent sequentially. T010 precedes T011; T012 follows all code and documentation tasks. No subagent work is used.
