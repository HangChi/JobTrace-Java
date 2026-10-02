# Tasks: Application Read Model Migration

**Input**: Design documents from `specs/004-application-read-model/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`

**Tests**: Required by the specification and constitution. Write each behavior test before its
implementation and confirm it fails for the intended reason.

**Organization**: Tasks are grouped by user story so the application list MVP, advanced discovery,
and core detail read can each be verified independently.

## Phase 1: Setup (Contract and Test Foundations)

**Purpose**: Establish 004-specific contract validation and deterministic test data without changing
runtime behavior.

- [x] T001 [P] Add feature OpenAPI parsing and path/schema assertions in `src/test/java/com/jobtrace/applications/ApplicationReadOpenApiTest.java`
- [x] T002 [P] Add legacy fixture provenance, normalization rules, and regeneration guidance in `src/test/resources/contracts/applications/README.md`
- [x] T003 [P] Add deterministic empty, representative list, and representative detail legacy fixtures in `src/test/resources/contracts/applications/empty-page.legacy.json`, `src/test/resources/contracts/applications/representative-page.legacy.json`, and `src/test/resources/contracts/applications/representative-detail.legacy.json`
- [x] T004 Add minimal applications, stage-occurrence, and event tables plus enums to `src/test/resources/postgres/applications-read-model.sql` without adding a production migration

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the shared read-model types, criteria normalization, cursor contract, and query
port used by every story.

**⚠️ CRITICAL**: No user story implementation starts until this phase passes its unit tests.

- [x] T005 [P] Add application status, type, stage, sort, and direction enums in `src/main/java/com/jobtrace/applications/domain/ApplicationCatalog.java`
- [x] T006 [P] Add immutable application summary, page, detail, stage occurrence, and event records in `src/main/java/com/jobtrace/applications/domain/ApplicationSummary.java`, `src/main/java/com/jobtrace/applications/domain/ApplicationPage.java`, and `src/main/java/com/jobtrace/applications/domain/ApplicationDetail.java`
- [x] T007 [P] Add criteria normalization and edge-case tests in `src/test/java/com/jobtrace/applications/ApplicationListCriteriaTest.java`
- [x] T008 [P] Add legacy cursor compatibility and malformed-input tests in `src/test/java/com/jobtrace/applications/ApplicationCursorCodecTest.java`
- [x] T009 Implement bounded query normalization in `src/main/java/com/jobtrace/applications/domain/ApplicationListCriteria.java`
- [x] T010 Implement strict base64url JSON cursor encoding and decoding in `src/main/java/com/jobtrace/applications/domain/ApplicationCursorCodec.java`
- [x] T011 Define list/detail query ports in `src/main/java/com/jobtrace/applications/application/ApplicationReadQuery.java`
- [x] T012 Add owner validation and not-found use-case tests in `src/test/java/com/jobtrace/applications/ApplicationReadUseCaseTest.java`
- [x] T013 Implement list and detail use cases in `src/main/java/com/jobtrace/applications/application/ListApplications.java` and `src/main/java/com/jobtrace/applications/application/GetApplicationDetail.java`

**Checkpoint**: Contract types, normalized criteria, cursor semantics, and application ports are
independently testable with no database or web layer.

---

## Phase 3: User Story 1 - Browse My Applications (Priority: P1) 🎯 MVP

**Goal**: Return an authenticated owner's basic application page with established fields, defaults,
empty state, count, and strict owner isolation.

**Independent Test**: Seed applications for two owners and verify the selected owner receives only
their summaries and metadata; an owner with no records receives an empty page.

### Tests for User Story 1

- [x] T014 [P] [US1] Add empty and representative page contract tests in `src/test/java/com/jobtrace/applications/ApplicationReadContractTest.java`
- [x] T015 [P] [US1] Add authenticated, unauthenticated, and no-store controller tests in `src/test/java/com/jobtrace/applications/ApplicationReadControllerTest.java`
- [x] T016 [P] [US1] Add basic list and cross-owner isolation Testcontainers tests in `src/test/java/com/jobtrace/applications/ApplicationOwnerIsolationIntegrationTest.java`

### Implementation for User Story 1

- [x] T017 [US1] Implement owner-scoped count, summary mapping, derived follow-up fields, and default list ordering in `src/main/java/com/jobtrace/applications/infrastructure/PostgresApplicationReadQuery.java`
- [x] T018 [US1] Expose the signed-principal protected list operation with `private, no-store` in `src/main/java/com/jobtrace/applications/web/ApplicationReadController.java`
- [x] T019 [US1] Add bounded outcome and latency metrics without owner or query labels in `src/main/java/com/jobtrace/applications/web/ApplicationReadMetrics.java`

**Checkpoint**: User Story 1 is a complete read-only MVP and can be verified without User Stories 2
or 3.

---

## Phase 4: User Story 2 - Find and Page Through Applications (Priority: P2)

**Goal**: Preserve all search, filter, sort, page, and continuation-cursor behavior.

**Independent Test**: Traverse a fixed mixed data set under every supported sort and direction and
verify combined filters, totals, stable order, and exactly-once pagination.

### Tests for User Story 2

- [x] T020 [P] [US2] Add web parameter normalization and malformed cursor response tests in `src/test/java/com/jobtrace/applications/ApplicationListParametersTest.java`
- [x] T021 [P] [US2] Add combined-filter and applied-date boundary integration tests in `src/test/java/com/jobtrace/applications/ApplicationReadQueryIntegrationTest.java`
- [x] T022 [P] [US2] Add default-priority and all explicit sort/cursor traversal tests in `src/test/java/com/jobtrace/applications/ApplicationPaginationIntegrationTest.java`

### Implementation for User Story 2

