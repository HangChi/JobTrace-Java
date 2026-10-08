# Feature Specification: Java Migration Code Hardening

**Feature Branch**: `codex/029-code-hardening`

**Created**: 2026-10-08

**Status**: In progress

**Input**: Finish stage 029: compare legacy and Java behavior, close security, concurrency and error-contract gaps, add end-to-end regression, and resolve schema ownership without enabling an unapproved migration baseline. Stage 028 coverage and manual acceptance are outside this request.

## User Scenarios & Testing

### User Story 1 - Existing workflows remain compatible (Priority: P1)

A signed-in job seeker or administrator uses the Java version for an existing workflow and receives the same supported business outcome as the legacy version, including validation, ownership and pagination semantics.

**Independent Test**: Run a versioned route-and-behavior matrix against representative legacy fixtures and Java responses for each supported workflow group.

**Acceptance Scenarios**:

1. **Given** a supported legacy workflow, **when** the equivalent Java route is called with a valid request, **then** its relevant response shape and state transition match the documented contract.
2. **Given** an unsupported legacy crawl or scheduler action, **when** Java routes are inspected, **then** no Java crawler or sync-run writer is exposed.
3. **Given** malformed or stale input, **when** a Java command is called, **then** the client receives the documented validation or conflict outcome without a partial write.

### User Story 2 - Failure and concurrent requests remain safe (Priority: P1)

A user cannot read or change someone else's data, repeat a consumed action, or mistake a failed request for a successful one. Concurrent commands preserve once-only and version invariants.

**Independent Test**: Exercise anonymous, cross-owner, forged, replayed and parallel requests against the same service boundaries with deterministic storage fixtures.

**Acceptance Scenarios**:

1. **Given** an unauthenticated or ordinary-user caller, **when** it attempts a protected owner/admin operation, **then** no protected data or mutation is returned.
2. **Given** two requests racing on one version or once-only token, **when** both execute, **then** no duplicate effect is committed and one receives a safe conflict or idempotent outcome.
3. **Given** a dependency failure, **when** an API request fails, **then** the response is a stable, non-sensitive error with a request identifier and no false success.

### User Story 3 - Data ownership is unambiguous (Priority: P2)

Maintainers can operate the Java version against the shared database without accidental schema takeover or duplicate marketplace collection.

**Independent Test**: Start Java against the synthetic legacy-shaped database and verify no schema history or collection write is created; review an ownership matrix and activation gate.

**Acceptance Scenarios**:

1. **Given** the current unapproved baseline, **when** Java starts, **then** it does not run schema migrations or create migration history.
2. **Given** shared marketplace data, **when** Java reads or curates it, **then** legacy ingestion remains the sole crawler and sync-run writer.
3. **Given** a request to turn on Java schema migrations later, **when** approval evidence is incomplete, **then** the documented gate prohibits activation.

### Edge Cases

- Expired sessions during mutation, wrong-origin browser requests, missing CSRF token and forged bridge assertions must not write data.
- Duplicate requests, stale versions and cross-owner identifiers must preserve atomicity and non-disclosure.
- Missing database, cache, email or object storage must return safe errors without leaking secrets.
- Mixed old/new database schema versions must not trigger automatic repair or baseline adoption.

## Requirements

### Functional Requirements

- **FR-001**: Maintain a traceable inventory of supported legacy routes, Java equivalents, intentional differences and legacy-only operations.
- **FR-002**: Protect owner/admin routes against anonymous, forged, replayed and cross-owner requests; browser writes must retain origin and CSRF checks.
- **FR-003**: Preserve atomic once-only, version and concurrent update behavior for state-changing workflows.
- **FR-004**: Return stable client-error, conflict and dependency-failure responses with non-sensitive diagnostics and request correlation.
- **FR-005**: Provide automated cross-workflow regression for representative user and admin journeys; manual acceptance is delegated to the user.
- **FR-006**: Keep the existing database schema and marketplace ingestion ownership explicit; do not enable migrations without the documented approval evidence.
- **FR-007**: Make no CI/CD, production routing or stage-028 coverage changes.

### Key Entities

- **Contract row**: Legacy route and behavior, Java equivalent, compatibility status, and evidence.
- **Mutation**: Owner-scoped command with idempotency or version state and an atomic outcome.
- **Error response**: Stable status/code, safe detail and request correlation identifier.
- **Schema ownership record**: Current writer, migration authority, baseline evidence and activation gate.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Every in-scope legacy route has a documented Java equivalent or explicit legacy-only disposition; zero unexplained routes remain.
- **SC-002**: Representative automated tests cover a successful and a rejected path for each user/admin workflow group, plus at least one concurrent once-only transition.
- **SC-003**: All protected-route security regressions tested in this stage reject unauthorized access without data disclosure or mutation.
- **SC-004**: All tested dependency failures return a safe correlated error; no tested failure is reported as success.
- **SC-005**: Synthetic legacy-database startup creates zero schema-migration records and zero marketplace ingestion runs.
- **SC-006**: The existing automated code-quality and test suite passes; interactive API reads and writes retain their documented latency budgets.

## Assumptions

- The Java version targets the current shared remote PostgreSQL data, but all automated tests use local/synthetic data and do not mutate production.
- ATS/WeChat collection and background marketplace synchronization remain in the legacy service.
- The legacy migration head and Flyway baseline are not approved, so this stage documents ownership and preserves Flyway disabled instead of claiming schema control.
- Browser UI manual verification and stage-028 coverage are postponed at the user's request.
