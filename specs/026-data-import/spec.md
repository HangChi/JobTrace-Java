# Feature Specification: Application Data Import

**Feature Branch**: `codex/026-data-import`
**Created**: 2026-10-07
**Status**: Implemented
**Input**: Migrate the existing application import preview, validation, confirmation and error handling to Java, completing the workflow alongside the existing exports.

## User Scenarios & Testing

### User Story 1 - Preview a spreadsheet safely (Priority: P1)

A signed-in job seeker uploads a CSV or XLSX file and sees normalized application rows, validation errors and possible duplicates before any application is created.

**Independent Test**: Upload representative valid, mixed and invalid files; inspect the preview and assert that the application count does not change.

**Acceptance Scenarios**:

1. **Given** a supported file with recognized headers, **when** it is previewed, **then** every nonblank row has a source row number, normalized values or field errors, and matching owned application IDs.
2. **Given** an unknown header, **when** a valid one-to-one column mapping is supplied, **then** the resulting preview uses that mapping.
3. **Given** an unsupported, oversized or malformed file, **when** preview is requested, **then** an actionable error is returned and no batch or application is created.
4. **Given** an existing preview, **when** a replacement preview succeeds, **then** the old preview becomes unusable; a failed replacement leaves it usable.

### User Story 2 - Confirm selected rows once (Priority: P2)

A job seeker chooses which valid rows to import and confirms the batch once, without overwriting existing applications.

**Independent Test**: Confirm a mixed preview with selected, skipped and invalid rows, then retry and test foreign/expired batches.

**Acceptance Scenarios**:

1. **Given** a current owned preview, **when** selected rows are confirmed, **then** selected valid rows create independent applications and other rows are skipped.
2. **Given** a duplicate candidate, **when** the user explicitly selects import, **then** a new application is created rather than replacing the candidate.
3. **Given** an expired, processed or foreign batch, **when** confirmation is requested, **then** no application is created and a safe not-found or conflict response is returned.
4. **Given** a failing row in a mixed batch, **when** confirmation runs, **then** other valid rows may succeed and each row's outcome is reported.

### Edge Cases

- CSV requires valid UTF-8 and rejects NUL bytes; XLSX reads only the first worksheet and never evaluates formulas.
- Files are limited to 5 MiB, 10,000 data rows, 100 columns and 200,000 cells.
- A header may not map twice to the same business field; unknown source and target fields are rejected.
- Names, date, URL, type, status and stage history follow the existing application rules; invalid rows cannot be imported.
- Concurrent confirmations cannot import the same batch twice, and no user may view or confirm another user's batch.
- Existing browser loading, preview, selection and error states remain unchanged; no frontend work is included.

## Requirements

### Functional Requirements

- **FR-001**: Preserve the existing authenticated preview and confirmation routes, request fields and response shapes.
- **FR-002**: Preview CSV and first-sheet XLSX files within documented limits, retaining source row numbers and safely rejecting malformed content.
- **FR-003**: Infer or validate column mappings and normalize the current application fields, including optional stage history.
- **FR-004**: Report row-level validation issues and owner-scoped duplicate candidates without writing applications during preview.
- **FR-005**: Persist a private preview for 24 hours and replace an old preview only after a new one is saved successfully.
- **FR-006**: Confirm selected rows atomically at batch level, isolate individual row failures, preserve imported creation events and report created/skipped/failed outcomes.
- **FR-007**: Reject foreign, expired or previously confirmed batches; repeated or concurrent confirmation must not create duplicate applications from one batch.
- **FR-008**: Reuse the current data store and export behavior without schema, frontend, routing, deployment or CI/CD changes.
- **FR-009**: Cover security, file limits, mapping, row validation, duplicate detection, confirmation conflicts and export-to-import round trips with automated tests.

### Key Entities

- **Import batch**: Private preview metadata, mapping, counts, expiration and lifecycle status.
- **Import row**: Source row number, normalized application data or errors, duplicate candidates, decision and result.
- **Application**: Owner-scoped record created from a confirmed row, with its normal creation event and optional stage history.

## Success Criteria

- **SC-001**: All tested invalid and foreign requests cause zero application writes or cross-account disclosures.
- **SC-002**: Every confirmed batch is processed at most once, including concurrent attempts.
- **SC-003**: A representative mixed 10,000-row file can be previewed within the 5 MiB limit and reported without truncating row outcomes.
- **SC-004**: Representative preview reads complete within 500 ms p95 and confirmations within 1 s p95 under documented local sample load, excluding file transfer time; larger batch timings are measured and reported separately.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- The current Next.js import workflow and existing PostgreSQL functions define compatible behavior.
- The old service remains the production route owner until a separate cutover.
- This stage changes only Java code and migration documentation; no CI/CD, production routing or frontend work is authorized.
