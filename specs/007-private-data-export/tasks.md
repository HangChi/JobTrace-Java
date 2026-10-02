# Tasks: Private Data Export Migration

**Input**: Design documents from `specs/007-private-data-export/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`

**Tests**: Required by the specification and constitution. Write each behavior's tests before implementation and confirm the intended missing-behavior failure. Use synthetic data only.

**Organization**: Tasks are grouped by independently testable user story. `[P]` means file-level independence, not permission or a need to use subagents.

## Phase 1: Setup — Current Export Contract

**Purpose**: Establish a trustworthy runtime oracle without production files or Java behavior changes.

- [X] T001 Record current application/interview export routes, defaults, selection bounds, filters, output headers, no-result behavior, filename rules, and the intentional application no-store hardening in `src/test/resources/contracts/exports/README.md`.
- [X] T002 [P] Add synthetic application CSV/XLSX and interview Markdown/ZIP expectations, tricky Unicode, formula, hyperlink, duplicate-name, and two-owner cases in `src/test/resources/contracts/exports/read-fixtures.legacy.json`.
- [X] T003 [P] Add a minimal test-only PostgreSQL schema for applications, stage occurrences, reviews, questions, and action items in `src/test/resources/postgres/data-export-read-model.sql`; add no production migration.
- [X] T004 [P] Add parser/path/media/error assertions for both planned download operations in `src/test/java/com/jobtrace/datatransfer/ExportOpenApiTest.java` against `specs/007-private-data-export/contracts/openapi.yaml`.
- [X] T005 Record the pinned and security-reviewed XLSX writer version and why streaming is needed in `pom.xml` and `specs/007-private-data-export/research.md`; verify the dependency introduces no unnecessary runtime stack.

**Checkpoint**: Synthetic legacy download oracle, isolated schema, contract and one justified writer dependency are ready.

---

## Phase 2: Foundational — Trusted Owner and Safe Download Types

**Purpose**: Establish exact private entry and reusable immutable selections before exposing either download.

**⚠️ CRITICAL**: Complete this phase before user-story implementation.

