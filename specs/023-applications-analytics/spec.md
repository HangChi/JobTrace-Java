# Feature Specification: Applications and Analytics Operations

**Feature Branch**: `codex/023-applications-analytics`
**Created**: 2026-10-05
**Status**: Implemented and locally verified
**Input**: Complete Java application creation, editing, status and stage changes, deletion, analytics reports, and progress-reminder actions with the existing product's data and behavior.

## User Scenarios & Testing

### User Story 1 - Maintain my applications (Priority: P1)

A signed-in person can create, edit, change status, and delete only their own applications. A changed record retains its history and version, and stale edits are rejected.

**Independent Test**: Create an application, edit it, change its status, and delete it using one owner; repeat update and delete with another owner and a stale version.

**Acceptance Scenarios**:

1. **Given** valid application details, **when** the owner creates a record, **then** the API returns the saved detail and its initial history.
2. **Given** a current version, **when** the owner edits or changes status, **then** the update is atomic and advances the version.
3. **Given** a stale version or another owner's identifier, **when** a change is attempted, **then** it is rejected without mutation or disclosure.
4. **Given** one or up to 100 selected records, **when** the owner deletes them, **then** only that owner's records are removed and the bulk count is accurate.

### User Story 2 - Manage recruitment stages (Priority: P2)

The owner can add, edit, or remove dated stage occurrences while preserving the existing database's stage-flow and date rules.

**Independent Test**: Add a stage, edit its date, remove it, and verify history, optimistic locking, and rejection of duplicate or invalid stages.

**Acceptance Scenarios**:

1. **Given** a valid active application, **when** a stage is added, **then** its returned detail includes the new occurrence.
2. **Given** a current version, **when** a stage is edited, **then** the occurrence and application version change atomically.
3. **Given** another owner's or missing stage, **when** an edit or removal is attempted, **then** the same not-found result is returned.

### User Story 3 - Analyze progress and resolve reminders (Priority: P3)

The owner can request the existing period report and mark an actionable progress reminder completed or dismissed.

**Independent Test**: Compare a report for a bounded period with source data; resolve an owned active stage and confirm it disappears from pending reminders while another owner's stage remains inaccessible.

**Acceptance Scenarios**:

1. **Given** a supported period and filters, **when** the report is read, **then** its metrics, trend, funnel, dimensions, interview outcomes, and data-quality notes follow the existing product contract.
2. **Given** an invalid custom range, **when** the report is requested, **then** a validation error is returned without a database query.
3. **Given** an owned active stage, **when** its reminder is completed or dismissed, **then** the resolution is idempotently stored; a missing, foreign, or inactive stage is not found.

### Edge Cases

- Invalid UUIDs, malformed dates, invalid enum values, duplicate stages, terminal statuses, and future stage dates must not partially write.
- Cross-owner IDs must be indistinguishable from missing IDs.
- A job-market-linked creation must reject an unavailable or already-linked post atomically.
- Empty report cohorts and zero denominators must produce finite, stable metrics.
- Storage failures must not be reported as success.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST preserve existing application JSON fields, defaults, validation limits, owner isolation, and exact routes for create, update, status change, single and bulk deletion.
- **FR-002**: The system MUST use the legacy owner-aware SQL functions for application and stage writes; it MUST NOT take schema ownership in this stage.
- **FR-003**: The system MUST preserve application event history, optimistic version conflicts, and the existing stage chronology and terminal-state rules.
- **FR-004**: The system MUST implement the period report contract, including range resolution, comparison periods, stage and interview aggregates, type/city breakdown, and no-store responses.
- **FR-005**: The system MUST provide both progress-reminder completion and completed/dismissed resolution routes using owner-scoped, active-stage storage.
- **FR-006**: All protected operations MUST use verified session/bridge identity, reject public identity assertions, and avoid leaking another owner's data.
- **FR-007**: Tests MUST cover validation, conflicts, isolation, database behavior, contracts, and failure paths.

### Key Entities

- **Application**: Owner-scoped job opportunity with details, status, version and audit events.
- **Stage occurrence**: Dated recruitment milestone belonging to one application.
- **Analytics report**: Owner-scoped aggregate of applications and interview reviews for a period and optional filters.
- **Progress resolution**: Owner-scoped completed/dismissed disposition for an active stage occurrence.

## Success Criteria

- **SC-001**: Every tested foreign or missing identifier returns an indistinguishable not-found result, and no foreign row is changed.
- **SC-002**: Every tested stale update returns conflict without partial changes; valid writes preserve legacy JSON shape and history.
- **SC-003**: Report values match equivalent legacy fixtures for empty and populated cohorts, including comparison periods.
- **SC-004**: Representative report reads finish within 500 ms p95 and interactive writes within 1 s p95 under documented local load.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- The current PostgreSQL schema and functions remain the source of truth; existing Next.js behavior defines compatibility.
- The old service remains the production route owner until a later cutover. Frontend, CI/CD, deployment, and production routing are outside this stage.
