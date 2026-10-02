# Feature Specification: Private Interview Read Model Migration

**Feature Branch**: `codex/005-interview-read-model`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "Continue Java migration with feature 005 through the plan stage; do not deploy the Java version."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse My Interview Reviews (Priority: P1)

An authenticated user can browse their private interview and assessment reviews with the same summary, search, filter, count, and continuation behavior available in the existing service.

**Why this priority**: The private list is the entry point to interview history and provides an independently useful, read-only migration slice.

**Independent Test**: Given reviews for two owners, request each owner's list with empty, filtered, and paginated data; verify summaries and counts match the existing service and never include the other owner's records.

**Acceptance Scenarios**:

1. **Given** reviews owned by the user, **When** they browse the list, **Then** they receive the established summary fields in interview-date-descending order with a stable tie-breaker, total count, limit, and optional next cursor.
2. **Given** no matching reviews, **When** the user browses or filters, **Then** the result has an empty item list, zero total, and no next cursor.
3. **Given** reviews for multiple owners, **When** one owner searches or filters, **Then** neither results nor totals reveal another owner's reviews.
4. **Given** a matching set larger than the requested limit, **When** the user follows successive cursors, **Then** each matching review appears exactly once while the data remains unchanged.

---

### User Story 2 - Inspect One Private Review (Priority: P2)

An authenticated user can inspect one of their own reviews, including ordered questions and action items, without learning whether an inaccessible identifier belongs to someone else.

**Why this priority**: Review detail is necessary to read the user's full notes and follow-up work, but requires no write ownership.

**Independent Test**: Read an owned review with and without child records, then request a missing review and another owner's review; verify contract parity and indistinguishable not-found outcomes.

**Acceptance Scenarios**:

1. **Given** an owned review with questions and action items, **When** the user opens it, **Then** all established detail fields and child records appear in saved order.
2. **Given** an owned review with no questions or action items, **When** the user opens it, **Then** both collections are empty rather than missing.
3. **Given** a missing identifier or another owner's review identifier, **When** the user opens it, **Then** both requests produce the same externally observable not-found result.

---

### User Story 3 - See Interviews Beside an Application (Priority: P3)

An authenticated user can open the existing application detail view and see the interview summaries related to that application beside the already available application information.

**Why this priority**: Feature 004 deliberately excluded this cross-context view; completing it avoids leaving the application dialog's interview timeline behind.

**Independent Test**: Given an owned application with linked and unlinked reviews, read its dialog data and compare both the application portion and ordered interview summaries with the existing service. Missing and cross-owner applications must be indistinguishable.

**Acceptance Scenarios**:

1. **Given** an owned application with reviews, **When** its detail view is opened, **Then** the existing application detail is accompanied by the established ordered interview summaries.
2. **Given** an owned application without reviews, **When** its detail view is opened, **Then** the application remains visible with an empty interview collection.
3. **Given** a missing or cross-owner application, **When** its detail view is requested, **Then** neither the application nor any interview existence signal is disclosed.

### Edge Cases

