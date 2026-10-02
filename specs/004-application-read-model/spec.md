# Feature Specification: Application Read Model Migration

**Feature Branch**: `codex/004-application-read-model`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "Start feature 004 without deploying the Java version. Continue the migration with the next low-risk business slice."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse My Applications (Priority: P1)

An authenticated user can browse only their own job applications with the same fields, default
ordering, total count, and page metadata they receive today.

**Why this priority**: The application list is the entry point to the private workspace and the
next migration domain after the analytics pilot. A faithful read-only list delivers useful coverage
without transferring write ownership.

**Independent Test**: Seed applications for two owners, request the first owner's list through the
replacement read path, and verify the response contains only that owner's records with the expected
summary fields and pagination metadata.

**Acceptance Scenarios**:

1. **Given** an authenticated user with applications, **When** they request the application list,
   **Then** they receive only their applications with the existing summary fields, total, page,
   limit, and next-cursor contract.
2. **Given** an authenticated user with no applications, **When** they request the list, **Then**
   they receive an empty item collection, a zero total, and no next cursor.
3. **Given** records belonging to another user, **When** the authenticated user requests their
   list, **Then** no field or identifier from the other user's records is disclosed.

---

### User Story 2 - Find and Page Through Applications (Priority: P2)

An authenticated user can search, combine filters, choose a supported sort, and move through stable
pages without duplicates or skipped matching records.

**Why this priority**: Users with a long application history depend on filtering and stable paging
to locate current work. Contract parity requires more than returning an unfiltered collection.

**Independent Test**: Seed a mixed application history, execute search and combined status, type,
stage, city, and date filters under each supported sort, then follow cursors and verify the complete
ordered result set exactly once.

**Acceptance Scenarios**:

1. **Given** applications with different names and attributes, **When** the user combines search
   and filters, **Then** every returned item matches all supplied criteria.
2. **Given** more matching records than the requested limit, **When** the user follows successive
   cursors, **Then** ordering remains stable and no matching record is duplicated or skipped.
3. **Given** no explicit valid sort, **When** the user requests the list, **Then** submitted
   applications appear first and records within the established groups follow the existing recent-
   progress ordering.
4. **Given** unsupported or malformed optional query values, **When** the list is requested, **Then**
   safe existing defaults are applied without exposing internal errors.

---

### User Story 3 - Inspect One Application (Priority: P3)

An authenticated user can retrieve the core details, stage occurrences, and event history for one
application they own without gaining information about another user's application.

**Why this priority**: A focused detail read completes the core application read model while
leaving interview aggregation and all mutations for later slices.

**Independent Test**: Request an owned application, a missing identifier, and another owner's valid
identifier; verify the owned response matches the established detail contract while both inaccessible
cases are indistinguishable.

**Acceptance Scenarios**:

1. **Given** an application owned by the authenticated user, **When** its detail is requested,
   **Then** the response contains the existing application summary, notes, stage occurrences, event
   history, timestamps, and version fields.
2. **Given** a well-formed identifier that is missing or belongs to another user, **When** detail is
   requested, **Then** both cases produce the same not-found response.
3. **Given** an unauthenticated, forged, stale, mismatched, or replayed identity assertion, **When**
   either read is requested, **Then** access is denied without disclosing application data.

### Edge Cases

- A requested page is beyond the matching result set while the total remains non-zero.
- The requested limit is missing, zero, negative, non-numeric, or above the supported maximum.
- Search text or city values exceed their supported lengths.
- Multiple values are supplied for status, type, stage, or city, including unknown values.
- Date boundaries are malformed, reversed, or match records exactly on the boundary.
- Several records share the same primary sort value and must still page deterministically.
- A cursor is malformed, was produced for a different query, or points to a record no longer present.
- Optional application fields, stage history, or event history are absent.
- The data store or identity replay protection is unavailable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The replacement read path MUST require a valid server-issued identity assertion and
  MUST reject public identity or role headers.
- **FR-002**: Every application read MUST be scoped to the authenticated owner.
- **FR-003**: A missing application and an application owned by someone else MUST produce the same
  externally observable not-found outcome.
- **FR-004**: The list response MUST preserve the established application summary fields: identifier,
  company, position, city, job link, applied date, type, status, latest activity date, stages,
  follow-up state and reason, and version.
