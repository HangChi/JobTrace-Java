# Feature Specification: Job Market Operations

**Feature Branch**: `codex/027-job-market`

**Created**: 2026-10-08

**Status**: In progress

**Input**: Complete the Java job-market backend: favorites, administrator source and catalog management, discovery and review, company research, ATS and WeChat intake, and synchronization jobs and run history.

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

An administrator can inspect the source catalog, add and update sources, bootstrap defaults, discover candidate sources and companies, research official company sites, and approve or ignore candidates.

**Why this priority**: A reviewed catalog is the prerequisite for trusted ingestion.

**Independent Test**: Create a source, inspect catalog and candidates, approve or ignore a candidate, and verify non-admin users cannot mutate the catalog.

**Acceptance Scenarios**:

1. **Given** a valid official source, **when** an administrator adds or edits it, **then** the catalog reflects the change and duplicate/invalid URLs are rejected.
2. **Given** a pending candidate, **when** an administrator approves or ignores it, **then** its review state changes once and approved records become usable sources or companies.
3. **Given** research or discovery results, **when** sites cannot be safely accessed, **then** the operation reports a bounded failure without accepting an untrusted URL.

### User Story 3 - Collect and synchronize safely (Priority: P3)

Authorized operators can scan ATS boards, collect WeChat articles, synchronize due or selected sources, and inspect run history and per-source outcomes.

**Why this priority**: Current marketplace listings depend on observable, repeatable background work.

**Independent Test**: Start a source sync against a deterministic fake adapter, verify its run record and resulting posts, then retry, fail, and inspect history.

**Acceptance Scenarios**:

1. **Given** an active approved source, **when** sync starts, **then** one run obtains its lease, records results and releases the lease.
2. **Given** an overlapping or stale run, **when** another worker tries to commit, **then** fencing prevents stale writes and history explains the outcome.
3. **Given** an untrusted ATS or WeChat URL, **when** collection is requested, **then** private-network destinations, redirects or unsupported hosts are rejected before sensitive access.
4. **Given** completed and failed jobs, **when** an administrator inspects run history, **then** pagination, status and error summaries are available without secrets.

### Edge Cases

- Concurrent favorite writes are idempotent and never reveal another owner's state.
- Source URLs and discovered links cannot reach loopback, private networks, metadata endpoints or unapproved redirect hosts.
- Candidate approval and source synchronization are safe under retries and concurrent workers.
- Administrator endpoints reject ordinary accounts; internal work endpoints require the existing trusted internal authentication mechanism.
- External pages can be empty, malformed, excessively large or unexpectedly encoded; collection remains bounded and auditable.
- No background work runs merely because a list or detail page was read.

## Requirements

### Functional Requirements

- **FR-001**: Preserve the existing favorite request and response behavior, including owner isolation and idempotence.
- **FR-002**: Provide administrator catalog, source CRUD, default bootstrap and source health operations compatible with the current client.
- **FR-003**: Provide source and company candidate discovery, list and review operations with explicit pending, approved and ignored states.
- **FR-004**: Support bounded research of official company sites and safe discovery of ATS board sources.
- **FR-005**: Support bounded WeChat article intake and reviewable company/source candidates.
- **FR-006**: Synchronize selected or due sources with lease/fencing safety and per-run metrics, errors and audit history.
- **FR-007**: Preserve existing administrator and internal authorization boundaries; never trust public identity headers or arbitrary remote URLs.
- **FR-008**: Reuse the existing schema, source adapters and business contract where possible without frontend, deployment or CI/CD changes.
- **FR-009**: Cover successful, invalid, cross-owner, non-admin, retry, concurrency, external-failure and SSRF cases with automated tests.

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
- **SC-003**: Stale or overlapping source workers cannot commit a second result for the same lease.
- **SC-004**: Representative user reads finish within 500 ms p95 and user-initiated writes within 1 s p95 under documented local sample load; external collection timings are reported separately.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- Existing Next.js routes and PostgreSQL schema define the compatibility contract until a separate routing cutover.
- Existing Java identity and administrator role checks are reused; no new public authentication mechanism is introduced.
- Frontend, production routing, scheduler deployment and CI/CD are outside this stage.
