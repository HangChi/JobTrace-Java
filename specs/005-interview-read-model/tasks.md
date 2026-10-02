# Tasks: Private Interview Read Model Migration

**Input**: Design documents from `specs/005-interview-read-model/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`

**Tests**: Required by the feature specification and constitution. For each behavior, write the indicated test before implementation and confirm it fails for the intended missing behavior. Use synthetic data only.

**Organization**: Tasks are grouped by independently testable user story. `[P]` indicates file-level parallelism, not a requirement to use multiple agents or maintainers.

## Phase 1: Setup — Legacy Contract and Test Foundations

**Purpose**: Establish reproducible evidence without changing Java runtime behavior or the production schema.

- [ ] T001 Document committed legacy source locations, fixture provenance, timestamp normalization, cursor regeneration, and synthetic-data rules in `src/test/resources/contracts/interviews/README.md`.
- [ ] T002 [P] Add synthetic empty, representative, filtered, and cursor-page legacy list fixtures in `src/test/resources/contracts/interviews/empty-page.legacy.json`, `src/test/resources/contracts/interviews/representative-page.legacy.json`, `src/test/resources/contracts/interviews/filtered-page.legacy.json`, and `src/test/resources/contracts/interviews/cursor-page.legacy.json`.
- [ ] T003 [P] Add a minimal test-only PostgreSQL 17 schema with applications, stage occurrences, interview reviews, questions, action items, current assessment/publication enum values, and relevant indexes in `src/test/resources/postgres/interviews-read-model.sql`; do not add a production migration.
- [ ] T004 [P] Add parsing and path/schema assertions for all three feature operations in `src/test/java/com/jobtrace/interviews/InterviewReadOpenApiTest.java` against `specs/005-interview-read-model/contracts/openapi.yaml`.

**Checkpoint**: Legacy contract evidence and isolated data setup are available to later story tests.

---

## Phase 2: Foundational — Shared Types and Trusted Identity

**Purpose**: Provide shared read types, strict interview-specific input rules, query ports, and a safe identity boundary before any route is implemented.

**⚠️ CRITICAL**: Complete this phase before user-story implementation.

- [ ] T005 [P] Define interview stage, review status, round result, publication, author mode, format, and question category catalogs matching the legacy values in `src/main/java/com/jobtrace/interviews/domain/InterviewCatalog.java`.
- [ ] T006 Define immutable interview summary, page, and application-stage summary records with the fields and nullability from `data-model.md` in `src/main/java/com/jobtrace/interviews/domain/InterviewSummary.java`, `src/main/java/com/jobtrace/interviews/domain/InterviewPage.java`, and `src/main/java/com/jobtrace/interviews/domain/StageInterviewSummary.java`.
- [ ] T007 [P] Write tests for trimmed search, repeated valid/empty filters, strict invalid enum/date/UUID/limit failures, inclusive date boundaries, and default limit in `src/test/java/com/jobtrace/interviews/InterviewListCriteriaTest.java`.
- [ ] T008 [P] Write base64url JSON cursor tests for required date value and UUID, malformed payloads, same-date ties, and owner-independent navigation in `src/test/java/com/jobtrace/interviews/InterviewCursorCodecTest.java`.
- [ ] T009 Implement interview-specific bounded and strict criteria normalization in `src/main/java/com/jobtrace/interviews/domain/InterviewListCriteria.java` to satisfy T007 without reusing the tolerant feature 004 parser.
- [ ] T010 Implement the legacy-compatible cursor codec in `src/main/java/com/jobtrace/interviews/domain/InterviewCursorCodec.java` to satisfy T008; treat the cursor as navigation data, never authorization.
- [ ] T011 Define owner-scoped list, count, detail, and application-summary read operations in `src/main/java/com/jobtrace/interviews/application/InterviewReadQuery.java`.
- [ ] T012 [P] Write tests rejecting null principals, ordinary authenticated principals, and mismatched principal names in `src/test/java/com/jobtrace/identityaccess/BridgePrincipalOwnerTest.java`.
- [ ] T013 Implement a reusable trusted-owner extractor for controllers in `src/main/java/com/jobtrace/identityaccess/application/BridgePrincipalOwner.java` to satisfy T012; accept only matching `BridgeIdentity` details.
- [ ] T014 [P] Add bridge-filter regression tests for exact private interview list/detail and application-dialog paths, path-bound assertions, and excluded public/community paths in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`.
- [ ] T015 Extend the feature 003 bridge path matcher in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java` to satisfy T014 without broadly matching public interview paths or changing the default-disabled bridge setting.

**Checkpoint**: The three stories can use the same trusted owner and query contract without transferring session or write ownership.

---

## Phase 3: User Story 1 — Browse My Interview Reviews (Priority: P1) 🎯 MVP

