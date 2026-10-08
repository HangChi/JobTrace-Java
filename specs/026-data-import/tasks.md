# Tasks: Application Data Import

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

**Tests**: Required by FR-009 and repository quality gates. Work is sequential in one agent; `[P]` marks independent files, not delegated work.

## Phase 1: Setup

- [x] T001 Record current import contract, schema and validation decisions in `specs/026-data-import/research.md` and `specs/026-data-import/contracts/openapi.yaml`
- [x] T002 Verify existing dependencies and ignore patterns in `pom.xml`, `.gitignore` and `.dockerignore` without changing CI/CD

## Phase 2: Foundational

- [x] T003 Define import preview/result records and store port in `src/main/java/com/jobtrace/datatransfer/domain/` and `src/main/java/com/jobtrace/datatransfer/application/ImportStore.java`
- [x] T004 Add reusable mapping and row validation in `src/main/java/com/jobtrace/datatransfer/domain/ImportRowNormalizer.java`

## Phase 3: User Story 1 — Safe preview (P1)

**Goal**: Preview supported spreadsheets with row errors and owned duplicate candidates, without application writes.

**Independent Test**: Valid, mixed, foreign and malformed inputs produce expected private previews and zero application changes.

- [x] T005 [P] [US1] Add CSV/XLSX file and bounds tests in `src/test/java/com/jobtrace/datatransfer/ImportSpreadsheetReaderTest.java`
- [x] T006 [P] [US1] Add mapping, aliases, stage and validation tests in `src/test/java/com/jobtrace/datatransfer/ImportRowNormalizerTest.java`
- [x] T007 [US1] Implement strict bounded reader in `src/main/java/com/jobtrace/datatransfer/infrastructure/ImportSpreadsheetReader.java`
- [x] T008 [US1] Implement owner-scoped duplicate lookup and transactional preview persistence in `src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresImportStore.java`
- [x] T009 [US1] Implement preview use case and multipart route in `src/main/java/com/jobtrace/datatransfer/application/PreviewImport.java` and `src/main/java/com/jobtrace/datatransfer/web/ImportController.java`
- [x] T010 [US1] Add PostgreSQL and HTTP preview integration tests in `src/test/java/com/jobtrace/datatransfer/ImportPreviewIntegrationTest.java` and `src/test/resources/postgres/import-workflow.sql`

## Phase 4: User Story 2 — Confirm once (P2)

**Goal**: Confirm selected owned rows once and report per-row outcomes.

**Independent Test**: Mixed rows create/skip correctly; replay, foreign and expired batches do not write applications.

- [x] T011 [US2] Add confirmation parsing and result mapping in `src/main/java/com/jobtrace/datatransfer/application/ConfirmImport.java` and `src/main/java/com/jobtrace/datatransfer/web/ImportController.java`
- [x] T012 [US2] Call the existing owner-aware confirmation function and translate errors in `src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresImportStore.java`
- [x] T013 [US2] Add owner, conflict, row-failure and concurrency tests in `src/test/java/com/jobtrace/datatransfer/ImportConfirmIntegrationTest.java`
- [x] T014 [US2] Add export-to-import compatibility test in `src/test/java/com/jobtrace/datatransfer/ImportRoundTripIntegrationTest.java`

## Phase 5: Polish

- [x] T015 Document route and writer boundaries in `docs/migration-slices/data-import.md` and `docs/migration.md`
- [x] T016 Run `./mvnw verify`, inspect changed-code coverage and record representative timings in `specs/026-data-import/quickstart.md`

## Dependencies and execution

T001–T002 precede T003–T004. T003–T004 precede US1; US1 precedes US2. T005 and T006 affect different files and may be prepared independently, as may the final documentation and timing evidence. Since the user requested no sub-agents, all work remains in this one task. US1 is the MVP; full stage completion requires US2 and polish.