- [X] T006 [P] Add tests for application format/scope fallbacks, bounded UUID selections, deduplication, safe formula text, CSV quoting, and filename sanitization in `src/test/java/com/jobtrace/datatransfer/ExportRulesTest.java`.
- [X] T007 [P] Add bridge tests for exact export GET paths, method/path-bound assertions, replay handling, and excluded import/mutation paths in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`.
- [X] T008 Define immutable export selection, application row, interview document, and download metadata types in `src/main/java/com/jobtrace/datatransfer/domain/ApplicationExportSelection.java`, `ApplicationExportRow.java`, `InterviewExportSelection.java`, `InterviewExportDocument.java`, and `ExportDownload.java` to satisfy T006; never carry caller-selected owner in a selection.
- [X] T009 Define owner-scoped application and interview export operations in `src/main/java/com/jobtrace/datatransfer/application/ExportReadQuery.java` and file-writer ports in `src/main/java/com/jobtrace/datatransfer/application/ExportFileWriter.java`.
- [X] T010 Extend `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java` for only the two exact export GET paths to satisfy T007; keep bridge enablement opt-in and reuse `BridgePrincipalOwner`.

**Checkpoint**: Both downloads share trusted identity, immutable selection and read-only ports; import remains excluded.

---

## Phase 3: User Story 1 — Download My Application Records (Priority: P1) 🎯 MVP

**Goal**: Produce owned all/filtered application downloads with exact portable fields and safe CSV/XLSX output.

**Independent Test**: Seed two owners and full stage histories; compare all and filtered CSV/XLSX readback with synthetic legacy fixtures, including owner isolation and file safety. No application state changes.

### Tests for User Story 1

- [X] T011 [P] [US1] Add CSV and workbook structural parity tests for Chinese columns, field values, BOM, Unicode, delimiters, empty cells, formulas and safe/unsafe links in `src/test/java/com/jobtrace/datatransfer/ApplicationExportFileContractTest.java`.
- [X] T012 [P] [US1] Add PostgreSQL tests for all and filtered owner-bound roots/stages, every filter, inclusive dates, current label values, complete stage-history order, and zero foreign data in `src/test/java/com/jobtrace/datatransfer/ApplicationExportQueryIntegrationTest.java`.
- [X] T013 [P] [US1] Add use-case tests for trusted owner, stable ordering, no-result error, and query/writer failure propagation in `src/test/java/com/jobtrace/datatransfer/ExportApplicationsTest.java`.
- [X] T014 [P] [US1] Add HTTP tests for default/unknown scope and format, download media/disposition/no-store, safe invalid identity, and safe empty/failed export in `src/test/java/com/jobtrace/datatransfer/ApplicationExportControllerTest.java`.

### Implementation for User Story 1

- [X] T015 [US1] Implement all/filtered selection rules and output rows in `src/main/java/com/jobtrace/datatransfer/domain/ApplicationExportSelection.java` and `ApplicationExportRow.java` to satisfy T006 and T011.
- [X] T016 [US1] Implement parameterized, owner-bound application/stage reads and exact all/filtered sorting in `src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresExportReadQuery.java` to satisfy T012.
- [X] T017 [US1] Implement formula-safe UTF-8 BOM CSV with established quoting and column order in `src/main/java/com/jobtrace/datatransfer/infrastructure/CsvExportWriter.java` to satisfy T011.
- [X] T018 [US1] Implement streaming XLSX generation, literal cells, HTTP(S)-only hyperlinks, and scratch cleanup on success/failure in `src/main/java/com/jobtrace/datatransfer/infrastructure/XlsxExportWriter.java` to satisfy T011.
- [X] T019 [US1] Implement owner-scoped all/filtered application export orchestration in `src/main/java/com/jobtrace/datatransfer/application/ExportApplications.java` to satisfy T013.
- [X] T020 [US1] Expose only `GET /api/exports/applications` with trusted owner, current defaults, attachment metadata, no-store, and safe errors in `src/main/java/com/jobtrace/datatransfer/web/ExportController.java` to satisfy T014.
- [X] T021 [US1] Add bounded download operation/outcome/latency metrics without owner, file, ID, filter or content labels in `src/main/java/com/jobtrace/datatransfer/web/ExportMetrics.java`.
- [X] T022 [US1] Run T011–T014 and record independent all/filtered parity, isolation, read-only and scratch-cleanup evidence in `specs/007-private-data-export/validation-report.md`.

**Checkpoint**: All/filtered application exports work independently; selected mode and interview export are not needed for this MVP.

---

## Phase 4: User Story 2 — Download Selected Applications (Priority: P2)

**Goal**: Export only selected owned applications without widening to filtered/all results.

**Independent Test**: Request 1, 100 and 101 selected IDs with mixed owners and unrelated filters; only owned selected rows are exported, and invalid selection fails before file generation.

### Tests for User Story 2

- [X] T023 [P] [US2] Add selected-mode CSV/XLSX parity, zero/100/101 ID, duplicate-ID and filter-independence tests in `src/test/java/com/jobtrace/datatransfer/SelectedApplicationExportContractTest.java`.
- [X] T024 [P] [US2] Add PostgreSQL mixed-owner and deleted-ID selection tests with stable date/ID ordering and bounded query count in `src/test/java/com/jobtrace/datatransfer/SelectedApplicationExportQueryIntegrationTest.java`.
- [X] T025 [P] [US2] Add HTTP validation, missing-owned-result, no-store, and ordinary-principal denial tests in `src/test/java/com/jobtrace/datatransfer/SelectedApplicationExportControllerTest.java`.

### Implementation for User Story 2

- [X] T026 [US2] Extend `src/main/java/com/jobtrace/datatransfer/domain/ApplicationExportSelection.java` with selected-ID validation and exact scope precedence to satisfy T023.
- [X] T027 [US2] Extend `src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresExportReadQuery.java` with owner-bound selected-ID predicates without altering all/filtered behavior to satisfy T024.
- [X] T028 [US2] Complete selected-mode orchestration and safe no-result behavior in `src/main/java/com/jobtrace/datatransfer/application/ExportApplications.java` and `src/main/java/com/jobtrace/datatransfer/web/ExportController.java` to satisfy T025.
- [X] T029 [US2] Run T023–T025 and record selected-only parity and isolation in `specs/007-private-data-export/validation-report.md`.

**Checkpoint**: All three application scopes are independently verified; interview export remains separate.

---

## Phase 5: User Story 3 — Download My Interview Reviews (Priority: P3)

**Goal**: Download one owned review as Markdown or multiple as a collision-safe ZIP.

**Independent Test**: Select one and several reviews for two owners, with duplicate IDs/names, missing stages and structured/plain content; compare text, entry order, names and security with current runtime fixtures.

### Tests for User Story 3

- [X] T030 [P] [US3] Add Markdown parity tests for plain/structured reviews, section ordering, nulls and stage fallback in `src/test/java/com/jobtrace/datatransfer/InterviewMarkdownExportContractTest.java`.
- [X] T031 [P] [US3] Add ZIP and filename tests for selected order, duplicate IDs, Unicode/path safety, collision suffixes and one-entry-per-owned-review in `src/test/java/com/jobtrace/datatransfer/InterviewZipExportContractTest.java`.
- [X] T032 [P] [US3] Add PostgreSQL owner-bound batch root/question/action joins, stored-stage fallback, deleted/foreign IDs and bounded query-count tests in `src/test/java/com/jobtrace/datatransfer/InterviewExportQueryIntegrationTest.java`.
- [X] T033 [P] [US3] Add HTTP tests for zero/100/101 IDs, one Markdown versus multi ZIP, headers/no-store, missing-owned-result and ordinary-principal denial in `src/test/java/com/jobtrace/datatransfer/InterviewExportControllerTest.java`.

### Implementation for User Story 3

- [X] T034 [US3] Define ordered deduplicated review selection and document metadata in `src/main/java/com/jobtrace/datatransfer/domain/InterviewExportSelection.java` and `InterviewExportDocument.java` to satisfy T031.
- [X] T035 [US3] Add owner-bound batch review/child reads to `src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresExportReadQuery.java` to satisfy T032 without N+1 detail calls.
- [X] T036 [US3] Implement the legacy plain/structured Markdown representation and safe descriptive filename rules in `src/main/java/com/jobtrace/datatransfer/infrastructure/InterviewMarkdownWriter.java` to satisfy T030.
- [X] T037 [US3] Implement ordered ZIP packaging and collision-safe entry names with JDK ZIP primitives in `src/main/java/com/jobtrace/datatransfer/infrastructure/InterviewZipWriter.java` to satisfy T031.
- [X] T038 [US3] Implement owner-scoped single/multi-file orchestration in `src/main/java/com/jobtrace/datatransfer/application/ExportInterviews.java` to satisfy T030–T032.
- [X] T039 [US3] Expose only `GET /api/exports/interviews` with trusted owner, attachment metadata, no-store, and safe errors in `src/main/java/com/jobtrace/datatransfer/web/ExportController.java` to satisfy T033.
- [X] T040 [US3] Run T030–T033 and record independent interview parity, archive safety, isolation and read-only evidence in `specs/007-private-data-export/validation-report.md`.

**Checkpoint**: Both export journeys work independently; imports and all writes still belong to the existing service.

---

## Phase 6: Polish and Release Gates

**Purpose**: Prove cross-route security, resource safety, performance, contract parity and review readiness without deployment.

- [X] T041 [P] Add only the two protected download GET operations and media/error contracts to `specs/001-java-migration/contracts/openapi.yaml`; preserve import and mutation exclusions.
- [X] T042 [P] Add absent, forged, expired, path-mismatched, replayed, and ordinary-principal denial tests for both downloads in `src/test/java/com/jobtrace/datatransfer/ExportSecurityTest.java`.
- [X] T043 [P] Add safe storage/writer failure, request-ID, log-redaction, private-label-free metrics and no-store tests in `src/test/java/com/jobtrace/datatransfer/ExportFailureAndMetricsTest.java`.
- [X] T044 [P] Add 10-warm-up/40-sample tests for both selected downloads at 100 records, p95 ≤500 ms including serialization, and fixed SQL query counts in `src/test/java/com/jobtrace/datatransfer/ExportPerformanceTest.java`.
- [X] T045 [P] Stress all/filtered application export resource use, workbook scratch cleanup on success/failure, and no silent truncation in `src/test/java/com/jobtrace/datatransfer/LargeExportSafetyTest.java`.
- [X] T046 [P] Assert no production migration/Flyway activation, import or mutation handler, email/job integration, or frontend route change in `src/test/java/com/jobtrace/datatransfer/ExportReadOnlySurfaceTest.java` and `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java`.
- [X] T047 [P] Add architecture rules preserving the data-transfer read context boundary and avoiding applications/interviews/analytics package cycles in `src/test/java/com/jobtrace/ArchitectureTest.java`.
- [X] T048 [P] Document current-service import/writer/session/schema ownership, application no-store hardening, and no-deployment decision in `docs/migration-slices/private-data-export.md` and `docs/migration.md`.
- [X] T049 Run `./mvnw verify` with Docker and record fixture parity, two-owner isolation, OpenAPI, architecture, ≥80% changed-code line/branch coverage, Checkstyle, p95/query counts, large-export cleanup and Java-only artifact evidence in `specs/007-private-data-export/validation-report.md`.
- [X] T050 Review against `spec.md` and all four constitution principles, record Solo-Maintainer Mode self-review in `specs/007-private-data-export/validation-report.md`, open a PR to `main`, pass required CI/security checks, and record its URL and unscheduled deployment status.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup provides fixtures, test-only schema, contract parser and vetted XLSX writer.
- Foundational follows setup and blocks all three user stories.
- US1 is the MVP. US2 extends its application selection/query/controller, so follows US1 in the single-maintainer workflow.
- US3 can be tested separately after the foundation but follows US2 to avoid overlapping query/controller edits.
- Polish follows the selected stories and blocks merge, not production deployment.

### Within Each Story

1. Write synthetic fixtures and behavior tests first; observe the intended failure before production behavior.
2. Add immutable selections/projections, then owner-bound query, writers, use case and HTTP composition.
3. Verify each independent checkpoint before proceeding.
4. Keep the existing service as sole importer and writer throughout.

### Parallel Opportunities

- T002–T004 target independent fixture, schema and parser files.
- T006/T007 target independent rules and bridge tests; T008–T010 follow their evidence.
- Each story's `[P]` tests target separate files. T041–T048 mostly target separate files, except cross-cutting shared test edits should be serialized.
- The user requested no subagents. `[P]` indicates file independence only; it does not call for delegation.

## Implementation Strategy

1. Complete Setup and Foundational, then deliver US1 all/filtered downloads as the read-only MVP.
2. Add US2 selected application exports and US3 interview Markdown/ZIP while preserving each independent test checkpoint.
3. Finish cross-cutting gates and PR review. Do not merge until all required checks pass.
4. Import, writer transfer, deployment, routing, canary and rollback rehearsal require separate authorization and work.