- Search matches a question but not company or position, or matches text in another owner's question.
- Repeated filter values, unknown enum values, invalid dates, invalid application identifiers, oversized search text, and limits outside the supported range.
- A malformed cursor, a structurally valid cursor reused with another filter set, and multiple reviews sharing the same interview date; every case remains owner-scoped and follows the existing navigation semantics.
- A linked stage occurrence has been removed, leaving its stored stage snapshot; assessment reviews are also valid.
- Visibility is public but the owner still needs to see the review in their private list; publication state must not broaden another user's access.
- A review has null optional fields, no children, or a large collection of questions and action items.
- The data store or identity replay protection is unavailable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every replacement private read MUST require the reviewed server-issued identity and MUST reject public identity or owner headers.
- **FR-002**: Every list, total, detail, child-record, and application-aggregation read MUST be scoped to the authenticated owner.
- **FR-003**: Missing and cross-owner identifiers MUST produce indistinguishable not-found outcomes for private review and application-dialog reads.
- **FR-004**: The private list MUST preserve the established review summary fields, including application and stage links, company, position, stage, date, review status, round result, linked flag, question/action counts, publication state, author mode, and publication date.
- **FR-005**: The list MUST preserve the established items, total, limit, and optional continuation cursor fields.
- **FR-006**: The list MUST support the established application, text, status, stage, result, interview-date, and publication filters, including their current defaults and validation behavior.
- **FR-007**: List ordering MUST be deterministic by interview date and identifier, and cursor traversal MUST not duplicate or skip matching records while the data is unchanged.
- **FR-008**: The detail read MUST preserve all summary fields plus format, duration, notes, highlights, gaps, version, timestamps, ordered questions, and ordered action items.
- **FR-009**: A review whose stage link was removed MUST still display its stored stage snapshot, as in the existing service.
- **FR-010**: Application dialog data MUST preserve the feature 004 application detail and add the established ordered, owner-scoped interview summaries.
- **FR-011**: Empty lists and child collections MUST have stable empty-array representations; optional values MUST retain established nullability.
- **FR-012**: Representative responses and error outcomes MUST be compared with the existing service for empty, normal, filtered, paged, unlinked, and owner-isolation cases.
- **FR-013**: Failures MUST use the project's safe problem response, carry a request identifier, and avoid exposing identity assertions, private review content, query text, or storage details in logs and metrics.
- **FR-014**: The replacement path MUST expose bounded diagnostics for success, invalid input, denied identity, not found, dependency failure, and latency-budget breach without user or review identifiers as labels.
- **FR-015**: This feature MUST be read-only. The existing service remains the sole session, business-write, publication, engagement, and schema owner.
- **FR-016**: Public interview feeds and details, view counts, likes, comments, exports, review creation/editing/deletion, and application mutations MUST remain on the existing service.
- **FR-017**: The feature MUST NOT alter or adopt the existing database migration history.
- **FR-018**: Java deployment, traffic activation, production canary, and production observation are outside feature 005; completion is based on local and continuous-integration evidence.

### Key Entities

- **Interview Review Summary**: The owner's list representation of one interview or assessment review, linked to an application and optionally to a stage occurrence.
- **Interview Review Detail**: Summary plus private notes, ordered questions and action items, version, and timestamps.
- **Interview Page**: Ordered owner-scoped summaries, total count, bounded limit, and optional continuation cursor.
- **Interview List Criteria**: Normalized search, filters, cursor, and limit for the owner's list.
- **Application Dialog Data**: Existing application detail plus its ordered interview summaries.
- **Authenticated Owner**: The trusted identity used as the mandatory scope for every private read.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative private list, detail, and application-dialog responses match the existing service for 100% of asserted fields, collection order, pagination metadata, and relevant error outcomes.
- **SC-002**: Isolation tests disclose zero review rows, child records, counts, application links, or existence signals from another owner.
- **SC-003**: Under every documented filter combination in a fixed data set, cursor traversal returns 100% of matching reviews exactly once.
- **SC-004**: At documented representative load, at least 95% of private list, detail, and application-dialog reads complete within 500 milliseconds.
- **SC-005**: Automated validation covers empty, normal, invalid-input, authorization, cross-owner, unavailable-dependency, unlinked-stage, and contract-parity behavior without production credentials.
- **SC-006**: A maintainer can verify the slice from a clean checkout and obtain actionable pass/fail evidence without deploying Java or changing production traffic.

## Assumptions

- The existing private review list, review detail, and application dialog are the next bounded read slice after feature 004; public/community capabilities and writes are separate future work.
- Feature 003's signed identity bridge remains the only trusted authentication boundary for replacement private reads.
- The existing service and schema remain authoritative; synthetic fixtures and isolated test data can represent the legacy behavior.
- Existing browser screens and accessibility behavior do not change in feature 005.
- The user's earlier no-deployment decision remains in force for the Java migration.