- **FR-005**: The list response MUST preserve the established page fields: items, next cursor, total,
  page, and limit.
- **FR-006**: The list MUST support partial text search and the established status, type, stage,
  city, applied-from, and applied-to filters.
- **FR-007**: The list MUST support company, position, applied-date, and latest-activity sorting in
  ascending or descending order.
- **FR-008**: When no valid explicit sort is provided, the list MUST preserve the existing default
  status-priority and recent-progress ordering.
- **FR-009**: Paging MUST be deterministic for equal sort values and MUST not duplicate or skip
  matching records while the underlying result set is unchanged.
- **FR-010**: Query normalization and bounds MUST preserve the existing behavior: search is trimmed
  and bounded, city values are bounded, page is at least one, limit is between one and 100, and the
  default limit is 50.
- **FR-011**: Unsupported optional filter values MUST be ignored and invalid optional sort or date
  values MUST fall back to the established safe behavior.
- **FR-012**: The detail response MUST preserve the established core application contract, including
  notes, stage occurrences, event history, created and updated timestamps, and version.
- **FR-013**: The feature MUST be read-only and MUST NOT create, update, delete, or transfer ownership
  of applications, stages, events, interviews, or any other business data.
- **FR-014**: The existing service MUST remain the sole writer and behavioral source of truth for
  this feature.
- **FR-015**: The feature MUST NOT change, replay, or take ownership of the existing database schema
  migration history.
- **FR-016**: List and detail responses MUST be contract-compared against representative existing-
  service fixtures, including empty, filtered, paged, and owner-isolation cases.
- **FR-017**: Failures MUST use the established problem response shape and MUST not expose secrets,
  personal data, query text, assertion contents, or storage details in logs.
- **FR-018**: The implementation MUST expose enough bounded diagnostics to distinguish success,
  invalid input, denied identity, not found, dependency failure, and latency-budget breach without
  identifying a user or application.
- **FR-019**: Production deployment, traffic activation, and a production observation window are
  explicitly outside feature 004; completion is based on local and continuous-integration evidence.
- **FR-020**: The interview-enriched dialog read and every application mutation endpoint are outside
  this feature and MUST continue to use the existing service.

### Key Entities

- **Application Summary**: The owner-scoped list representation of a job application, including its
  identifying display fields, dates, classification, current status, stages, follow-up state, and
  optimistic version.
- **Application Detail**: The owner-scoped core record plus notes, ordered stage occurrences, event
  history, timestamps, and version.
- **Application Page**: An ordered collection of summaries plus total count, requested page and
  limit, and an optional continuation cursor.
- **Application List Criteria**: Normalized search, filters, sort, direction, cursor, page, and limit
  used to select one owner's applications.
- **Authenticated Owner**: The identity established by the reviewed signed assertion contract and
  used as the mandatory scope for every read.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the representative contract data set, list and detail responses match the existing
  service for 100% of asserted fields, ordering, pagination metadata, and error outcomes.
- **SC-002**: Across owner-isolation tests, zero records, identifiers, counts, stages, events, or
  existence signals belonging to another owner are disclosed.
- **SC-003**: Stable-pagination tests traverse 100% of a fixed matching result set exactly once under
  every supported sort and direction.
- **SC-004**: At the documented representative load, at least 95% of list and detail reads complete
  within 500 milliseconds.
- **SC-005**: Automated tests cover empty, normal, boundary, invalid-query, authentication, owner-
  isolation, unavailable-dependency, and contract-parity behavior with at least 80% line and branch
  coverage for changed production code.
- **SC-006**: A maintainer can run the documented verification workflow from a clean checkout and
  obtain a pass or actionable failure without production credentials or production deployment.

## Assumptions

- The reviewed feature 003 signed identity bridge is available and remains the authentication
  boundary for protected replacement reads.
- The existing application schema, database functions, enum values, and response behavior remain
  authoritative during feature 004.
- Existing clients continue using the current contract; this feature does not introduce a new user
  interface or change accessibility behavior.
- Representative verification may seed isolated test data and use synthetic identities; production
  data and production traffic are not required.
- The interview-enriched application dialog belongs to the later interviews migration because its
  response crosses bounded contexts.
