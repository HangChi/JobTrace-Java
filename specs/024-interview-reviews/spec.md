# Feature Specification: Interview Reviews and Public Sharing

**Feature Branch**: `codex/024-interview-reviews`
**Created**: 2026-10-07
**Status**: Implemented
**Input**: Migrate private interview review creation, editing, deletion, public feed and detail, comments, likes, and privacy behavior to Java while preserving the current product and data.

## User Scenarios & Testing

### User Story 1 - Maintain private interview reviews (Priority: P1)

A signed-in person records an interview for one of their applications, edits the review and its questions/action items, and deletes it when no longer needed.

**Independent Test**: Create a review for an owned application and stage, edit it with a current version, and delete it; repeat with a foreign application/review and a stale version.

**Acceptance Scenarios**:

1. **Given** an owned active application and valid interview stage/date, **when** a review is created, **then** its detail and initial version are returned.
2. **Given** a current review version, **when** the owner saves questions and action items, **then** the update is atomic and advances the version.
3. **Given** a stale version, **when** an edit is attempted, **then** no content is changed and a conflict is returned.
4. **Given** a missing or foreign review, **when** it is edited or deleted, **then** the same not-found response is returned.

### User Story 2 - Publish and browse safe reviews (Priority: P2)

An owner may publish a completed review anonymously or with attribution. Signed-in readers can browse and open only public, completed reviews; private assessment and action information is never exposed.

**Independent Test**: Publish one completed review, read the feed and detail as another user, then make it private and verify it disappears and its old public URL fails.

**Acceptance Scenarios**:

1. **Given** a completed review with content, **when** its owner publishes it, **then** it appears in public latest/hot lists and detail using the selected attribution mode.
2. **Given** a draft or incomplete review, **when** publication is requested, **then** publication is rejected without changing visibility.
3. **Given** an anonymous public review, **when** another user reads it, **then** owner identity, private ratings, private actions and private application metadata are absent.
4. **Given** a review changed back to private, **when** its public URL is opened, **then** it is not found and cannot be liked or commented on.

### User Story 3 - Engage with public reviews (Priority: P3)

Signed-in readers can like/unlike a public review and add or browse comments. Engagement follows the review's current visibility.

**Independent Test**: Toggle a like twice, add a valid comment, list comments, then make the review private and retry each action.

**Acceptance Scenarios**:

1. **Given** an available public review, **when** a reader toggles like, **then** the returned count and viewer state reflect the change.
2. **Given** a nonblank comment of at most 1000 characters, **when** a reader submits it, **then** the comment and current engagement are returned.
3. **Given** an unavailable review, **when** an interaction is attempted, **then** it is not found and no interaction is stored.

### Edge Cases

- Invalid identifiers, dates, enum values, stage associations, terminal applications, duplicate review stages, and oversized content must not partially write.
- Repeated autosave revisions are idempotent; conflicting sessions or stale versions do not silently overwrite new work.
- Empty feed pages retain correct totals, facets, and pagination behavior.
- Public data never exposes ratings, interviewer notes, action items, or private owner/application identifiers.
- Storage failures must not be reported as success.

## Requirements

### Functional Requirements

- **FR-001**: Preserve the existing private review create, edit, delete routes, JSON fields, defaults, validation bounds, and status codes.
- **FR-002**: Owner-only writes must use existing owner-aware database operations and preserve stage/date checks, version conflicts, atomic nested content, and autosave revision behavior.
- **FR-003**: Only completed, explicitly public reviews may appear in the public feed, detail, comments, and likes; unpublishing takes effect immediately.
- **FR-004**: Public summary/detail must preserve latest/hot sorting, filters, cursor pagination, counts, facets, view recording, attribution choice, and safe content fields.
- **FR-005**: Comments and likes must preserve current toggle and comment contracts, validation, ordering, limits, and availability checks.
- **FR-006**: All routes use verified identity; missing and foreign private identifiers are indistinguishable, and public clients cannot assert identity through headers.
- **FR-007**: Automated tests cover validation, privacy, optimistic concurrency, persistence, public contracts, and failure paths.

### Key Entities

- **Interview review**: Owner-scoped review linked to an application and stage, with completion, visibility, attribution, version, and publication state.
- **Question and action item**: Ordered private review content; only selected question fields may enter public content.
- **Public engagement**: Viewer likes, comments and view count for an available public review.

## Success Criteria

- **SC-001**: All tested foreign and missing private identifiers produce indistinguishable not-found outcomes with no foreign changes.
- **SC-002**: Every tested stale edit leaves content unchanged; repeated autosave revisions do not duplicate writes.
- **SC-003**: No tested public response contains private ratings, notes, action items, owner IDs or application IDs.
- **SC-004**: Representative public reads complete within 500 ms p95 and interactive writes within 1 s p95 under documented local load.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- Existing Next.js behavior and the current PostgreSQL schema/functions are compatibility sources; no database migration is required in this stage.
- The old service remains production route owner pending a separate cutover. Frontend, CI/CD and deployment are outside this stage.
- The existing signed-in requirement for public routes is preserved.
