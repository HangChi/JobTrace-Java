# Feature Specification: Job Market Operations

**Feature Branch**: `codex/027-job-market`

**Created**: 2026-10-08

**Status**: Complete for the revised shared-database, no-Java-crawler scope

**Input**: Complete the Java job-market experience using the existing remote PostgreSQL marketplace data. Java does not crawl, scan ATS/WeChat sites, bootstrap the remote catalog, or run a second synchronization worker.

## User Scenarios & Testing

### User Story 1 - Save and unsave a campaign (Priority: P1)

A signed-in job seeker can mark a job-market campaign as a favorite and remove that mark. The existing list and detail views immediately reflect the owner's choice.

**Why this priority**: It completes the existing user-facing marketplace interaction without requiring background processing.

**Independent Test**: Favorite and unfavorite a campaign as two different owners; verify isolated list/detail state and no cross-owner changes.

**Acceptance Scenarios**:

1. **Given** a campaign, **when** its owner favorites it twice, **then** both requests succeed and exactly one favorite exists.
2. **Given** a favorited company, **when** the owner removes the favorite, **then** all campaign favorite marks for that company disappear for that owner only.
3. **Given** a missing campaign or unauthenticated request, **when** it is changed, **then** the request fails safely and no favorite is written.

### User Story 2 - Curate sources and companies (Priority: P2)

An administrator can inspect the current remote source catalog and candidate records, add or update source metadata, and approve or ignore candidates already produced by the existing ingestion service.

**Why this priority**: A reviewed catalog is the prerequisite for trusted ingestion.

**Independent Test**: Create a source, inspect catalog and candidates, approve or ignore a candidate, and verify non-admin users cannot mutate the catalog.

**Acceptance Scenarios**:

1. **Given** a valid official source, **when** an administrator adds or edits it, **then** the catalog reflects the change and duplicate/invalid URLs are rejected.
2. **Given** a pending candidate, **when** an administrator approves or ignores it, **then** its review state changes once and approved records become usable sources or companies.
3. **Given** candidate and source records in the shared database, **when** an administrator views them, **then** the displayed status reflects the persisted data without a Java network request.

### User Story 3 - Observe existing ingestion (Priority: P3)

Authorized operators can inspect persisted source health and run history produced by the existing ingestion service. Java must not create a competing crawler or claim source leases.

**Why this priority**: Current marketplace listings depend on observable, repeatable background work.

**Independent Test**: Seed source, candidate and run rows, then verify Java list/detail and admin history responses without changing synchronization state.

**Acceptance Scenarios**:

1. **Given** existing campaign and post rows, **when** a user browses the market, **then** Java returns the same persisted data without fetching remote websites.
2. **Given** completed and failed sync jobs, **when** an administrator inspects run history, **then** pagination, status and safe error summaries are available.
3. **Given** a browser request for a legacy crawl action, **when** Java handles it, **then** it does not start a second ingest job or claim a source lease.

### Edge Cases

- Concurrent favorite writes are idempotent and never reveal another owner's state.
- Candidate approval is safe under retries and concurrent workers; source URLs remain validated metadata, not Java fetch targets.
- Administrator endpoints reject ordinary accounts. Java exposes no internal ingestion endpoint.
- Shared-database reads tolerate empty and stale ingestion results without triggering collection.
- No background work runs merely because a list or detail page was read.

## Requirements

### Functional Requirements

- **FR-001**: Preserve the existing favorite request and response behavior, including owner isolation and idempotence.
- **FR-002**: Provide administrator catalog, source CRUD and health views from the shared remote database; do not maintain a second bundled default catalog.
- **FR-003**: Provide source and company candidate list and review operations over persisted records with explicit pending, approved and ignored states.
- **FR-004**: Show persisted ingestion outcomes and sync run history; do not fetch ATS, WeChat or company pages, bootstrap defaults, or claim leases in Java.
- **FR-005**: Preserve administrator authorization and validate manually submitted source URLs as metadata; never trust public identity headers.
- **FR-006**: Reuse the existing schema and Java read model without frontend, deployment or CI/CD changes in this stage.
- **FR-007**: Cover successful, invalid, cross-owner, non-admin, retry, concurrency and empty/stale-data cases with automated tests.

### Key Entities

- **Campaign favorite**: Owner-to-campaign preference reflected in the marketplace read model.
- **Company and source**: Curated company identity, official source metadata and health state.
- **Source or company candidate**: Discovered item with provenance, confidence and review state.
- **Sync run**: Leased execution record with trigger, timing, counts and safe error summary.
- **Market post**: Normalized job listing tied to a company and one or more sources.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All tested cross-owner and unauthorized attempts cause zero unauthorized writes or disclosures.
- **SC-002**: Repeated favorite and review requests do not create duplicate state.
- **SC-003**: Java market reads and administrative inspection create zero ingestion runs or outbound collection requests.
- **SC-004**: Representative user reads finish within 500 ms p95 and user-initiated writes within 1 s p95 under documented local sample load.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- Existing Next.js routes and PostgreSQL schema define the compatibility contract until a separate routing cutover.
- Existing Java identity and administrator role checks are reused; no new public authentication mechanism is introduced.
- The legacy service remains the sole ingestion worker and shares the configured remote PostgreSQL schema with Java. Database connectivity and credentials are provided by deployment configuration, not committed files.
- Frontend, production routing, scheduler deployment and CI/CD are outside this stage.