- [x] T023 [US2] Bind repeated query parameters and legacy defaults in `src/main/java/com/jobtrace/applications/web/ApplicationListParameters.java`
- [x] T024 [US2] Implement allowlisted search, filters, explicit sorts, offset pages, and tuple cursor predicates in `src/main/java/com/jobtrace/applications/infrastructure/PostgresApplicationReadQuery.java`
- [x] T025 [US2] Complete list controller validation and safe problem mapping in `src/main/java/com/jobtrace/applications/web/ApplicationReadController.java` and `src/main/java/com/jobtrace/shared/web/GlobalExceptionHandler.java`

**Checkpoint**: User Stories 1 and 2 reproduce the complete legacy list contract independently of
the detail read.

---

## Phase 5: User Story 3 - Inspect One Application (Priority: P3)

**Goal**: Return the complete core application detail and history for an owned record while making
missing and cross-owner identifiers indistinguishable.

**Independent Test**: Retrieve an owned application with stages and events, then request a missing
UUID and another owner's UUID and verify the two denied outcomes are identical.

### Tests for User Story 3

- [x] T026 [P] [US3] Add representative detail fixture parity tests in `src/test/java/com/jobtrace/applications/ApplicationDetailContractTest.java`
- [x] T027 [P] [US3] Add stage/event ordering and optional-field integration tests in `src/test/java/com/jobtrace/applications/ApplicationDetailQueryIntegrationTest.java`
- [x] T028 [P] [US3] Add missing-versus-cross-owner indistinguishability tests in `src/test/java/com/jobtrace/applications/ApplicationDetailIsolationIntegrationTest.java`
- [x] T029 [P] [US3] Add forged, stale, path-mismatched, and replayed assertion tests for both application routes in `src/test/java/com/jobtrace/applications/ApplicationReadSecurityTest.java`

### Implementation for User Story 3

- [x] T030 [US3] Implement owner-scoped core detail, stage occurrence, and complete event history mapping in `src/main/java/com/jobtrace/applications/infrastructure/PostgresApplicationReadQuery.java`
- [x] T031 [US3] Expose the UUID detail operation and uniform not-found response in `src/main/java/com/jobtrace/applications/web/ApplicationReadController.java`

**Checkpoint**: All three user stories are complete; the Java surface remains read-only and excludes
interview-enriched detail and mutations.

---

## Phase 6: Polish and Release Gates

**Purpose**: Prove architecture, performance, compatibility, safety, and review readiness without
deployment.

- [x] T032 [P] Add list/detail p95 tests with fixed clock and representative cardinality in `src/test/java/com/jobtrace/applications/ApplicationReadPerformanceTest.java`
- [x] T033 [P] Add applications package dependency rules to `src/test/java/com/jobtrace/ArchitectureTest.java`
- [x] T034 [P] Add application routes and schemas to the repository API baseline in `specs/001-java-migration/contracts/openapi.yaml`
- [x] T035 [P] Document the read-only slice, excluded writes, and no-deployment decision in `docs/migration-slices/application-read-model.md` and `docs/migration.md`
- [x] T036 Verify no production migration or non-GET application handler was introduced using `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java` and `src/test/java/com/jobtrace/applications/ApplicationReadOnlySurfaceTest.java`
- [x] T037 Run `./mvnw verify` and record tests, coverage, static analysis, contracts, performance, and Java-only artifact evidence in `specs/004-application-read-model/validation-report.md`
- [x] T038 Review the diff against the specification and all four constitution principles and record the Solo-Maintainer Mode self-review in `specs/004-application-read-model/validation-report.md`
- [ ] T039 Open a stacked pull request with feature 003 as its dependency, pass all CI/security checks, and record its URL and unscheduled deployment status in `specs/004-application-read-model/validation-report.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately.
- **Foundational (Phase 2)**: Depends on fixture/schema setup and blocks all user stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and is the MVP.
- **User Story 2 (Phase 4)**: Depends on shared criteria/cursor foundations and extends the US1 list
  adapter; execute after US1 in a single-maintainer workflow.
- **User Story 3 (Phase 5)**: Depends only on Phase 2, but follows US2 to minimize concurrent edits to
  the shared query adapter and controller.
- **Polish (Phase 6)**: Depends on every selected user story.

### User Story Dependencies

- **US1**: No story dependency after foundation.
- **US2**: Reuses US1's page model and list adapter but remains independently testable through the
  list endpoint.
- **US3**: Reuses only shared models, query port, controller, and signed identity; it does not depend
  on list filtering behavior.

### Parallel Opportunities

- T001–T003 can proceed in parallel.
- T005–T008 target independent model/test files.
- Test tasks inside each story marked `[P]` can be prepared independently before implementation.
- T032–T035 target separate performance, architecture, contract, and documentation files.
- The repository has one human maintainer, so the recommended execution order is sequential even
  where tasks are structurally parallel.

## Parallel Example: User Story 3

```text
Task T026: Detail legacy-fixture contract test
Task T027: Stage/event mapping integration test
Task T028: Missing/cross-owner isolation test
Task T029: Signed identity negative-path tests
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete User Story 1.
3. Run its unit, controller, contract, and Testcontainers isolation tests.
4. Stop safely if desired; no routing or deployment changes are required.

### Incremental Delivery

1. Add US2 search/filter/sort/cursor parity and re-run the US1 suite.
2. Add US3 core detail parity and re-run all list tests.
3. Complete Phase 6 and open a stacked pull request.
4. Do not add deployment or canary tasks unless the user later creates a separate release feature.

## Notes

- `[P]` marks file-level parallelism only; it does not imply multiple agents or maintainers.
- Every SQL path derives `owner_id` from the signed principal and binds it as a parameter.
- Tests precede behavior implementation and must fail for the intended missing behavior.
- Feature 004 completion is code-and-CI complete, not production-active.