**Goal**: Provide the owner-scoped private list with strict filters, full filtered total, stable cursor pagination, and legacy summary fields.

**Independent Test**: Seed two owners and mixed reviews; verify empty, searched, combined-filter, publication, same-date, and successive-cursor results match the legacy fixtures and disclose no cross-owner data.

### Tests for User Story 1

- [ ] T016 [P] [US1] Add structural JSON parity tests for empty, representative, filtered, and cursor-page fixtures in `src/test/java/com/jobtrace/interviews/InterviewListContractTest.java`.
- [ ] T017 [P] [US1] Add HTTP parameter and response tests for defaults, repeated filters, strict 400 failures, `private, no-store`, and the ordinary-principal 401 case in `src/test/java/com/jobtrace/interviews/InterviewListControllerTest.java`.
- [ ] T018 [P] [US1] Add Testcontainers tests proving owner isolation in list items, totals, company/position/question search, and publication modes in `src/test/java/com/jobtrace/interviews/InterviewListOwnerIsolationIntegrationTest.java`.
- [ ] T019 [P] [US1] Add Testcontainers tests for application/status/stage/result/date filter combinations, inclusive boundaries, assessment reviews, and unlinked stage snapshots in `src/test/java/com/jobtrace/interviews/InterviewListQueryIntegrationTest.java`.
- [ ] T020 [P] [US1] Add same-date UUID tie-break, cursor traversal exactly once, filtered total-before-cursor, malformed cursor, and cursor reuse under another filter tests in `src/test/java/com/jobtrace/interviews/InterviewPaginationIntegrationTest.java`.
- [ ] T021 [P] [US1] Add unit tests for owner validation, criteria forwarding, empty results, and query failure propagation in `src/test/java/com/jobtrace/interviews/ListPrivateInterviewsTest.java`.

### Implementation for User Story 1

- [ ] T022 [US1] Implement the list use case using the shared query port in `src/main/java/com/jobtrace/interviews/application/ListPrivateInterviews.java` to satisfy T021.
- [ ] T023 [US1] Implement parameterized, owner-bound list/count SQL, question-text search, fixed date/UUID ordering, tuple cursor predicate, count fields, and summary mapping in `src/main/java/com/jobtrace/interviews/infrastructure/PostgresInterviewReadQuery.java` to satisfy T018–T020.
- [ ] T024 [US1] Bind repeated query values and strict validation in `src/main/java/com/jobtrace/interviews/web/InterviewListParameters.java` to satisfy T017.
- [ ] T025 [US1] Expose only `GET /api/interviews` with trusted owner, safe errors, and `private, no-store` in `src/main/java/com/jobtrace/interviews/web/InterviewReadController.java` to satisfy T016–T017.
- [ ] T026 [US1] Add bounded operation/outcome and latency metrics without owner, review ID, query, token, or response labels in `src/main/java/com/jobtrace/interviews/web/InterviewReadMetrics.java`.
- [ ] T027 [US1] Map invalid filters and malformed cursors to the established safe problem envelope in `src/main/java/com/jobtrace/interviews/web/InterviewReadExceptionHandler.java`.
- [ ] T028 [US1] Run the US1 unit, contract, HTTP, and PostgreSQL tests from T016–T021 and record the independent MVP result in `specs/005-interview-read-model/validation-report.md`.

**Checkpoint**: User Story 1 is independently testable and remains read-only; no detail or dialog route is required for the MVP.

---

## Phase 4: User Story 2 — Inspect One Private Review (Priority: P2)

**Goal**: Return complete owned review detail and ordered children with indistinguishable missing/cross-owner outcomes.

**Independent Test**: Read an owned review with children, an empty-child review, and an unlinked-stage review; compare legacy JSON, then verify absent and cross-owner IDs yield the same 404 outcome.

### Tests for User Story 2

- [ ] T029 [P] [US2] Add synthetic complete, empty-child, and unlinked-stage legacy detail fixtures in `src/test/resources/contracts/interviews/representative-detail.legacy.json`, `src/test/resources/contracts/interviews/empty-detail.legacy.json`, and `src/test/resources/contracts/interviews/unlinked-detail.legacy.json`.
- [ ] T030 [US2] Add detail JSON parity and child-order tests against T029 fixtures in `src/test/java/com/jobtrace/interviews/InterviewDetailContractTest.java`.
- [ ] T031 [P] [US2] Add Testcontainers tests for ordered questions/action items, null fields, assessment and stage-snapshot fallback, and owner-bound child reads in `src/test/java/com/jobtrace/interviews/InterviewDetailQueryIntegrationTest.java`.
- [ ] T032 [P] [US2] Add missing-versus-cross-owner 404 equivalence and valid-owner detail HTTP tests in `src/test/java/com/jobtrace/interviews/InterviewDetailIsolationTest.java`.
- [ ] T033 [P] [US2] Add detail use-case tests for blank owner, null ID, owned result, and missing result in `src/test/java/com/jobtrace/interviews/GetPrivateInterviewTest.java`.

