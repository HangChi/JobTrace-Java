# Feature Specification: Java Migration Foundation

**Feature Branch**: `main`

**Created**: 2026-09-30

**Status**: Complete — validated on 2026-10-01

**Input**: User description: "Create the new JobTrace repository foundation for a Java backend and a React/TypeScript frontend, including documentation, configuration, continuous integration, and an actionable migration backlog."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reproducible contributor setup (Priority: P1)

As a contributor, I can clone the repository, follow one documented setup path, and verify both the backend and browser application without discovering undocumented local prerequisites.

**Why this priority**: Every later migration slice depends on a trustworthy, repeatable engineering baseline.

**Independent Test**: A contributor using a clean workstation can follow the quickstart, run all automated checks, and see the application shell and live health response.

**Acceptance Scenarios**:

1. **Given** a clean clone with the documented runtime versions, **When** the contributor runs the documented verification commands, **Then** backend tests, frontend tests, linting, and the production build complete successfully.
2. **Given** a local database and example configuration, **When** the contributor starts both development processes, **Then** the browser application loads and backend health endpoints are reachable through the documented addresses.

---

### User Story 2 - Single production artifact (Priority: P2)

As an operator, I can deploy one backend artifact that serves the compiled browser application and does not require a JavaScript runtime in production.

**Why this priority**: Removing the production Node.js runtime is the main operational benefit of the selected architecture.

**Independent Test**: Build the production artifact, run it with only the documented backend runtime and database, and open the application shell without starting a separate frontend server.

**Acceptance Scenarios**:

1. **Given** a successful production build, **When** the artifact is started in an environment without a JavaScript runtime, **Then** it serves both the browser application and live health endpoint.
2. **Given** an unavailable database, **When** the operator checks service health, **Then** liveness remains distinguishable from database readiness.

---

### User Story 3 - Safe incremental migration (Priority: P3)

As a maintainer, I can migrate one existing capability at a time while preserving public behavior, data ownership rules, and a clear rollback path.

**Why this priority**: The existing application contains mature authentication, database, concurrency, and privacy behavior that must not be lost in a rewrite.

**Independent Test**: Select one read-only capability, document its existing contract, implement it in the new repository, and demonstrate equivalent observable results against representative data without changing the existing database schema.

**Acceptance Scenarios**:

1. **Given** an existing capability selected for migration, **When** its contract and verification fixtures are recorded, **Then** both implementations can be compared using the same acceptance cases.
2. **Given** an existing production database, **When** the new service connects before migration ownership is transferred, **Then** it does not automatically modify the schema or claim ownership of legacy migrations.
3. **Given** a migrated capability that fails release validation, **When** the rollback procedure is followed, **Then** traffic returns to the existing implementation without data loss.

### Edge Cases

- The database is unreachable while the backend process itself remains healthy.
- A contributor has the correct backend runtime but no JavaScript tooling, or the reverse.
- The legacy migration history and the new migration tool disagree about schema ownership.
- Browser routing requests a nested path directly from the backend artifact.
- Authentication identity headers are supplied by an untrusted public client.
- The old and new services attempt to write the same business aggregate concurrently.
- Secrets or local environment values are accidentally added to version control.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide a documented and reproducible setup for backend and browser development.
- **FR-002**: The repository MUST pin the required backend runtime and provide a version-controlled build wrapper.
- **FR-003**: The repository MUST provide example configuration containing no usable secrets.
- **FR-004**: Automated checks MUST cover backend tests, browser tests, static analysis, and production builds.
- **FR-005**: The production deliverable MUST serve the compiled browser application without a separately running browser build service.
- **FR-006**: The service MUST expose separate liveness and database-readiness signals.
- **FR-007**: Legacy database migration execution MUST remain disabled until an explicit, reviewed baseline is approved.
- **FR-008**: Migration work MUST preserve existing API behavior, authorization outcomes, owner isolation, concurrency conflict behavior, and safe error responses.
- **FR-009**: Each migrated capability MUST have an independent verification method and rollback path before it becomes the only implementation.
- **FR-010**: Only one implementation MAY own writes for a business aggregate at any point during migration.
- **FR-011**: Public clients MUST NOT be trusted to assert user identity or administrative roles.
- **FR-012**: Repository documentation MUST record the selected remote hosting, applied branch protection, and owner-approved licensing decisions.

### Key Entities

- **Migration Slice**: One bounded capability selected for migration, including its contracts, data access, verification evidence, traffic ownership, and rollback state.
- **Contract Baseline**: The observable request, response, error, authorization, and persistence behavior that a migrated capability must preserve.
- **Schema Baseline**: The reviewed point at which the new repository begins owning future database migrations without replaying or rewriting legacy history.
- **Build Artifact**: The deployable unit containing the backend application and compiled browser assets.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new contributor can complete the documented local verification workflow in under 20 minutes after installing the listed prerequisites.
- **SC-002**: All repository checks run automatically for every proposed change and report a clear pass or failure before merge.
- **SC-003**: A production build produces one deployable artifact that renders the application shell and reports liveness without a separately running frontend service.
- **SC-004**: Database readiness failure is detectable independently from process liveness within 10 seconds.
- **SC-005**: Every migrated capability passes 100% of its recorded contract acceptance cases before traffic ownership changes.
- **SC-006**: No migration release permits simultaneous old-and-new writes to the same aggregate.
- **SC-007**: No committed file contains a production credential or usable secret.

## Assumptions

- The existing JobTrace repository remains the behavioral source of truth during migration.
- The existing PostgreSQL schema and stored routines are reused initially rather than redesigned.
- Browser functionality continues to require a JavaScript toolchain during development and CI, but not in production.
- Authentication remains owned by the existing service until a separately specified transition is approved.
- Repository hosting, remote URL, branch protection, and license choice require explicit owner decisions; the approved GitHub remote, protected `main` rules, and MIT License are recorded in `docs/repository-setup.md`.