### Implementation for User Story 2

- [ ] T034 [P] [US2] Define immutable detail, question, and action-item models with empty collections and nullable optional values in `src/main/java/com/jobtrace/interviews/domain/InterviewDetail.java`, `src/main/java/com/jobtrace/interviews/domain/InterviewQuestion.java`, and `src/main/java/com/jobtrace/interviews/domain/InterviewActionItem.java`.
- [ ] T035 [US2] Implement owned detail lookup and a uniform not-found outcome in `src/main/java/com/jobtrace/interviews/application/GetPrivateInterview.java` to satisfy T033.
- [ ] T036 [US2] Add owner-bound detail and child queries ordered by saved `sort_order` to `src/main/java/com/jobtrace/interviews/infrastructure/PostgresInterviewReadQuery.java` to satisfy T031–T032.
- [ ] T037 [US2] Add only `GET /api/interviews/{id}` to `src/main/java/com/jobtrace/interviews/web/InterviewReadController.java`, using the shared owner extractor, no-store response, metrics, and safe problem mapping.

**Checkpoint**: User Stories 1 and 2 each work independently; public/community reads and every write remain outside Java.

---

## Phase 5: User Story 3 — See Interviews Beside an Application (Priority: P3)

**Goal**: Compose the existing feature 004 application detail with owner-scoped interview summaries for the legacy dialog response.

**Independent Test**: For an owned application with linked/unlinked reviews, verify exact dialog JSON and order; for an owned application without reviews, return an empty array; absent and cross-owner applications must be indistinguishable.

### Tests for User Story 3

- [ ] T038 [P] [US3] Add synthetic application-dialog fixtures with interviews and no interviews in `src/test/resources/contracts/interviews/representative-dialog.legacy.json` and `src/test/resources/contracts/interviews/empty-dialog.legacy.json`.
- [ ] T039 [US3] Add dialog contract tests verifying the feature 004 application object remains unchanged and interview summaries match T038 fixtures in `src/test/java/com/jobtrace/applicationdialog/ApplicationDialogContractTest.java`.
- [ ] T040 [P] [US3] Add PostgreSQL tests for application/review owner predicates, linked/unlinked stages, descending date/ID order, and zero cross-owner summary/count disclosure in `src/test/java/com/jobtrace/applicationdialog/ApplicationDialogIsolationIntegrationTest.java`.
- [ ] T041 [P] [US3] Add composition tests proving application ownership is checked before interview summaries and missing/cross-owner applications produce identical 404 outcomes in `src/test/java/com/jobtrace/applicationdialog/GetApplicationDialogDataTest.java`.

### Implementation for User Story 3

- [ ] T042 [US3] Add an owner-scoped stage-summary use case to the existing interview query port in `src/main/java/com/jobtrace/interviews/application/ListInterviewsForApplication.java` and `src/main/java/com/jobtrace/interviews/application/InterviewReadQuery.java`.
- [ ] T043 [US3] Implement the application-specific summary query with both application and review owner predicates in `src/main/java/com/jobtrace/interviews/infrastructure/PostgresInterviewReadQuery.java` to satisfy T040.
- [ ] T044 [US3] Compose `GetApplicationDetail` from feature 004 with T042 in `src/main/java/com/jobtrace/applicationdialog/application/GetApplicationDialogData.java` and `src/main/java/com/jobtrace/applicationdialog/application/ApplicationDialogData.java` to satisfy T041.
- [ ] T045 [US3] Expose only `GET /api/applications/{id}/detail` with trusted owner, uniform 404, metrics, and `private, no-store` in `src/main/java/com/jobtrace/applicationdialog/web/ApplicationDialogController.java` to satisfy T039.
- [ ] T046 [US3] Add architecture rules that keep `applications` and `interviews` independent and allow only the thin `applicationdialog` orchestration edge in `src/test/java/com/jobtrace/ArchitectureTest.java`.

**Checkpoint**: All three user stories are independently verified and no production traffic or write ownership has moved.

---

## Phase 6: Polish and Release Gates

**Purpose**: Prove security, compatibility, performance, schema safety, and review readiness without deployment.

- [ ] T047 [P] Add the three protected GET operations and schemas to the repository API baseline in `specs/001-java-migration/contracts/openapi.yaml` without adding mutation or public interview routes.
- [ ] T048 [P] Add bounded success, invalid, denied, not-found, dependency-failure, and latency-breach metrics tests for all three operations in `src/test/java/com/jobtrace/interviews/InterviewReadMetricsTest.java`.
- [ ] T049 [P] Add missing, forged, expired, path-mismatched, and replayed assertion tests for each route plus ordinary-principal and public-header denial in `src/test/java/com/jobtrace/interviews/InterviewReadSecurityTest.java`.
- [ ] T050 [P] Add safe database-outage responses and absence of private data in logs/problems for list, detail, and dialog in `src/test/java/com/jobtrace/interviews/InterviewReadDependencyFailureTest.java`.
- [ ] T051 [P] Add fixed-clock, documented representative-load tests with at least 40 measured samples and bounded query counts for list, detail, and dialog p95 ≤ 500 ms in `src/test/java/com/jobtrace/interviews/InterviewReadPerformanceTest.java`.
- [ ] T052 [P] Assert that 005 adds no production migration, Flyway activation, non-GET interview handler, public feed route, view-count mutation, export, or other write surface in `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java` and `src/test/java/com/jobtrace/interviews/InterviewReadOnlySurfaceTest.java`.
- [ ] T053 [P] Document the private read slice, unchanged legacy writer/session/schema owner, excluded public/write flows, and no-deployment decision in `docs/migration-slices/interview-read-model.md` and `docs/migration.md`.
- [ ] T054 Run `./mvnw verify` with Docker and record contract parity, Testcontainers isolation, OpenAPI, architecture, coverage ≥ 80% line/branch, Checkstyle, p95, query-count, and Java-only artifact evidence in `specs/005-interview-read-model/validation-report.md`.
- [ ] T055 Review the implementation against `spec.md` and all four constitution principles, record the Solo-Maintainer Mode self-review in `specs/005-interview-read-model/validation-report.md`, then open a PR to `main`, pass required CI/security checks, and record its URL and unscheduled deployment status there.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** starts immediately and supplies fixtures, schema, and contract parser.
- **Foundational (Phase 2)** follows setup and blocks every user story.
- **US1 (Phase 3)** is the MVP after Phase 2.
- **US2 (Phase 4)** uses shared summaries, query port, bridge, and adapter from US1. It can be independently verified as a detail read once those shared pieces exist.
- **US3 (Phase 5)** needs feature 004 application detail plus the shared interview query port; it does not require US2 detail fields, but follows US2 in a single-maintainer workflow to avoid conflicting edits to the same adapter.
- **Polish (Phase 6)** follows all selected stories and blocks PR merge, not deployment.

### User Story Dependencies

- **US1**: No story dependency after foundational work.
- **US2**: Reuses US1's summary model, query port, adapter, and controller; its owned-detail acceptance test remains independent of list filtering.
- **US3**: Reuses the interview stage-summary model and feature 004's application detail; its dialog acceptance test remains independent of US2 detail content.

### Within Each Story

1. Add the story's fixtures and tests first; confirm the intended failure.
2. Add models, use cases, parameter handling, and parameterized SQL.
3. Add only the story's GET surface, then pass its independent contract, isolation, and HTTP tests.
4. Do not defer owner-isolation or public-header denial to a later story.

### Parallel Opportunities

- T002–T004 can be prepared in separate fixture, schema, and contract-test files.
- T005, T007–T008, T012, and T014 target distinct foundational files; T006 follows T005, T009 follows T007, and T010 follows T008.
- Within US1, T016–T021 target separate test files; T022–T027 then follow the tests and shared foundation.
- Within US2, T029, T031–T033 target separate fixtures and tests; T030 follows T029, and T034 can be prepared separately from the SQL adapter.
- Within US3, T038, T040–T041 target separate fixtures and tests; T039 follows T038, and T046 touches only architecture rules.
- T047–T053 mostly target separate files, but T052 and any other change to `LegacySchemaSafetyTest.java` must be serialized.
- These markers describe file independence only; the repository has one human maintainer and no parallel agents are required.

## Parallel Example: User Story 1

```text
T016: Interview list JSON contract test
T018: PostgreSQL owner-isolation test
T020: Cursor traversal and tie-break test
```

## Parallel Example: User Story 2

```text
T029: Synthetic detail fixtures
T031: Ordered child-query integration test
T033: Detail use-case unit test
```

## Parallel Example: User Story 3

```text
T038: Application dialog fixtures
T040: Cross-owner dialog integration test
T041: Dialog composition unit test
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete US1 through T028.
3. Run its contract, HTTP, PostgreSQL, security, and cursor suite independently.
4. Stop safely if desired; the existing service still owns all sessions, writes, public interview behavior, and traffic.

### Incremental Delivery

1. Add US2 private detail without changing US1 list behavior.
2. Add US3 dialog composition without duplicating feature 004 application SQL.
3. Finish cross-cutting gates and PR review.
4. Do not add deployment, canary, cutover, or rollback-rehearsal tasks unless the user later authorizes a separate release feature.

## Notes

- `[P]` marks file-level independence, not delegation.
- Every SQL path derives the owner from the verified signed principal; query/path inputs never choose an owner.
- The current Java repository is not being deployed; completion means verified code and CI, not production activation.
